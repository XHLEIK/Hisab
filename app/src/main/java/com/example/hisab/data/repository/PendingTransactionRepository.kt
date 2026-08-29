package com.example.hisab.data.repository

import androidx.room.withTransaction
import com.example.hisab.data.backup.AutoBackupManager
import com.example.hisab.data.db.HisabDatabase
import com.example.hisab.data.db.dao.PendingTransactionDao
import com.example.hisab.data.db.dao.TransactionDao
import com.example.hisab.data.db.entity.PendingTransactionEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionConfidence
import kotlinx.coroutines.flow.Flow

class PendingTransactionRepository(
    private val pendingDao: PendingTransactionDao,
    private val transactionDao: TransactionDao,
    private val db: HisabDatabase,
    private val autoBackupManager: AutoBackupManager? = null
) {
    fun getAllPendingFlow(): Flow<List<PendingTransactionEntity>> =
        pendingDao.getAllPendingFlow()

    suspend fun getAllPendingSync(): List<PendingTransactionEntity> =
        pendingDao.getAllPendingSync()

    suspend fun getById(id: Long): PendingTransactionEntity? =
        pendingDao.getById(id)

    suspend fun insert(pending: PendingTransactionEntity): Long =
        pendingDao.insert(pending)

    suspend fun delete(pending: PendingTransactionEntity) =
        pendingDao.delete(pending)

    suspend fun deleteById(id: Long) =
        pendingDao.deleteById(id)

    /**
     * Materialises a pending SMS row into a real transaction as one all-or-nothing step (INV-3).
     *
     * Returns `false` when the pending row is already gone — two taps on the same dashboard card, or
     * a notification action that got there first. Previously this was an insert followed by an
     * unrelated delete, so a crash in between either duplicated the transaction or destroyed the
     * pending row with nothing to show for it.
     *
     * Provenance rules:
     * - `sourceMessageHash` is carried forward so the message stays claimed once the pending row is
     *   gone (cross-table dedup, INV-2). The `takeIf` is a restore-safety guard: `insert()` is
     *   REPLACE, so writing a hash another row already holds would *delete* that row. Dropping the
     *   hash costs dedup, not data.
     * - `source` keeps the *pending row's* origin (`SMS_REALTIME`/`SMS_CATCHUP`), deliberately NOT
     *   `MANUAL`. `TransactionDao.findMatchingManualTransaction` suppresses a new SMS when a matching
     *   manual row exists; stamping approved SMS rows as `MANUAL` would make every approval suppress
     *   the next same-amount SMS on that account — root cause #4 rebuilt one layer up.
     * - `confidence` becomes `MANUAL`: a human confirmed the amount and category.
     *
     * The backup runs after the commit, never inside it — it serialises the whole database.
     */
    suspend fun approve(pending: PendingTransactionEntity, transaction: TransactionEntity): Boolean {
        val logged = db.withTransaction {
            val live = pendingDao.getById(pending.id) ?: return@withTransaction false
            pendingDao.delete(live)
            transactionDao.insert(
                transaction.copy(
                    sourceMessageHash = live.sourceMessageHash
                        ?.takeIf { transactionDao.getBySourceHash(it) == null },
                    source = live.source,
                    confidence = TransactionConfidence.MANUAL.name,
                    referenceNumber = live.referenceNumber
                )
            )
            true
        }
        if (logged) autoBackupManager?.performBackup()
        return logged
    }
}
