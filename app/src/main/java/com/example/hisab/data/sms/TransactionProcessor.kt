package com.example.hisab.data.sms

import com.example.hisab.data.db.dao.AccountDao
import com.example.hisab.data.db.dao.CategoryDao
import com.example.hisab.data.db.dao.PendingTransactionDao
import com.example.hisab.data.db.dao.TransactionDao
import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.db.entity.PendingTransactionEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionConfidence
import com.example.hisab.data.model.TransactionSource
import com.example.hisab.data.model.TransactionType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.round

/**
 * What happened to one message. Every path returns one of these — nothing exits in silence, which is
 * design principle 4 and the reason the next "it didn't notify" report can name a cause instead of a
 * guess.
 */
sealed interface ProcessingOutcome {

    /** Claimed and the Stage-1 notification was accepted by the platform. */
    data class Notified(val pendingId: Long) : ProcessingOutcome

    /**
     * Claimed, but the notification could not be posted (permission denied, notifier threw).
     * The row is durable and dashboard-visible, and [TransactionProcessor.recoverUnnotified] will
     * retry it within the INV-6 bounds. This is *not* a lost message.
     */
    data class ClaimedNotNotified(val pendingId: Long, val reason: String) : ProcessingOutcome

    /** Merged with an opposite pending row into a single TRANSFER. */
    data class Merged(val transactionId: Long, val fromAccount: String, val toAccount: String) :
        ProcessingOutcome

    /** Room's UNIQUE index proved this identity was already claimed (INV-2) — the only true dup proof. */
    data class Duplicate(val identity: String) : ProcessingOutcome

    /** A positive finding said not to create a row. Never an absence of evidence. */
    data class Suppressed(val reason: SuppressReason) : ProcessingOutcome

    /** Not a bank transaction message, or unparseable. */
    data object NotATransaction : ProcessingOutcome

    /** Something threw. The notification attempt still happened if a claim had committed. */
    data class Failed(val error: Throwable) : ProcessingOutcome
}

/**
 * The one pipeline every bank SMS flows through, whether it arrived live ([SmsOrigin.REALTIME]) or
 * was found by the inbox scanner ([SmsOrigin.CATCHUP]).
 *
 * Previously the receiver and the catch-up scanner each had their own copy of the logic, writing to
 * one shared `SharedPreferences` file with byte-identical keys — so the scanner routinely consumed a
 * message's identity and then inserted the row *without notifying*, which is root cause #9 of the
 * missing-notification defect. Both callers now delegate here, and a class-level [mutex] serialises
 * them.
 *
 * Android-free by construction: everything platform-specific arrives through the gateways in
 * `SmsGateways.kt`, so the whole policy is exercised by JVM fakes in `TransactionProcessorTest`.
 *
 * ### Ordering contract (design principle 3)
 * ```
 * parse → gates → ATOMIC CLAIM → COMMIT → notification attempt → balance sync → backup
 * ```
 * The authoritative commit necessarily precedes the notification — the claimed row's id *is* the
 * notification's subject — so "notify first" is impossible. The binding rule is what comes after:
 * once the claim commits, nothing slow or non-essential may run before the notification attempt.
 * The old receiver ran a full serialise-and-write auto-backup between the two, inside a ~10 s
 * `goAsync` budget; if it threw or overran, the transaction was saved and *no* notification appeared.
 *
 * Note this puts balance sync *after* the notification rather than at the plan's stage 9. Balance
 * sync writes to the database and can post a second notification of its own, so running it first
 * would put the guaranteed Stage-1 notification behind avoidable failure modes. Nothing depends on
 * the order: the claim only writes a *pending* row, and the one transaction the claim can write —
 * the auto-merged TRANSFER — is excluded from the balance arithmetic by its own identity.
 */
