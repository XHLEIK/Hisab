@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.hisab.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionConfidence
import com.example.hisab.data.model.TransactionSource
import com.example.hisab.data.model.TransactionSubtype
import com.example.hisab.data.model.TransactionType
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.CalculatorEngine
import com.example.hisab.util.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun QuickAddSheet(
    categories: List<CategoryEntity>,
    accounts: List<String>,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity) -> Unit,
    editTransaction: TransactionEntity? = null,
    editCategoryId: Long? = null,
    initialType: TransactionType = TransactionType.EXPENSE,
    initialAmount: Double? = null,
    initialAccount: String? = null,
    onAddAccount: ((name: String, type: String) -> Unit)? = null,
    recentExpenseCategories: List<CategoryEntity> = emptyList()
) {
    val colors = HisabTheme.colors
    val colorScheme = MaterialTheme.colorScheme

    var type by remember { mutableStateOf(editTransaction?.type ?: initialType) }
    var amountText by remember(editTransaction, initialAmount) {
        mutableStateOf((editTransaction?.amount ?: initialAmount)?.let {
            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        } ?: "")
    }
    val calculator = remember(editTransaction, initialAmount) {
        CalculatorEngine().apply { if (amountText.isNotEmpty()) setExpression(amountText) }
    }
    fun syncAmount() { amountText = calculator.expression }
    LaunchedEffect(amountText) {
        if (calculator.expression != amountText) calculator.setExpression(amountText)
    }

    var selectedCategoryId by remember {
        mutableLongStateOf(editCategoryId ?: editTransaction?.categoryId ?: 0L)
    }
    val isEditingSplit = editTransaction?.subtype == TransactionSubtype.SPLIT_REIMBURSEMENT.name
    var isSplit by remember { mutableStateOf(isEditingSplit) }

    val filteredCategories = remember(categories, type, isSplit, recentExpenseCategories) {
        if (isSplit) {
            if (recentExpenseCategories.isNotEmpty()) recentExpenseCategories else categories.filter { it.type == TransactionType.EXPENSE }
        } else categories.filter { it.type == type }
    }
    LaunchedEffect(type) {
        if (editTransaction == null || editTransaction.type != type) {
            val cur = categories.firstOrNull { it.id == selectedCategoryId }
            val valid = when {
                isSplit && type == TransactionType.INCOME -> cur?.type == TransactionType.EXPENSE
                else -> cur?.type == type
            }
            if (!valid && filteredCategories.none { it.id == selectedCategoryId }) selectedCategoryId = 0L
        }
    }
    LaunchedEffect(isSplit) {
        if (isSplit) {
            val cur = categories.firstOrNull { it.id == selectedCategoryId }
            if (cur == null || cur.type != TransactionType.EXPENSE) {
                if (filteredCategories.none { it.id == selectedCategoryId }) selectedCategoryId = 0L
            }
        } else if (type == TransactionType.INCOME) {
            val cur = categories.firstOrNull { it.id == selectedCategoryId }
            if (cur?.type == TransactionType.EXPENSE && filteredCategories.none { it.id == selectedCategoryId }) selectedCategoryId = 0L
        }
    }
    LaunchedEffect(filteredCategories, selectedCategoryId) {
        if (selectedCategoryId != 0L && filteredCategories.isNotEmpty() && filteredCategories.none { it.id == selectedCategoryId }) {
            val exists = categories.any { it.id == selectedCategoryId }
            if (!exists || editTransaction == null) selectedCategoryId = 0L
        }
    }

    var selectedAccount by remember {
        mutableStateOf(editTransaction?.account ?: initialAccount?.takeIf { it.isNotBlank() } ?: accounts.firstOrNull() ?: "Primary Bank")
    }
    var selectedToAccount by remember {
        mutableStateOf(editTransaction?.toAccount ?: accounts.getOrNull(1) ?: accounts.firstOrNull() ?: "Secondary Bank")
    }
    var selectedDate by remember { mutableStateOf(editTransaction?.date ?: LocalDate.now()) }
    var notes by remember { mutableStateOf(editTransaction?.notes ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var categorySearchQuery by remember { mutableStateOf("") }
    val categoryPickerScroll = rememberScrollState()
    var showAmountContextMenu by remember { mutableStateOf(false) }
    var amountBoxSize by remember { mutableStateOf(IntSize.Zero) }
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current

    val evaluatedForSave = calculator.peekEvaluatedAmount()
    val canSave = evaluatedForSave != null && evaluatedForSave > 0 && selectedCategoryId > 0 &&
        (type != TransactionType.TRANSFER || isSplit || (accounts.size >= 2 && selectedAccount != selectedToAccount))

    fun performSave() {
        val evaluated = calculator.peekEvaluatedAmount()
        val finalEvaluated = evaluated ?: calculator.evaluate()?.toDouble() ?: return
        if (evaluated == null) syncAmount()
        val amount = finalEvaluated
        val isSplitSave = isSplit
        val resolvedSubtype = if (isSplitSave) TransactionSubtype.SPLIT_REIMBURSEMENT.name
        else if (editTransaction != null && !isSplitSave && editTransaction.subtype == TransactionSubtype.SPLIT_REIMBURSEMENT.name) null
        else editTransaction?.subtype
        val finalTypeResolved = if (isSplitSave) TransactionType.EXPENSE else type
        val transaction = TransactionEntity(
            id = editTransaction?.id ?: 0,
            amount = amount,
            type = finalTypeResolved,
            categoryId = selectedCategoryId,
            account = selectedAccount,
            toAccount = if (finalTypeResolved == TransactionType.TRANSFER && !isSplitSave) selectedToAccount else null,
            date = selectedDate,
            notes = notes.trim(),
            createdAt = editTransaction?.createdAt ?: System.currentTimeMillis(),
            sourceMessageHash = editTransaction?.sourceMessageHash,
            referenceNumber = editTransaction?.referenceNumber,
            source = editTransaction?.source ?: TransactionSource.MANUAL.name,
            confidence = TransactionConfidence.MANUAL.name,
            subtype = resolvedSubtype
        )
        onSave(transaction)
    }

    val configuration = LocalConfiguration.current
    val isShortScreen = configuration.screenHeightDp < 700
    val isVeryShort = configuration.screenHeightDp < 620
    val swipeOffsetY = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    val swipeDensity = LocalDensity.current
    val swipeThresholdPx = with(swipeDensity) { 160.dp.toPx() }
    val mainScrollState = rememberScrollState()
    val swipeDraggableState = rememberDraggableState { delta ->
        if (mainScrollState.value == 0 && delta > 0) {
            swipeScope.launch {
                val newValue = (swipeOffsetY.value + delta).coerceAtLeast(0f)
                swipeOffsetY.snapTo(newValue)
            }
        } else if (delta < 0 && swipeOffsetY.value > 0) {
            swipeScope.launch {
                val newValue = (swipeOffsetY.value + delta).coerceAtLeast(0f)
                swipeOffsetY.snapTo(newValue)
            }
        }
    }
    val swipeNestedConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (mainScrollState.value == 0 && available.y > 0) {
                    swipeScope.launch {
                        val newValue = (swipeOffsetY.value + available.y).coerceAtLeast(0f)
                        swipeOffsetY.snapTo(newValue)
                    }
                    return androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                if (swipeOffsetY.value > 0 && available.y < 0) {
                    swipeScope.launch {
                        val newValue = (swipeOffsetY.value + available.y).coerceAtLeast(0f)
                        swipeOffsetY.snapTo(newValue)
                    }
                    return androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                // Fling handling for swipe
                if (available.y > 0 && swipeOffsetY.value > 0) {
                    swipeScope.launch {
                        val shouldDismiss = swipeOffsetY.value > swipeThresholdPx || available.y > 800f
                        if (shouldDismiss) {
                            swipeOffsetY.animateTo(with(swipeDensity) { 800.dp.toPx() }, tween(220))
                            onDismiss()
                        } else {
                            swipeOffsetY.animateTo(0f, tween(260))
                        }
                    }
                    return available
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
            override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                swipeScope.launch {
                    val shouldDismiss = swipeOffsetY.value > swipeThresholdPx
                    if (shouldDismiss) {
                        swipeOffsetY.animateTo(with(swipeDensity) { 800.dp.toPx() }, tween(220))
                        onDismiss()
                    } else {
                        swipeOffsetY.animateTo(0f, tween(260))
                    }
                }
                return super.onPostFling(consumed, available)
            }
        }
    }
    val verticalGap = when {
        isVeryShort -> 8.dp
        isShortScreen -> 10.dp
        else -> 12.dp
    }
    val smallGap = when {
        isVeryShort -> 6.dp
        isShortScreen -> 8.dp
        else -> 10.dp
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // Scrim + swipe-dismiss container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, swipeOffsetY.value.roundToInt()) }
                    .background(colorScheme.background)
                    .nestedScroll(swipeNestedConnection)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                        .verticalScroll(mainScrollState)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(verticalGap)
                ) {
                    // Drag handle + Header — handled by outer Box swipe, header just shows handle
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.textTertiary.copy(alpha = 0.35f))
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (editTransaction != null) "Edit Entry" else "Add Entry",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                // Type selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    TypeToggleButton(text = "Expense", isSelected = type == TransactionType.EXPENSE, color = colors.expense, onClick = {
                        type = TransactionType.EXPENSE; isSplit = false
                        val cur = categories.firstOrNull { it.id == selectedCategoryId }
                        if (cur?.type != TransactionType.EXPENSE) selectedCategoryId = 0L
                    }, modifier = Modifier.weight(1f))
                    TypeToggleButton(text = "Income", isSelected = type == TransactionType.INCOME, color = colors.income, onClick = {
                        type = TransactionType.INCOME
                        val cur = categories.firstOrNull { it.id == selectedCategoryId }
                        if (cur?.type != TransactionType.INCOME) selectedCategoryId = 0L
                    }, modifier = Modifier.weight(1f))
                    TypeToggleButton(text = "Transfer", isSelected = type == TransactionType.TRANSFER, color = colorScheme.primary, onClick = {
                        type = TransactionType.TRANSFER; isSplit = false
                        val cur = categories.firstOrNull { it.id == selectedCategoryId }
                        if (cur?.type != TransactionType.TRANSFER) selectedCategoryId = 0L
                        if (accounts.size > 1 && selectedAccount == selectedToAccount) {
                            selectedToAccount = accounts.firstOrNull { it != selectedAccount } ?: accounts.getOrNull(1) ?: "Secondary Bank"
                        }
                    }, modifier = Modifier.weight(1f))
                }

                // Amount display with long-press copy/cut/paste
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                                onLongClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showAmountContextMenu = true
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .onGloballyPositioned { amountBoxSize = it.size }
                    ) {
                        Text(
                            text = if (amountText.isEmpty()) "₹0" else "₹$amountText",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (amountText.isEmpty()) colors.textTertiary else colors.textPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (showAmountContextMenu) {
                        val density = LocalDensity.current
                        Popup(
                            alignment = Alignment.TopCenter,
                            offset = IntOffset(0, with(density) { (amountBoxSize.height + 4.dp.roundToPx()) }),
                            onDismissRequest = { showAmountContextMenu = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            val toolbarBg = colorScheme.surface
                            val toolbarBorder = colors.cardBorder
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Caret pointing up toward the amount
                                androidx.compose.foundation.Canvas(
                                    modifier = Modifier.size(width = 12.dp, height = 6.dp)
                                ) {
                                    drawPath(
                                        path = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(0f, 0f)
                                            lineTo(size.width / 2f, size.height)
                                            lineTo(size.width, 0f)
                                            close()
                                        },
                                        color = toolbarBg
                                    )
                                }
                                Surface(
                                    modifier = Modifier.shadow(8.dp, RoundedCornerShape(10.dp)),
                                    shape = RoundedCornerShape(10.dp),
                                    color = toolbarBg,
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, toolbarBorder.copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                                    ) {
                                        if (amountText.isNotEmpty()) {
                                            TextToolbarButton(text = "Cut", onClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                clipboardManager.setText(AnnotatedString(amountText))
                                                amountText = ""
                                                calculator.setExpression("")
                                                showAmountContextMenu = false
                                            })
                                            VerticalDivider(modifier = Modifier.height(20.dp).width(0.5.dp), color = toolbarBorder.copy(alpha = 0.4f))
                                            TextToolbarButton(text = "Copy", onClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                clipboardManager.setText(AnnotatedString(amountText))
                                                showAmountContextMenu = false
                                            })
                                        }
                                        if (amountText.isNotEmpty()) {
                                            VerticalDivider(modifier = Modifier.height(20.dp).width(0.5.dp), color = toolbarBorder.copy(alpha = 0.4f))
                                        }
                                        TextToolbarButton(text = "Paste", onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val clipText = clipboardManager.getText()?.toString() ?: ""
                                            val numericPaste = clipText.replace("[^0-9.]".toRegex(), "")
                                            if (numericPaste.isNotEmpty()) {
                                                amountText = numericPaste
                                                calculator.setExpression(numericPaste)
                                            }
                                            showAmountContextMenu = false
                                        })
                                    }
                                }
                            }
                        }
                    }
                }

                // Split reimbursement - only for Income, minimal texts
                if (type == TransactionType.INCOME || isEditingSplit) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSplit) colorScheme.primary.copy(alpha = 0.08f) else colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { isSplit = !isSplit }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isSplit, onCheckedChange = { isSplit = it }, colors = CheckboxDefaults.colors(checkedColor = colorScheme.primary, uncheckedColor = colors.textTertiary))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Split reimbursement",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isSplit) colorScheme.primary else colors.textPrimary
                        )
                    }
                }

                // Category - compact selector for all types
                val selectedCategory = remember(selectedCategoryId, categories) { categories.firstOrNull { it.id == selectedCategoryId } }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isSplit) "Reimbursed Category" else "Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        Text("${filteredCategories.size} categories", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selectedCategory != null) {
                                    try { Color(android.graphics.Color.parseColor(selectedCategory.colorHex)).copy(alpha = 0.10f) }
                                    catch (e: Exception) { colorScheme.surfaceVariant.copy(alpha = 0.7f) }
                                } else colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .border(
                                width = 0.5.dp,
                                color = if (selectedCategory != null) {
                                    try { Color(android.graphics.Color.parseColor(selectedCategory.colorHex)).copy(alpha = 0.5f) }
                                    catch (e: Exception) { colors.cardBorder }
                                } else colors.cardBorder.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { categorySearchQuery = ""; showCategoryPicker = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                                if (selectedCategory != null) {
                                    Box(
                                        modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(
                                                try { Color(android.graphics.Color.parseColor(selectedCategory.colorHex)).copy(alpha = 0.15f) }
                                                catch (e: Exception) { colorScheme.primary.copy(alpha = 0.15f) }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val emoji = com.example.hisab.data.sms.SmsNotificationHelper.getCategoryEmoji(selectedCategory.iconName)
                                        Text(text = emoji, fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp))
                                    }
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(selectedCategory.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text(when (selectedCategory.type) {
                                            TransactionType.EXPENSE -> "Expense"
                                            TransactionType.INCOME -> "Income"
                                            TransactionType.TRANSFER -> "Transfer"
                                        }, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colorScheme.primary)
                                    }
                                    Text("Select a category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = colors.textSecondary, modifier = Modifier.weight(1f, fill = false))
                                }
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Open categories", tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Num pad
                NumericKeypad(
                    onInput = { token -> calculator.input(token); syncAmount() },
                    onBackspace = { calculator.backspace(); syncAmount() },
                    onEquals = { calculator.evaluate(); syncAmount() }
                )

                // Account selector
                if (type == TransactionType.TRANSFER && !isSplit) {
                    if (accounts.size < 2) {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(colorScheme.primaryContainer.copy(alpha = 0.5f)).padding(12.dp)
                        ) {
                            Column {
                                Text("Only 1 account exists", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colorScheme.onPrimaryContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Create a secondary account to transfer.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(onClick = { showAddAccountDialog = true }) { Text("+ Create Secondary Account", fontWeight = FontWeight.Bold) }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(smallGap), modifier = Modifier.fillMaxWidth()) {
                            Text("From", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp), color = colors.textSecondary)
                            AccountPicker(accounts = accounts, selectedAccount = selectedAccount, onAccountSelected = { selectedAccount = it }, onAddAccountClick = { showAddAccountDialog = true })
                            Text("To", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp), color = colors.textSecondary)
                            AccountPicker(accounts = accounts, selectedAccount = selectedToAccount, onAccountSelected = { selectedToAccount = it }, onAddAccountClick = { showAddAccountDialog = true })
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                Text("ACCOUNT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp), color = colors.textSecondary)
                            }
                            Text("${accounts.size} accounts", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                        }
                        AccountPicker(accounts = accounts, selectedAccount = selectedAccount, onAccountSelected = { selectedAccount = it }, onAddAccountClick = { showAddAccountDialog = true })
                    }
                }

                // Date and Note
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(colorScheme.surfaceVariant)
                            .clickable { showDatePicker = true }.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(DateUtils.formatShort(selectedDate), style = MaterialTheme.typography.bodySmall, color = colors.textPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("Add note...", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colors.cardBorder,
                            focusedContainerColor = colorScheme.surface.copy(alpha = 0.6f),
                            unfocusedContainerColor = colorScheme.surface.copy(alpha = 0.4f)
                        )
                    )
                }

                // Save button - thicker and more prominent
                Button(
                    onClick = { performSave() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = canSave,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary, contentColor = colorScheme.onPrimary, disabledContainerColor = colors.textTertiary.copy(alpha = 0.12f)),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(if (editTransaction != null) "Update Entry" else "Save Entry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
                }
        }
    }

    if (showCategoryPicker) {
        val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val searchedCategories = remember(filteredCategories, categorySearchQuery) {
            val q = categorySearchQuery.trim()
            if (q.isEmpty()) filteredCategories else filteredCategories.filter { it.name.contains(q, ignoreCase = true) }
        }
        val recentForPicker = remember(filteredCategories, categorySearchQuery, recentExpenseCategories, type, isSplit) {
            val q = categorySearchQuery.trim()
            val baseRecent: List<CategoryEntity> = when {
                isSplit -> if (recentExpenseCategories.isNotEmpty()) recentExpenseCategories else filteredCategories
                type == TransactionType.EXPENSE && recentExpenseCategories.isNotEmpty() -> recentExpenseCategories.filter { it.type == TransactionType.EXPENSE }
                else -> filteredCategories
            }
            val pool = if (q.isEmpty()) baseRecent else baseRecent.filter { it.name.contains(q, ignoreCase = true) }
            pool.filter { c -> filteredCategories.any { it.id == c.id } }.distinctBy { it.id }.take(4)
        }
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showCategoryPicker = false },
            sheetState = pickerSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            dragHandle = {
                Box(
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).width(40.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(HisabTheme.colors.textTertiary.copy(alpha = 0.3f))
                )
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().heightIn(max = 560.dp)
                    .verticalScroll(categoryPickerScroll).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = HisabTheme.colors.textPrimary)
                    IconButton(onClick = { showCategoryPicker = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = HisabTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
                OutlinedTextField(
                    value = categorySearchQuery,
                    onValueChange = { categorySearchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search categories...", style = MaterialTheme.typography.bodyMedium, color = HisabTheme.colors.textTertiary) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = HisabTheme.colors.textTertiary, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (categorySearchQuery.isNotEmpty()) {
                            IconButton(onClick = { categorySearchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = HisabTheme.colors.textTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = HisabTheme.colors.cardBorder,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
                if (recentForPicker.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Recent", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = HisabTheme.colors.textSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                            items(recentForPicker, key = { it.id }) { cat ->
                                CategoryStripItem(category = cat, isSelected = cat.id == selectedCategoryId, onClick = {
                                    selectedCategoryId = cat.id
                                    showCategoryPicker = false
                                })
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("All Categories", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = HisabTheme.colors.textSecondary)
                    if (searchedCategories.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text("No categories found", style = MaterialTheme.typography.bodyMedium, color = HisabTheme.colors.textTertiary)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            searchedCategories.chunked(4).forEach { row ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { cat ->
                                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                            CategoryGridPickerItem(category = cat, isSelected = cat.id == selectedCategoryId, onClick = {
                                                selectedCategoryId = cat.id
                                                showCategoryPicker = false
                                            })
                                        }
                                    }
                                    repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showAddAccountDialog) {
        AddAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onAccountAdded = { name, typeStr, bankCode, last4 ->
                onAddAccount?.invoke(name, typeStr)
                if (type == TransactionType.TRANSFER) selectedToAccount = name else selectedAccount = name
            }
        )
    }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { millis -> selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() }; showDatePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
private fun TypeToggleButton(text: String, isSelected: Boolean, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val backgroundColor by animateColorAsState(targetValue = if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent, animationSpec = tween(200), label = "typeToggleBg")
    val textColor by animateColorAsState(targetValue = if (isSelected) color else HisabTheme.colors.textSecondary, animationSpec = tween(200), label = "typeToggleText")
    Box(modifier = modifier.clip(RoundedCornerShape(10.dp)).background(backgroundColor).clickable(onClick = onClick).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = textColor)
    }
}

@Composable
private fun CategoryStripItem(category: CategoryEntity, isSelected: Boolean, onClick: () -> Unit) {
    val parsedColor = try { Color(android.graphics.Color.parseColor(category.colorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
    val borderColor = if (isSelected) parsedColor else HisabTheme.colors.cardBorder
    val container = if (isSelected) parsedColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    Box(
        modifier = Modifier.widthIn(min = 96.dp, max = 156.dp).height(44.dp).clip(RoundedCornerShape(12.dp))
            .background(container).border(width = if (isSelected) 1.dp else 0.5.dp, color = borderColor.copy(alpha = if (isSelected) 0.9f else 0.8f), shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(parsedColor.copy(alpha = if (isSelected) 0.22f else 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                val emoji = com.example.hisab.data.sms.SmsNotificationHelper.getCategoryEmoji(category.iconName)
                Text(text = emoji, fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp))
            }
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) parsedColor else HisabTheme.colors.textPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun CategoryGridPickerItem(category: CategoryEntity, isSelected: Boolean, onClick: () -> Unit) {
    val parsedColor = try { Color(android.graphics.Color.parseColor(category.colorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) parsedColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(width = if (isSelected) 1.dp else 0.5.dp, color = if (isSelected) parsedColor else HisabTheme.colors.cardBorder.copy(alpha = 0.6f), shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp).fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(parsedColor.copy(alpha = if (isSelected) 0.20f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            val emoji = com.example.hisab.data.sms.SmsNotificationHelper.getCategoryEmoji(category.iconName)
            Text(text = emoji, fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) parsedColor else HisabTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            lineHeight = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)
        )
    }
}

@Composable
private fun TextToolbarButton(text: String, onClick: () -> Unit) {
    val colors = HisabTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(150), label = "toolbarBtnBg"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary
        )
    }
}
