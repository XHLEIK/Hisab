package com.example.hisab.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.hisab.data.model.TransactionType

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name", "type"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val iconName: String,
    val colorHex: String,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0
)
