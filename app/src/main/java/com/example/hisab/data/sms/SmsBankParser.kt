package com.example.hisab.data.sms

import java.util.regex.Pattern

data class ParsedBankSms(
    val amount: Double,
    val type: String, // "DEBIT" or "CREDIT"
    val bankName: String,
    val senderHeader: String,
    val accountLast4: String? = null,
    val merchantOrPayee: String? = null,
    val rawBody: String
)

object SmsBankParser {

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
        "PYTM" to "Paytm Payments Bank",
        "PAYTM" to "Paytm Payments Bank",
        "AIRTEL" to "Airtel Payments Bank",
        "ARTL" to "Airtel Payments Bank",
        "IPPB" to "India Post Payments Bank",
        "JIO" to "Jio Payments Bank",
        "NSDL" to "NSDL Payments Bank",
        "FI" to "Fi Money (Federal Bank)",
        "JUPITER" to "Jupiter Money (Federal Bank)",
        "SLICE" to "Slice",
        "NIYO" to "Niyo Global / NiyoX"
    )

    private val AMOUNT_PATTERNS = listOf(
        Pattern.compile("(?:rs\\.?|inr|₹)\\s*([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:debited|credited|spent|received|withdrawn|deposited)\\s+(?:by|for|with|of)?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE)
    )

    private val ACCOUNT_LAST4_PATTERNS = listOf(
        Pattern.compile("(?:a/c|account|acct|acc)\\s*(?:no\\.?)?\\s*(?:[x\\*]{2,12})?(\\d{4})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ending\\s*(?:with|in)?\\s*(\\d{4})", Pattern.CASE_INSENSITIVE)
    )

    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("(?:to|at|info:)\\s+([a-zA-Z0-9\\s&\\.'-]{2,25})(?:\\s+on|\\s+ref|\\s+via|\\s+a/c|\\.|,|\\$) ", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:vpa|upi|ref)\\s+([a-zA-Z0-9\\.@_-]{3,30})", Pattern.CASE_INSENSITIVE)
    )

    fun parse(senderHeader: String, body: String): ParsedBankSms? {
        val cleanHeader = senderHeader.trim().uppercase()
        val cleanBody = body.trim()

        // ── Stage 1: DLT Suffix Verification ─────────────
        // Header format: XX-AAAAAA-T or XX-AAAAAA-S
        if (cleanHeader.contains("-")) {
            val parts = cleanHeader.split("-")
            val categorySuffix = parts.lastOrNull()?.uppercase()
            // Reject -P (Promotional) and -G (Government)
            if (categorySuffix == "P" || categorySuffix == "G") {
                return null
            }
        }

        // ── Stage 1b: Bank Code Identification ───────────
        val bankName = identifyBankName(cleanHeader, cleanBody) ?: return null

        // ── Stage 2: OTP & Non-Transaction Noise Exclusion ─
        val lowerBody = cleanBody.lowercase()
        if (isOtpOrNoise(lowerBody)) {
            return null
        }

        // ── Stage 3: Transaction Intent Detection ────────
        val isDebit = lowerBody.contains("debited") || lowerBody.contains("dr.") || lowerBody.contains("dr ") ||
                lowerBody.contains("spent") || lowerBody.contains("withdrawn") || lowerBody.contains("paid to") ||
                lowerBody.contains("transferred from") || lowerBody.contains("sent to")

        val isCredit = lowerBody.contains("credited") || lowerBody.contains("cr.") || lowerBody.contains("cr ") ||
                lowerBody.contains("received") || lowerBody.contains("deposited") || lowerBody.contains("added to") ||
                lowerBody.contains("refund")

        if (!isDebit && !isCredit) {
            return null
        }

        val type = if (isDebit) "DEBIT" else "CREDIT"

        // ── Stage 4: Amount Extraction ────────────────────
        val amount = extractAmount(cleanBody) ?: return null
        if (amount <= 0.0) return null

        // ── Stage 5: Optional Account & Merchant Extraction ─
        val accountLast4 = extractAccountLast4(cleanBody)
        val merchantOrPayee = extractMerchant(cleanBody)

        return ParsedBankSms(
            amount = amount,
            type = type,
            bankName = bankName,
            senderHeader = cleanHeader,
            accountLast4 = accountLast4,
            merchantOrPayee = merchantOrPayee,
            rawBody = cleanBody
        )
    }

    private fun identifyBankName(header: String, body: String): String? {
        val cleanHeader = header.replace("-", "").uppercase()
        for ((code, name) in BANK_SENDER_MAP) {
            if (cleanHeader.contains(code)) {
                return name
            }
        }
        // Fallback search inside body for explicit bank names
        val upperBody = body.uppercase()
        for ((code, name) in BANK_SENDER_MAP) {
            if (upperBody.contains(name.uppercase()) || upperBody.contains(code)) {
                return name
            }
        }
        return null
    }

    private fun isOtpOrNoise(lowerBody: String): Boolean {
        if (lowerBody.contains("otp") ||
            lowerBody.contains("verification code") ||
            lowerBody.contains("one time password") ||
            lowerBody.contains("secret code") ||
            lowerBody.contains("login pin") ||
            lowerBody.contains("do not share") ||
            lowerBody.contains("passcode")
        ) {
            return true
        }

        // Pure balance inquiry without debit/credit
        if (lowerBody.contains("avail bal") || lowerBody.contains("available balance")) {
            val hasTxKeyword = lowerBody.contains("debited") || lowerBody.contains("credited") ||
                    lowerBody.contains("dr") || lowerBody.contains("cr") || lowerBody.contains("spent")
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
}
