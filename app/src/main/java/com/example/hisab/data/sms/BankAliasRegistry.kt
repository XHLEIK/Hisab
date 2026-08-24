package com.example.hisab.data.sms

/**
 * Canonical registry that bridges Indian Bank short codes (e.g. "BOB", "SBI", "HDFC"),
 * full official names ("Bank of Baroda", "State Bank of India"), DLT header prefixes,
 * and body sign-off aliases.
 */
object BankAliasRegistry {

    data class BankDefinition(
        val code: String,              // e.g. "BOB"
        val fullName: String,          // e.g. "Bank of Baroda"
        val headerPrefixes: List<String>, // e.g. ["BOB", "BOBTXN", "BOBSMS", "BOBUPI", "BARODA"]
        val bodyKeywords: List<String>    // e.g. ["BOB", "BANK OF BARODA", "BARODA"]
    )

    private val BANKS = listOf(
        BankDefinition(
            code = "BOB",
            fullName = "Bank of Baroda",
            headerPrefixes = listOf("BOB", "BOBTXN", "BOBSMS", "BOBUPI", "BARODA"),
            bodyKeywords = listOf("BANK OF BARODA", "BOB UPI", "- BOB", "-BOB", "/5000-BOB")
        ),
        BankDefinition(
            code = "SBI",
            fullName = "State Bank of India",
            headerPrefixes = listOf("SBI", "SBIBNK", "SBIINB", "SBIPSG", "SBISMS", "SBITXN", "SBIPAY"),
            bodyKeywords = listOf("STATE BANK OF INDIA", "SBI UPI", "- SBI", "-SBI", "SBIPAY")
        ),
        BankDefinition(
            code = "HDFC",
            fullName = "HDFC Bank",
            headerPrefixes = listOf("HDFC", "HDFCBK", "HDFCTX", "HDFCSMS"),
            bodyKeywords = listOf("HDFC BANK", "HDFC")
        ),
        BankDefinition(
            code = "ICICI",
            fullName = "ICICI Bank",
            headerPrefixes = listOf("ICICI", "ICICIB", "ICICIT", "ICICISMS", "IPAY"),
            bodyKeywords = listOf("ICICI BANK", "ICICI")
        ),
        BankDefinition(
            code = "AXIS",
            fullName = "Axis Bank",
            headerPrefixes = listOf("AXIS", "AXISBK", "AXISTX", "AXISSMS"),
            bodyKeywords = listOf("AXIS BANK", "AXIS")
        ),
        BankDefinition(
            code = "PNB",
            fullName = "Punjab National Bank",
            headerPrefixes = listOf("PNB", "PNBSMS", "PNBTXN"),
            bodyKeywords = listOf("PUNJAB NATIONAL BANK", "PNB")
        ),
        BankDefinition(
            code = "CANARA",
            fullName = "Canara Bank",
            headerPrefixes = listOf("CANARA", "CANBNK", "CANSMS"),
            bodyKeywords = listOf("CANARA BANK", "CANARA")
        ),
        BankDefinition(
            code = "KOTAK",
            fullName = "Kotak Mahindra Bank",
            headerPrefixes = listOf("KOTAK", "KOTAKB", "KOTAKS"),
            bodyKeywords = listOf("KOTAK MAHINDRA BANK", "KOTAK BANK", "KOTAK")
        ),
        BankDefinition(
            code = "UNION",
            fullName = "Union Bank of India",
            headerPrefixes = listOf("UNION", "UNIONB", "UBI", "UNIONS"),
            bodyKeywords = listOf("UNION BANK OF INDIA", "UNION BANK")
        ),
        BankDefinition(
            code = "BOI",
            fullName = "Bank of India",
            headerPrefixes = listOf("BOI", "BOISMS", "BOITXN"),
            bodyKeywords = listOf("BANK OF INDIA", "BOI")
        ),
        BankDefinition(
            code = "CBI",
            fullName = "Central Bank of India",
            headerPrefixes = listOf("CBI", "CBISMS"),
            bodyKeywords = listOf("CENTRAL BANK OF INDIA", "CBI")
        ),
        BankDefinition(
            code = "IDIB",
            fullName = "Indian Bank",
            headerPrefixes = listOf("IDIB", "INDIBK"),
            bodyKeywords = listOf("INDIAN BANK", "IDIB")
        ),
        BankDefinition(
            code = "IOB",
            fullName = "Indian Overseas Bank",
            headerPrefixes = listOf("IOB", "IOBMS"),
            bodyKeywords = listOf("INDIAN OVERSEAS BANK", "IOB")
        ),
        BankDefinition(
            code = "PSB",
            fullName = "Punjab & Sind Bank",
            headerPrefixes = listOf("PSB", "PSSMS"),
            bodyKeywords = listOf("PUNJAB & SIND BANK", "PSB")
        ),
        BankDefinition(
            code = "UCO",
            fullName = "UCO Bank",
            headerPrefixes = listOf("UCO", "UCOBNK"),
            bodyKeywords = listOf("UCO BANK", "UCO")
        ),
        BankDefinition(
            code = "BOM",
            fullName = "Bank of Maharashtra",
            headerPrefixes = listOf("BOM", "MAHABK"),
            bodyKeywords = listOf("BANK OF MAHARASHTRA", "BOM")
        ),
        BankDefinition(
            code = "IDBI",
            fullName = "IDBI Bank",
            headerPrefixes = listOf("IDBI", "IDBIBK"),
            bodyKeywords = listOf("IDBI BANK", "IDBI")
        ),
        BankDefinition(
            code = "INDUS",
            fullName = "IndusInd Bank",
            headerPrefixes = listOf("INDUS", "INDUSB"),
            bodyKeywords = listOf("INDUSIND BANK", "INDUSIND")
        ),
        BankDefinition(
            code = "FED",
            fullName = "Federal Bank",
            headerPrefixes = listOf("FED", "FEDBNK"),
            bodyKeywords = listOf("FEDERAL BANK")
        ),
        BankDefinition(
            code = "YES",
            fullName = "YES Bank",
            headerPrefixes = listOf("YES", "YESBNK"),
            bodyKeywords = listOf("YES BANK")
        ),
        BankDefinition(
            code = "RBL",
            fullName = "RBL Bank",
            headerPrefixes = listOf("RBL", "RBLBNK"),
            bodyKeywords = listOf("RBL BANK")
        ),
        BankDefinition(
            code = "IDFC",
            fullName = "IDFC FIRST Bank",
            headerPrefixes = listOf("IDFC", "IDFCFB"),
            bodyKeywords = listOf("IDFC FIRST BANK", "IDFC BANK")
        ),
        BankDefinition(
            code = "BANDHAN",
            fullName = "Bandhan Bank",
            headerPrefixes = listOf("BANDHAN"),
            bodyKeywords = listOf("BANDHAN BANK")
        ),
        BankDefinition(
            code = "SIB",
            fullName = "South Indian Bank",
            headerPrefixes = listOf("SIB", "SIBL"),
            bodyKeywords = listOf("SOUTH INDIAN BANK")
        ),
        BankDefinition(
            code = "KVB",
            fullName = "Karur Vysya Bank",
            headerPrefixes = listOf("KVB"),
            bodyKeywords = listOf("KARUR VYSYA BANK")
        ),
        BankDefinition(
            code = "JKB",
            fullName = "Jammu & Kashmir Bank",
            headerPrefixes = listOf("JKB", "JKBANK"),
            bodyKeywords = listOf("JAMMU & KASHMIR BANK")
        ),
        BankDefinition(
            code = "CUB",
            fullName = "City Union Bank",
            headerPrefixes = listOf("CUB"),
            bodyKeywords = listOf("CITY UNION BANK")
        ),
        BankDefinition(
            code = "TMB",
            fullName = "Tamilnad Mercantile Bank",
            headerPrefixes = listOf("TMB"),
            bodyKeywords = listOf("TAMILNAD MERCANTILE BANK")
        ),
        BankDefinition(
            code = "AU",
            fullName = "AU Small Finance Bank",
            headerPrefixes = listOf("AU", "AUBNK"),
            bodyKeywords = listOf("AU SMALL FINANCE BANK", "AU BANK")
        ),
        BankDefinition(
            code = "EQUITAS",
            fullName = "Equitas Small Finance Bank",
            headerPrefixes = listOf("EQUITAS"),
            bodyKeywords = listOf("EQUITAS SMALL FINANCE BANK", "EQUITAS")
        ),
        BankDefinition(
            code = "UJJIVAN",
            fullName = "Ujjivan Small Finance Bank",
            headerPrefixes = listOf("UJJIVAN"),
            bodyKeywords = listOf("UJJIVAN SMALL FINANCE BANK", "UJJIVAN")
        ),
        BankDefinition(
            code = "PYTMPB",
            fullName = "Paytm Payments Bank",
            headerPrefixes = listOf("PYTMPB", "PAYTMB"),
            bodyKeywords = listOf("PAYTM PAYMENTS BANK")
        ),
        BankDefinition(
            code = "ARTLPB",
            fullName = "Airtel Payments Bank",
            headerPrefixes = listOf("ARTLPB"),
            bodyKeywords = listOf("AIRTEL PAYMENTS BANK")
        ),
        BankDefinition(
            code = "IPPB",
            fullName = "India Post Payments Bank",
            headerPrefixes = listOf("IPPB"),
            bodyKeywords = listOf("INDIA POST PAYMENTS BANK", "IPPB")
        ),
        BankDefinition(
            code = "JIOPB",
            fullName = "Jio Payments Bank",
            headerPrefixes = listOf("JIOPB"),
            bodyKeywords = listOf("JIO PAYMENTS BANK")
        ),
        BankDefinition(
            code = "NSDL",
            fullName = "NSDL Payments Bank",
            headerPrefixes = listOf("NSDL"),
            bodyKeywords = listOf("NSDL PAYMENTS BANK")
        ),
        BankDefinition(
            code = "FI",
            fullName = "Fi Money (Federal Bank)",
            headerPrefixes = listOf("FI"),
            bodyKeywords = listOf("FI MONEY")
        ),
        BankDefinition(
            code = "JUPITER",
            fullName = "Jupiter Money (Federal Bank)",
            headerPrefixes = listOf("JUPITER"),
            bodyKeywords = listOf("JUPITER MONEY")
        ),
        BankDefinition(
            code = "SLICE",
            fullName = "Slice",
            headerPrefixes = listOf("SLICE"),
            bodyKeywords = listOf("SLICE")
        ),
        BankDefinition(
            code = "NIYO",
            fullName = "Niyo Global / NiyoX",
            headerPrefixes = listOf("NIYO"),
            bodyKeywords = listOf("NIYO GLOBAL", "NIYOX")
        )
    )

