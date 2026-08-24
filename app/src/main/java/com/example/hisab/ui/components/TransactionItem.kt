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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.sp
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.CategoryIconMapper
import com.example.hisab.util.CurrencyFormatter
import com.example.hisab.util.DateUtils

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

    val transferColor = Color(0xFF64B5F6) // Muted cyan blue for transfers
    val incomeColor = Color(0xFF00E676) // Bright green for income
    val expenseColor = Color(0xFFFF5252) // Bright coral red for expenses

    val defaultPrimary = MaterialTheme.colorScheme.primary
    val parsedColor = remember(categoryColor, isTransfer, defaultPrimary) {
        try {
            if (categoryColor.isNotBlank()) {
                Color(android.graphics.Color.parseColor(categoryColor))
            } else if (isTransfer) {
                Color(0xFF64B5F6)
            } else {
                defaultPrimary
            }
        } catch (e: Exception) {
            if (isTransfer) Color(0xFF64B5F6) else defaultPrimary
        }
    }

    val amountColor = when {
        isTransfer -> parsedColor
        isExpense -> expenseColor
        else -> incomeColor
    }
    val amountPrefix = when {
        isTransfer -> "⇄ "
        isExpense -> "−"
        else -> "+"
    }

    val displayName = categoryName

    val emoji = remember(categoryIcon, isTransfer) {
        if (categoryIcon.isNotBlank()) com.example.hisab.data.sms.SmsNotificationHelper.getCategoryEmoji(categoryIcon)
        else if (isTransfer) "🔄"
        else "📌"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category emoji container
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(parsedColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Name & notes / account transfer direction
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val subtitleText = remember(transaction, isTransfer, showDate) {
                if (isTransfer) {
                    val fromAcc = transaction.account
                    val toAcc = transaction.toAccount ?: "Account"
                    val accDirection = "$fromAcc → $toAcc"
                    if (transaction.notes.isNotBlank()) "$accDirection  •  ${transaction.notes}" else accDirection
                } else if (transaction.notes.isNotBlank()) {
                    transaction.notes
                } else if (showDate) {
                    DateUtils.formatShort(transaction.date)
                } else {
                    transaction.account
                }
            }

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Amount
        Text(
            text = "$amountPrefix${CurrencyFormatter.format(transaction.amount)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = amountColor
        )
    }
}
