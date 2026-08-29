package com.example.hisab.data.sms

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Policy-level tests for [decide].
 *
 * The old pipeline was fail-*closed*: anything it could not prove to be new was dropped in silence,
 * which is how a genuine ₹45 debit vanished. So the important assertions here are the ones that prove
 * an *absence of evidence* still notifies — a null target account, an unknown opposite bank, a manual
 * entry on some other account. `Suppress` is reserved for positive findings, and each of those gets a
 * paired negative case so the two can never drift together.
 */
class SmsDecisionTest {

    // ── Baseline ──────────────────────────────────────────────────────────

    @Test
    fun knowingNothing_notifies() {
        assertEquals(SmsDecision.Notify, decide(bobDebit(), SmsDecisionContext(SmsOrigin.REALTIME)))
    }

    // ── Fail-open: ambiguity is never a suppression ───────────────────────

    @Test
    fun unresolvableTargetAccount_notifies() {
        // Root cause #2: an SMS from a bank the user linked imprecisely resolved to no account, and
        // the old gate treated "we don't know which account" as "drop it".
        val decision = decide(
            bobDebit(),
            SmsDecisionContext(SmsOrigin.REALTIME, targetAccountName = null)
        )

        assertEquals(SmsDecision.Notify, decision)
    }

    @Test
    fun manualMatchWithUnresolvableTarget_notifies() {
        // The manual entry might be on a completely different account. Without a target to compare it
        // against, the match proves nothing, so it cannot suppress.
        val decision = decide(
            bobDebit(),
            SmsDecisionContext(
                SmsOrigin.REALTIME,
                manualMatchAccountName = "BOB Current",
                targetAccountName = null
            )
        )

        assertEquals(SmsDecision.Notify, decision)
    }

    @Test
    fun manualMatchOnADifferentAccount_notifies() {
        val decision = decide(
            bobDebit(),
            SmsDecisionContext(
                SmsOrigin.REALTIME,
                manualMatchAccountName = "SBI Savings",
                targetAccountName = "BOB Current"
            )
        )

        assertEquals("a ₹45 lunch paid in cash is not this SMS", SmsDecision.Notify, decision)
    }

    // ── The two legitimate suppressions ───────────────────────────────────

    @Test
    fun manualMatchOnTheSameAccount_suppresses() {
        val decision = decide(
            bobDebit(),
            SmsDecisionContext(
                SmsOrigin.REALTIME,
                manualMatchAccountName = "BOB Current",
                targetAccountName = "BOB Current"
            )
        )

        assertEquals(SmsDecision.Suppress(SuppressReason.MANUAL_MATCH), decision)
    }

    @Test
    fun outstandingReconciliationMarker_suppresses() {
        val decision = decide(
            bobDebit(),
            SmsDecisionContext(SmsOrigin.REALTIME, reconciliationMarkerPresent = true)
        )

        assertEquals(SmsDecision.Suppress(SuppressReason.RECONCILIATION_MARKER), decision)
    }

    @Test
    fun cacheHit_suppressesAheadOfEverythingElse() {
        val decision = decide(
            bobDebit(),
            SmsDecisionContext(
                SmsOrigin.REALTIME,
                cacheSaysSeen = true,
                oppositePendingId = 7L,
                oppositePendingBankName = "State Bank of India"
            )
        )

        assertEquals(SmsDecision.Suppress(SuppressReason.CACHE_HIT), decision)
    }

    // ── Catch-up tiers are catch-up only ──────────────────────────────────

    @Test
    fun proximityMatch_suppressesOnCatchUpOnly() {
        val ctx = SmsDecisionContext(SmsOrigin.CATCHUP, catchUpProximityMatch = true)

        assertEquals(SmsDecision.Suppress(SuppressReason.CATCHUP_PROXIMITY), decide(bobDebit(), ctx))
        assertEquals(
            "a live message must not inherit the scanner's heuristics",
            SmsDecision.Notify,
            decide(bobDebit(), ctx.copy(origin = SmsOrigin.REALTIME))
        )
    }

