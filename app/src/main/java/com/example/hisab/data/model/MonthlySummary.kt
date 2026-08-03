package com.example.hisab.data.model

data class MonthlySummary(
    val totalIncome: Double,
    val totalExpense: Double,
    val netBalance: Double,
    val transactionCount: Int
)
