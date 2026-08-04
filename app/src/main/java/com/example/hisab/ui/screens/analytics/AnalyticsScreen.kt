package com.example.hisab.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hisab.data.repository.AccountRepository
import com.example.hisab.data.repository.CategoryRepository
import com.example.hisab.data.repository.TransactionRepository
import com.example.hisab.ui.charts.AnalyticsLineChart
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
    val totalExpenseMonthToDate by viewModel.totalExpenseMonthToDate.collectAsState()
    val todayExpense by viewModel.todayExpense.collectAsState()
    val savingsAccountBalance by viewModel.savingsAccountBalance.collectAsState()
    val savingsAccountName by viewModel.savingsAccountName.collectAsState()

    val primaryAccountName by viewModel.primaryAccountName.collectAsState()
    val primaryAccountBalance by viewModel.primaryAccountBalance.collectAsState()
    val secondaryAccounts by viewModel.secondaryAccounts.collectAsState()
    val selectedSecondaryAccountName by viewModel.selectedSecondaryAccountName.collectAsState()
    val selectedAccountBalance by viewModel.selectedAccountBalance.collectAsState()

    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val filteredCategoryBreakdown by viewModel.filteredCategoryBreakdown.collectAsState()
    val categoryBreakdownMomChange by viewModel.categoryBreakdownMomChange.collectAsState()

    val dailyExpenseTotals by viewModel.dailyExpenseTotals.collectAsState()
    val dailyIncomeTotals by viewModel.dailyIncomeTotals.collectAsState()

    val barChartFilter by viewModel.barChartFilter.collectAsState()
    val barChartData by viewModel.barChartData.collectAsState()
    val barChartSpecificDate by viewModel.barChartSpecificDate.collectAsState()

    val topExpenses by viewModel.topExpensesLeaderboard.collectAsState()

    var activeTrendTab by remember { mutableIntStateOf(0) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    val colors = HisabTheme.colors

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. Header & Month Selector ───────────────────
        item {
            Column {
                Text(
                    text = "Analytics",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                MonthSelector(
                    selectedMonth = selectedMonth,
                    onMonthChange = { viewModel.selectMonth(it) }
                )
            }
        }

        // ── 2. Unified Master Hero Card (2x2 Grid) ────────
        item {
            AnalyticsMasterHeroCard(
                totalExpenseMonth = totalExpenseMonthToDate,
                todayExpense = todayExpense,
                savingsAccountBalance = savingsAccountBalance,
                savingsAccountName = savingsAccountName,
                primaryAccountName = primaryAccountName,
                primaryAccountBalance = primaryAccountBalance,
                secondaryAccounts = secondaryAccounts,
                selectedSecondaryAccountName = selectedSecondaryAccountName,
                selectedSecondaryBalance = selectedAccountBalance,
                onSelectSecondaryAccount = { viewModel.selectSecondaryAccount(it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // ── 3. Category Breakdown (Donut Chart) ─────────
        item {
            AnalyticsSectionCard(title = "Category Breakdown") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryFilterType.values().forEach { filter ->
                        val isSelected = (filter == categoryFilter)
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
                                containerColor = colors.innerSurface,
                                labelColor = colors.textSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = colors.cardBorder,
                                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                enabled = true,
                                selected = isSelected
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredCategoryBreakdown.isNotEmpty()) {
                    val totalAmount = filteredCategoryBreakdown.sumOf { it.totalAmount }
                    val chartTitle = when (categoryFilter) {
                        CategoryFilterType.ALL -> "Total Volume"
                        CategoryFilterType.INCOME -> "Total Income"
                        CategoryFilterType.EXPENSE -> "Total Expenses"
                        CategoryFilterType.TRANSFERS -> "Total Savings"
                    }
                    DonutChart(
                        data = filteredCategoryBreakdown,
                        totalAmount = totalAmount,
                        centerTitle = chartTitle,
                        momChangePercentage = categoryBreakdownMomChange,
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

        // ── 4. Top Expenses Leaderboard ─────────────────────
        item {
            AnalyticsSectionCard(title = "Top Expenses Leaderboard") {
                ExpenseLeaderboard(
                    expenses = topExpenses,
                    categories = categories,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ── 5. Unified Spending Trends Card (3-Tab Switcher) ─
        item {
            AnalyticsSectionCard(title = "Spending Trends") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Segmented Control Tabs Switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.innerSurface)
                            .border(0.5.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf(
                            Triple(0, Icons.AutoMirrored.Filled.ShowChart, "Line Trend"),
                            Triple(1, Icons.Filled.BarChart, "Monthly Bar"),
                            Triple(2, Icons.Filled.CalendarMonth, "Heatmap")
                        )

                        tabs.forEach { (index, icon, label) ->
                            val isSelected = (activeTrendTab == index)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { activeTrendTab = index },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else colors.textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tab Views
                    when (activeTrendTab) {
                        0 -> {
                            AnalyticsLineChart(
                                expenseData = dailyExpenseTotals,
                                incomeData = dailyIncomeTotals,
                                yearMonth = selectedMonth,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        1 -> {
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
                        2 -> {
                            SpendingHeatmap(
                                data = dailyExpenseTotals,
                                yearMonth = selectedMonth,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(115.dp)) }
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
private fun AnalyticsMasterHeroCard(
    totalExpenseMonth: Double,
    todayExpense: Double,
    savingsAccountBalance: Double,
    savingsAccountName: String,
    primaryAccountName: String,
    primaryAccountBalance: Double,
    secondaryAccounts: List<String>,
    selectedSecondaryAccountName: String?,
    selectedSecondaryBalance: Double,
    onSelectSecondaryAccount: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors
    var isPrimarySelected by remember { mutableStateOf(true) }

    val activeBalance = if (isPrimarySelected) primaryAccountBalance else selectedSecondaryBalance
    val activeAccountName = if (isPrimarySelected) primaryAccountName else (selectedSecondaryAccountName ?: secondaryAccounts.firstOrNull() ?: "Secondary")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceCard)
            .border(0.5.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Row 1: Total Expenses (Month) & Today's Expense
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: Total Expenses (Month)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.innerSurface)
                        .border(0.5.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF5252).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Receipt,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Month Expense",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = CurrencyFormatter.format(totalExpenseMonth),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Total spent this month",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.textTertiary,
                        maxLines = 1
                    )
                }

                // Section 2: Today's Expense
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.innerSurface)
                        .border(0.5.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Today,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Today's Expense",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = CurrencyFormatter.format(todayExpense),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Spent today",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.textTertiary,
                        maxLines = 1
                    )
                }
            }

            // Row 2: Savings Account Balance & Account Balance with Primary/Secondary Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 3: Savings Account Balance
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.innerSurface)
                        .border(0.5.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00E676).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Savings,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Savings Account",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = CurrencyFormatter.format(savingsAccountBalance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676)
                    )
                    Text(
                        text = savingsAccountName,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Section 4: Balance with Primary / Secondary Toggle Pill
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.innerSurface)
                        .border(0.5.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF64B5F6).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = Color(0xFF64B5F6),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Balance",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }

                        // Toggle Pill (Primary / Secondary)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.innerSurface)
                                .border(0.5.dp, colors.cardBorder, RoundedCornerShape(8.dp))
                                .padding(2.dp)
                        ) {
                            Text(
                                text = "Pri",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = if (isPrimarySelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isPrimarySelected) MaterialTheme.colorScheme.primary else colors.textTertiary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isPrimarySelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { isPrimarySelected = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                            Text(
                                text = "Sec",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = if (!isPrimarySelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (!isPrimarySelected) MaterialTheme.colorScheme.primary else colors.textTertiary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (!isPrimarySelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { isPrimarySelected = false }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = CurrencyFormatter.format(activeBalance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )

                    Text(
                        text = activeAccountName,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
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
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCard)
            .border(0.5.dp, colors.cardBorder, RoundedCornerShape(16.dp))
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
