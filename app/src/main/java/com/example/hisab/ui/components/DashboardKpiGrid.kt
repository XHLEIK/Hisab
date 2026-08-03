package com.example.hisab.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hisab.data.model.MonthlySummary
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.CurrencyFormatter
import com.example.hisab.util.DateUtils
import java.time.YearMonth

@Composable
fun DashboardKpiGrid(
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    summary: MonthlySummary,
    netBalance: Double = summary.netBalance,
    accountCount: Int = 3,
    savingsAmount: Double,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors
    var showPicker by remember { mutableStateOf(false) }

    val animNetBalance by animateFloatAsState(
        targetValue = netBalance.toFloat(),
        animationSpec = tween(700),
        label = "netBalanceAnim"
    )
    val animIncome by animateFloatAsState(
        targetValue = summary.totalIncome.toFloat(),
        animationSpec = tween(700),
        label = "incomeAnim"
    )
    val animExpense by animateFloatAsState(
        targetValue = summary.totalExpense.toFloat(),
        animationSpec = tween(700),
        label = "expenseAnim"
    )
    val animSavings by animateFloatAsState(
        targetValue = savingsAmount.toFloat(),
        animationSpec = tween(700),
        label = "savingsAnim"
    )

    val isCurrentMonth = DateUtils.isCurrentMonth(selectedMonth)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column {
            // ── Top Row: Month Selector + Status Badge ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onMonthChange(selectedMonth.minusMonths(1)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "Previous month",
                            tint = colors.textSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                            .clickable { showPicker = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = DateUtils.formatMonthYear(selectedMonth),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { onMonthChange(selectedMonth.plusMonths(1)) },
                        modifier = Modifier.size(32.dp),
                        enabled = !isCurrentMonth
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Next month",
                            tint = if (!isCurrentMonth) colors.textSecondary else colors.textTertiary.copy(alpha = 0.3f)
                        )
                    }
                }

                // Micro Status Badge
                val isPositive = netBalance >= 0
                val badgeColor = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isPositive) "On Track" else "Deficit",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Middle Section: Net Balance Header, Amount & Subtitle ───────────
            Text(
                text = "Net Balance",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = CurrencyFormatter.format(animNetBalance.toDouble()),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Text(
                text = "Across $accountCount Accounts",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // ── Bottom Row: 3 Equal Columns (INCOME, EXPENSES, SAVINGS) ─────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // INCOME
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "INCOME",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "+${CurrencyFormatter.format(animIncome.toDouble())}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }

                // Vertical Divider 1
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .width(1.dp)
                        .background(colors.cardBorder.copy(alpha = 0.6f))
                )

                // EXPENSES
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "EXPENSES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "-${CurrencyFormatter.format(animExpense.toDouble())}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                }

                // Vertical Divider 2
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .width(1.dp)
                        .background(colors.cardBorder.copy(alpha = 0.6f))
                )

                // SAVINGS
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SAVINGS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyFormatter.format(animSavings.toDouble()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B5CF6)
                    )
                }
            }
        }
    }

    if (showPicker) {
        MonthYearPickerDialog(
            initialMonth = selectedMonth,
            onDismiss = { showPicker = false },
            onMonthYearSelected = { newMonth ->
                onMonthChange(newMonth)
                showPicker = false
            }
        )
    }
}
