package com.example.hisab.data.sms

import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.db.entity.PendingTransactionEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionConfidence
import com.example.hisab.data.model.TransactionSource
import com.example.hisab.data.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The regression suite for the missing-notification defect (`issues.txt:1`).
 *
 * The parser was never the problem — `SmsBankParserTest` proves all three reported messages parse
 * identically, yet only one of them notified. Everything that actually went wrong lived downstream:
 * ordering, atomicity, and a dedup scheme that suppressed genuine transactions. Those are exactly the
 * things these tests pin down, using the JVM fakes in `SmsPipelineFakes.kt`.
 *
 * Each test names the invariant or root cause it defends, so a future failure says *what broke*
 * rather than just *where*.
 */
class TransactionProcessorTest {

    private val journal = mutableListOf<String>()
    private val cache = FakeSmsHashCache(journal)
    private val db = FakeAtomicDb(journal)
    private val notifier = FakeSmsNotifier(journal)
    private val backup = FakeBackupTrigger(journal)
    private val pendingDao = FakePendingTransactionDao(journal)
    private val transactionDao = FakeTransactionDao()
    private val categoryDao = FakeCategoryDao()
    private var accountDao = FakeAccountDao()
    private val diagnostics = FakeSmsDiagnosticsLog(journal)

    private val clock = SmsClock(nowMillis = { NOW }, today = { TODAY })

    private fun processorWith(vararg accounts: AccountEntity): TransactionProcessor {
        accountDao = FakeAccountDao(accounts.toList())
        return TransactionProcessor(
            pendingDao = pendingDao,
            transactionDao = transactionDao,
            accountDao = accountDao,
            categoryDao = categoryDao,
            cache = cache,
            db = db,
            notifier = notifier,
            backup = backup,
            clock = clock,
            diagnostics = diagnostics
        )
    }

    // ── The ordering contract (design principle 3, INV-7) ─────────────────

    /**
     * The canonical order, asserted as a whole sequence rather than as a set of "did it happen"
     * checks. The shipped receiver ran a full serialise-and-write auto-backup *between* the commit and
     * the notification, inside a ~10 s `goAsync` budget — root cause #6, and the reason an SMS could be
     * logged with no notification at all.
     *
     * Zero linked accounts on purpose: that is also the fail-open case (design principle 1).
     */
    @Test
    fun happyPath_ordersClaimCommitNotifyMarkBackup_andNotifiesWithNoLinkedAccounts() = runBlocking {
        val outcome = processorWith().process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertTrue("no linked account must never suppress: $outcome", outcome is ProcessingOutcome.Notified)
        assertEquals(
            listOf("tx-begin", "claim", "commit", "notify", "mark", "backup", "diag"),
            journal
        )
        assertEquals(1, notifier.bankPosts.size)
        assertEquals(1, backup.performCalls)
    }

    /**
     * Root cause #2: the gate was asymmetric — zero linked accounts sailed through, but one linked
     * account that failed to match dropped the message in silence. Two non-matching accounts here so
     * `resolveAccount`'s single-account fallback cannot mask the regression.
     */
    @Test
    fun linkedButNonMatchingAccounts_stillNotify() = runBlocking {
        val outcome = processorWith(
            AccountEntity(name = "HDFC Salary", bankCode = "HDFC", accountLast4 = "1111", isPrimary = true),
            AccountEntity(name = "ICICI Savings", bankCode = "ICICI", accountLast4 = "2222")
        ).process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertTrue("unmatched account is not evidence of a duplicate: $outcome", outcome is ProcessingOutcome.Notified)
        assertEquals(1, notifier.bankPosts.size)
    }

    /**
     * The reported trio, asserted **together** so the ₹40 and ₹45 cases can never diverge again.
     *
     * The balance chain is realistic (855.43 → 810.43 → 840.43), which doubles as a check that the
     * discrepancy detector stays quiet when the maths adds up: a false "missed transaction" alert on
     * every message would be its own bug.
     */
    @Test
    fun reportedTrio_allThreeNotify_andNoFalseMissedTransactionAlerts() = runBlocking {
        val processor = processorWith(bobAccount())

        val first = processor.process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)
        val second = processor.process(BOB_HEADER, BOB_45_DEBIT, NOW + 1_000, SmsOrigin.REALTIME)
        val third = processor.process(BOB_HEADER, BOB_30_CREDIT, NOW + 2_000, SmsOrigin.REALTIME)

