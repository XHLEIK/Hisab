package com.example.hisab.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hisab.data.model.DailyTotal
import com.example.hisab.ui.theme.ExpenseRed
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.ui.theme.IncomeGreen
import com.example.hisab.ui.theme.PrimaryTeal
import java.time.YearMonth

enum class LineChartDisplayMode { EXPENSE, INCOME, BOTH }

@Composable
fun AnalyticsLineChart(
    expenseData: List<DailyTotal>,
    incomeData: List<DailyTotal>,
    yearMonth: YearMonth,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val animationProgress = remember { Animatable(0f) }
    var displayMode by remember { mutableStateOf(LineChartDisplayMode.EXPENSE) }

    LaunchedEffect(expenseData, incomeData, displayMode) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(1000))
    }

    val daysInMonth = yearMonth.lengthOfMonth()
    val expenseMap = remember(expenseData) { expenseData.associate { it.date.dayOfMonth to it.totalAmount } }
    val incomeMap = remember(incomeData) { incomeData.associate { it.date.dayOfMonth to it.totalAmount } }

    val maxExpense = expenseData.maxOfOrNull { it.totalAmount } ?: 1.0
    val maxIncome = incomeData.maxOfOrNull { it.totalAmount } ?: 1.0
    val maxAmount = maxOf(maxExpense, maxIncome, 1.0)

    Column(modifier = modifier.fillMaxWidth()) {
        // Toggle Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LineChartDisplayMode.values().forEach { mode ->
                val label = when (mode) {
                    LineChartDisplayMode.EXPENSE -> "Expense Line"
                    LineChartDisplayMode.INCOME -> "Income Line"
                    LineChartDisplayMode.BOTH -> "Both (Overlay)"
                }
                val isSelected = mode == displayMode
                val chipColor = when (mode) {
                    LineChartDisplayMode.EXPENSE -> ExpenseRed
                    LineChartDisplayMode.INCOME -> IncomeGreen
                    LineChartDisplayMode.BOTH -> PrimaryTeal
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { displayMode = mode },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor.copy(alpha = 0.15f),
                        selectedLabelColor = chipColor,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = colors.textSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Line Chart Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .padding(start = 40.dp, end = 20.dp, top = 10.dp, bottom = 28.dp)
        ) {
            val chartWidth = size.width
            val chartHeight = size.height
            val stepX = chartWidth / (daysInMonth - 1).coerceAtLeast(1)

            // Grid lines
            val gridLines = 4
            for (i in 0..gridLines) {
                val y = chartHeight * i / gridLines
                drawLine(
                    color = colors.cardBorder.copy(alpha = 0.3f),
                    start = Offset(0f, y),
                    end = Offset(chartWidth, y),
                    strokeWidth = 1f
                )
            }

            fun drawSingleLine(dailyMap: Map<Int, Double>, lineColor: Color) {
                val linePath = Path()
                val fillPath = Path()
                var firstPoint = true

                for (day in 1..daysInMonth) {
                    val amount = dailyMap[day] ?: 0.0
                    val x = (day - 1) * stepX
                    val y = chartHeight - (amount / maxAmount * chartHeight * animationProgress.value).toFloat()

                    if (firstPoint) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, chartHeight)
                        fillPath.lineTo(x, y)
                        firstPoint = false
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                fillPath.lineTo(chartWidth, chartHeight)
                fillPath.close()

                // Gradient fill
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.25f * animationProgress.value),
                            lineColor.copy(alpha = 0.0f)
                        )
                    )
                )

                // Line
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    ),
                    alpha = animationProgress.value
                )

                // Point dots
                for (day in 1..daysInMonth) {
                    val amount = dailyMap[day] ?: 0.0
                    if (amount > 0) {
                        val x = (day - 1) * stepX
                        val y = chartHeight - (amount / maxAmount * chartHeight * animationProgress.value).toFloat()
                        drawCircle(
                            color = lineColor,
                            radius = 3.dp.toPx(),
                            center = Offset(x, y),
                            alpha = animationProgress.value
                        )
                    }
                }
            }

            when (displayMode) {
                LineChartDisplayMode.EXPENSE -> drawSingleLine(expenseMap, ExpenseRed)
                LineChartDisplayMode.INCOME -> drawSingleLine(incomeMap, IncomeGreen)
                LineChartDisplayMode.BOTH -> {
                    drawSingleLine(expenseMap, ExpenseRed)
                    drawSingleLine(incomeMap, IncomeGreen)
                }
            }

            // X-Axis Date Labels
            val sampleDays = listOf(1, 5, 10, 15, 20, 25, daysInMonth)
            sampleDays.forEach { day ->
                val x = (day - 1) * stepX
                val labelStyle = TextStyle(
                    fontSize = 10.sp,
                    color = colors.textTertiary,
                    fontWeight = FontWeight.Normal
                )
                val measured = textMeasurer.measure("$day", labelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(x - measured.size.width / 2, chartHeight + 6.dp.toPx())
                )
            }
        }
    }
}
