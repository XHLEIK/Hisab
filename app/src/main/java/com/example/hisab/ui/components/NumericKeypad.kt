package com.example.hisab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.example.hisab.ui.theme.HisabTheme

@Composable
fun NumericKeypad(
    onInput: (String) -> Unit,
    onBackspace: () -> Unit,
    onEquals: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: %  ÷  ×  −
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CalculatorKey("%", onClick = { onInput("%") }, modifier = Modifier.weight(1f), isOperator = true)
            CalculatorKey("÷", onClick = { onInput("÷") }, modifier = Modifier.weight(1f), isOperator = true)
            CalculatorKey("×", onClick = { onInput("×") }, modifier = Modifier.weight(1f), isOperator = true)
            CalculatorKey("−", onClick = { onInput("−") }, modifier = Modifier.weight(1f), isOperator = true)
        }
        // Row 2: 7 8 9 +
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CalculatorKey("7", onClick = { onInput("7") }, modifier = Modifier.weight(1f))
            CalculatorKey("8", onClick = { onInput("8") }, modifier = Modifier.weight(1f))
            CalculatorKey("9", onClick = { onInput("9") }, modifier = Modifier.weight(1f))
            CalculatorKey("+", onClick = { onInput("+") }, modifier = Modifier.weight(1f), isOperator = true)
        }
        // Row 3: 4 5 6 ⌫
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CalculatorKey("4", onClick = { onInput("4") }, modifier = Modifier.weight(1f))
            CalculatorKey("5", onClick = { onInput("5") }, modifier = Modifier.weight(1f))
            CalculatorKey("6", onClick = { onInput("6") }, modifier = Modifier.weight(1f))
            CalculatorKey("⌫", onClick = onBackspace, modifier = Modifier.weight(1f), isOperator = true)
        }
        // Rows 4-5: left 3x2 grid + tall = on right
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(modifier = Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalculatorKey("1", onClick = { onInput("1") }, modifier = Modifier.weight(1f))
                    CalculatorKey("2", onClick = { onInput("2") }, modifier = Modifier.weight(1f))
                    CalculatorKey("3", onClick = { onInput("3") }, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalculatorKey("00", onClick = { onInput("00") }, modifier = Modifier.weight(1f))
                    CalculatorKey("0", onClick = { onInput("0") }, modifier = Modifier.weight(1f))
                    CalculatorKey(".", onClick = { onInput(".") }, modifier = Modifier.weight(1f))
                }
            }
            // Tall = button spanning 2 rows
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(98.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onEquals),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "=",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 24.sp
                )
            }
        }
    }
}

@Composable
private fun CalculatorKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isOperator: Boolean = false
) {
    val colors = HisabTheme.colors
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isOperator) colors.textTertiary.copy(alpha = 0.11f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isOperator) colors.textSecondary else colors.textPrimary,
            fontSize = if (text == "⌫") 17.sp else 18.sp
        )
    }
}


