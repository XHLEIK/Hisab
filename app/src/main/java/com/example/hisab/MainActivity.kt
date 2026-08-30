package com.example.hisab

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hisab.data.repository.AccountRepository
import com.example.hisab.data.repository.BackupRepository
import com.example.hisab.data.repository.CategoryRepository
import com.example.hisab.data.repository.TransactionRepository
import com.example.hisab.ui.components.AutoRestoreLoadingDialog
import com.example.hisab.ui.navigation.Screen
import com.example.hisab.ui.theme.HisabAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private companion object {
        /**
         * Process-scoped so the startup SMS work runs once per launch, not once per Activity
         * instance. `LaunchedEffect(Unit)` re-runs on recomposition after a configuration change,
         * and a configuration change can recreate the Activity outright — an instance field would
         * reset and start a second inbox scan.
         */
        @Volatile
        private var smsStartupWorkStarted = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = (application as HisabApplication).database
        val autoBackupManager = com.example.hisab.data.backup.AutoBackupManager(applicationContext, database)
        val transactionRepository = TransactionRepository(database.transactionDao(), autoBackupManager)
        val categoryRepository = CategoryRepository(database.categoryDao(), autoBackupManager)
        val accountRepository = AccountRepository(database.accountDao(), database.transactionDao(), autoBackupManager)
        val backupRepository = BackupRepository(transactionRepository, categoryRepository, autoBackupManager)
        val pendingTransactionRepository = com.example.hisab.data.repository.PendingTransactionRepository(
            pendingDao = database.pendingTransactionDao(),
            transactionDao = database.transactionDao(),
            db = database,
            autoBackupManager = autoBackupManager
        )

        setContent {
            HisabAppTheme {
                var showRestoreLoading by remember { mutableStateOf(false) }
                var showNotificationCard by remember { mutableStateOf(false) }
                var showStorageCard by remember { mutableStateOf(false) }
                var showSmsCard by remember { mutableStateOf(false) }
                var showSmsRestrictedDialog by remember { mutableStateOf(false) }
                var showBackupDiscoveryCard by remember { mutableStateOf(false) }
                var backupTransactionCount by remember { mutableStateOf(0) }
                var hasCheckedPermissions by remember { mutableStateOf(false) }

                // Permission launchers — system default dialogs, sequential
                val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { _ ->
                    // Next: storage (if needed), else SMS
                    val storageState = com.example.hisab.data.permissions.PermissionCoordinator.checkStoragePermissions(applicationContext)
                    val manageState = com.example.hisab.data.permissions.PermissionCoordinator.checkManageStoragePermission(applicationContext)
                    if (storageState.first == com.example.hisab.data.permissions.PermissionStatus.DENIED ||
                        manageState == com.example.hisab.data.permissions.PermissionStatus.DENIED) {
                        showStorageCard = true
                    } else {
                        val smsState = com.example.hisab.data.permissions.PermissionCoordinator.checkSmsPermissions(applicationContext)
                        if (smsState.first != com.example.hisab.data.permissions.PermissionStatus.GRANTED) {
                            showSmsCard = true
                        }
                    }
                }

                val storageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                ) { _ ->
                    // After storage, check MANAGE (for Documents on API 30+), then SMS; also re-attempt backup restore
                    CoroutineScope(Dispatchers.IO).launch {
                        val restored = autoBackupManager.restoreIfEmpty()
                        if (restored) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(applicationContext, "Backup restored after storage permission", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    val manageStatus = com.example.hisab.data.permissions.PermissionCoordinator.checkManageStoragePermission(applicationContext)
                    if (manageStatus == com.example.hisab.data.permissions.PermissionStatus.DENIED && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        // On API 30+, Documents access via File API requires All files access — show system settings
                        showStorageCard = true
                    } else {
                        val smsState = com.example.hisab.data.permissions.PermissionCoordinator.checkSmsPermissions(applicationContext)
                        if (smsState.first != com.example.hisab.data.permissions.PermissionStatus.GRANTED) {
                            showSmsCard = true
                        }
                    }
                }

                val manageStorageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                ) { _ ->
                    // After returning from All files access settings, check if granted and re-attempt backup
                    CoroutineScope(Dispatchers.IO).launch {
                        val restored = autoBackupManager.restoreIfEmpty()
                        if (restored) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(applicationContext, "Backup restored after all-files permission", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    val smsState = com.example.hisab.data.permissions.PermissionCoordinator.checkSmsPermissions(applicationContext)
                    if (smsState.first != com.example.hisab.data.permissions.PermissionStatus.GRANTED) {
                        // Show the SMS card — it handles system dialog + restricted settings flow
                        showSmsCard = true
                    }
                }

                val smsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    val denied = result.values.any { !it }
                    if (denied) {
                        val isRestricted = !shouldShowRequestPermissionRationale(android.Manifest.permission.RECEIVE_SMS)
                        if (isRestricted) {
                            showSmsRestrictedDialog = true
                        } else {
                            // Denied but not restricted: show the card with an "Allow" retry button
                            showSmsCard = true
                        }
                    }
                    // After SMS, check backup picker for Documents on API 33+
                    if (android.os.Build.VERSION.SDK_INT > 32) {
                        CoroutineScope(Dispatchers.IO).launch {
                            // Only offer restore when the database is actually empty —
                            // suppress the card if data was already imported earlier this session.
                            val dbEmpty = database.transactionDao().getAllTransactionsSync().isEmpty()
                            if (dbEmpty) {
                                val needsPicker = com.example.hisab.data.permissions.PermissionCoordinator.isBackupAccessNeedsPicker(applicationContext)
                                if (needsPicker) {
                                    val count = try { autoBackupManager.getBackupTransactionCount(applicationContext) } catch (e: Exception) { 0 }
                                    if (count > 0) {
                                        withContext(Dispatchers.Main) {
                                            backupTransactionCount = count
                                            showBackupDiscoveryCard = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val backupPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        CoroutineScope(Dispatchers.IO).launch {
                            val result = autoBackupManager.restoreFromUri(applicationContext, it)
                            withContext(Dispatchers.Main) {
                                if (result) {
                                    Toast.makeText(applicationContext, "Backup restored successfully!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(applicationContext, "Failed to restore backup", Toast.LENGTH_LONG).show()
                                }
                                showBackupDiscoveryCard = false
                            }
                        }
                    } ?: run { showBackupDiscoveryCard = false }
                }

                LaunchedEffect(Unit) {
                    // Check permissions sequentially, only for denied — after reinstall all are denied so all popups show in order
                    if (!hasCheckedPermissions) {
                        hasCheckedPermissions = true
                        val state = com.example.hisab.data.permissions.PermissionCoordinator.checkAll(applicationContext)
                        var handled = false
                        when {
                            state.notification == com.example.hisab.data.permissions.PermissionStatus.DENIED -> {
                                showNotificationCard = true
                                handled = true
                            }
                            state.storageRead == com.example.hisab.data.permissions.PermissionStatus.DENIED -> {
                                showStorageCard = true
                                handled = true
                            }
                            // On API 30+ (Android 11+), storageRead is NOT_APPLICABLE because
                            // READ_EXTERNAL_STORAGE has maxSdkVersion=32. But the app still needs
                            // MANAGE_EXTERNAL_STORAGE (All Files Access) to read/write backup
                            // files in Documents/Hisab via the File API.
                            state.manageStorage == com.example.hisab.data.permissions.PermissionStatus.DENIED -> {
                                showStorageCard = true
                                handled = true
                            }
                            state.smsReceive == com.example.hisab.data.permissions.PermissionStatus.DENIED -> {
                                // ALWAYS fire the real Android SMS dialog first. On hard-restricted
                                // devices this request is what makes App Info show the ⋮ → "Allow
                                // restricted settings" menu; the in-app instruction card only
                // appears AFTER this system attempt fails. (A brand-new install has
                // shouldShowRationale == false too — that alone must never mean "restricted".)
                                val perms = com.example.hisab.data.permissions.PermissionCoordinator.getSmsPermissionsToRequest(applicationContext)
                                if (perms.isNotEmpty()) smsLauncher.launch(perms)
                                handled = true
                            }
                        }
                        // On API 33+, storage is NOT_APPLICABLE but Documents backup may still need SAF picker
                        if (!handled && android.os.Build.VERSION.SDK_INT > 32) {
                            CoroutineScope(Dispatchers.IO).launch {
                                if (com.example.hisab.data.permissions.PermissionCoordinator.isBackupAccessNeedsPicker(applicationContext)) {
                                    val count = try { autoBackupManager.getBackupTransactionCount(applicationContext) } catch (e: Exception) { 0 }
                                    if (count > 0 && database.transactionDao().getAllTransactionsSync().isEmpty()) {
                                        withContext(Dispatchers.Main) {
                                            backupTransactionCount = count
                                            showBackupDiscoveryCard = true
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Guarded against re-entrancy: LaunchedEffect(Unit) re-runs after a
                    // configuration change, and two concurrent scans of the same inbox would race
                    // each other through the processor's lock for no benefit.
                    if (!smsStartupWorkStarted) {
                        smsStartupWorkStarted = true
                        CoroutineScope(Dispatchers.IO).launch {
                            // INV-4/INV-6: re-post Stage-1 notifications for claims that committed
                            // but never notified (process death, or POST_NOTIFICATIONS denied at the
                            // time). Runs BEFORE the inbox scan and independently of READ_SMS — it
                            // must work on an install that only ever granted RECEIVE_SMS.
                            try {
                                val recovered = com.example.hisab.data.sms
                                    .buildTransactionProcessor(applicationContext)
                                    .recoverUnnotified()
                                if (recovered.isNotEmpty()) {
                                    android.util.Log.d(
                                        "MainActivity",
                                        "Recovered ${recovered.size} unnotified transaction(s)"
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Notification recovery failed", e)
                            }

                            com.example.hisab.data.sms.SmsCatchUpSync.runSync(applicationContext)
                        }
                    }

                    // Restore is app-private first (no permission needed) — filesDir/backups/
                    // This always runs; if backup is in Documents and needs permission/SAF, it will be handled after permission flow
                    showRestoreLoading = true
                    CoroutineScope(Dispatchers.IO).launch {
                        database.ensureDefaults()
                        val restored = autoBackupManager.restoreIfEmpty()
                        accountRepository.syncAccountNames()
                        withContext(Dispatchers.Main) {
                            showRestoreLoading = false
                            if (restored) {
                                Toast.makeText(
                                    applicationContext,
                                    "Auto-backup file detected! Financial data restored successfully.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                // If still empty and external backup may exist, offer SAF picker (API 33+ scoped storage)
                                if (database.transactionDao().getAllTransactionsSync().isEmpty()) {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val needsPicker = com.example.hisab.data.permissions.PermissionCoordinator.isBackupAccessNeedsPicker(applicationContext)
                                        if (needsPicker) {
                                            val count = try { autoBackupManager.getBackupTransactionCount(applicationContext) } catch (e: Exception) { 0 }
                                            if (count > 0) {
                                                withContext(Dispatchers.Main) {
                                                    backupTransactionCount = count
                                                    showBackupDiscoveryCard = true
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                var showRestrictedSettingsDialog by remember { mutableStateOf(false) }

                if (showRestoreLoading) {
                    AutoRestoreLoadingDialog(
                        statusMessage = "Restoring your data from the last auto-backup..."
                    )
                }

                if (showNotificationCard) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showNotificationCard = false }) {
                        com.example.hisab.ui.components.permission.NotificationPermissionCard(
                            onGrantClick = {
                                showNotificationCard = false
                                val perm = com.example.hisab.data.permissions.PermissionCoordinator.getNotificationPermissionToRequest(applicationContext)
                                if (perm.isNotEmpty()) notificationLauncher.launch(perm[0])
                            },
                            onDismiss = { showNotificationCard = false }
                        )
                    }
                }

                if (showStorageCard) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showStorageCard = false }) {
                        com.example.hisab.ui.components.permission.StoragePermissionCard(
                            onGrantClick = {
                                showStorageCard = false
                                // On API 30+ Documents/Hisab via File API requires All files access — show system Settings for it
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    val manageStatus = com.example.hisab.data.permissions.PermissionCoordinator.checkManageStoragePermission(applicationContext)
                                    if (manageStatus == com.example.hisab.data.permissions.PermissionStatus.DENIED) {
                                        try {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = android.net.Uri.parse("package:$packageName")
                                            }
                                            manageStorageLauncher.launch(intent)
                                            return@StoragePermissionCard
                                        } catch (e: Exception) {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                            manageStorageLauncher.launch(intent)
                                            return@StoragePermissionCard
                                        }
                                    }
                                }
                                val perms = com.example.hisab.data.permissions.PermissionCoordinator.getStoragePermissionsToRequest(applicationContext)
                                if (perms.isNotEmpty()) {
                                    storageLauncher.launch(perms)
                                } else {
                                    // No runtime storage perms needed (e.g., already granted or API 33+ with MediaStore) — proceed to SMS
                                    val smsState = com.example.hisab.data.permissions.PermissionCoordinator.checkSmsPermissions(applicationContext)
                                    if (smsState.first != com.example.hisab.data.permissions.PermissionStatus.GRANTED) {
                                        showSmsCard = true
                                    } else if (android.os.Build.VERSION.SDK_INT > 32) {
                                        // Check backup picker even if no storage perm needed
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                            val dbEmpty = database.transactionDao().getAllTransactionsSync().isEmpty()
                                            if (dbEmpty && com.example.hisab.data.permissions.PermissionCoordinator.isBackupAccessNeedsPicker(applicationContext)) {
                                                val count = try { autoBackupManager.getBackupTransactionCount(applicationContext) } catch (e: Exception) { 0 }
                                                if (count > 0) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    backupTransactionCount = count
                                                    showBackupDiscoveryCard = true
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            onDismiss = { showStorageCard = false }
                        )
                    }
                }

                if (showSmsCard) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showSmsCard = false }) {
                        com.example.hisab.ui.components.permission.SmsPermissionCard(
                            isRestricted = false,
                            onGrantClick = {
                                showSmsCard = false
                                val perms = com.example.hisab.data.permissions.PermissionCoordinator.getSmsPermissionsToRequest(applicationContext)
                                if (perms.isNotEmpty()) smsLauncher.launch(perms)
                            },
                            onOpenSettings = {
                                showSmsCard = false
                                showSmsRestrictedDialog = true
                            },
                            onDismiss = { showSmsCard = false }
                        )
                    }
                }

                if (showSmsRestrictedDialog) {
                    com.example.hisab.ui.components.RestrictedSettingsDialog(
                        onDismiss = { showSmsRestrictedDialog = false },
                        onOpenAppInfo = {
                            showSmsRestrictedDialog = false
                            // After user enables restricted settings and returns, re-show SMS card
                            showSmsCard = true
                        }
                    )
                }

                if (showBackupDiscoveryCard) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showBackupDiscoveryCard = false }) {
                        com.example.hisab.ui.components.permission.BackupDiscoveryCard(
                            transactionCount = backupTransactionCount,
                            onImportClick = {
                                backupPickerLauncher.launch(arrayOf("application/json"))
                            },
                            onDismiss = { showBackupDiscoveryCard = false }
                        )
                    }
                }

                if (showRestrictedSettingsDialog) {
                    com.example.hisab.ui.components.RestrictedSettingsDialog(
                        onDismiss = { showRestrictedSettingsDialog = false },
                        onOpenAppInfo = {
                            showRestrictedSettingsDialog = false
                            // After user enables restricted settings and returns, re-show SMS card
                            showSmsCard = true
                        }
                    )
                }

                HisabApp(
                    transactionRepository = transactionRepository,
                    categoryRepository = categoryRepository,
                    accountRepository = accountRepository,
                    backupRepository = backupRepository,
                    pendingTransactionRepository = pendingTransactionRepository
                )
            }
        }
    }
}

@Composable
fun HisabApp(
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    backupRepository: BackupRepository,
    pendingTransactionRepository: com.example.hisab.data.repository.PendingTransactionRepository
) {
    val hazeState = remember { HazeState() }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { com.example.hisab.ui.navigation.Screen.bottomNavItems.size }
    )
    val scope = rememberCoroutineScope()
    val currentRouteForBar = com.example.hisab.ui.navigation.Screen.bottomNavItems[pagerState.currentPage].route
    var settingsExportTrigger by remember { mutableStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // ponytail: HorizontalPager gives interactive drag — page follows finger, no jitter; beyondViewport keeps adjacent page composed
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .haze(hazeState),
                beyondViewportPageCount = 1,
                key = { com.example.hisab.ui.navigation.Screen.bottomNavItems[it].route }
            ) { page ->
                when (com.example.hisab.ui.navigation.Screen.bottomNavItems[page]) {
                    is com.example.hisab.ui.navigation.Screen.Dashboard -> com.example.hisab.ui.screens.dashboard.DashboardScreen(
                        transactionRepository = transactionRepository,
                        categoryRepository = categoryRepository,
                        accountRepository = accountRepository,
                        pendingTransactionRepository = pendingTransactionRepository,
                        onSeeAllTransactions = {
                            scope.launch { pagerState.animateScrollToPage(com.example.hisab.ui.navigation.Screen.bottomNavItems.indexOf(com.example.hisab.ui.navigation.Screen.History)) }
                        },
                        onNavigateToExport = {
                            scope.launch {
                                val settingsIdx = com.example.hisab.ui.navigation.Screen.bottomNavItems.indexOf(com.example.hisab.ui.navigation.Screen.Settings)
                                pagerState.animateScrollToPage(settingsIdx)
                                // Trigger scroll inside Settings to make Export visible; no auto-open dialog
                                settingsExportTrigger++
                            }
                        }
                    )
                    is com.example.hisab.ui.navigation.Screen.Analytics -> com.example.hisab.ui.screens.analytics.AnalyticsScreen(
                        transactionRepository = transactionRepository,
                        categoryRepository = categoryRepository,
                        accountRepository = accountRepository
                    )
                    is com.example.hisab.ui.navigation.Screen.History -> com.example.hisab.ui.screens.history.HistoryScreen(
                        transactionRepository = transactionRepository,
                        categoryRepository = categoryRepository,
                        accountRepository = accountRepository
                    )
                    is com.example.hisab.ui.navigation.Screen.Settings -> com.example.hisab.ui.screens.settings.SettingsScreen(
                        categoryRepository = categoryRepository,
                        accountRepository = accountRepository,
                        backupRepository = backupRepository,
                        scrollToExportTrigger = settingsExportTrigger
                    )
                }
            }

            com.example.hisab.ui.components.FloatingGlassmorphicBottomBar(
                currentDestination = null,
                selectedRoute = currentRouteForBar,
                hazeState = hazeState,
                onNavigate = { screen ->
                    val idx = com.example.hisab.ui.navigation.Screen.bottomNavItems.indexOf(screen)
                    if (idx != -1 && idx != pagerState.currentPage) scope.launch { pagerState.animateScrollToPage(idx) }
                },
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            )
        }
    }
}