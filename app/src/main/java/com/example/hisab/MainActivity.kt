package com.example.hisab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.example.hisab.data.repository.AccountRepository
import com.example.hisab.data.repository.BackupRepository
import com.example.hisab.data.repository.CategoryRepository
import com.example.hisab.data.repository.TransactionRepository
import com.example.hisab.ui.navigation.HisabNavHost
import com.example.hisab.ui.navigation.Screen
import com.example.hisab.ui.theme.HisabAppTheme

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request storage permission so it appears in Android App Info -> Permissions
        try {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                permissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_MEDIA_IMAGES
                    )
                )
            }
        } catch (e: Exception) {
            // Permission request fallback
        }

        val database = (application as HisabApplication).database
        val autoBackupManager = com.example.hisab.data.backup.AutoBackupManager(applicationContext, database)
        val transactionRepository = TransactionRepository(database.transactionDao(), autoBackupManager)
        val categoryRepository = CategoryRepository(database.categoryDao(), autoBackupManager)
        val accountRepository = AccountRepository(database.accountDao(), database.transactionDao(), autoBackupManager)
        val backupRepository = BackupRepository(transactionRepository, categoryRepository, autoBackupManager)

        CoroutineScope(Dispatchers.IO).launch {
            database.ensureDefaults()
            // Auto restore if fresh install & database is empty
            autoBackupManager.restoreIfEmpty()
            // Fix any stale account names in transactions (e.g. if user renamed accounts)
            accountRepository.syncAccountNames()
        }

        setContent {
            HisabAppTheme {
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