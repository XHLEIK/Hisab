package com.example.hisab.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.hisab.data.repository.AccountRepository
import com.example.hisab.data.repository.BackupRepository
import com.example.hisab.data.repository.CategoryRepository
import com.example.hisab.data.repository.TransactionRepository
import com.example.hisab.ui.screens.analytics.AnalyticsScreen
import com.example.hisab.ui.screens.dashboard.DashboardScreen
import com.example.hisab.ui.screens.history.HistoryScreen
import com.example.hisab.ui.screens.settings.SettingsScreen

@Composable
fun HisabNavHost(
    navController: NavHostController,
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    backupRepository: BackupRepository,
    modifier: Modifier = Modifier,
    onAddTransaction: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                transactionRepository = transactionRepository,
                categoryRepository = categoryRepository,
                accountRepository = accountRepository,
                onAddTransaction = onAddTransaction,
                onSeeAllTransactions = {
                    navController.navigate(Screen.History.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable(Screen.Analytics.route) {
            AnalyticsScreen(
                transactionRepository = transactionRepository,
                categoryRepository = categoryRepository,
                accountRepository = accountRepository
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                transactionRepository = transactionRepository,
                categoryRepository = categoryRepository,
                accountRepository = accountRepository
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                categoryRepository = categoryRepository,
                accountRepository = accountRepository,
                backupRepository = backupRepository
            )
        }
    }
}
