package com.example.hisab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType
import com.example.hisab.ui.theme.ExpenseRed
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.CategoryIconMapper
import com.example.hisab.util.CurrencyFormatter

private data class CategoryExpenseSummary(
    val categoryId: Long,
    val totalAmount: Double,
    val transactionCount: Int
)

@Composable
fun ExpenseLeaderboard(
    expenses: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors
    val categoryMap = categories.associateBy { it.id }

    val categorySummaries = expenses
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.categoryId }
        .map { (catId, list) ->
            CategoryExpenseSummary(
                categoryId = catId,
                totalAmount = list.sumOf { it.amount },
                transactionCount = list.size
            )
        }
        .sortedByDescending { it.totalAmount }

    val maxExpenseAmount = categorySummaries.firstOrNull()?.totalAmount ?: 1.0

    if (categorySummaries.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No expenses recorded this month",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        categorySummaries.take(10).forEachIndexed { index, item ->
            val rank = index + 1
            val category = categoryMap[item.categoryId]
            val categoryName = category?.name ?: "Other Expense"
            val iconName = category?.iconName ?: "MoreHoriz"
            val colorHex = category?.colorHex ?: "#FF5252"

            val badgeColor = when (rank) {
                1 -> Color(0xFFFFD700) // Gold
                2 -> Color(0xFFC0C0C0) // Silver
                3 -> Color(0xFFCD7F32) // Bronze
                else -> colors.textTertiary.copy(alpha = 0.2f)
            }

            val badgeTextColor = when (rank) {
                1, 2, 3 -> Color.Black
                else -> colors.textSecondary
            }

            val progress = (item.totalAmount / maxExpenseAmount).toFloat().coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rank Badge
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(badgeColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#$rank",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeTextColor
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Category Icon
                        val parsedCatColor = try {
                            Color(android.graphics.Color.parseColor(colorHex))
                        } catch (e: Exception) {
                            colors.expense
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(parsedCatColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = CategoryIconMapper.getIcon(iconName),
                                contentDescription = categoryName,
                                tint = parsedCatColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Category & Details
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = categoryName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.transactionCount} payment${if (item.transactionCount > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Total sum of category
                        Text(
                            text = CurrencyFormatter.format(item.totalAmount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Relative visual bar
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = ExpenseRed,
                        trackColor = colors.cardBorder,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
