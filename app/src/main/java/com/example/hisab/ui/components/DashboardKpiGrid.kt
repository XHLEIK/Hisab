package com.example.hisab.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hisab.data.model.MonthlySummary
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.CurrencyFormatter

@Composable
fun DashboardKpiGrid(
    summary: MonthlySummary,
    netBalance: Double = summary.netBalance,
    savingsAmount: Double,
    savingsRate: Double,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── HERO CARD: Net Balance ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Net Balance",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Monthly Net Balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textTertiary
                            )
                        }
                    }

                    // Micro Status Badge
                    val isPositive = netBalance >= 0
                    val badgeColor = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
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

                Text(
                    text = CurrencyFormatter.format(animNetBalance.toDouble()),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }
        }

        // ── SUB-ROW: Income, Expenses, Savings ──────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MinimalMetricCard(
                title = "Income",
                amount = animIncome.toDouble(),
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                accentColor = Color(0xFF10B981),
                subText = "Earned",
                modifier = Modifier.weight(1f)
            )

            MinimalMetricCard(
                title = "Expenses",
                amount = animExpense.toDouble(),
                icon = Icons.AutoMirrored.Filled.TrendingDown,
                accentColor = Color(0xFFEF4444),
                subText = "${summary.transactionCount} txns",
                modifier = Modifier.weight(1f)
            )

            MinimalMetricCard(
                title = "Savings",
                amount = animSavings.toDouble(),
                icon = Icons.Filled.Savings,
                accentColor = Color(0xFF8B5CF6),
                subText = "${savingsRate.toInt()}% saved",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MinimalMetricCard(
    title: String,
    amount: Double,
    icon: ImageVector,
    accentColor: Color,
    subText: String,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(accentColor.copy(alpha = 0.08f))
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = CurrencyFormatter.format(amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subText,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                maxLines = 1
            )
        }
    }
}
