package com.example.hisab.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.hisab.data.model.TransactionType
import java.time.LocalDate

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_DEFAULT
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["date"]),
        Index(value = ["type"]),
        Index(value = ["account"]),
        // INV-2: closes the cross-table dedup hole — a message already materialised into
        // history cannot be re-claimed as a new pending row. NULLs stay unconstrained.
        Index(value = ["sourceMessageHash"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: TransactionType,
    val categoryId: Long,
    val account: String = "Cash",
    val toAccount: String? = null,
    val date: LocalDate,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),

    /** Canonical message identity this row was materialised from; null for manual entries. */
    val sourceMessageHash: String? = null,

    /** [com.example.hisab.data.model.TransactionSource] name; null on pre-v8 rows. */
    val source: String? = null,

    /** [com.example.hisab.data.model.TransactionConfidence] name; null on pre-v8 rows. */
    val confidence: String? = null,

    /** Bank-issued reference carried over from the originating SMS, if any. */
    val referenceNumber: String? = null,

    /** Subtype; null on pre-v9 rows means NORMAL for backward compat. */
    val subtype: String? = null
)
