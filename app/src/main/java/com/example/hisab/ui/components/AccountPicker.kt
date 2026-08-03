package com.example.hisab.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hisab.ui.theme.HisabTheme

@Composable
fun AccountPicker(
    accounts: List<String>,
    selectedAccount: String,
    onAccountSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAddAccountClick: (() -> Unit)? = null
) {
    val colors = HisabTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        accounts.forEach { account ->
            val isSelected = account == selectedAccount
            FilterChip(
                selected = isSelected,
                onClick = { onAccountSelected(account) },
                label = {
                    Text(
                        text = account,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
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

        if (onAddAccountClick != null) {
            FilterChip(
                selected = false,
                onClick = onAddAccountClick,
                label = {
                    Text(
                        text = "Add Account",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Account",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    labelColor = MaterialTheme.colorScheme.primary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    enabled = true,
                    selected = false
                )
            )
        }
    }
}
