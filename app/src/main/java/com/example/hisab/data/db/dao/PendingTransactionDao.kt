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

    @Delete
    suspend fun delete(pending: PendingTransactionEntity)

    @Query("DELETE FROM pending_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_transactions")
    suspend fun deleteAll()
}
