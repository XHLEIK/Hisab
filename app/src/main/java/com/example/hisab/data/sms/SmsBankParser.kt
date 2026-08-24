package com.example.hisab.data.sms

import java.util.regex.Pattern

data class ParsedBankSms(
    val amount: Double,
    val type: String, // "DEBIT" or "CREDIT"
    val bankName: String,
    val senderHeader: String,
    val accountLast4: String? = null,
    val merchantOrPayee: String? = null,
    val endingBalance: Double? = null,
    val rawBody: String
)

object SmsBankParser {

    // ── Bank Sender DLT Header Map ────────────────────────────────────────
    // Cleaned: Removed ambiguous short codes (JIO, AIRTEL, PAYTM, PYTM, ARTL)
    // that collide with telecom/service provider headers.
    // Kept specific payment bank headers (JIOPB, ARTLPB, PYTMPB) only.
    private val BANK_SENDER_MAP = mapOf(
        "BOB" to "Bank of Baroda",
        "BOBSMS" to "Bank of Baroda",
        "BOBTXN" to "Bank of Baroda",
        "SBI" to "State Bank of India",
        "SBIBNK" to "State Bank of India",
        "SBIINB" to "State Bank of India",
        "SBIPSG" to "State Bank of India",
        "HDFC" to "HDFC Bank",
        "HDFCBK" to "HDFC Bank",
        "HDFCTX" to "HDFC Bank",
        "ICICI" to "ICICI Bank",
        "ICICIB" to "ICICI Bank",
        "ICICIT" to "ICICI Bank",
        "AXIS" to "Axis Bank",
        "AXISBK" to "Axis Bank",
        "AXISTX" to "Axis Bank",
        "PNB" to "Punjab National Bank",
        "PNBSMS" to "Punjab National Bank",
        "CANARA" to "Canara Bank",
        "CANBNK" to "Canara Bank",
        "KOTAK" to "Kotak Mahindra Bank",
        "KOTAKB" to "Kotak Mahindra Bank",
        "UNION" to "Union Bank of India",
        "UNIONB" to "Union Bank of India",
        "UBI" to "Union Bank of India",
        "BOI" to "Bank of India",
        "BOISMS" to "Bank of India",
        "CBI" to "Central Bank of India",
        "CBISMS" to "Central Bank of India",
        "IDIB" to "Indian Bank",
        "INDIBK" to "Indian Bank",
        "IOB" to "Indian Overseas Bank",
        "IOBMS" to "Indian Overseas Bank",
        "PSB" to "Punjab & Sind Bank",
        "PSSMS" to "Punjab & Sind Bank",
        "UCO" to "UCO Bank",
        "UCOBNK" to "UCO Bank",
        "BOM" to "Bank of Maharashtra",
        "MAHABK" to "Bank of Maharashtra",
        "IDBI" to "IDBI Bank",
        "IDBIBK" to "IDBI Bank",
        "INDUS" to "IndusInd Bank",
        "INDUSB" to "IndusInd Bank",
        "FED" to "Federal Bank",
        "FEDBNK" to "Federal Bank",
        "YES" to "YES Bank",
        "YESBNK" to "YES Bank",
        "RBL" to "RBL Bank",
        "RBLBNK" to "RBL Bank",
        "IDFC" to "IDFC FIRST Bank",
        "IDFCFB" to "IDFC FIRST Bank",
        "BANDHAN" to "Bandhan Bank",
        "SIB" to "South Indian Bank",
        "SIBL" to "South Indian Bank",
        "KVB" to "Karur Vysya Bank",
        "JKB" to "Jammu & Kashmir Bank",
        "JKBANK" to "Jammu & Kashmir Bank",
        "CUB" to "City Union Bank",
        "TMB" to "Tamilnad Mercantile Bank",
        "AU" to "AU Small Finance Bank",
        "AUBNK" to "AU Small Finance Bank",
        "EQUITAS" to "Equitas Small Finance Bank",
        "UJJIVAN" to "Ujjivan Small Finance Bank",
        "CAPITAL" to "Capital Small Finance Bank",
        "FINCARE" to "Fincare Small Finance Bank",
        "JANA" to "Jana Small Finance Bank",
        "SURYODAY" to "Suryoday Small Finance Bank",
        "UTKARSH" to "Utkarsh Small Finance Bank",
        "ESAF" to "ESAF Small Finance Bank",
        // Payment Banks — specific DLT headers ONLY (not short telecom codes)
        "PYTMPB" to "Paytm Payments Bank",
        "PAYTMB" to "Paytm Payments Bank",
        "ARTLPB" to "Airtel Payments Bank",
        "IPPB" to "India Post Payments Bank",
        "JIOPB" to "Jio Payments Bank",
        "NSDL" to "NSDL Payments Bank",
        "FI" to "Fi Money (Federal Bank)",
        "JUPITER" to "Jupiter Money (Federal Bank)",
        "SLICE" to "Slice",
        "NIYO" to "Niyo Global / NiyoX"
    )

