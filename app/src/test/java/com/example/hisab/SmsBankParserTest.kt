package com.example.hisab

import com.example.hisab.data.sms.SmsBankParser
import com.example.hisab.data.sms.SmsNotificationHelper
import org.junit.Assert.*
import org.junit.Test

class SmsBankParserTest {

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
        // Complex real-world case: "credited with Rs 150 towards refund of previously debited order"
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
        assertEquals("DEBIT", parsed.type) // "paid" triggers debit
    }

    @Test
    fun testNonBankBlacklist_JioFiber_IsRejected() {
        val header = "AD-JIOFIBER"
        val body = "Your JioFiber bill of Rs 699 is generated. Pay before due date to avoid disconnection."
        val parsed = SmsBankParser.parse(header, body)

        assertNull("JioFiber telecom messages must be rejected by blacklist", parsed)
    }

    @Test
    fun testNonBankBlacklist_AirtelFi_IsRejected() {
        val header = "VK-AIRTELFI"
        val body = "Dear Customer, your Airtel Wi-Fi bill of Rs 999 is due on 15-Aug."
        val parsed = SmsBankParser.parse(header, body)

        assertNull("Airtel Wi-Fi telecom messages must be rejected", parsed)
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
    fun testOtpMessage_WithAmount_IsRejected() {
        val header = "VM-HDFCBK"
        val body = "123456 is your OTP for transaction of Rs 1,500.00 at Amazon. Do not share OTP with anyone."
        val parsed = SmsBankParser.parse(header, body)

        assertNull("OTP messages must be rejected as noise even if amount is present", parsed)
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
    fun testSalaryDeposit_IsCredit() {
        val header = "AD-KOTAKB"
        val body = "Your A/C ending in 3311 has been deposited with INR 45,000.00 towards monthly salary on 01-Aug. Bal: Rs 55,200"
        val parsed = SmsBankParser.parse(header, body)

        assertNotNull(parsed)
        assertEquals(45000.0, parsed!!.amount, 0.001)
        assertEquals("CREDIT", parsed.type)
        assertEquals("3311", parsed.accountLast4)
        assertEquals(55200.0, parsed.endingBalance ?: 0.0, 0.001)
    }

    @Test
    fun testOnlyBalanceInquiry_IsRejected() {
        val header = "AD-PNBSMS"
        val body = "Dear Customer, Avail Bal for your PNB A/C ending 7766 is Rs 24,150.00."
        val parsed = SmsBankParser.parse(header, body)

        assertNull("Pure balance alerts without transactional verbs must be rejected", parsed)
    }

    @Test
    fun testGetCategoryEmoji_MappingCorrectness() {
        assertEquals("☕", SmsNotificationHelper.getCategoryEmoji("Coffee"))
        assertEquals("🍽️", SmsNotificationHelper.getCategoryEmoji("Restaurant"))
        assertEquals("🛒", SmsNotificationHelper.getCategoryEmoji("ShoppingCart"))
        assertEquals("🚗", SmsNotificationHelper.getCategoryEmoji("DirectionsCar"))
        assertEquals("🧺", SmsNotificationHelper.getCategoryEmoji("LocalLaundryService"))
        assertEquals("👕", SmsNotificationHelper.getCategoryEmoji("Checkroom"))
        assertEquals("🏥", SmsNotificationHelper.getCategoryEmoji("LocalHospital"))
        assertEquals("🐷", SmsNotificationHelper.getCategoryEmoji("Savings"))
        assertEquals("💼", SmsNotificationHelper.getCategoryEmoji("Work"))
        assertEquals("📋", SmsNotificationHelper.getCategoryEmoji("UnknownCategory"))
    }
}
