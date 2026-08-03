package com.example.hisab.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIconMapper {

    /**
     * Maps icon string names stored in database to vector icons.
     */
    fun getIcon(iconName: String): ImageVector {
        return when (iconName) {
            "AccountBalance" -> Icons.Filled.AccountBalance
            "Laptop" -> Icons.Filled.Laptop
            "TrendingUp" -> Icons.AutoMirrored.Filled.TrendingUp
            "CardGiftcard" -> Icons.Filled.CardGiftcard
            "AddCircle" -> Icons.Filled.AddCircle
            "ShoppingCart" -> Icons.Filled.ShoppingCart
            "Restaurant" -> Icons.Filled.Restaurant
            "ShoppingBag" -> Icons.Filled.ShoppingBag
            "DirectionsCar" -> Icons.Filled.DirectionsCar
            "Receipt" -> Icons.Filled.Receipt
            "ReceiptLong" -> Icons.AutoMirrored.Filled.ReceiptLong
            "Home" -> Icons.Filled.Home
            "LocalHospital" -> Icons.Filled.LocalHospital
            "Movie" -> Icons.Filled.Movie
            "School" -> Icons.Filled.School
            "People" -> Icons.Filled.People
            "FitnessCenter" -> Icons.Filled.FitnessCenter
            "Flight" -> Icons.Filled.Flight
            "Subscriptions", "Smartphone" -> Icons.Filled.Smartphone
            "Savings" -> Icons.Filled.Savings
            "Stocks", "ShowChart" -> Icons.Filled.ShowChart
            "Lock" -> Icons.Filled.Lock
            "PieChart" -> Icons.Filled.PieChart
            "SwapHoriz" -> Icons.Filled.SwapHoriz
            "Work" -> Icons.Filled.Work
            "Payments" -> Icons.Filled.Payments
            "CreditCard" -> Icons.Filled.CreditCard
            "AccountBalanceWallet" -> Icons.Filled.AccountBalanceWallet
            "AutoGraph" -> Icons.Filled.AutoGraph
            "DirectionsRun" -> Icons.Filled.DirectionsRun
            else -> Icons.Filled.MoreHoriz
        }
    }

    /**
     * Maps account types/names to icons for consistent badge rendering.
     */
    fun getAccountIcon(accountName: String): ImageVector {
        val lower = accountName.lowercase()
        return when {
            lower.contains("cash") -> Icons.Filled.Payments
            lower.contains("card") || lower.contains("credit") -> Icons.Filled.CreditCard
            lower.contains("upi") || lower.contains("wallet") || lower.contains("pay") -> Icons.Filled.AccountBalanceWallet
            else -> Icons.Filled.AccountBalance
        }
    }

    /**
     * Full list of selectable category icons for the CategoryEditDialog picker.
     */
    val availableIcons: List<Pair<String, ImageVector>> = listOf(
        "ShoppingCart" to Icons.Filled.ShoppingCart,
        "Restaurant" to Icons.Filled.Restaurant,
        "ShoppingBag" to Icons.Filled.ShoppingBag,
        "DirectionsCar" to Icons.Filled.DirectionsCar,
        "Receipt" to Icons.Filled.Receipt,
        "Home" to Icons.Filled.Home,
        "LocalHospital" to Icons.Filled.LocalHospital,
        "Movie" to Icons.Filled.Movie,
        "School" to Icons.Filled.School,
        "FitnessCenter" to Icons.Filled.FitnessCenter,
        "Flight" to Icons.Filled.Flight,
        "Subscriptions" to Icons.Filled.Smartphone,
        "AccountBalance" to Icons.Filled.AccountBalance,
        "Laptop" to Icons.Filled.Laptop,
        "TrendingUp" to Icons.AutoMirrored.Filled.TrendingUp,
        "CardGiftcard" to Icons.Filled.CardGiftcard,
        "Savings" to Icons.Filled.Savings,
        "Stocks" to Icons.Filled.ShowChart,
        "Lock" to Icons.Filled.Lock,
        "PieChart" to Icons.Filled.PieChart,
        "SwapHoriz" to Icons.Filled.SwapHoriz,
        "Work" to Icons.Filled.Work,
        "People" to Icons.Filled.People,
        "MoreHoriz" to Icons.Filled.MoreHoriz
    )
}
