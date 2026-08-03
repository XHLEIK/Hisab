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
import com.example.hisab.ui.theme.PrimaryTeal
import com.example.hisab.util.CurrencyFormatter
import com.example.hisab.util.DateUtils

@Composable
fun WeekdayBarChart(
    data: Map<Int, Double>, // dayOfWeek (1=Mon) -> average amount
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(data) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(800))
    }

    val maxValue = data.values.maxOrNull() ?: 1.0

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp)
    ) {
        val chartHeight = size.height
        val chartWidth = size.width
        val barWidth = chartWidth / 7 * 0.6f
        val barGap = chartWidth / 7

        // Draw average line
        val avgValue = data.values.average()
        val avgY = chartHeight - (avgValue / maxValue * chartHeight).toFloat()
        drawLine(
            color = colors.textTertiary.copy(alpha = 0.3f),
            start = Offset(0f, avgY),
            end = Offset(chartWidth, avgY),
            strokeWidth = 1.5f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(8f, 4f)
            )
        )

        for (dayOfWeek in 1..7) {
            val value = data[dayOfWeek] ?: 0.0
            val barHeight = (value / maxValue * chartHeight * animationProgress.value).toFloat()
            val x = (dayOfWeek - 1) * barGap + (barGap - barWidth) / 2

            // Bar opacity based on value
            val alpha = if (value > 0) 0.5f + (value / maxValue * 0.5).toFloat() else 0.2f

            drawRoundRect(
                color = PrimaryTeal.copy(alpha = alpha * animationProgress.value),
                topLeft = Offset(x, chartHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(6.dp.toPx())
            )

            // Weekday label
            val label = DateUtils.weekdayShortName(dayOfWeek)
            val labelStyle = TextStyle(
                fontSize = 10.sp,
                color = colors.textTertiary,
                fontWeight = FontWeight.Normal
            )
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x + barWidth / 2 - measured.size.width / 2,
                    chartHeight + 6.dp.toPx()
                )
            )
        }
    }
}
