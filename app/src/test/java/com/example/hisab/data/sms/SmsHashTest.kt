package com.example.hisab.data.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Identity tests for the SMS pipeline: reference extraction, validation, and the three-tier hash.
 *
 * The reported defect (`issues.txt:1`) is that a ₹40 BOB debit notified while a structurally
 * identical ₹45 debit and a ₹30 credit did not. The three real message bodies are pinned here
 * **together**, in one test, so the trio can never diverge again — a fix that repairs one and breaks
 * another fails loudly instead of looking green.
 */
class SmsHashTest {

    // ── The three real bodies from issues.txt, verbatim ───────────────────

    private val header40 = "AD-BOBTXN"
    private val body40 = "Rs.40.00 Dr. from A/C XXXXXX1463 and Cr. to " +
        "sbibhim.instant06929686794868563@sbipay. Ref:623681255058. " +
        "AvlBal:Rs855.43(2026:08:24 06:25:53). Not you? Call 18005700/5000-BOB"

    private val header45 = "AD-BOBTXN"
    private val body45 = "Rs.45.00 Dr. from A/C XXXXXX1463 and Cr. to " +
        "yespay.bizsbiz102249@yesbankltd. Ref:623654059521. " +
        "AvlBal:Rs810.43(2026:08:24 06:29:44). Not you? Call 18005700/5000-BOB"

    private val header30 = "VM-BOBTXN"
    private val body30 = "Dear BOB UPI User: Your account is credited with INR 30.00 on " +
        "2026-08-24 06:47:49 PM by UPI Ref No 313159087592; AvlBal: Rs840.43 - BOB"

    @Test
    fun `all three reported messages parse, keep their reference, and get distinct identities`() {
        val forty = SmsBankParser.parse(header40, body40)
        val fortyFive = SmsBankParser.parse(header45, body45)
        val thirty = SmsBankParser.parse(header30, body30)

        assertNotNull("₹40 debit must parse", forty)
        assertNotNull("₹45 debit must parse — this is the message that silently vanished", fortyFive)
        assertNotNull("₹30 credit must parse — this one vanished too", thirty)

        assertEquals(40.0, forty!!.amount, 0.001)
        assertEquals(45.0, fortyFive!!.amount, 0.001)
        assertEquals(30.0, thirty!!.amount, 0.001)

        assertEquals("DEBIT", forty.type)
        assertEquals("DEBIT", fortyFive.type)
        assertEquals("CREDIT", thirty.type)

        assertEquals("Bank of Baroda", forty.bankName)
        assertEquals("Bank of Baroda", fortyFive.bankName)
        assertEquals("Bank of Baroda", thirty.bankName)

        // Each carries its real bank reference, and the trailing '.' / ';' never leak into it.
        assertEquals("623681255058", forty.referenceNumber)
        assertEquals("623654059521", fortyFive.referenceNumber)
        assertEquals("313159087592", thirty.referenceNumber)

        // The two debits name the same account; the credit body has none.
        assertEquals("1463", forty.accountLast4)
        assertEquals("1463", fortyFive.accountLast4)
        assertNull("₹30 credit body carries no account number", thirty.accountLast4)

        assertEquals(SmsHash.IdentityTier.REFERENCE_AND_ACCOUNT, SmsHash.tier(forty.accountLast4, forty.referenceNumber))
        assertEquals(SmsHash.IdentityTier.REFERENCE_AND_ACCOUNT, SmsHash.tier(fortyFive.accountLast4, fortyFive.referenceNumber))
        assertEquals(
            "No account in the body ⇒ reference-only tier, not a body-tier fallback",
            SmsHash.IdentityTier.REFERENCE_ONLY,
            SmsHash.tier(thirty.accountLast4, thirty.referenceNumber)
        )

        val keys = listOf(forty, fortyFive, thirty).map { SmsHash.canonical(it) }
        assertEquals("All three messages must hold distinct identities", 3, keys.toSet().size)
    }

    @Test
    fun `re-delivery of the same message yields the same identity`() {
        val first = SmsBankParser.parse(header30, body30)!!
        val redelivered = SmsBankParser.parse(header30, body30)!!
        assertEquals(SmsHash.canonical(first), SmsHash.canonical(redelivered))
    }

    // ── Reference extraction: accepted forms ──────────────────────────────

    @Test
    fun `every supported reference label is extracted`() {
        val cases = listOf(
            "Ref:123456" to "123456",
            "Ref: 123456" to "123456",
            "Ref No 123456" to "123456",
            "Ref No. 123456" to "123456",
            "Reference No: 123456" to "123456",
            "UPI Ref No 123456" to "123456",
            "UPI Ref No: 123456" to "123456",
            "RRN 123456789012" to "123456789012",
            "RRN: 123456789012" to "123456789012",
            "UTR 123456789012" to "123456789012",
            "Txn ID: ABC123" to "ABC123",
            "Txn No ABC123" to "ABC123",
            "Transaction ID abc123" to "ABC123",
            // The real fixtures again, in isolation.
            "Ref:623681255058." to "623681255058",
            "UPI Ref No 313159087592;" to "313159087592"
        )
        for ((fragment, expected) in cases) {
            val body = "Your A/C XXXXXX1463 is debited by Rs 45.00. $fragment AvlBal:Rs810.43"
            val parsed = SmsBankParser.parse("AD-BOBTXN", body)
            assertNotNull("Should parse: $fragment", parsed)
            assertEquals("Reference from '$fragment'", expected, parsed!!.referenceNumber)
        }
    }

