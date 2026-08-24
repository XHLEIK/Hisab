package com.example.hisab.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["name"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String = "SECONDARY", // e.g., "PRIMARY", "SECONDARY", "SAVINGS", "CASH"
    val colorHex: String = "#2196F3",
    val isPrimary: Boolean = false,
    val bankCode: String? = null, // e.g., "BOB", "SBI", "HDFC", "ICICI", "AXIS"
    val accountLast4: String? = null, // e.g., "1234"
    val lastKnownBalance: Double? = null,
    val lastBalanceTimestamp: Long? = null
)
