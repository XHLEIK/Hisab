package com.example.hisab.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hisab.data.repository.AccountRepository
import com.example.hisab.data.repository.CategoryRepository
import com.example.hisab.data.repository.TransactionRepository
import com.example.hisab.ui.charts.DonutChart
import com.example.hisab.ui.charts.SpendingHeatmap
import com.example.hisab.ui.charts.UnifiedBarChart
import com.example.hisab.ui.components.AddAccountDialog
import com.example.hisab.ui.components.EmptyState
import com.example.hisab.ui.components.ExpenseLeaderboard
import com.example.hisab.ui.components.MonthSelector
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.CurrencyFormatter

@Composable
fun AnalyticsScreen(
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository? = null
) {
    val viewModel: AnalyticsViewModel = viewModel(
        factory = AnalyticsViewModel.Factory(transactionRepository, categoryRepository, accountRepository)
    )

    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val todayExpense by viewModel.todayExpense.collectAsState()
    val totalExpenseMonthToDate by viewModel.totalExpenseMonthToDate.collectAsState()
    val primaryAccountName by viewModel.primaryAccountName.collectAsState()
    val primaryAccountBalance by viewModel.primaryAccountBalance.collectAsState()
    val secondaryAccounts by viewModel.secondaryAccounts.collectAsState()
    val selectedAccountBalance by viewModel.selectedAccountBalance.collectAsState()
    val selectedSecondaryAccountName by viewModel.selectedSecondaryAccountName.collectAsState()

    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val filteredCategoryBreakdown by viewModel.filteredCategoryBreakdown.collectAsState()
    val dailyExpenseTotals by viewModel.dailyExpenseTotals.collectAsState()
    val dailyIncomeTotals by viewModel.dailyIncomeTotals.collectAsState()

    val barChartFilter by viewModel.barChartFilter.collectAsState()
    val barChartData by viewModel.barChartData.collectAsState()
    val barChartSpecificDate by viewModel.barChartSpecificDate.collectAsState()

    val topExpenses by viewModel.topExpensesLeaderboard.collectAsState()

    var showAddAccountDialog by remember { mutableStateOf(false) }
    val colors = HisabTheme.colors

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. Header ───────────────────────────────────
        item {
            Text(
                text = "Analytics",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        // ── 2. Month Selector ───────────────────────────
        item {
            MonthSelector(
                selectedMonth = selectedMonth,
                onMonthChange = { viewModel.selectMonth(it) }
            )
        }

        // ── 3. KPI Overview Cards ───────────────────────
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnalyticsKpiCard(
                        icon = Icons.Filled.Receipt,
                        label = "Total Expense (MTD)",
                        value = CurrencyFormatter.format(totalExpenseMonthToDate),
                        subtitle = "total spent",
                        modifier = Modifier.weight(1f)
                    )
                    AnalyticsKpiCard(
                        icon = Icons.Filled.Today,
                        label = "Today Spent",
                        value = CurrencyFormatter.format(todayExpense),
                        subtitle = "so far today",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnalyticsKpiCard(
                        icon = Icons.Filled.AccountBalanceWallet,
                        label = "Main Bank",
                        value = CurrencyFormatter.format(primaryAccountBalance),
                        subtitle = primaryAccountName,
                        modifier = Modifier.weight(1f)
                    )

                    if (secondaryAccounts.isEmpty()) {
                        // Create Account Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .clickable { showAddAccountDialog = true }
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Create Account",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Secondary / Savings",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    } else {
                        // Selected Secondary Account Card
                        val currentSecAccount = selectedSecondaryAccountName ?: secondaryAccounts.first()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CreditCard,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.height(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Selected Balance",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = CurrencyFormatter.format(selectedAccountBalance),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    secondaryAccounts.forEach { acc ->
                                        val isSelected = acc == currentSecAccount
                                        Text(
                                            text = acc,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else colors.textTertiary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { viewModel.selectSecondaryAccount(acc) }
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 4. Top Expenses Leaderboard ──────────────
        item {
            AnalyticsSectionCard(title = "Top Expenses Leaderboard") {
                ExpenseLeaderboard(
                    expenses = topExpenses,
                    categories = categories,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ── 5. Category Breakdown (Pie/Donut Chart) ──
        item {
            AnalyticsSectionCard(title = "Category Breakdown") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryFilterType.values().forEach { filter ->
                        val isSelected = filter == categoryFilter
                        val label = when (filter) {
                            CategoryFilterType.ALL -> "All"
                            CategoryFilterType.INCOME -> "Income"
                            CategoryFilterType.EXPENSE -> "Expense"
                            CategoryFilterType.TRANSFERS -> "Transfers"
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setCategoryFilter(filter) },
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

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredCategoryBreakdown.isNotEmpty()) {
                    val totalAmount = filteredCategoryBreakdown.sumOf { it.totalAmount }
                    DonutChart(
                        data = filteredCategoryBreakdown,
                        totalAmount = totalAmount,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    EmptyState(
                        title = "No categories for filter",
                        subtitle = "Try changing category filter"
                    )
                }
            }
        }

        // ── 6. Income, Expense & Transfers Bar Chart ─
        item {
            AnalyticsSectionCard(title = "Income, Expense & Transfers Bar Chart") {
                UnifiedBarChart(
                    data = barChartData,
                    selectedFilter = barChartFilter,
                    onFilterSelected = { viewModel.setBarChartFilter(it) },
                    specificDate = barChartSpecificDate,
                    onDateSelected = { viewModel.setBarChartSpecificDate(it) },
                    onNavigateWeek = { viewModel.navigateWeek(it) },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ── 7. Daily Trends Line Chart ────────────────
        item {
            AnalyticsSectionCard(title = "Daily Trends Line Chart") {
                com.example.hisab.ui.charts.AnalyticsLineChart(
                    expenseData = dailyExpenseTotals,
                    incomeData = dailyIncomeTotals,
                    yearMonth = selectedMonth,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ── 8. Spending Calendar (Heatmap) ────────────
        item {
            AnalyticsSectionCard(title = "Spending Calendar") {
                SpendingHeatmap(
                    data = dailyExpenseTotals,
                    yearMonth = selectedMonth,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }

    if (showAddAccountDialog) {
        AddAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onAccountAdded = { name, typeStr ->
                viewModel.addAccount(name, typeStr)
            }
        )
    }
}

@Composable
private fun AnalyticsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val colors = HisabTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .padding(vertical = 16.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun AnalyticsKpiCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val colors = HisabTheme.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
            }
        }
    }
}
