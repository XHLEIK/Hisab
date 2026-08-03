package com.example.hisab.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType
import com.example.hisab.data.repository.CategoryRepository
import com.example.hisab.data.repository.TransactionRepository
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

import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.repository.AccountRepository
import kotlinx.coroutines.Dispatchers

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
        _searchQuery
    ) { month, type, account, query ->
        FilterParams(month, type, account, query)
    }.flatMapLatest { params ->
        val start = params.month.atDay(1)
        val end = params.month.atEndOfMonth()
        // Load all transactions for month (with type + search filter in DB for perf)
        // Account filter is done in-memory to handle renamed accounts correctly
        transactionRepository.getFilteredTransactions(
            startDate = start,
            endDate = end,
            type = params.type,
            account = null,   // always null — we filter by account in-memory below
            searchQuery = params.query.ifBlank { null }
        ).map { allTxns ->
            if (params.account == null) {
                allTxns
            } else {
                allTxns.filter { tx ->
                    tx.account == params.account ||
                    (tx.type == TransactionType.TRANSFER && tx.toAccount == params.account)
                }
            }
        }
    }.stateIn(
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

    private data class FilterParams(
        val month: YearMonth,
        val type: TransactionType?,
        val account: String?,
        val query: String
    )

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
