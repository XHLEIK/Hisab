package com.example.hisab.data.sms

import com.example.hisab.data.db.dao.AccountDao
import com.example.hisab.data.db.dao.CategoryBreakdownRaw
import com.example.hisab.data.db.dao.CategoryDao
import com.example.hisab.data.db.dao.DailyTotalRaw
import com.example.hisab.data.db.dao.PendingTransactionDao
import com.example.hisab.data.db.dao.TransactionDao
import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.db.entity.PendingTransactionEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import kotlin.math.abs

/**
 * JVM fakes for everything [TransactionProcessor] depends on.
 *
 * These exist because the pipeline's defects were never in the parser — they were in ordering,
 * atomicity and dedup, none of which a parser test can reach. The fakes therefore reproduce the
 * *semantics the processor relies on*, not just the signatures:
 *
 * - [FakePendingTransactionDao.insertClaim] honours `UNIQUE(sourceMessageHash)` with IGNORE, so a
 *   second claim of one identity really does return `-1` (INV-2).
 * - `insert` on both fake DAOs honours REPLACE, including the fact that a hash collision *deletes*
 *   the incumbent row — the hazard the production code guards with `takeIf`/`getBySourceHash`.
 * - Every DAO method the processor does not use throws instead of returning a plausible empty value,
 *   so a future change that starts calling one fails loudly rather than silently reading `null`.
 *
 * A shared [journal] records the order of the operations that INV-7 and design principle 3 are about
 * (claim, commit, notify, cache mark, backup), which is the only way to assert ordering rather than
 * merely asserting outcomes.
 */

private fun unused(name: String): Nothing =
    error("$name is not part of the SMS pipeline; the fake deliberately has no behaviour for it")

// ── Gateways ──────────────────────────────────────────────────────────────

class FakeSmsHashCache(private val journal: MutableList<String>) : SmsHashCache {
    val keys = mutableSetOf<String>()
    var markCalls = 0
        private set

    override fun peek(key: String): Boolean = keys.contains(key)

    override fun mark(vararg keys: String) {
        markCalls++
        journal += "mark"
        this.keys += keys
    }

    override fun forget(key: String) {
        journal += "forget"
        keys -= key
    }
}

/**
 * Runs the block and journals the commit. A throw from inside the block propagates without a commit
 * entry, which — for the single-write claim transaction — is a faithful stand-in for a rollback: the
 * fake DAO throws *before* storing anything.
 */
class FakeAtomicDb(private val journal: MutableList<String>) : AtomicDb {
    var transactions = 0
        private set

    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        transactions++
        journal += "tx-begin"
        val result = block()
        journal += "commit"
        return result
    }
}

class FakeSmsNotifier(private val journal: MutableList<String>) : SmsNotifier {
    /** What the platform reports back. `false` models POST_NOTIFICATIONS denied / channel off. */
    var accept = true

    /** Models a notification manager that throws rather than declining. */
    var throwOnPost = false

    val bankPosts = mutableListOf<PendingTransactionEntity>()
    val missedPosts = mutableListOf<PendingTransactionEntity>()
    val mergePosts = mutableListOf<Triple<Long, String, String>>()

    override suspend fun postBankTransaction(pending: PendingTransactionEntity): Boolean {
        journal += "notify"
        if (throwOnPost) throw RuntimeException("notification manager unavailable")
        bankPosts += pending
        return accept
    }

    override suspend fun postMissedTransaction(
        pending: PendingTransactionEntity,
        actualBalance: Double,
        expectedBalance: Double
    ): Boolean {
        journal += "notify-missed"
        missedPosts += pending
        return accept
    }

    override suspend fun postAutoMerge(
        transactionId: Long,
        sourceAccount: String,
        targetAccount: String,
        amount: Double
    ): Boolean {
        journal += "notify-merge"
        mergePosts += Triple(transactionId, sourceAccount, targetAccount)
        return accept
    }
}

class FakeBackupTrigger(private val journal: MutableList<String>) : BackupTrigger {
    var throwOnPerform = false
    var performCalls = 0
        private set

    override suspend fun perform() {
        performCalls++
        journal += "backup"
        if (throwOnPerform) throw RuntimeException("backup failed to serialise")
    }
}

/**
 * Records into the same [journal] as everything else, which is the point: the diagnostics write must
 * land *after* the notification attempt (design principle 3), and a journal is the only way to say so.
 *
 * [throwOnRecord] models the log itself being broken — a corrupt prefs file, a full disk. A witness
 * that can change what it witnessed is not a witness, so the processor must absorb this.
 */
