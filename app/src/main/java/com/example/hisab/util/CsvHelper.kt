package com.example.hisab.util

import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CsvHelper {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // yyyy-MM-dd

    private const val HEADER = "Date,Type,Category,Amount,Account,Notes"

    /**
     * Converts a list of transactions to CSV string.
     * Category names are resolved using the provided map.
     */
    fun transactionsToCsv(
        transactions: List<TransactionEntity>,
        categoryMap: Map<Long, CategoryEntity>
    ): String {
        val sb = StringBuilder()
        sb.appendLine(HEADER)
        for (txn in transactions) {
            val categoryName = categoryMap[txn.categoryId]?.name ?: "Unknown"
            val line = listOf(
                txn.date.format(dateFormatter),
                txn.type.name,
                escapeCsvField(categoryName),
                txn.amount.toString(),
                escapeCsvField(txn.account),
                escapeCsvField(txn.notes)
            ).joinToString(",")
            sb.appendLine(line)
        }
        return sb.toString()
    }

    /**
     * Parses a CSV string into a list of (TransactionEntity, categoryName) pairs.
     * The categoryName is needed to resolve/create category IDs during import.
     */
    fun csvToTransactions(csv: String): List<Pair<TransactionEntity, String>> {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()

        // Skip header
        return lines.drop(1).mapNotNull { line ->
            try {
                val fields = parseCsvLine(line)
                if (fields.size < 4) return@mapNotNull null

                val date = LocalDate.parse(fields[0].trim(), dateFormatter)
                val type = TransactionType.valueOf(fields[1].trim().uppercase())
                val categoryName = fields[2].trim()
                val amount = fields[3].trim().toDouble()
                val account = fields.getOrElse(4) { "Cash" }.trim()
                val notes = fields.getOrElse(5) { "" }.trim()

                val transaction = TransactionEntity(
                    amount = amount,
                    type = type,
                    categoryId = 0, // Will be resolved during import
                    account = account.ifEmpty { "Cash" },
                    date = date,
                    notes = notes
                )
                Pair(transaction, categoryName)
            } catch (e: Exception) {
                null // Skip malformed lines
            }
        }
    }

    /**
     * Escapes a CSV field: wraps in quotes if it contains comma, quote, or newline.
     */
    private fun escapeCsvField(field: String): String {
        return if (field.contains(',') || field.contains('"') || field.contains('\n')) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }

    /**
     * Simple CSV line parser that handles quoted fields.
     */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        fields.add(current.toString())
        return fields
    }
}
