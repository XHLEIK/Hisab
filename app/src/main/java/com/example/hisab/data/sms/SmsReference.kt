package com.example.hisab.data.sms

/**
 * Normalisation and validation for bank-issued reference numbers.
 *
 * A reference is the strongest identity a bank SMS carries — issued by the bank, unique per
 * transaction, and byte-stable across a re-delivery — which is exactly why an extracted reference
 * may **never** be trusted blindly. Regexes misfire; a label followed by the wrong token yields a
 * plausible-looking string that would become a *wrong* identity, silently collapsing two unrelated
 * transactions into one or splitting one into two.
 *
 * So every candidate passes through [normalize], which returns `null` for anything it cannot vouch
 * for. A `null` is cheap: `SmsHash` simply falls back to its body tier, which is always available.
 * A bogus reference is not cheap. When in doubt, reject.
 *
 * Pure Kotlin, no Android imports — see `AGENTS.md`.
 */
object SmsReference {

    /** Below this, a candidate is far more likely to be a stray token than a bank reference. */
    private const val MIN_LENGTH = 6

    /**
     * Fragments that prove the extraction grabbed an amount rather than a reference (`Ref: Rs. 500`).
     * `.` and `,` are already excluded by the alphanumeric rule below; they are listed anyway so the
     * currency-echo guard reads as one complete rule instead of relying on a rule stated elsewhere.
     */
    private val FORBIDDEN_FRAGMENTS = listOf("RS", "INR", ".", ",")

    /**
     * Canonicalises [raw] and returns it only if it can serve as a transaction identity.
     *
     * Normalisation is deliberately narrow: trim, drop internal whitespace, upper-case. Stripping
     * arbitrary separators is *not* done here — that is the extraction regex's job, which captures
     * alphanumerics only. Anything non-alphanumeric that survives into this function is treated as
     * evidence the extraction was wrong, and rejected rather than repaired.
     *
     * @return the canonical reference, or `null` if it fails any validation rule.
     */
    fun normalize(raw: String?): String? {
        if (raw == null) return null

        val candidate = raw.filterNot { it.isWhitespace() }.uppercase()

        if (candidate.length < MIN_LENGTH) return null

        // ASCII-only on purpose: Char.isLetterOrDigit() would admit Unicode digits and letters, and
        // a reference containing e.g. Devanagari numerals is a parse artefact, not a bank reference.
        if (!candidate.all { it in '0'..'9' || it in 'A'..'Z' }) return null

        if (FORBIDDEN_FRAGMENTS.any { candidate.contains(it) }) return null

        return candidate
    }
}
