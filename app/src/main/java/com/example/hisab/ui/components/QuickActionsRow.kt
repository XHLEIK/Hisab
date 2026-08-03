package com.example.hisab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hisab.data.model.TransactionType
import com.example.hisab.ui.theme.HisabTheme

@Composable
fun QuickActionsRow(
    onAddType: (TransactionType) -> Unit,
    onOpenSetLimit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MinimalActionPill(
            label = "Income",
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            accentColor = Color(0xFF10B981), // Emerald Green
            modifier = Modifier.weight(1f),
            onClick = { onAddType(TransactionType.INCOME) }
        )
        MinimalActionPill(
            label = "Expense",
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            accentColor = Color(0xFFEF4444), // Coral Red
            modifier = Modifier.weight(1f),
            onClick = { onAddType(TransactionType.EXPENSE) }
        )
        MinimalActionPill(
            label = "Transfer",
            icon = Icons.Filled.SwapHoriz,
            accentColor = Color(0xFF3B82F6), // Blue
            modifier = Modifier.weight(1f),
            onClick = { onAddType(TransactionType.TRANSFER) }
        )
        MinimalActionPill(
            label = "Limit",
            icon = Icons.Filled.Tune,
            accentColor = Color(0xFFF59E0B), // Amber
            modifier = Modifier.weight(1f),
            onClick = onOpenSetLimit
        )
    }
}

@Composable
private fun MinimalActionPill(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = HisabTheme.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        }
    }
}