    @Test
    fun `reference lowercased in the body is upper-cased into the identity`() {
        val body = "Your A/C XXXXXX1463 is debited by Rs 45.00. Txn ID: abc123def AvlBal:Rs810.43"
        assertEquals("ABC123DEF", SmsBankParser.parse("AD-BOBTXN", body)!!.referenceNumber)
    }

    // ── Reference extraction: rejected forms ──────────────────────────────

    @Test
    fun `malformed references are rejected and never reach the identity`() {
        val rejected = listOf(
            "Ref: 12",          // too short
            "Ref: ABC",         // too short
            "Ref: Rs. 500",     // amount echo
            "Ref: INR500",      // amount echo
            "Ref: 1,234",       // separator ⇒ not a reference
            "Ref: 12.34"        // separator ⇒ not a reference
        )
        for (fragment in rejected) {
            val body = "Your A/C XXXXXX1463 is debited by Rs 45.00. $fragment AvlBal:Rs810.43"
            val parsed = SmsBankParser.parse("AD-BOBTXN", body)
            assertNotNull("Should still parse the transaction: $fragment", parsed)
            assertNull("'$fragment' must not produce a reference", parsed!!.referenceNumber)

            // A rejected reference must not merely be blank — the key must be the *body* tier, i.e.
            // exactly what a message with no reference at all would produce. The key is a digest, so
            // "the rejected text is absent" is asserted as "the key is the no-reference key".
            assertEquals(
                "'$fragment' must fall through to the body tier",
                SmsHash.IdentityTier.BODY,
                SmsHash.tier(parsed.accountLast4, parsed.referenceNumber)
            )
            val asIfNeverExtracted = SmsHash.canonical(
                senderHeader = parsed.senderHeader,
                bankName = parsed.bankName,
                accountLast4 = parsed.accountLast4,
                referenceNumber = null,
                amount = parsed.amount,
                type = parsed.type,
                rawBody = parsed.rawBody
            )
            assertEquals(SmsHash.canonical(parsed), asIfNeverExtracted)
        }
    }

    @Test
    fun `a word merely starting with a label is not mistaken for a reference`() {
        // "Refund123456" must not become reference "UND123456".
        val body = "Your A/C XXXXXX1463 is credited by Rs 45.00 Refund123456 AvlBal:Rs810.43"
        assertNull(SmsBankParser.parse("AD-BOBTXN", body)!!.referenceNumber)
    }

    @Test
    fun `a decoy before the real reference does not shadow it`() {
        val body = "Your A/C XXXXXX1463 is debited by Rs 45.00 Ref: 12 then Ref:623654059521 " +
            "AvlBal:Rs810.43"
        assertEquals("623654059521", SmsBankParser.parse("AD-BOBTXN", body)!!.referenceNumber)
    }

    @Test
    fun `normalize enforces every documented rule`() {
        assertEquals("623681255058", SmsReference.normalize("623681255058"))
        assertEquals("ABC123", SmsReference.normalize("  abc 123 "))
        assertNull("too short", SmsReference.normalize("12345"))
        assertNull("blank", SmsReference.normalize("      "))
        assertNull("null in, null out", SmsReference.normalize(null))
        assertNull("currency echo", SmsReference.normalize("RS500000"))
        assertNull("currency echo", SmsReference.normalize("inr500000"))
        assertNull("decimal separator", SmsReference.normalize("1234.56"))
        assertNull("thousands separator", SmsReference.normalize("1,234,567"))
        assertNull("non-ASCII digits are a parse artefact", SmsReference.normalize("१२३४५६७८"))
    }

    // ── Identity tiers ────────────────────────────────────────────────────

    @Test
    fun `same reference on different accounts of one bank stays distinct`() {
        val a = SmsHash.canonical(
            senderHeader = "AD-BOBTXN", bankName = "Bank of Baroda", accountLast4 = "1463",
            referenceNumber = "623654059521", amount = 45.0, type = "DEBIT", rawBody = body45
        )
        val b = SmsHash.canonical(
            senderHeader = "AD-BOBTXN", bankName = "Bank of Baroda", accountLast4 = "9999",
            referenceNumber = "623654059521", amount = 45.0, type = "DEBIT", rawBody = body45
        )
        assertNotEquals("Tier 1 must scope identity by account", a, b)
    }

    @Test
    fun `tier 1 ignores body and header noise once reference and account are known`() {
        val base = SmsHash.canonical(
            senderHeader = "AD-BOBTXN", bankName = "Bank of Baroda", accountLast4 = "1463",
            referenceNumber = "623654059521", amount = 45.0, type = "DEBIT", rawBody = body45
        )
        val differentHeaderAndSpacing = SmsHash.canonical(
            senderHeader = "VM-BOBTXN", bankName = "Bank of Baroda", accountLast4 = "XX1463",
            referenceNumber = "623654059521", amount = 45.0, type = "DEBIT",
            rawBody = body45.replace(" ", "  ")
        )
        assertEquals(
            "The bank's own reference is the identity; DLT header churn must not fork it",
            base, differentHeaderAndSpacing
        )
    }

