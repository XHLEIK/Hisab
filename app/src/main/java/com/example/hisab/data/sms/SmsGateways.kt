package com.example.hisab.data.sms

import com.example.hisab.data.db.entity.PendingTransactionEntity
import java.time.LocalDate

/**
 * The seams that keep [TransactionProcessor] free of Android imports, so the whole pipeline can be
 * driven by JVM fakes in a unit test. Android implementations live in `AndroidSmsGateways.kt`.
 */

/**
 * A **performance cache** for message identities. Never an authority — see INV-1.
 *
 * `peek() == true` is a safe skip; `peek() == false` means "don't know", and the caller must fall
 * through to the authoritative Room claim. Losing the whole cache must cost nothing but a few extra
 * queries; it must never cause a duplicate row or a lost message.
 *
 * **INV-7: writes follow the authority, never lead it.** [mark] may only be called after a Room
 * claim has committed. Marking earlier is prohibited: a mark followed by a failed or crashed
 * transaction leaves the cache saying "seen" for a message that was never persisted, and because
 * `peek() == true` is by definition a safe skip, INV-1 cannot rescue it — the message is lost
 * forever. This is the structural fix for the old receiver burning the hash *before* its permission
 * gate.
 */
interface SmsHashCache {
    fun peek(key: String): Boolean
    fun mark(vararg keys: String)
    fun forget(key: String)
}

/** Time, injectable so tests can pin "now" instead of racing the wall clock. */
data class SmsClock(
    val nowMillis: () -> Long = { System.currentTimeMillis() },
    val today: () -> LocalDate = { LocalDate.now() }
)

/**
 * Runs [block] as one all-or-nothing database transaction (INV-3). Android: `room.withTransaction`.
 */
interface AtomicDb {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

/**
 * Notification delivery. Every method reports whether the post was *attempted successfully* —
 * `true` means "the platform accepted it without throwing", never "the user saw it" (see the
 * SEMANTIC note on `notificationPostedAt`). A `false` from a missing POST_NOTIFICATIONS permission
 * is a retryable condition, not a terminal one.
 */
interface SmsNotifier {
    suspend fun postBankTransaction(pending: PendingTransactionEntity): Boolean

    suspend fun postMissedTransaction(
        pending: PendingTransactionEntity,
        actualBalance: Double,
        expectedBalance: Double
    ): Boolean

    suspend fun postAutoMerge(
        transactionId: Long,
        sourceAccount: String,
        targetAccount: String,
        amount: Double
    ): Boolean
}

/** Fires an auto-backup. Always invoked last, and always time-boxed by the caller. */
interface BackupTrigger {
    suspend fun perform()
}

/**
 * One line in the pipeline's decision log.
 *
 * Deliberately a summary and not the message: sender header, amount, origin, outcome, reason — no raw
 * body, no reference number, no balance. Naming the decision is what turns "it sometimes doesn't
 * notify" into a line number; keeping a copy of the user's bank messages is not.
 */
data class SmsDiagnosticEntry(
    val timestamp: Long,
    val sender: String,
    val amount: Double?,
    val origin: String,
    val outcome: String,
    val reason: String? = null
)

/**
 * A bounded, persisted record of what the pipeline decided about recent messages.
 *
 * The defect this whole phase exists to fix was reported as "sometimes it doesn't notify", and there
 * was no way to tell *which* of nine silent paths had taken the message. Every outcome now lands here
 * (design principle 4), so the next report starts from a decision instead of a guess.
 *
 * A witness, never a participant: the processor records after the notification attempt and swallows
 * any failure, so a broken log can never cost a notification.
 */
interface SmsDiagnosticsLog {
    suspend fun record(entry: SmsDiagnosticEntry)

    /** Newest first, capped by the implementation. */
    suspend fun recent(): List<SmsDiagnosticEntry>

    companion object {
        /** Default for call sites that don't want a log — keeps diagnostics non-load-bearing. */
        val None: SmsDiagnosticsLog = object : SmsDiagnosticsLog {
            override suspend fun record(entry: SmsDiagnosticEntry) = Unit
            override suspend fun recent(): List<SmsDiagnosticEntry> = emptyList()
        }
    }
}
