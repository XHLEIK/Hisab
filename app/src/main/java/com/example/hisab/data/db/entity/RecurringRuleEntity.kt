package com.example.hisab.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.hisab.data.model.TransactionType

@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val categoryId: Long,
    val account: String = "Primary Bank",
    val toAccount: String? = null,
    val dayOfMonth: Int, // 1..31
    val isActive: Boolean = true
)
