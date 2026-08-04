package com.example.hisab.ui.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType
import com.example.hisab.data.repository.AccountRepository
import com.example.hisab.data.repository.CategoryRepository
import com.example.hisab.data.repository.TransactionRepository
import com.example.hisab.ui.components.EmptyState
import com.example.hisab.ui.components.FilterBar
import com.example.hisab.ui.components.MonthSelector
import com.example.hisab.ui.components.QuickAddSheet
import com.example.hisab.ui.components.TransactionItem
import com.example.hisab.ui.components.TransactionOptionsBottomSheet
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.CurrencyFormatter
import com.example.hisab.util.DateUtils

import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun HistoryScreen(
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository? = null
) {
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.Factory(transactionRepository, categoryRepository, accountRepository)
    )

    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var selectedTransactionForOptions by remember { mutableStateOf<TransactionEntity?>(null) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    val colors = HisabTheme.colors
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    // Group transactions by date (descending order)
    val grouped = remember(transactions) {
        transactions.groupBy { it.date }
            .toSortedMap(compareByDescending { it })
    }

    val filteredTotal = transactions.sumOf { tx ->
        when (tx.type) {
            TransactionType.EXPENSE -> -tx.amount
            TransactionType.INCOME -> tx.amount
            TransactionType.TRANSFER -> 0.0
        }
    }

    // Calculate daily sums for each group once when grouped changes
    val dailySums = remember(grouped) {
        grouped.mapValues { (_, txns) ->
            txns.sumOf { tx ->
                when (tx.type) {
                    TransactionType.EXPENSE -> -tx.amount
                    TransactionType.INCOME -> tx.amount
                    TransactionType.TRANSFER -> 0.0
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Header ───────────────────────────────────
        Text(
            text = "History",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        )

        // ── Month Selector ───────────────────────────
        MonthSelector(
            selectedMonth = selectedMonth,
            onMonthChange = { viewModel.selectMonth(it) }
        )

        // ── Search Bar ───────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = {
                Text(
                    text = "Search category, notes, or amount...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textTertiary
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = colors.textTertiary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear",
                            tint = colors.textSecondary
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = colors.cardBorder,
                focusedContainerColor = colors.surfaceCard,
                unfocusedContainerColor = colors.surfaceCard
            )
        )

        // ── Filter Bar ───────────────────────────────
        FilterBar(
            selectedType = selectedType,
            onTypeSelected = { viewModel.selectType(it) },
            selectedAccount = selectedAccount,
            accounts = accounts,
            onAccountSelected = { viewModel.selectAccount(it) }
        )

        // ── Summary Footer ──────────────────────────
        AnimatedVisibility(visible = transactions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${transactions.size} transactions",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textTertiary
                )
                Text(
                    text = "Net: ${if (filteredTotal >= 0) "+" else ""}${CurrencyFormatter.format(filteredTotal)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (filteredTotal >= 0) Color(0xFF00E676) else Color(0xFFFF5252)
                )
            }
        }

        // ── Transaction List Grouped by Day ──────────
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .animateContentSize()
        ) {
            if (transactions.isEmpty()) {
                item {
                    EmptyState(
                        title = "No transactions found",
                        subtitle = if (searchQuery.isNotEmpty()) "Try a different search term"
                        else "No transactions for this month"
                    )
                }
            } else {
                grouped.forEach { (date, txns) ->
                    val dailySum = dailySums[date] ?: 0.0

                    item(key = "day_card_$date") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            // Daily Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = DateUtils.formatRelative(date),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )

                                val dayTotalPrefix = if (dailySum > 0) "+₹" else if (dailySum < 0) "-₹" else "₹"
                                val formattedDailyAmount = CurrencyFormatter.format(kotlin.math.abs(dailySum)).replace("₹", "")
                                Text(
                                    text = "Day Total: $dayTotalPrefix$formattedDailyAmount",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textSecondary
                                )
                            }

                            // Daily Card Group Container
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.surfaceCard)
                                    .border(0.5.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                            ) {
                                Column {
                                    txns.forEachIndexed { index, transaction ->
                                        val category = categoryMap[transaction.categoryId]
                                        TransactionItem(
                                            transaction = transaction,
                                            categoryName = category?.name ?: "Unknown",
                                            categoryColor = category?.colorHex ?: "#607D8B",
                                            categoryIcon = category?.iconName ?: "MoreHoriz",
                                            showDate = false,
                                            onClick = { selectedTransactionForOptions = transaction }
                                        )

                                        if (index < txns.size - 1) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                color = colors.cardBorder.copy(alpha = 0.5f),
                                                thickness = 0.5.dp
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
}
