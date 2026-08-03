package com.example.hisab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.CurrencyFormatter
import com.example.hisab.util.DateUtils

import androidx.compose.material3.Icon
import com.example.hisab.util.CategoryIconMapper

@Composable
fun TransactionItem(
    transaction: TransactionEntity,
    categoryName: String,
    categoryColor: String,
    categoryIcon: String,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    onClick: () -> Unit = {}
) {
    val colors = HisabTheme.colors
    val isTransfer = transaction.type == TransactionType.TRANSFER
    val isExpense = transaction.type == TransactionType.EXPENSE

    val amountColor = when {
        isTransfer -> MaterialTheme.colorScheme.primary
        isExpense -> colors.expense
        else -> colors.income
    }
    val amountPrefix = when {
        isTransfer -> "⇄ "
        isExpense -> "−"
        else -> "+"
    }

    val displayName = if (isTransfer) {
        val toAcc = transaction.toAccount ?: "Account"
        "Transfer: ${transaction.account} → $toAcc"
    } else {
        categoryName
    }

    val iconVector = if (isTransfer) CategoryIconMapper.getIcon("SwapHoriz") else CategoryIconMapper.getIcon(categoryIcon)

    val defaultPrimary = MaterialTheme.colorScheme.primary
    val parsedColor = remember(categoryColor, isTransfer, defaultPrimary) {
        if (isTransfer) defaultPrimary
        else {
            try {
                Color(android.graphics.Color.parseColor(categoryColor))
            } catch (e: Exception) {
                defaultPrimary
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category icon circle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(parsedColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = displayName,
                tint = parsedColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Name & notes
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (transaction.notes.isNotBlank()) {
                Text(
                    text = transaction.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (showDate) {
                Text(
                    text = DateUtils.formatShort(transaction.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Amount
        Text(
            text = "$amountPrefix${CurrencyFormatter.format(transaction.amount)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = amountColor
        )
    }
}

