package com.example.hisab.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    categories: List<CategoryEntity>,
    accounts: List<String>,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity) -> Unit,
    editTransaction: TransactionEntity? = null,
    editCategoryId: Long? = null,
    initialType: TransactionType = TransactionType.EXPENSE,
    onAddAccount: ((name: String, type: String) -> Unit)? = null
) {
    val colors = HisabTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var type by remember {
        mutableStateOf(editTransaction?.type ?: initialType)
    }
    var amountText by remember {
        mutableStateOf(editTransaction?.amount?.let {
            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        } ?: "")
    }
    var selectedCategoryId by remember {
        mutableLongStateOf(editCategoryId ?: editTransaction?.categoryId ?: categories.firstOrNull()?.id ?: 0L)
    }
    var selectedAccount by remember {
        mutableStateOf(editTransaction?.account ?: accounts.firstOrNull() ?: "Primary Bank")
    }
    var selectedToAccount by remember {
        mutableStateOf(
            editTransaction?.toAccount
                ?: accounts.getOrNull(1)
                ?: accounts.firstOrNull()
                ?: "Secondary Bank"
        )
    }
    var selectedDate by remember {
        mutableStateOf(editTransaction?.date ?: LocalDate.now())
    }
    var notes by remember {
        mutableStateOf(editTransaction?.notes ?: "")
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }

    val filteredCategories = categories.filter { it.type == type }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textTertiary.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Title ────────────────────────────────────
            Text(
                text = if (editTransaction != null) "Edit Entry" else "Add Entry",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // ── Type Toggle ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
            ) {
                TypeToggleButton(
                    text = "Expense",
                    isSelected = type == TransactionType.EXPENSE,
                    color = colors.expense,
                    onClick = {
                        type = TransactionType.EXPENSE
                        selectedCategoryId = categories.firstOrNull { it.type == TransactionType.EXPENSE }?.id ?: 0L
                    },
                    modifier = Modifier.weight(1f)
                )
                TypeToggleButton(
                    text = "Income",
                    isSelected = type == TransactionType.INCOME,
                    color = colors.income,
                    onClick = {
                        type = TransactionType.INCOME
                        selectedCategoryId = categories.firstOrNull { it.type == TransactionType.INCOME }?.id ?: 0L
                    },
                    modifier = Modifier.weight(1f)
                )
                TypeToggleButton(
                    text = "Transfer",
                    isSelected = type == TransactionType.TRANSFER,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = {
                        type = TransactionType.TRANSFER
                        selectedCategoryId = categories.firstOrNull { it.type == TransactionType.TRANSFER }?.id ?: 0L
                        if (accounts.size > 1 && selectedAccount == selectedToAccount) {
                            selectedToAccount = accounts.firstOrNull { it != selectedAccount } ?: accounts.getOrNull(1) ?: "Secondary Bank"
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Amount Display ───────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (amountText.isEmpty()) "₹0" else "₹$amountText",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (amountText.isEmpty()) colors.textTertiary else colors.textPrimary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Numeric Keypad ───────────────────────────
            NumericKeypad(
                onDigit = { digit ->
                    if (digit == "." && amountText.contains(".")) {
                        // ignore duplicate dot
                    } else if (digit == "." && amountText.isEmpty()) {
                        amountText = "0."
                    } else {
                        val dotIndex = amountText.indexOf(".")
                        if (dotIndex < 0 || amountText.length - dotIndex <= 2) {
                            amountText += digit
                        }
                    }
                },
                onBackspace = {
                    if (amountText.isNotEmpty()) {
                        amountText = amountText.dropLast(1)
                    }
                },
                onClear = { amountText = "" }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Category Picker ──────────────────────────
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            CategoryPicker(
                categories = filteredCategories,
                selectedCategoryId = selectedCategoryId,
                onCategorySelected = { selectedCategoryId = it.id },
                modifier = Modifier.heightIn(max = 200.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ── Account Picker (From / To) ───────────────
            if (type == TransactionType.TRANSFER) {
                if (accounts.size < 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "Only 1 account exists",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create a secondary account to perform transfer between accounts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { showAddAccountDialog = true }
                            ) {
                                Text("+ Create Secondary Account", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "From Account",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    AccountPicker(
                        accounts = accounts,
                        selectedAccount = selectedAccount,
                        onAccountSelected = { selectedAccount = it },
                        onAddAccountClick = { showAddAccountDialog = true }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "To Account",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    AccountPicker(
                        accounts = accounts,
                        selectedAccount = selectedToAccount,
                        onAccountSelected = { selectedToAccount = it },
                        onAddAccountClick = { showAddAccountDialog = true }
                    )
                }
            } else {
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                AccountPicker(
                    accounts = accounts,
                    selectedAccount = selectedAccount,
                    onAccountSelected = { selectedAccount = it },
                    onAddAccountClick = { showAddAccountDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Date & Notes Row ─────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = "Select date",
                            tint = colors.textSecondary,
                            modifier = Modifier.height(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = DateUtils.formatShort(selectedDate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = {
                        Text(
                            "Add note...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = colors.cardBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Save Button ──────────────────────────────
            val canSave = amountText.isNotBlank() &&
                    amountText.toDoubleOrNull() != null &&
                    amountText.toDouble() > 0 &&
                    (type != TransactionType.TRANSFER && selectedCategoryId > 0 ||
                     type == TransactionType.TRANSFER && accounts.size >= 2 && selectedAccount != selectedToAccount)

            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: return@Button
                    val transferCatId = categories.firstOrNull { it.name.equals("Transfer", ignoreCase = true) }?.id
                        ?: categories.firstOrNull()?.id ?: 1L
                    val transaction = TransactionEntity(
                        id = editTransaction?.id ?: 0,
                        amount = amount,
                        type = type,
                        categoryId = if (type == TransactionType.TRANSFER) transferCatId else selectedCategoryId,
                        account = selectedAccount,
                        toAccount = if (type == TransactionType.TRANSFER) selectedToAccount else null,
                        date = selectedDate,
                        notes = notes.trim(),
                        createdAt = editTransaction?.createdAt ?: System.currentTimeMillis()
                    )
                    onSave(transaction)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = canSave,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (editTransaction != null) "Update" else "Save Entry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showAddAccountDialog) {
        AddAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onAccountAdded = { name, typeStr ->
                onAddAccount?.invoke(name, typeStr)
                if (type == TransactionType.TRANSFER && selectedAccount == name) {
                    selectedToAccount = name
                } else if (type == TransactionType.TRANSFER) {
                    selectedToAccount = name
                } else {
                    selectedAccount = name
                }
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun TypeToggleButton(
    text: String,
    isSelected: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) color.copy(alpha = 0.15f)
        else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(200),
        label = "typeToggleBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) color
        else HisabTheme.colors.textSecondary,
        animationSpec = tween(200),
        label = "typeToggleText"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}