    // ── Non-Bank Service Header Blacklist ──────────────────────────────────
    // Hard-reject any SMS whose sender header contains these substrings
    // BEFORE running bank identification or amount parsing.
    private val NON_BANK_BLACKLIST = setOf(
        "JIOFIBER", "JIOMOB", "JIONET", "JIODGT", "JIOPOS", "JIOMRT",
        "AIRTELFI", "AIRTELDTH", "AIRTELTV",
        "VITELE", "VIDATA",
        "BILLDESK", "BILLPAY",
        "SWIGGY", "ZOMATO", "AMAZON", "AMZN",
        "FLIPKART", "FLIPK", "MYNTRA",
        "IRCTC", "IRCTCWEB",
        "OLACAB", "UBERIN",
        "PAYTMMALL", "PAYTMFST"
    )

    // ── Amount Extraction Patterns ────────────────────────────────────────
    private val AMOUNT_PATTERNS = listOf(
        Pattern.compile("(?:rs\\.?|inr|₹)\\s*([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:debited|credited|spent|received|withdrawn|deposited)\\s+(?:by|for|with|of)?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE)
    )

    // ── Account Last-4 Digits Patterns ────────────────────────────────────
    private val ACCOUNT_LAST4_PATTERNS = listOf(
        Pattern.compile("(?:a/c|account|acct|acc)\\s*(?:no\\.?)?\\s*[:.]?\\s*(?:[x\\*\\.-]*)\\s*(\\d{4})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ending\\s*(?:with|in)?\\s*[:.]?\\s*(\\d{4})", Pattern.CASE_INSENSITIVE)
    )

    // ── Merchant / Payee Extraction Patterns ──────────────────────────────
    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("(?:cr\\.?\\s+to|to|at|info:)\\s+([a-zA-Z0-9\\s&\\.'-]{2,25})(?:\\s+on|\\s+ref|\\s+via|\\s+a/c|\\.|,|\\$) ", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:vpa|upi|ref)\\s+([a-zA-Z0-9\\.@_-]{3,30})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:cr\\.?\\s+to)\\s+([a-zA-Z0-9\\.@_-]{3,35})", Pattern.CASE_INSENSITIVE)
    )

    // ── Ending Balance Extraction Patterns ────────────────────────────────
    private val BALANCE_PATTERNS = listOf(
        Pattern.compile("(?:bal|balance|avail bal|available balance|a/c bal|avlbal)\\s*(?:is|:)?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE)
    )

    // ── 3-Pass Direction Engine: Strict Word-Boundary Regex ───────────────
    private val CREDIT_VERB_PATTERN = Pattern.compile(
        "\\b(credited|received|deposited|added|refunded|refund|cashback|reversal|cr\\.?)\\b",
        Pattern.CASE_INSENSITIVE
    )
    private val DEBIT_VERB_PATTERN = Pattern.compile(
        "\\b(debited|spent|withdrawn|paid|sent|transferred|dr\\.?)\\b",
        Pattern.CASE_INSENSITIVE
    )
    // Supplementary debit context (tie-breaker only, never sole signal)
    private val DEBIT_CONTEXT_PATTERN = Pattern.compile(
        "\\b(vpa|upi|purchase)\\b",
        Pattern.CASE_INSENSITIVE
    )

    // ── Main Parse Entry Point ────────────────────────────────────────────
    fun parse(senderHeader: String, body: String): ParsedBankSms? {
        val cleanHeader = senderHeader.trim().uppercase()
        val cleanBody = body.trim()

        // ── Stage 0: Non-Bank Blacklist Hard-Reject ───────────────────
        val headerForBlacklist = cleanHeader.replace("-", "")
        for (blacklisted in NON_BANK_BLACKLIST) {
            if (headerForBlacklist.contains(blacklisted)) {
                return null
            }
        }

        // ── Stage 1: DLT Suffix Verification ─────────────────────────
        if (cleanHeader.contains("-")) {
            val parts = cleanHeader.split("-")
            val categorySuffix = parts.lastOrNull()?.uppercase()
            if (categorySuffix == "P" || categorySuffix == "G") {
                return null
            }
        }

        // ── Stage 1b: Bank Code Identification ───────────────────────
        val bankName = identifyBankName(cleanHeader, cleanBody) ?: return null

        // ── Stage 2: OTP & Non-Transaction Noise Exclusion ───────────
        val lowerBody = cleanBody.lowercase()
        if (isOtpOrNoise(lowerBody)) {
            return null
        }

        // ── Stage 3: Amount Extraction (needed for proximity scoring) ─
        val amount = extractAmount(cleanBody) ?: return null
        if (amount <= 0.0) return null

        // ── Stage 4: 3-Pass Direction Intent Engine ──────────────────
        val endingBalance = extractEndingBalance(cleanBody)
        val type = determineTransactionDirection(cleanBody, amount, endingBalance) ?: return null

        // ── Stage 5: Optional Account, Merchant Extraction ───────────
        val accountLast4 = extractAccountLast4(cleanBody)
        val merchantOrPayee = extractMerchant(cleanBody)

        return ParsedBankSms(
            amount = amount,
            type = type,
            bankName = bankName,
            senderHeader = cleanHeader,
            accountLast4 = accountLast4,
            merchantOrPayee = merchantOrPayee,
            endingBalance = endingBalance,
            rawBody = cleanBody
        )
    }

    /**
     * 3-Pass Multi-Factor Direction Engine
     *
     * Pass 1: Strict word-boundary regex matching for credit/debit verbs
     * Pass 2: Proximity-based scoring with inverse-distance decay relative to the amount
     * Pass 3: Credit-first precedence with ending-balance tie-breaker
     */
    private fun determineTransactionDirection(
        body: String,
        amount: Double,
        endingBalance: Double?
    ): String? {
        // Find amount position in the body for proximity calculation
        val amountPosition = findAmountPosition(body, amount)

        var creditScore = 0.0
        var debitScore = 0.0

        // Score credit verbs with distance-based weighting
        val creditMatcher = CREDIT_VERB_PATTERN.matcher(body)
        while (creditMatcher.find()) {
            val verbPos = creditMatcher.start()
            val distance = if (amountPosition >= 0) kotlin.math.abs(verbPos - amountPosition) else 100
            val weight = when {
                distance <= 30 -> 4.0 // Immediately adjacent to amount
                distance <= 60 -> 2.5
                distance <= 100 -> 1.5
                else -> 0.8
            }
            creditScore += weight
        }

        // Score debit verbs with distance-based weighting
        val debitMatcher = DEBIT_VERB_PATTERN.matcher(body)
        while (debitMatcher.find()) {
            val verbPos = debitMatcher.start()
            val distance = if (amountPosition >= 0) kotlin.math.abs(verbPos - amountPosition) else 100
            val weight = when {
                distance <= 30 -> 4.0 // Immediately adjacent to amount
                distance <= 60 -> 2.5
                distance <= 100 -> 1.5
                else -> 0.8
            }
            debitScore += weight
        }

        // Score supplementary debit context (0.5 weight — tie-breaker only)
        val contextMatcher = DEBIT_CONTEXT_PATTERN.matcher(body)
        while (contextMatcher.find()) {
            debitScore += 0.5
        }

        // ── Pass 3: Direction Resolution ──
        return when {
            // Both scores zero → not a transaction
            creditScore == 0.0 && debitScore == 0.0 -> null

            // Credit-First Precedence: if creditScore > 0 and >= debitScore -> CREDIT
            creditScore > 0 && creditScore >= debitScore -> "CREDIT"

            // Clear debit win
            debitScore > creditScore -> "DEBIT"

            // Fallback (if any remaining ambiguous state)
            endingBalance != null && endingBalance > amount -> "CREDIT"
            else -> "DEBIT"
        }
    }

    /**
     * Finds the character position of the monetary amount in the SMS body
     * for proximity-based verb scoring.
     */
    private fun findAmountPosition(body: String, amount: Double): Int {
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                return matcher.start()
            }
        }
        return -1
    }

    private fun identifyBankName(header: String, body: String): String? {
        return BankAliasRegistry.identifyBankName(header, body)
    }

    private fun isOtpOrNoise(lowerBody: String): Boolean {
        if (lowerBody.contains("otp") ||
            lowerBody.contains("verification code") ||
            lowerBody.contains("one time password") ||
            lowerBody.contains("secret code") ||
            lowerBody.contains("login pin") ||
            lowerBody.contains("do not share") ||
            lowerBody.contains("passcode") ||
            lowerBody.contains("failed") ||
            lowerBody.contains("declined") ||
            lowerBody.contains("unsuccessful") ||
            lowerBody.contains("reversed") ||
            lowerBody.contains("cancelled") ||
            lowerBody.contains("canceled") ||
            lowerBody.contains("rejected")
        ) {
            return true
        }

        if (lowerBody.contains("avail bal") || lowerBody.contains("available balance") || lowerBody.contains("avlbal")) {
            val hasTxKeyword = CREDIT_VERB_PATTERN.matcher(lowerBody).find() ||
                    DEBIT_VERB_PATTERN.matcher(lowerBody).find()
            if (!hasTxKeyword) return true
        }

        return false
    }

    private fun extractAmount(body: String): Double? {
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val rawStr = matcher.group(1)?.replace(",", "") ?: continue
                val parsed = rawStr.toDoubleOrNull()
                if (parsed != null && parsed > 0.0) {
                    return parsed
                }
            }
        }
        return null
    }

    private fun extractAccountLast4(body: String): String? {
        for (pattern in ACCOUNT_LAST4_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val digits = matcher.group(1)
                if (!digits.isNullOrEmpty() && digits.length == 4) {
                    return digits
                }
            }
        }
        return null
    }

    private fun extractMerchant(body: String): String? {
        for (pattern in MERCHANT_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val name = matcher.group(1)?.trim()
                if (!name.isNullOrEmpty() && name.length >= 2) {
                    return name
                }
            }
        }
        return null
    }

    private fun extractEndingBalance(body: String): Double? {
        for (pattern in BALANCE_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val rawStr = matcher.group(1)?.replace(",", "") ?: continue
                val parsed = rawStr.toDoubleOrNull()
                if (parsed != null && parsed >= 0.0) {
                    return parsed
                }
            }
        }
        return null
    }
}
