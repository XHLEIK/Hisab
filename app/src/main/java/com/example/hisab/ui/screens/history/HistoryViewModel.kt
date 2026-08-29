package com.example.hisab.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType
import com.example.hisab.data.repository.AccountRepository
import com.example.hisab.data.repository.CategoryRepository
import com.example.hisab.data.repository.TransactionRepository
import com.example.hisab.util.CurrencyFormatter
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
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository? = null
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _selectedType = MutableStateFlow<TransactionType?>(null)
    val selectedType: StateFlow<TransactionType?> = _selectedType.asStateFlow()

    private val _selectedAccount = MutableStateFlow<String?>(null)
    val selectedAccount: StateFlow<String?> = _selectedAccount.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val transactions: StateFlow<List<TransactionEntity>> = combine(
        _selectedMonth,
        _selectedType,
        _selectedAccount,
        _searchQuery,
        categories
    ) { month, type, account, query, categoryList ->
        val catMap = categoryList.associateBy { it.id }
        val start = month.atDay(1)
        val end = month.atEndOfMonth()

        transactionRepository.getFilteredTransactions(
            startDate = start,
            endDate = end,
            type = null, // Filter by type in-memory
            account = null, // Filter by account in-memory
            searchQuery = null // Filter by search query in-memory
        ).map { allTxns ->
            allTxns.filter { tx ->
                val matchesType = (type == null || tx.type == type)
                val matchesAccount = (account == null || tx.account == account || (tx.type == TransactionType.TRANSFER && tx.toAccount == account))

                val matchesQuery = if (query.isBlank()) {
                    true
                } else {
                    val q = query.trim().lowercase()
                    val catName = catMap[tx.categoryId]?.name?.lowercase() ?: ""
                    val notes = tx.notes.lowercase()
                    val amountStr = tx.amount.toString()
                    val formattedAmount = CurrencyFormatter.format(tx.amount).lowercase()
                    val acc = tx.account.lowercase()
                    val toAcc = tx.toAccount?.lowercase() ?: ""

                    catName.contains(q) || notes.contains(q) || amountStr.contains(q) || formattedAmount.contains(q) || acc.contains(q) || toAcc.contains(q)
                }

                matchesType && matchesAccount && matchesQuery
            }
        }
    }.flatMapLatest { flow -> flow }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun selectMonth(yearMonth: YearMonth) {
        _selectedMonth.value = yearMonth
    }

    fun selectType(type: TransactionType?) {
        _selectedType.value = type
    }

    fun selectAccount(account: String?) {
        _selectedAccount.value = account
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.delete(transaction)
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.update(transaction)
        }
    }

    fun addTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.insert(transaction)
        }
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
            return HistoryViewModel(transactionRepository, categoryRepository, accountRepository) as T
        }
    }
}
