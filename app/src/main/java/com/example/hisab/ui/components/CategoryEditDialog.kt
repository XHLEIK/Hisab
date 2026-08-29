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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.model.TransactionType
import com.example.hisab.data.sms.SmsNotificationHelper
import com.example.hisab.ui.components.emoji.EmojiPickerSheet
import com.example.hisab.ui.theme.HisabTheme
import java.text.BreakIterator

internal fun lastGrapheme(text: String): String {
    if (text.isEmpty()) return text
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val end = iterator.last()
    val start = iterator.previous()
    return if (start == BreakIterator.DONE) text else text.substring(start, end)
}

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
    var showEmojiPicker by remember { mutableStateOf(false) }
    var hasAttemptedSave by remember { mutableStateOf(false) }

    val trimmedName = name.trim()
    val isNameValid = trimmedName.isNotBlank()
    val showNameError = hasAttemptedSave && !isNameValid
    val canSave = isNameValid && selectedEmoji.isNotBlank()

    val colorOptions = listOf(
        "#4CAF50", "#FF5252", "#2196F3", "#9C27B0", "#FF9800",
        "#00BCD4", "#E91E63", "#607D8B", "#795548", "#FF5722", "#3F51B5"
    )

    val parsedColor = try {
        Color(android.graphics.Color.parseColor(selectedColorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

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
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Category Name ──
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Category Name") },
                        placeholder = { Text("Category Name", color = colors.textTertiary) },
                        singleLine = true,
                        isError = showNameError,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = colors.cardBorder,
                            errorBorderColor = colors.expense,
                            focusedContainerColor = colors.innerSurface,
                            unfocusedContainerColor = colors.innerSurface,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                    if (showNameError) {
                        Text(
                            text = "Category name is required",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.expense,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }

                // ── Category Type ──
                if (category == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Category Type",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SegmentedTypeButton(
                                label = "Expense",
                                isSelected = type == TransactionType.EXPENSE,
                                selectedColor = colors.expense,
                                onClick = { type = TransactionType.EXPENSE },
                                modifier = Modifier.weight(1f)
                            )
                            SegmentedTypeButton(
                                label = "Income",
                                isSelected = type == TransactionType.INCOME,
                                selectedColor = colors.income,
                                onClick = { type = TransactionType.INCOME },
                                modifier = Modifier.weight(1f)
                            )
                            SegmentedTypeButton(
                                label = "Transfer",
                                isSelected = type == TransactionType.TRANSFER,
                                selectedColor = MaterialTheme.colorScheme.primary,
                                onClick = { type = TransactionType.TRANSFER },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ── Category Icon ──
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Category Icon",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(parsedColor.copy(alpha = 0.10f))
                            .border(1.2.dp, parsedColor.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) { showEmojiPicker = true }
                            .semantics {
                                contentDescription = "Category icon $selectedEmoji, tap to choose"
                                role = Role.Button
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedEmoji.ifBlank { "🛒" },
                            fontSize = 36.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Text(
                        text = "Tap to choose",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center
                    )
                }

                // ── Theme Color ──
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Category Color",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) colors.textPrimary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorHex = hex }
                                    .semantics {
                                        contentDescription = "Color $hex"
                                        role = Role.Button
                                    }
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.95f))
                                            .align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    hasAttemptedSave = true
                    val finalName = name.trim()
                    if (finalName.isBlank() || selectedEmoji.isBlank()) return@Button
                    val finalEmoji = lastGrapheme(selectedEmoji.trim())
                    onSave(finalName, type, finalEmoji, selectedColorHex)
                    onDismiss()
                },
                enabled = canSave,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = colors.cardBorder.copy(alpha = 0.5f),
                    contentColor = Color.White,
                    disabledContentColor = colors.textTertiary
                )
            ) {
                Text(
                    text = if (category != null) "Save Changes" else "Create Category",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary, fontWeight = FontWeight.Medium)
            }
        }
    )

    if (showEmojiPicker) {
        EmojiPickerSheet(
            currentEmoji = selectedEmoji,
            onEmojiSelected = { emoji ->
                selectedEmoji = lastGrapheme(emoji.trim())
            },
            onDismiss = { showEmojiPicker = false }
        )
    }
}

@Composable
private fun SegmentedTypeButton(
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) selectedColor.copy(alpha = 0.14f)
                else colors.surfaceCard
            )
            .border(
                width = if (isSelected) 1.4.dp else 0.8.dp,
                color = if (isSelected) selectedColor else colors.cardBorder.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) selectedColor else colors.textSecondary
        )
    }
}
