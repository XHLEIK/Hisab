package com.example.hisab.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_transactions")
data class PendingTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: String, // "DEBIT" or "CREDIT"
    val bankName: String,
    val accountLast4: String? = null,
    val merchantOrPayee: String? = null,
    val rawSmsBody: String,
    val senderHeader: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
