package com.example.hisab.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Account badge icons only.
 *
 * The category half of this file — `getIcon` and the `availableIcons` picker list — is gone.
 * Categories are emoji end to end now (`iconName` stores the emoji itself; see
 * `SmsNotificationHelper.getCategoryEmoji`, which still maps the legacy Material names for rows
 * written before the switch). Keeping a vector lookup alongside that only invited the two to drift,
 * which is exactly how the notification and the dashboard ended up showing different icons for the
 * same category.
 */
object CategoryIconMapper {

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
}
