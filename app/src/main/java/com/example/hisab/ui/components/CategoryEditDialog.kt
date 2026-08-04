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
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.model.TransactionType
import com.example.hisab.ui.theme.HisabTheme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.example.hisab.util.CategoryIconMapper

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
    var selectedIcon by remember { mutableStateOf(category?.iconName ?: "ShoppingCart") }
    var selectedColorHex by remember { mutableStateOf(category?.colorHex ?: "#4CAF50") }
    var iconSearchQuery by remember { mutableStateOf("") }
    val iconOptions = CategoryIconMapper.availableIcons

    val filteredIconOptions = remember(iconSearchQuery) {
        if (iconSearchQuery.isBlank()) {
            iconOptions
        } else {
            val q = iconSearchQuery.trim().lowercase()
            iconOptions.filter { (key, _) ->
                key.lowercase().contains(q) || when (q) {
                    "shirt", "tshirt", "cloth", "dress", "apparel", "t-shirt" -> key.contains("Checkroom", ignoreCase = true)
                    "wash", "washing", "machine", "laundry", "clean" -> key.contains("LocalLaundryService", ignoreCase = true)
                    "coffee", "tea", "cafe", "drink" -> key.contains("Coffee", ignoreCase = true)
                    "food", "snack", "burger", "pizza", "dining" -> key.contains("Fastfood", ignoreCase = true) || key.contains("Restaurant", ignoreCase = true)
                    "pet", "dog", "cat", "animal" -> key.contains("Pets", ignoreCase = true)
                    "gas", "fuel", "petrol" -> key.contains("LocalGasStation", ignoreCase = true)
                    "bus", "travel", "transport" -> key.contains("Bus", ignoreCase = true) || key.contains("Flight", ignoreCase = true)
                    "wifi", "net", "internet" -> key.contains("Wifi", ignoreCase = true)
                    "game", "gaming" -> key.contains("Esports", ignoreCase = true)
                    "salon", "hair", "barber", "grooming" -> key.contains("ContentCut", ignoreCase = true)
                    "repair", "tool", "fix", "hardware" -> key.contains("Build", ignoreCase = true)
                    "hospital", "doctor", "health", "medicine", "pharmacy" -> key.contains("Hospital", ignoreCase = true) || key.contains("Medical", ignoreCase = true)
                    else -> key.lowercase().contains(q)
                }
            }
        }
    }

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

                // Icon Search & Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category Icon",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                    if (iconSearchQuery.isNotEmpty()) {
                        Text(
                            text = "${filteredIconOptions.size} icons",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                OutlinedTextField(
                    value = iconSearchQuery,
                    onValueChange = { iconSearchQuery = it },
                    placeholder = { Text("Search icon (e.g. shirt, wash, coffee, gas)...", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (iconSearchQuery.isNotEmpty()) {
                            androidx.compose.material3.IconButton(onClick = { iconSearchQuery = "" }) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = colors.cardBorder
                    )
                )

                if (filteredIconOptions.isEmpty()) {
                    Text(
                        text = "No icons found for '$iconSearchQuery'",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredIconOptions.forEach { (iconKey, vector) ->
                            val isSelected = iconKey == selectedIcon
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
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
                                    .clickable { selectedIcon = iconKey },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = vector,
                                    contentDescription = iconKey,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else colors.textSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // Color Selector
                Text(
                    text = "Color",
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
                    if (name.isNotBlank()) {
                        onSave(name.trim(), type, selectedIcon, selectedColorHex)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank(),
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
