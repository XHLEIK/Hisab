package com.example.hisab.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_transactions",
    indices = [
        // INV-2: Room is the sole dedup authority. SQLite permits unlimited NULLs in a UNIQUE
        // index, so every legacy/manual/inferred row (hash == null) coexists freely while any
        // two rows claiming the same message identity collide.
        Index(value = ["sourceMessageHash"], unique = true)
    ]
)
data class PendingTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: String, // "DEBIT" or "CREDIT"
    val bankName: String,
    val accountLast4: String? = null,
    val merchantOrPayee: String? = null,
    val endingBalance: Double? = null,
    val rawSmsBody: String,
    val senderHeader: String? = null,
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * Canonical message identity (see `SmsHash.canonical`). Null for rows that do not originate
     * from a single SMS (manual entries, balance-reconciliation inferences). Never backfilled
     * for historic rows.
     */
    val sourceMessageHash: String? = null,

    /** [com.example.hisab.data.model.TransactionSource] name; null on pre-v8 rows. */
    val source: String? = null,

    /** [com.example.hisab.data.model.TransactionConfidence] name; null on pre-v8 rows. */
    val confidence: String? = null,

    /** Bank-issued reference (Ref / UPI Ref No / RRN / Txn ID), normalized and validated. */
    val referenceNumber: String? = null,

    /**
     * When the Stage-1 notification `post()` last returned without throwing.
     *
     * SEMANTIC (binding): this means *only* "post() did not throw". It is never evidence that
     * the user saw or read the notification.
     */
    val notificationPostedAt: Long? = null,

    /**
     * Count of notification attempts, successful or not. Drives the INV-6 attempt cap so a
     * permanently-failing notification cannot loop forever.
     *
     * The `defaultValue` annotation is REQUIRED: it must match `DEFAULT 0` in MIGRATION_7_8 or
     * Room's schema validation rejects the migrated database.
     */
    @ColumnInfo(defaultValue = "0")
    val notificationAttempts: Int = 0
)
