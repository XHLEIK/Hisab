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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hisab.ui.screens.analytics.BarChartDataPoint
import com.example.hisab.ui.screens.analytics.BarChartTimeFilter
import com.example.hisab.ui.theme.ExpenseRed
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.ui.theme.IncomeGreen
import com.example.hisab.ui.theme.PrimaryTeal
import com.example.hisab.util.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedBarChart(
    data: List<BarChartDataPoint>,
    selectedFilter: BarChartTimeFilter,
    onFilterSelected: (BarChartTimeFilter) -> Unit,
    specificDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onNavigateWeek: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val animationProgress = remember { Animatable(0f) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(data, selectedFilter) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(800))
    }

    val transferColor = Color(0xFF3A86FF)

    Column(modifier = modifier.fillMaxWidth()) {
        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BarChartTimeFilter.values().forEach { filter ->
                val label = when (filter) {
                    BarChartTimeFilter.TODAY -> "Today"
                    BarChartTimeFilter.SPECIFIC_DATE -> "Specific Date"
                    BarChartTimeFilter.WEEKLY -> "Weekly"
                    BarChartTimeFilter.FIFTEEN_DAYS -> "15 Days"
                    BarChartTimeFilter.MONTHLY -> "Monthly"
                }
                val isSelected = filter == selectedFilter
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = colors.textSecondary
                    )
                )
            }
        }

        if (selectedFilter == BarChartTimeFilter.WEEKLY && onNavigateWeek != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onNavigateWeek(-1) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Week",
                            modifier = Modifier.height(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Previous Week", style = MaterialTheme.typography.labelMedium)
                    }
                }
                TextButton(onClick = { onNavigateWeek(1) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Next Week", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Week",
                            modifier = Modifier.height(16.dp)
                        )
                    }
                }
            }
        }

        if (selectedFilter == BarChartTimeFilter.SPECIFIC_DATE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.height(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Date: ${DateUtils.formatShort(specificDate)}")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Chart Canvas
        val hasValues = remember(data) { data.any { it.income > 0 || it.expense > 0 || it.transfer > 0 } }

        if (data.isEmpty() || !hasValues) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No transactions for selected period",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textTertiary
                )
            }
        } else {
            val maxVal = remember(data) {
                val highest = data.maxOfOrNull { maxOf(it.income, it.expense, it.transfer) } ?: 100.0
                if (highest <= 0) 100.0 else highest
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                val chartWidth = (data.size * 65.dp.value).coerceAtLeast(320f).dp

                Canvas(
                    modifier = Modifier
                        .width(chartWidth)
                        .height(200.dp)
                ) {
                    val labelHeight = 24.dp.toPx()
                    val availableHeight = size.height - labelHeight - 16.dp.toPx()
                    val groupWidth = size.width / data.size
                    val barWidth = (groupWidth * 0.22f).coerceAtMost(16.dp.toPx())
                    val barSpacing = 4.dp.toPx()

                    val labelStyle = TextStyle(
                        fontSize = 9.sp,
                        color = colors.textTertiary,
                        fontWeight = FontWeight.Medium
                    )

                    data.forEachIndexed { i, point ->
                        val centerX = i * groupWidth + groupWidth / 2
                        val incomeH = (point.income / maxVal * availableHeight * animationProgress.value).toFloat()
                        val expenseH = (point.expense / maxVal * availableHeight * animationProgress.value).toFloat()
                        val transferH = (point.transfer / maxVal * availableHeight * animationProgress.value).toFloat()

                        val baselineY = size.height - labelHeight

                        // Income Bar (Green)
                        val incomeX = centerX - barWidth * 1.5f - barSpacing
                        if (incomeH > 0) {
                            drawRoundRect(
                                color = IncomeGreen,
                                topLeft = Offset(incomeX, baselineY - incomeH),
                                size = Size(barWidth, incomeH),
                                cornerRadius = CornerRadius(3.dp.toPx())
                            )
                        }

                        // Expense Bar (Red)
                        val expenseX = centerX - barWidth * 0.5f
                        if (expenseH > 0) {
                            drawRoundRect(
                                color = ExpenseRed,
                                topLeft = Offset(expenseX, baselineY - expenseH),
                                size = Size(barWidth, expenseH),
                                cornerRadius = CornerRadius(3.dp.toPx())
                            )
                        }

                        // Transfer Bar (Blue/Primary)
                        val transferX = centerX + barWidth * 0.5f + barSpacing
                        if (transferH > 0) {
                            drawRoundRect(
                                color = transferColor,
                                topLeft = Offset(transferX, baselineY - transferH),
                                size = Size(barWidth, transferH),
                                cornerRadius = CornerRadius(3.dp.toPx())
                            )
                        }

                        // Label under group
                        val measuredText = textMeasurer.measure(point.label, labelStyle)
                        drawText(
                            textLayoutResult = measuredText,
                            topLeft = Offset(
                                centerX - measuredText.size.width / 2,
                                baselineY + 4.dp.toPx()
                            )
                        )
                    }

                    // Baseline
                    drawLine(
                        color = colors.cardBorder,
                        start = Offset(0f, size.height - labelHeight),
                        end = Offset(size.width, size.height - labelHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = IncomeGreen, label = "Income")
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem(color = ExpenseRed, label = "Expense")
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem(color = transferColor, label = "Transfer")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = specificDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selected = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            onDateSelected(selected)
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(10.dp)
                .padding(2.dp)
                .fillMaxWidth()
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(color = color)
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = HisabTheme.colors.textSecondary
        )
    }
}
