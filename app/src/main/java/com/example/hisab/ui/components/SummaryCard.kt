package com.example.hisab.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.CurrencyFormatter

enum class SummaryCardType {
    INCOME, EXPENSE, BALANCE
}

@Composable
fun SummaryCard(
    label: String,
    amount: Double,
    type: SummaryCardType,
    modifier: Modifier = Modifier,
    previousAmount: Double? = null
) {
    val colors = HisabTheme.colors

    val (accentColor, surfaceColor) = when (type) {
        SummaryCardType.INCOME -> Pair(colors.income, colors.incomeSurface)
        SummaryCardType.EXPENSE -> Pair(colors.expense, colors.expenseSurface)
        SummaryCardType.BALANCE -> Pair(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )
    }

    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(500),
        label = "accentColor"
    )

    // Trend indicator
    val trendIcon = when {
        previousAmount == null -> null
        amount > previousAmount -> Icons.AutoMirrored.Filled.TrendingUp
        amount < previousAmount -> Icons.AutoMirrored.Filled.TrendingDown
        else -> Icons.AutoMirrored.Filled.TrendingFlat
    }

    // Animate amount counting up
    var displayAmount by remember { mutableStateOf(0.0) }
    val animatedAmount by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = tween(800),
        label = "amount"
    )
    LaunchedEffect(amount) {
        displayAmount = amount
    }

    Box(
        modifier = modifier
            .width(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        surfaceColor,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .border(
                width = 1.dp,
                color = colors.cardBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(animatedAccent)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = CurrencyFormatter.format(animatedAmount.toDouble()),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1
            )

            if (trendIcon != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = trendIcon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "vs last month",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary
                    )
                }
            }
        }
    }
}
