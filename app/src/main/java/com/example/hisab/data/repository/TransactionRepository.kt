package com.example.hisab.data.repository

import com.example.hisab.data.db.dao.CategoryBreakdownRaw
import com.example.hisab.data.db.dao.DailyTotalRaw
import com.example.hisab.data.db.dao.TransactionDao
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.CategoryBreakdown
import com.example.hisab.data.model.DailyTotal
import com.example.hisab.data.model.MonthlySummary
import com.example.hisab.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private var autoBackupManager: com.example.hisab.data.backup.AutoBackupManager? = null
) {

    fun setAutoBackupManager(manager: com.example.hisab.data.backup.AutoBackupManager) {
        this.autoBackupManager = manager
    }

    // ── CRUD ─────────────────────────────────────────────

    suspend fun insert(transaction: TransactionEntity): Long {
        val result = transactionDao.insert(transaction)
        autoBackupManager?.performBackup()
        return result
    }

    suspend fun update(transaction: TransactionEntity) {
        transactionDao.update(transaction)
        autoBackupManager?.performBackup()
    }

    suspend fun delete(transaction: TransactionEntity) {
        transactionDao.delete(transaction)
        autoBackupManager?.performBackup()
    }

    suspend fun deleteById(id: Long) {
        transactionDao.deleteById(id)
        autoBackupManager?.performBackup()
    }

    suspend fun getById(id: Long): TransactionEntity? =
        transactionDao.getById(id)

    // ── Monthly Data ─────────────────────────────────────

    fun getTransactionsForMonth(yearMonth: YearMonth): Flow<List<TransactionEntity>> {
        val start = yearMonth.atDay(1)
        val end = yearMonth.atEndOfMonth()
        return transactionDao.getTransactionsBetween(start, end)
    }

    fun getMonthlySummary(yearMonth: YearMonth): Flow<MonthlySummary> {
        val start = yearMonth.atDay(1)
        val end = yearMonth.atEndOfMonth()
        return combine(
            transactionDao.getTotalIncome(start, end),
            transactionDao.getTotalExpense(start, end),
            transactionDao.getTransactionCount(start, end)
        ) { income, expense, count ->
            MonthlySummary(
                totalIncome = income,
                totalExpense = expense,
                netBalance = income - expense,
                transactionCount = count
            )
        }
    }

    fun getTotalExpenseBetween(startDate: LocalDate, endDate: LocalDate): Flow<Double> =
        transactionDao.getTotalExpense(startDate, endDate)

    // ── Analytics Data ───────────────────────────────────

    fun getDailyExpenseTotals(yearMonth: YearMonth): Flow<List<DailyTotal>> {
        val start = yearMonth.atDay(1)
        val end = yearMonth.atEndOfMonth()
        return transactionDao.getDailyTotals(
            TransactionType.EXPENSE.name, start, end
        ).map { list ->
            list.map { DailyTotal(it.date, it.totalAmount) }
        }
    }

    fun getDailyIncomeTotals(yearMonth: YearMonth): Flow<List<DailyTotal>> {
        val start = yearMonth.atDay(1)
        val end = yearMonth.atEndOfMonth()
        return transactionDao.getDailyTotals(
            TransactionType.INCOME.name, start, end
        ).map { list ->
            list.map { DailyTotal(it.date, it.totalAmount) }
        }
    }

    fun getCategoryBreakdown(
        yearMonth: YearMonth,
        type: TransactionType = TransactionType.EXPENSE
    ): Flow<List<CategoryBreakdown>> {
        val start = yearMonth.atDay(1)
        val end = yearMonth.atEndOfMonth()
        return transactionDao.getCategoryBreakdown(type.name, start, end)
            .map { list ->
                val total = list.sumOf { it.totalAmount }
                list.map { raw ->
                    CategoryBreakdown(
                        categoryId = raw.categoryId,
                        categoryName = raw.categoryName,
                        colorHex = raw.colorHex,
                        iconName = raw.iconName,
                        totalAmount = raw.totalAmount,
                        percentage = if (total > 0) (raw.totalAmount / total) * 100 else 0.0,
                        transactionCount = raw.transactionCount
                    )
                }
            }
    }

    fun getHighestSpendingDay(yearMonth: YearMonth): Flow<DailyTotal?> {
        val start = yearMonth.atDay(1)
        val end = yearMonth.atEndOfMonth()
        return transactionDao.getHighestSpendingDay(start, end)
            .map { it?.let { raw -> DailyTotal(raw.date, raw.totalAmount) } }
    }

    /**
     * Returns monthly income/expense/net for the last [months] months
     * ending at [endMonth]. Used for the grouped bar chart.
     */
    suspend fun getMonthlyTrend(endMonth: YearMonth, months: Int = 6): List<Triple<YearMonth, Double, Double>> {
        val result = mutableListOf<Triple<YearMonth, Double, Double>>()
        for (i in (months - 1) downTo 0) {
            val ym = endMonth.minusMonths(i.toLong())
            val start = ym.atDay(1)
            val end = ym.atEndOfMonth()
            val transactions = transactionDao.getTransactionsBetweenSync(start, end)
            val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            result.add(Triple(ym, income, expense))
        }
        return result
    }

    /**
     * Returns average spending per weekday for a given month.
     * Index 1=Monday ... 7=Sunday (ISO day-of-week).
     */
    suspend fun getWeekdayAverages(yearMonth: YearMonth): Map<Int, Double> {
        val start = yearMonth.atDay(1)
        val end = yearMonth.atEndOfMonth()
        val transactions = transactionDao.getTransactionsBetweenSync(start, end)
            .filter { it.type == TransactionType.EXPENSE }

        val grouped = transactions.groupBy { it.date.dayOfWeek.value }
        val weekdayTotals = mutableMapOf<Int, Double>()

        for (dayOfWeek in 1..7) {
            val dayTransactions = grouped[dayOfWeek] ?: emptyList()
            val total = dayTransactions.sumOf { it.amount }
            // Count distinct dates for this weekday in the month
            val distinctDays = (1..yearMonth.lengthOfMonth())
                .map { yearMonth.atDay(it) }
                .count { it.dayOfWeek.value == dayOfWeek && it <= LocalDate.now() }
            weekdayTotals[dayOfWeek] = if (distinctDays > 0) total / distinctDays else 0.0
        }
        return weekdayTotals
    }

    /**
     * Returns top N category totals over last [months] months for trend lines.
     */
    suspend fun getCategoryTrend(
        endMonth: YearMonth,
        months: Int = 6,
        topN: Int = 5
    ): Map<String, List<Pair<YearMonth, Double>>> {
        // First, find top N categories by total in the end month
        val endStart = endMonth.atDay(1)
        val endEnd = endMonth.atEndOfMonth()
        val endTransactions = transactionDao.getTransactionsBetweenSync(endStart, endEnd)
            .filter { it.type == TransactionType.EXPENSE }
        val topCategories = endTransactions.groupBy { it.categoryId }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .take(topN)
            .map { it.key }

        if (topCategories.isEmpty()) return emptyMap()

        // Now get monthly data for each top category
        val allStart = endMonth.minusMonths((months - 1).toLong()).atDay(1)
        val allTransactions = transactionDao.getTransactionsBetweenSync(allStart, endEnd)
            .filter { it.type == TransactionType.EXPENSE && it.categoryId in topCategories }

        // We need category names — group by categoryId, get first for name
        // For simplicity, return categoryId as key (ViewModel will resolve names)
        val result = mutableMapOf<String, List<Pair<YearMonth, Double>>>()
        val byCat = allTransactions.groupBy { it.categoryId }

        for (catId in topCategories) {
            val catTxns = byCat[catId] ?: emptyList()
            val monthlyData = (0 until months).map { i ->
                val ym = endMonth.minusMonths((months - 1 - i).toLong())
                val total = catTxns
                    .filter { YearMonth.from(it.date) == ym }
                    .sumOf { it.amount }
                Pair(ym, total)
            }
            result[catId.toString()] = monthlyData
        }
        return result
    }

    // ── Filtered Queries ─────────────────────────────────

    fun getFilteredTransactions(
        startDate: LocalDate,
        endDate: LocalDate,
        type: TransactionType? = null,
        categoryId: Long? = null,
        account: String? = null,
        searchQuery: String? = null
    ): Flow<List<TransactionEntity>> {
        return transactionDao.getFilteredTransactions(
            startDate, endDate, type?.name, categoryId, account, searchQuery
        )
    }

    fun searchTransactions(query: String): Flow<List<TransactionEntity>> =
        transactionDao.searchTransactions(query)

    fun getAllAccounts(): Flow<List<String>> =
        transactionDao.getAllAccounts()

    suspend fun getAllTransactionsSync(): List<TransactionEntity> =
        transactionDao.getAllTransactionsSync()
}
