package com.example.hisab.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hisab.data.model.DailyTotal
import com.example.hisab.ui.theme.HisabTheme
import java.time.YearMonth

@Composable
fun SpendingHeatmap(
    data: List<DailyTotal>,
    yearMonth: YearMonth,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors
    val textMeasurer = rememberTextMeasurer()

    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value // 1=Mon, 7=Sun
    val dailyMap = remember(data) { data.associate { it.date.dayOfMonth to it.totalAmount } }
    val maxAmount = remember(data) { data.maxOfOrNull { it.totalAmount } ?: 1.0 }

    fun lerpColor(a: Color, b: Color, t: Float): Color {
        return Color(
            red = a.red + (b.red - a.red) * t,
            green = a.green + (b.green - a.green) * t,
            blue = a.blue + (b.blue - a.blue) * t,
            alpha = a.alpha + (b.alpha - a.alpha) * t
        )
    }

    // Continuous color gradient: light green (lowest expense) → yellow → orange → dark red (highest)
    // Each day gets a unique shade based on its position relative to the max.
    fun expenseColor(intensity: Float): Color {
        val stops = listOf(
            0.0f to Color(0xFFC8E6C9),
            0.25f to Color(0xFFA5D6A7),
            0.50f to Color(0xFFFFF176),
            0.75f to Color(0xFFFFCC80),
            1.0f to Color(0xFFEF5350)
        )
        val lower = stops.last { it.first <= intensity }
        val upper = stops.first { it.first >= intensity }
        if (lower == upper) return lower.second
        val range = upper.first - lower.first
        val t = if (range > 0f) (intensity - lower.first) / range else 0f
        return lerpColor(lower.second, upper.second, t)
    }

    val totalCells = firstDayOfWeek - 1 + daysInMonth
    val weeks = (totalCells + 6) / 7
    val weekdayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Weekday Headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekdayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Grid Canvas inside BoxWithConstraints for dynamic height & width matching
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cellPaddingDp = 4.dp
            val availableWidthDp = maxWidth
            val cellSizeDp = (availableWidthDp - (cellPaddingDp * 6)) / 7
            val rowHeightDp = cellSizeDp + cellPaddingDp
            val totalGridHeightDp = rowHeightDp * weeks

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalGridHeightDp)
            ) {
                val cellPaddingPx = cellPaddingDp.toPx()
                val cellSizePx = cellSizeDp.toPx()

                val activeDayStyle = TextStyle(
                    fontSize = 11.sp,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                val inactiveDayStyle = TextStyle(
                    fontSize = 11.sp,
                    color = colors.textTertiary.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Normal
                )

                for (week in 0 until weeks) {
                    for (dayOfWeek in 0..6) {
                        val cellIndex = week * 7 + dayOfWeek
                        val day = cellIndex - (firstDayOfWeek - 2)

                        if (day in 1..daysInMonth) {
                            val amount = dailyMap[day] ?: 0.0
                            // Relative grading: 0.0 = no expense, 1.0 = highest expense day
                            val intensity = if (amount <= 0 || maxAmount <= 0) 0f
                                else (amount / maxAmount).toFloat()

                            val cellColor = when {
                                amount <= 0 -> colors.heatmapZero
                                else -> expenseColor(intensity)
                            }

                            val x = dayOfWeek * (cellSizePx + cellPaddingPx)
                            val y = week * (cellSizePx + cellPaddingPx)

                            drawRoundRect(
                                color = cellColor,
                                topLeft = Offset(x, y),
                                size = Size(cellSizePx, cellSizePx),
                                cornerRadius = CornerRadius(8.dp.toPx())
                            )

                            val style = if (amount > 0) activeDayStyle else inactiveDayStyle
                            val measured = textMeasurer.measure("$day", style)
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(
                                    x + (cellSizePx - measured.size.width) / 2,
                                    y + (cellSizePx - measured.size.height) / 2
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Less ... More Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Less",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )
            Spacer(modifier = Modifier.width(8.dp))

            val legendColors = listOf(
                colors.heatmapZero,
                expenseColor(0.1f),
                expenseColor(0.3f),
                expenseColor(0.5f),
                expenseColor(0.7f),
                expenseColor(0.9f),
                expenseColor(1.0f)
            )
            Canvas(modifier = Modifier.width(100.dp).height(14.dp)) {
                val boxWidth = size.width / legendColors.size
                legendColors.forEachIndexed { i, color ->
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(i * boxWidth + 2, 0f),
                        size = Size(boxWidth - 4, size.height),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "More",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )
        }
    }
}
