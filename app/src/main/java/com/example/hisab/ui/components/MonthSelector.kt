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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.DateUtils
import java.time.YearMonth

@Composable
fun MonthSelector(
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors
    val isCurrentMonth = DateUtils.isCurrentMonth(selectedMonth)
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onMonthChange(selectedMonth.minusMonths(1)) },
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "Previous month",
                tint = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = if (isCurrentMonth) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else colors.cardBorder,
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable { showPicker = true }
                .padding(horizontal = 18.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = DateUtils.formatMonthYear(selectedMonth),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Select Month & Year",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = { onMonthChange(selectedMonth.plusMonths(1)) },
            modifier = Modifier.size(38.dp),
            enabled = !isCurrentMonth
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Next month",
                tint = if (!isCurrentMonth) colors.textSecondary
                else colors.textTertiary.copy(alpha = 0.3f)
            )
        }
    }

    if (showPicker) {
        MonthYearPickerDialog(
            initialMonth = selectedMonth,
            onDismiss = { showPicker = false },
            onMonthYearSelected = { newMonth ->
                onMonthChange(newMonth)
                showPicker = false
            }
        )
    }
}