    @Test
    fun reconciledBalance_suppressesOnCatchUpOnly() {
        val ctx = SmsDecisionContext(SmsOrigin.CATCHUP, catchUpBalanceReconciled = true)

        assertEquals(
            SmsDecision.Suppress(SuppressReason.CATCHUP_BALANCE_RECONCILED),
            decide(bobDebit(), ctx)
        )
        assertEquals(SmsDecision.Notify, decide(bobDebit(), ctx.copy(origin = SmsOrigin.REALTIME)))
    }

    // ── Auto-merge criteria (false-positive parity) ───────────────────────
    //
    // Merge criteria are carried over from the shipped behaviour unchanged. These cases pin that
    // parity down: fixing duplicate and missing logging must not start inventing transfers.

    @Test
    fun oppositePendingAtADifferentBank_merges() {
        val decision = decide(
            bobDebit(),
            SmsDecisionContext(
                SmsOrigin.REALTIME,
                oppositePendingId = 42L,
                oppositePendingBankName = "State Bank of India"
            )
        )

        assertEquals(SmsDecision.AutoMerge(42L, "State Bank of India"), decision)
    }

    @Test
    fun oppositePendingAtTheSameBank_doesNotMerge() {
        // Case-insensitively the same bank: an internal debit/credit pair is not an inter-account
        // transfer, and merging it would delete one of two genuine rows.
        val decision = decide(
            bobDebit(),
            SmsDecisionContext(
                SmsOrigin.REALTIME,
                oppositePendingId = 42L,
                oppositePendingBankName = "bank of baroda"
            )
        )

        assertEquals(SmsDecision.Notify, decision)
    }

    @Test
    fun oppositePendingWithNoIdentifiableBank_doesNotMerge() {
        val decision = decide(
            bobDebit(),
            SmsDecisionContext(
                SmsOrigin.REALTIME,
                oppositePendingId = 42L,
                oppositePendingBankName = null
            )
        )

        assertEquals("an unknown counterparty is not a proven transfer", SmsDecision.Notify, decision)
    }

    @Test
    fun bankNameWithoutAPendingRow_doesNotMerge() {
        val decision = decide(
            bobDebit(),
            SmsDecisionContext(
                SmsOrigin.REALTIME,
                oppositePendingId = null,
                oppositePendingBankName = "State Bank of India"
            )
        )

        assertEquals(SmsDecision.Notify, decision)
    }

    @Test
    fun handEnteredTransactionOutranksAutoMerge() {
        // Ordering matters: a transaction the user already logged by hand must not be swept into a
        // phantom transfer with an unrelated opposite row.
        val decision = decide(
            bobDebit(),
            SmsDecisionContext(
                SmsOrigin.REALTIME,
                manualMatchAccountName = "BOB Current",
                targetAccountName = "BOB Current",
                oppositePendingId = 42L,
                oppositePendingBankName = "State Bank of India"
            )
        )

        assertEquals(SmsDecision.Suppress(SuppressReason.MANUAL_MATCH), decision)
    }

    @Test
    fun catchUpProximityOutranksAutoMerge() {
        val decision = decide(
            bobDebit(),
            SmsDecisionContext(
                SmsOrigin.CATCHUP,
                catchUpProximityMatch = true,
                oppositePendingId = 42L,
                oppositePendingBankName = "State Bank of India"
            )
        )

        assertEquals(SmsDecision.Suppress(SuppressReason.CATCHUP_PROXIMITY), decision)
    }

    /** Built directly rather than parsed: [decide] reads only these fields, and a hand-built fixture
     *  keeps a parser change from silently altering what this suite is testing. */
    private fun bobDebit(
        amount: Double = 45.0,
        type: String = "DEBIT",
        bankName: String = "Bank of Baroda"
    ) = ParsedBankSms(
        amount = amount,
        type = type,
        bankName = bankName,
        senderHeader = "AD-BOBTXN",
        accountLast4 = "1463",
        endingBalance = 810.43,
        rawBody = "Dear BOB UPI User: Your account is debited with INR 45.00 - BOB"
    )
}
