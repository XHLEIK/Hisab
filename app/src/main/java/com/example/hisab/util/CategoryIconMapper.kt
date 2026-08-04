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
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIconMapper {

    /**
     * Maps icon string names stored in database to vector icons.
     */
    fun getIcon(iconName: String): ImageVector {
        return when (iconName) {
            "Checkroom", "TShirt", "Apparel", "Clothing" -> Icons.Filled.Checkroom
            "LocalLaundryService", "WashingMachine", "Laundry" -> Icons.Filled.LocalLaundryService
            "Coffee", "Cafe", "Tea" -> Icons.Filled.Coffee
            "Fastfood", "Snacks" -> Icons.Filled.Fastfood
            "Pets", "Animals" -> Icons.Filled.Pets
            "DirectionsBus", "Bus" -> Icons.Filled.DirectionsBus
            "TwoWheeler", "Bike" -> Icons.Filled.TwoWheeler
            "LocalGasStation", "Fuel", "Petrol" -> Icons.Filled.LocalGasStation
            "Build", "Repairs", "Hardware" -> Icons.Filled.Build
            "MedicalServices", "Pharmacy", "Medicine" -> Icons.Filled.MedicalServices
            "SportsEsports", "Gaming" -> Icons.Filled.SportsEsports
            "Headphones", "Music" -> Icons.Filled.Headphones
            "Tv", "Television" -> Icons.Filled.Tv
            "Wifi", "Internet" -> Icons.Filled.Wifi
            "ElectricalServices", "Electricity" -> Icons.Filled.ElectricalServices
            "WaterDrop", "Water" -> Icons.Filled.WaterDrop
            "VolunteerActivism", "Charity", "Donation" -> Icons.Filled.VolunteerActivism
            "ChildCare", "Baby", "Kids" -> Icons.Filled.ChildCare
            "Hotel", "Stay" -> Icons.Filled.Hotel
            "CameraAlt", "Photography" -> Icons.Filled.CameraAlt
            "ContentCut", "Salon", "Grooming" -> Icons.Filled.ContentCut
            "Book", "Books" -> Icons.Filled.Book
            "Storefront", "Store" -> Icons.Filled.Storefront
            "LocalGroceryStore", "GroceriesStore" -> Icons.Filled.LocalGroceryStore
            "LocalMall", "Mall" -> Icons.Filled.LocalMall
            "Lightbulb" -> Icons.Filled.Lightbulb
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
            "Lock" -> Icons.Filled.Lock
            "Movie" -> Icons.Filled.Movie
            "School" -> Icons.Filled.School
            "People" -> Icons.Filled.People
            "FitnessCenter" -> Icons.Filled.FitnessCenter
            "Flight" -> Icons.Filled.Flight
            "Subscriptions", "Smartphone" -> Icons.Filled.Smartphone
            "Savings" -> Icons.Filled.Savings
            "Stocks", "ShowChart" -> Icons.Filled.ShowChart
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
     * Full list of selectable category icons for the CategoryEditDialog picker with search tags.
     */
    val availableIcons: List<Pair<String, ImageVector>> = listOf(
        "Checkroom" to Icons.Filled.Checkroom,
        "LocalLaundryService" to Icons.Filled.LocalLaundryService,
        "Coffee" to Icons.Filled.Coffee,
        "Fastfood" to Icons.Filled.Fastfood,
        "ShoppingCart" to Icons.Filled.ShoppingCart,
        "Restaurant" to Icons.Filled.Restaurant,
        "ShoppingBag" to Icons.Filled.ShoppingBag,
        "LocalGroceryStore" to Icons.Filled.LocalGroceryStore,
        "Storefront" to Icons.Filled.Storefront,
        "LocalMall" to Icons.Filled.LocalMall,
        "DirectionsCar" to Icons.Filled.DirectionsCar,
        "DirectionsBus" to Icons.Filled.DirectionsBus,
        "TwoWheeler" to Icons.Filled.TwoWheeler,
        "LocalGasStation" to Icons.Filled.LocalGasStation,
        "Flight" to Icons.Filled.Flight,
        "Hotel" to Icons.Filled.Hotel,
        "Receipt" to Icons.Filled.Receipt,
        "Home" to Icons.Filled.Home,
        "LocalHospital" to Icons.Filled.LocalHospital,
        "MedicalServices" to Icons.Filled.MedicalServices,
        "FitnessCenter" to Icons.Filled.FitnessCenter,
        "SportsEsports" to Icons.Filled.SportsEsports,
        "Movie" to Icons.Filled.Movie,
        "Headphones" to Icons.Filled.Headphones,
        "Tv" to Icons.Filled.Tv,
        "Smartphone" to Icons.Filled.Smartphone,
        "Wifi" to Icons.Filled.Wifi,
        "ElectricalServices" to Icons.Filled.ElectricalServices,
        "WaterDrop" to Icons.Filled.WaterDrop,
        "School" to Icons.Filled.School,
        "Book" to Icons.Filled.Book,
        "ChildCare" to Icons.Filled.ChildCare,
        "Pets" to Icons.Filled.Pets,
        "Build" to Icons.Filled.Build,
        "ContentCut" to Icons.Filled.ContentCut,
        "CameraAlt" to Icons.Filled.CameraAlt,
        "VolunteerActivism" to Icons.Filled.VolunteerActivism,
        "CardGiftcard" to Icons.Filled.CardGiftcard,
        "AccountBalance" to Icons.Filled.AccountBalance,
        "Laptop" to Icons.Filled.Laptop,
        "TrendingUp" to Icons.AutoMirrored.Filled.TrendingUp,
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