    /**
     * Resolves bank name from sender header (primary) or body (secondary).
     */
    fun identifyBankName(senderHeader: String, body: String): String? {
        val cleanHeader = senderHeader.replace("-", "").uppercase()

        // 1. Primary: Match against sender header prefixes
        for (bank in BANKS) {
            for (prefix in bank.headerPrefixes) {
                if (cleanHeader.contains(prefix)) {
                    return bank.fullName
                }
            }
        }

        // 2. Secondary: Match against body sign-offs / keywords
        val upperBody = body.uppercase()
        for (bank in BANKS) {
            for (kw in bank.bodyKeywords) {
                if (upperBody.contains(kw)) {
                    return bank.fullName
                }
            }
        }

        return null
    }

    /**
     * Checks if a user's linked account matches the parsed bank name or sender header.
     * Handles short code ("BOB"), full name ("Bank of Baroda"), and alias variations.
     */
    fun matches(accountBankCode: String?, parsedBankName: String, senderHeader: String? = null): Boolean {
        if (accountBankCode.isNullOrBlank()) return false
        val cleanAccCode = accountBankCode.trim().uppercase()
        val cleanParsedName = parsedBankName.trim().uppercase()

        // Direct equality
        if (cleanAccCode == cleanParsedName) return true

        // Find the definition for the account's bankCode
        val def = BANKS.firstOrNull {
            it.code.equals(cleanAccCode, ignoreCase = true) ||
            it.fullName.equals(cleanAccCode, ignoreCase = true) ||
            it.headerPrefixes.any { p -> p.equals(cleanAccCode, ignoreCase = true) }
        }

        if (def != null) {
            if (def.fullName.equals(parsedBankName, ignoreCase = true)) return true
            if (def.code.equals(parsedBankName, ignoreCase = true)) return true
            if (def.headerPrefixes.any { parsedBankName.uppercase().contains(it) }) return true
            if (senderHeader != null) {
                val cleanH = senderHeader.replace("-", "").uppercase()
                if (def.headerPrefixes.any { cleanH.contains(it) }) return true
            }
        }

        // Substring check fallback
        if (cleanParsedName.contains(cleanAccCode) || cleanAccCode.contains(cleanParsedName)) return true

        return false
    }
}