        assertTrue("₹40 debit: $first", first is ProcessingOutcome.Notified)
        assertTrue("₹45 debit (the reported drop): $second", second is ProcessingOutcome.Notified)
        assertTrue("₹30 credit (the reported drop): $third", third is ProcessingOutcome.Notified)
        assertEquals(3, notifier.bankPosts.size)
        assertEquals(3, pendingDao.rows.size)
        assertEquals("balance chain reconciles; nothing to report", 0, notifier.missedPosts.size)
    }

    // ── Dedup authority (INV-1, INV-2) ────────────────────────────────────

    @Test
    fun immediateRedelivery_isSuppressedByTheCache() = runBlocking {
        val processor = processorWith(bobAccount())
        processor.process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        val outcome = processor.process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertEquals(ProcessingOutcome.Suppressed(SuppressReason.CACHE_HIT), outcome)
        assertEquals(1, pendingDao.rows.size)
    }

    /**
     * INV-1 + INV-2 together: the cache is a performance shortcut, never the authority. Losing it must
     * cost extra queries and nothing else — Room's UNIQUE index still proves the duplicate.
     */
    @Test
    fun cacheIsNotAuthority_roomStillProvesTheDuplicate() = runBlocking {
        val processor = processorWith(bobAccount())
        assertTrue(processor.process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME) is ProcessingOutcome.Notified)

        cache.keys.clear()
        val outcome = processor.process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertTrue("only the UNIQUE conflict may prove duplication: $outcome", outcome is ProcessingOutcome.Duplicate)
        assertEquals("second claim must not create a row", 1, pendingDao.rows.size)
        assertEquals(1, notifier.bankPosts.size)
    }

    /**
     * INV-7, the one that makes a lost message impossible: a `mark()` before a committed claim would
     * leave the cache saying "seen" for a message that was never persisted, and since `peek() == true`
     * is by definition a safe skip, INV-1 could not rescue it. The message would be gone forever.
     */
    @Test
    fun claimThatThrows_neverMarksTheCache_andRedeliveryReachesTheClaimAgain() = runBlocking {
        val processor = processorWith(bobAccount())
        pendingDao.throwOnClaim = true

        val failed = processor.process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertTrue("a throw is an outcome, not a silent return: $failed", failed is ProcessingOutcome.Failed)
        assertEquals("cache must not lead the authority", 0, cache.markCalls)
        assertTrue("nothing committed", !journal.contains("commit"))
        assertEquals(0, pendingDao.rows.size)

        pendingDao.throwOnClaim = false
        val retried = processor.process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertTrue("redelivery must still reach Room: $retried", retried is ProcessingOutcome.Notified)
        assertEquals(2, pendingDao.claimCalls)
        assertEquals(1, notifier.bankPosts.size)
    }

    /**
     * The cross-table hole: once a message has been materialised into history (dashboard approve,
     * notification action, or a merge), it must not be re-claimable as a fresh pending row.
     */
    @Test
    fun identityAlreadyInHistory_isDuplicate_andTheClaimedRowIsRolledBack() = runBlocking {
        val identity = identityOf(BOB_HEADER, BOB_40_DEBIT)
        transactionDao.seed(
            TransactionEntity(
                amount = 40.0, type = TransactionType.EXPENSE, categoryId = 1, date = TODAY,
                account = "BOB Current", sourceMessageHash = identity,
                source = TransactionSource.SMS_REALTIME.name
            )
        )

        val outcome = processorWith(bobAccount()).process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertEquals(ProcessingOutcome.Duplicate(identity), outcome)
        assertEquals("the claimed row must not survive", 0, pendingDao.rows.size)
        assertEquals(0, notifier.bankPosts.size)
        assertEquals("a duplicate exit must not mark the cache", 0, cache.markCalls)
    }

    // ── Notification is an attempt, not a promise (INV-4, INV-6) ───────────

    @Test
    fun notifierDeclines_rowSurvivesForRecovery() = runBlocking {
        notifier.accept = false

        val outcome = processorWith(bobAccount()).process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertTrue("POST_NOTIFICATIONS denied is retryable, not terminal: $outcome",
            outcome is ProcessingOutcome.ClaimedNotNotified)
        val row = pendingDao.rows.values.single()
        assertNull("postedAt only ever means post() returned", row.notificationPostedAt)
        assertEquals(1, row.notificationAttempts)
    }

    @Test
    fun notifierThrows_rowStillSurvivesAndCountsTheAttempt() = runBlocking {
        notifier.throwOnPost = true

        val outcome = processorWith(bobAccount()).process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertTrue("$outcome", outcome is ProcessingOutcome.ClaimedNotNotified)
        assertEquals(1, pendingDao.rows.values.single().notificationAttempts)
    }

    /** Root cause #6, from the other side: the tail must never be able to cost the notification. */
    @Test
    fun backupThatThrows_cannotUndoTheNotification() = runBlocking {
        backup.throwOnPerform = true

        val outcome = processorWith(bobAccount()).process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertTrue("$outcome", outcome is ProcessingOutcome.Notified)
        assertEquals(1, notifier.bankPosts.size)
        assertTrue(journal.indexOf("notify") < journal.indexOf("backup"))
    }

    // ── Recovery (INV-4's "bounded attempt", INV-6's bounds) ───────────────

    /**
     * The crash-after-claim case. Without recovery, INV-4's *bounded* notification attempt would be a
     * single attempt, and a process death between commit and post would lose the alert permanently
     * even though the row sits safely in the database.
     */
    @Test
    fun recoverUnnotified_postsOncePerRow_thenStaysQuiet() = runBlocking {
        val processor = processorWith()
        seedUnnotified(timestamp = NOW - 60_000)

        assertEquals(1, processor.recoverUnnotified().size)
        assertEquals(1, notifier.bankPosts.size)
        assertNotNull(pendingDao.rows.values.single().notificationPostedAt)

        assertEquals("a recovered row must not be re-posted", 0, processor.recoverUnnotified().size)
        assertEquals(1, notifier.bankPosts.size)
    }

    /** INV-6: past either bound a row stops retrying, but stays valid and dashboard-visible. */
    @Test
    fun recoverUnnotified_respectsAgeAndAttemptCaps_butKeepsTheRows() = runBlocking {
        val processor = processorWith()
        seedUnnotified(timestamp = NOW - 49L * 60 * 60 * 1000)
        seedUnnotified(
            timestamp = NOW - 60_000,
            attempts = TransactionProcessor.MAX_NOTIFICATION_ATTEMPTS
        )

        assertEquals(0, processor.recoverUnnotified().size)
        assertEquals(0, notifier.bankPosts.size)
        assertEquals("no retry loops, but no data loss either", 2, pendingDao.rows.size)
    }

    @Test
    fun recoverUnnotified_failedPost_incrementsAttemptsOnly() = runBlocking {
        val processor = processorWith()
        seedUnnotified(timestamp = NOW - 60_000)
        notifier.accept = false

        assertEquals(0, processor.recoverUnnotified().size)
        val row = pendingDao.rows.values.single()
        assertNull(row.notificationPostedAt)
        assertEquals("drives the INV-6 cap", 1, row.notificationAttempts)
    }

    // ── Manual reconciliation, scoped (root cause #4) ──────────────────────

    @Test
    fun handEnteredTransactionOnTheSameAccount_suppresses() = runBlocking {
        seedTransaction(amount = 45.0, source = TransactionSource.MANUAL.name)

        val outcome = processorWith(bobAccount()).process(BOB_HEADER, BOB_45_DEBIT, NOW, SmsOrigin.REALTIME)

        assertEquals(ProcessingOutcome.Suppressed(SuppressReason.MANUAL_MATCH), outcome)
        assertEquals(0, pendingDao.rows.size)
        assertEquals(0, notifier.bankPosts.size)
        assertEquals("a suppression must not burn the identity (INV-7)", 0, cache.markCalls)
    }

    /**
     * Root cause #4, the worst of the nine: the reconciliation query was not scoped to hand-entered
     * rows, so it matched transactions the pipeline itself had auto-logged. Every second same-amount
     * SMS on an account was suppressed as a "manual duplicate" — the engine poisoned its own dedup.
     */
    @Test
    fun previouslyAutoLoggedTransaction_neverSuppressesTheNextSms() = runBlocking {
        seedTransaction(amount = 45.0, source = TransactionSource.SMS_REALTIME.name)

        val outcome = processorWith(bobAccount()).process(BOB_HEADER, BOB_45_DEBIT, NOW, SmsOrigin.REALTIME)

        assertTrue("auto-logged history is not a manual entry: $outcome", outcome is ProcessingOutcome.Notified)
        assertEquals(1, notifier.bankPosts.size)
    }

    // ── Auto-merge: criteria at strict parity, false positives pinned ──────
    //
    // This is the one way the hardening could make things *worse*. Fixing duplicate and missing
    // logging must not start inventing transfers, so the criteria are carried over unchanged and these
    // tests exist to keep them that way. A failure here is a merge-criteria bug to investigate, never
    // a test to relax.

    @Test
    fun autoMerge_genuineCrossBankTransfer_becomesOneTransfer() = runBlocking {
        val processor = processorWith(bobAccount(), sbiAccount())
        seedPending(amount = 500.0, type = "CREDIT", bank = "State Bank of India", at = NOW - 30_000)

        val outcome = processor.process(BOB_HEADER, bobDebit("500.00", "1000.00"), NOW, SmsOrigin.REALTIME)

        assertTrue("$outcome", outcome is ProcessingOutcome.Merged)
        val transfer = transactionDao.rows.values.single()
        assertEquals(TransactionType.TRANSFER, transfer.type)
        assertEquals(500.0, transfer.amount, 0.001)
        assertEquals("BOB Current", transfer.account)
        assertEquals("SBI Savings", transfer.toAccount)
        assertEquals(identityOf(BOB_HEADER, bobDebit("500.00", "1000.00")), transfer.sourceMessageHash)
        assertEquals("both halves are consumed", 0, pendingDao.rows.size)
        assertEquals(1, notifier.mergePosts.size)
        // The shipped merge path never touched lastKnownBalance, so the *next* SMS computed a
        // discrepancy that did not exist and raised a false "missed transaction".
        assertTrue("merge must still sync the balance", accountDao.updates.isNotEmpty())
    }

    @Test
    fun autoMerge_sameBank_doesNotMerge() = runBlocking {
        val processor = processorWith(bobAccount(), sbiAccount())
        seedPending(amount = 500.0, type = "CREDIT", bank = "Bank of Baroda", at = NOW - 30_000)

        val outcome = processor.process(BOB_HEADER, bobDebit("500.00", "1000.00"), NOW, SmsOrigin.REALTIME)

        assertTrue("$outcome", outcome is ProcessingOutcome.Notified)
        assertEquals(0, transactionDao.rows.size)
        assertEquals(2, pendingDao.rows.size)
    }

    @Test
    fun autoMerge_outsideTheWindow_doesNotMerge() = runBlocking {
        val processor = processorWith(bobAccount(), sbiAccount())
        seedPending(
            amount = 500.0, type = "CREDIT", bank = "State Bank of India",
            at = NOW - TransactionProcessor.AUTO_MERGE_WINDOW_MS - 80_000
        )

        val outcome = processor.process(BOB_HEADER, bobDebit("500.00", "1000.00"), NOW, SmsOrigin.REALTIME)

        assertTrue("$outcome", outcome is ProcessingOutcome.Notified)
        assertEquals(0, transactionDao.rows.size)
        assertEquals(2, pendingDao.rows.size)
    }

    @Test
    fun autoMerge_sameDirection_doesNotMerge() = runBlocking {
        val processor = processorWith(bobAccount(), sbiAccount())
        seedPending(amount = 500.0, type = "DEBIT", bank = "State Bank of India", at = NOW - 30_000)

        val outcome = processor.process(BOB_HEADER, bobDebit("500.00", "1000.00"), NOW, SmsOrigin.REALTIME)

        assertTrue("two debits are not a transfer: $outcome", outcome is ProcessingOutcome.Notified)
        assertEquals(0, transactionDao.rows.size)
    }

    /** A ₹5,000 refund and an unrelated ₹5,000 debit ten minutes apart must stay two rows. */
    @Test
    fun autoMerge_unrelatedSameAmountPairTenMinutesApart_staysTwoRows() = runBlocking {
        val processor = processorWith(bobAccount(), sbiAccount())
        seedPending(amount = 5000.0, type = "CREDIT", bank = "State Bank of India", at = NOW - 600_000)

        val outcome = processor.process(BOB_HEADER, bobDebit("5000.00", "6000.00"), NOW, SmsOrigin.REALTIME)

        assertTrue("$outcome", outcome is ProcessingOutcome.Notified)
        assertEquals("no invented transfer", 0, transactionDao.rows.size)
        assertEquals(2, pendingDao.rows.size)
    }

    /**
     * The lost race: the opposite row vanished between the lookup and the merge. Fail-open prefers one
     * honest pending row over a silent drop or a half-built transfer.
     */
    @Test
    fun autoMerge_lostRace_keepsOwnClaimAndNotifies() = runBlocking {
        val processor = processorWith(bobAccount(), sbiAccount())
        pendingDao.phantomOpposite = PendingTransactionEntity(
            id = 999, amount = 500.0, type = "CREDIT", bankName = "State Bank of India",
            rawSmsBody = "already consumed by another path", timestamp = NOW - 30_000
        )

        val outcome = processor.process(BOB_HEADER, bobDebit("500.00", "1000.00"), NOW, SmsOrigin.REALTIME)

        assertTrue("a lost race must not drop the message: $outcome", outcome is ProcessingOutcome.Notified)
        assertEquals("no half-built transfer", 0, transactionDao.rows.size)
        assertEquals(1, pendingDao.rows.size)
        assertEquals(1, notifier.bankPosts.size)
    }

    // ── Balance sync ──────────────────────────────────────────────────────

    @Test
    fun balanceDiscrepancy_recordsAnInferredMarkerAndAlerts() = runBlocking {
        // Bank says 855.43 after a ₹40 debit from 900.00, so ₹4.57 left the account unlogged.
        val processor = processorWith(bobAccount(lastBalance = 900.0, lastStamp = NOW - 60_000))

        val outcome = processor.process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertTrue("$outcome", outcome is ProcessingOutcome.Notified)
        val marker = pendingDao.rows.values.single { it.source == TransactionSource.BALANCE_RECONCILIATION.name }
        assertEquals(4.57, marker.amount, 0.001)
        assertEquals(TransactionConfidence.INFERRED.name, marker.confidence)
        assertNull("an inference has no message identity to claim", marker.sourceMessageHash)
        assertEquals(1, notifier.missedPosts.size)
        assertEquals(855.43, accountDao.updates.last().lastKnownBalance!!, 0.001)
    }

    @Test
    fun balanceThatReconcilesExactly_recordsNothing() = runBlocking {
        val processor = processorWith(bobAccount(lastBalance = 895.43, lastStamp = NOW - 60_000))

        processor.process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertEquals(0, notifier.missedPosts.size)
        assertEquals("only the claim", 1, pendingDao.rows.size)
        assertEquals(855.43, accountDao.updates.last().lastKnownBalance!!, 0.001)
    }

    // ── Balance netting: the app must not accuse the user of its own records ──
    //
    // The detector compares the bank's figure against what the app expected. It used to adjust the
    // previous reading by the current SMS alone, so anything the user logged by hand in between showed
    // up as a shortfall and the app raised a "missed transaction" for a transaction already sitting in
    // its own database. These cases come in pairs — netted vs still-reported — so neither the netting
    // nor the detector can be removed without a failure.

    @Test
    fun handEnteredActivityBetweenTwoSms_isNettedNotAccused() = runBlocking {
        // 1100.43 − ₹200 card payment entered by hand − ₹45 SMS debit = 855.43. Nothing is missing.
        val processor = processorWith(bobAccount(lastBalance = 1100.43, lastStamp = NOW - 60_000))
        seedUserEntered(amount = 200.0, type = TransactionType.EXPENSE, createdAt = NOW - 30_000)

        processor.process(BOB_HEADER, bobDebit("45.00", "855.43"), NOW, SmsOrigin.REALTIME)

        assertEquals("the ₹200 is in the database; accusing the user of it is the bug", 0, notifier.missedPosts.size)
        assertEquals(0, inferredMarkers().size)
    }

    @Test
    fun theSameShortfallWithNothingLogged_isStillReported() = runBlocking {
        // Identical arithmetic minus the hand entry: now ₹200 really is unaccounted for.
        val processor = processorWith(bobAccount(lastBalance = 1100.43, lastStamp = NOW - 60_000))

        processor.process(BOB_HEADER, bobDebit("45.00", "855.43"), NOW, SmsOrigin.REALTIME)

        assertEquals(200.0, inferredMarkers().single().amount, 0.001)
        assertEquals(1, notifier.missedPosts.size)
    }

    @Test
    fun handEnteredIncome_isNettedAsAnInflow() = runBlocking {
        // 700.43 + ₹200 received − ₹45 = 855.43. A flipped sign would report a ₹400 gap.
        val processor = processorWith(bobAccount(lastBalance = 700.43, lastStamp = NOW - 60_000))
        seedUserEntered(amount = 200.0, type = TransactionType.INCOME, createdAt = NOW - 30_000)

        processor.process(BOB_HEADER, bobDebit("45.00", "855.43"), NOW, SmsOrigin.REALTIME)

        assertEquals(0, notifier.missedPosts.size)
    }

    @Test
    fun handEnteredTransferOutOfTheAccount_countsOnceAsAnOutflow() = runBlocking {
        val processor = processorWith(bobAccount(lastBalance = 1100.43, lastStamp = NOW - 60_000))
        seedUserEntered(
            amount = 200.0, type = TransactionType.TRANSFER, createdAt = NOW - 30_000,
            account = "BOB Current", toAccount = "SBI Savings"
        )

        processor.process(BOB_HEADER, bobDebit("45.00", "855.43"), NOW, SmsOrigin.REALTIME)

        assertEquals(0, notifier.missedPosts.size)
    }

    @Test
    fun handEnteredTransferIntoTheAccount_countsOnceAsAnInflow() = runBlocking {
        val processor = processorWith(bobAccount(lastBalance = 700.43, lastStamp = NOW - 60_000))
        seedUserEntered(
            amount = 200.0, type = TransactionType.TRANSFER, createdAt = NOW - 30_000,
            account = "SBI Savings", toAccount = "BOB Current"
        )

        processor.process(BOB_HEADER, bobDebit("45.00", "855.43"), NOW, SmsOrigin.REALTIME)

        assertEquals(0, notifier.missedPosts.size)
    }

    /**
     * The window's lower bound is load-bearing. The bank's figure at `lastBalanceTimestamp` already
     * includes everything that happened before it, so netting an older row again would cancel out a
     * genuine gap and silence a real alert.
     */
    @Test
    fun activityLoggedBeforeTheLastBalanceReading_isNotNetted() = runBlocking {
        val processor = processorWith(bobAccount(lastBalance = 1100.43, lastStamp = NOW - 60_000))
        seedUserEntered(amount = 200.0, type = TransactionType.EXPENSE, createdAt = NOW - 90_000)

        processor.process(BOB_HEADER, bobDebit("45.00", "855.43"), NOW, SmsOrigin.REALTIME)

        assertEquals(200.0, inferredMarkers().single().amount, 0.001)
    }

    @Test
    fun activityOnAnotherAccount_isNotNetted() = runBlocking {
        val processor = processorWith(bobAccount(lastBalance = 1100.43, lastStamp = NOW - 60_000))
        seedUserEntered(
            amount = 200.0, type = TransactionType.EXPENSE, createdAt = NOW - 30_000, account = "Cash"
        )

        processor.process(BOB_HEADER, bobDebit("45.00", "855.43"), NOW, SmsOrigin.REALTIME)

        assertEquals("cash spending cannot explain a bank balance", 200.0, inferredMarkers().single().amount, 0.001)
    }

    /**
     * The mirror image of the netting bug, and the reason the query filters on provenance: an earlier
     * message's own `AvlBal` was already written into `lastKnownBalance` when it was processed, so
     * approving that message into history must not move the expected balance a second time.
     */
    @Test
    fun anEarlierMessagesOwnTransaction_isNotNettedTwice() = runBlocking {
        // 900.43 − ₹45 = 855.43 reconciles. Netting the SMS-derived ₹200 row would invent a ₹200 gap.
        val processor = processorWith(bobAccount(lastBalance = 900.43, lastStamp = NOW - 60_000))
        transactionDao.seed(
            TransactionEntity(
                amount = 200.0, type = TransactionType.EXPENSE, categoryId = 1, date = TODAY,
                account = "BOB Current", createdAt = NOW - 30_000,
                source = TransactionSource.SMS_REALTIME.name,
                sourceMessageHash = "an-earlier-message-already-counted-in-the-balance"
            )
        )

        processor.process(BOB_HEADER, bobDebit("45.00", "855.43"), NOW, SmsOrigin.REALTIME)

        assertEquals(0, notifier.missedPosts.size)
        assertEquals(0, inferredMarkers().size)
    }

    /**
     * A balance with no timestamp has no window to net over, so its age is unknown. Refresh the
     * reading, but do not infer from it — that is a false-alert machine, not a detector.
     */
    @Test
    fun balanceWithNoTimestamp_refreshesWithoutInferring() = runBlocking {
        val processor = processorWith(bobAccount(lastBalance = 900.0, lastStamp = null))

        processor.process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertEquals(0, notifier.missedPosts.size)
        assertEquals(855.43, accountDao.updates.last().lastKnownBalance!!, 0.001)
        assertEquals(NOW, accountDao.updates.last().lastBalanceTimestamp)
    }

    /** An SMS older than the balance we already hold carries a stale figure; applying it would
     *  walk the balance backwards and manufacture a discrepancy out of nothing. */
    @Test
    fun staleSms_monotonicGuardLeavesTheBalanceAlone() = runBlocking {
        val processor = processorWith(bobAccount(lastBalance = 700.0, lastStamp = NOW + 60_000))

        val outcome = processor.process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertTrue("$outcome", outcome is ProcessingOutcome.Notified)
        assertEquals(0, accountDao.updates.size)
        assertEquals(0, notifier.missedPosts.size)
    }

    // ── Catch-up parity ───────────────────────────────────────────────────

    /**
     * Catch-up keeps its proximity tier, but suppressing there must not burn the identity — otherwise
     * the scanner would consume a message the live receiver was about to notify about, which is root
     * cause #9 in a new costume.
     */
    @Test
    fun catchUpProximitySuppresses_withoutBurningTheIdentityForRealtime() = runBlocking {
        val processor = processorWith()
        seedTransaction(amount = 500.0, source = TransactionSource.SMS_REALTIME.name, account = "Cash")

        val scanned = processor.process(SBI_HEADER, SBI_500_DEBIT, NOW, SmsOrigin.CATCHUP)
        assertEquals(ProcessingOutcome.Suppressed(SuppressReason.CATCHUP_PROXIMITY), scanned)
        assertEquals(0, cache.markCalls)

        val live = processor.process(SBI_HEADER, SBI_500_DEBIT, NOW, SmsOrigin.REALTIME)
        assertTrue("realtime does not inherit the catch-up tiers: $live", live is ProcessingOutcome.Notified)
    }

    @Test
    fun nonBankMessage_isNotATransaction() = runBlocking {
        val outcome = processorWith(bobAccount()).process(
            "AD-JIOFIBER",
            "Your JioFiber bill of Rs 699 is generated. Pay before due date to avoid disconnection.",
            NOW,
            SmsOrigin.REALTIME
        )

        assertEquals(ProcessingOutcome.NotATransaction, outcome)
        assertEquals(0, pendingDao.rows.size)
    }

    // ── Diagnostics: a witness, never a participant ───────────────────────
    //
    // The defect was reported as "sometimes it doesn't notify" and there was no way to tell which of
    // nine silent paths had taken the message. These tests defend the two properties that make the log
    // worth having: every outcome is named exactly once, and the log can never change what it observes.

    /**
     * One entry per processed message, whatever the outcome — design principle 4's "no silent return"
     * expressed as an assertion. The four messages here take four different exits, and the recorded
     * codes have to distinguish them; a log that said `NOTIFIED` or nothing would be no better than the
     * stack traces it replaces.
     */
    @Test
    fun everyOutcome_recordsExactlyOneEntryNamingTheDecision() = runBlocking {
        val processor = processorWith(bobAccount())

        processor.process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)          // claimed + posted
        processor.process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)          // cache says seen
        seedTransaction(amount = 45.0, source = TransactionSource.MANUAL.name)
        processor.process(BOB_HEADER, BOB_45_DEBIT, NOW + 1_000, SmsOrigin.REALTIME)  // hand-entered
        processor.process("AD-JIOFIBER", "Your JioFiber bill of Rs 699 is generated.", NOW, SmsOrigin.REALTIME)

        assertEquals(
            listOf("NOTIFIED", "SUPPRESSED", "SUPPRESSED", "NOT_A_TRANSACTION"),
            diagnostics.outcomes()
        )
        assertEquals("one line per message, no more and no less", 4, diagnostics.entries.size)

        val cacheHit = diagnostics.entries[1]
        assertEquals(SuppressReason.CACHE_HIT.name, cacheHit.reason)
        assertEquals(40.0, cacheHit.amount!!, 0.001)
        assertEquals(SmsOrigin.REALTIME.name, cacheHit.origin)
        assertEquals(SuppressReason.MANUAL_MATCH.name, diagnostics.entries[2].reason)
        assertNull("an unparsed message has no amount to report", diagnostics.entries[3].amount)
    }

    /**
     * The one that would have shortened the original investigation: a claimed row whose notification
     * never went out now says so by name, instead of looking identical to a message that was never
     * received.
     */
    @Test
    fun aClaimedButUnnotifiedRow_isRecordedAsSuch_afterTheNotificationAttempt() = runBlocking {
        notifier.accept = false

        processorWith(bobAccount()).process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertEquals(listOf("CLAIMED_NOT_NOTIFIED"), diagnostics.outcomes())
        assertTrue(
            "the reason must name the row so it can be found: ${diagnostics.entries.single().reason}",
            diagnostics.entries.single().reason!!.contains("pending #")
        )
        // Design principle 3: nothing non-essential may run between the commit and the notification.
        assertTrue("diagnostics belong after the attempt", journal.indexOf("notify") < journal.indexOf("diag"))
    }

    /** A throw is an outcome too — otherwise root cause #8 stays invisible. */
    @Test
    fun aFailedProcessing_isRecordedWithItsCause() = runBlocking {
        pendingDao.throwOnClaim = true

        processorWith(bobAccount()).process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        val entry = diagnostics.entries.single()
        assertEquals("FAILED", entry.outcome)
        assertEquals(
            "the cause is the whole point",
            "IllegalStateException: database unavailable", entry.reason
        )
        assertEquals("the parse succeeded, so the amount is known", 40.0, entry.amount!!, 0.001)
    }

    /**
     * A witness that can change what it witnessed is not a witness. A corrupt prefs file or a full disk
     * must cost the diagnostic line and nothing else — least of all the notification the line exists to
     * explain.
     */
    @Test
    fun aDiagnosticsLogThatThrows_cannotChangeTheOutcome() = runBlocking {
        diagnostics.throwOnRecord = true

        val outcome = processorWith(bobAccount()).process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        assertTrue("$outcome", outcome is ProcessingOutcome.Notified)
        assertEquals(1, notifier.bankPosts.size)
        assertEquals(1, pendingDao.rows.size)
    }

    /** Recovery is the other half of INV-4's bounded attempt, so it reports for itself too. */
    @Test
    fun recovery_recordsWhetherTheRetryLanded() = runBlocking {
        val processor = processorWith()
        seedUnnotified(timestamp = NOW - 60_000)

        notifier.accept = false
        processor.recoverUnnotified()
        assertEquals(listOf("RECOVERY_FAILED"), diagnostics.outcomes())

        notifier.accept = true
        processor.recoverUnnotified()
        assertEquals(listOf("RECOVERY_FAILED", "RECOVERED"), diagnostics.outcomes())
        assertTrue(
            "the attempt number is what shows the INV-6 cap approaching: ${diagnostics.entries.last().reason}",
            diagnostics.entries.last().reason!!.contains("attempt 2")
        )
    }

    /**
     * The log names decisions; it is not a copy of the user's bank messages. `SmsDiagnosticEntry` has no
     * field for a body, a reference or a balance, and this pins the freer-form parts — sender and
     * reason — to the same rule, since a future "more helpful" reason string is exactly how that leaks.
     */
    @Test
    fun diagnosticEntries_carryNoMessageContent() = runBlocking {
        val processor = processorWith(bobAccount())
        processor.process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)
        processor.process(BOB_HEADER, BOB_40_DEBIT, NOW, SmsOrigin.REALTIME)

        diagnostics.entries.forEach { entry ->
            val text = "${entry.sender} ${entry.reason.orEmpty()}"
            assertTrue("no payee or UPI handle may reach the log: $text", !text.contains("UPI:1234567890"))
            assertTrue("no balance may reach the log: $text", !text.contains("855.43"))
        }
    }

    // ── Fixtures ──────────────────────────────────────────────────────────

    private fun bobAccount(lastBalance: Double? = null, lastStamp: Long? = null) = AccountEntity(
        name = "BOB Current", type = "PRIMARY", isPrimary = true, bankCode = "BOB",
        accountLast4 = "1463", lastKnownBalance = lastBalance, lastBalanceTimestamp = lastStamp
    )

    private fun sbiAccount() = AccountEntity(
        name = "SBI Savings", bankCode = "SBI", accountLast4 = "5678"
    )

    private fun seedPending(amount: Double, type: String, bank: String, at: Long) = pendingDao.seed(
        PendingTransactionEntity(
            amount = amount, type = type, bankName = bank,
            rawSmsBody = "seeded $type $amount from $bank", timestamp = at,
            sourceMessageHash = "seeded-$bank-$type-$amount",
            source = TransactionSource.SMS_REALTIME.name,
            confidence = TransactionConfidence.CONFIRMED.name
        )
    )

    private fun seedUnnotified(timestamp: Long, attempts: Int = 0) = pendingDao.seed(
        PendingTransactionEntity(
            amount = 40.0, type = "DEBIT", bankName = "Bank of Baroda", accountLast4 = "1463",
            rawSmsBody = BOB_40_DEBIT, senderHeader = BOB_HEADER, timestamp = timestamp,
            sourceMessageHash = "claimed-but-never-notified-$timestamp-$attempts",
            source = TransactionSource.SMS_REALTIME.name,
            confidence = TransactionConfidence.CONFIRMED.name,
            notificationPostedAt = null, notificationAttempts = attempts
        )
    )

    private fun seedTransaction(amount: Double, source: String, account: String = "BOB Current") =
        transactionDao.seed(
            TransactionEntity(
                amount = amount, type = TransactionType.EXPENSE, categoryId = 1, date = TODAY,
                account = account, createdAt = NOW, source = source
            )
        )

    /** A row the user entered themselves: `source = MANUAL`, and no message identity behind it. */
    private fun seedUserEntered(
        amount: Double,
        type: TransactionType,
        createdAt: Long,
        account: String = "BOB Current",
        toAccount: String? = null
    ) = transactionDao.seed(
        TransactionEntity(
            amount = amount, type = type, categoryId = 1, date = TODAY,
            account = account, toAccount = toAccount, createdAt = createdAt,
            source = TransactionSource.MANUAL.name
        )
    )

    private fun inferredMarkers() = pendingDao.rows.values
        .filter { it.source == TransactionSource.BALANCE_RECONCILIATION.name }

    private fun identityOf(header: String, body: String): String {
        val parsed = SmsBankParser.parse(header, body)
        assertNotNull("fixture must parse", parsed)
        return SmsHash.canonical(parsed!!, header)
    }

    private companion object {
        /** Pinned so nothing in the suite races the wall clock. */
        const val NOW = 1_787_000_000_000L
        val TODAY: LocalDate = LocalDate.of(2026, 8, 24)

        const val BOB_HEADER = "AD-BOBTXN"
        const val SBI_HEADER = "VM-SBIINB"

        /** The three bodies from `issues.txt`, byte-identical to the parser suite's fixtures. */
        const val BOB_40_DEBIT =
            "Dear BOB UPI User: Your A/C XXXXXX1463 is debited by INR 40.00 on 24-08-2026 " +
                "12:00:00 by UPI:1234567890:MerchantName. AvlBal:Rs855.43 - BOB"
        const val BOB_45_DEBIT =
            "Dear BOB UPI User: Your account is debited with INR 45.00 on 24-08-2026 transfer " +
                "to yespay.bizsbiz102249@yesbankltd UPI:9876543210. AvlBal:Rs810.43 - BOB"
        const val BOB_30_CREDIT =
            "Dear BOB UPI User: Your account is credited with INR 30.00 on 24-08-2026 by " +
                "UPI:2345678901. AvlBal:Rs840.43 - BOB"
        const val SBI_500_DEBIT =
            "Your A/C *5678 is debited for Rs 500.00 on 12-Aug-26 transfer to Swiggy UPI " +
                "Ref 123456. Bal: INR 12,000.50"

        /** The ₹40 body's shape with a different amount — same parse path, different identity. */
        fun bobDebit(amount: String, balance: String) =
            "Dear BOB UPI User: Your A/C XXXXXX1463 is debited by INR $amount on 24-08-2026 " +
                "12:00:00 by UPI:1234567890:MerchantName. AvlBal:Rs$balance - BOB"
    }
}
