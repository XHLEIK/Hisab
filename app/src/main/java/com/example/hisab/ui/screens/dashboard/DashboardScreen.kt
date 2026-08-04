package com.example.hisab.ui.screens.dashboard

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType
import com.example.hisab.data.repository.AccountRepository
import com.example.hisab.data.repository.CategoryRepository
import com.example.hisab.data.repository.SpendingLimitRepository
import com.example.hisab.data.repository.TransactionRepository
import com.example.hisab.ui.components.DashboardKpiGrid
import com.example.hisab.ui.components.EmptyState
import com.example.hisab.ui.components.MonthSelector
import com.example.hisab.ui.components.QuickActionsRow
import com.example.hisab.ui.components.QuickAddSheet
import com.example.hisab.ui.components.SetLimitDialog
import com.example.hisab.ui.components.SpendingLimitWidget
import com.example.hisab.ui.components.TransactionItem
import com.example.hisab.ui.components.TransactionOptionsBottomSheet
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.DateUtils
import java.time.LocalDate

import androidx.compose.foundation.layout.statusBarsPadding

import com.example.hisab.data.repository.PendingTransactionRepository
import com.example.hisab.ui.components.PendingTransactionsCard

@Composable
fun DashboardScreen(
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository? = null,
    pendingTransactionRepository: PendingTransactionRepository? = null,
    onAddTransaction: () -> Unit = {},
    onSeeAllTransactions: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(
            transactionRepository,
            categoryRepository,
            accountRepository,
            SpendingLimitRepository(context),
            pendingTransactionRepository
        )
    )

    val pendingTransactions by viewModel.pendingTransactions.collectAsState()

    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val summary by viewModel.monthlySummary.collectAsState()
    val recentTransactions by viewModel.recentTransactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val savingsRate by viewModel.savingsRate.collectAsState()
    val savingsAmount by viewModel.totalSavingsAmount.collectAsState()
    val limitConfig by viewModel.limitConfig.collectAsState()
    val limitStatus by viewModel.spendingLimitStatus.collectAsState()
    val safeDailyPace by viewModel.safeDailyPace.collectAsState()
    val topCategories by viewModel.topCategoryBreakdown.collectAsState()
    val accountBalances by viewModel.accountBalances.collectAsState()
    val primaryAndSecondaryBalance by viewModel.primaryAndSecondaryBalance.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var addSheetInitialType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var showSetLimitDialog by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var selectedTransactionForOptions by remember { mutableStateOf<TransactionEntity?>(null) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    val colors = HisabTheme.colors
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val groupedTransactions = remember(recentTransactions) { recentTransactions.groupBy { it.date } }

    val today = LocalDate.now()
    val isEndOfMonth = today.dayOfMonth >= today.lengthOfMonth() - 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Header ───────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Hisab",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Smart Financial Overview",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary
                        )
                    }
                }
            }

            // ── End-of-Month Backup Reminder Banner ──────
            if (isEndOfMonth) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "End of Month Backup",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "End of month! Go to Settings to export your CSV backup.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // ── 1. Master Hero Card (Month Selector + Net Balance + 3-Column Metrics) ──
            item {
                DashboardKpiGrid(
                    selectedMonth = selectedMonth,
                    onMonthChange = { viewModel.selectMonth(it) },
                    summary = summary,
                    netBalance = primaryAndSecondaryBalance,
                    accountCount = accounts.size,
                    savingsAmount = savingsAmount
                )
            }

            // ── 1b. Pending SMS Transactions Banner Card ──
            item {
                PendingTransactionsCard(
                    pendingTransactions = pendingTransactions,
                    onApprove = { pending, category -> viewModel.approvePendingTransaction(pending, category) },
                    onDismiss = { pending -> viewModel.dismissPendingTransaction(pending) }
                )
            }

            // ── 2. Quick Actions Section (+ Income, - Expense, ⇄ Transfer) ──
            item {
                QuickActionsRow(
                    onAddType = { selectedType ->
                        addSheetInitialType = selectedType
                        showAddSheet = true
                    }
                )
            }

            // ── 3. Accounts Overview Section (LazyRow + Scroll Dots) ──
            item {
                com.example.hisab.ui.components.AccountsOverviewWidget(
                    accountBalances = accountBalances,
                    onAddAccount = { showAddAccountDialog = true }
                )
            }

            // ── 4. Monthly Budget Progress Card ──────────
            item {
                SpendingLimitWidget(
                    status = limitStatus,
                    safeDailyPace = safeDailyPace,
                    onOpenSetLimit = { showSetLimitDialog = true }
                )
            }

            // ── 5. Top Expense Categories ───────────────
            item {
                com.example.hisab.ui.components.TopCategorySpendWidget(
                    topCategories = topCategories,
                    totalExpense = summary.totalExpense
                )
            }

            // ── Recent Transactions Header ───────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    if (recentTransactions.isNotEmpty()) {
                        Text(
                            text = "See All →",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onSeeAllTransactions() }
                        )
                    }
                }
            }

            // ── Transaction List or Empty State ──────────
            if (recentTransactions.isEmpty()) {
                item {
                    EmptyState()
                }
            } else {
                groupedTransactions.forEach { (date, transactions) ->
                    item(key = "dash_group_$date") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = DateUtils.formatRelative(date),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.surfaceCard)
                                    .border(0.5.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                            ) {
                                Column {
                                    transactions.forEachIndexed { index, transaction ->
                                        val category = categoryMap[transaction.categoryId]
                                        TransactionItem(
                                            transaction = transaction,
                                            categoryName = category?.name ?: "Unknown",
                                            categoryColor = category?.colorHex ?: "#607D8B",
                                            categoryIcon = category?.iconName ?: "MoreHoriz",
                                            onClick = { selectedTransactionForOptions = transaction }
                                        )
                                        if (index < transactions.size - 1) {
                                            androidx.compose.material3.HorizontalDivider(
                                                color = colors.cardBorder.copy(alpha = 0.5f),
                                                thickness = 0.5.dp,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(115.dp)) }
        }
    }

    // Options Bottom Sheet
    if (selectedTransactionForOptions != null) {
        TransactionOptionsBottomSheet(
            transaction = selectedTransactionForOptions!!,
            onDismiss = { selectedTransactionForOptions = null },
            onEdit = { tx ->
                editingTransaction = tx
            },
            onDelete = { tx ->
                viewModel.deleteTransaction(tx)
            }
        )
    }

    // Edit Transaction Sheet
    if (editingTransaction != null) {
        QuickAddSheet(
            categories = categories,
            accounts = accounts,
            onDismiss = { editingTransaction = null },
            editTransaction = editingTransaction,
            onSave = { transaction ->
                viewModel.updateTransaction(transaction)
                editingTransaction = null
            },
            onAddAccount = { name, type ->
                viewModel.addAccount(name, type)
            }
        )
    }

    // Quick Add Sheet
    if (showAddSheet) {
        QuickAddSheet(
            categories = categories,
            accounts = accounts,
            initialType = addSheetInitialType,
            onDismiss = { showAddSheet = false },
            onSave = { transaction ->
                viewModel.addTransaction(transaction)
                showAddSheet = false
            },
            onAddAccount = { name, type ->
                viewModel.addAccount(name, type)
            }
        )
    }

    // Set Limit Modal Dialog
    if (showSetLimitDialog) {
        SetLimitDialog(
            currentConfig = limitConfig,
            onDismiss = { showSetLimitDialog = false },
            onSaveLimit = { type, amount, date ->
                viewModel.saveSpendingLimit(type, amount, date)
            },
            onDisableLimit = {
                viewModel.disableSpendingLimit()
            }
        )
    }

    // Add Account Dialog
    if (showAddAccountDialog) {
        com.example.hisab.ui.components.AddAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onAccountAdded = { name, type, bankCode, last4 ->
                viewModel.addAccount(name, type, bankCode, last4)
            }
        )
    }
}
