package com.example.hisab.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.ui.theme.PrimaryTeal
import java.time.YearMonth

@Composable
fun DailyLineChart(
    data: List<DailyTotal>,
    yearMonth: YearMonth,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(data) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(1200))
    }

    val daysInMonth = yearMonth.lengthOfMonth()
    val maxAmount = data.maxOfOrNull { it.totalAmount } ?: 1.0

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(start = 40.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
    ) {
        val chartWidth = size.width
        val chartHeight = size.height
        val stepX = chartWidth / (daysInMonth - 1).coerceAtLeast(1)

        // Create data map for quick lookup
        val dailyMap = data.associate { it.date.dayOfMonth to it.totalAmount }

        // Draw grid lines
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

        // Build path for the line
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

        // Close fill path
        fillPath.lineTo(chartWidth, chartHeight)
        fillPath.close()

        // Draw gradient fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    PrimaryTeal.copy(alpha = 0.3f * animationProgress.value),
                    PrimaryTeal.copy(alpha = 0.0f)
                )
            )
        )

        // Draw line
        drawPath(
            path = linePath,
            color = PrimaryTeal,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            ),
            alpha = animationProgress.value
        )

        // Draw dots on data points
        for (day in 1..daysInMonth) {
            val amount = dailyMap[day] ?: 0.0
            if (amount > 0) {
                val x = (day - 1) * stepX
                val y = chartHeight - (amount / maxAmount * chartHeight * animationProgress.value).toFloat()
                drawCircle(
                    color = PrimaryTeal,
                    radius = 3.dp.toPx(),
                    center = Offset(x, y),
                    alpha = animationProgress.value
                )
            }
        }

        // Draw X-axis labels (every 5 days)
        for (day in listOf(1, 5, 10, 15, 20, 25, daysInMonth)) {
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
