package com.example.hisab

import com.example.hisab.data.sms.BankAliasRegistry
import com.example.hisab.data.sms.SmsBankParser
import com.example.hisab.data.sms.SmsNotificationHelper
import org.junit.Assert.*
import org.junit.Test

class SmsBankParserTest {

    @Test
    fun testBobExactSms_40RupeesDebit() {
        val header = "AD-BOBTXN"
        val body = "Dear BOB UPI User: Your A/C XXXXXX1463 is debited by INR 40.00 on 24-08-2026 12:00:00 by UPI:1234567890:MerchantName. AvlBal:Rs855.43 - BOB"
        val parsed = SmsBankParser.parse(header, body)

        assertNotNull("Should parse BOB 40 Rs debit", parsed)
        assertEquals(40.0, parsed!!.amount, 0.001)
        assertEquals("DEBIT", parsed.type)
        assertEquals("Bank of Baroda", parsed.bankName)
        assertEquals("1463", parsed.accountLast4)
        assertEquals(855.43, parsed.endingBalance ?: 0.0, 0.001)
    }

    @Test
    fun testBobExactSms_45RupeesDebit_ComplexPayeeHandle() {
        val header = "AX-BOBTXN"
        val body = "Dear BOB UPI User: Your account is debited with INR 45.00 on 24-08-2026 transfer to yespay.bizsbiz102249@yesbankltd UPI:9876543210. AvlBal:Rs810.43 - BOB"
        val parsed = SmsBankParser.parse(header, body)

        assertNotNull("Should parse BOB 45 Rs debit with complex payee handle", parsed)
        assertEquals(45.0, parsed!!.amount, 0.001)
        assertEquals("DEBIT", parsed.type)
        assertEquals("Bank of Baroda", parsed.bankName)
        assertEquals(810.43, parsed.endingBalance ?: 0.0, 0.001)
        assertTrue("BankAliasRegistry should match BOB to Bank of Baroda", BankAliasRegistry.matches("BOB", parsed.bankName, header))
    }

    @Test
    fun testBobExactSms_30RupeesCredit_NoAccountNumber() {
        val header = "VM-BOBTXN"
        val body = "Dear BOB UPI User: Your account is credited with INR 30.00 on 24-08-2026 by UPI:2345678901. AvlBal:Rs840.43 - BOB"
        val parsed = SmsBankParser.parse(header, body)

        assertNotNull("Should parse BOB 30 Rs credit without account number", parsed)
        assertEquals(30.0, parsed!!.amount, 0.001)
        assertEquals("CREDIT", parsed.type)
        assertEquals("Bank of Baroda", parsed.bankName)
        assertEquals(840.43, parsed.endingBalance ?: 0.0, 0.001)
        assertTrue("BankAliasRegistry should match BOB to Bank of Baroda", BankAliasRegistry.matches("BOB", parsed.bankName, header))
    }

    @Test
    fun testBankAliasRegistry_MatchesAllCommonBanks() {
        assertTrue(BankAliasRegistry.matches("BOB", "Bank of Baroda", "AD-BOBTXN"))
        assertTrue(BankAliasRegistry.matches("SBI", "State Bank of India", "VM-SBIINB"))
        assertTrue(BankAliasRegistry.matches("HDFC", "HDFC Bank", "AX-HDFCBK"))
        assertTrue(BankAliasRegistry.matches("ICICI", "ICICI Bank", "AD-ICICIB"))
        assertTrue(BankAliasRegistry.matches("AXIS", "Axis Bank", "BZ-AXISBK"))
        assertTrue(BankAliasRegistry.matches("KOTAK", "Kotak Mahindra Bank", "AD-KOTAKB"))
        assertTrue(BankAliasRegistry.matches("PNB", "Punjab National Bank", "AD-PNBSMS"))
    }

    @Test
    fun testCreditParsing_StrictWordBoundaries_30Rupees() {
        val header = "AD-BOBTXN"
        val body = "Dear Customer, your A/C ending 1234 has been credited by Rs. 30.00 on 12-Aug-2026. Avail Bal: Rs 5,430.00"
        val parsed = SmsBankParser.parse(header, body)

        assertNotNull("Should successfully parse Bank of Baroda credit SMS", parsed)
        assertEquals(30.0, parsed!!.amount, 0.001)
        assertEquals("CREDIT", parsed.type)
        assertEquals("Bank of Baroda", parsed.bankName)
        assertEquals("1234", parsed.accountLast4)
        assertEquals(5430.0, parsed.endingBalance ?: 0.0, 0.001)
    }

