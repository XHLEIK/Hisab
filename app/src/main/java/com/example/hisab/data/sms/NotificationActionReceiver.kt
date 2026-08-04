package com.example.hisab.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.hisab.data.backup.AutoBackupManager
import com.example.hisab.data.db.HisabDatabase
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType
import com.example.hisab.util.CurrencyFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingId = intent.getLongExtra(SmsNotificationHelper.EXTRA_PENDING_ID, -1L)
        val notificationId = intent.getIntExtra(SmsNotificationHelper.EXTRA_NOTIFICATION_ID, -1)

        if (action == SmsNotificationHelper.ACTION_SWAP_TRANSFER_ACCOUNTS) {
            val amount = intent.getDoubleExtra(SmsNotificationHelper.EXTRA_AMOUNT, 0.0)
            val bankName = intent.getStringExtra(SmsNotificationHelper.EXTRA_BANK_NAME) ?: "Bank"
            SmsNotificationHelper.swapToTransferAccountsNotification(context, notificationId, pendingId, amount, bankName)
            return
        }

        if (action == SmsNotificationHelper.ACTION_DISMISS_NOTIFICATION) {
            // Swiped away -> Record remains safely in pending_transactions table
            return
        }

        if (action == SmsNotificationHelper.ACTION_LOG_TRANSACTION) {
            val pendingResult = goAsync()
            val categoryName = intent.getStringExtra(SmsNotificationHelper.EXTRA_CATEGORY_NAME) ?: "Other Expense"
            val typeStr = intent.getStringExtra(SmsNotificationHelper.EXTRA_TRANSACTION_TYPE) ?: "EXPENSE"
            val toAccount = intent.getStringExtra(SmsNotificationHelper.EXTRA_TO_ACCOUNT)
            val amount = intent.getDoubleExtra(SmsNotificationHelper.EXTRA_AMOUNT, 0.0)

            val txType = when (typeStr) {
                "INCOME" -> TransactionType.INCOME
                "TRANSFER" -> TransactionType.TRANSFER
                else -> TransactionType.EXPENSE
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = HisabDatabase.getDatabase(context)
                    val pendingDao = db.pendingTransactionDao()
                    val transactionDao = db.transactionDao()
                    val categoryDao = db.categoryDao()
                    val accountDao = db.accountDao()

                    // Match Category
                    val categories = categoryDao.getAllSync()
                    val matchedCategory = categories.firstOrNull {
                        it.name.equals(categoryName, ignoreCase = true) && it.type == txType
                    } ?: categories.firstOrNull { it.type == txType }

                    val categoryId = matchedCategory?.id ?: 1L

                    // Match Primary/Source Account
                    val accounts = accountDao.getAllSync()
                    val primaryAccount = accounts.firstOrNull { it.isPrimary } ?: accounts.firstOrNull()
                    val sourceAccountName = primaryAccount?.name ?: "Primary Bank"

                    // Create Transaction
                    val newTx = TransactionEntity(
                        amount = amount,
                        type = txType,
                        categoryId = categoryId,
                        date = LocalDate.now(),
                        account = sourceAccountName,
                        toAccount = if (txType == TransactionType.TRANSFER) (toAccount ?: "Savings") else null,
                        notes = "Auto-logged from SMS"
                    )

                    transactionDao.insert(newTx)

                    // Remove from pending table
                    if (pendingId > 0) {
                        pendingDao.deleteById(pendingId)
                    }

                    // Trigger AutoBackup
                    val autoBackupManager = AutoBackupManager(context, db)
                    autoBackupManager.performBackup()

                    // Post success notification
                    val formattedAmount = CurrencyFormatter.format(amount)
                    SmsNotificationHelper.postSuccessNotification(
                        context,
                        notificationId,
                        "Logged $formattedAmount under $categoryName"
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
