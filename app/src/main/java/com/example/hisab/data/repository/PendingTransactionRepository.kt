package com.example.hisab.data.repository

import com.example.hisab.data.db.dao.PendingTransactionDao
import com.example.hisab.data.db.entity.PendingTransactionEntity
import kotlinx.coroutines.flow.Flow

class PendingTransactionRepository(
    private val pendingDao: PendingTransactionDao
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
}
