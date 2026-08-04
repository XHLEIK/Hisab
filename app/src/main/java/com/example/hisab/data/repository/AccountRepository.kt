package com.example.hisab.data.repository

import com.example.hisab.data.db.dao.AccountDao
import com.example.hisab.data.db.dao.TransactionDao
import com.example.hisab.data.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

class AccountRepository(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao? = null,
    private var autoBackupManager: com.example.hisab.data.backup.AutoBackupManager? = null
) {

    fun setAutoBackupManager(manager: com.example.hisab.data.backup.AutoBackupManager) {
        this.autoBackupManager = manager
    }

    fun getAllAccounts(): Flow<List<AccountEntity>> =
        accountDao.getAll()

    fun getAllAccountNames(): Flow<List<String>> =
        accountDao.getAllNames()

    suspend fun insertAccount(account: AccountEntity): Long {
        val result = accountDao.insert(account)
        autoBackupManager?.performBackup()
        return result
    }

    suspend fun updateAccount(account: AccountEntity) {
        accountDao.update(account)
        autoBackupManager?.performBackup()
    }

    suspend fun updateAccount(oldName: String, account: AccountEntity) {
        if (oldName != account.name && transactionDao != null) {
            transactionDao.updateAccountName(oldName, account.name)
            transactionDao.updateToAccountName(oldName, account.name)
        }
        accountDao.update(account)
        autoBackupManager?.performBackup()
    }

    suspend fun deleteAccount(account: AccountEntity) {
        accountDao.delete(account)
        autoBackupManager?.performBackup()
    }

    suspend fun setPrimaryAccount(account: AccountEntity) {
        val all = accountDao.getAllSync()
        all.forEach { acc ->
            val updated = acc.copy(isPrimary = (acc.id == account.id))
            accountDao.update(updated)
        }
        autoBackupManager?.performBackup()
    }

    suspend fun getAllAccountsSync(): List<AccountEntity> =
        accountDao.getAllSync()

    suspend fun getPrimaryAccount(): AccountEntity? =
        accountDao.getPrimaryAccount()

    /**
     * Syncs transaction account names with the current accounts table.
     * If any transactions still reference old names (e.g. "Primary Bank" after
     * renaming to "BOB"), this updates them to the current account name by
     * matching on account type (PRIMARY → primary account, etc.).
     *
     * Called once at app startup.
     */
    suspend fun syncAccountNames() {
        val transactionDao = transactionDao ?: return
        val currentAccounts = accountDao.getAllSync()
        if (currentAccounts.isEmpty()) return

        val currentNames = currentAccounts.map { it.name }.toSet()
        val txAccountNames = transactionDao.getAllDistinctAccountNamesSync()

        // Find names in transactions that no longer exist in accounts table
        val staleNames = txAccountNames.filter { it !in currentNames }
        if (staleNames.isEmpty()) return

        // Build a mapping of default names → current account name by type
        val defaultToCurrentMap = buildMap<String, String> {
            val primary = currentAccounts.firstOrNull { it.isPrimary } ?: currentAccounts.firstOrNull()
            val nonPrimary = currentAccounts.filter { !it.isPrimary }

            // Map old default primary names
            if (primary != null) {
                listOf("Primary Bank", "Cash", "primary").forEach { old ->
                    if (old !in currentNames) put(old, primary.name)
                }
            }
            // Map old default secondary names
            nonPrimary.getOrNull(0)?.let { sec ->
                listOf("Secondary Bank", "secondary").forEach { old ->
                    if (old !in currentNames) put(old, sec.name)
                }
            }
            // Map old default savings names
            nonPrimary.getOrNull(1)?.let { sav ->
                listOf("Savings Bank", "savings").forEach { old ->
                    if (old !in currentNames) put(old, sav.name)
                }
            }
        }

        staleNames.forEach { staleName ->
            val newName = defaultToCurrentMap[staleName] ?: return@forEach
            transactionDao.updateAccountName(staleName, newName)
            transactionDao.updateToAccountName(staleName, newName)
        }
    }
}