class TransactionProcessor(
    private val pendingDao: PendingTransactionDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val cache: SmsHashCache,
    private val db: AtomicDb,
    private val notifier: SmsNotifier,
    private val backup: BackupTrigger,
    private val clock: SmsClock = SmsClock(),
    private val diagnostics: SmsDiagnosticsLog = SmsDiagnosticsLog.None
) {

    companion object {
        /** INV-6: how far back an unnotified claim stays eligible for automatic retry. */
        const val NOTIFICATION_RECOVERY_WINDOW_MS = 48L * 60 * 60 * 1000

        /** INV-6: retries per row. Past this the row stays valid and visible, but stops retrying. */
        const val MAX_NOTIFICATION_ATTEMPTS = 5

        /** Transfer auto-merge window, unchanged from the shipped behaviour. */
        const val AUTO_MERGE_WINDOW_MS = 120_000L

        /** Manual-entry reconciliation lookback, unchanged from the shipped behaviour. */
        const val MANUAL_MATCH_LOOKBACK_DAYS = 1L

        /** Catch-up proximity window, unchanged from the shipped behaviour. */
        const val CATCHUP_PROXIMITY_MS = 30L * 60 * 1000

        /** Smallest balance gap worth reporting as an unlogged transaction. */
        const val BALANCE_DISCREPANCY_THRESHOLD = 1.0

        /** Backup must never be able to outlive the broadcast that triggered it. */
        private const val BACKUP_TIMEOUT_MS = 5_000L

        /**
         * Serialises the realtime receiver against the catch-up scanner. Class-level on purpose: the
         * two callers construct their own processor instances, and without a shared lock they
         * interleave on the same message — the race behind root cause #9.
         */
        private val mutex = Mutex()
    }

    /**
     * Processes one message end to end.
     *
     * @param rawBody the body exactly as the platform delivered it, untrimmed. The legacy cache keys
     *   were computed from this string, so trimming it here would silently break upgrade recognition.
     */
    suspend fun process(
        senderHeader: String,
        rawBody: String,
        timestamp: Long,
        origin: SmsOrigin
    ): ProcessingOutcome = mutex.withLock {
        var amount: Double? = null
        val outcome = try {
            processLocked(senderHeader, rawBody, timestamp, origin) { amount = it }
        } catch (e: Exception) {
            // Design principle 4: a throw is an outcome, not a silent return.
            ProcessingOutcome.Failed(e)
        }

        // Diagnostics last, at the one place every path converges. Recording here rather than at each
        // exit means all nine outcomes are covered by construction — no future early return can forget
        // to log — and it keeps the write after the notification attempt (design principle 3). Wrapped
        // in runCatching because a witness must never be able to change what it witnessed.
        runCatching {
            diagnostics.record(outcome.toDiagnosticEntry(senderHeader, amount, timestamp, origin))
        }
        outcome
    }

    private suspend fun processLocked(
        senderHeader: String,
        rawBody: String,
        timestamp: Long,
        origin: SmsOrigin,
        onAmountParsed: (Double) -> Unit
    ): ProcessingOutcome {
        // ── Stage 1: parse ────────────────────────────────────────────────
        val parsed = SmsBankParser.parse(senderHeader, rawBody)
            ?: return ProcessingOutcome.NotATransaction
        onAmountParsed(parsed.amount)

        val identity = SmsHash.canonical(parsed, senderHeader)
        val legacyKeys = arrayOf(
            SmsHash.legacyBodyKey(senderHeader, parsed.amount, parsed.type, rawBody),
            SmsHash.legacyTimestampKey(senderHeader, parsed.amount, parsed.type, timestamp)
        )

        // ── Stage 2: account resolution — advisory, never a drop ──────────
        // The old code returned here when nothing matched, which is how a correctly parsed message
        // from a bank the user had not linked precisely enough disappeared without trace.
        val accounts = accountDao.getAllSync()
        val matchedAccount = resolveAccount(accounts, parsed, senderHeader)

        // ── Stage 3: cache peek (INV-1) ───────────────────────────────────
        // Legacy keys are consulted so an install upgrading from v3.2.0 recognises messages the old
        // pipeline already handled, instead of re-notifying its whole catch-up window once.
        if (cache.peek(identity) || legacyKeys.any { cache.peek(it) }) {
            return ProcessingOutcome.Suppressed(SuppressReason.CACHE_HIT)
        }

        // ── Stages 4-6: gather positive findings, then decide ─────────────
        val reconciliationKey = SmsHash.reconciliationKey(
            parsed.amount, parsed.type, parsed.accountLast4 ?: matchedAccount?.accountLast4, timestamp
        )
        val decision = decide(parsed, buildContext(parsed, matchedAccount, accounts, origin, timestamp, reconciliationKey))

        if (decision is SmsDecision.Suppress) {
            // The reconciliation marker is a one-shot handshake: consuming it *is* an authoritative
            // state change, which is why this single cache write is allowed outside the post-commit
            // tail (INV-7).
            if (decision.reason == SuppressReason.RECONCILIATION_MARKER) cache.forget(reconciliationKey)
            return ProcessingOutcome.Suppressed(decision.reason)
        }

        // ── Stages 7-8: the atomic transition (INV-2, INV-3) ──────────────
        val claim = db.inTransaction {
            claimAndMaybeMerge(parsed, identity, decision, accounts, origin, timestamp)
        }

        // ── COMMITTED. Notification attempt is the very next thing. ───────
        val outcome = when (claim) {
            is Claim.Duplicate -> return ProcessingOutcome.Duplicate(identity)

            is Claim.Merged -> {
                notifier.postAutoMerge(claim.transactionId, claim.fromAccount, claim.toAccount, parsed.amount)
                ProcessingOutcome.Merged(claim.transactionId, claim.fromAccount, claim.toAccount)
            }

            is Claim.Claimed -> {
                val posted = try {
                    notifier.postBankTransaction(claim.row)
                } catch (e: Exception) {
                    false
                }
                if (posted) {
                    pendingDao.markNotified(claim.row.id, clock.nowMillis())
                    ProcessingOutcome.Notified(claim.row.id)
                } else {
                    pendingDao.markNotificationAttempted(claim.row.id)
                    ProcessingOutcome.ClaimedNotNotified(claim.row.id, "notifier declined or threw")
                }
            }
        }

        // ── INV-7: cache writes follow the authority ──────────────────────
        // Only reachable once a claim has committed. Duplicate and Suppressed exits above return
        // before this line, so a rolled-back or crashed claim leaves the cache untouched and a
        // redelivery still reaches Room.
        cache.mark(identity, *legacyKeys)

        // ── Non-essential tail. Nothing here can cost the notification. ───
        runCatching { syncAccountBalance(parsed, matchedAccount, timestamp) }
        withTimeoutOrNull(BACKUP_TIMEOUT_MS) { runCatching { backup.perform() } }

        return outcome
    }

    // ── Stage 7/8: claim, cross-table check, auto-merge ───────────────────

    private sealed interface Claim {
        data object Duplicate : Claim
        data class Claimed(val row: PendingTransactionEntity) : Claim
        data class Merged(val transactionId: Long, val fromAccount: String, val toAccount: String) : Claim
    }

    /**
     * Runs inside one transaction. Claims the identity, then either merges or keeps the claim.
     *
     * Doing the merge here rather than in a second transaction removes the window in which the
     * opposite row could vanish between lookup and consumption.
     */
    private suspend fun claimAndMaybeMerge(
        parsed: ParsedBankSms,
        identity: String,
        decision: SmsDecision,
        accounts: List<AccountEntity>,
        origin: SmsOrigin,
        timestamp: Long
    ): Claim {
        val row = PendingTransactionEntity(
            amount = parsed.amount,
            type = parsed.type,
            bankName = parsed.bankName,
            accountLast4 = parsed.accountLast4,
            merchantOrPayee = parsed.merchantOrPayee,
            endingBalance = parsed.endingBalance,
            rawSmsBody = parsed.rawBody,
            senderHeader = parsed.senderHeader,
            timestamp = timestamp,
            sourceMessageHash = identity,
            source = origin.toSource().name,
            confidence = TransactionConfidence.CONFIRMED.name,
            referenceNumber = parsed.referenceNumber
        )

        // INV-2: the one authoritative dedup operation. -1 from the UNIQUE index is the only proof of
        // duplication this pipeline acts on.
        val claimedId = pendingDao.insertClaim(row)
        if (claimedId == -1L) return Claim.Duplicate

        // Cross-table hole: a message already approved into history must not be re-claimable.
        if (transactionDao.getBySourceHash(identity) != null) {
            pendingDao.deleteById(claimedId)
            return Claim.Duplicate
        }

        val claimed = row.copy(id = claimedId)
        if (decision !is SmsDecision.AutoMerge) return Claim.Claimed(claimed)

        // Consume the opposite row. A count of 0 means a concurrent path already took it — a lost
        // race. Rather than exiting empty-handed, keep our own claim and notify normally: fail-open
        // prefers one honest pending row over a silent drop or a half-built transfer.
        if (pendingDao.deleteById(decision.oppositePendingId) == 0) return Claim.Claimed(claimed)

        val debitBank = if (parsed.type == "DEBIT") parsed.bankName else decision.oppositeBankName
        val creditBank = if (parsed.type == "CREDIT") parsed.bankName else decision.oppositeBankName

        val sourceAccount = accounts.firstOrNull { BankAliasRegistry.matches(it.bankCode, debitBank) }
            ?: accounts.firstOrNull { it.isPrimary } ?: accounts.firstOrNull()
        val targetAccount = accounts.firstOrNull { BankAliasRegistry.matches(it.bankCode, creditBank) }
            ?: accounts.firstOrNull { it.name.contains("Savings", ignoreCase = true) }
            ?: accounts.lastOrNull()

        val fromName = sourceAccount?.name ?: debitBank
        val toName = targetAccount?.name ?: creditBank

        val categoryId = categoryDao.getAllSync()
            .firstOrNull { it.type == TransactionType.TRANSFER }?.id ?: 1L

        // insert() is REPLACE, which would delete an incumbent row on a hash conflict — safe only
        // because getBySourceHash above already proved no transaction holds this identity.
        val transactionId = transactionDao.insert(
            TransactionEntity(
                amount = parsed.amount,
                type = TransactionType.TRANSFER,
                categoryId = categoryId,
                date = clock.today(),
                account = fromName,
                toAccount = toName,
                notes = "Auto-merged inter-account transfer ($debitBank -> $creditBank)",
                sourceMessageHash = identity,
                source = origin.toSource().name,
                confidence = TransactionConfidence.CONFIRMED.name,
                referenceNumber = parsed.referenceNumber
            )
        )

        // Our own claim has served its purpose; the TRANSFER now carries the identity.
        pendingDao.deleteById(claimedId)
        return Claim.Merged(transactionId, fromName, toName)
    }

    // ── Stage 9: balance sync ─────────────────────────────────────────────

    /**
     * Keeps the app's idea of the account balance aligned with the bank's, and reports the gap.
     *
     * Runs on **every** origin and on the merge path too. Previously only the plain realtime path
     * updated `lastKnownBalance`, so an auto-merged transfer left it stale and the *next* SMS
     * computed a discrepancy that did not exist — a false "missed transaction" alert.
     *
     * The expected balance nets **every transaction the user logged by hand against this account since
     * the last balance reading**, not just the current SMS. Without that netting the detector accused
     * the user of unlogged activity for activity they had logged themselves: a ₹200 card payment
     * entered by hand between two BOB messages left the second message's arithmetic short by exactly
     * ₹200, and the app raised a ₹200 "missed transaction" for a transaction sitting in its own
     * database. Rows derived from a bank message are excluded — see
     * [TransactionDao.getUserEnteredForAccountBetween] for why netting those would double-count.
     *
     * Netting can only ever *shrink* a discrepancy, so it trades a class of false accusations for the
     * possibility of staying quiet about a real gap. For an inference shown to the user as a
     * suggestion, that is the right direction.
     */
    private suspend fun syncAccountBalance(
        parsed: ParsedBankSms,
        matchedAccount: AccountEntity?,
        timestamp: Long
    ) {
        val endingBalance = parsed.endingBalance ?: return
        val accountId = matchedAccount?.id ?: return
        val account = accountDao.getAllSync().firstOrNull { it.id == accountId } ?: return

        // Monotonic guard: an SMS older than the balance we already hold carries a stale figure.
        // Applying it would walk the balance backwards and manufacture a discrepancy.
        val lastStamp = account.lastBalanceTimestamp
        if (lastStamp != null && timestamp <= lastStamp) return

        val previousBalance = account.lastKnownBalance
        // A balance with no timestamp has no window to net over, so its age is unknown — it could be
        // months stale, and inferring from it is how false alerts get manufactured. The reading is
        // still refreshed below; only the inference is skipped. In practice this coincides with
        // `previousBalance == null`, since this method is the only writer of either column and always
        // writes both.
        if (previousBalance != null && previousBalance > 0 && lastStamp != null) {
            val net = netUserEnteredActivity(account.name, lastStamp, timestamp)
            val signedThisSms = if (parsed.type == "DEBIT") -parsed.amount else parsed.amount
            val expected = previousBalance + net + signedThisSms
            val discrepancy = abs(expected - endingBalance)
            if (discrepancy >= BALANCE_DISCREPANCY_THRESHOLD) {
                reportUnloggedActivity(parsed, account, expected, endingBalance, discrepancy, timestamp)
            }
        }

        accountDao.update(
            account.copy(lastKnownBalance = endingBalance, lastBalanceTimestamp = timestamp)
        )
    }

    /**
     * Signed sum of what the user told us moved through [accountName] in `(since, until]`.
     *
     * Money leaving the account is negative, money arriving positive; a TRANSFER counts once per leg,
     * so a transfer between two of the user's own accounts that names this account on both sides nets
     * to zero — which is correct, the balance did not move.
     */
    private suspend fun netUserEnteredActivity(
        accountName: String,
        since: Long,
        until: Long
    ): Double = transactionDao
        .getUserEnteredForAccountBetween(accountName, since, until)
        .sumOf { row ->
            when (row.type) {
                TransactionType.EXPENSE -> if (row.account == accountName) -row.amount else 0.0
                TransactionType.INCOME -> if (row.account == accountName) row.amount else 0.0
                TransactionType.TRANSFER -> {
                    val out = if (row.account == accountName) -row.amount else 0.0
                    val into = if (row.toAccount == accountName) row.amount else 0.0
                    out + into
                }
            }
        }

    /**
     * Records the balance gap as an INFERRED pending row and alerts the user.
     *
     * `sourceMessageHash` stays null: no message was claimed here, and inventing an identity for a
     * transaction the bank never told us about would let it collide with a real one later.
     */
    private suspend fun reportUnloggedActivity(
        parsed: ParsedBankSms,
        account: AccountEntity,
        expectedBalance: Double,
        actualBalance: Double,
        discrepancy: Double,
        timestamp: Long
    ) {
        val amount = round(discrepancy * 100.0) / 100.0
        val accountLast4 = parsed.accountLast4 ?: account.accountLast4

        // Repeated detections of the same gap must not stack up duplicate rows.
        if (pendingDao.findInferredMarker(amount, accountLast4) != null) return

        val marker = PendingTransactionEntity(
            amount = amount,
            type = if (actualBalance < expectedBalance) "DEBIT" else "CREDIT",
            bankName = parsed.bankName,
            accountLast4 = accountLast4,
            merchantOrPayee = "Unlogged activity (balance sync)",
            endingBalance = actualBalance,
            rawSmsBody = "Auto-detected unlogged activity via balance discrepancy " +
                "(expected ₹$expectedBalance, bank reported ₹$actualBalance)",
            senderHeader = parsed.senderHeader,
            timestamp = timestamp - 1000,
            sourceMessageHash = null,
            source = TransactionSource.BALANCE_RECONCILIATION.name,
            confidence = TransactionConfidence.INFERRED.name
        )
        val markerId = pendingDao.insert(marker)
        notifier.postMissedTransaction(marker.copy(id = markerId), actualBalance, expectedBalance)
    }

    // ── Recovery ──────────────────────────────────────────────────────────

    /**
     * Re-posts Stage-1 notifications for claims that committed but never notified — the crash, kill,
     * or permission-denied case. Without this, INV-4's "bounded notification attempt" would be a
     * single attempt, and a process death between commit and post would lose the alert permanently
     * even though the row is safely in the database.
     *
     * Deliberately independent of READ_SMS and of the catch-up scanner, so it works on an install
     * that only ever granted RECEIVE_SMS. Notification ids are derived from the row id, so a retry
     * replaces any earlier notification rather than stacking a second one.
     *
     * Bounded by INV-6: past 48 h or 5 attempts a row stops retrying but stays valid and visible for
     * manual action — no retry loops, no alerts surfacing months later.
     *
     * @return ids of rows notified on this pass.
     */
    suspend fun recoverUnnotified(): List<Long> {
        val cutoff = clock.nowMillis() - NOTIFICATION_RECOVERY_WINDOW_MS
        val recovered = mutableListOf<Long>()
        for (row in pendingDao.getUnnotified(cutoff, MAX_NOTIFICATION_ATTEMPTS)) {
            val posted = try {
                notifier.postBankTransaction(row)
            } catch (e: Exception) {
                false
            }
            if (posted) {
                pendingDao.markNotified(row.id, clock.nowMillis())
                recovered += row.id
            } else {
                pendingDao.markNotificationAttempted(row.id)
            }
            // A recovery pass is exactly the situation the log exists for: it says a claim survived
            // without its notification, and whether the retry got through.
            runCatching {
                diagnostics.record(
                    SmsDiagnosticEntry(
                        timestamp = row.timestamp,
                        sender = row.senderHeader ?: row.bankName,
                        amount = row.amount,
                        origin = row.source ?: "UNKNOWN",
                        outcome = if (posted) "RECOVERED" else "RECOVERY_FAILED",
                        reason = "pending #${row.id}, attempt ${row.notificationAttempts + 1}"
                    )
                )
            }
        }
        return recovered
    }

    // ── Context gathering ─────────────────────────────────────────────────

    private suspend fun buildContext(
        parsed: ParsedBankSms,
        matchedAccount: AccountEntity?,
        accounts: List<AccountEntity>,
        origin: SmsOrigin,
        timestamp: Long,
        reconciliationKey: String
    ): SmsDecisionContext {
        val txType = if (parsed.type == "CREDIT") TransactionType.INCOME else TransactionType.EXPENSE

        // Scoped to source = 'MANUAL' OR NULL by the DAO. Unscoped, this matched rows the pipeline
        // itself had auto-logged, so every second same-amount SMS was suppressed as a "manual
        // duplicate" — the engine poisoned its own dedup (root cause #4).
        val targetAccount = (matchedAccount ?: accounts.firstOrNull {
            BankAliasRegistry.matches(it.bankCode, parsed.bankName, parsed.senderHeader)
        })
        val manualMatch = transactionDao.findMatchingManualTransaction(
            amount = parsed.amount,
            type = txType.name,
            minDate = clock.today().minusDays(MANUAL_MATCH_LOOKBACK_DAYS),
            account = targetAccount?.name
        )

        val oppositeType = if (parsed.type == "DEBIT") "CREDIT" else "DEBIT"
        val opposite = pendingDao.findMatchingOppositePending(
            parsed.amount, oppositeType, timestamp - AUTO_MERGE_WINDOW_MS
        )

        var proximityMatch = false
        var balanceReconciled = false
        if (origin == SmsOrigin.CATCHUP) {
            proximityMatch = hasProximityMatch(parsed, timestamp)
            balanceReconciled = hasBalanceReconciled(parsed, targetAccount)
        }

        return SmsDecisionContext(
            origin = origin,
            cacheSaysSeen = false, // already checked; the claim is the authority from here on
            manualMatchAccountName = manualMatch?.account,
            targetAccountName = targetAccount?.name,
            reconciliationMarkerPresent = cache.peek(reconciliationKey),
            oppositePendingId = opposite?.id,
            oppositePendingBankName = opposite?.bankName,
            catchUpProximityMatch = proximityMatch,
            catchUpBalanceReconciled = balanceReconciled
        )
    }

    /** Catch-up tier 2: an existing pending row or logged transaction within ±30 min. */
    private suspend fun hasProximityMatch(parsed: ParsedBankSms, timestamp: Long): Boolean {
        val pendingNearby = pendingDao.getAllPendingSync().any {
            it.amount == parsed.amount &&
                it.type.equals(parsed.type, ignoreCase = true) &&
                abs(it.timestamp - timestamp) <= CATCHUP_PROXIMITY_MS
        }
        if (pendingNearby) return true

        val expectedType = if (parsed.type == "CREDIT") TransactionType.INCOME else TransactionType.EXPENSE
        return transactionDao.getTransactionsBetweenSync(
            clock.today().minusDays(3), clock.today()
        ).any {
            it.amount == parsed.amount &&
                (it.type == expectedType || it.type == TransactionType.TRANSFER) &&
                abs(it.createdAt - timestamp) <= CATCHUP_PROXIMITY_MS
        }
    }

    /** Catch-up tier 3: the account's recent net already equals the balance the SMS reports. */
    private suspend fun hasBalanceReconciled(parsed: ParsedBankSms, account: AccountEntity?): Boolean {
        val endingBalance = parsed.endingBalance ?: return false
        val name = account?.name ?: return false
        val recent = transactionDao.getTransactionsBetweenSync(clock.today().minusDays(3), clock.today())
            .filter { it.account == name || it.toAccount == name }
        val income = recent
            .filter { it.type == TransactionType.INCOME || (it.type == TransactionType.TRANSFER && it.toAccount == name) }
            .sumOf { it.amount }
        val expense = recent
            .filter { it.type == TransactionType.EXPENSE || (it.type == TransactionType.TRANSFER && it.account == name) }
            .sumOf { it.amount }
        return abs((income - expense) - endingBalance) < BALANCE_DISCREPANCY_THRESHOLD
    }

    /**
     * Resolves the linked account this SMS belongs to, or null.
     *
     * Advisory only. A null must never end processing — that asymmetry (zero linked accounts sailed
     * through, one non-matching linked account dropped the message) is root cause #2.
     */
    private fun resolveAccount(
        accounts: List<AccountEntity>,
        parsed: ParsedBankSms,
        senderHeader: String
    ): AccountEntity? = accounts.firstOrNull { account ->
        BankAliasRegistry.matches(account.bankCode, parsed.bankName, senderHeader) ||
            (account.accountLast4 != null && parsed.accountLast4 != null &&
                account.accountLast4 == parsed.accountLast4)
    } ?: accounts.singleOrNull()

    private fun SmsOrigin.toSource(): TransactionSource = when (this) {
        SmsOrigin.REALTIME -> TransactionSource.SMS_REALTIME
        SmsOrigin.CATCHUP -> TransactionSource.SMS_CATCHUP
    }

    /**
     * Flattens an outcome into the log's two text columns.
     *
     * The reason is the part worth having: "SUPPRESSED" alone would repeat the original complaint, and
     * `SUPPRESSED / MANUAL_DUPLICATE` names the gate to go and read.
     */
    private fun ProcessingOutcome.toDiagnosticEntry(
        senderHeader: String,
        amount: Double?,
        timestamp: Long,
        origin: SmsOrigin
    ): SmsDiagnosticEntry {
        val (outcome, reason) = when (this) {
            is ProcessingOutcome.Notified -> "NOTIFIED" to "pending #$pendingId"
            is ProcessingOutcome.ClaimedNotNotified -> "CLAIMED_NOT_NOTIFIED" to "pending #$pendingId: $reason"
            is ProcessingOutcome.Merged -> "MERGED" to "$fromAccount -> $toAccount"
            is ProcessingOutcome.Duplicate -> "DUPLICATE" to "identity already claimed"
            is ProcessingOutcome.Suppressed -> "SUPPRESSED" to reason.name
            ProcessingOutcome.NotATransaction -> "NOT_A_TRANSACTION" to null
            is ProcessingOutcome.Failed -> "FAILED" to
                (error.message?.let { "${error.javaClass.simpleName}: $it" } ?: error.javaClass.simpleName)
        }
        return SmsDiagnosticEntry(
            timestamp = timestamp,
            sender = senderHeader,
            amount = amount,
            origin = origin.name,
            outcome = outcome,
            reason = reason
        )
    }
}
