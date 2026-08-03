package com.example.hisab.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.hisab.data.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("SELECT * FROM accounts ORDER BY isPrimary DESC, id ASC")
    fun getAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY isPrimary DESC, id ASC")
    suspend fun getAllSync(): List<AccountEntity>

    @Query("SELECT name FROM accounts ORDER BY isPrimary DESC, id ASC")
    fun getAllNames(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun getCount(): Int

    @Query("SELECT * FROM accounts WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryAccount(): AccountEntity?
}
