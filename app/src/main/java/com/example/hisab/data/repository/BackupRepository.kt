package com.example.hisab.data.repository

import android.content.Context
import android.net.Uri
import com.example.hisab.data.backup.AutoBackupManager
import com.example.hisab.data.db.HisabDatabase
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.export.ExportFormat
import com.example.hisab.data.export.ReportGenerator
import com.example.hisab.util.CsvHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupRepository(
    val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private var autoBackupManager: AutoBackupManager? = null
) {

    /**
     * Exports financial report or backup in the specified format (PDF, XLSX, CSV, JSON),
     * optionally filtered by targetMonth (YearMonth).
     */
    suspend fun exportReport(
        context: Context,
        uri: Uri,
        format: ExportFormat,
        targetMonth: java.time.YearMonth? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val transactions = transactionRepository.getAllTransactionsSync()
            val categories = categoryRepository.getAllCategoriesSync()
            val reportGenerator = ReportGenerator(context)
            reportGenerator.generateReport(uri, format, transactions, categories, targetMonth)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Legacy CSV export.
     */
    suspend fun exportToCsv(context: Context, uri: Uri): Result<Int> =
        exportReport(context, uri, ExportFormat.CSV)

    /**
     * Attempts smart auto-restore from Documents/Hisab/hisab_auto_backup.json.
     */
    suspend fun smartImport(context: Context): Boolean = withContext(Dispatchers.IO) {
        val manager = autoBackupManager ?: AutoBackupManager(context, HisabDatabase.getDatabase(context))
        manager.smartImportFromDocuments()
    }

    /**
     * Imports data from a backup file (JSON or CSV).
     */
    suspend fun importBackup(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: return@withContext Result.failure(Exception("Could not open file input stream"))

            if (content.trimStart().startsWith("{")) {
                // JSON Backup format
                val manager = autoBackupManager ?: AutoBackupManager(context, HisabDatabase.getDatabase(context))
                val success = manager.restoreFromJson(content)
                if (success) {
                    val count = transactionRepository.getAllTransactionsSync().size
                    Result.success(count)
                } else {
                    Result.failure(Exception("Invalid or corrupted JSON backup file"))
                }
            } else {
                // CSV Backup format
                importFromCsv(context, uri)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Imports transactions from a CSV file.
     */
    suspend fun importFromCsv(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val csvContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).readText()
            } ?: return@withContext Result.failure(Exception("Could not open input stream"))

            val existingCategories = categoryRepository.getAllCategoriesSync()
            val categoryNameMap = existingCategories.associateBy { "${it.name}_${it.type}" }

            val parsed = CsvHelper.csvToTransactions(csvContent)
            var importedCount = 0

            for ((transaction, categoryName) in parsed) {
                val key = "${categoryName}_${transaction.type}"
                val category = categoryNameMap[key]
                val categoryId = if (category != null) {
                    category.id
                } else {
                    categoryRepository.insertCategory(
                        CategoryEntity(
                            name = categoryName,
                            type = transaction.type,
                            iconName = "MoreHoriz",
                            colorHex = "#607D8B",
                            isDefault = false
                        )
                    )
                }
                transactionRepository.insert(transaction.copy(id = 0, categoryId = categoryId))
                importedCount++
            }

            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
