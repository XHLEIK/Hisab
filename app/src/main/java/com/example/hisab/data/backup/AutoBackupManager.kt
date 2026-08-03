package com.example.hisab.data.backup

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.hisab.data.db.HisabDatabase
import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AutoBackupManager(
    private val context: Context,
    private val database: HisabDatabase
) {

    private val backupPrefs = BackupPreferences(context)

    companion object {
        private const val TAG = "AutoBackupManager"
        private const val BACKUP_VERSION = 4
        private const val MAIN_BACKUP_NAME = "hisab_auto_backup.json"
    }

    /**
     * Performs a full backup of accounts, categories, and transactions.
     * Writes to Documents/Hisab/ on external storage (or App Internal fallback if restricted).
     */
    suspend fun performBackup(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val transactions = database.transactionDao().getAllTransactionsSync()
            val categories = database.categoryDao().getAllSync()
            val accounts = database.accountDao().getAllSync()

            val jsonContent = serializeToJson(transactions, categories, accounts)

            // Write to local internal storage first (always guaranteed)
            val internalDir = File(context.filesDir, "backups")
            if (!internalDir.exists()) internalDir.mkdirs()
            val internalFile = File(internalDir, MAIN_BACKUP_NAME)
            internalFile.writeText(jsonContent, Charsets.UTF_8)

            // Try writing to public Documents folder
            saveToDocumentsFolder(jsonContent)

            backupPrefs.updateLastBackupTime()
            Log.d(TAG, "Auto-backup performed successfully. Transactions: ${transactions.size}")
            Result.success(internalFile)
        } catch (e: Exception) {
            Log.e(TAG, "Auto-backup failed", e)
            Result.failure(e)
        }
    }

    /**
     * Saves backup content to Documents/Hisab/hisab_auto_backup.json using MediaStore or File API.
     */
    private fun saveToDocumentsFolder(jsonContent: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                // Query existing file to overwrite
                val queryUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
                val selectionArgs = arrayOf(MAIN_BACKUP_NAME, "Documents/Hisab/")

                val existingUri = resolver.query(queryUri, arrayOf(MediaStore.Files.FileColumns._ID), selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY, id)
                    } else null
                }

                val targetUri = existingUri ?: run {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Files.FileColumns.DISPLAY_NAME, MAIN_BACKUP_NAME)
                        put(MediaStore.Files.FileColumns.MIME_TYPE, "application/json")
                        put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Documents/Hisab")
                    }
                    resolver.insert(queryUri, contentValues)
                }

                targetUri?.let { uri ->
                    resolver.openOutputStream(uri, "rwt")?.use { out ->
                        out.write(jsonContent.toByteArray(Charsets.UTF_8))
                    }
                }
            } else {
                val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Hisab")
                if (!docsDir.exists()) docsDir.mkdirs()
                val file = File(docsDir, MAIN_BACKUP_NAME)
                file.writeText(jsonContent, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write to public Documents folder (falling back to internal storage)", e)
        }
    }

    /**
     * Attempts auto-restore if database is empty on fresh install.
     */
    suspend fun restoreIfEmpty(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (database.transactionDao().getAllTransactionsSync().isNotEmpty()) {
                return@withContext false
            }

            // Check internal backup file first
            val internalFile = File(File(context.filesDir, "backups"), MAIN_BACKUP_NAME)
            val jsonStr = if (internalFile.exists()) {
                internalFile.readText(Charsets.UTF_8)
            } else {
                readFromDocumentsFolder()
            }

            if (!jsonStr.isNullOrBlank()) {
                return@withContext restoreFromJson(jsonStr)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Auto-restore failed", e)
            false
        }
    }

    private fun readFromDocumentsFolder(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val queryUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(MAIN_BACKUP_NAME, "%Documents/Hisab%")

                val targetUri = resolver.query(queryUri, arrayOf(MediaStore.Files.FileColumns._ID), selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY, id)
                    } else null
                }

                if (targetUri != null) {
                    resolver.openInputStream(targetUri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                } else {
                    val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Hisab")
                    val file = File(docsDir, MAIN_BACKUP_NAME)
                    if (file.exists()) file.readText(Charsets.UTF_8) else null
                }
            } else {
                val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Hisab")
                val file = File(docsDir, MAIN_BACKUP_NAME)
                if (file.exists()) file.readText(Charsets.UTF_8) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading backup from Documents folder", e)
            null
        }
    }

    /**
     * Parses JSON string and restores accounts, categories, and transactions into database with 100% DE-DUPLICATION.
     */
    suspend fun restoreFromJson(jsonStr: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonStr)
            val transactionsArr = root.getJSONArray("transactions")
            val categoriesArr = root.optJSONArray("categories")
            val accountsArr = root.optJSONArray("accounts")

            // De-duplicate Accounts
            val existingAccounts = database.accountDao().getAllSync().map { it.name }.toSet()
            if (accountsArr != null) {
                for (i in 0 until accountsArr.length()) {
                    val obj = accountsArr.getJSONObject(i)
                    val accName = obj.getString("name")
                    if (!existingAccounts.contains(accName)) {
                        val account = AccountEntity(
                            name = accName,
                            type = obj.optString("type", "PRIMARY"),
                            colorHex = obj.optString("colorHex", "#4CAF50"),
                            isPrimary = obj.optBoolean("isPrimary", false)
                        )
                        database.accountDao().insert(account)
                    }
                }
            }

            // De-duplicate Categories
            val existingCategories = database.categoryDao().getAllSync()
            val existingCatMap = existingCategories.associateBy { "${it.name}_${it.type}" }.toMutableMap()
            val categoryMap = mutableMapOf<String, Long>()

            if (categoriesArr != null) {
                for (i in 0 until categoriesArr.length()) {
                    val obj = categoriesArr.getJSONObject(i)
                    val catName = obj.getString("name")
                    val catTypeStr = obj.getString("type")
                    val type = try { TransactionType.valueOf(catTypeStr) } catch (e: Exception) { TransactionType.EXPENSE }
                    val key = "${catName}_$catTypeStr"

                    if (!existingCatMap.containsKey(key)) {
                        val category = CategoryEntity(
                            name = catName,
                            type = type,
                            iconName = obj.optString("iconName", "MoreHoriz"),
                            colorHex = obj.optString("colorHex", "#607D8B"),
                            isDefault = obj.optBoolean("isDefault", false),
                            sortOrder = obj.optInt("sortOrder", 0)
                        )
                        val id = database.categoryDao().insert(category)
                        existingCatMap[key] = category.copy(id = id)
                        categoryMap[key] = id
                    } else {
                        categoryMap[key] = existingCatMap[key]!!.id
                    }
                }
            }

            // De-duplicate Transactions using fingerprint
            val existingTransactions = database.transactionDao().getAllTransactionsSync()
            val existingTxFingerprints = existingTransactions.map { tx ->
                "${tx.amount}_${tx.type}_${tx.account}_${tx.toAccount}_${tx.date}_${tx.notes}"
            }.toSet()

            var restoredCount = 0
            for (i in 0 until transactionsArr.length()) {
                val obj = transactionsArr.getJSONObject(i)
                val catName = obj.optString("categoryName", "Other Expense")
                val typeStr = obj.getString("type")
                val type = try { TransactionType.valueOf(typeStr) } catch (e: Exception) { TransactionType.EXPENSE }
                val dateStr = obj.getString("date")
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                val amount = obj.getDouble("amount")
                val account = obj.optString("account", "Primary Bank")
                val toAccount = if (obj.has("toAccount")) obj.optString("toAccount") else null
                val notes = obj.optString("notes", "")

                val fingerprint = "${amount}_${type}_${account}_${toAccount}_${date}_${notes}"
                if (existingTxFingerprints.contains(fingerprint)) {
                    continue // Skip duplicate
                }

                var catId = obj.optLong("categoryId", 0L)
                if (catId <= 0L || database.categoryDao().getById(catId) == null) {
                    catId = existingCatMap["${catName}_$type"]?.id
                        ?: categoryMap["${catName}_$type"]
                        ?: existingCategories.firstOrNull { it.type == type }?.id
                        ?: 1L
                }

                val transaction = TransactionEntity(
                    amount = amount,
                    type = type,
                    categoryId = catId,
                    account = account,
                    toAccount = toAccount,
                    date = date,
                    notes = notes,
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
                database.transactionDao().insert(transaction)
                restoredCount++
            }

            Log.d(TAG, "Restored $restoredCount non-duplicate transactions from backup.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore from JSON", e)
            false
        }
    }

    private fun serializeToJson(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        accounts: List<AccountEntity>
    ): String {
        val categoryMap = categories.associateBy { it.id }
        val root = JSONObject()
        root.put("app", "Hisab")
        root.put("version", BACKUP_VERSION)
        root.put("timestamp", System.currentTimeMillis())

        // Accounts
        val accArray = JSONArray()
        accounts.forEach { acc ->
            val obj = JSONObject().apply {
                put("id", acc.id)
                put("name", acc.name)
                put("type", acc.type)
                put("colorHex", acc.colorHex)
                put("isPrimary", acc.isPrimary)
            }
            accArray.put(obj)
        }
        root.put("accounts", accArray)

        // Categories
        val catArray = JSONArray()
        categories.forEach { cat ->
            val obj = JSONObject().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("type", cat.type.name)
                put("iconName", cat.iconName)
                put("colorHex", cat.colorHex)
                put("isDefault", cat.isDefault)
                put("sortOrder", cat.sortOrder)
            }
            catArray.put(obj)
        }
        root.put("categories", catArray)

        // Transactions
        val txArray = JSONArray()
        transactions.forEach { tx ->
            val obj = JSONObject().apply {
                put("amount", tx.amount)
                put("type", tx.type.name)
                put("categoryId", tx.categoryId)
                put("categoryName", categoryMap[tx.categoryId]?.name ?: "Unknown")
                put("account", tx.account)
                if (tx.toAccount != null) put("toAccount", tx.toAccount)
                put("date", tx.date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                put("notes", tx.notes)
                put("createdAt", tx.createdAt)
            }
            txArray.put(obj)
        }
        root.put("transactions", txArray)

        // Calculate SHA-256 checksum of raw content
        val rawBytes = root.toString().toByteArray(Charsets.UTF_8)
        val checksum = MessageDigest.getInstance("SHA-256").digest(rawBytes).joinToString("") { "%02x".format(it) }
        root.put("checksum", checksum)

        return root.toString(2)
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()
}
