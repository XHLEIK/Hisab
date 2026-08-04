package com.example.hisab.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
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
    val isDark = isSystemInDarkTheme()

    // Metric color palette (Soft theme-adaptive emerald, rose, indigo)
    val incomeColor = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    val expenseColor = if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
    val savingsColor = if (isDark) Color(0xFF64B5F6) else Color(0xFF1565C0)

    val cardBgColor = if (isDark) colors.surfaceCard else Color(0xFFFFFFFF)
    val heroGradient = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                colors.surfaceCard,
                colors.surfaceCard,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.03f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0xFFF8FAFC)
            )
        )
    }

    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        color = cardBgColor,
        shadowElevation = if (isDark) 0.dp else 2.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.cardBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroGradient)
                .padding(18.dp)
        ) {
            Column {
                // ── Top Row: Ghost Pill Month Selector & Subtle Status Badge ─────
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // Centered Ghost Pill Month Selector
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

                        // Ghost Pill Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                .border(1.dp, colors.cardBorder.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                                .clickable { showPicker = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = DateUtils.formatMonthYear(selectedMonth),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
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

                    // Soft Status Badge (Top Right)
                    val isPositive = netBalance >= 0
                    val badgeColor = if (isPositive) incomeColor else expenseColor
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor.copy(alpha = 0.10f))
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
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountBalance,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Primary + Secondary Accounts",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
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
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "+${CurrencyFormatter.format(animIncome.toDouble())}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = incomeColor
                        )
                    }

                    // Vertical Divider 1
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .width(0.5.dp)
                            .background(colors.cardBorder.copy(alpha = 0.4f))
                    )

                    // EXPENSES
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "EXPENSES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "-${CurrencyFormatter.format(animExpense.toDouble())}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = expenseColor
                        )
                    }

                    // Vertical Divider 2
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .width(0.5.dp)
                            .background(colors.cardBorder.copy(alpha = 0.4f))
                    )

                    // SAVINGS
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SAVINGS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = CurrencyFormatter.format(animSavings.toDouble()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = savingsColor
                        )
                    }
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
