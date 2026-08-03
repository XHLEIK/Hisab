package com.example.hisab.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.hisab.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TransactionDao {

    // ── CRUD ─────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    // ── Monthly Queries ──────────────────────────────────

    @Query("""
        SELECT * FROM transactions 
        WHERE date >= :startDate AND date <= :endDate
        ORDER BY date DESC, createdAt DESC
    """)
    fun getTransactionsBetween(startDate: LocalDate, endDate: LocalDate): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions 
        WHERE date >= :startDate AND date <= :endDate
        ORDER BY date DESC, createdAt DESC
    """)
    suspend fun getTransactionsBetweenSync(startDate: LocalDate, endDate: LocalDate): List<TransactionEntity>

    // ── Aggregation Queries ──────────────────────────────

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions 
        WHERE type = 'INCOME' AND date >= :startDate AND date <= :endDate
    """)
    fun getTotalIncome(startDate: LocalDate, endDate: LocalDate): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions 
        WHERE type = 'EXPENSE' AND date >= :startDate AND date <= :endDate
    """)
    fun getTotalExpense(startDate: LocalDate, endDate: LocalDate): Flow<Double>

    @Query("""
        SELECT COUNT(*) FROM transactions 
        WHERE date >= :startDate AND date <= :endDate
    """)
    fun getTransactionCount(startDate: LocalDate, endDate: LocalDate): Flow<Int>

    // ── Daily Totals (for line chart & heatmap) ──────────

    @Query("""
        SELECT date, SUM(amount) as totalAmount 
        FROM transactions 
        WHERE type = :type AND date >= :startDate AND date <= :endDate
        GROUP BY date 
        ORDER BY date ASC
    """)
    fun getDailyTotals(type: String, startDate: LocalDate, endDate: LocalDate): Flow<List<DailyTotalRaw>>

    // ── Category Breakdown (for donut chart) ─────────────

    @Query("""
        SELECT 
            t.categoryId,
            c.name as categoryName,
            c.colorHex,
            c.iconName,
            SUM(t.amount) as totalAmount,
            COUNT(t.id) as transactionCount
        FROM transactions t
        INNER JOIN categories c ON t.categoryId = c.id
        WHERE t.type = :type AND t.date >= :startDate AND t.date <= :endDate
        GROUP BY t.categoryId
        ORDER BY totalAmount DESC
    """)
    fun getCategoryBreakdown(type: String, startDate: LocalDate, endDate: LocalDate): Flow<List<CategoryBreakdownRaw>>

    // ── Search ───────────────────────────────────────────

    @Query("""
        SELECT * FROM transactions 
        WHERE notes LIKE '%' || :query || '%'
        ORDER BY date DESC, createdAt DESC
    """)
    fun searchTransactions(query: String): Flow<List<TransactionEntity>>

    @Query("UPDATE transactions SET account = :newName WHERE account = :oldName")
    suspend fun updateAccountName(oldName: String, newName: String)

    @Query("UPDATE transactions SET toAccount = :newName WHERE toAccount = :oldName")
    suspend fun updateToAccountName(oldName: String, newName: String)

    // ── Filtered Queries ─────────────────────────────────

    @Query("""
        SELECT * FROM transactions 
        WHERE date >= :startDate AND date <= :endDate
        AND (:type IS NULL OR type = :type)
        AND (:categoryId IS NULL OR categoryId = :categoryId)
        AND (
            :account IS NULL 
            OR account = :account 
            OR (type = 'TRANSFER' AND toAccount = :account)
        )
        AND (:searchQuery IS NULL OR notes LIKE '%' || :searchQuery || '%')
        ORDER BY date DESC, createdAt DESC
    """)
    fun getFilteredTransactions(
        startDate: LocalDate,
        endDate: LocalDate,
        type: String?,
        categoryId: Long?,
        account: String?,
        searchQuery: String?
    ): Flow<List<TransactionEntity>>

    // ── Highest Spending Day ─────────────────────────────

    @Query("""
        SELECT date, SUM(amount) as totalAmount 
        FROM transactions 
        WHERE type = 'EXPENSE' AND date >= :startDate AND date <= :endDate
        GROUP BY date 
        ORDER BY totalAmount DESC 
        LIMIT 1
    """)
    fun getHighestSpendingDay(startDate: LocalDate, endDate: LocalDate): Flow<DailyTotalRaw?>

    // ── All Accounts ─────────────────────────────────────

    @Query("SELECT DISTINCT account FROM transactions ORDER BY account ASC")
    fun getAllAccounts(): Flow<List<String>>

    @Query("SELECT DISTINCT account FROM transactions UNION SELECT DISTINCT toAccount FROM transactions WHERE toAccount IS NOT NULL")
    suspend fun getAllDistinctAccountNamesSync(): List<String>

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC")
    suspend fun getAllTransactionsSync(): List<TransactionEntity>
}

/**
 * Raw result class for daily totals from Room query.
 * Room needs a concrete class to map GROUP BY results.
 */
data class DailyTotalRaw(
    val date: LocalDate,
    val totalAmount: Double
)

/**
 * Raw result class for category breakdown from Room query.
 */
data class CategoryBreakdownRaw(
    val categoryId: Long,
    val categoryName: String,
    val colorHex: String,
    val iconName: String,
    val totalAmount: Double,
    val transactionCount: Int
)