class FakeSmsDiagnosticsLog(private val journal: MutableList<String>) : SmsDiagnosticsLog {
    val entries = mutableListOf<SmsDiagnosticEntry>()
    var throwOnRecord = false

    override suspend fun record(entry: SmsDiagnosticEntry) {
        journal += "diag"
        if (throwOnRecord) throw RuntimeException("diagnostics store unavailable")
        entries += entry
    }

    override suspend fun recent(): List<SmsDiagnosticEntry> = entries.reversed()

    /** The outcome codes recorded so far, oldest first. */
    fun outcomes(): List<String> = entries.map { it.outcome }
}

// ── DAOs ──────────────────────────────────────────────────────────────────

class FakePendingTransactionDao(
    private val journal: MutableList<String> = mutableListOf()
) : PendingTransactionDao {

    val rows = LinkedHashMap<Long, PendingTransactionEntity>()
    private var nextId = 1L

    var claimCalls = 0
        private set

    /** Set to make the next [insertClaim] throw *before* storing — a rolled-back claim. */
    var throwOnClaim = false

    /**
     * An opposite-direction row that [findMatchingOppositePending] reports but which no longer
     * exists, so `deleteById` returns 0. This is the auto-merge lost race: another consumer took the
     * row between the lookup and the merge.
     */
    var phantomOpposite: PendingTransactionEntity? = null

    fun seed(row: PendingTransactionEntity): PendingTransactionEntity {
        val id = if (row.id != 0L) row.id else nextId++
        if (id >= nextId) nextId = id + 1
        val stored = row.copy(id = id)
        rows[id] = stored
        return stored
    }

    override fun getAllPendingFlow(): Flow<List<PendingTransactionEntity>> =
        flowOf(rows.values.sortedByDescending { it.timestamp })

    override suspend fun getAllPendingSync(): List<PendingTransactionEntity> =
        rows.values.sortedByDescending { it.timestamp }

    override suspend fun getById(id: Long): PendingTransactionEntity? = rows[id]

    /** REPLACE: a hash collision evicts the incumbent row. */
    override suspend fun insert(pending: PendingTransactionEntity): Long {
        pending.sourceMessageHash?.let { hash ->
            rows.values.filter { it.sourceMessageHash == hash && it.id != pending.id }
                .forEach { rows.remove(it.id) }
        }
        return seed(pending).id
    }

    /** IGNORE: the UNIQUE index rejects a second claim of the same identity with -1 (INV-2). */
    override suspend fun insertClaim(pending: PendingTransactionEntity): Long {
        claimCalls++
        journal += "claim"
        if (throwOnClaim) error("database unavailable")
        val hash = pending.sourceMessageHash
        if (hash != null && rows.values.any { it.sourceMessageHash == hash }) return -1L
        return seed(pending).id
    }

    override suspend fun delete(pending: PendingTransactionEntity) {
        rows.remove(pending.id)
    }

    override suspend fun findMatchingOppositePending(
        amount: Double,
        oppositeType: String,
        minTimestamp: Long
    ): PendingTransactionEntity? {
        phantomOpposite?.let { return it }
        return rows.values
            .filter { it.amount == amount && it.type == oppositeType && it.timestamp >= minTimestamp }
            .maxByOrNull { it.timestamp }
    }

    override suspend fun deleteById(id: Long): Int = if (rows.remove(id) != null) 1 else 0

    override suspend fun getBySourceHash(hash: String): PendingTransactionEntity? =
        rows.values.firstOrNull { it.sourceMessageHash == hash }

    override suspend fun findInferredMarker(
        amount: Double,
        accountLast4: String?
    ): PendingTransactionEntity? = rows.values
        .filter { it.source == "BALANCE_RECONCILIATION" && abs(it.amount - amount) < 1.0 }
        .filter { accountLast4 == null || it.accountLast4 == accountLast4 }
        .maxByOrNull { it.timestamp }

    override suspend fun getUnnotified(
        minTimestamp: Long,
        maxAttempts: Int
    ): List<PendingTransactionEntity> = rows.values
        .filter { it.notificationPostedAt == null }
        .filter { it.confidence == "CONFIRMED" }
        .filter { it.source == null || it.source != "BALANCE_RECONCILIATION" }
        .filter { it.timestamp > minTimestamp }
        .filter { it.notificationAttempts < maxAttempts }
        .sortedBy { it.timestamp }

    override suspend fun markNotified(id: Long, postedAt: Long): Int {
        val row = rows[id] ?: return 0
        rows[id] = row.copy(
            notificationPostedAt = postedAt,
            notificationAttempts = row.notificationAttempts + 1
        )
        return 1
    }

    override suspend fun markNotificationAttempted(id: Long): Int {
        val row = rows[id] ?: return 0
        rows[id] = row.copy(notificationAttempts = row.notificationAttempts + 1)
        return 1
    }

    override suspend fun deleteAll() = rows.clear()
}

