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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import com.example.hisab.data.db.entity.PendingTransactionEntity
import com.example.hisab.data.repository.PendingTransactionRepository

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
    private val limitRepository: SpendingLimitRepository? = null,
    private val pendingTransactionRepository: PendingTransactionRepository? = null
) : ViewModel() {

    init {
        viewModelScope.launch(Dispatchers.IO) {
            transactionRepository.repairCorruptedTransferCategories()
        }
    }

    val pendingTransactions: StateFlow<List<PendingTransactionEntity>> = (pendingTransactionRepository?.getAllPendingFlow() ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val recentExpenseCategories: StateFlow<List<CategoryEntity>> = combine(
        categories,
        transactionRepository.getAllTransactionsFlow()
    ) { cats, txs ->
        val expenseCats = cats.filter { it.type == TransactionType.EXPENSE }
        val lastUsed = txs.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.maxOf { it.createdAt } }
        expenseCats.sortedByDescending { lastUsed[it.id] ?: 0L }
    }.stateIn(
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

    /**
     * The accounts as entities, needed wherever a bank identity (`bankCode`/`accountLast4`) has to be
     * resolved to an account *name* — [accounts] carries names only. See [resolveAccountName].
     */
    val linkedAccounts: StateFlow<List<AccountEntity>> =
        (accountRepository?.getAllAccounts() ?: flowOf(emptyList()))
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
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

    // ── All-Time Live Account Balances — centralized via SplitAccounting (INV: split reimbursement is +balance) ──
    val accountBalances: StateFlow<Map<String, Double>> = combine(
        transactionRepository.getAllTransactionsFlow(),
        accounts
    ) { allTxns, accList ->
        accList.associate { acct -> acct to com.example.hisab.util.SplitAccounting.accountBalance(acct, allTxns) }
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

    // ── Primary + Secondary Live Account Balance for Hero Card ─────────
    // "Net Balance / Primary + Secondary Accounts": sum of every non-savings account's
    // CURRENT balance (see SplitAccounting.primaryPlusSecondaryBalance). A current-state
    // value, independent of the selected month — unlike the Income/Expense tiles, which
    // remain period metrics.
    val primaryAndSecondaryBalance: StateFlow<Double> = accountBalances
        .map { balances -> com.example.hisab.util.SplitAccounting.primaryPlusSecondaryBalance(balances) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    // ── Hero SAVINGS tile: monthly net transfer INTO savings (period metric) ──
    // Per user clarification: hero Income/Expense/Savings are month-scoped, only Net Balance is current-state.
    // Transfer activity is not a balance; hero Savings must be monthly flow, not current Savings account balance.
    val heroSavingsTransfer: StateFlow<Double> = combine(
        _selectedMonth.flatMapLatest { transactionRepository.getTransactionsForMonth(it) },
        accounts,
        linkedAccounts
    ) { monthTxns, accNames, accEntities ->
        val savingsName = accEntities.firstOrNull { com.example.hisab.util.SplitAccounting.isSavingsAccount(it) }?.name
            ?: accNames.firstOrNull { com.example.hisab.util.SplitAccounting.isSavingsAccountName(it) }
            ?: return@combine 0.0
        // Net savings flow = all inflows – all outflows touching the savings account.
        // Inflows: transfers INTO savings.
        // Outflows: transfers OUT of savings + expenses charged to the savings account.
        // Using accountBalance for the selected month keeps this consistent with the
        // Accounts Overview section (SplitAccounting is the single source of truth).
        com.example.hisab.util.SplitAccounting.accountBalance(savingsName, monthTxns)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    // Kept for AccountsOverview and Analytics account-detail (current-state). Hero no longer uses this.
    val savingsAccountBalance: StateFlow<Double> = combine(
        transactionRepository.getAllTransactionsFlow(),
        accounts,
        linkedAccounts
    ) { allTxns, accNames, accEntities ->
        val targetName = accEntities.firstOrNull { com.example.hisab.util.SplitAccounting.isSavingsAccount(it) }?.name
            ?: accNames.firstOrNull { com.example.hisab.util.SplitAccounting.isSavingsAccountName(it) }
            ?: return@combine 0.0
        com.example.hisab.util.SplitAccounting.accountBalance(targetName, allTxns)
    }.stateIn(
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

    fun addAccount(name: String, type: String = "SECONDARY", bankCode: String? = null, accountLast4: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            accountRepository?.insertAccount(
                AccountEntity(name = name, type = type, isPrimary = false, bankCode = bankCode, accountLast4 = accountLast4)
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

    /**
     * Which of the user's accounts a pending bank row belongs to.
     *
     * Prefer the account the SMS actually names — matching how the notification actions resolve it.
     * Falling straight through to "primary" logged a BOB debit against the primary account whenever
     * the user approved from the dashboard instead of the notification. Primary/first remains the
     * fallback so approval never fails.
     */
    fun resolveAccountName(
        pending: PendingTransactionEntity,
        candidates: List<AccountEntity>
    ): String {
        val matched = candidates.firstOrNull { acc ->
            acc.bankCode.equals(pending.bankName, ignoreCase = true) ||
                    (pending.accountLast4 != null && acc.accountLast4 == pending.accountLast4)
        } ?: candidates.firstOrNull { it.isPrimary } ?: candidates.firstOrNull()
        return matched?.name ?: "Primary Bank"
    }

    fun approvePendingTransaction(pending: PendingTransactionEntity, categoryName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val txType = if (pending.type == "CREDIT") TransactionType.INCOME else TransactionType.EXPENSE
            val categories = categoryRepository.getAllCategoriesSync()
            val matchedCat = categories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) && it.type == txType }
                ?: categories.firstOrNull { it.type == txType }
            val catId = matchedCat?.id ?: 1L

            val accName = resolveAccountName(pending, accountRepository?.getAllAccountsSync() ?: emptyList())

            val newTx = TransactionEntity(
                amount = pending.amount,
                type = txType,
                categoryId = catId,
                // The SMS's own date, not today's: a catch-up scan can surface a message up to 24h
                // old, and approving it used to file the expense on the wrong day.
                date = Instant.ofEpochMilli(pending.timestamp)
                    .atZone(ZoneId.systemDefault()).toLocalDate(),
                account = accName,
                notes = "Auto-logged from SMS"
            )

            // INV-3: one atomic step, and it carries the message identity + provenance forward.
            // See PendingTransactionRepository.approve.
            pendingTransactionRepository?.approve(pending, newTx)
        }
    }

    /**
     * Logs the transaction a user wrote by hand to explain an inferred balance discrepancy, consuming
     * the marker row in the same step.
     *
     * [PendingTransactionRepository.approve] is exactly the right primitive here, for a reason worth
     * stating: it preserves the marker's `source` (`BALANCE_RECONCILIATION`) on the saved transaction.
     * That keeps the row *out* of the balance-netting query, which is correct — the movement is already
     * inside `accounts.lastKnownBalance`, because the bank's own reported balance is what revealed the
     * gap in the first place. Re-netting it would manufacture a fresh discrepancy the same size.
     */
    fun logInferredActivity(pending: PendingTransactionEntity, transaction: TransactionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            pendingTransactionRepository?.approve(pending, transaction)
        }
    }

    fun dismissPendingTransaction(pending: PendingTransactionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            pendingTransactionRepository?.delete(pending)
        }
    }

    class Factory(
        private val transactionRepository: TransactionRepository,
        private val categoryRepository: CategoryRepository,
        private val accountRepository: AccountRepository? = null,
        private val limitRepository: SpendingLimitRepository? = null,
        private val pendingTransactionRepository: PendingTransactionRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(transactionRepository, categoryRepository, accountRepository, limitRepository, pendingTransactionRepository) as T
        }
    }
}
