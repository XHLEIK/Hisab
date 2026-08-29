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

/**
 * Reads an optional string field, mapping both "absent" and JSON null to Kotlin null.
 *
 * `JSONObject.optString` returns `""` in both cases, which would turn a missing provenance field
 * into an empty-string identity — a value that a UNIQUE index treats as real. Restores must stay
 * tolerant of older backup versions without inventing data.
 */
private fun JSONObject.optNullableString(key: String): String? =
    if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotBlank() } else null

class AutoBackupManager(
    private val context: Context,
    private val database: HisabDatabase
) {
    private val backupPrefs = BackupPreferences(context)

    companion object {
        private const val TAG = "AutoBackupManager"
        private const val BACKUP_VERSION = 7
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
     * Exports a JSON backup string.
     *
     * [transactions] lets a caller narrow the ledger to rows it already selected — the month-scoped
     * export is the reason this exists. Accounts and categories always come along in full: a
     * transaction row is meaningless without the account and category it points at. Pending bank
     * rows are unlogged suggestions belonging to no month's ledger, so [includePending] drops them
     * from a scoped export while the full backup keeps them.
     */
    suspend fun exportBackupString(
        transactions: List<TransactionEntity>? = null,
        includePending: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        serializeToJson(
            transactions ?: database.transactionDao().getAllTransactionsSync(),
            database.categoryDao().getAllSync(),
            database.accountDao().getAllSync(),
            if (includePending) database.pendingTransactionDao().getAllPendingSync() else emptyList()
        )
    }

    /**
     * Saves backup content to Documents/Hisab/hisab_auto_backup.json.
     *
     * SINGLE-FILE DISCIPLINE: exactly ONE public backup file may exist.
     *
     * On Android 10+ (scoped storage), ALWAYS use MediaStore UPSERT — POSIX writes to
     * external Documents silently succeed on some OEMs but produce files invisible to other
     * apps, and then a subsequent MediaStore insert creates a deconflicted (1), (2) copy.
     * Bypassing POSIX entirely on API 29+ eliminates the dual-write that causes duplicates.
     *
     * On Android 9 and below, POSIX write works reliably — no MediaStore needed.
     */
    private fun saveToDocumentsFolder(jsonContent: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: scoped storage — MediaStore is the ONLY reliable path.
                upsertMediaStoreBackup(jsonContent)
            } else {
                // Android 9 and below: POSIX writes work directly.
                val hisabDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Hisab")
                try {
                    if (!hisabDir.exists()) hisabDir.mkdirs()
                    File(hisabDir, MAIN_BACKUP_NAME).writeText(jsonContent, Charsets.UTF_8)
                } catch (_: Exception) {}
            }
            cleanupStrayBackupFiles()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write to public Documents folder", e)
        }
    }

    /**
     * MediaStore upsert for devices where direct File access is blocked (scoped storage).
     * Matches ALL hisab_auto_backup*.json variants in Documents/Hisab, updates the canonical
     * row, or renames the newest "(N)" variant back to the canonical name instead of letting
     * Android create yet another deconflicted duplicate.
     */
    private fun upsertMediaStoreBackup(jsonContent: String) {
        val resolver = context.contentResolver
        val queryUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("hisab_auto_backup%.json", "%Documents/Hisab%")

        data class Row(val id: Long, val name: String, val modified: Long)
        val rows = mutableListOf<Row>()
        try {
            resolver.query(
                queryUri,
                arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.DATE_MODIFIED),
                selection, selectionArgs, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val modIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    rows.add(Row(cursor.getLong(idIdx), cursor.getString(nameIdx) ?: "", cursor.getLong(modIdx)))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore backup lookup failed", e)
        }

        val exact = rows.firstOrNull { it.name.equals(MAIN_BACKUP_NAME, ignoreCase = true) }
        val newestVariant = rows.filter { !it.name.equals(MAIN_BACKUP_NAME, ignoreCase = true) }.maxByOrNull { it.modified }

        val targetUri: android.net.Uri? = when {
            exact != null -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY, exact.id)
            newestVariant != null -> {
                // Reclaim a deconflicted duplicate: rename it to the canonical name instead of
                // inserting yet another (N) file.
                val renamed = try {
                    resolver.update(
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY, newestVariant.id),
                        ContentValues().apply { put(MediaStore.Files.FileColumns.DISPLAY_NAME, MAIN_BACKUP_NAME) },
                        null, null
                    )
                    true
                } catch (_: Exception) {
                    false
                }
                if (renamed) MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY, newestVariant.id) else null
            }
            else -> {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, MAIN_BACKUP_NAME)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "application/json")
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Documents/Hisab/")
                    put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                }
                resolver.insert(queryUri, contentValues)
            }
        }

        targetUri?.let { uri ->
            try {
                resolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(jsonContent.toByteArray(Charsets.UTF_8))
                }
                // Clear IS_PENDING so the file is visible (no-op update on a pre-existing row).
                resolver.update(uri, ContentValues().apply { put(MediaStore.Files.FileColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore backup write failed", e)
                // Only clean up a row this call created — never a pre-existing file we merely updated.
                if (exact == null && newestVariant == null) {
                    try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                }
            }
        }

        // Single-file discipline: drop any leftover variant rows we can remove (best effort).
        val keepId = targetUri?.lastPathSegment?.toLongOrNull() ?: -1L
        rows.filter { it.id != keepId }.forEach { row ->
            try { resolver.delete(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY, row.id), null, null) } catch (_: Exception) {}
        }
    }

    /**
     * Only ONE backup file may exist: Documents/Hisab/hisab_auto_backup.json.
     * Best-effort removal of "(N)" deconflicted variants everywhere and of the old fan-out
     * copies that previous versions scattered into the Documents/Downloads roots.
     */
    private fun cleanupStrayBackupFiles() {
        // Search both Hisab/ subdirectory AND the parent directories (Documents/, Download/)
        // for stray backup files. Previous versions scattered copies in the parent root.
        val dirs = listOfNotNull(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Hisab"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Hisab"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File("/storage/emulated/0/Documents/Hisab"),
            File("/storage/emulated/0/Documents"),
            File("/storage/emulated/0/Download/Hisab"),
            File("/storage/emulated/0/Download"),
            File("/sdcard/Documents"),
            File("/sdcard/Download")
        )
        for (dir in dirs) {
            try {
                val files = dir.listFiles() ?: continue
                for (f in files) {
                    if (!f.isFile) continue
                    val name = f.name.lowercase()
                    if (!name.startsWith("hisab_auto_backup") || !name.endsWith(".json")) continue
                    val inHisabDir = f.parentFile?.name.equals("Hisab", ignoreCase = true)
                    val isCanonical = name == MAIN_BACKUP_NAME && inHisabDir
                    if (!isCanonical) try { f.delete() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val queryUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                resolver.query(
                    queryUri,
                    arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME),
                    selection,
                    arrayOf("hisab_auto_backup%.json", "%Documents/Hisab%"),
                    null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIdx) ?: ""
                        if (name.equals(MAIN_BACKUP_NAME, ignoreCase = true)) continue
                        val rowUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY, cursor.getLong(idIdx))
                        try { resolver.delete(rowUri, null, null) } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
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
     *
     * The internal copy is carried across reinstalls by Android Auto Backup, but a stale
     * cloud snapshot can exist with zero transactions — that was the "backup imported"
     * toast with nothing actually restored. Both candidates are now compared by how much
     * data they actually contain and success is only reported when rows really landed.
     */
    suspend fun restoreIfEmpty(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (database.transactionDao().getAllTransactionsSync().isNotEmpty()) {
                return@withContext false
            }

            data class Candidate(val json: String, val txCount: Int)
            val candidates = mutableListOf<Candidate>()

            val internalFile = File(File(context.filesDir, "backups"), MAIN_BACKUP_NAME)
            if (internalFile.exists() && internalFile.length() > 0) {
                val content = runCatching { internalFile.readText(Charsets.UTF_8) }.getOrNull()
                if (content != null && isValidBackupJson(content)) {
                    candidates.add(Candidate(content, JSONObject(content).optJSONArray("transactions")!!.length()))
                }
            }

            val external = readFromDocumentsFolder()
            if (external != null && isValidBackupJson(external)) {
                candidates.add(Candidate(external, JSONObject(external).optJSONArray("transactions")!!.length()))
            }

            val best = candidates.maxByOrNull { it.txCount } ?: return@withContext false
            if (best.txCount <= 0) return@withContext false

            val restored = restoreFromJson(best.json)
            // Honesty guarantee: report success only if data actually landed in the database.
            restored && database.transactionDao().getAllTransactionsSync().isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Auto-restore failed", e)
            false
        }
    }

    suspend fun getBackupTransactionCount(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val jsonStr = readFromDocumentsFolder()
                ?: run {
                    val internalFile = File(File(context.filesDir, "backups"), MAIN_BACKUP_NAME)
                    if (internalFile.exists() && internalFile.length() > 0) internalFile.readText(Charsets.UTF_8) else null
                }
            if (jsonStr.isNullOrBlank()) return@withContext 0
            val root = JSONObject(jsonStr)
            root.optJSONArray("transactions")?.length() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    suspend fun restoreFromUri(context: Context, uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonStr = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
            if (jsonStr.isNullOrBlank() || !isValidBackupJson(jsonStr)) return@withContext false
            val restored = restoreFromJson(jsonStr)
            if (restored) {
                // Make the restored file app-owned so future launches (and Android Auto
                // Backup) always have the authoritative copy internally.
                try {
                    val internalDir = File(context.filesDir, "backups")
                    if (!internalDir.exists()) internalDir.mkdirs()
                    File(internalDir, MAIN_BACKUP_NAME).writeText(jsonStr, Charsets.UTF_8)
                } catch (_: Exception) {}
            }
            restored
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore from uri $uri", e)
            false
        }
    }

    /** Structural validation: a Hisab backup must parse as JSON and carry a non-empty transactions array. */
    private fun isValidBackupJson(content: String): Boolean = try {
        val arr = JSONObject(content).optJSONArray("transactions")
        arr != null && arr.length() > 0
    } catch (_: Exception) {
        false
    }

    private data class BackupCandidate(val content: String, val lastModified: Long, val isCanonical: Boolean)

    /**
     * Finds the best backup file from external storage.
     *
     * Priority: canonical name (hisab_auto_backup.json) in Hisab/ directory wins when it has
     * transactions. Only falls back to "(N)" variants or other locations when the canonical
     * file is missing or empty. Among candidates with the same transaction count, the most
     * recently modified wins.
     */
    private fun readFromDocumentsFolder(): String? {
        val candidates = mutableListOf<BackupCandidate>()

        // ── Step 1: Direct File System Search ──────────────────────────────
        // Accept BOTH the canonical name AND deconflicted "(N)" variants.
        // Previous app versions could leave hisab_auto_backup(1).json, (2).json, etc.
        // when POSIX and MediaStore writes raced each other.
        val searchDirectories = listOfNotNull(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Hisab"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Hisab"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File("/storage/emulated/0/Documents/Hisab"),
            File("/storage/emulated/0/Documents"),
            File("/storage/emulated/0/Download/Hisab"),
            File("/storage/emulated/0/Download"),
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        )

        for (dir in searchDirectories) {
            try {
                if (!dir.exists() || !dir.isDirectory) continue
                val files = dir.listFiles() ?: continue
                for (f in files) {
                    if (!f.isFile || f.length() == 0L) continue
                    val nameLower = f.name.lowercase()
                    // Accept any file whose name starts with "hisab_auto_backup" and ends with .json
                    if (!nameLower.startsWith("hisab_auto_backup") || !nameLower.endsWith(".json")) continue
                    val isCanonical = f.name.equals(MAIN_BACKUP_NAME, ignoreCase = true)
                    try {
                        val content = f.readText(Charsets.UTF_8)
                        if (isValidBackupJson(content)) {
                            candidates.add(BackupCandidate(content, f.lastModified(), isCanonical))
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }

        // If any POSIX files found, use the best one (canonical wins, then most txns, then newest).
        // Skip MediaStore — it may have stale indexed copies from before the duplication fix.
        val posixBest = candidates
            .sortedWith(
                compareByDescending<BackupCandidate> { it.isCanonical }
                    .thenByDescending { countTransactions(it.content) }
                    .thenByDescending { it.lastModified }
            )
            .firstOrNull()
        if (posixBest != null) return posixBest.content

        // ── Step 2: MediaStore Query for Android 10+ (API 29+) ───────────────
        // Only reached when no POSIX file was found (scoped storage blocks File I/O).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val queryUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.DATE_MODIFIED
                )
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("hisab_auto_backup%.json", "%Documents/Hisab%")

                resolver.query(queryUri, projection, selection, selectionArgs, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                    val nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val modIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        val name = cursor.getString(nameIdx) ?: ""
                        val isCanonical = name.equals(MAIN_BACKUP_NAME, ignoreCase = true)
                        val modified = if (modIdx >= 0) cursor.getLong(modIdx) * 1000L else 0L
                        val itemUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY, id)
                        try {
                            val content = resolver.openInputStream(itemUri)?.use { stream ->
                                stream.bufferedReader(Charsets.UTF_8).readText()
                            }
                            if (!content.isNullOrBlank() && isValidBackupJson(content)) {
                                candidates.add(BackupCandidate(content, modified, isCanonical))
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore query failed", e)
            }
        }

        // Prefer canonical by transaction count, then by recency
        val canonicalMs = candidates.filter { it.isCanonical }.maxByOrNull { it.lastModified }
        if (canonicalMs != null) return canonicalMs.content

        // Last resort: any valid backup with the most transactions
        return candidates
            .groupBy { countTransactions(it.content) }
            .maxByOrNull { it.key }
            ?.value?.maxByOrNull { it.lastModified }
            ?.content
    }

    /** Count transactions in a backup JSON string without full parsing. */
    private fun countTransactions(json: String): Int = try {
        JSONObject(json).optJSONArray("transactions")?.length() ?: 0
    } catch (_: Exception) { 0 }

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
            val existingTxnHashes = existingTxns.mapNotNull { it.sourceMessageHash }.toMutableSet()

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
                // v6 fields; absent in v5-and-earlier backups, which is why every read is tolerant.
                val txHash = obj.optNullableString("sourceMessageHash")
                val txSource = obj.optNullableString("source")
                val txConfidence = obj.optNullableString("confidence")
                val txReference = obj.optNullableString("referenceNumber")
                // v7 field (schema v9); tolerant read keeps pre-v9 backups restorable.
                val txSubtype = obj.optNullableString("subtype")

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
                    createdAt = createdAt,
                    // A hash already present in history belongs to a row we are keeping; carrying a
                    // duplicate here would make Room's REPLACE strategy delete that live row. Drop
                    // the identity instead — the row still restores, it just stops claiming a
                    // message that is already claimed.
                    sourceMessageHash = txHash?.takeIf { it !in existingTxnHashes },
                    source = txSource,
                    confidence = txConfidence,
                    referenceNumber = txReference,
                    subtype = txSubtype
                )

                val fp = computeFingerprint(tempTx)
                if (!existingFingerprints.contains(fp)) {
                    // Reserve the hash so a second row inside this same backup file cannot claim
                    // the same identity and silently REPLACE the first one on insert.
                    tempTx.sourceMessageHash?.let { existingTxnHashes.add(it) }
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
                val existingPendingHashes = existingPending.mapNotNull { it.sourceMessageHash }.toMutableSet()

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
                    // v6 fields; tolerant reads keep v5-and-earlier backups restorable.
                    val pEndingBalance = if (pObj.has("endingBalance") && !pObj.isNull("endingBalance")) pObj.getDouble("endingBalance") else null
                    val pHash = pObj.optNullableString("sourceMessageHash")
                    val pSource = pObj.optNullableString("source")
                    val pConfidence = pObj.optNullableString("confidence")
                    val pReference = pObj.optNullableString("referenceNumber")
                    val pPostedAt = if (pObj.has("notificationPostedAt") && !pObj.isNull("notificationPostedAt")) pObj.getLong("notificationPostedAt") else null
                    val pAttempts = pObj.optInt("notificationAttempts", 0)

                    val pf = "${pAmount}_${pRawSmsBody}"
                    if (!existingPendingFingerprints.contains(pf)) {
                        // Same reasoning as the transaction path: never let a restored row claim an
                        // identity that is already live, and never let two rows in one file collide.
                        val safeHash = pHash?.takeIf { it !in existingPendingHashes }
                        safeHash?.let { existingPendingHashes.add(it) }
                        newPending.add(
                            PendingTransactionEntity(
                                amount = pAmount,
                                type = pType,
                                bankName = pBankName,
                                accountLast4 = pAccountLast4,
                                merchantOrPayee = pMerchant,
                                endingBalance = pEndingBalance,
                                rawSmsBody = pRawSmsBody,
                                senderHeader = pSenderHeader,
                                timestamp = pTimestamp,
                                sourceMessageHash = safeHash,
                                source = pSource,
                                confidence = pConfidence,
                                referenceNumber = pReference,
                                notificationPostedAt = pPostedAt,
                                notificationAttempts = pAttempts
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
            // v6: provenance / identity (schema v8). Null-safe — legacy rows carry NULL.
            obj.put("sourceMessageHash", tx.sourceMessageHash ?: JSONObject.NULL)
            obj.put("source", tx.source ?: JSONObject.NULL)
            obj.put("confidence", tx.confidence ?: JSONObject.NULL)
            obj.put("referenceNumber", tx.referenceNumber ?: JSONObject.NULL)
            // v7: split reimbursement subtype (schema v9)
            obj.put("subtype", tx.subtype ?: JSONObject.NULL)
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
            // v6: endingBalance was previously dropped on backup; plus provenance / identity /
            // notification bookkeeping (schema v8).
            obj.put("endingBalance", p.endingBalance ?: JSONObject.NULL)
            obj.put("sourceMessageHash", p.sourceMessageHash ?: JSONObject.NULL)
            obj.put("source", p.source ?: JSONObject.NULL)
            obj.put("confidence", p.confidence ?: JSONObject.NULL)
            obj.put("referenceNumber", p.referenceNumber ?: JSONObject.NULL)
            obj.put("notificationPostedAt", p.notificationPostedAt ?: JSONObject.NULL)
            obj.put("notificationAttempts", p.notificationAttempts)
            pendingArr.put(obj)
        }
        root.put("pendingTransactions", pendingArr)

        val checksum = computeChecksum(root.toString())
        root.put("checksum", checksum)

        return root.toString(2)
    }

    private fun computeFingerprint(tx: TransactionEntity): String {
        return "${tx.amount}_${tx.type}_${tx.subtype}_${tx.date}_${tx.notes}_${tx.account}_${tx.toAccount}_${tx.createdAt}"
    }

    private fun computeChecksum(data: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(data.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
