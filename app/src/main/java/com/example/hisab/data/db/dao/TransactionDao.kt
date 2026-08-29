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

    /**
     * Finds a recent **user-entered** transaction that an incoming SMS is probably duplicating.
     *
     * `AND source = 'MANUAL'` is load-bearing. Without it this query also matched rows the SMS
     * pipeline itself had auto-logged, so every second same-amount SMS was suppressed as a
     * "manual duplicate" — the engine poisoned its own dedup. Legacy rows (`source IS NULL`)
     * predate the SMS pipeline's provenance tagging and were all hand-entered, so they count as
     * manual too.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE ABS(amount - :amount) < 1.0
          AND type = :type
          AND date >= :minDate
          AND (source = 'MANUAL' OR source IS NULL)
          AND (:account IS NULL OR account = :account)
        ORDER BY createdAt DESC LIMIT 1
        """
    )
    suspend fun findMatchingManualTransaction(
        amount: Double,
        type: String,
        minDate: LocalDate,
        account: String? = null
    ): TransactionEntity?

    /**
     * Closes the cross-table dedup hole: a message already materialised into history must not be
     * re-claimable as a fresh pending row.
     */
    @Query("SELECT * FROM transactions WHERE sourceMessageHash = :hash LIMIT 1")
    suspend fun getBySourceHash(hash: String): TransactionEntity?

    /**
     * User-entered transactions on one account inside a wall-clock window, used to net the balance the
     * app *should* expect before it accuses the user of an unlogged transaction.
     *
     * Two filters carry the whole meaning:
     *
     * `(source = 'MANUAL' OR source IS NULL) AND sourceMessageHash IS NULL` — only movements the app
     * learned about from the **user**. Anything derived from a bank message is deliberately excluded:
     * that message's own `AvlBal` was already written into `accounts.lastKnownBalance` when it was
     * processed, so netting it again double-counts and manufactures a discrepancy exactly the size of
     * the transaction. This is the same "the user entered this" definition
     * [findMatchingManualTransaction] uses, on purpose — one notion of manual, not two.
     *
     * `createdAt` rather than `date` — the window's endpoints are `accounts.lastBalanceTimestamp` and
     * an SMS timestamp, both epoch millis, while `date` is only day-granular. A back-dated hand entry
     * therefore nets into the window it was *entered* in, which is the conservative direction: netting
     * can only shrink a discrepancy, never invent one.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE createdAt > :afterCreatedAt AND createdAt <= :untilCreatedAt
          AND (account = :account OR toAccount = :account)
          AND (source = 'MANUAL' OR source IS NULL)
          AND sourceMessageHash IS NULL
          AND (subtype IS NULL OR subtype != 'SPLIT_REIMBURSEMENT')
        ORDER BY createdAt ASC
        """
    )
    suspend fun getUserEnteredForAccountBetween(
        account: String,
        afterCreatedAt: Long,
        untilCreatedAt: Long
    ): List<TransactionEntity>

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
        SELECT COALESCE(SUM(CASE WHEN subtype = 'SPLIT_REIMBURSEMENT' THEN -amount ELSE amount END), 0.0)
        FROM transactions WHERE type = 'EXPENSE' AND date >= :startDate AND date <= :endDate
    """)
    fun getTotalExpense(startDate: LocalDate, endDate: LocalDate): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions 
        WHERE type = 'EXPENSE' AND subtype = 'SPLIT_REIMBURSEMENT' AND date >= :startDate AND date <= :endDate
    """)
    fun getTotalSplitReimbursement(startDate: LocalDate, endDate: LocalDate): Flow<Double>

    @Query("""
        SELECT COUNT(*) FROM transactions 
        WHERE date >= :startDate AND date <= :endDate
    """)
    fun getTransactionCount(startDate: LocalDate, endDate: LocalDate): Flow<Int>

    // ── Daily Totals (for line chart & heatmap) ──────────

    @Query("""
        SELECT date, SUM(amount) as totalAmount 
        FROM transactions 
        WHERE type = :type AND (subtype IS NULL OR subtype != 'SPLIT_REIMBURSEMENT') AND date >= :startDate AND date <= :endDate
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
        WHERE t.type = :type AND (t.subtype IS NULL OR t.subtype != 'SPLIT_REIMBURSEMENT') AND t.date >= :startDate AND t.date <= :endDate
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
        WHERE type = 'EXPENSE' AND (subtype IS NULL OR subtype != 'SPLIT_REIMBURSEMENT') AND date >= :startDate AND date <= :endDate
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

    @Query("UPDATE transactions SET categoryId = (SELECT id FROM categories WHERE type = 'TRANSFER' AND name = 'Savings' LIMIT 1) WHERE type = 'TRANSFER' AND (categoryId IN (SELECT id FROM categories WHERE type != 'TRANSFER') OR categoryId NOT IN (SELECT id FROM categories))")
    suspend fun repairCorruptedTransferCategories()

    // ── Split Reimbursement — gross / reimbursed / net (single source of truth via SplitAccounting) ──

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE type = 'EXPENSE' AND (subtype IS NULL OR subtype = 'NORMAL') AND categoryId = :categoryId")
    suspend fun getGrossExpenseForCategory(categoryId: Long): Double

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE type = 'EXPENSE' AND subtype = 'SPLIT_REIMBURSEMENT' AND categoryId = :categoryId")
    suspend fun getSplitReimbursementForCategory(categoryId: Long): Double

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE type = 'EXPENSE' AND (subtype IS NULL OR subtype = 'NORMAL') AND date >= :startDate AND date <= :endDate")
    suspend fun getGrossExpenseBetween(startDate: LocalDate, endDate: LocalDate): Double

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE type = 'EXPENSE' AND subtype = 'SPLIT_REIMBURSEMENT' AND date >= :startDate AND date <= :endDate")
    suspend fun getSplitReimbursementBetween(startDate: LocalDate, endDate: LocalDate): Double
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
