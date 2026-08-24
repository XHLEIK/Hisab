package com.example.hisab.data.model

/**
 * How certain the app is that a row reflects a real, correctly-valued transaction.
 *
 * Persisted as the plain [name] string in the nullable `confidence` column (schema v8+).
 * Legacy rows created before v8 carry `confidence = null`.
 *
 * The distinction is user-visible: [INFERRED] rows are balance-derived guesses (amount is a
 * net discrepancy, direction/merchant unknown) and must never be rendered as if [CONFIRMED].
 */
enum class TransactionConfidence {
    /** Parsed directly from a bank SMS with a known amount and direction. */
    CONFIRMED,

    /** Derived from a balance discrepancy; the amount is a net guess, not a parsed value. */
    INFERRED,

    /** Set/verified by the user. */
    MANUAL
}
