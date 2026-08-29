package com.example.hisab.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

import com.example.hisab.data.repository.PendingTransactionRepository

@Composable
fun HisabNavHost(
    navController: NavHostController,
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    backupRepository: BackupRepository,
    pendingTransactionRepository: PendingTransactionRepository? = null,
    modifier: Modifier = Modifier,
    onAddTransaction: () -> Unit = {},
    slideDirection: Int = 1
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                towards = if (slideDirection > 0) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300),
                initialOffset = { it / 4 }
            ) + fadeIn(tween(300))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = if (slideDirection > 0) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300),
                targetOffset = { it / 4 }
            ) + fadeOut(tween(200))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300),
                initialOffset = { it / 4 }
            ) + fadeIn(tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            ) + fadeOut(tween(200))
        }
    ) {
        composable(
            Screen.Dashboard.route,
            enterTransition = {
                slideIntoContainer(
                    towards = if (slideDirection > 0) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                    initialOffset = { it / 4 }
                ) + fadeIn(tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = if (slideDirection > 0) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                    targetOffset = { it / 4 }
                ) + fadeOut(tween(200))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                    initialOffset = { it / 4 }
                ) + fadeIn(tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeOut(tween(200))
            }
        ) {
            DashboardScreen(
                transactionRepository = transactionRepository,
                categoryRepository = categoryRepository,
                accountRepository = accountRepository,
                pendingTransactionRepository = pendingTransactionRepository,
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
        composable(
            Screen.Analytics.route,
            enterTransition = {
                slideIntoContainer(
                    towards = if (slideDirection > 0) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                    initialOffset = { it / 4 }
                ) + fadeIn(tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = if (slideDirection > 0) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                    targetOffset = { it / 4 }
                ) + fadeOut(tween(200))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                    initialOffset = { it / 4 }
                ) + fadeIn(tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeOut(tween(200))
            }
        ) {
            AnalyticsScreen(
                transactionRepository = transactionRepository,
                categoryRepository = categoryRepository,
                accountRepository = accountRepository
            )
        }
        composable(
            Screen.History.route,
            enterTransition = {
                slideIntoContainer(
                    towards = if (slideDirection > 0) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                    initialOffset = { it / 4 }
                ) + fadeIn(tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = if (slideDirection > 0) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                    targetOffset = { it / 4 }
                ) + fadeOut(tween(200))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                    initialOffset = { it / 4 }
                ) + fadeIn(tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeOut(tween(200))
            }
        ) {
            HistoryScreen(
                transactionRepository = transactionRepository,
                categoryRepository = categoryRepository,
                accountRepository = accountRepository
            )
        }
        composable(
            Screen.Settings.route,
            enterTransition = {
                slideIntoContainer(
                    towards = if (slideDirection > 0) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                    initialOffset = { it / 4 }
                ) + fadeIn(tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = if (slideDirection > 0) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                    targetOffset = { it / 4 }
                ) + fadeOut(tween(200))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                    initialOffset = { it / 4 }
                ) + fadeIn(tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeOut(tween(200))
            }
        ) {
            SettingsScreen(
                categoryRepository = categoryRepository,
                accountRepository = accountRepository,
                backupRepository = backupRepository
            )
        }
    }
}
