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
    PDF("PDF Report (With Charts & Ledger)", "pdf", "application/pdf"),
    XLSX("Excel Ledger (.xlsx)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
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

        val pageWidth = 595 // A4 standard width in points
        val pageHeight = 842 // A4 standard height in points

        // Computations
        val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val totalTransfer = transactions.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }
        val netBalance = totalIncome - totalExpense
        val savingsRate = if (totalIncome > 0) ((totalIncome - totalExpense) / totalIncome) * 100 else 0.0

        // Page 1: Executive KPI Cards & Balance Sheet Ledger
        val pageInfo1 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page1 = pdfDoc.startPage(pageInfo1)
        val canvas1 = page1.canvas

        val titlePaint = Paint().apply { color = Color.parseColor("#0F172A"); textSize = 22f; isFakeBoldText = true; isAntiAlias = true }
        val subTitlePaint = Paint().apply { color = Color.parseColor("#64748B"); textSize = 10f; isAntiAlias = true }
        val headerPaint = Paint().apply { color = Color.parseColor("#0F172A"); textSize = 13f; isFakeBoldText = true; isAntiAlias = true }
        val bodyPaint = Paint().apply { color = Color.parseColor("#334155"); textSize = 9.5f; isAntiAlias = true }
        val linePaint = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 1f }

        val incomeColor = Color.parseColor("#10B981")
        val expenseColor = Color.parseColor("#EF4444")
        val transferColor = Color.parseColor("#3B82F6")

        // App Logo & Header
        canvas1.drawText("Hisab Financial Statement & Ledger", 40f, 48f, titlePaint)
        canvas1.drawText("Generated on: ${LocalDate.now().format(dateFormatter)}  •  Total Transactions: ${transactions.size}", 40f, 65f, subTitlePaint)
        canvas1.drawLine(40f, 75f, (pageWidth - 40).toFloat(), 75f, linePaint)

        // 1. Top 4 KPI Summary Cards
        var currentY = 90f
        val cardWidth = 118f
        val cardHeight = 52f

        fun drawKpiCard(x: Float, label: String, value: String, accentHex: String) {
            val bgPaint = Paint().apply { color = Color.parseColor(accentHex); alpha = 20 }
            val borderPaint = Paint().apply { color = Color.parseColor(accentHex); style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true }
            val rect = RectF(x, currentY, x + cardWidth, currentY + cardHeight)
            canvas1.drawRoundRect(rect, 8f, 8f, bgPaint)
            canvas1.drawRoundRect(rect, 8f, 8f, borderPaint)

            val labelP = Paint().apply { color = Color.parseColor("#64748B"); textSize = 8.5f; isAntiAlias = true }
            val valP = Paint().apply { color = Color.parseColor("#0F172A"); textSize = 10.5f; isFakeBoldText = true; isAntiAlias = true }

            canvas1.drawText(label, x + 8f, currentY + 18f, labelP)
            canvas1.drawText(value, x + 8f, currentY + 38f, valP)
        }

        drawKpiCard(40f, "Total Income", CurrencyFormatter.format(totalIncome), "#10B981")
        drawKpiCard(168f, "Total Expenses", CurrencyFormatter.format(totalExpense), "#EF4444")
        drawKpiCard(296f, "Net Balance", CurrencyFormatter.format(netBalance), "#3B82F6")
        drawKpiCard(424f, "Savings Rate", "${String.format("%.1f", savingsRate)}%", "#F59E0B")

        currentY += 70f

        // 2. Financial Ledger Table (Date | Category | Type | Account | Debit | Credit)
        canvas1.drawText("Transaction Ledger", 40f, currentY, headerPaint)
        currentY += 15f

        val thBg = Paint().apply { color = Color.parseColor("#F1F5F9") }
        canvas1.drawRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 20f, thBg)

        val thText = Paint().apply { color = Color.parseColor("#1E293B"); textSize = 9f; isFakeBoldText = true; isAntiAlias = true }
        canvas1.drawText("Date", 48f, currentY + 14f, thText)
        canvas1.drawText("Category", 110f, currentY + 14f, thText)
        canvas1.drawText("Type", 220f, currentY + 14f, thText)
        canvas1.drawText("Account", 285f, currentY + 14f, thText)
        canvas1.drawText("Debit (Expense)", 375f, currentY + 14f, thText)
        canvas1.drawText("Credit (Income/Trf)", 465f, currentY + 14f, thText)

        currentY += 20f

        val rowBgEven = Paint().apply { color = Color.parseColor("#FFFFFF") }
        val rowBgOdd = Paint().apply { color = Color.parseColor("#F8FAFC") }

        transactions.take(30).forEachIndexed { idx, tx ->
            val bg = if (idx % 2 == 0) rowBgEven else rowBgOdd
            canvas1.drawRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 18f, bg)

            val catName = categoryMap[tx.categoryId]?.name ?: "General"
            val isDebit = tx.type == TransactionType.EXPENSE
            val debitText = if (isDebit) CurrencyFormatter.format(tx.amount) else "-"
            val creditText = if (!isDebit) CurrencyFormatter.format(tx.amount) else "-"

            val debitPaint = Paint().apply { color = if (isDebit) expenseColor else Color.parseColor("#94A3B8"); textSize = 9f; isAntiAlias = true }
            val creditPaint = Paint().apply { color = if (!isDebit) incomeColor else Color.parseColor("#94A3B8"); textSize = 9f; isAntiAlias = true }

            canvas1.drawText(DateUtils.formatShort(tx.date), 48f, currentY + 13f, bodyPaint)
            canvas1.drawText(catName.take(16), 110f, currentY + 13f, bodyPaint)
            canvas1.drawText(tx.type.name, 220f, currentY + 13f, bodyPaint)
            canvas1.drawText(tx.account.take(12), 285f, currentY + 13f, bodyPaint)
            canvas1.drawText(debitText, 375f, currentY + 13f, debitPaint)
            canvas1.drawText(creditText, 465f, currentY + 13f, creditPaint)

            canvas1.drawLine(40f, currentY + 18f, (pageWidth - 40).toFloat(), currentY + 18f, linePaint)
            currentY += 18f
        }

        // Ledger Totals Row
        canvas1.drawRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 20f, thBg)
        val totalTextPaint = Paint().apply { color = Color.parseColor("#0F172A"); textSize = 9.5f; isFakeBoldText = true; isAntiAlias = true }
        canvas1.drawText("TOTAL LEDGER", 48f, currentY + 14f, totalTextPaint)
        canvas1.drawText(CurrencyFormatter.format(totalExpense), 375f, currentY + 14f, totalTextPaint)
        canvas1.drawText(CurrencyFormatter.format(totalIncome + totalTransfer), 465f, currentY + 14f, totalTextPaint)

        canvas1.drawText("Page 1 of 2  •  Hisab Balance Sheet & Ledger Statement", (pageWidth / 2 - 80).toFloat(), 820f, subTitlePaint)
        pdfDoc.finishPage(page1)

        // ── Page 2: Visual Charts (Donut, Bar & Line Charts) ──
        val pageInfo2 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
        val page2 = pdfDoc.startPage(pageInfo2)
        val canvas2 = page2.canvas

        canvas2.drawText("Visual Analytics & Charts", 40f, 48f, titlePaint)
        canvas2.drawLine(40f, 60f, (pageWidth - 40).toFloat(), 60f, linePaint)

        currentY = 80f

        // 1. Donut Chart (Category Breakdown across Income, Expense, Transfer)
        canvas2.drawText("1. Category Distribution (All Types)", 40f, currentY, headerPaint)
        currentY += 15f

        val categoryBreakdown = transactions.groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }

        val totalAllAmount = transactions.sumOf { it.amount }
        val chartRadius = 55f
        val chartCenterX = 100f
        val chartCenterY = currentY + chartRadius + 10f

        val arcRect = RectF(chartCenterX - chartRadius, chartCenterY - chartRadius, chartCenterX + chartRadius, chartCenterY + chartRadius)
        var startAngle = -90f
        val chartColors = listOf("#4CAF50", "#FF9800", "#E91E63", "#2196F3", "#FF5722", "#9C27B0", "#00BCD4", "#8BC34A")

        if (totalAllAmount > 0) {
            categoryBreakdown.take(8).forEachIndexed { idx, entry ->
                val sweep = ((entry.value / totalAllAmount) * 360f).toFloat()
                val arcPaint = Paint().apply {
                    color = Color.parseColor(categoryMap[entry.key]?.colorHex ?: chartColors[idx % chartColors.size])
                    style = Paint.Style.STROKE
                    strokeWidth = 20f
                    isAntiAlias = true
                }
                canvas2.drawArc(arcRect, startAngle, sweep, false, arcPaint)
                startAngle += sweep
            }
        } else {
            val emptyPaint = Paint().apply { color = Color.parseColor("#CBD5E1"); style = Paint.Style.STROKE; strokeWidth = 20f; isAntiAlias = true }
            canvas2.drawArc(arcRect, 0f, 360f, false, emptyPaint)
        }

        var legendY = currentY + 10f
        categoryBreakdown.take(6).forEachIndexed { idx, entry ->
            val cat = categoryMap[entry.key]
            val colorHex = cat?.colorHex ?: chartColors[idx % chartColors.size]
            val pct = if (totalAllAmount > 0) (entry.value / totalAllAmount) * 100 else 0.0

            val dotPaint = Paint().apply { color = Color.parseColor(colorHex); isAntiAlias = true }
            canvas2.drawCircle(200f, legendY - 4f, 4f, dotPaint)
            canvas2.drawText("${cat?.name ?: "General"} (${cat?.type?.name ?: "EXPENSE"}): ${CurrencyFormatter.format(entry.value)} (${String.format("%.1f", pct)}%)", 212f, legendY, bodyPaint)
            legendY += 16f
        }

        currentY += 140f

        // 2. Bar Chart (Income vs Expense vs Transfer Comparison)
        canvas2.drawText("2. Financial Flow Comparison (Bar Chart)", 40f, currentY, headerPaint)
        currentY += 25f

        val maxVal = maxOf(totalIncome, totalExpense, totalTransfer, 1.0)
        val barMaxHeight = 80f

        fun drawBar(x: Float, label: String, amount: Double, colorHex: String) {
            val h = ((amount / maxVal) * barMaxHeight).toFloat().coerceAtLeast(4f)
            val barPaint = Paint().apply { color = Color.parseColor(colorHex); isAntiAlias = true }
            val rect = RectF(x, currentY + barMaxHeight - h, x + 50f, currentY + barMaxHeight)
            canvas2.drawRoundRect(rect, 4f, 4f, barPaint)

            val valP = Paint().apply { color = Color.parseColor("#0F172A"); textSize = 8.5f; isFakeBoldText = true; isAntiAlias = true }
            canvas2.drawText(CurrencyFormatter.format(amount), x, currentY + barMaxHeight - h - 5f, valP)
            canvas2.drawText(label, x + 8f, currentY + barMaxHeight + 14f, bodyPaint)
        }

        drawBar(80f, "Income", totalIncome, "#10B981")
        drawBar(220f, "Expense", totalExpense, "#EF4444")
        drawBar(360f, "Transfer", totalTransfer, "#3B82F6")

        currentY += 135f

        // 3. Line Chart (Daily Financial Trajectory Line)
        canvas2.drawText("3. Daily Trajectory (Line Chart)", 40f, currentY, headerPaint)
        currentY += 20f

        val sortedTx = transactions.sortedBy { it.date }
        val dailyMap = sortedTx.groupBy { it.date }
            .mapValues { (_, txs) ->
                txs.sumOf { if (it.type == TransactionType.INCOME) it.amount else if (it.type == TransactionType.EXPENSE) -it.amount else 0.0 }
            }

        val chartWidth = 500f
        val lineMaxH = 90f
        val startX = 55f
        val chartY = currentY

        val borderPaint = Paint().apply { color = Color.parseColor("#E2E8F0"); style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas2.drawRect(startX, chartY, startX + chartWidth, chartY + lineMaxH, borderPaint)

        if (dailyMap.isNotEmpty()) {
            val path = Path()
            val entries = dailyMap.entries.toList()
            val stepX = chartWidth / maxOf(entries.size - 1, 1)

            var accum = 0.0
            val values = entries.map { accum += it.value; accum }
            val minV = values.minOrNull() ?: 0.0
            val maxV = values.maxOrNull() ?: 1.0
            val rangeV = maxOf(maxV - minV, 1.0)

            values.forEachIndexed { i, v ->
                val px = startX + i * stepX
                val py = chartY + lineMaxH - (((v - minV) / rangeV) * (lineMaxH - 20f)).toFloat() - 10f
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                canvas2.drawCircle(px, py, 3f, Paint().apply { color = Color.parseColor("#0D9488"); isAntiAlias = true })
            }

            val strokeP = Paint().apply { color = Color.parseColor("#0D9488"); style = Paint.Style.STROKE; strokeWidth = 2.5f; isAntiAlias = true }
            canvas2.drawPath(path, strokeP)
        }

        canvas2.drawText("Page 2 of 2  •  Hisab Financial Statement & Visual Analytics", (pageWidth / 2 - 90).toFloat(), 820f, subTitlePaint)
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

        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont().apply { bold = true; color = IndexedColors.WHITE.index }
            setFont(font)
            alignment = HorizontalAlignment.CENTER
            borderBottom = BorderStyle.THIN
        }

        val txSheet = workbook.createSheet("Ledger & Transactions")
        val headers = listOf("Date", "Type", "Category", "Account", "To Account", "Debit (Expense)", "Credit (Income/Transfer)", "Notes")

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
            if (tx.type == TransactionType.EXPENSE) {
                row.createCell(5).setCellValue(tx.amount)
                row.createCell(6).setCellValue(0.0)
            } else {
                row.createCell(5).setCellValue(0.0)
                row.createCell(6).setCellValue(tx.amount)
            }
            row.createCell(7).setCellValue(tx.notes)
        }

        for (i in 0..7) txSheet.autoSizeColumn(i)

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
        sb.appendLine("Date,Type,Category,Account,ToAccount,Debit (Expense),Credit (Income/Transfer),Notes")

        for (tx in transactions) {
            val catName = categoryMap[tx.categoryId]?.name ?: "Unknown"
            val debit = if (tx.type == TransactionType.EXPENSE) tx.amount else 0.0
            val credit = if (tx.type != TransactionType.EXPENSE) tx.amount else 0.0
            val line = listOf(
                tx.date.toString(),
                tx.type.name,
                "\"${catName}\"",
                "\"${tx.account}\"",
                "\"${tx.toAccount ?: ""}\"",
                debit.toString(),
                credit.toString(),
                "\"${tx.notes}\""
            ).joinToString(",")
            sb.appendLine(line)
        }
        stream.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    // ── JSON BACKUP GENERATOR ──────────────────────────────────────────────────

    private suspend fun generateJsonReport(
        stream: OutputStream,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>
    ) {
        val backupManager = AutoBackupManager(context, HisabDatabase.getDatabase(context))
        val jsonStr = backupManager.exportBackupString()
        stream.write(jsonStr.toByteArray(Charsets.UTF_8))
    }
}
