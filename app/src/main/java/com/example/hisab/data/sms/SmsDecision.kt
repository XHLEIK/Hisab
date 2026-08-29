package com.example.hisab.data.sms

/**
 * The pure decision layer: given a parsed SMS and everything the database already told us about it,
 * decide what to do. No Android imports, no I/O, no coroutines — so the whole policy is unit-testable
 * without a device (see `AGENTS.md`).
 *
 * **Fail-open is the governing rule.** The old pipeline was fail-closed — anything it could not prove
 * to be new was dropped in silence, which is how a genuine ₹45 debit vanished. Here, [Suppress] is
 * returned for exactly two situations, both of which represent a *positive* finding, never an
 * absence of evidence:
 *
 *  1. the user already entered this transaction by hand, on this account, inside the window; or
 *  2. a reconciliation marker for this amount/account/window is outstanding.
 *
 * Everything else notifies: unknown bank, no matching linked account, an unresolvable target
 * account. Proven duplication is deliberately *not* decided here — it can only come from the
 * authoritative Room claim's UNIQUE conflict (INV-2), which is what keeps fail-open from being noisy.
 */
sealed interface SmsDecision {

    /** Create a pending row and attempt the Stage-1 notification. */
    data object Notify : SmsDecision

    /**
     * This SMS is the other half of an inter-account transfer already sitting in `pending`.
     * Consume that row and write a single TRANSFER instead of two independent rows.
     */
    data class AutoMerge(val oppositePendingId: Long, val oppositeBankName: String) : SmsDecision

    /** Do not create a row. [reason] is always recorded — no outcome is silent. */
    data class Suppress(val reason: SuppressReason) : SmsDecision
}

/** Why a message was suppressed. Logged verbatim so a future "it didn't notify" report names a line. */
enum class SuppressReason {
    /** The performance cache had already seen this identity. Advisory only — see INV-1. */
    CACHE_HIT,

    /** A hand-entered transaction on the same account already covers this amount. */
    MANUAL_MATCH,

    /** An outstanding reconciliation marker claims this amount/account/window. */
    RECONCILIATION_MARKER,

    /** Catch-up scan only: an existing pending row or logged transaction sits within ±30 min. */
    CATCHUP_PROXIMITY,

    /** Catch-up scan only: the account's 3-day net already equals the SMS's ending balance. */
    CATCHUP_BALANCE_RECONCILED
}

/** Where a message entered the pipeline. Recorded on the row as its provenance. */
enum class SmsOrigin { REALTIME, CATCHUP }

/**
 * Everything [decide] is allowed to know. The caller performs the lookups; this stays pure.
 *
 * Note what is *absent*: whether the identity is already claimed. That question belongs to the Room
 * transaction, not to policy.
 */
data class SmsDecisionContext(
    val origin: SmsOrigin,

    /** True when the performance cache already holds this identity (canonical or legacy key). */
    val cacheSaysSeen: Boolean = false,

    /**
     * Account name a hand-entered transaction was found on, or `null` if none was found.
     * The lookup itself must already be scoped to `source = 'MANUAL' OR NULL` and to the window —
     * an unscoped lookup matches rows this very pipeline auto-logged, which is root cause #4.
     */
    val manualMatchAccountName: String? = null,

    /** Linked account this SMS resolves to, or `null` when it cannot be resolved. */
    val targetAccountName: String? = null,

    /** True when a reconciliation marker for this amount/account/window is outstanding. */
    val reconciliationMarkerPresent: Boolean = false,

    /** Opposite-direction pending row inside the 120 s transfer window, if any. */
    val oppositePendingId: Long? = null,
    val oppositePendingBankName: String? = null,

    /** Catch-up only: an existing pending row or logged transaction within ±30 min. */
    val catchUpProximityMatch: Boolean = false,

    /** Catch-up only: the account's recent net already reconciles to the SMS's ending balance. */
    val catchUpBalanceReconciled: Boolean = false
)

/**
 * Decides the outcome for one parsed SMS.
 *
 * Order matters: the cheap positive findings are checked before auto-merge, so a message the user
 * already logged by hand does not get merged into a phantom transfer.
 */
fun decide(parsed: ParsedBankSms, ctx: SmsDecisionContext): SmsDecision {
    if (ctx.cacheSaysSeen) return SmsDecision.Suppress(SuppressReason.CACHE_HIT)

    // A manual entry suppresses only when we can confirm it sits on *this* SMS's account. The old
    // code also suppressed when the target account was unresolvable, turning "we don't know" into
    // "drop it" — the exact fail-closed behaviour this layer exists to remove.
    val manualAccount = ctx.manualMatchAccountName
    val targetAccount = ctx.targetAccountName
    if (manualAccount != null && targetAccount != null && manualAccount == targetAccount) {
        return SmsDecision.Suppress(SuppressReason.MANUAL_MATCH)
    }

    if (ctx.reconciliationMarkerPresent) {
        return SmsDecision.Suppress(SuppressReason.RECONCILIATION_MARKER)
    }

    if (ctx.origin == SmsOrigin.CATCHUP) {
        if (ctx.catchUpProximityMatch) return SmsDecision.Suppress(SuppressReason.CATCHUP_PROXIMITY)
        if (ctx.catchUpBalanceReconciled) {
            return SmsDecision.Suppress(SuppressReason.CATCHUP_BALANCE_RECONCILED)
        }
    }

    // ── Auto-merge, at strict parity with the shipped criteria ────────────
    // Criteria are carried over unchanged on purpose. Loosening them is the one way this work could
    // make things worse: an unrelated ₹5,000 credit and ₹5,000 debit must stay two rows, not become
    // an invented transfer. The DAO supplies exact-amount, opposite-direction and 120 s window; the
    // different-bank rule lives here so it is covered by the false-positive tests.
    val oppositeId = ctx.oppositePendingId
    val oppositeBank = ctx.oppositePendingBankName
    if (oppositeId != null && oppositeBank != null &&
        !oppositeBank.equals(parsed.bankName, ignoreCase = true)
    ) {
        return SmsDecision.AutoMerge(oppositeId, oppositeBank)
    }

    return SmsDecision.Notify
}