    @Test
    fun testDebitParsing_StrictWordBoundaries_500Rupees() {
        val header = "VM-SBIINB"
        val body = "Your A/C *5678 is debited for Rs 500.00 on 12-Aug-26 transfer to Swiggy UPI Ref 123456. Bal: INR 12,000.50"
        val parsed = SmsBankParser.parse(header, body)

        assertNotNull("Should successfully parse SBI debit SMS", parsed)
        assertEquals(500.0, parsed!!.amount, 0.001)
        assertEquals("DEBIT", parsed.type)
        assertEquals("State Bank of India", parsed.bankName)
        assertEquals("5678", parsed.accountLast4)
        assertEquals(12000.50, parsed.endingBalance ?: 0.0, 0.001)
    }

    @Test
    fun testCredit_WithDistantDebitWord_CreditWinsByProximity() {
        val header = "AX-HDFCBK"
        val body = "Your A/C 9999 has been credited with INR 150.00 on 10-Aug-26 for refund of debited amount."
        val parsed = SmsBankParser.parse(header, body)

        assertNotNull(parsed)
        assertEquals(150.0, parsed!!.amount, 0.001)
        assertEquals("CREDIT", parsed.type)
    }

    @Test
    fun testWordBoundary_DearAndCardAndDriveDoNotTriggerDebitOrCredit() {
        val header = "AD-ICICIB"
        val body = "Dear customer, your credit card bill of Rs 2500 is paid from A/C 4321 on 05-Aug."
        val parsed = SmsBankParser.parse(header, body)

        assertNotNull(parsed)
        assertEquals(2500.0, parsed!!.amount, 0.001)
        assertEquals("DEBIT", parsed.type)
    }

    @Test
    fun testNonBankBlacklist_JioFiber_IsRejected() {
        val header = "AD-JIOFIBER"
        val body = "Your JioFiber bill of Rs 699 is generated. Pay before due date to avoid disconnection."
        val parsed = SmsBankParser.parse(header, body)

        assertNull("JioFiber telecom messages must be rejected by blacklist", parsed)
    }

    @Test
    fun testPaymentBank_JIOPB_IsAccepted() {
        val header = "AD-JIOPB"
        val body = "Your Jio Payments Bank A/C *1122 is debited with Rs 100.00. Avail Bal: Rs 400.00"
        val parsed = SmsBankParser.parse(header, body)

        assertNotNull("Jio Payments Bank (JIOPB) DLT header should be accepted", parsed)
        assertEquals("Jio Payments Bank", parsed!!.bankName)
        assertEquals(100.0, parsed.amount, 0.001)
        assertEquals("DEBIT", parsed.type)
    }

    @Test
    fun testAtmWithdrawal_IsDebit() {
        val header = "BZ-AXISBK"
        val body = "Rs 2,000.00 withdrawn from A/C XX8844 at Axis ATM New Delhi. Avail Bal: INR 18,500.00"
        val parsed = SmsBankParser.parse(header, body)

        assertNotNull(parsed)
        assertEquals(2000.0, parsed!!.amount, 0.001)
        assertEquals("DEBIT", parsed.type)
        assertEquals("8844", parsed.accountLast4)
        assertEquals(18500.0, parsed.endingBalance ?: 0.0, 0.001)
    }

    @Test
    fun testGetCategoryEmoji_DirectEmojiAndLegacyMapping() {
        // Direct emojis are preserved
        assertEquals("🛒", SmsNotificationHelper.getCategoryEmoji("🛒"))
        assertEquals("🍔", SmsNotificationHelper.getCategoryEmoji("🍔"))
        assertEquals("🍽️", SmsNotificationHelper.getCategoryEmoji("🍽️"))

        // Legacy icon names are converted
        assertEquals("☕", SmsNotificationHelper.getCategoryEmoji("Coffee"))
        assertEquals("🍽️", SmsNotificationHelper.getCategoryEmoji("Restaurant"))
        assertEquals("🛒", SmsNotificationHelper.getCategoryEmoji("ShoppingCart"))
        assertEquals("🚗", SmsNotificationHelper.getCategoryEmoji("DirectionsCar"))
        assertEquals("🧺", SmsNotificationHelper.getCategoryEmoji("LocalLaundryService"))
        assertEquals("👕", SmsNotificationHelper.getCategoryEmoji("Checkroom"))
        assertEquals("🏥", SmsNotificationHelper.getCategoryEmoji("LocalHospital"))
        assertEquals("🐷", SmsNotificationHelper.getCategoryEmoji("Savings"))
        assertEquals("💼", SmsNotificationHelper.getCategoryEmoji("Work"))
        assertEquals("📋", SmsNotificationHelper.getCategoryEmoji(""))
    }
}