    @Test
    fun `tier 3 uses the full body, so two same-amount messages no longer collide`() {
        // Both ₹45 debits, same sender, same account, identical first thirty characters — the exact
        // shape the old `rawBody.take(30)` key collapsed into one identity, permanently.
        val first = "Rs.45.00 Dr. from A/C XXXXXX1463 and Cr. to " +
            "yespay.bizsbiz102249@yesbankltd. AvlBal:Rs810.43"
        val second = "Rs.45.00 Dr. from A/C XXXXXX1463 and Cr. to " +
            "otherpay.merchant999@okhdfcbank. AvlBal:Rs765.43"
        assertEquals("Fixture invalid: prefixes must match", first.take(30), second.take(30))

        val a = SmsBankParser.parse(header45, first)!!
        val b = SmsBankParser.parse(header45, second)!!
        assertNull("Fixture must have no reference so tier 3 is exercised", a.referenceNumber)
        assertNull("Fixture must have no reference so tier 3 is exercised", b.referenceNumber)
        assertEquals(SmsHash.IdentityTier.BODY, SmsHash.tier(a.accountLast4, a.referenceNumber))

        assertEquals(
            "The legacy key collapsed these two — this equality is the bug being fixed",
            SmsHash.legacyBodyKey(header45, a.amount, a.type, first),
            SmsHash.legacyBodyKey(header45, b.amount, b.type, second)
        )
        assertNotEquals(
            "Two genuinely different ₹45 debits must hold different identities",
            SmsHash.canonical(a), SmsHash.canonical(b)
        )
    }

    @Test
    fun `tier 3 is stable across whitespace and case churn in re-delivery`() {
        val body = "Rs.45.00 Dr. from A/C XXXXXX1463 and Cr. to shop@bank. AvlBal:Rs810.43"
        val a = SmsHash.canonical(
            senderHeader = "AD-BOBTXN", bankName = "Bank of Baroda", accountLast4 = "1463",
            referenceNumber = null, amount = 45.0, type = "DEBIT", rawBody = body
        )
        val b = SmsHash.canonical(
            senderHeader = "ad-bobtxn", bankName = "Bank of Baroda", accountLast4 = "1463",
            referenceNumber = null, amount = 45.0, type = "debit",
            rawBody = "  Rs.45.00 Dr.  from A/C XXXXXX1463 and Cr. to shop@bank.\nAvlBal:Rs810.43 "
        )
        assertEquals(a, b)
    }

    @Test
    fun `amount is keyed in paise so representation cannot fork an identity`() {
        val body = "Rs.45.00 Dr. from A/C XXXXXX1463 and Cr. to shop@bank. AvlBal:Rs810.43"
        fun key(amount: Double) = SmsHash.canonical(
            senderHeader = "AD-BOBTXN", bankName = "Bank of Baroda", accountLast4 = "1463",
            referenceNumber = null, amount = amount, type = "DEBIT", rawBody = body
        )
        assertEquals(key(45.0), key(45.000000001))
        assertNotEquals(key(45.0), key(45.01))
    }

    // ── Reconciliation key ────────────────────────────────────────────────

    @Test
    fun `reconciliation key is scoped by account and time bucket`() {
        val now = 1_756_000_000_000L
        val base = SmsHash.reconciliationKey(500.0, "CREDIT", "1463", now)
        assertEquals("Same inputs ⇒ same key", base, SmsHash.reconciliationKey(500.0, "CREDIT", "1463", now))
        assertNotEquals(
            "A different account must not consume this marker",
            base, SmsHash.reconciliationKey(500.0, "CREDIT", "9999", now)
        )
        assertNotEquals(
            "A marker must not stay live indefinitely",
            base, SmsHash.reconciliationKey(500.0, "CREDIT", "1463", now + 7_200_000L)
        )
        assertNotEquals(base, SmsHash.reconciliationKey(500.0, "DEBIT", "1463", now))
        assertNotEquals(base, SmsHash.reconciliationKey(501.0, "CREDIT", "1463", now))
    }

    @Test
    fun `legacy keys reproduce the pre-upgrade formulas byte for byte`() {
        // Pinned so a refactor cannot quietly break upgrade compatibility: an install upgrading from
        // v3.2.0 recognises already-processed messages only through these exact strings.
        assertEquals(
            md5("AD-BOBTXN-40.0-DEBIT-${body40.take(30)}"),
            SmsHash.legacyBodyKey("AD-BOBTXN", 40.0, "DEBIT", body40)
        )
        assertEquals(
            md5("AD-BOBTXN-40.0-DEBIT-1756000000000"),
            SmsHash.legacyTimestampKey("AD-BOBTXN", 40.0, "DEBIT", 1_756_000_000_000L)
        )
    }

    private fun md5(input: String): String =
        java.security.MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
