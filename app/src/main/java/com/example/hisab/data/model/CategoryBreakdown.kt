package com.example.hisab.data.model

data class CategoryBreakdown(
    val categoryId: Long,
    val categoryName: String,
    val colorHex: String,
    val iconName: String,
    val totalAmount: Double,
    val percentage: Double,
    val transactionCount: Int
)
