package com.example.hisab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Tier 1: Segmented Control Tabs (All, Income, Expense, Transfer) ──
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
            val typeTabs = listOf(
                null to "All",
                TransactionType.INCOME to "Income",
                TransactionType.EXPENSE to "Expense",
                TransactionType.TRANSFER to "Transfer"
            )

            typeTabs.forEach { (type, label) ->
                val isSelected = (selectedType == type)
                val activeBg = when (type) {
                    TransactionType.INCOME -> Color(0xFF00E676).copy(alpha = 0.15f)
                    TransactionType.EXPENSE -> Color(0xFFFF5252).copy(alpha = 0.15f)
                    TransactionType.TRANSFER -> Color(0xFF64B5F6).copy(alpha = 0.15f)
                    null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                }
                val activeText = when (type) {
                    TransactionType.INCOME -> Color(0xFF00E676)
                    TransactionType.EXPENSE -> Color(0xFFFF5252)
                    TransactionType.TRANSFER -> Color(0xFF64B5F6)
                    null -> MaterialTheme.colorScheme.primary
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSelected) activeBg else Color.Transparent)
                        .clickable { onTypeSelected(type) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) activeText else colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ── Tier 2: Account Filter Chips ──
        if (accounts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // "All Accounts" Chip
                val isAllAccountsSelected = (selectedAccount == null)
                FilterChip(
                    selected = isAllAccountsSelected,
                    onClick = { onAccountSelected(null) },
                    label = { Text("All Accounts", style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        containerColor = colors.surfaceCard,
                        labelColor = colors.textSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = colors.cardBorder,
                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        enabled = true,
                        selected = isAllAccountsSelected
                    )
                )

                // Individual Account Chips
                accounts.forEach { account ->
                    val isSelected = (selectedAccount == account)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onAccountSelected(if (isSelected) null else account)
                        },
                        label = { Text(account, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            containerColor = colors.surfaceCard,
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
    }
}
