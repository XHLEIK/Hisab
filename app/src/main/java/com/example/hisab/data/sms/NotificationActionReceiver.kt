package com.example.hisab.data.sms

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import com.example.hisab.data.backup.AutoBackupManager
import com.example.hisab.data.db.HisabDatabase
import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionConfidence
import com.example.hisab.data.model.TransactionSource
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

        if (action == SmsNotificationHelper.ACTION_SELECT_SPLIT_CATEGORY) {
            val pendingResult = goAsync()
            val amount = intent.getDoubleExtra(SmsNotificationHelper.EXTRA_AMOUNT, 0.0)
            val bankName = intent.getStringExtra(SmsNotificationHelper.EXTRA_BANK_NAME) ?: "Bank"

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    SmsNotificationHelper.postSplitCategoryPickerNotification(
                        context, pendingId, notificationId, amount, bankName, 0
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
                            val isDebit = intent.getBooleanExtra(SmsNotificationHelper.EXTRA_IS_DEBIT, false)
                            SmsNotificationHelper.swapToCreditTransferAccountsNotification(
                                context, notificationId, pendingId, amount, bankName, pageIndex, isDebit = isDebit
                            )
                        }
                        "EXPENSE", "INCOME" -> {
                            SmsNotificationHelper.postCategoryPickerNotification(
                                context, pendingId, notificationId, mode, amount, bankName, pageIndex
                            )
                        }
                        "SPLIT" -> {
                            SmsNotificationHelper.postSplitCategoryPickerNotification(
                                context, pendingId, notificationId, amount, bankName, pageIndex
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
                        context, notificationId, pendingId, amount, bankName, 0, isDebit = true
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
            val pickedAccount = intent.getStringExtra(SmsNotificationHelper.EXTRA_SOURCE_ACCOUNT) ?: "Secondary Bank"
            val smsBankName = intent.getStringExtra(SmsNotificationHelper.EXTRA_BANK_NAME) ?: "Primary Bank"
            val amount = intent.getDoubleExtra(SmsNotificationHelper.EXTRA_AMOUNT, 0.0)
            val isDebitIntent = intent.getBooleanExtra(SmsNotificationHelper.EXTRA_IS_DEBIT, false)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = HisabDatabase.getDatabase(context)
                    val pendingDao = db.pendingTransactionDao()
                    val transactionDao = db.transactionDao()
                    val accountDao = db.accountDao()
                    val categoryDao = db.categoryDao()

                    val accounts = accountDao.getAllSync()
                    // Resolve the bank that sent the SMS (authoritative for one leg of the transfer)
                    val smsAccount = accounts.firstOrNull { BankAliasRegistry.matches(it.bankCode, smsBankName, null) }
                        ?: accounts.firstOrNull { it.isPrimary } ?: accounts.firstOrNull()
                    val smsAccountName = smsAccount?.name ?: smsBankName

                    // The picked account from the notification is the *other* leg
                    val pickedAccountEntity = accounts.firstOrNull { it.name.equals(pickedAccount, ignoreCase = true) }
                    val pickedAccountName = pickedAccountEntity?.name ?: pickedAccount

                    if (pickedAccountName.equals(smsAccountName, ignoreCase = true)) {
                        SmsNotificationHelper.postSuccessNotification(context, notificationId, "Cannot transfer to same account")
                        return@launch
                    }

                    // Direction determination: prefer the intent flag (always reliable) over the
                    // pending row lookup (which can be null if the pending was consumed by auto-merge).
                    // For a DEBIT SMS: money left smsAccount, so FROM = smsAccount, TO = picked.
                    // For a CREDIT SMS: money arrived at smsAccount, so FROM = picked, TO = smsAccount.
                    val pendingForType = pendingDao.getById(pendingId)
                    val isDebit = isDebitIntent || pendingForType?.type == "DEBIT"
                    val fromAccountName: String
                    val toAccountName: String
                    val fromAccountEntity: AccountEntity?
                    val toAccountEntity: AccountEntity?
                    if (isDebit) {
                        fromAccountName = smsAccountName
                        toAccountName = pickedAccountName
                        fromAccountEntity = smsAccount
                        toAccountEntity = pickedAccountEntity
                    } else {
                        // CREDIT: picked is source, sms is destination
                        fromAccountName = pickedAccountName
                        toAccountName = smsAccountName
                        fromAccountEntity = pickedAccountEntity
                        toAccountEntity = smsAccount
                    }

                    val transferCategories = categoryDao.getAllSync().filter { it.type == TransactionType.TRANSFER }
                    val categoryId = transferCategories.firstOrNull()?.id ?: 1L

                    // INV-3: consuming the pending row and materialising the transaction is one
                    // all-or-nothing step. Non-atomically, a crash between the delete and the insert
                    // destroyed the pending row and left no transaction — the user's tap silently
                    // erased their own transaction.
                    val logged = db.withTransaction {
                        val pending = pendingDao.getById(pendingId)
                        if (pending != null) {
                            pendingDao.delete(pending)
                        }

                        transactionDao.insert(
                            TransactionEntity(
                                amount = amount,
                                type = TransactionType.TRANSFER,
                                categoryId = categoryId,
                                date = LocalDate.now(),
                                account = fromAccountName,
                                toAccount = toAccountName,
                                notes = "Transfer ($fromAccountName → $toAccountName)",
                                // Carry the identity forward so the message stays claimed after the
                                // pending row is gone (cross-table dedup, INV-2).
                                sourceMessageHash = pending?.sourceMessageHash
                                    ?.takeIf { transactionDao.getBySourceHash(it) == null },
                                source = TransactionSource.NOTIFICATION_ACTION.name,
                                confidence = TransactionConfidence.MANUAL.name,
                                referenceNumber = pending?.referenceNumber
                            )
                        )
                        true
                    }
                    if (!logged) return@launch

                    // Short-lived marker so the matching CREDIT SMS does not also ask to be logged.
                    // Scoped by account and hour bucket: the old key was `recon_${amount}_CREDIT`,
                    // unscoped and never expiring, so one ₹500 transfer suppressed the next ₹500
                    // credit on any account, forever. A marker that misses (an SMS arriving in the
                    // following hour bucket) costs one redundant notification — the fail-open side.
                    PrefsSmsHashCache(context).mark(
                        SmsHash.reconciliationKey(
                            amount = amount,
                            type = "CREDIT",
                            accountLast4 = toAccountEntity?.accountLast4,
                            timestamp = System.currentTimeMillis()
                        )
                    )

                    val autoBackupManager = AutoBackupManager(context, db)
                    autoBackupManager.performBackup()

                    val formattedAmount = CurrencyFormatter.format(amount)
                    SmsNotificationHelper.postSuccessNotification(
                        context,
                        notificationId,
                        "Logged Transfer $formattedAmount ($fromAccountName → $toAccountName)"
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
        //  Log Split Reimbursement (NEW — category-level negative expense)
        // ══════════════════════════════════════════════════════════════

        if (action == SmsNotificationHelper.ACTION_LOG_SPLIT) {
            val pendingResult = goAsync()
            val categoryId = intent.getLongExtra(SmsNotificationHelper.EXTRA_CATEGORY_ID, -1L)
            val categoryName = intent.getStringExtra(SmsNotificationHelper.EXTRA_CATEGORY_NAME) ?: "Expense"
            val amount = intent.getDoubleExtra(SmsNotificationHelper.EXTRA_AMOUNT, 0.0)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = HisabDatabase.getDatabase(context)
                    val pendingDao = db.pendingTransactionDao()
                    val transactionDao = db.transactionDao()
                    val categoryDao = db.categoryDao()
                    val accountDao = db.accountDao()

                    val pending = pendingDao.getById(pendingId) ?: return@launch
                    val splitCategory = if (categoryId != -1L) categoryDao.getById(categoryId)
                        else categoryDao.getAllSync().firstOrNull { it.name.equals(categoryName, ignoreCase = true) && it.type == TransactionType.EXPENSE }
                    if (splitCategory == null || splitCategory.type != TransactionType.EXPENSE) {
                        SmsNotificationHelper.postSuccessNotification(context, notificationId, "Invalid category for split")
                        return@launch
                    }

                    val gross = transactionDao.getGrossExpenseForCategory(splitCategory.id)
                    val reimbursed = transactionDao.getSplitReimbursementForCategory(splitCategory.id)
                    val remaining = gross - reimbursed

                    if (remaining <= 0.0) {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(notificationId)
                        SmsNotificationHelper.postSuccessNotification(
                            context,
                            notificationId,
                            "No unreimbursed ${splitCategory.name} expense remains"
                        )
                        return@launch
                    }
                    if (amount > remaining) {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(notificationId)
                        // Keep pending classifiable — re-post Stage-1 so user can choose another eligible category
                        SmsNotificationHelper.postBankTransactionNotification(context, pending)
                        SmsNotificationHelper.postSuccessNotification(
                            context,
                            notificationId + 1,
                            "₹${CurrencyFormatter.format(amount)} cannot be fully applied to ${splitCategory.name}. Only ${CurrencyFormatter.format(remaining)} remains unreimbursed."
                        )
                        return@launch
                    }

                    val accounts = accountDao.getAllSync()
                    val bankName = intent.getStringExtra(SmsNotificationHelper.EXTRA_BANK_NAME)
                    val matchedAccount = accounts.firstOrNull { BankAliasRegistry.matches(it.bankCode, pending.bankName, pending.senderHeader) }
                        ?: accounts.firstOrNull { it.isPrimary } ?: accounts.firstOrNull()
                    val accountName = matchedAccount?.name ?: pending.bankName

                    val logged = db.withTransaction {
                        val stillPending = pendingDao.getById(pendingId) ?: return@withTransaction false
                        if (transactionDao.getBySourceHash(stillPending.sourceMessageHash ?: "") != null) {
                            pendingDao.deleteById(pendingId)
                            return@withTransaction false
                        }
                        pendingDao.delete(stillPending)
                        transactionDao.insert(
                            TransactionEntity(
                                amount = amount,
                                type = TransactionType.EXPENSE,
                                subtype = com.example.hisab.data.model.TransactionSubtype.SPLIT_REIMBURSEMENT.name,
                                categoryId = splitCategory.id,
                                date = LocalDate.now(),
                                account = accountName,
                                toAccount = null,
                                notes = "Split reimbursement for ${splitCategory.name}",
                                sourceMessageHash = stillPending.sourceMessageHash?.takeIf { transactionDao.getBySourceHash(it) == null },
                                source = stillPending.source ?: TransactionSource.NOTIFICATION_ACTION.name,
                                confidence = TransactionConfidence.CONFIRMED.name,
                                referenceNumber = stillPending.referenceNumber
                            )
                        )
                        true
                    }
                    if (!logged) return@launch

                    val autoBackupManager = AutoBackupManager(context, db)
                    autoBackupManager.performBackup()

                    val formattedAmount = CurrencyFormatter.format(amount)
                    val newNet = (gross - reimbursed) - amount
                    SmsNotificationHelper.postSuccessNotification(
                        context,
                        notificationId,
                        "Split $formattedAmount → ${splitCategory.name} — net now ${CurrencyFormatter.format(newNet)}"
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

                    // Match Category
                    val categories = categoryDao.getAllSync()
                    val matchedCategory = categories.firstOrNull {
                        it.name.equals(categoryName, ignoreCase = true) && it.type == txType
                    } ?: categories.firstOrNull { it.type == txType }

                    val categoryId = matchedCategory?.id ?: 1L

                    // Match Primary/Source Account — hardened to use canonical alias matching
                    val accounts = accountDao.getAllSync()
                    val bankName = intent.getStringExtra(SmsNotificationHelper.EXTRA_BANK_NAME)
                    val matchedAccount = accounts.firstOrNull { BankAliasRegistry.matches(it.bankCode, bankName ?: "", null) }
                        ?: accounts.firstOrNull { it.isPrimary } ?: accounts.firstOrNull()
                    val sourceAccountName = matchedAccount?.name ?: "Primary Bank"

                    if (txType == TransactionType.TRANSFER && toAccount != null && sourceAccountName.equals(toAccount, ignoreCase = true)) {
                        SmsNotificationHelper.postSuccessNotification(context, notificationId, "Cannot transfer to same account")
                        return@launch
                    }

                    // INV-3: the double-tap guard and the insert are one atomic step. Previously a
                    // crash between them left the user with neither a pending row nor a transaction.
                    val logged = db.withTransaction {
                        val pending = pendingDao.getById(pendingId) ?: return@withTransaction false
                        pendingDao.delete(pending)

                        transactionDao.insert(
                            TransactionEntity(
                                amount = amount,
                                type = txType,
                                categoryId = categoryId,
                                date = LocalDate.now(),
                                account = sourceAccountName,
                                toAccount = if (txType == TransactionType.TRANSFER) (toAccount ?: "Savings") else null,
                                notes = "Auto-logged from SMS",
                                // Keeps the message identity claimed once the pending row is gone.
                                // The `takeIf` is a restore-safety guard: transactionDao.insert() is
                                // REPLACE, so writing a hash another row already holds would delete
                                // that row. Dropping the hash costs dedup, not data.
                                sourceMessageHash = pending.sourceMessageHash
                                    ?.takeIf { transactionDao.getBySourceHash(it) == null },
                                source = TransactionSource.NOTIFICATION_ACTION.name,
                                confidence = TransactionConfidence.MANUAL.name,
                                referenceNumber = pending.referenceNumber
                            )
                        )
                        true
                    }
                    if (!logged) return@launch

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
