package com.example.hisab.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hisab.ui.theme.ChartColors
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.DateUtils
import java.time.YearMonth

@Composable
fun CategoryTrendChart(
    data: Map<String, List<Pair<YearMonth, Double>>>, // categoryName -> [(month, amount)]
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(data) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(1000))
    }

    if (data.isEmpty()) return

    val allMonths = data.values.firstOrNull()?.map { it.first } ?: return
    val maxValue = data.values.flatMap { series -> series.map { it.second } }.maxOrNull() ?: 1.0

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp)
        ) {
            val chartWidth = size.width
            val chartHeight = size.height
            val monthCount = allMonths.size
            val stepX = chartWidth / (monthCount - 1).coerceAtLeast(1)

            // Grid
            for (i in 0..3) {
                val y = chartHeight * i / 3
                drawLine(
                    color = colors.cardBorder.copy(alpha = 0.2f),
                    start = Offset(0f, y),
                    end = Offset(chartWidth, y),
                    strokeWidth = 1f
                )
            }

            // Draw each category line
            data.entries.forEachIndexed { catIndex, (_, series) ->
                val lineColor = ChartColors[catIndex % ChartColors.size]

                for (i in 0 until series.size - 1) {
                    val x1 = i * stepX
                    val y1 = chartHeight - (series[i].second / maxValue * chartHeight * animationProgress.value).toFloat()
                    val x2 = (i + 1) * stepX
                    val y2 = chartHeight - (series[i + 1].second / maxValue * chartHeight * animationProgress.value).toFloat()

                    drawLine(
                        color = lineColor,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        alpha = animationProgress.value
                    )
                }

                // End dot
                val lastX = (series.size - 1) * stepX
                val lastY = chartHeight - (series.last().second / maxValue * chartHeight * animationProgress.value).toFloat()
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = Offset(lastX, lastY),
                    alpha = animationProgress.value
                )
            }

            // Month labels
            allMonths.forEachIndexed { i, month ->
                val x = i * stepX
                val label = DateUtils.formatShortMonth(month)
                val labelStyle = TextStyle(
                    fontSize = 10.sp,
                    color = colors.textTertiary,
                    fontWeight = FontWeight.Normal
                )
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(x - measured.size.width / 2, chartHeight + 6.dp.toPx())
                )
            }
        }

        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            data.keys.forEachIndexed { index, name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val lineColor = ChartColors[index % ChartColors.size]
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .drawBehind { drawCircle(color = lineColor) }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}
