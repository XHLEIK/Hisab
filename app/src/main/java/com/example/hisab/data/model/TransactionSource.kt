package com.example.hisab.data.model

/**
 * Provenance of a transaction / pending-transaction row: which pipeline stage created it.
 *
 * Persisted as the plain [name] string in the nullable `source` column (schema v8+).
 * Legacy rows created before v8 carry `source = null` and are treated as [MANUAL] by
 * convention (they were all user-entered or hand-approved).
 */
enum class TransactionSource {
    /** Created by [com.example.hisab.data.sms.SmsReceiver] from a live incoming SMS broadcast. */
    SMS_REALTIME,

    /** Created by [com.example.hisab.data.sms.SmsCatchUpSync] scanning the SMS inbox on app open. */
    SMS_CATCHUP,

    /** Inferred from a bank-reported ending-balance discrepancy, not from a parsed transaction SMS. */
    BALANCE_RECONCILIATION,

    /** Materialised from a pending row via a notification action button. */
    NOTIFICATION_ACTION,

    /** Entered or approved by the user directly. */
    MANUAL
}