class FakeTransactionDao : TransactionDao {

    val rows = LinkedHashMap<Long, TransactionEntity>()
    private var nextId = 1L

    fun seed(row: TransactionEntity): TransactionEntity {
        val id = if (row.id != 0L) row.id else nextId++
        if (id >= nextId) nextId = id + 1
        val stored = row.copy(id = id)
        rows[id] = stored
        return stored
    }

    /** REPLACE, including the destructive side of a hash collision. */
    override suspend fun insert(transaction: TransactionEntity): Long {
        transaction.sourceMessageHash?.let { hash ->
            rows.values.filter { it.sourceMessageHash == hash && it.id != transaction.id }
                .forEach { rows.remove(it.id) }
        }
        return seed(transaction).id
    }

    override suspend fun update(transaction: TransactionEntity) {
        rows[transaction.id] = transaction
    }

    override suspend fun delete(transaction: TransactionEntity) {
        rows.remove(transaction.id)
    }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }

    override suspend fun getById(id: Long): TransactionEntity? = rows[id]

    override suspend fun findMatchingManualTransaction(
        amount: Double,
        type: String,
        minDate: LocalDate,
        account: String?
    ): TransactionEntity? = rows.values
        .filter { abs(it.amount - amount) < 1.0 }
        .filter { it.type.name == type }
        .filter { !it.date.isBefore(minDate) }
        // The load-bearing clause: rows the pipeline auto-logged carry a non-MANUAL source and must
        // not match, or the engine suppresses on its own output (root cause #4).
        .filter { it.source == "MANUAL" || it.source == null }
        .filter { account == null || it.account == account }
        .maxByOrNull { it.createdAt }

    override suspend fun getBySourceHash(hash: String): TransactionEntity? =
        rows.values.firstOrNull { it.sourceMessageHash == hash }

    override suspend fun getTransactionsBetweenSync(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<TransactionEntity> = rows.values
        .filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
        .sortedByDescending { it.date }

    override suspend fun getUserEnteredForAccountBetween(
        account: String,
        afterCreatedAt: Long,
        untilCreatedAt: Long
    ): List<TransactionEntity> = rows.values
        .filter { it.createdAt > afterCreatedAt && it.createdAt <= untilCreatedAt }
        .filter { it.account == account || it.toAccount == account }
        // The load-bearing pair: only user-originated movements. An SMS-derived row's own AvlBal was
        // already folded into lastKnownBalance, so netting it double-counts.
        .filter { it.source == "MANUAL" || it.source == null }
        .filter { it.sourceMessageHash == null }
        .sortedBy { it.createdAt }

    override suspend fun getAllTransactionsSync(): List<TransactionEntity> = rows.values.toList()

    override fun getTransactionsBetween(startDate: LocalDate, endDate: LocalDate): Flow<List<TransactionEntity>> =
        unused("getTransactionsBetween")

    override fun getTotalIncome(startDate: LocalDate, endDate: LocalDate): Flow<Double> =
        unused("getTotalIncome")

    override fun getTotalExpense(startDate: LocalDate, endDate: LocalDate): Flow<Double> =
        unused("getTotalExpense")

    override fun getTransactionCount(startDate: LocalDate, endDate: LocalDate): Flow<Int> =
        unused("getTransactionCount")

    override fun getDailyTotals(type: String, startDate: LocalDate, endDate: LocalDate): Flow<List<DailyTotalRaw>> =
        unused("getDailyTotals")

    override fun getCategoryBreakdown(
        type: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<CategoryBreakdownRaw>> = unused("getCategoryBreakdown")

    override fun searchTransactions(query: String): Flow<List<TransactionEntity>> =
        unused("searchTransactions")

    override suspend fun updateAccountName(oldName: String, newName: String) =
        unused("updateAccountName")

    override suspend fun updateToAccountName(oldName: String, newName: String) =
        unused("updateToAccountName")

    override fun getFilteredTransactions(
        startDate: LocalDate,
        endDate: LocalDate,
        type: String?,
        categoryId: Long?,
        account: String?,
        searchQuery: String?
    ): Flow<List<TransactionEntity>> = unused("getFilteredTransactions")

    override fun getHighestSpendingDay(startDate: LocalDate, endDate: LocalDate): Flow<DailyTotalRaw?> =
        unused("getHighestSpendingDay")

    override fun getAllAccounts(): Flow<List<String>> = unused("getAllAccounts")

    override suspend fun getAllDistinctAccountNamesSync(): List<String> =
        unused("getAllDistinctAccountNamesSync")

    override fun getAllTransactionsFlow(): Flow<List<TransactionEntity>> =
        unused("getAllTransactionsFlow")

    override suspend fun repairCorruptedTransferCategories() =
        unused("repairCorruptedTransferCategories")

    override suspend fun getGrossExpenseForCategory(categoryId: Long): Double =
        rows.values.filter { it.categoryId == categoryId && it.type == TransactionType.EXPENSE && it.subtype != "SPLIT_REIMBURSEMENT" }.sumOf { it.amount }

    override suspend fun getSplitReimbursementForCategory(categoryId: Long): Double =
        rows.values.filter { it.categoryId == categoryId && it.subtype == "SPLIT_REIMBURSEMENT" }.sumOf { it.amount }

    override suspend fun getGrossExpenseBetween(startDate: LocalDate, endDate: LocalDate): Double =
        rows.values.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) && it.type == TransactionType.EXPENSE && it.subtype != "SPLIT_REIMBURSEMENT" }.sumOf { it.amount }

    override suspend fun getSplitReimbursementBetween(startDate: LocalDate, endDate: LocalDate): Double =
        rows.values.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) && it.subtype == "SPLIT_REIMBURSEMENT" }.sumOf { it.amount }

    override fun getTotalSplitReimbursement(startDate: LocalDate, endDate: LocalDate): Flow<Double> =
        flowOf(rows.values.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) && it.subtype == "SPLIT_REIMBURSEMENT" }.sumOf { it.amount })
}

