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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
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
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MinimalActionPill(
                label = "Expense",
                icon = Icons.Filled.Remove,
                accentColor = Color(0xFFFF5252), // Bright Red
                modifier = Modifier.weight(1f),
                onClick = { onAddType(TransactionType.EXPENSE) }
            )
            MinimalActionPill(
                label = "Income",
                icon = Icons.Filled.Add,
                accentColor = Color(0xFF00E676), // Bright Green
                modifier = Modifier.weight(1f),
                onClick = { onAddType(TransactionType.INCOME) }
            )
            MinimalActionPill(
                label = "Transfer",
                icon = Icons.Filled.SwapHoriz,
                accentColor = Color(0xFF64B5F6), // Bright Blue
                modifier = Modifier.weight(1f),
                onClick = { onAddType(TransactionType.TRANSFER) }
            )
        }
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
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.30f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}
