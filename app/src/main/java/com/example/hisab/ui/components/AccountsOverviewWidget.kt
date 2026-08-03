package com.example.hisab.ui.components

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.CategoryIconMapper
import com.example.hisab.util.CurrencyFormatter

@Composable
fun AccountsOverviewWidget(
    accountBalances: Map<String, Double>,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Accounts Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                text = "+ Add Account",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onAddAccount() }
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(accountBalances.entries.toList(), key = { it.key }) { (accName, balance) ->
                val icon = CategoryIconMapper.getAccountIcon(accName)
                val lowerName = accName.lowercase()
                val accentColor = when {
                    lowerName.contains("primary") -> androidx.compose.ui.graphics.Color(0xFF10B981) // Green
                    lowerName.contains("secondary") -> androidx.compose.ui.graphics.Color(0xFF3B82F6) // Blue
                    lowerName.contains("savings") || lowerName.contains("saving") -> androidx.compose.ui.graphics.Color(0xFFF59E0B) // Yellow/Gold
                    lowerName.contains("cash") -> androidx.compose.ui.graphics.Color(0xFF8B5CF6) // Purple
                    else -> androidx.compose.ui.graphics.Color(0xFF14B8A6) // Teal
                }

                Box(
                    modifier = Modifier
                        .width(155.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = accName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary,
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = CurrencyFormatter.format(balance),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
