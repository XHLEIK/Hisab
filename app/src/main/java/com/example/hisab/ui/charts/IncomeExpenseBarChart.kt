package com.example.hisab.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.ui.theme.IncomeGreen
import com.example.hisab.ui.theme.ExpenseRed
import com.example.hisab.ui.theme.PrimaryTeal
import com.example.hisab.util.CurrencyFormatter
import com.example.hisab.util.DateUtils
import java.time.YearMonth

@Composable
fun IncomeExpenseBarChart(
    data: List<Triple<YearMonth, Double, Double>>, // (month, income, expense)
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(data) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(1000))
    }

    val maxValue = data.maxOfOrNull { maxOf(it.second, it.third) } ?: 1.0

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 32.dp)
    ) {
        val chartHeight = size.height
        val chartWidth = size.width
        val groupCount = data.size
        val groupWidth = chartWidth / groupCount
        val barWidth = groupWidth * 0.25f
        val gap = groupWidth * 0.05f

        // Draw grid lines
        for (i in 0..3) {
            val y = chartHeight * i / 3
            drawLine(
                color = colors.cardBorder.copy(alpha = 0.2f),
                start = Offset(0f, y),
                end = Offset(chartWidth, y),
                strokeWidth = 1f
            )
        }

        data.forEachIndexed { index, (yearMonth, income, expense) ->
            val groupCenter = groupWidth * index + groupWidth / 2
            val savings = income - expense

            // Income bar
            val incomeHeight = (income / maxValue * chartHeight * animationProgress.value).toFloat()
            drawRoundRect(
                color = IncomeGreen,
                topLeft = Offset(groupCenter - barWidth * 1.5f - gap, chartHeight - incomeHeight),
                size = Size(barWidth, incomeHeight),
                cornerRadius = CornerRadius(4.dp.toPx())
            )

            // Expense bar
            val expenseHeight = (expense / maxValue * chartHeight * animationProgress.value).toFloat()
            drawRoundRect(
                color = ExpenseRed,
                topLeft = Offset(groupCenter - barWidth / 2, chartHeight - expenseHeight),
                size = Size(barWidth, expenseHeight),
                cornerRadius = CornerRadius(4.dp.toPx())
            )

            // Savings bar
            val savingsHeight = (savings.coerceAtLeast(0.0) / maxValue * chartHeight * animationProgress.value).toFloat()
            drawRoundRect(
                color = PrimaryTeal,
                topLeft = Offset(groupCenter + barWidth / 2 + gap, chartHeight - savingsHeight),
                size = Size(barWidth, savingsHeight),
                cornerRadius = CornerRadius(4.dp.toPx())
            )

            // Month label
            val label = DateUtils.formatShortMonth(yearMonth)
            val labelStyle = TextStyle(
                fontSize = 10.sp,
                color = colors.textTertiary,
                fontWeight = FontWeight.Normal
            )
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    groupCenter - measured.size.width / 2,
                    chartHeight + 8.dp.toPx()
                )
            )
        }
    }
}
