package com.example.hisab.ui.screens

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.YearMonth

/**
 * Shared month selection state across Dashboard, Analytics, and History screens.
 * When any screen changes the month, all other screens reflect the same selection.
 */
object SharedMonthState {
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    fun selectMonth(yearMonth: YearMonth) {
        _selectedMonth.value = yearMonth
    }
}
