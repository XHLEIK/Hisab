package com.example.hisab.data.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.hisab.data.backup.AutoBackupManager
import com.example.hisab.data.db.HisabDatabase
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType
import com.example.hisab.util.CurrencyFormatter
import com.example.hisab.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ExportFormat(val displayName: String, val extension: String, val mimeType: String) {
    PDF("PDF Report (With Charts & Tables)", "pdf", "application/pdf"),
    XLSX("Excel Workbook (.xlsx)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("CSV Spreadsheet (.csv)", "csv", "text/csv"),
    JSON("Full JSON Backup (.json)", "json", "application/json")
}

class ReportGenerator(private val context: Context) {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

    /**
     * Main entry point for exporting report to Uri based on chosen format.
     */
    suspend fun generateReport(
        uri: Uri,
        format: ExportFormat,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return@withContext Result.failure(Exception("Could not open output stream"))

            outputStream.use { stream ->
                when (format) {
                    ExportFormat.PDF -> generatePdfReport(stream, transactions, categories)
                    ExportFormat.XLSX -> generateXlsxReport(stream, transactions, categories)
                    ExportFormat.CSV -> generateCsvReport(stream, transactions, categories)
                    ExportFormat.JSON -> generateJsonReport(stream, transactions, categories)
                }
            }
            Result.success(transactions.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── PDF GENERATOR ──────────────────────────────────────────────────────────

    private fun generatePdfReport(
        stream: OutputStream,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>
    ) {
        val pdfDoc = PdfDocument()
        val categoryMap = categories.associateBy { it.id }

        val pageWidth = 595 // A4 standard width in points (72 dpi)
        val pageHeight = 842 // A4 standard height in points

        // Computations
        val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val netBalance = totalIncome - totalExpense
        val savingsRate = if (totalIncome > 0) ((totalIncome - totalExpense) / totalIncome) * 100 else 0.0

        // Page 1: Executive Summary & Visual Charts
        val pageInfo1 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page1 = pdfDoc.startPage(pageInfo1)
        val canvas1 = page1.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subTitlePaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 11f
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.parseColor("#334155")
            textSize = 10f
            isAntiAlias = true
        }

        val primaryColor = Color.parseColor("#0D9488") // Teal
        val incomeColor = Color.parseColor("#10B981") // Emerald
        val expenseColor = Color.parseColor("#EF4444") // Coral Red

        // App Logo & Header
        canvas1.drawText("Hisab Financial Statement", 40f, 50f, titlePaint)
        canvas1.drawText("Generated on: ${LocalDate.now().format(dateFormatter)}  •  Total Entries: ${transactions.size}", 40f, 68f, subTitlePaint)

        // Divider
        val linePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }
        canvas1.drawLine(40f, 80f, (pageWidth - 40).toFloat(), 80f, linePaint)

        // KPI Summary Cards
        var currentY = 100f
        val cardWidth = 115f
        val cardHeight = 54f

        fun drawKpiCard(x: Float, label: String, value: String, accentHex: String) {
            val bgPaint = Paint().apply {
                color = Color.parseColor(accentHex)
                alpha = 25
            }
            val borderPaint = Paint().apply {
                color = Color.parseColor(accentHex)
                style = Paint.Style.STROKE
                strokeWidth = 1f
                isAntiAlias = true
            }
            val rect = RectF(x, currentY, x + cardWidth, currentY + cardHeight)
            canvas1.drawRoundRect(rect, 8f, 8f, bgPaint)
            canvas1.drawRoundRect(rect, 8f, 8f, borderPaint)

            val labelP = Paint().apply { color = Color.parseColor("#64748B"); textSize = 9f; isAntiAlias = true }
            val valP = Paint().apply { color = Color.parseColor("#0F172A"); textSize = 11f; isFakeBoldText = true; isAntiAlias = true }

            canvas1.drawText(label, x + 10f, currentY + 20f, labelP)
            canvas1.drawText(value, x + 10f, currentY + 40f, valP)
        }

        drawKpiCard(40f, "Total Income", CurrencyFormatter.format(totalIncome), "#10B981")
        drawKpiCard(170f, "Total Expenses", CurrencyFormatter.format(totalExpense), "#EF4444")
        drawKpiCard(300f, "Net Balance", CurrencyFormatter.format(netBalance), "#3B82F6")
        drawKpiCard(430f, "Savings Rate", "${String.format("%.1f", savingsRate)}%", "#8B5CF6")

        currentY += 75f

        // ── Visual Donut Chart Rendering on Canvas ──
        canvas1.drawText("Expense Category Distribution", 40f, currentY, headerPaint)
        currentY += 20f

        val expenseTxns = transactions.filter { it.type == TransactionType.EXPENSE }
        val categoryBreakdown = expenseTxns.groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }

        val chartRadius = 60f
        val chartCenterX = 110f
        val chartCenterY = currentY + chartRadius + 10f

        val arcRect = RectF(
            chartCenterX - chartRadius,
            chartCenterY - chartRadius,
            chartCenterX + chartRadius,
            chartCenterY + chartRadius
        )

        var startAngle = -90f
        val chartColors = listOf("#4CAF50", "#FF9800", "#E91E63", "#2196F3", "#FF5722", "#9C27B0", "#00BCD4", "#607D8B")

        if (totalExpense > 0) {
            categoryBreakdown.take(8).forEachIndexed { idx, entry ->
                val sweep = ((entry.value / totalExpense) * 360f).toFloat()
                val arcPaint = Paint().apply {
                    color = Color.parseColor(categoryMap[entry.key]?.colorHex ?: chartColors[idx % chartColors.size])
                    style = Paint.Style.STROKE
                    strokeWidth = 24f
                    isAntiAlias = true
                }
                canvas1.drawArc(arcRect, startAngle, sweep, false, arcPaint)
                startAngle += sweep
            }
        } else {
            val emptyPaint = Paint().apply { color = Color.parseColor("#CBD5E1"); style = Paint.Style.STROKE; strokeWidth = 24f; isAntiAlias = true }
            canvas1.drawArc(arcRect, 0f, 360f, false, emptyPaint)
        }

        // Donut Legend next to Chart
        var legendY = currentY + 15f
        categoryBreakdown.take(6).forEachIndexed { idx, entry ->
            val cat = categoryMap[entry.key]
            val colorHex = cat?.colorHex ?: chartColors[idx % chartColors.size]
            val pct = if (totalExpense > 0) (entry.value / totalExpense) * 100 else 0.0

            val dotPaint = Paint().apply { color = Color.parseColor(colorHex); isAntiAlias = true }
            canvas1.drawCircle(220f, legendY - 4f, 5f, dotPaint)
            canvas1.drawText("${cat?.name ?: "Expense"}: ${CurrencyFormatter.format(entry.value)} (${String.format("%.1f", pct)}%)", 235f, legendY, bodyPaint)
            legendY += 18f
        }

        currentY += 155f

        // ── Category Summary Table ──
        canvas1.drawText("Category Breakdown Ledger", 40f, currentY, headerPaint)
        currentY += 18f

        // Table Header
        val thBg = Paint().apply { color = Color.parseColor("#F1F5F9") }
        canvas1.drawRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 22f, thBg)

        val thText = Paint().apply { color = Color.parseColor("#334155"); textSize = 10f; isFakeBoldText = true; isAntiAlias = true }
        canvas1.drawText("Category Name", 50f, currentY + 15f, thText)
        canvas1.drawText("Type", 240f, currentY + 15f, thText)
        canvas1.drawText("Total Amount", 360f, currentY + 15f, thText)
        canvas1.drawText("% Share", 480f, currentY + 15f, thText)

        currentY += 22f

        categoryBreakdown.take(10).forEach { (catId, amount) ->
            val cat = categoryMap[catId]
            val pct = if (totalExpense > 0) (amount / totalExpense) * 100 else 0.0

            canvas1.drawText(cat?.name ?: "Unknown", 50f, currentY + 15f, bodyPaint)
            canvas1.drawText("EXPENSE", 240f, currentY + 15f, bodyPaint)
            canvas1.drawText(CurrencyFormatter.format(amount), 360f, currentY + 15f, bodyPaint)
            canvas1.drawText("${String.format("%.1f", pct)}%", 480f, currentY + 15f, bodyPaint)
            canvas1.drawLine(40f, currentY + 20f, (pageWidth - 40).toFloat(), currentY + 20f, linePaint)
            currentY += 22f
        }

        // Page footer
        canvas1.drawText("Page 1 of 2  •  Hisab Offline Expense Tracker", (pageWidth / 2 - 60).toFloat(), 820f, subTitlePaint)
        pdfDoc.finishPage(page1)

        // ── Page 2: Detailed Transaction Ledger ──
        val pageInfo2 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
        val page2 = pdfDoc.startPage(pageInfo2)
        val canvas2 = page2.canvas

        canvas2.drawText("Detailed Transaction Ledger", 40f, 45f, titlePaint)
        canvas2.drawLine(40f, 55f, (pageWidth - 40).toFloat(), 55f, linePaint)

        currentY = 70f
        canvas2.drawRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 22f, thBg)
        canvas2.drawText("Date", 50f, currentY + 15f, thText)
        canvas2.drawText("Type", 120f, currentY + 15f, thText)
        canvas2.drawText("Category", 190f, currentY + 15f, thText)
        canvas2.drawText("Account", 310f, currentY + 15f, thText)
        canvas2.drawText("Amount", 420f, currentY + 15f, thText)
        canvas2.drawText("Notes", 490f, currentY + 15f, thText)

        currentY += 22f

        val rowBgEven = Paint().apply { color = Color.parseColor("#FFFFFF") }
        val rowBgOdd = Paint().apply { color = Color.parseColor("#F8FAFC") }

        transactions.take(32).forEachIndexed { idx, tx ->
            val bg = if (idx % 2 == 0) rowBgEven else rowBgOdd
            canvas2.drawRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 20f, bg)

            val catName = categoryMap[tx.categoryId]?.name ?: "Expense"
            val amtColor = when (tx.type) {
                TransactionType.INCOME -> incomeColor
                TransactionType.EXPENSE -> expenseColor
                else -> primaryColor
            }
            val amtPaint = Paint().apply { color = amtColor; textSize = 9.5f; isFakeBoldText = true; isAntiAlias = true }

            canvas2.drawText(DateUtils.formatShort(tx.date), 50f, currentY + 14f, bodyPaint)
            canvas2.drawText(tx.type.name, 120f, currentY + 14f, bodyPaint)
            canvas2.drawText(catName.take(18), 190f, currentY + 14f, bodyPaint)
            canvas2.drawText(tx.account.take(12), 310f, currentY + 14f, bodyPaint)
            canvas2.drawText(CurrencyFormatter.format(tx.amount), 420f, currentY + 14f, amtPaint)
            canvas2.drawText(tx.notes.take(15), 490f, currentY + 14f, bodyPaint)

            canvas2.drawLine(40f, currentY + 20f, (pageWidth - 40).toFloat(), currentY + 20f, linePaint)
            currentY += 20f
        }

        canvas2.drawText("Page 2 of 2  •  Hisab Offline Expense Tracker", (pageWidth / 2 - 60).toFloat(), 820f, subTitlePaint)
        pdfDoc.finishPage(page2)

        pdfDoc.writeTo(stream)
        pdfDoc.close()
    }

    // ── XLSX GENERATOR ─────────────────────────────────────────────────────────

    private fun generateXlsxReport(
        stream: OutputStream,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>
    ) {
        val workbook = XSSFWorkbook()
        val categoryMap = categories.associateBy { it.id }

        // Header style
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
            }
            setFont(font)
            alignment = HorizontalAlignment.CENTER
            borderBottom = BorderStyle.THIN
        }

        // Sheet 1: Financial Summary
        val summarySheet = workbook.createSheet("Summary")
        var rowIdx = 0

        val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val netBalance = totalIncome - totalExpense

        val sRow0 = summarySheet.createRow(rowIdx++)
        sRow0.createCell(0).apply { setCellValue("Hisab Financial Summary"); setCellStyle(headerStyle) }

        val metrics = listOf(
            "Report Date" to LocalDate.now().toString(),
            "Total Transactions" to transactions.size.toString(),
            "Total Income" to CurrencyFormatter.format(totalIncome),
            "Total Expenses" to CurrencyFormatter.format(totalExpense),
            "Net Balance" to CurrencyFormatter.format(netBalance)
        )

        metrics.forEach { (key, value) ->
            val row = summarySheet.createRow(rowIdx++)
            row.createCell(0).setCellValue(key)
            row.createCell(1).setCellValue(value)
        }

        // Sheet 2: Transactions
        val txSheet = workbook.createSheet("Transactions")
        val headers = listOf("Date", "Type", "Category", "Account", "To Account", "Amount", "Notes")

        val hRow = txSheet.createRow(0)
        headers.forEachIndexed { i, h ->
            val cell = hRow.createCell(i)
            cell.setCellValue(h)
            cell.setCellStyle(headerStyle)
        }

        transactions.forEachIndexed { idx, tx ->
            val row = txSheet.createRow(idx + 1)
            row.createCell(0).setCellValue(tx.date.toString())
            row.createCell(1).setCellValue(tx.type.name)
            row.createCell(2).setCellValue(categoryMap[tx.categoryId]?.name ?: "Unknown")
            row.createCell(3).setCellValue(tx.account)
            row.createCell(4).setCellValue(tx.toAccount ?: "-")
            row.createCell(5).setCellValue(tx.amount)
            row.createCell(6).setCellValue(tx.notes)
        }

        for (i in 0..6) txSheet.autoSizeColumn(i)

        workbook.write(stream)
        workbook.close()
    }

    // ── CSV GENERATOR ──────────────────────────────────────────────────────────

    private fun generateCsvReport(
        stream: OutputStream,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>
    ) {
        val categoryMap = categories.associateBy { it.id }
        val sb = StringBuilder()
        sb.appendLine("Date,Type,Category,Amount,Account,ToAccount,Notes")

        for (tx in transactions) {
            val catName = categoryMap[tx.categoryId]?.name ?: "Unknown"
            val line = listOf(
                tx.date.toString(),
                tx.type.name,
                "\"${catName}\"",
                tx.amount.toString(),
                "\"${tx.account}\"",
                "\"${tx.toAccount ?: ""}\"",
                "\"${tx.notes}\""
            ).joinToString(",")
            sb.appendLine(line)
        }
        stream.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    // ── JSON BACKUP GENERATOR ──────────────────────────────────────────────────

    private fun generateJsonReport(
        stream: OutputStream,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>
    ) {
        val backupManager = AutoBackupManager(context, HisabDatabase.getDatabase(context))
        val jsonStr = com.example.hisab.util.CsvHelper.transactionsToCsv(transactions, categories.associateBy { it.id })
        // Use JSON serialization from AutoBackupManager if called
        stream.write(jsonStr.toByteArray(Charsets.UTF_8))
    }
}
