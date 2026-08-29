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
        categories: List<CategoryEntity>,
        targetMonth: java.time.YearMonth? = null,
        currentBalances: Map<String, Double> = emptyMap()
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return@withContext Result.failure(Exception("Could not open output stream"))

            val filteredTx = if (targetMonth != null) {
                transactions.filter {
                    it.date.year == targetMonth.year && it.date.month == targetMonth.month
                }
            } else {
                transactions
            }

            // Requirement 2: Sort in ASCENDING order (oldest first, newest last)
            val sortedTx = filteredTx.sortedWith(
                compareBy<TransactionEntity> { it.date }.thenBy { it.createdAt }
            )

            outputStream.use { stream ->
                when (format) {
                    ExportFormat.PDF -> generatePdfReport(stream, sortedTx, categories, targetMonth, currentBalances)
                    ExportFormat.XLSX -> generateXlsxReport(stream, sortedTx, categories)
                    ExportFormat.CSV -> generateCsvReport(stream, sortedTx, categories)
                    ExportFormat.JSON -> generateJsonReport(stream, sortedTx, targetMonth)
                }
            }
            Result.success(sortedTx.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── PDF GENERATOR ──────────────────────────────────────────────────────────

    private fun generatePdfReport(
        stream: OutputStream,
        transactions: List<TransactionEntity>, // Ascending sorted
        categories: List<CategoryEntity>,
        targetMonth: java.time.YearMonth? = null,
        currentBalances: Map<String, Double> = emptyMap()
    ) {
        val pdfDoc = PdfDocument()
        val categoryMap = categories.associateBy { it.id }

        val pageWidth = 595 // A4 standard width in points
        val pageHeight = 842 // A4 standard height in points

        // ── ONE authoritative summary (see FinancialSummary / SplitAccounting) ──
        // Period metrics come from the report-scope rows; current account balances are
        // passed in by the caller (computed from the FULL ledger — a month-scoped row
        // list cannot produce a current balance).
        val summary = com.example.hisab.util.FinancialSummary.of(
            transactions = transactions,
            currentBalances = currentBalances
        )

        // Paints
        val titlePaint = Paint().apply { color = Color.parseColor("#0F172A"); textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
        val subTitlePaint = Paint().apply { color = Color.parseColor("#64748B"); textSize = 9.5f; isAntiAlias = true }
        val headerPaint = Paint().apply { color = Color.parseColor("#0F172A"); textSize = 12f; isFakeBoldText = true; isAntiAlias = true }
        val bodyPaint = Paint().apply { color = Color.parseColor("#334155"); textSize = 8.5f; isAntiAlias = true }
        val linePaint = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 1f }

        val incomeColor = Color.parseColor("#10B981")
        val expenseColor = Color.parseColor("#EF4444")
        val savingsColor = Color.parseColor("#8B5CF6")
        val netColor = Color.parseColor("#3B82F6")

        // Split transactions into pages of 24 rows max for ledger
        val rowsPerPage = 24
        val ledgerPages = if (transactions.isEmpty()) 1 else (transactions.size + rowsPerPage - 1) / rowsPerPage
        var pageNumber = 1

        for (p in 0 until ledgerPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            // App Header
            val titleText = if (targetMonth != null) {
                val monthTitle = targetMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                "Hisab Financial Statement ($monthTitle)"
            } else {
                "Hisab Financial Statement & Ledger"
            }
            canvas.drawText(titleText, 35f, 42f, titlePaint)
            val reportPeriodText = if (targetMonth != null) {
                val monthStr = targetMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                "Period: $monthStr  •  Total Transactions: ${transactions.size}"
            } else if (transactions.isNotEmpty()) {
                "Period: ${transactions.first().date.format(dateFormatter)} to ${transactions.last().date.format(dateFormatter)}  •  Total Txns: ${transactions.size}"
            } else {
                "Generated on: ${LocalDate.now().format(dateFormatter)}  •  Total Txns: 0"
            }
            canvas.drawText(reportPeriodText, 35f, 57f, subTitlePaint)
            canvas.drawLine(35f, 65f, (pageWidth - 35).toFloat(), 65f, linePaint)

            var currentY = 80f

            // Requirement 4: Top 4 KPI Summary Cards (Show Savings AMOUNT, not percentage!)
            if (p == 0) {
                val cardWidth = 120f
                val cardHeight = 48f

                fun drawKpiCard(x: Float, label: String, value: String, accentHex: String) {
                    val bgPaint = Paint().apply { color = Color.parseColor(accentHex); alpha = 18 }
                    val borderPaint = Paint().apply { color = Color.parseColor(accentHex); style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true }
                    val rect = RectF(x, currentY, x + cardWidth, currentY + cardHeight)
                    canvas.drawRoundRect(rect, 8f, 8f, bgPaint)
                    canvas.drawRoundRect(rect, 8f, 8f, borderPaint)

                    val labelP = Paint().apply { color = Color.parseColor("#64748B"); textSize = 8f; isAntiAlias = true }
                    val valP = Paint().apply { color = Color.parseColor("#0F172A"); textSize = 10f; isFakeBoldText = true; isAntiAlias = true }

                    canvas.drawText(label, x + 8f, currentY + 16f, labelP)
                    canvas.drawText(value, x + 8f, currentY + 35f, valP)
                }

                // Row 1 — PERIOD METRICS for the report scope
                drawKpiCard(35f, "Total Income", CurrencyFormatter.format(summary.totalIncome), "#10B981")
                drawKpiCard(165f, "Gross Expenses", CurrencyFormatter.format(summary.grossExpense), "#EF4444")
                drawKpiCard(295f, "Net Expenses", CurrencyFormatter.format(summary.netExpense), "#F97316")
                drawKpiCard(425f, "Transfer Activity", CurrencyFormatter.format(summary.transferActivity), "#8B5CF6")

                currentY += 64f

                // Row 2 — CURRENT ACCOUNT STATE (ledger balances, not period flow).
                // Transfer volume is deliberately absent: moving money between your own
                // accounts is not a balance.
                if (summary.accountBalances.isNotEmpty()) {
                    currentY += 6f
                    canvas.drawText("Account Balances (Current)", 35f, currentY, headerPaint)
                    currentY += 12f
                    summary.accountBalances.entries.take(3).forEachIndexed { i, (name, balance) ->
                        drawKpiCard(35f + i * 130f, name.take(14), CurrencyFormatter.format(balance), "#3B82F6")
                    }
                    drawKpiCard(425f, "Combined Balance", CurrencyFormatter.format(summary.combinedBalance), "#3B82F6")
                    currentY += 64f
                }
            }

            // Requirement 3: Financial Ledger Table with separate Credit & Savings columns
            canvas.drawText("Transaction Ledger (Oldest → Newest)", 35f, currentY, headerPaint)
            currentY += 12f

            val thBg = Paint().apply { color = Color.parseColor("#F1F5F9") }
            canvas.drawRect(35f, currentY, (pageWidth - 35).toFloat(), currentY + 18f, thBg)

            val thText = Paint().apply { color = Color.parseColor("#1E293B"); textSize = 8.5f; isFakeBoldText = true; isAntiAlias = true }
            canvas.drawText("Date", 40f, currentY + 12f, thText)
            canvas.drawText("Category", 105f, currentY + 12f, thText)
            canvas.drawText("Type", 205f, currentY + 12f, thText)
            canvas.drawText("Account", 265f, currentY + 12f, thText)
            canvas.drawText("Debit (Expense)", 345f, currentY + 12f, thText)
            canvas.drawText("Credit (Income)", 425f, currentY + 12f, thText)
            canvas.drawText("Transfer", 500f, currentY + 12f, thText)

            currentY += 18f

            val rowBgEven = Paint().apply { color = Color.parseColor("#FFFFFF") }
            val rowBgOdd = Paint().apply { color = Color.parseColor("#F8FAFC") }

            val pageTxns = transactions.drop(p * rowsPerPage).take(rowsPerPage)
            val isLastLedgerPage = (p == ledgerPages - 1)

            pageTxns.forEachIndexed { idx, tx ->
                val bg = if (idx % 2 == 0) rowBgEven else rowBgOdd
                canvas.drawRect(35f, currentY, (pageWidth - 35).toFloat(), currentY + 16f, bg)

                val catName = categoryMap[tx.categoryId]?.name ?: "General"
                
                // Requirement 1: Explicit formatted date string (NO "Today")
                val dateStr = tx.date.format(dateFormatter)

                val isExpense = tx.type == TransactionType.EXPENSE
                val isTransfer = tx.type == TransactionType.TRANSFER
                val isIncome = tx.type == TransactionType.INCOME

                // Requirement 3: Deduct Savings from Credit and put in Savings column
                val debitText = if (isExpense) CurrencyFormatter.format(tx.amount) else "-"
                val creditText = if (isIncome) CurrencyFormatter.format(tx.amount) else "-"
                val savingsText = if (isTransfer) CurrencyFormatter.format(tx.amount) else "-"

                val debitPaint = Paint().apply { color = if (isExpense) expenseColor else Color.parseColor("#94A3B8"); textSize = 8.5f; isAntiAlias = true }
                val creditPaint = Paint().apply { color = if (isIncome) incomeColor else Color.parseColor("#94A3B8"); textSize = 8.5f; isAntiAlias = true }
                val savingsPaint = Paint().apply { color = if (isTransfer) savingsColor else Color.parseColor("#94A3B8"); textSize = 8.5f; isAntiAlias = true }

                val typeLabel = if (tx.subtype == "SPLIT_REIMBURSEMENT") "Expense/Split" else tx.type.name
                canvas.drawText(dateStr, 40f, currentY + 12f, bodyPaint)
                canvas.drawText(catName.take(15), 105f, currentY + 12f, bodyPaint)
                canvas.drawText(typeLabel, 205f, currentY + 12f, bodyPaint)
                canvas.drawText(tx.account.take(10), 265f, currentY + 12f, bodyPaint)
                canvas.drawText(debitText, 345f, currentY + 12f, debitPaint)
                canvas.drawText(creditText, 425f, currentY + 12f, creditPaint)
                canvas.drawText(savingsText, 500f, currentY + 12f, savingsPaint)

                canvas.drawLine(35f, currentY + 16f, (pageWidth - 35).toFloat(), currentY + 16f, linePaint)
                currentY += 16f
            }

            // Ledger Totals Row — period sums of the columns above (a ledger footer, not balances)
            if (isLastLedgerPage) {
                canvas.drawRect(35f, currentY, (pageWidth - 35).toFloat(), currentY + 18f, thBg)
                val totalTextPaint = Paint().apply { color = Color.parseColor("#0F172A"); textSize = 8.5f; isFakeBoldText = true; isAntiAlias = true }
                canvas.drawText("TOTALS (PERIOD)", 40f, currentY + 13f, totalTextPaint)
                canvas.drawText(CurrencyFormatter.format(summary.grossExpense), 345f, currentY + 13f, totalTextPaint)
                canvas.drawText(CurrencyFormatter.format(summary.totalIncome), 425f, currentY + 13f, totalTextPaint)
                canvas.drawText(CurrencyFormatter.format(summary.transferActivity), 500f, currentY + 13f, totalTextPaint)
            }

            canvas.drawText("Page $pageNumber  •  Hisab Balance Sheet & Ledger Statement", (pageWidth / 2 - 90).toFloat(), 815f, subTitlePaint)
            pdfDoc.finishPage(page)
            pageNumber++
        }

        // ── Requirement 5: Whole Month Visual Analytics & Charts Page ───────────────────
        val chartPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val chartPage = pdfDoc.startPage(chartPageInfo)
        val canvas2 = chartPage.canvas

        canvas2.drawText("Whole Month Visual Analytics & Breakdown", 35f, 42f, titlePaint)
        canvas2.drawLine(35f, 55f, (pageWidth - 35).toFloat(), 55f, linePaint)

        var currentY = 70f

        // 1. Category Distribution Donut Chart
        canvas2.drawText("1. Monthly Category Expense & Savings Breakdown", 35f, currentY, headerPaint)
        currentY += 15f

        val categoryBreakdown = transactions.groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }

        val totalAllAmount = transactions.sumOf { it.amount }
        val chartRadius = 50f
        val chartCenterX = 95f
        val chartCenterY = currentY + chartRadius + 5f

        val arcRect = RectF(chartCenterX - chartRadius, chartCenterY - chartRadius, chartCenterX + chartRadius, chartCenterY + chartRadius)
        var startAngle = -90f
        val chartColors = listOf("#4CAF50", "#FF9800", "#E91E63", "#2196F3", "#FF5722", "#9C27B0", "#00BCD4", "#8BC34A")

        if (totalAllAmount > 0) {
            categoryBreakdown.take(8).forEachIndexed { idx, entry ->
                val sweep = ((entry.value / totalAllAmount) * 360f).toFloat()
                val arcPaint = Paint().apply {
                    color = Color.parseColor(categoryMap[entry.key]?.colorHex ?: chartColors[idx % chartColors.size])
                    style = Paint.Style.STROKE
                    strokeWidth = 18f
                    isAntiAlias = true
                }
                canvas2.drawArc(arcRect, startAngle, sweep, false, arcPaint)
                startAngle += sweep
            }
        } else {
            val emptyPaint = Paint().apply { color = Color.parseColor("#CBD5E1"); style = Paint.Style.STROKE; strokeWidth = 18f; isAntiAlias = true }
            canvas2.drawArc(arcRect, 0f, 360f, false, emptyPaint)
        }

        var legendY = currentY + 5f
        categoryBreakdown.take(6).forEachIndexed { idx, entry ->
            val cat = categoryMap[entry.key]
            val colorHex = cat?.colorHex ?: chartColors[idx % chartColors.size]
            val pct = if (totalAllAmount > 0) (entry.value / totalAllAmount) * 100 else 0.0

            val dotPaint = Paint().apply { color = Color.parseColor(colorHex); isAntiAlias = true }
            canvas2.drawCircle(195f, legendY - 3f, 3.5f, dotPaint)
            canvas2.drawText("${cat?.name ?: "General"} (${cat?.type?.name ?: "EXPENSE"}): ${CurrencyFormatter.format(entry.value)} (${String.format("%.1f", pct)}%)", 205f, legendY, bodyPaint)
            legendY += 15f
        }

        currentY += 125f

        // 2. Whole Month Daily Continuous Trajectory Line Chart (Days 1 to 31)
        canvas2.drawText("2. Whole Month Daily Financial Trajectory (Days 1 – 31)", 35f, currentY, headerPaint)
        currentY += 15f

        val chartWidth = 520f
        val lineMaxH = 85f
        val startX = 40f
        val chartY = currentY

        val bgBoxPaint = Paint().apply { color = Color.parseColor("#F8FAFC") }
        val borderPaint = Paint().apply { color = Color.parseColor("#E2E8F0"); style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas2.drawRect(startX, chartY, startX + chartWidth, chartY + lineMaxH, bgBoxPaint)
        canvas2.drawRect(startX, chartY, startX + chartWidth, chartY + lineMaxH, borderPaint)

        // Draw 5-day tick gridlines & labels on X-axis (1, 5, 10, 15, 20, 25, 30)
        val gridLinePaint = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 1f }
        val dayLabelPaint = Paint().apply { color = Color.parseColor("#64748B"); textSize = 7.5f; isAntiAlias = true }

        val daysInMonth = if (transactions.isNotEmpty()) transactions.first().date.lengthOfMonth() else 30
        for (day in listOf(1, 5, 10, 15, 20, 25, daysInMonth)) {
            val posX = startX + ((day - 1).toFloat() / (daysInMonth - 1).toFloat()) * chartWidth
            canvas2.drawLine(posX, chartY, posX, chartY + lineMaxH, gridLinePaint)
            canvas2.drawText("Day $day", posX - 10f, chartY + lineMaxH + 11f, dayLabelPaint)
        }

        if (transactions.isNotEmpty()) {
            val dateGrouped = transactions.groupBy { it.date.dayOfMonth }

            val incomePath = Path()
            val expensePath = Path()
            val savingsPath = Path()

            val maxDailyVal = maxOf(
                dateGrouped.values.sumOf { list -> list.filter { it.type == TransactionType.INCOME }.sumOf { it.amount } },
                dateGrouped.values.sumOf { list -> list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount } },
                dateGrouped.values.sumOf { list -> list.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount } },
                1.0
            )

            for (d in 1..daysInMonth) {
                val dayTxns = dateGrouped[d] ?: emptyList()
                val dInc = dayTxns.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                val dExp = dayTxns.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                val dSav = dayTxns.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }

                val posX = startX + ((d - 1).toFloat() / (daysInMonth - 1).toFloat()) * chartWidth

                val pyInc = chartY + lineMaxH - ((dInc / maxDailyVal) * (lineMaxH - 15f)).toFloat() - 5f
                val pyExp = chartY + lineMaxH - ((dExp / maxDailyVal) * (lineMaxH - 15f)).toFloat() - 5f
                val pySav = chartY + lineMaxH - ((dSav / maxDailyVal) * (lineMaxH - 15f)).toFloat() - 5f

                if (d == 1) {
                    incomePath.moveTo(posX, pyInc)
                    expensePath.moveTo(posX, pyExp)
                    savingsPath.moveTo(posX, pySav)
                } else {
                    incomePath.lineTo(posX, pyInc)
                    expensePath.lineTo(posX, pyExp)
                    savingsPath.lineTo(posX, pySav)
                }

                if (dInc > 0) canvas2.drawCircle(posX, pyInc, 2.5f, Paint().apply { color = incomeColor; isAntiAlias = true })
                if (dExp > 0) canvas2.drawCircle(posX, pyExp, 2.5f, Paint().apply { color = expenseColor; isAntiAlias = true })
                if (dSav > 0) canvas2.drawCircle(posX, pySav, 2.5f, Paint().apply { color = savingsColor; isAntiAlias = true })
            }

            canvas2.drawPath(incomePath, Paint().apply { color = incomeColor; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true })
            canvas2.drawPath(expensePath, Paint().apply { color = expenseColor; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true })
            canvas2.drawPath(savingsPath, Paint().apply { color = savingsColor; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true })
        }

        currentY += 115f

        // 3. Whole Month 5-Period Grouped Bar Chart
        canvas2.drawText("3. Whole Month 5-Period Comparison (Grouped Bar Chart)", 35f, currentY, headerPaint)
        currentY += 18f

        // Divide month into 5 period blocks: 1-6, 7-12, 13-18, 19-24, 25-31
        val periodLabels = listOf("Days 1–6", "Days 7–12", "Days 13–18", "Days 19–24", "Days 25–31")
        val periodRanges = listOf(1..6, 7..12, 13..18, 19..24, 25..31)

        val periodSums = periodRanges.map { range ->
            val periodTxns = transactions.filter { it.date.dayOfMonth in range }
            Triple(
                periodTxns.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                periodTxns.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                periodTxns.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }
            )
        }

        val maxPeriodVal = periodSums.maxOfOrNull { maxOf(it.first, it.second, it.third) }?.coerceAtLeast(1.0) ?: 1.0
        val barMaxH = 75f
        val periodWidth = chartWidth / 5f

        periodSums.forEachIndexed { idx, (inc, exp, sav) ->
            val pStartX = startX + idx * periodWidth
            val barW = 16f

            val hInc = ((inc / maxPeriodVal) * barMaxH).toFloat().coerceAtLeast(2f)
            val hExp = ((exp / maxPeriodVal) * barMaxH).toFloat().coerceAtLeast(2f)
            val hSav = ((sav / maxPeriodVal) * barMaxH).toFloat().coerceAtLeast(2f)

            // Draw 3 side-by-side bars for Income, Expense, Savings in this period
            canvas2.drawRoundRect(RectF(pStartX + 8f, currentY + barMaxH - hInc, pStartX + 8f + barW, currentY + barMaxH), 3f, 3f, Paint().apply { color = incomeColor; isAntiAlias = true })
            canvas2.drawRoundRect(RectF(pStartX + 28f, currentY + barMaxH - hExp, pStartX + 28f + barW, currentY + barMaxH), 3f, 3f, Paint().apply { color = expenseColor; isAntiAlias = true })
            canvas2.drawRoundRect(RectF(pStartX + 48f, currentY + barMaxH - hSav, pStartX + 48f + barW, currentY + barMaxH), 3f, 3f, Paint().apply { color = savingsColor; isAntiAlias = true })

            // Period Label
            canvas2.drawText(periodLabels[idx], pStartX + 16f, currentY + barMaxH + 13f, bodyPaint)
        }

        // Legend box at bottom right
        val legX = startX + 320f
        val legY = currentY + barMaxH + 28f
        canvas2.drawCircle(legX, legY - 3f, 4f, Paint().apply { color = incomeColor })
        canvas2.drawText("Income", legX + 8f, legY, bodyPaint)
        canvas2.drawCircle(legX + 60f, legY - 3f, 4f, Paint().apply { color = expenseColor })
        canvas2.drawText("Expense", legX + 68f, legY, bodyPaint)
        canvas2.drawCircle(legX + 130f, legY - 3f, 4f, Paint().apply { color = savingsColor })
        canvas2.drawText("Savings", legX + 138f, legY, bodyPaint)

        canvas2.drawText("Page $pageNumber of $pageNumber  •  Hisab Whole Month Visual Analytics Statement", (pageWidth / 2 - 110).toFloat(), 815f, subTitlePaint)
        pdfDoc.finishPage(chartPage)

        pdfDoc.writeTo(stream)
        pdfDoc.close()
    }

    // ── XLSX GENERATOR ─────────────────────────────────────────────────────────

    private fun generateXlsxReport(
        stream: OutputStream,
        transactions: List<TransactionEntity>, // Ascending sorted
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
        val headers = listOf("Date", "Type", "Subtype", "Category", "Account", "To Account", "Debit (Expense)", "Credit (Income)", "Transfer", "Notes")

        val hRow = txSheet.createRow(0)
        headers.forEachIndexed { i, h ->
            val cell = hRow.createCell(i)
            cell.setCellValue(h)
            cell.setCellStyle(headerStyle)
        }

        transactions.forEachIndexed { idx, tx ->
            val row = txSheet.createRow(idx + 1)
            row.createCell(0).setCellValue(tx.date.toString()) // YYYY-MM-DD
            row.createCell(1).setCellValue(tx.type.name)
            row.createCell(2).setCellValue(tx.subtype ?: "NORMAL")
            row.createCell(3).setCellValue(categoryMap[tx.categoryId]?.name ?: "Unknown")
            row.createCell(4).setCellValue(tx.account)
            row.createCell(5).setCellValue(tx.toAccount ?: "-")

            val isExpense = tx.type == TransactionType.EXPENSE
            val isTransfer = tx.type == TransactionType.TRANSFER
            val isIncome = tx.type == TransactionType.INCOME

            row.createCell(6).setCellValue(if (isExpense) tx.amount else 0.0)
            row.createCell(7).setCellValue(if (isIncome) tx.amount else 0.0)
            row.createCell(8).setCellValue(if (isTransfer) tx.amount else 0.0)
            row.createCell(9).setCellValue(tx.notes)
        }

        for (i in 0..9) txSheet.autoSizeColumn(i)

        workbook.write(stream)
        workbook.close()
    }

    // ── CSV GENERATOR ──────────────────────────────────────────────────────────

    private fun generateCsvReport(
        stream: OutputStream,
        transactions: List<TransactionEntity>, // Ascending sorted
        categories: List<CategoryEntity>
    ) {
        val categoryMap = categories.associateBy { it.id }
        val sb = StringBuilder()
        sb.appendLine("Date,Type,Subtype,Category,Account,ToAccount,Debit (Expense),Credit (Income),Transfer,Notes")

        for (tx in transactions) {
            val catName = categoryMap[tx.categoryId]?.name ?: "Unknown"
            val debit = if (tx.type == TransactionType.EXPENSE) tx.amount else 0.0
            val credit = if (tx.type == TransactionType.INCOME) tx.amount else 0.0
            val transfer = if (tx.type == TransactionType.TRANSFER) tx.amount else 0.0

            val line = listOf(
                tx.date.toString(), // YYYY-MM-DD
                tx.type.name,
                tx.subtype ?: "NORMAL",
                "\"${catName}\"",
                "\"${tx.account}\"",
                "\"${tx.toAccount ?: ""}\"",
                debit.toString(),
                credit.toString(),
                transfer.toString(),
                "\"${tx.notes}\""
            ).joinToString(",")
            sb.appendLine(line)
        }
        stream.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    // ── JSON BACKUP GENERATOR ──────────────────────────────────────────────────

    /**
     * Serializes the rows [generateReport] already scoped, never the whole database.
     *
     * Re-reading the ledger here was the bug: "Specific Month" + JSON wrote every transaction ever
     * recorded while the success toast reported only the month's count. Passing [transactions] on is
     * also structural — this function no longer has a way to ignore the month filter.
     */
    private suspend fun generateJsonReport(
        stream: OutputStream,
        transactions: List<TransactionEntity>,
        targetMonth: java.time.YearMonth?
    ) {
        val backupManager = AutoBackupManager(context, HisabDatabase.getDatabase(context))
        val jsonStr = backupManager.exportBackupString(
            transactions = transactions,
            includePending = targetMonth == null
        )
        stream.write(jsonStr.toByteArray(Charsets.UTF_8))
    }
}