class FakeAccountDao(seed: List<AccountEntity> = emptyList()) : AccountDao {

    val rows = LinkedHashMap<Long, AccountEntity>()
    private var nextId = 1L
    val updates = mutableListOf<AccountEntity>()

    init {
        seed.forEach { insertSync(it) }
    }

    private fun insertSync(account: AccountEntity): Long {
        val id = if (account.id != 0L) account.id else nextId++
        if (id >= nextId) nextId = id + 1
        rows[id] = account.copy(id = id)
        return id
    }

    override suspend fun insert(account: AccountEntity): Long = insertSync(account)

    override suspend fun update(account: AccountEntity) {
        rows[account.id] = account
        updates += account
    }

    override suspend fun getAllSync(): List<AccountEntity> =
        rows.values.sortedWith(compareByDescending<AccountEntity> { it.isPrimary }.thenBy { it.id })

    override suspend fun getPrimaryAccount(): AccountEntity? = rows.values.firstOrNull { it.isPrimary }

    override suspend fun getCount(): Int = rows.size

    override suspend fun insertAll(accounts: List<AccountEntity>) = unused("insertAll")

    override suspend fun delete(account: AccountEntity) = unused("delete")

    override fun getAll(): Flow<List<AccountEntity>> = unused("getAll")

    override fun getAllNames(): Flow<List<String>> = unused("getAllNames")
}

class FakeCategoryDao(seed: List<CategoryEntity> = defaultCategories()) : CategoryDao {

    val rows = seed.toMutableList()

    override suspend fun getAllSync(): List<CategoryEntity> = rows.toList()

    override suspend fun getById(id: Long): CategoryEntity? = rows.firstOrNull { it.id == id }

    override suspend fun getCount(): Int = rows.size

    override suspend fun insert(category: CategoryEntity): Long = unused("insert")

    override suspend fun insertAll(categories: List<CategoryEntity>) = unused("insertAll")

    override suspend fun update(category: CategoryEntity) = unused("update")

    override suspend fun delete(category: CategoryEntity) = unused("delete")

    override fun getAllByType(type: String): Flow<List<CategoryEntity>> = unused("getAllByType")

    override fun getAll(): Flow<List<CategoryEntity>> = unused("getAll")

    companion object {
        fun defaultCategories(): List<CategoryEntity> = listOf(
            CategoryEntity(id = 1, name = "Other Expense", type = TransactionType.EXPENSE, iconName = "📋", colorHex = "#888888"),
            CategoryEntity(id = 2, name = "Other Income", type = TransactionType.INCOME, iconName = "💰", colorHex = "#4CAF50"),
            CategoryEntity(id = 3, name = "Savings", type = TransactionType.TRANSFER, iconName = "🐷", colorHex = "#2196F3")
        )
    }
}
