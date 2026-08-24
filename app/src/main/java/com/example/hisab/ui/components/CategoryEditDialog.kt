package com.example.hisab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.model.TransactionType
import com.example.hisab.data.sms.SmsNotificationHelper
import com.example.hisab.ui.theme.HisabTheme

@Composable
fun CategoryEditDialog(
    category: CategoryEntity? = null,
    initialType: TransactionType = TransactionType.EXPENSE,
    onDismiss: () -> Unit,
    onSave: (name: String, type: TransactionType, iconName: String, colorHex: String) -> Unit
) {
    val colors = HisabTheme.colors

    var name by remember { mutableStateOf(category?.name ?: "") }
    var type by remember { mutableStateOf(category?.type ?: initialType) }
    var selectedEmoji by remember {
        mutableStateOf(
            if (category != null) SmsNotificationHelper.getCategoryEmoji(category.iconName) else "🛒"
        )
    }
    var selectedColorHex by remember { mutableStateOf(category?.colorHex ?: "#4CAF50") }

    val presetEmojis = listOf(
        "🛒", "🍽️", "🛍️", "🚗", "🧾", "👥", "💪", "🏥", "🎬", "🎓",
        "✈️", "📱", "🏦", "📌", "🐷", "📊", "📈", "🔒", "🥧", "🔄",
        "🍔", "☕", "🎁", "💻", "💵", "💳", "👛", "✨", "👶", "🐾",
        "🔧", "🏠", "💡", "⚡", "💧", "📶", "👕", "🧺", "🏍️", "⛽",
        "🚌", "🏨", "📷", "✂️", "📚", "💼", "➕", "🎯", "🍕", "🎮"
    )

    val colorOptions = listOf(
        "#4CAF50", "#FF5252", "#2196F3", "#9C27B0", "#FF9800",
        "#00BCD4", "#E91E63", "#607D8B", "#795548", "#FF5722", "#3F51B5"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (category != null) "Edit Category" else "Add Category",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g. Groceries, Coffee, Clothes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = colors.cardBorder
                    )
                )

                // Type selector if creating new
                if (category == null) {
                    Text(
                        text = "Category Type",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (type == TransactionType.EXPENSE) colors.expense.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (type == TransactionType.EXPENSE) colors.expense else colors.cardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { type = TransactionType.EXPENSE }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Expense",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (type == TransactionType.EXPENSE) colors.expense else colors.textSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (type == TransactionType.INCOME) colors.income.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (type == TransactionType.INCOME) colors.income else colors.cardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { type = TransactionType.INCOME }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Income",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (type == TransactionType.INCOME) colors.income else colors.textSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (type == TransactionType.TRANSFER) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (type == TransactionType.TRANSFER) MaterialTheme.colorScheme.primary else colors.cardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { type = TransactionType.TRANSFER }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Transfer",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (type == TransactionType.TRANSFER) MaterialTheme.colorScheme.primary else colors.textSecondary
                            )
                        }
                    }
                }

                // Emoji Icon Selector
                Text(
                    text = "Category Emoji",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Selected Emoji Display Badge
                    val parsedColor = try {
                        Color(android.graphics.Color.parseColor(selectedColorHex))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.primary
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(parsedColor.copy(alpha = 0.18f))
                            .border(1.5.dp, parsedColor, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedEmoji,
                            fontSize = 28.sp
                        )
                    }

                    // Input field allowing user to type or paste ANY Android emoji from keyboard
                    OutlinedTextField(
                        value = selectedEmoji,
                        onValueChange = {
                            if (it.isNotBlank()) {
                                selectedEmoji = it.trim()
                            }
                        },
                        label = { Text("Choose Emoji") },
                        placeholder = { Text("Type any emoji...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = colors.cardBorder
                        )
                    )
                }

                // Quick Preset Emojis Row
                Text(
                    text = "Popular Emojis",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetEmojis.forEach { emoji ->
                        val isSelected = (emoji == selectedEmoji)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 20.sp
                            )
                        }
                    }
                }

                // Color Selector
                Text(
                    text = "Theme Color",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colorOptions.forEach { hex ->
                        val isSelected = hex.equals(selectedColorHex, ignoreCase = true)
                        val color = try {
                            Color(android.graphics.Color.parseColor(hex))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    if (isSelected) 3.dp else 0.dp,
                                    if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { selectedColorHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && selectedEmoji.isNotBlank()) {
                        onSave(name.trim(), type, selectedEmoji.trim(), selectedColorHex)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank() && selectedEmoji.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (category != null) "Save Changes" else "Create Category")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
