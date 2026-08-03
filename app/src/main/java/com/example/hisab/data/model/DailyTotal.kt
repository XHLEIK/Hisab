package com.example.hisab.data.model

import java.time.LocalDate

data class DailyTotal(
    val date: LocalDate,
    val totalAmount: Double
)
