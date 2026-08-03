package com.example.hisab.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.CategoryBreakdown
import com.example.hisab.data.model.DailyTotal
import com.example.hisab.data.model.MonthlySummary
import com.example.hisab.data.model.TransactionType
import com.example.hisab.data.repository.AccountRepository
import com.example.hisab.data.repository.CategoryRepository
import com.example.hisab.data.repository.TransactionRepository
import com.example.hisab.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class CategoryFilterType { ALL, INCOME, EXPENSE, TRANSFERS }

enum class BarChartTimeFilter { TODAY, SPECIFIC_DATE, WEEKLY, FIFTEEN_DAYS, MONTHLY }

data class BarChartDataPoint(
    val label: String,
    val income: Double,
    val expense: Double,
    val transfer: Double
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository? = null
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    // ── Categories & Accounts ─────────────────────────────────────
    val categories: StateFlow<List<CategoryEntity>> = categoryRepository
        .getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val accounts: StateFlow<List<AccountEntity>> = (accountRepository?.getAllAccounts() ?: MutableStateFlow(emptyList()))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val accountNames: StateFlow<List<String>> = accounts
        .map { list ->
            if (list.isEmpty()) listOf("Primary Bank", "Secondary Bank", "Savings")
            else list.map { it.name }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf("Primary Bank", "Secondary Bank", "Savings")
        )

    // ── Monthly Transactions ──────────────────────────────────────
    val monthlyTransactions: StateFlow<List<TransactionEntity>> = _selectedMonth
        .flatMapLatest { month ->
            transactionRepository.getTransactionsForMonth(month)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // ── All Transactions (for balance & account calculation) ────────
    private val _allTransactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val allTransactions: StateFlow<List<TransactionEntity>> = _allTransactions.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            transactionRepository.getFilteredTransactions(
                startDate = LocalDate.of(2020, 1, 1),
                endDate = LocalDate.of(2030, 12, 31)
            ).collect { list ->
                _allTransactions.value = list
            }
        }
    }

    // ── KPI Overview ──────────────────────────────────────────────
    val todayExpense: StateFlow<Double> = allTransactions.map { txns ->
        val today = LocalDate.now()
        txns.filter { it.type == TransactionType.EXPENSE && it.date == today }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpenseMonthToDate: StateFlow<Double> = monthlyTransactions.map { txns ->
        val today = LocalDate.now()
        txns.filter { it.type == TransactionType.EXPENSE && (it.date <= today || _selectedMonth.value.isBefore(YearMonth.now())) }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Account Balances
    val primaryAccountName: StateFlow<String> = accounts.map { list ->
        list.firstOrNull { it.isPrimary }?.name ?: list.firstOrNull()?.name ?: "Primary Bank"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Primary Bank")

    val selectedSecondaryAccountName = MutableStateFlow<String?>(null)

    val primaryAccountBalance: StateFlow<Double> = combine(allTransactions, primaryAccountName) { txns, primaryName ->
        calculateBalanceForAccount(primaryName, txns)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val secondaryAccounts: StateFlow<List<String>> = combine(accountNames, primaryAccountName) { names, primaryName ->
        names.filter { it != primaryName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedAccountBalance: StateFlow<Double> = combine(allTransactions, selectedSecondaryAccountName, secondaryAccounts) { txns, selected, secList ->
        val target = selected ?: secList.firstOrNull()
        if (target != null) calculateBalanceForAccount(target, txns) else 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private fun calculateBalanceForAccount(accName: String, txns: List<TransactionEntity>): Double {
        var balance = 0.0
        txns.forEach { tx ->
            if (tx.type == TransactionType.INCOME && tx.account == accName) {
                balance += tx.amount
            } else if (tx.type == TransactionType.EXPENSE && tx.account == accName) {
                balance -= tx.amount
            } else if (tx.type == TransactionType.TRANSFER) {
                if (tx.account == accName) balance -= tx.amount
                if (tx.toAccount == accName) balance += tx.amount
            }
        }
        return balance
    }

    // ── Category Breakdown with Filter ────────────────────────────
    val categoryFilter = MutableStateFlow(CategoryFilterType.EXPENSE)

    val filteredCategoryBreakdown: StateFlow<List<CategoryBreakdown>> = combine(
        monthlyTransactions,
        categories,
        categoryFilter
    ) { txns, catList, filter ->
        val catMap = catList.associateBy { it.id }
        val filteredTxns = when (filter) {
            CategoryFilterType.ALL -> txns
            CategoryFilterType.INCOME -> txns.filter { it.type == TransactionType.INCOME }
            CategoryFilterType.EXPENSE -> txns.filter { it.type == TransactionType.EXPENSE }
            CategoryFilterType.TRANSFERS -> txns.filter { it.type == TransactionType.TRANSFER }
        }

        val totalAmount = filteredTxns.sumOf { it.amount }

        // In ALL mode group by (categoryId + type) so income and expense of same
        // category appear as separate, distinctly colored slices
        val grouped = if (filter == CategoryFilterType.ALL) {
            filteredTxns.groupBy { "${it.categoryId}_${it.type.name}" }
        } else {
            filteredTxns.groupBy { it.categoryId.toString() }
        }

        grouped.map { (key, items) ->
            val firstItem = items.first()
            val cat = catMap[firstItem.categoryId]
            val catTotal = items.sumOf { it.amount }
            val txType = firstItem.type
            val isTransfer = txType == TransactionType.TRANSFER
            val isIncome = txType == TransactionType.INCOME

            val rawColor = cat?.colorHex ?: "#607D8B"
            val color = when {
                isTransfer -> "#00695C"   // dark teal-green — different shade of green from income
                isIncome -> rawColor       // keep original category color (Salary = #4CAF50 green, etc.)
                else -> rawColor           // original color for expense
            }

            val name = when {
                isTransfer && cat == null -> "Transfer"
                isIncome -> "${cat?.name ?: "Unknown"}"
                else -> cat?.name ?: "Unknown"
            }
            val icon = if (isTransfer && cat == null) "SwapHoriz" else cat?.iconName ?: "MoreHoriz"

            CategoryBreakdown(
                categoryId = firstItem.categoryId,
                categoryName = name,
                colorHex = color,
                iconName = icon,
                totalAmount = catTotal,
                percentage = if (totalAmount > 0) (catTotal / totalAmount) * 100 else 0.0,
                transactionCount = items.size
            )
        }.sortedByDescending { it.totalAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Spending Calendar (Daily Expense & Income Totals) ─────────
    val dailyExpenseTotals: StateFlow<List<DailyTotal>> = _selectedMonth
        .flatMapLatest { month ->
            transactionRepository.getDailyExpenseTotals(month)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val dailyIncomeTotals: StateFlow<List<DailyTotal>> = _selectedMonth
        .flatMapLatest { month ->
            transactionRepository.getDailyIncomeTotals(month)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // ── Time Filtered Bar Chart ────────────────────────────────────
    val barChartFilter = MutableStateFlow(BarChartTimeFilter.MONTHLY)
    val barChartSpecificDate = MutableStateFlow(LocalDate.now())
    val weekOffset = MutableStateFlow(0) // 0 = current week, -1 = last week, etc.

    fun navigateWeek(delta: Int) {
        weekOffset.value += delta
    }

    val barChartData: StateFlow<List<BarChartDataPoint>> = combine(
        _selectedMonth,
        barChartFilter,
        barChartSpecificDate,
        weekOffset
    ) { month, filter, specificDate, offset ->
        Tuple4(month, filter, specificDate, offset)
    }.flatMapLatest { (month, filter, specificDate, offset) ->
        val today = LocalDate.now()
        val (startDate, endDate) = when (filter) {
            BarChartTimeFilter.TODAY -> Pair(today, today)
            BarChartTimeFilter.SPECIFIC_DATE -> Pair(specificDate, specificDate)
            BarChartTimeFilter.WEEKLY -> {
                val anchor = if (month == YearMonth.now()) today else month.atEndOfMonth()
                val targetEnd = anchor.plusWeeks(offset.toLong())
                val targetStart = targetEnd.minusDays(6)
                Pair(targetStart, targetEnd)
            }
            BarChartTimeFilter.FIFTEEN_DAYS -> {
                val isCurrentMonth = month == YearMonth.now()
                val currentDay = if (isCurrentMonth) today.dayOfMonth else 30
                if (currentDay <= 15) {
                    val start = month.atDay(1)
                    val end = if (isCurrentMonth) today else month.atDay(15)
                    Pair(start, end)
                } else {
                    val start = month.atDay(16)
                    val end = if (isCurrentMonth) today else month.atEndOfMonth()
                    Pair(start, end)
                }
            }
            BarChartTimeFilter.MONTHLY -> Pair(month.atDay(1), month.atEndOfMonth())
        }

        transactionRepository.getFilteredTransactions(startDate, endDate)
            .map { txns ->
                when (filter) {
                    BarChartTimeFilter.TODAY -> {
                        listOf(
                            BarChartDataPoint(
                                label = "Today",
                                income = txns.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                                expense = txns.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                                transfer = txns.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }
                            )
                        )
                    }
                    BarChartTimeFilter.SPECIFIC_DATE -> {
                        listOf(
                            BarChartDataPoint(
                                label = DateUtils.formatShort(specificDate),
                                income = txns.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                                expense = txns.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                                transfer = txns.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }
                            )
                        )
                    }
                    BarChartTimeFilter.WEEKLY -> {
                        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toInt()
                        (0..totalDays).map { dayOffset ->
                            val date = startDate.plusDays(dayOffset.toLong())
                            val dTxns = txns.filter { it.date == date }
                            BarChartDataPoint(
                                label = date.format(DateTimeFormatter.ofPattern("EEE\ndd")),
                                income = dTxns.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                                expense = dTxns.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                                transfer = dTxns.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }
                            )
                        }
                    }
                    BarChartTimeFilter.FIFTEEN_DAYS -> {
                        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toInt()
                        (0..totalDays).map { dayOffset ->
                            val date = startDate.plusDays(dayOffset.toLong())
                            val dTxns = txns.filter { it.date == date }
                            BarChartDataPoint(
                                label = date.format(DateTimeFormatter.ofPattern("dd\nMMM")),
                                income = dTxns.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                                expense = dTxns.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                                transfer = dTxns.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }
                            )
                        }
                    }
                    BarChartTimeFilter.MONTHLY -> {
                        val daysInMonth = month.lengthOfMonth()
                        (1..daysInMonth).map { day ->
                            val date = month.atDay(day)
                            val dTxns = txns.filter { it.date == date }
                            BarChartDataPoint(
                                label = "$day",
                                income = dTxns.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                                expense = dTxns.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                                transfer = dTxns.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }
                            )
                        }
                    }
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    // ── Expense Leaderboard ───────────────────────────────────────
    val topExpensesLeaderboard: StateFlow<List<TransactionEntity>> = monthlyTransactions.map { txns ->
        txns.filter { it.type == TransactionType.EXPENSE }
            .sortedByDescending { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectMonth(yearMonth: YearMonth) {
        _selectedMonth.value = yearMonth
    }

    fun selectSecondaryAccount(accName: String) {
        selectedSecondaryAccountName.value = accName
    }

    fun setCategoryFilter(filter: CategoryFilterType) {
        categoryFilter.value = filter
    }

    fun setBarChartFilter(filter: BarChartTimeFilter) {
        barChartFilter.value = filter
    }

    fun setBarChartSpecificDate(date: LocalDate) {
        barChartSpecificDate.value = date
    }

    fun addAccount(name: String, type: String = "SECONDARY") {
        viewModelScope.launch(Dispatchers.IO) {
            accountRepository?.insertAccount(
                AccountEntity(name = name, type = type, isPrimary = false)
            )
        }
    }

    class Factory(
        private val transactionRepository: TransactionRepository,
        private val categoryRepository: CategoryRepository,
        private val accountRepository: AccountRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AnalyticsViewModel(transactionRepository, categoryRepository, accountRepository) as T
        }
    }
}
