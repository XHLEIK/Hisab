package com.example.hisab.data.sms

import java.security.MessageDigest

/**
 * The single source of transaction identity for the SMS pipeline.
 *
 * Replaces the `computeHash` helper that was duplicated verbatim in `SmsReceiver` and
 * `SmsCatchUpSync`, where the two copies produced byte-identical keys and therefore raced each other
 * through one shared `SharedPreferences` file.
 *
 * The identity is tiered — the first satisfied tier wins:
 *
 * | Tier | Precondition | Key material |
 * |---|---|---|
 * | [IdentityTier.REFERENCE_AND_ACCOUNT] | valid reference **and** account known | bank + account + reference |
 * | [IdentityTier.REFERENCE_ONLY] | valid reference, account unknown | bank + reference |
 * | [IdentityTier.BODY] | no valid reference | sender + amount + type + full normalised body |
 *
 * Tier 1 exists so the model never *depends* on the assumption that two accounts at one bank cannot
 * share a reference: wherever the account is reliably known, identity is scoped by it.
 *
 * Tier 3 hashes the **full** body. The old key used `body.take(30)`, which made two distinct
 * transactions with the same amount and the same first thirty characters permanently
 * indistinguishable — the second one was suppressed forever. That is root-cause #3 of the
 * missing-notification defect.
 *
 * Reference validation is *not* done here; see [SmsReference]. Whatever arrives is re-normalised
 * defensively, so a caller that hands over raw label text cannot smuggle an unvalidated reference
 * into the key.
 *
 * Pure Kotlin, no Android imports — see `AGENTS.md`.
 */
object SmsHash {

    /** Which tier produced a key. Exposed so tests can assert the tier, not just hash inequality. */
    enum class IdentityTier { REFERENCE_AND_ACCOUNT, REFERENCE_ONLY, BODY }

    /**
     * Schema tag on every canonical key. Bumping it deliberately re-partitions the identity space;
     * it also guarantees a canonical key can never coincide with a [legacyBodyKey].
     */
    private const val SCHEMA = "v8"

    /**
     * Reports which tier [canonical] will use for these inputs, without computing the hash.
     */
    fun tier(accountLast4: String?, referenceNumber: String?): IdentityTier {
        val reference = SmsReference.normalize(referenceNumber)
        val account = normalizeAccount(accountLast4)
        return when {
            reference != null && account != null -> IdentityTier.REFERENCE_AND_ACCOUNT
            reference != null -> IdentityTier.REFERENCE_ONLY
            else -> IdentityTier.BODY
        }
    }

    /**
     * The authoritative identity for one bank SMS. Written to `sourceMessageHash`, where a UNIQUE
     * index turns it into the only proof of duplication the pipeline is allowed to act on (INV-2).
     *
     * SHA-256 rather than the MD5 the old helper used: this value backs a UNIQUE database
     * constraint, so a collision would not merely mis-cache, it would silently suppress a real
     * transaction the user made.
     */
    fun canonical(
        senderHeader: String,
        bankName: String,
        accountLast4: String?,
        referenceNumber: String?,
        amount: Double,
        type: String,
        rawBody: String
    ): String {
        val reference = SmsReference.normalize(referenceNumber)
        val account = normalizeAccount(accountLast4)
        val bank = normalizeBank(bankName)

        val material = when {
            reference != null && account != null -> "$SCHEMA|t1|$bank|$account|$reference"
            reference != null -> "$SCHEMA|t2|$bank|$reference"
            else -> "$SCHEMA|t3|${normalizeSender(senderHeader)}|${paise(amount)}|" +
                    "${normalizeType(type)}|${normalizeBody(rawBody)}"
        }
        return sha256(material)
    }

    /**
     * Convenience overload for the common case: the parse result plus the header it arrived on.
     */
    fun canonical(parsed: ParsedBankSms, senderHeader: String = parsed.senderHeader): String =
        canonical(
            senderHeader = senderHeader,
            bankName = parsed.bankName,
            accountLast4 = parsed.accountLast4,
            referenceNumber = parsed.referenceNumber,
            amount = parsed.amount,
            type = parsed.type,
            rawBody = parsed.rawBody
        )

    /**
     * Reproduces the pre-v3.2.1 body key byte-for-byte:
     * `MD5("$sender-$amount-$type-${body.take(30)}")`.
     *
     * Kept solely so an upgraded install can still recognise messages the old pipeline had already
     * processed. Without it, every message in the catch-up window would look new under the tiered
     * scheme and be re-notified once, on upgrade. The cache is allowed to answer "already seen" —
     * INV-1 makes `peek() == true` a safe skip — it is only forbidden from being the *authority*.
     *
     * Takes the untrimmed body and the raw `Double` amount because that is what the old call sites
     * passed; do not "clean up" the formatting here or the key stops matching.
     */
    fun legacyBodyKey(senderHeader: String, amount: Double, type: String, rawBody: String): String =
        md5("$senderHeader-$amount-$type-${rawBody.take(30)}")

    /**
     * Reproduces the catch-up scanner's timestamp key: `MD5("$sender-$amount-$type-$timestamp")`.
     * Same upgrade-compatibility purpose as [legacyBodyKey]; the realtime receiver never wrote it.
     */
    fun legacyTimestampKey(
        senderHeader: String,
        amount: Double,
        type: String,
        timestamp: Long
    ): String = md5("$senderHeader-$amount-$type-$timestamp")

    /**
     * Key for the transfer-reconciliation handshake, scoped by account and time bucket.
     *
     * The old key was `"recon_${amount}_${type}"` — unscoped, so a ₹500 debit on any account
     * suppressed the *next* ₹500 credit on any account, indefinitely. The hour bucket bounds the
     * window; the account keeps two accounts from consuming each other's marker.
     */
    fun reconciliationKey(amount: Double, type: String, accountLast4: String?, timestamp: Long): String {
        val account = normalizeAccount(accountLast4) ?: "unknown"
        val hourBucket = timestamp / 3_600_000L
        return "recon_${paise(amount)}_${normalizeType(type)}_${account}_$hourBucket"
    }

    // ── Normalisation helpers ─────────────────────────────────────────────

    /** Digits only; `null` when no usable account identity is present. */
    private fun normalizeAccount(accountLast4: String?): String? =
        accountLast4?.filter { it in '0'..'9' }?.takeIf { it.isNotEmpty() }

    /** "Bank of Baroda" -> "BANKOFBARODA", so DLT header variants map to one bank. */
    private fun normalizeBank(bankName: String): String =
        bankName.uppercase().filter { it in '0'..'9' || it in 'A'..'Z' }

    private fun normalizeSender(senderHeader: String): String = senderHeader.trim().uppercase()

    private fun normalizeType(type: String): String = type.trim().uppercase()

    /** Whitespace runs collapsed, case folded — the full body, never a prefix. */
    private fun normalizeBody(rawBody: String): String =
        rawBody.trim().uppercase().replace(WHITESPACE_RUN, " ")

    /** Integer paise, so no locale or `Double.toString` formatting can shift the key. */
    private fun paise(amount: Double): Long = Math.round(amount * 100.0)

    private val WHITESPACE_RUN = Regex("\\s+")

    // ── Digests ───────────────────────────────────────────────────────────

    private fun sha256(input: String): String = digest("SHA-256", input)

    private fun md5(input: String): String = digest("MD5", input)

    private fun digest(algorithm: String, input: String): String =
        MessageDigest.getInstance(algorithm)
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
