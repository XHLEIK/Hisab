package com.example.hisab.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.MonthlySummary
import com.example.hisab.data.model.TransactionType
import com.example.hisab.data.repository.AccountRepository
import com.example.hisab.data.repository.CategoryRepository
import com.example.hisab.data.repository.LimitType
import com.example.hisab.data.repository.SpendingLimitConfig
import com.example.hisab.data.repository.SpendingLimitRepository
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class SpendingLimitStatus(
    val isEnabled: Boolean = false,
    val limitType: LimitType = LimitType.DAILY,
    val limitAmount: Double = 0.0,
    val spentAmount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val progressPercentage: Float = 0f,
    val isExceeded: Boolean = false,
    val isWarning: Boolean = false,
    val periodLabel: String = "Daily Limit"
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository? = null,
    private val limitRepository: SpendingLimitRepository? = null
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    val monthlySummary: StateFlow<MonthlySummary> = _selectedMonth
        .flatMapLatest { month ->
            transactionRepository.getMonthlySummary(month)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MonthlySummary(0.0, 0.0, 0.0, 0)
        )

    val recentTransactions: StateFlow<List<TransactionEntity>> = _selectedMonth
        .flatMapLatest { month ->
            transactionRepository.getTransactionsForMonth(month)
        }
        .map { it.take(10) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository
        .getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val accounts: StateFlow<List<String>> = (accountRepository?.getAllAccountNames() ?: transactionRepository.getAllAccounts())
        .map { accs ->
            if (accs.isEmpty()) listOf("Primary Bank", "Secondary Bank", "Savings")
            else accs
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf("Primary Bank", "Secondary Bank", "Savings")
        )

    val savingsRate: StateFlow<Double> = monthlySummary
        .map { summary ->
            if (summary.totalIncome > 0) {
                (summary.netBalance / summary.totalIncome) * 100
            } else 0.0
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    val topCategoryBreakdown: StateFlow<List<Pair<CategoryEntity, Double>>> = combine(
        _selectedMonth.flatMapLatest { transactionRepository.getTransactionsForMonth(it) },
        categories
    ) { txns, cats ->
        val catMap = cats.associateBy { it.id }
        txns.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapNotNull { (catId, list) ->
                val cat = catMap[catId]
                if (cat != null) cat to list.sumOf { it.amount } else null
            }
            .sortedByDescending { it.second }
            .take(3)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val accountBalances: StateFlow<Map<String, Double>> = combine(
        _selectedMonth.flatMapLatest { transactionRepository.getTransactionsForMonth(it) },
        accounts
    ) { txns, accList ->
        val balances = accList.associateWith { 0.0 }.toMutableMap()
        txns.forEach { tx ->
            when (tx.type) {
                TransactionType.INCOME -> {
                    balances[tx.account] = (balances[tx.account] ?: 0.0) + tx.amount
                }
                TransactionType.EXPENSE -> {
                    balances[tx.account] = (balances[tx.account] ?: 0.0) - tx.amount
                }
                TransactionType.TRANSFER -> {
                    balances[tx.account] = (balances[tx.account] ?: 0.0) - tx.amount
                    if (tx.toAccount != null && balances.containsKey(tx.toAccount)) {
                        balances[tx.toAccount] = (balances[tx.toAccount] ?: 0.0) + tx.amount
                    }
                }
            }
        }
        balances
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    val dailyAverage: StateFlow<Double> = monthlySummary
        .map { summary ->
            val days = DateUtils.daysElapsed(_selectedMonth.value)
            if (days > 0) summary.totalExpense / days else 0.0
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    // ── Safe Daily Pace ─────────────────────────────────────────────
    val safeDailyPace: StateFlow<Double> = combine(
        monthlySummary,
        _selectedMonth
    ) { summary, month ->
        val today = LocalDate.now()
        val totalDaysInMonth = month.lengthOfMonth()
        val currentDay = if (month == YearMonth.now()) today.dayOfMonth else totalDaysInMonth
        val remainingDays = (totalDaysInMonth - currentDay + 1).coerceAtLeast(1)
        val available = summary.netBalance.coerceAtLeast(0.0)
        available / remainingDays
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    // ── Primary + Secondary Combined Remaining Balance ──────────────
    val primaryAndSecondaryBalance: StateFlow<Double> = accountBalances
        .map { balances ->
            balances.filterKeys { accName ->
                val lower = accName.lowercase()
                !lower.contains("cash") && !lower.contains("savings") && !lower.contains("saving")
            }.values.sum()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    // ── Total Accumulated Savings ───────────────────────────────────
    val totalSavingsAmount: StateFlow<Double> = _selectedMonth
        .flatMapLatest { month ->
            transactionRepository.getTransactionsForMonth(month)
        }
        .map { txns ->
            // Strictly sum of transfers to Savings accounts (returns 0.0 when no transfers exist)
            txns.filter { 
                it.type == TransactionType.TRANSFER && 
                (it.toAccount?.contains("Savings", ignoreCase = true) == true || 
                 it.toAccount?.contains("SAVING", ignoreCase = true) == true)
            }.sumOf { it.amount }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    // ── Spending Limit Configuration & Real-Time Status ──────────────
    val limitConfig: StateFlow<SpendingLimitConfig> = (limitRepository?.limitConfig ?: flowOf(SpendingLimitConfig()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpendingLimitConfig())

    val spendingLimitStatus: StateFlow<SpendingLimitStatus> = combine(
        limitConfig,
        _selectedMonth
    ) { config, month ->
        Pair(config, month)
    }.flatMapLatest { (config, month) ->
        if (!config.isEnabled || config.amount <= 0) {
            flowOf(SpendingLimitStatus(isEnabled = false))
        } else {
            val (start, end, label) = when (config.type) {
                LimitType.DAILY -> {
                    val today = LocalDate.now()
                    Triple(today, today, "Daily Limit")
                }
                LimitType.WEEKLY -> {
                    val today = LocalDate.now()
                    val startOfWeek = today.with(DayOfWeek.MONDAY)
                    Triple(startOfWeek, today, "Weekly Limit")
                }
                LimitType.MONTHLY -> {
                    Triple(month.atDay(1), month.atEndOfMonth(), "Monthly Limit (${month.format(DateTimeFormatter.ofPattern("MMM yyyy"))})")
                }
                LimitType.SPECIFIC_DAY -> {
                    val targetDate = config.specificDate
                    Triple(targetDate, targetDate, "Limit for ${DateUtils.formatShort(targetDate)}")
                }
            }

            transactionRepository.getTotalExpenseBetween(start, end).map { spent ->
                val remaining = config.amount - spent
                val pct = (spent / config.amount).toFloat().coerceAtLeast(0f)
                SpendingLimitStatus(
                    isEnabled = true,
                    limitType = config.type,
                    limitAmount = config.amount,
                    spentAmount = spent,
                    remainingAmount = remaining,
                    progressPercentage = pct,
                    isExceeded = spent > config.amount,
                    isWarning = pct in 0.75f..1.00f,
                    periodLabel = label
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SpendingLimitStatus()
    )

    fun selectMonth(yearMonth: YearMonth) {
        _selectedMonth.value = yearMonth
    }

    fun addTransaction(transaction: TransactionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            transactionRepository.insert(transaction)
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            transactionRepository.update(transaction)
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            transactionRepository.delete(transaction)
        }
    }

    fun addAccount(name: String, type: String = "SECONDARY") {
        viewModelScope.launch(Dispatchers.IO) {
            accountRepository?.insertAccount(
                AccountEntity(name = name, type = type, isPrimary = false)
            )
        }
    }

    fun saveSpendingLimit(type: LimitType, amount: Double, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch(Dispatchers.IO) {
            limitRepository?.saveLimitConfig(
                SpendingLimitConfig(
                    type = type,
                    amount = amount,
                    specificDate = date,
                    isEnabled = true
                )
            )
        }
    }

    fun disableSpendingLimit() {
        viewModelScope.launch(Dispatchers.IO) {
            limitRepository?.clearLimit()
        }
    }

    class Factory(
        private val transactionRepository: TransactionRepository,
        private val categoryRepository: CategoryRepository,
        private val accountRepository: AccountRepository? = null,
        private val limitRepository: SpendingLimitRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(transactionRepository, categoryRepository, accountRepository, limitRepository) as T
        }
    }
}
