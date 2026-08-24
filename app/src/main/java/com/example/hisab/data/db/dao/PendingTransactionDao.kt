package com.example.hisab.data.db.dao

import androidx.room.*
import com.example.hisab.data.db.entity.PendingTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTransactionDao {
    @Query("SELECT * FROM pending_transactions ORDER BY timestamp DESC")
    fun getAllPendingFlow(): Flow<List<PendingTransactionEntity>>

    @Query("SELECT * FROM pending_transactions ORDER BY timestamp DESC")
    suspend fun getAllPendingSync(): List<PendingTransactionEntity>

    @Query("SELECT * FROM pending_transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pending: PendingTransactionEntity): Long

    /**
     * INV-2: the one authoritative dedup operation. Attempts to claim a message identity.
     *
     * Returns the new row id, or **-1** when the UNIQUE index on `sourceMessageHash` rejects the
     * insert — which is the *only* proof of a duplicate the pipeline is allowed to act on.
     * Unlike [insert] (REPLACE) this never destroys the incumbent row.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClaim(pending: PendingTransactionEntity): Long

    @Delete
    suspend fun delete(pending: PendingTransactionEntity)

    @Query("SELECT * FROM pending_transactions WHERE amount = :amount AND type = :oppositeType AND timestamp >= :minTimestamp ORDER BY timestamp DESC LIMIT 1")
    suspend fun findMatchingOppositePending(amount: Double, oppositeType: String, minTimestamp: Long): PendingTransactionEntity?

    /**
     * Returns the number of rows actually deleted. The count matters: auto-merge consumes an
     * opposite pending row, and a `0` means a concurrent path already consumed it — a lost race
     * that must abort the merge rather than fabricate a half-transfer.
     */
    @Query("DELETE FROM pending_transactions WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM pending_transactions WHERE sourceMessageHash = :hash LIMIT 1")
    suspend fun getBySourceHash(hash: String): PendingTransactionEntity?

    /**
     * Finds an existing balance-reconciliation marker for the same amount + account, so repeated
     * discrepancy detections do not stack up duplicate INFERRED rows.
     */
    @Query(
        """
        SELECT * FROM pending_transactions
        WHERE source = 'BALANCE_RECONCILIATION'
          AND ABS(amount - :amount) < 1.0
          AND (:accountLast4 IS NULL OR accountLast4 = :accountLast4)
        ORDER BY timestamp DESC LIMIT 1
        """
    )
    suspend fun findInferredMarker(amount: Double, accountLast4: String?): PendingTransactionEntity?

    /**
     * INV-6 bounded recovery: confirmed claims whose Stage-1 notification never went out.
     *
     * Excludes BALANCE_RECONCILIATION rows (they post their own missed-transaction alert) and
     * anything past the age or attempt cap — those stay valid and dashboard-visible, they just
     * stop being retried automatically.
     */
    @Query(
        """
        SELECT * FROM pending_transactions
        WHERE notificationPostedAt IS NULL
          AND confidence = 'CONFIRMED'
          AND (source IS NULL OR source != 'BALANCE_RECONCILIATION')
          AND timestamp > :minTimestamp
          AND notificationAttempts < :maxAttempts
        ORDER BY timestamp ASC
        """
    )
    suspend fun getUnnotified(minTimestamp: Long, maxAttempts: Int): List<PendingTransactionEntity>

    /** Records a successful notification post. See the SEMANTIC note on `notificationPostedAt`. */
    @Query("UPDATE pending_transactions SET notificationPostedAt = :postedAt, notificationAttempts = notificationAttempts + 1 WHERE id = :id")
    suspend fun markNotified(id: Long, postedAt: Long): Int

    /** Records a failed notification attempt — increments the counter that drives the INV-6 cap. */
    @Query("UPDATE pending_transactions SET notificationAttempts = notificationAttempts + 1 WHERE id = :id")
    suspend fun markNotificationAttempted(id: Long): Int

    @Query("DELETE FROM pending_transactions")
    suspend fun deleteAll()
}
