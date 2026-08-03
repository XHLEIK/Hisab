package com.example.hisab.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.model.TransactionType
import com.example.hisab.data.repository.AccountRepository
import com.example.hisab.data.repository.BackupRepository
import com.example.hisab.data.repository.CategoryRepository
import com.example.hisab.ui.components.AddAccountDialog
import com.example.hisab.ui.components.CategoryEditDialog
import com.example.hisab.ui.theme.HisabTheme
import java.time.LocalDate

import com.example.hisab.data.export.ExportFormat

@Composable
fun SettingsScreen(
    categoryRepository: CategoryRepository,
    backupRepository: BackupRepository,
    accountRepository: AccountRepository? = null
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(categoryRepository, backupRepository, accountRepository)
    )

    val context = LocalContext.current
    val colors = HisabTheme.colors
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()
    val importResult by viewModel.importResult.collectAsState()

    val incomeCategories = remember(categories) { categories.filter { it.type == TransactionType.INCOME } }
    val expenseCategories = remember(categories) { categories.filter { it.type == TransactionType.EXPENSE } }
    val transferCategories = remember(categories) { categories.filter { it.type == TransactionType.TRANSFER } }

    val snackbarHostState = remember { SnackbarHostState() }

    var categoryToEdit by remember { mutableStateOf<CategoryEntity?>(null) }
    var showAddCategoryType by remember { mutableStateOf<TransactionType?>(null) }

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<AccountEntity?>(null) }
    var accountToDelete by remember { mutableStateOf<AccountEntity?>(null) }
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    var selectedExportFormat by remember { mutableStateOf<ExportFormat?>(null) }
    var showExportFormatDialog by remember { mutableStateOf(false) }

    val backupPrefs = remember { com.example.hisab.data.backup.BackupPreferences(context) }
    val isAutoBackupEnabled by backupPrefs.isAutoBackupEnabled.collectAsState(initial = true)

    // SAF file pickers
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(selectedExportFormat?.mimeType ?: "application/pdf")
    ) { uri ->
        val format = selectedExportFormat ?: ExportFormat.PDF
        uri?.let { viewModel.exportReport(context, it, format) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importBackup(context, it) }
    }

    // Show results as toast
    LaunchedEffect(exportResult) {
        exportResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(importResult) {
        importResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ── Header ───────────────────────────────
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }

            // ── Account Management Section ─────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(icon = Icons.Filled.CreditCard, title = "Accounts Management")
                    TextButton(onClick = { showAddAccountDialog = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Account", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(accounts, key = { "acc_${it.id}" }) { account ->
                AccountRow(
                    account = account,
                    onEdit = { accountToEdit = account },
                    onDelete = { accountToDelete = account }
                )
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ── Categories Management Section ────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(icon = Icons.Outlined.Category, title = "Income Categories")
                    TextButton(onClick = { showAddCategoryType = TransactionType.INCOME }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(incomeCategories, key = { "inc_${it.id}" }) { category ->
                CategoryRow(
                    category = category,
                    onEdit = { categoryToEdit = category },
                    onDelete = { categoryToDelete = category }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(icon = Icons.Outlined.Category, title = "Expense Categories")
                    TextButton(onClick = { showAddCategoryType = TransactionType.EXPENSE }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(expenseCategories, key = { "exp_${it.id}" }) { category ->
                CategoryRow(
                    category = category,
                    onEdit = { categoryToEdit = category },
                    onDelete = { categoryToDelete = category }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(icon = Icons.Outlined.Category, title = "Transfer Categories")
                    TextButton(onClick = { showAddCategoryType = TransactionType.TRANSFER }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(transferCategories, key = { "trf_${it.id}" }) { category ->
                CategoryRow(
                    category = category,
                    onEdit = { categoryToEdit = category },
                    onDelete = { categoryToDelete = category }
                )
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ── Data & Backup Section ────────────────
            item {
                SectionTitle(icon = Icons.Outlined.FolderOpen, title = "Data & Backup")
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Auto Backup",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Saves to Documents/Hisab/ after every transaction",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                    androidx.compose.material3.Switch(
                        checked = isAutoBackupEnabled,
                        onCheckedChange = { viewModel.setAutoBackupEnabled(context, it) }
                    )
                }
            }

            item {
                SettingsItem(
                    icon = Icons.Filled.CloudUpload,
                    title = "Export Report",
                    subtitle = "Generate professional balance sheet (PDF, XLSX, CSV, JSON)",
                    onClick = { showExportFormatDialog = true }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Filled.CloudDownload,
                    title = "Import Backup",
                    subtitle = "Restore transactions from a JSON or CSV backup file",
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/csv", "text/comma-separated-values", "*/*"))
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ── About Section ────────────────────────
            item {
                SectionTitle(icon = Icons.Outlined.Code, title = "About")
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Hisab v1.0",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hisab is a personal finance tracking application designed to simplify tracking income and expenses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Add Category Dialog
    if (showAddCategoryType != null) {
        CategoryEditDialog(
            initialType = showAddCategoryType!!,
            onDismiss = { showAddCategoryType = null },
            onSave = { name, type, iconName, colorHex ->
                viewModel.addCategory(name, type, iconName, colorHex)
            }
        )
    }

    // Edit Category Dialog
    if (categoryToEdit != null) {
        CategoryEditDialog(
            category = categoryToEdit,
            onDismiss = { categoryToEdit = null },
            onSave = { name, type, iconName, colorHex ->
                val updated = categoryToEdit!!.copy(
                    name = name,
                    type = type,
                    iconName = iconName,
                    colorHex = colorHex
                )
                viewModel.updateCategory(updated)
            }
        )
    }

    // Add Account Dialog
    if (showAddAccountDialog) {
        AddAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onAccountAdded = { name, typeStr ->
                viewModel.addAccount(name, typeStr)
            }
        )
    }

    // Export Format Dialog
    if (showExportFormatDialog) {
        ExportFormatDialog(
            onDismiss = { showExportFormatDialog = false },
            onFormatSelected = { format ->
                showExportFormatDialog = false
                selectedExportFormat = format
                val timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss").format(java.time.LocalDateTime.now())
                val filename = "hisab_report_${timestamp}.${format.extension}"
                exportLauncher.launch(filename)
            }
        )
    }

    // Confirm Delete Category Dialog
    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category?") },
            text = { Text("Are you sure you want to delete category '${categoryToDelete!!.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCategory(categoryToDelete!!)
                        categoryToDelete = null
                    }
                ) {
                    Text("Delete", color = colors.expense, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Edit Account Name Dialog
    if (accountToEdit != null) {
        var editedAccountName by remember(accountToEdit) { mutableStateOf(accountToEdit!!.name) }
        AlertDialog(
            onDismissRequest = { accountToEdit = null },
            title = { Text("Edit Account Name", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = editedAccountName,
                        onValueChange = { editedAccountName = it },
                        label = { Text("Account Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editedAccountName.isNotBlank()) {
                            val updated = accountToEdit!!.copy(name = editedAccountName.trim())
                            viewModel.updateAccount(accountToEdit!!.name, updated)
                            accountToEdit = null
                        }
                    },
                    enabled = editedAccountName.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToEdit = null }) { Text("Cancel") }
            }
        )
    }

    // Confirm Delete Account Dialog
    if (accountToDelete != null) {
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Delete Account?") },
            text = { Text("Are you sure you want to delete account '${accountToDelete!!.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(accountToDelete!!)
                        accountToDelete = null
                    }
                ) {
                    Text("Delete", color = colors.expense, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    val colors = HisabTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = HisabTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = HisabTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = com.example.hisab.util.CategoryIconMapper.getAccountIcon(account.name),
                contentDescription = account.name,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                if (account.isPrimary) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(Primary)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = account.type,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )
        }

        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit account name",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }

        if (!account.isPrimary) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete account",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: CategoryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = HisabTheme.colors
    val parsedColor = try {
        Color(android.graphics.Color.parseColor(category.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(parsedColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = com.example.hisab.util.CategoryIconMapper.getIcon(category.iconName),
                contentDescription = category.name,
                tint = parsedColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit category",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete category",
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ExportFormatDialog(
    onDismiss: () -> Unit,
    onFormatSelected: (ExportFormat) -> Unit
) {
    val colors = HisabTheme.colors
    val formats = ExportFormat.entries
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Export Financial Report",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select your desired report format:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )

                formats.forEach { fmt ->
                    val isSelected = fmt == selectedFormat
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else colors.cardBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedFormat = fmt }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = isSelected,
                            onClick = { selectedFormat = fmt }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = fmt.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else colors.textPrimary
                            )
                            Text(
                                text = "Format: .${fmt.extension}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = { onFormatSelected(selectedFormat) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Generate Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
