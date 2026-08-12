package com.example.hisab.data.sms

import android.app.NotificationManager
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

        // ══════════════════════════════════════════════════════════════
        //  Stage 1 → Stage 2 Transition Actions (NEW)
        // ══════════════════════════════════════════════════════════════

        if (action == SmsNotificationHelper.ACTION_SELECT_EXPENSE_CATEGORY) {
            val pendingResult = goAsync()
            val amount = intent.getDoubleExtra(SmsNotificationHelper.EXTRA_AMOUNT, 0.0)
            val bankName = intent.getStringExtra(SmsNotificationHelper.EXTRA_BANK_NAME) ?: "Bank"

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    SmsNotificationHelper.postCategoryPickerNotification(
                        context, pendingId, notificationId, "EXPENSE", amount, bankName, 0
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        if (action == SmsNotificationHelper.ACTION_SELECT_INCOME_CATEGORY) {
            val pendingResult = goAsync()
            val amount = intent.getDoubleExtra(SmsNotificationHelper.EXTRA_AMOUNT, 0.0)
            val bankName = intent.getStringExtra(SmsNotificationHelper.EXTRA_BANK_NAME) ?: "Bank"

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    SmsNotificationHelper.postCategoryPickerNotification(
                        context, pendingId, notificationId, "INCOME", amount, bankName, 0
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // ══════════════════════════════════════════════════════════════
        //  Pagination Action (Updated to route to Stage 2 Category Picker)
        // ══════════════════════════════════════════════════════════════

        if (action == SmsNotificationHelper.ACTION_PAGINATE_NOTIFICATION) {
            val pendingResult = goAsync()
            val pageIndex = intent.getIntExtra(SmsNotificationHelper.EXTRA_PAGE_INDEX, 0)
            val mode = intent.getStringExtra(SmsNotificationHelper.EXTRA_PAGINATION_MODE) ?: "EXPENSE"
            val amount = intent.getDoubleExtra(SmsNotificationHelper.EXTRA_AMOUNT, 0.0)
            val bankName = intent.getStringExtra(SmsNotificationHelper.EXTRA_BANK_NAME) ?: "Bank"

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    when (mode) {
                        "TRANSFER_CREDIT" -> {
                            SmsNotificationHelper.swapToCreditTransferAccountsNotification(
                                context, notificationId, pendingId, amount, bankName, pageIndex
                            )
                        }
                        "EXPENSE", "INCOME" -> {
                            // Route to Stage 2 Category Picker with pagination
                            SmsNotificationHelper.postCategoryPickerNotification(
                                context, pendingId, notificationId, mode, amount, bankName, pageIndex
                            )
                        }
                        else -> {
                            // Legacy fallback — shouldn't reach here
                            val db = HisabDatabase.getDatabase(context)
                            val pending = db.pendingTransactionDao().getById(pendingId)
                            if (pending != null) {
                                SmsNotificationHelper.postBankTransactionNotification(context, pending)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // ══════════════════════════════════════════════════════════════
        //  Swap Credit → Transfer Account Picker
        // ══════════════════════════════════════════════════════════════

        if (action == SmsNotificationHelper.ACTION_SWAP_CREDIT_TRANSFER) {
            val pendingResult = goAsync()
            val amount = intent.getDoubleExtra(SmsNotificationHelper.EXTRA_AMOUNT, 0.0)
            val bankName = intent.getStringExtra(SmsNotificationHelper.EXTRA_BANK_NAME) ?: "Bank"

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    SmsNotificationHelper.swapToCreditTransferAccountsNotification(
                        context, notificationId, pendingId, amount, bankName, 0
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // ══════════════════════════════════════════════════════════════
        //  Debit Transfer Account Picker (Swap to target accounts)
        // ══════════════════════════════════════════════════════════════

        if (action == SmsNotificationHelper.ACTION_SWAP_TRANSFER_ACCOUNTS) {
            val pendingResult = goAsync()
            val amount = intent.getDoubleExtra(SmsNotificationHelper.EXTRA_AMOUNT, 0.0)
            val bankName = intent.getStringExtra(SmsNotificationHelper.EXTRA_BANK_NAME) ?: "Bank"

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // For debit transfers, the source bank is known (the one debited)
                    // Show other accounts as destination options
                    SmsNotificationHelper.swapToCreditTransferAccountsNotification(
                        context, notificationId, pendingId, amount, bankName, 0
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // ══════════════════════════════════════════════════════════════
        //  Dismiss Action
        // ══════════════════════════════════════════════════════════════

        if (action == SmsNotificationHelper.ACTION_DISMISS_NOTIFICATION) {
            // Swiped away → Record remains safely in pending_transactions table
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
            return
        }

        // ══════════════════════════════════════════════════════════════
        //  Undo Auto-Merge Action
        // ══════════════════════════════════════════════════════════════

        if (action == SmsNotificationHelper.ACTION_UNDO_AUTO_MERGE) {
            val pendingResult = goAsync()
            val txId = intent.getLongExtra(SmsNotificationHelper.EXTRA_TX_ID, -1L)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = HisabDatabase.getDatabase(context)
                    val transactionDao = db.transactionDao()

                    // Safe Auto-Merge Undo Boundary: Check existence in Room DB
                    val existingTx = transactionDao.getById(txId)
                    if (existingTx != null) {
                        transactionDao.delete(existingTx)

                        // Trigger AutoBackup after undo
                        val autoBackupManager = AutoBackupManager(context, db)
                        autoBackupManager.performBackup()

                        // Cancel toast notification
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(notificationId)

                        SmsNotificationHelper.postSuccessNotification(context, notificationId, "Reverted auto-merged transfer")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // ══════════════════════════════════════════════════════════════
        //  Log Inward Transfer Action
        // ══════════════════════════════════════════════════════════════

        if (action == SmsNotificationHelper.ACTION_LOG_INWARD_TRANSFER) {
            val pendingResult = goAsync()
            val sourceAccount = intent.getStringExtra(SmsNotificationHelper.EXTRA_SOURCE_ACCOUNT) ?: "Secondary Bank"
            val targetBankName = intent.getStringExtra(SmsNotificationHelper.EXTRA_BANK_NAME) ?: "Primary Bank"
            val amount = intent.getDoubleExtra(SmsNotificationHelper.EXTRA_AMOUNT, 0.0)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = HisabDatabase.getDatabase(context)
                    val pendingDao = db.pendingTransactionDao()
                    val transactionDao = db.transactionDao()
                    val accountDao = db.accountDao()
                    val categoryDao = db.categoryDao()

                    // Atomic Double-Tap Guard
                    val pending = pendingDao.getById(pendingId) ?: return@launch
                    pendingDao.delete(pending)

                    val accounts = accountDao.getAllSync()
                    val targetAccount = accounts.firstOrNull { it.bankCode.equals(targetBankName, ignoreCase = true) }
                        ?: accounts.firstOrNull { it.isPrimary } ?: accounts.firstOrNull()
                    val targetAccountName = targetAccount?.name ?: targetBankName

                    val transferCategories = categoryDao.getAllSync().filter { it.type == TransactionType.TRANSFER }
                    val categoryId = transferCategories.firstOrNull()?.id ?: 1L

                    val transferTx = TransactionEntity(
                        amount = amount,
                        type = TransactionType.TRANSFER,
                        categoryId = categoryId,
                        date = LocalDate.now(),
                        account = sourceAccount,
                        toAccount = targetAccountName,
                        notes = "Inward transfer from SMS alert ($sourceAccount -> $targetAccountName)"
                    )

                    transactionDao.insert(transferTx)

                    // Store short-lived reconciliation hash to drop any redundant CREDIT alert
                    val prefs = context.getSharedPreferences("sms_processed_hashes", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("recon_${amount}_CREDIT", true).apply()

                    val autoBackupManager = AutoBackupManager(context, db)
                    autoBackupManager.performBackup()

                    val formattedAmount = CurrencyFormatter.format(amount)
                    SmsNotificationHelper.postSuccessNotification(
                        context,
                        notificationId,
                        "Logged Transfer $formattedAmount ($sourceAccount → $targetAccountName)"
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // ══════════════════════════════════════════════════════════════
        //  Log Transaction Action (Final — logs to Room DB)
        // ══════════════════════════════════════════════════════════════

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

                    // Atomic Double-Tap Guard: Check pending record existence
                    val pending = pendingDao.getById(pendingId) ?: return@launch
                    pendingDao.delete(pending)

                    // Match Category
                    val categories = categoryDao.getAllSync()
                    val matchedCategory = categories.firstOrNull {
                        it.name.equals(categoryName, ignoreCase = true) && it.type == txType
                    } ?: categories.firstOrNull { it.type == txType }

                    val categoryId = matchedCategory?.id ?: 1L

                    // Match Primary/Source Account
                    val accounts = accountDao.getAllSync()
                    val bankName = intent.getStringExtra(SmsNotificationHelper.EXTRA_BANK_NAME)
                    val matchedAccount = accounts.firstOrNull { it.bankCode.equals(bankName, ignoreCase = true) }
                        ?: accounts.firstOrNull { it.isPrimary } ?: accounts.firstOrNull()
                    val sourceAccountName = matchedAccount?.name ?: "Primary Bank"

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
