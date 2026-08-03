package com.example.hisab

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.hisab.data.repository.AccountRepository
import com.example.hisab.data.repository.BackupRepository
import com.example.hisab.data.repository.CategoryRepository
import com.example.hisab.data.repository.TransactionRepository
import com.example.hisab.ui.components.AutoRestoreLoadingDialog
import com.example.hisab.ui.components.StoragePermissionDialog
import com.example.hisab.ui.navigation.HisabNavHost
import com.example.hisab.ui.navigation.Screen
import com.example.hisab.ui.theme.HisabAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var onPermissionResultCallback: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onPermissionResultCallback?.invoke()
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

        setContent {
            HisabAppTheme {
                var showPermissionDialog by remember { mutableStateOf(false) }
                var showRestoreLoading by remember { mutableStateOf(false) }

                fun requestSystemPermissions() {
                    onPermissionResultCallback = {
                        showPermissionDialog = false
                        // Trigger backup scan after permissions granted
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
                                }
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            if (!Environment.isExternalStorageManager()) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:$packageName")
                                }
                                startActivity(intent)
                            }
                        } catch (e: Exception) {
                            // Fallback
                        }
                        permissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        )
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    val hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Environment.isExternalStorageManager() ||
                                checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    } else {
                        checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    }

                    if (!hasStoragePermission) {
                        showPermissionDialog = true
                    } else {
                        // Scan for backup file with loading modal
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
                                }
                            }
                        }
                    }
                }

                if (showPermissionDialog) {
                    StoragePermissionDialog(
                        onGrantRequested = {
                            requestSystemPermissions()
                        },
                        onDismiss = {
                            showPermissionDialog = false
                            // Initialize default database without restore
                            CoroutineScope(Dispatchers.IO).launch {
                                database.ensureDefaults()
                                accountRepository.syncAccountNames()
                            }
                        }
                    )
                }

                if (showRestoreLoading) {
                    AutoRestoreLoadingDialog(
                        statusMessage = "Scanning Documents/Hisab for auto-backup file..."
                    )
                }

                HisabApp(
                    transactionRepository = transactionRepository,
                    categoryRepository = categoryRepository,
                    accountRepository = accountRepository,
                    backupRepository = backupRepository
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
    backupRepository: BackupRepository
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp
            ) {
                Screen.bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == screen.route
                    } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon
                                else screen.unselectedIcon,
                                contentDescription = screen.label
                            )
                        },
                        label = {
                            Text(
                                text = screen.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold
                                else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        HisabNavHost(
            navController = navController,
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            accountRepository = accountRepository,
            backupRepository = backupRepository,
            modifier = Modifier.padding(innerPadding)
        )
    }
}