package com.example.hisab.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hisab.data.model.CategoryBreakdown
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.CurrencyFormatter

// Vibrant modern pastel palette with 12 distinct colors matching the reference screenshot
val ModernDonutPalette = listOf(
    Color(0xFFA3E635), // 1. Lime Green
    Color(0xFFF87171), // 2. Coral Pink/Red
    Color(0xFFFACC15), // 3. Golden Yellow
    Color(0xFFC084FC), // 4. Lavender Purple
    Color(0xFF38BDF8), // 5. Sky Blue
    Color(0xFF2DD4BF), // 6. Mint Teal
    Color(0xFFFB923C), // 7. Soft Orange
    Color(0xFFF472B6), // 8. Hot Pink
    Color(0xFF818CF8), // 9. Indigo Blue
    Color(0xFF34D399), // 10. Emerald Green
    Color(0xFFE879F9), // 11. Magenta
    Color(0xFF0EA5E9)  // 12. Ocean Cyan
)

@Composable
fun DonutChart(
    data: List<CategoryBreakdown>,
    totalAmount: Double,
    centerTitle: String = "Total Spending",
    momChangePercentage: Double = 0.0,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(data) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(1000))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Modern Rounded Donut Chart Ring ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(220.dp)) {
                val strokeWidth = 28.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val topLeft = Offset(
                    (size.width - radius * 2) / 2,
                    (size.height - radius * 2) / 2
                )
                val arcSize = Size(radius * 2, radius * 2)

                // Background track ring
                drawArc(
                    color = colors.cardBorder.copy(alpha = 0.5f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                if (data.isNotEmpty()) {
                    val gapDegrees = if (data.size > 1) 18f else 0f
                    val totalGapDegrees = data.size * gapDegrees
                    val availableDegrees = (360f - totalGapDegrees).coerceAtLeast(160f)

                    var currentAngle = -90f

                    data.forEachIndexed { index, item ->
                        val itemSweep = (item.percentage / 100f * availableDegrees * animationProgress.value).toFloat()
                        val drawSweep = itemSweep.coerceAtLeast(1f)
                        val startAngle = currentAngle + (gapDegrees / 2f)

                        val color = ModernDonutPalette[index % ModernDonutPalette.size]

                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = drawSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        currentAngle += itemSweep + gapDegrees
                    }
                }
            }

            // ── Center Content: Title, Amount & Dynamic MoM Growth Chip ──────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = centerTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = CurrencyFormatter.format(totalAmount),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Dynamic MoM growth indicator chip
                val isPositive = momChangePercentage >= 0.0
                val formattedPercentage = String.format(java.util.Locale.US, "%s%.1f%%", if (isPositive) "+" else "", momChangePercentage)
                val badgeColor = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444)
                val badgeIcon = if (isPositive) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formattedPercentage,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Modern Category Legend ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            data.take(8).forEachIndexed { index, item ->
                val color = ModernDonutPalette[index % ModernDonutPalette.size]

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.innerSurface)
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Text(
                                text = item.categoryName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = CurrencyFormatter.format(item.totalAmount),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(color.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f%%", item.percentage),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
