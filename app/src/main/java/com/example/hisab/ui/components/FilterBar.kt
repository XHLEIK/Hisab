package com.example.hisab.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hisab.data.model.TransactionType
import com.example.hisab.ui.theme.HisabTheme

@Composable
fun FilterBar(
    selectedType: TransactionType?,
    onTypeSelected: (TransactionType?) -> Unit,
    selectedAccount: String?,
    accounts: List<String>,
    onAccountSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Type filters
        FilterChip(
            selected = selectedType == null,
            onClick = { onTypeSelected(null) },
            label = { Text("All", style = MaterialTheme.typography.labelMedium) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                selectedLabelColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = colors.textSecondary
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = colors.cardBorder,
                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                enabled = true,
                selected = selectedType == null
            )
        )

        FilterChip(
            selected = selectedType == TransactionType.INCOME,
            onClick = {
                onTypeSelected(
                    if (selectedType == TransactionType.INCOME) null
                    else TransactionType.INCOME
                )
            },
            label = { Text("Income", style = MaterialTheme.typography.labelMedium) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colors.income.copy(alpha = 0.15f),
                selectedLabelColor = colors.income,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = colors.textSecondary
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = colors.cardBorder,
                selectedBorderColor = colors.income.copy(alpha = 0.5f),
                enabled = true,
                selected = selectedType == TransactionType.INCOME
            )
        )

        FilterChip(
            selected = selectedType == TransactionType.EXPENSE,
            onClick = {
                onTypeSelected(
                    if (selectedType == TransactionType.EXPENSE) null
                    else TransactionType.EXPENSE
                )
            },
            label = { Text("Expense", style = MaterialTheme.typography.labelMedium) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colors.expense.copy(alpha = 0.15f),
                selectedLabelColor = colors.expense,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = colors.textSecondary
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = colors.cardBorder,
                selectedBorderColor = colors.expense.copy(alpha = 0.5f),
                enabled = true,
                selected = selectedType == TransactionType.EXPENSE
            )
        )

        // Account filters
        accounts.forEach { account ->
            val isSelected = selectedAccount == account
            FilterChip(
                selected = isSelected,
                onClick = {
                    onAccountSelected(if (isSelected) null else account)
                },
                label = { Text(account, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
}
