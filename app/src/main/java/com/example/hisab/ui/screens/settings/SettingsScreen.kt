package com.example.hisab.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.export.ExportFormat
import com.example.hisab.data.model.TransactionType
import com.example.hisab.data.repository.AccountRepository
import com.example.hisab.data.repository.BackupRepository
import com.example.hisab.data.repository.CategoryRepository
import com.example.hisab.data.sms.PrefsSmsDiagnosticsLog
import com.example.hisab.data.sms.SmsDiagnosticEntry
import com.example.hisab.ui.components.AddAccountDialog
import com.example.hisab.ui.components.CategoryEditDialog
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.CategoryIconMapper
import com.example.hisab.util.CurrencyFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import androidx.compose.foundation.layout.statusBarsPadding

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

    // Collapsible Accordion States
    var isIncomeExpanded by remember { mutableStateOf(true) }
    var isExpenseExpanded by remember { mutableStateOf(true) }
    var isTransferExpanded by remember { mutableStateOf(true) }

    // Action Modal States
    var selectedCategoryForActions by remember { mutableStateOf<CategoryEntity?>(null) }
    var selectedAccountForActions by remember { mutableStateOf<AccountEntity?>(null) }
    var bankSelectionAccount by remember { mutableStateOf<AccountEntity?>(null) }

    var categoryToEdit by remember { mutableStateOf<CategoryEntity?>(null) }
    var showAddCategoryType by remember { mutableStateOf<TransactionType?>(null) }

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<AccountEntity?>(null) }
    var accountToDelete by remember { mutableStateOf<AccountEntity?>(null) }
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    var selectedExportFormat by remember { mutableStateOf<ExportFormat?>(null) }
    var showExportFormatDialog by remember { mutableStateOf(false) }
    var showOpenSourceDialog by remember { mutableStateOf(false) }

    // Hidden SMS diagnostics viewer: five taps on the version line, the usual Android build-number
    // gesture. Kept out of the way because it is a debugging aid for one reported class of bug, not a
    // feature — but reachable without a debug build, because the bug only shows up on the user's phone.
    var versionTapCount by remember { mutableStateOf(0) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var showPrivacyNoticeDialog by remember { mutableStateOf(false) }
    var showUserAgreementDialog by remember { mutableStateOf(false) }
    var showRestrictedSettingsDialog by remember { mutableStateOf(false) }

    val backupPrefs = remember { com.example.hisab.data.backup.BackupPreferences(context) }
    val isAutoBackupEnabled by backupPrefs.isAutoBackupEnabled.collectAsState(initial = true)

    val availableExportMonths by viewModel.availableExportMonths.collectAsState()
    var selectedExportMonth by remember { mutableStateOf<java.time.YearMonth?>(null) }

    // SAF file pickers
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(selectedExportFormat?.mimeType ?: "application/pdf")
    ) { uri ->
        val format = selectedExportFormat ?: ExportFormat.PDF
        uri?.let { viewModel.exportReport(context, it, format, selectedExportMonth) }
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
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }

            // ── 1. Accounts Management Section (Sleek Fintech Cards) ─────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                AccountCard(
                    account = account,
                    onClick = { selectedAccountForActions = account }
                )
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ── 2. Collapsible Income Categories ────────────────
            item {
                CategoryAccordionHeader(
                    icon = Icons.Outlined.Category,
                    title = "Income Categories",
                    count = incomeCategories.size,
                    isExpanded = isIncomeExpanded,
                    onToggle = { isIncomeExpanded = !isIncomeExpanded }
                )
            }

            item {
                AnimatedVisibility(
                    visible = isIncomeExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    CategoryGrid(
                        categories = incomeCategories,
                        onCategoryClick = { selectedCategoryForActions = it },
                        onAddClick = { showAddCategoryType = TransactionType.INCOME }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // ── 3. Collapsible Expense Categories ───────────────
            item {
                CategoryAccordionHeader(
                    icon = Icons.Outlined.Category,
                    title = "Expense Categories",
                    count = expenseCategories.size,
                    isExpanded = isExpenseExpanded,
                    onToggle = { isExpenseExpanded = !isExpenseExpanded }
                )
            }

            item {
                AnimatedVisibility(
                    visible = isExpenseExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    CategoryGrid(
                        categories = expenseCategories,
                        onCategoryClick = { selectedCategoryForActions = it },
                        onAddClick = { showAddCategoryType = TransactionType.EXPENSE }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // ── 4. Collapsible Transfer Categories ──────────────
            item {
                CategoryAccordionHeader(
                    icon = Icons.Outlined.Category,
                    title = "Transfer Categories",
                    count = transferCategories.size,
                    isExpanded = isTransferExpanded,
                    onToggle = { isTransferExpanded = !isTransferExpanded }
                )
            }

            item {
                AnimatedVisibility(
                    visible = isTransferExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    CategoryGrid(
                        categories = transferCategories,
                        onCategoryClick = { selectedCategoryForActions = it },
                        onAddClick = { showAddCategoryType = TransactionType.TRANSFER }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ── Data & Backup Section ────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    SectionTitle(icon = Icons.Outlined.FolderOpen, title = "Data & Backup")
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceCard)
                        .border(0.5.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                ) {
                    Column {
                        // 1. Auto Backup Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Sync,
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

                        androidx.compose.material3.HorizontalDivider(
                            color = colors.cardBorder.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )

                        // 2. Export Report Row
                        SettingsItem(
                            icon = Icons.Filled.FileDownload,
                            title = "Export Report",
                            subtitle = "Generate professional balance sheet (PDF, XLSX, CSV, JSON)",
                            onClick = { showExportFormatDialog = true }
                        )

                        androidx.compose.material3.HorizontalDivider(
                            color = colors.cardBorder.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )

                        // 3. Import Backup Row
                        SettingsItem(
                            icon = Icons.Filled.FileUpload,
                            title = "Import Backup",
                            subtitle = "Restore transactions from a JSON or CSV backup file",
                            onClick = {
                                viewModel.smartImportBackup(context) {
                                    importLauncher.launch(arrayOf("application/json", "text/csv", "text/comma-separated-values", "*/*"))
                                }
                            }
                        )

                        androidx.compose.material3.HorizontalDivider(
                            color = colors.cardBorder.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )

                        // 4. Restricted Settings & SMS Logging Row
                        SettingsItem(
                            icon = Icons.Filled.Security,
                            title = "App Info & Restricted Settings",
                            subtitle = "Allow restricted settings on Android 13+ for 1-tap bank SMS logging",
                            onClick = { showRestrictedSettingsDialog = true }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ── About & Credits Section ────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    SectionTitle(icon = Icons.Outlined.Code, title = "About & Credits")
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceCard)
                        .border(0.5.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                        .padding(vertical = 4.dp)
                ) {
                    Column {
                        // Header App Info Row
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    versionTapCount++
                                    if (versionTapCount >= VERSION_TAPS_FOR_DIAGNOSTICS) {
                                        showDiagnosticsDialog = true
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Hisab v${com.example.hisab.util.AppVersion.name(context)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                com.example.hisab.util.AppVersion.code(context)?.let { buildNumber ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Build $buildNumber",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Hisab is a privacy-first, offline personal finance tracker designed for modern budget management.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }

                        androidx.compose.material3.HorizontalDivider(
                            color = colors.cardBorder.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )

                        // Revealed by the version-line gesture above, and it stays for the session so
                        // the user isn't asked to tap five times again on the way back.
                        if (versionTapCount >= VERSION_TAPS_FOR_DIAGNOSTICS) {
                            SettingsItem(
                                icon = Icons.Filled.BugReport,
                                title = "SMS Auto-Logging Diagnostics",
                                subtitle = "The last ${PrefsSmsDiagnosticsLog.CAPACITY} decisions the SMS pipeline made",
                                onClick = { showDiagnosticsDialog = true }
                            )

                            androidx.compose.material3.HorizontalDivider(
                                color = colors.cardBorder.copy(alpha = 0.5f),
                                thickness = 0.5.dp
                            )
                        }

                        // Developer Credit Item
                        SettingsItem(
                            icon = Icons.Filled.Code,
                            title = "Developer Credit",
                            subtitle = "Subham Bose  •  GitHub: @XHLEIK",
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/XHLEIK"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback
                                }
                            }
                        )

                        androidx.compose.material3.HorizontalDivider(
                            color = colors.cardBorder.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )

                        // Open Source Licenses Item
                        SettingsItem(
                            icon = Icons.Filled.Terminal,
                            title = "Open Source Licenses",
                            subtitle = "Apache License 2.0, Jetpack Compose, Room, Kotlin Coroutines",
                            onClick = { showOpenSourceDialog = true }
                        )

                        androidx.compose.material3.HorizontalDivider(
                            color = colors.cardBorder.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )

                        // Privacy Notice Item
                        SettingsItem(
                            icon = Icons.Filled.Shield,
                            title = "Privacy Notice",
                            subtitle = "100% Offline Policy, Zero Network Tracking, Local Safety",
                            onClick = { showPrivacyNoticeDialog = true }
                        )

                        androidx.compose.material3.HorizontalDivider(
                            color = colors.cardBorder.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )

                        // User Agreement Item
                        SettingsItem(
                            icon = Icons.Filled.Gavel,
                            title = "User Agreement",
                            subtitle = "End-User Terms, Local Backup Ownership & Usage Rules",
                            onClick = { showUserAgreementDialog = true }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(115.dp)) }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // ── Account Action Modal (No Set as Primary) ─────────────────────────
    if (selectedAccountForActions != null) {
        val account = selectedAccountForActions!!
        AlertDialog(
            onDismissRequest = { selectedAccountForActions = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = CategoryIconMapper.getAccountIcon(account.name),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = account.name, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val acc = account
                            selectedAccountForActions = null
                            accountToEdit = acc
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Account Details", fontWeight = FontWeight.Bold)
                        }
                    }

                    val isBankLinked = !account.bankCode.isNullOrEmpty()
                    OutlinedButton(
                        onClick = {
                            val acc = account
                            selectedAccountForActions = null
                            if (isBankLinked) {
                                viewModel.updateAccountBankMapping(acc, null, null)
                            } else {
                                bankSelectionAccount = acc
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isBankLinked) Icons.Filled.LinkOff else Icons.Filled.Sms,
                                contentDescription = null,
                                tint = if (isBankLinked) colors.expense else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBankLinked) "Unlink Bank Account" else "Link Bank for SMS Auto-Detect",
                                color = if (isBankLinked) colors.expense else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!account.isPrimary) {
                        TextButton(
                            onClick = {
                                val acc = account
                                selectedAccountForActions = null
                                accountToDelete = acc
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.expense, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete Account", color = colors.expense, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedAccountForActions = null }) {
                    Text("Close")
                }
            }
        )
    }

    // ── Category Action Modal ─────────────────────────────────────────────
    if (selectedCategoryForActions != null) {
        val category = selectedCategoryForActions!!
        AlertDialog(
            onDismissRequest = { selectedCategoryForActions = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val emoji = com.example.hisab.data.sms.SmsNotificationHelper.getCategoryEmoji(category.iconName)
                        Text(
                            text = emoji,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = category.name, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val cat = category
                            selectedCategoryForActions = null
                            categoryToEdit = cat
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Category", fontWeight = FontWeight.Bold)
                        }
                    }

                    TextButton(
                        onClick = {
                            val cat = category
                            selectedCategoryForActions = null
                            categoryToDelete = cat
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.expense, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Category", color = colors.expense, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCategoryForActions = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (bankSelectionAccount != null) {
        com.example.hisab.ui.components.BankSelectionSheet(
            account = bankSelectionAccount!!,
            onDismiss = { bankSelectionAccount = null },
            onSaveBankMapping = { acc, bankCode, accountLast4 ->
                viewModel.updateAccountBankMapping(acc, bankCode, accountLast4)
            }
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
            onAccountAdded = { name, typeStr, bankCode, last4 ->
                viewModel.addAccount(name, typeStr, bankCode, last4)
            }
        )
    }

    // Export Format & Scope Dialog
    if (showExportFormatDialog) {
        ExportFormatDialog(
            availableMonths = availableExportMonths,
            onDismiss = { showExportFormatDialog = false },
            onConfirm = { format, targetMonth ->
                showExportFormatDialog = false
                selectedExportFormat = format
                selectedExportMonth = targetMonth
                val defaultFilename = if (targetMonth != null) {
                    val monthStr = targetMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMM_yyyy"))
                    "Hisab_Statement_${monthStr}.${format.extension}"
                } else {
                    val timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd").format(java.time.LocalDate.now())
                    "Hisab_Statement_All_Records_${timestamp}.${format.extension}"
                }
                exportLauncher.launch(defaultFilename)
            }
        )
    }

    if (showRestrictedSettingsDialog) {
        com.example.hisab.ui.components.RestrictedSettingsDialog(
            onDismiss = { showRestrictedSettingsDialog = false },
            onOpenAppInfo = { showRestrictedSettingsDialog = false }
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
            title = { Text("Edit Account Details", fontWeight = FontWeight.Bold) },
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

    // Open Source Licenses Dialog
    if (showOpenSourceDialog) {
        AlertDialog(
            onDismissRequest = { showOpenSourceDialog = false },
            title = { Text("Open Source Licenses", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Apache License, Version 2.0",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Copyright 2026 Subham Bose (@XHLEIK)\n\n" +
                               "Licensed under the Apache License, Version 2.0 (the \"License\"); " +
                               "you may not use this software except in compliance with the License. " +
                               "You may obtain a copy of the License at:\n" +
                               "http://www.apache.org/licenses/LICENSE-2.0\n\n" +
                               "Unless required by applicable law or agreed to in writing, software " +
                               "distributed under the License is distributed on an \"AS IS\" BASIS, " +
                               "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. " +
                               "See the License for the specific language governing permissions and " +
                               "limitations under the License.\n\n" +
                               "Third-Party Open Source Libraries & Frameworks:\n" +
                               "• Android Jetpack Compose (Apache 2.0)\n" +
                               "• Room Persistence Database (Apache 2.0)\n" +
                               "• Kotlin Coroutines & Flow (Apache 2.0)\n" +
                               "• Gson JSON Library (Apache 2.0)\n" +
                               "• Apache POI Excel Library (Apache 2.0)\n" +
                               "• Material Design 3 Components (Apache 2.0)",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showOpenSourceDialog = false }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // SMS Auto-Logging Diagnostics Dialog (hidden behind the version-line gesture)
    if (showDiagnosticsDialog) {
        SmsDiagnosticsDialog(onDismiss = { showDiagnosticsDialog = false })
    }

    // Privacy Notice Dialog
    if (showPrivacyNoticeDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyNoticeDialog = false },
            title = { Text("Privacy Notice", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "100% Offline & Local Data Privacy",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Zero Network Data Collection: Hisab operates completely offline without internet permissions, analytics trackers, advertising identifiers, or third-party telemetry. No personal or financial data ever leaves your device.\n\n" +
                               "2. Local Storage Safety: All income, expense, account balances, and budget records are stored strictly on your local device inside a sandboxed SQLite database.\n\n" +
                               "3. User Backup Control: Auto-backup and exported files stored in local storage (Documents/Hisab/) remain entirely under your manual control.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyNoticeDialog = false }) {
                    Text("I Understand", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // User Agreement Dialog
    if (showUserAgreementDialog) {
        AlertDialog(
            onDismissRequest = { showUserAgreementDialog = false },
            title = { Text("User Agreement", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "End-User Terms & Ownership Agreement",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Financial Records Ownership: You retain 100% ownership, control, and responsibility over all financial records and data entered into Hisab.\n\n" +
                               "2. Local Backup Responsibility: Because Hisab is 100% offline and serverless, you are responsible for maintaining local backup files (Documents/Hisab/) before uninstalling the application or resetting your device.\n\n" +
                               "3. Limitation of Liability & Warranty: Hisab is provided 'AS IS' under the Apache License 2.0, without implied warranties of any kind. The developer (Subham Bose) is not liable for data loss arising from hardware failure, OS resets, or manual file deletion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showUserAgreementDialog = false }) {
                    Text("I Understand", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun CategoryGrid(
    categories: List<CategoryEntity>,
    onCategoryClick: (CategoryEntity) -> Unit,
    onAddClick: () -> Unit
) {
    val items = remember(categories) {
        categories.map { CategoryItem.Category(it) } + listOf(CategoryItem.Add)
    }
    val rows = remember(items) { items.chunked(4) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        when (item) {
                            is CategoryItem.Category -> {
                                CategorySquareTile(
                                    category = item.category,
                                    onClick = { onCategoryClick(item.category) }
                                )
                            }
                            is CategoryItem.Add -> {
                                AddCategorySquareTile(
                                    onClick = onAddClick
                                )
                            }
                        }
                    }
                }
                if (rowItems.size < 4) {
                    repeat(4 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private sealed class CategoryItem {
    data class Category(val category: CategoryEntity) : CategoryItem()
    object Add : CategoryItem()
}

@Composable
private fun CategoryAccordionHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val colors = HisabTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Icon(
            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = colors.textSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun CategorySquareTile(
    category: CategoryEntity,
    onClick: () -> Unit
) {
    val colors = HisabTheme.colors
    val defaultColor = MaterialTheme.colorScheme.primary
    val parsedColor = remember(category, defaultColor) {
        try {
            Color(android.graphics.Color.parseColor(category.colorHex))
        } catch (e: Exception) {
            defaultColor
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceCard)
            .border(0.5.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(parsedColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                val emoji = com.example.hisab.data.sms.SmsNotificationHelper.getCategoryEmoji(category.iconName)
                Text(
                    text = emoji,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = category.name,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AddCategorySquareTile(
    onClick: () -> Unit
) {
    val colors = HisabTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.innerSurface)
            .border(0.5.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.cardBorder.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add Category",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Add",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: AccountEntity,
    onClick: () -> Unit
) {
    val colors = HisabTheme.colors
    val defaultPrimaryColor = MaterialTheme.colorScheme.primary
    val parsedColor = remember(account, defaultPrimaryColor) {
        try {
            if (!account.colorHex.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(account.colorHex))
            } else {
                val lower = account.name.lowercase()
                when {
                    lower.contains("primary") -> Color(0xFF10B981)
                    lower.contains("secondary") -> Color(0xFF3B82F6)
                    lower.contains("savings") || lower.contains("saving") -> Color(0xFFF59E0B)
                    lower.contains("cash") -> Color(0xFF8B5CF6)
                    else -> Color(0xFF14B8A6)
                }
            }
        } catch (e: Exception) {
            defaultPrimaryColor
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceCard)
            .border(0.5.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(parsedColor.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CategoryIconMapper.getAccountIcon(account.name),
                        contentDescription = account.name,
                        tint = parsedColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (!account.bankCode.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Sms,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Linked: ${account.bankCode}${if (!account.accountLast4.isNullOrBlank()) " (**${account.accountLast4})" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981)
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Sms,
                                contentDescription = null,
                                tint = colors.textTertiary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Unlinked (Tap to Link Bank)",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!account.bankCode.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = account.bankCode!!,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                if (account.isPrimary) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "PRIMARY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        }
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
private fun ExportFormatDialog(
    availableMonths: List<ExportMonthOption>,
    onDismiss: () -> Unit,
    onConfirm: (format: ExportFormat, targetMonth: java.time.YearMonth?) -> Unit
) {
    var isAllRecords by remember { mutableStateOf(true) }
    var selectedMonth by remember { mutableStateOf(availableMonths.firstOrNull()?.yearMonth) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }
    val colors = HisabTheme.colors

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
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Scope Selection Section
                Text(
                    text = "1. Report Scope",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Option A: All Records
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isAllRecords = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isAllRecords,
                            onClick = { isAllRecords = true }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "All Records",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Complete all-time financial ledger",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }
                    }

                    // Option B: Specific Month
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                isAllRecords = false
                                if (selectedMonth == null && availableMonths.isNotEmpty()) {
                                    selectedMonth = availableMonths.first().yearMonth
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isAllRecords,
                            onClick = {
                                isAllRecords = false
                                if (selectedMonth == null && availableMonths.isNotEmpty()) {
                                    selectedMonth = availableMonths.first().yearMonth
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Specific Month Report",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Export transactions for a single month",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }
                    }

                    // Month Picker Sub-List (Only visible when Specific Month is selected)
                    if (!isAllRecords) {
                        if (availableMonths.isEmpty()) {
                            Text(
                                text = "No transaction records found to export.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.expense,
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 28.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                availableMonths.forEach { monthOption ->
                                    val isSelected = (selectedMonth == monthOption.yearMonth)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else Color.Transparent
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedMonth = monthOption.yearMonth }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = monthOption.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else colors.textPrimary
                                        )
                                        Text(
                                            text = "${monthOption.count} txns",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Format Selection Section
                Text(
                    text = "2. File Format",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ExportFormat.values().forEach { format ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedFormat = format }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedFormat == format),
                                onClick = { selectedFormat = format }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = format.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedFormat == format) FontWeight.Bold else FontWeight.Normal,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetMonth = if (isAllRecords) null else selectedMonth
                    onConfirm(selectedFormat, targetMonth)
                },
                enabled = isAllRecords || (selectedMonth != null && availableMonths.isNotEmpty()),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Export Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/** Taps on the version line that reveal the SMS diagnostics log. */
private const val VERSION_TAPS_FOR_DIAGNOSTICS = 5

/**
 * Shows the SMS pipeline's decision log, newest first.
 *
 * This exists because the defect that prompted the v3.2.1 hardening — "bank SMS arrive but sometimes
 * no notification appears" — was undiagnosable from the outside: nine different paths could end a
 * message in silence and none of them left a trace. Each line here names the outcome and the reason,
 * so the same report next time points at a gate instead of a guess.
 */
@Composable
private fun SmsDiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = HisabTheme.colors
    val scope = rememberCoroutineScope()
    val log = remember { PrefsSmsDiagnosticsLog(context) }

    var entries by remember { mutableStateOf<List<SmsDiagnosticEntry>>(emptyList()) }
    var reloadToken by remember { mutableStateOf(0) }

    LaunchedEffect(reloadToken) {
        entries = withContext(Dispatchers.IO) { log.recent() }
    }

    val stamp = remember { DateTimeFormatter.ofPattern("dd MMM HH:mm:ss") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SMS Auto-Logging Diagnostics", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                if (entries.isEmpty()) {
                    Text(
                        text = "Nothing logged yet. Entries appear here as bank messages are " +
                            "processed — one line per message, whatever the outcome.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                } else {
                    Text(
                        text = "Newest first. \"NOTIFIED\" means the notification was accepted by " +
                            "Android, which is not the same as you having seen it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    entries.forEach { entry ->
                        val when0 = Instant.ofEpochMilli(entry.timestamp)
                            .atZone(ZoneId.systemDefault()).format(stamp)
                        val amount = entry.amount
                            ?.let { CurrencyFormatter.format(it) }
                            ?: "—"
                        Text(
                            text = "$when0  •  ${entry.sender}  •  $amount",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary
                        )
                        Text(
                            text = buildString {
                                append(entry.outcome)
                                append("  [")
                                append(entry.origin)
                                append(']')
                                entry.reason?.let { append("  —  ").append(it) }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = when (entry.outcome) {
                                "NOTIFIED", "MERGED", "RECOVERED" -> Color(0xFF10B981)
                                "FAILED", "CLAIMED_NOT_NOTIFIED", "RECOVERY_FAILED" -> Color(0xFFEF4444)
                                else -> colors.textSecondary
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (entries.isNotEmpty()) {
                TextButton(
                    onClick = {
                        // Clear off the main thread, bump the token back on it: the token drives
                        // recomposition, so it belongs where Compose expects state writes.
                        scope.launch {
                            withContext(Dispatchers.IO) { log.clear() }
                            reloadToken++
                        }
                    }
                ) {
                    Text("Clear log")
                }
            }
        }
    )
}
