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
import com.example.hisab.data.db.entity.PendingTransactionEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
        private const val BACKUP_VERSION = 5
        private const val MAIN_BACKUP_NAME = "hisab_auto_backup.json"
    }

    /**
     * Performs a full backup of accounts, categories, transactions, and pending bank transactions.
     * Writes to Documents/Hisab/ on external storage (or App Internal fallback if restricted).
     */
    suspend fun performBackup(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val transactions = database.transactionDao().getAllTransactionsSync()
            val categories = database.categoryDao().getAllSync()
            val accounts = database.accountDao().getAllSync()
            val pendingTxs = database.pendingTransactionDao().getAllPendingSync()

            val jsonContent = serializeToJson(transactions, categories, accounts, pendingTxs)

            // Write to local internal storage first (always guaranteed)
            val internalDir = File(context.filesDir, "backups")
            if (!internalDir.exists()) internalDir.mkdirs()
            val internalFile = File(internalDir, MAIN_BACKUP_NAME)
            internalFile.writeText(jsonContent, Charsets.UTF_8)

            // Try writing to public Documents folder
            saveToDocumentsFolder(jsonContent)

            backupPrefs.updateLastBackupTime()
            Log.d(TAG, "Auto-backup performed successfully. Transactions: ${transactions.size}, Accounts: ${accounts.size}, Pending: ${pendingTxs.size}")
            Result.success(internalFile)
        } catch (e: Exception) {
            Log.e(TAG, "Auto-backup failed", e)
            Result.failure(e)
        }
    }

    /**
     * Exports full JSON backup string.
     */
    suspend fun exportBackupString(): String = withContext(Dispatchers.IO) {
        val transactions = database.transactionDao().getAllTransactionsSync()
        val categories = database.categoryDao().getAllSync()
        val accounts = database.accountDao().getAllSync()
        val pendingTxs = database.pendingTransactionDao().getAllPendingSync()
        serializeToJson(transactions, categories, accounts, pendingTxs)
    }

    /**
     * Saves backup content to Documents/Hisab/hisab_auto_backup.json using MediaStore and File API.
     */
    private fun saveToDocumentsFolder(jsonContent: String) {
        try {
            // 1. Direct POSIX writing to all available external documents directories
            val targetDirNames = listOf("Hisab", "")
            val posixParentDirs = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File("/storage/emulated/0/Documents"),
                File("/storage/emulated/0/Download"),
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                context.getExternalFilesDir(null)
            )

            for (parentDir in posixParentDirs) {
                for (dirName in targetDirNames) {
                    try {
                        val dir = if (dirName.isEmpty()) parentDir else File(parentDir, dirName)
                        if (!dir.exists()) dir.mkdirs()
                        val file = File(dir, MAIN_BACKUP_NAME)
                        file.writeText(jsonContent, Charsets.UTF_8)
                    } catch (_: Exception) {}
                }
            }

            // 2. MediaStore insertion for Android 10+ (API 29+) scoped storage indexing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val queryUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(MAIN_BACKUP_NAME, "%Documents/Hisab%")

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
                        put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Documents/Hisab/")
                        put(MediaStore.Files.FileColumns.IS_PENDING, 0)
                    }
                    resolver.insert(queryUri, contentValues)
                }

                targetUri?.let { uri ->
                    resolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(jsonContent.toByteArray(Charsets.UTF_8))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write to public Documents folder", e)
        }
    }

    /**
     * Smart Import: Attempts to read Documents/Hisab/hisab_auto_backup.json automatically.
     * Returns true if successfully imported, false if file not found or failed.
     */
    suspend fun smartImportFromDocuments(): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonStr = readFromDocumentsFolder()
                ?: run {
                    val internalFile = File(File(context.filesDir, "backups"), MAIN_BACKUP_NAME)
                    if (internalFile.exists() && internalFile.length() > 0) internalFile.readText(Charsets.UTF_8) else null
                }

            if (!jsonStr.isNullOrBlank()) {
                return@withContext restoreFromJson(jsonStr)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Smart import failed", e)
            false
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

            val internalFile = File(File(context.filesDir, "backups"), MAIN_BACKUP_NAME)
            val jsonStr = if (internalFile.exists() && internalFile.length() > 0) {
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
        // ── Step 1: Direct File System Search Across All Directories ─────────
        val searchDirectories = listOfNotNull(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Hisab"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Hisab"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File("/storage/emulated/0/Documents/Hisab"),
            File("/storage/emulated/0/Documents"),
            File("/storage/emulated/0/Download/Hisab"),
            File("/storage/emulated/0/Download"),
            File("/sdcard/Documents/Hisab"),
            File("/sdcard/Download"),
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            context.getExternalFilesDir(null)
        )

        for (dir in searchDirectories) {
            try {
                if (!dir.exists() || !dir.isDirectory) continue
                val files = dir.listFiles() ?: continue
                for (f in files) {
                    if (f.isFile && f.length() > 0 && (f.name.endsWith(".json", ignoreCase = true) || f.name.equals(MAIN_BACKUP_NAME, ignoreCase = true))) {
                        val content = f.readText(Charsets.UTF_8)
                        if (content.contains("\"transactions\"")) {
                            Log.d(TAG, "Found valid backup file at ${f.absolutePath}")
                            return content
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // ── Step 2: MediaStore Query for Android 10+ (API 29+) ───────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val volumeUris = listOf(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                MediaStore.Files.getContentUri("external")
            )

            for (queryUri in volumeUris) {
                try {
                    val resolver = context.contentResolver
                    val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                    val selectionArgs = arrayOf("%.json%", "%Documents/Hisab%")

                    resolver.query(queryUri, projection, selection, selectionArgs, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idIdx)
                            val itemUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY, id)
                            try {
                                val content = resolver.openInputStream(itemUri)?.use { stream ->
                                    stream.bufferedReader(Charsets.UTF_8).readText()
                                }
                                if (!content.isNullOrBlank() && content.contains("\"transactions\"")) {
                                    Log.d(TAG, "Found valid backup file via MediaStore query")
                                    return content
                                }
                            } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MediaStore query failed for volume", e)
                }
            }
        }

        return null
    }

    /**
     * Parses JSON string and restores accounts (with bank links), categories, transactions, and pending bank transactions into database.
     */
    suspend fun restoreFromJson(jsonStr: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonStr)
            val transactionsArr = root.getJSONArray("transactions")
            val categoriesArr = root.optJSONArray("categories")
            val accountsArr = root.optJSONArray("accounts")
            val pendingArr = root.optJSONArray("pendingTransactions")

            // Intelligent Account Synchronization & De-duplication
            val currentAccounts = database.accountDao().getAllSync().toMutableList()
            val matchedAccountIds = mutableSetOf<Long>()

            if (accountsArr != null && accountsArr.length() > 0) {
                for (i in 0 until accountsArr.length()) {
                    val obj = accountsArr.getJSONObject(i)
                    val impName = obj.getString("name")
                    val impType = obj.optString("type", "PRIMARY")
                    val impColorHex = obj.optString("colorHex", "#10B981")
                    val impIsPrimary = obj.optBoolean("isPrimary", false)
                    val impBankCode = if (obj.has("bankCode") && !obj.isNull("bankCode")) obj.getString("bankCode") else null
                    val impAccountLast4 = if (obj.has("accountLast4") && !obj.isNull("accountLast4")) obj.getString("accountLast4") else null

                    // 1. Exact name match FIRST
                    val exactMatch = currentAccounts.firstOrNull { it.name.equals(impName, ignoreCase = true) }
                    if (exactMatch != null) {
                        val updated = exactMatch.copy(
                            name = impName,
                            type = impType,
                            colorHex = impColorHex,
                            isPrimary = impIsPrimary,
                            bankCode = impBankCode ?: exactMatch.bankCode,
                            accountLast4 = impAccountLast4 ?: exactMatch.accountLast4
                        )
                        database.accountDao().update(updated)
                        matchedAccountIds.add(exactMatch.id)
                    } else {
                        // 2. Type match fallback
                        val typeMatch = currentAccounts.firstOrNull {
                            it.type.equals(impType, ignoreCase = true) && !matchedAccountIds.contains(it.id)
                        }
                        if (typeMatch != null) {
                            val updated = typeMatch.copy(
                                name = impName,
                                type = impType,
                                colorHex = impColorHex,
                                isPrimary = impIsPrimary,
                                bankCode = impBankCode ?: typeMatch.bankCode,
                                accountLast4 = impAccountLast4 ?: typeMatch.accountLast4
                            )
                            database.accountDao().update(updated)
                            matchedAccountIds.add(typeMatch.id)
                        } else {
                            // 3. New account insertion
                            val newAccount = AccountEntity(
                                name = impName,
                                type = impType,
                                colorHex = impColorHex,
                                isPrimary = impIsPrimary,
                                bankCode = impBankCode,
                                accountLast4 = impAccountLast4
                            )
                            database.accountDao().insert(newAccount)
                        }
                    }
                }
            }

            // Restore Categories
            val existingCategories = database.categoryDao().getAllSync()
            val categoryMap = mutableMapOf<String, Long>()
            existingCategories.forEach { categoryMap["${it.name}_${it.type}"] = it.id }

            if (categoriesArr != null && categoriesArr.length() > 0) {
                for (i in 0 until categoriesArr.length()) {
                    val obj = categoriesArr.getJSONObject(i)
                    val name = obj.getString("name")
                    val typeStr = obj.getString("type")
                    val type = TransactionType.valueOf(typeStr)
                    val iconName = obj.optString("iconName", "MoreHoriz")
                    val colorHex = obj.optString("colorHex", "#607D8B")
                    val isDefault = obj.optBoolean("isDefault", false)
                    val sortOrder = obj.optInt("sortOrder", 0)

                    val key = "${name}_${type}"
                    if (!categoryMap.containsKey(key)) {
                        val category = CategoryEntity(
                            name = name,
                            type = type,
                            iconName = iconName,
                            colorHex = colorHex,
                            isDefault = isDefault,
                            sortOrder = sortOrder
                        )
                        val id = database.categoryDao().insert(category)
                        categoryMap[key] = id
                    }
                }
            }

            // Restore Transactions
            val existingTxns = database.transactionDao().getAllTransactionsSync()
            val existingFingerprints = existingTxns.map { computeFingerprint(it) }.toSet()

            val newTransactions = mutableListOf<TransactionEntity>()

            for (i in 0 until transactionsArr.length()) {
                val obj = transactionsArr.getJSONObject(i)
                val amount = obj.getDouble("amount")
                val typeStr = obj.getString("type")
                val type = TransactionType.valueOf(typeStr)
                val categoryName = obj.getString("categoryName")
                val dateStr = obj.getString("date")
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                val notes = obj.optString("notes", "")
                val account = obj.optString("account", "Primary Bank")
                val toAccount = if (obj.has("toAccount") && !obj.isNull("toAccount")) obj.getString("toAccount") else null
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())

                val categoryKey = "${categoryName}_${type}"
                val categoryId = categoryMap[categoryKey] ?: run {
                    val fallbackCat = database.categoryDao().getAllSync().firstOrNull { it.type == type }
                    fallbackCat?.id ?: 1L
                }

                val tempTx = TransactionEntity(
                    amount = amount,
                    type = type,
                    categoryId = categoryId,
                    date = date,
                    notes = notes,
                    account = account,
                    toAccount = toAccount,
                    createdAt = createdAt
                )

                val fp = computeFingerprint(tempTx)
                if (!existingFingerprints.contains(fp)) {
                    newTransactions.add(tempTx)
                }
            }

            if (newTransactions.isNotEmpty()) {
                newTransactions.forEach { database.transactionDao().insert(it) }
            }

            // Restore Pending Transactions Queue
            if (pendingArr != null && pendingArr.length() > 0) {
                val existingPending = database.pendingTransactionDao().getAllPendingSync()
                val existingPendingFingerprints = existingPending.map { "${it.amount}_${it.rawSmsBody}" }.toSet()

                val newPending = mutableListOf<PendingTransactionEntity>()
                for (i in 0 until pendingArr.length()) {
                    val pObj = pendingArr.getJSONObject(i)
                    val pAmount = pObj.getDouble("amount")
                    val pType = pObj.getString("type")
                    val pBankName = pObj.getString("bankName")
                    val pAccountLast4 = if (pObj.has("accountLast4") && !pObj.isNull("accountLast4")) pObj.getString("accountLast4") else null
                    val pMerchant = if (pObj.has("merchantOrPayee") && !pObj.isNull("merchantOrPayee")) pObj.getString("merchantOrPayee") else null
                    val pRawSmsBody = pObj.getString("rawSmsBody")
                    val pSenderHeader = if (pObj.has("senderHeader") && !pObj.isNull("senderHeader")) pObj.getString("senderHeader") else null
                    val pTimestamp = pObj.optLong("timestamp", System.currentTimeMillis())

                    val pf = "${pAmount}_${pRawSmsBody}"
                    if (!existingPendingFingerprints.contains(pf)) {
                        newPending.add(
                            PendingTransactionEntity(
                                amount = pAmount,
                                type = pType,
                                bankName = pBankName,
                                accountLast4 = pAccountLast4,
                                merchantOrPayee = pMerchant,
                                rawSmsBody = pRawSmsBody,
                                senderHeader = pSenderHeader,
                                timestamp = pTimestamp
                            )
                        )
                    }
                }
                if (newPending.isNotEmpty()) {
                    newPending.forEach { database.pendingTransactionDao().insert(it) }
                }
            }

            backupPrefs.updateLastBackupTime()
            Log.d(TAG, "Restored ${newTransactions.size} new transactions from backup JSON.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse/restore JSON backup", e)
            false
        }
    }

    private fun serializeToJson(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        accounts: List<AccountEntity>,
        pendingTxs: List<PendingTransactionEntity>
    ): String {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("backupDate", LocalDate.now().toString())
        root.put("app", "Hisab")

        // 1. Accounts Array (with bankCode & accountLast4)
        val accountsArr = JSONArray()
        accounts.forEach { acc ->
            val obj = JSONObject()
            obj.put("id", acc.id)
            obj.put("name", acc.name)
            obj.put("type", acc.type)
            obj.put("colorHex", acc.colorHex)
            obj.put("isPrimary", acc.isPrimary)
            obj.put("bankCode", acc.bankCode ?: JSONObject.NULL)
            obj.put("accountLast4", acc.accountLast4 ?: JSONObject.NULL)
            accountsArr.put(obj)
        }
        root.put("accounts", accountsArr)

        // 2. Categories Array
        val categoriesArr = JSONArray()
        categories.forEach { cat ->
            val obj = JSONObject()
            obj.put("id", cat.id)
            obj.put("name", cat.name)
            obj.put("type", cat.type.name)
            obj.put("iconName", cat.iconName)
            obj.put("colorHex", cat.colorHex)
            obj.put("isDefault", cat.isDefault)
            obj.put("sortOrder", cat.sortOrder)
            categoriesArr.put(obj)
        }
        root.put("categories", categoriesArr)

        // 3. Transactions Array
        val categoryMap = categories.associateBy { it.id }
        val transactionsArr = JSONArray()
        transactions.forEach { tx ->
            val obj = JSONObject()
            val category = categoryMap[tx.categoryId]
            obj.put("id", tx.id)
            obj.put("amount", tx.amount)
            obj.put("type", tx.type.name)
            obj.put("categoryId", tx.categoryId)
            obj.put("categoryName", category?.name ?: "Unknown")
            obj.put("date", tx.date.toString())
            obj.put("notes", tx.notes)
            obj.put("account", tx.account)
            obj.put("toAccount", tx.toAccount ?: JSONObject.NULL)
            obj.put("createdAt", tx.createdAt)
            transactionsArr.put(obj)
        }
        root.put("transactions", transactionsArr)

        // 4. Pending Transactions Queue Array
        val pendingArr = JSONArray()
        pendingTxs.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("amount", p.amount)
            obj.put("type", p.type)
            obj.put("bankName", p.bankName)
            obj.put("accountLast4", p.accountLast4 ?: JSONObject.NULL)
            obj.put("merchantOrPayee", p.merchantOrPayee ?: JSONObject.NULL)
            obj.put("rawSmsBody", p.rawSmsBody)
            obj.put("senderHeader", p.senderHeader ?: JSONObject.NULL)
            obj.put("timestamp", p.timestamp)
            pendingArr.put(obj)
        }
        root.put("pendingTransactions", pendingArr)

        val checksum = computeChecksum(root.toString())
        root.put("checksum", checksum)

        return root.toString(2)
    }

    private fun computeFingerprint(tx: TransactionEntity): String {
        return "${tx.amount}_${tx.type}_${tx.date}_${tx.notes}_${tx.account}_${tx.toAccount}_${tx.createdAt}"
    }

    private fun computeChecksum(data: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(data.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
