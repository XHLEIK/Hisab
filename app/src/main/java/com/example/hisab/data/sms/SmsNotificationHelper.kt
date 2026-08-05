package com.example.hisab.data.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.hisab.MainActivity
import com.example.hisab.R
import com.example.hisab.data.db.HisabDatabase
import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.db.entity.PendingTransactionEntity
import com.example.hisab.data.model.TransactionType
import com.example.hisab.util.CurrencyFormatter

object SmsNotificationHelper {

    const val CHANNEL_ID = "bank_transactions_channel"
    const val ACTION_LOG_TRANSACTION = "com.example.hisab.ACTION_LOG_TRANSACTION"
    const val ACTION_SWAP_TRANSFER_ACCOUNTS = "com.example.hisab.ACTION_SWAP_TRANSFER_ACCOUNTS"
    const val ACTION_SWAP_CREDIT_TRANSFER = "com.example.hisab.ACTION_SWAP_CREDIT_TRANSFER"
    const val ACTION_LOG_INWARD_TRANSFER = "com.example.hisab.ACTION_LOG_INWARD_TRANSFER"
    const val ACTION_PAGINATE_NOTIFICATION = "com.example.hisab.ACTION_PAGINATE_NOTIFICATION"
    const val ACTION_DISMISS_NOTIFICATION = "com.example.hisab.ACTION_DISMISS_NOTIFICATION"
    const val ACTION_UNDO_AUTO_MERGE = "com.example.hisab.ACTION_UNDO_AUTO_MERGE"

    const val EXTRA_PENDING_ID = "extra_pending_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_CATEGORY_NAME = "extra_category_name"
    const val EXTRA_TRANSACTION_TYPE = "extra_transaction_type" // "EXPENSE", "INCOME", "TRANSFER"
    const val EXTRA_SOURCE_ACCOUNT = "extra_source_account"
    const val EXTRA_TO_ACCOUNT = "extra_to_account"
    const val EXTRA_AMOUNT = "extra_amount"
    const val EXTRA_BANK_NAME = "extra_bank_name"
    const val EXTRA_PAGE_INDEX = "extra_page_index"
    const val EXTRA_PAGINATION_MODE = "extra_pagination_mode" // "EXPENSE", "INCOME", "TRANSFER_DEBIT", "TRANSFER_CREDIT"
    const val EXTRA_TX_ID = "extra_tx_id"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Bank Transactions"
            val descriptionText = "Notifications for automatic bank transaction logging"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Posts Bank Transaction Notification with 3-Action Dynamic Pagination (2 Categories + [ 🔄 More... ])
     */
    suspend fun postBankTransactionNotification(
        context: Context,
        pending: PendingTransactionEntity,
        pageIndex: Int = 0
    ) {
        createNotificationChannel(context)

        val db = HisabDatabase.getDatabase(context)
        val categoryDao = db.categoryDao()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (pending.id * 1000L).toInt() and 0x7FFFFFFF

        val formattedAmount = CurrencyFormatter.format(pending.amount)
        val title: String
        val body: String

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_DISMISS_NOTIFICATION
            putExtra(EXTRA_PENDING_ID, pending.id)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 100,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (pending.type == "CREDIT") {
            title = "💰 Income Received: $formattedAmount"
            body = "Credited to ${pending.bankName}${if (!pending.accountLast4.isNullOrEmpty()) " (A/C **${pending.accountLast4})" else ""}. Select income or transfer:"
            builder.setContentTitle(title).setContentText(body)

            val incomeCategories = categoryDao.getAllSync().filter { it.type == TransactionType.INCOME }
            val totalCategories = if (incomeCategories.isNotEmpty()) incomeCategories.size else 3

            val idx1 = (pageIndex * 2) % totalCategories
            val cat1 = if (incomeCategories.isNotEmpty()) incomeCategories[idx1].name else "Salary"

            // Button 1: Income Category 1
            builder.addAction(
                createLogAction(context, notificationId, pending.id, cat1, "INCOME", null, pending.amount, pending.bankName, "💼 $cat1")
            )

            // Button 2: [ ⇄ Transfer In ] -> Swaps to Source Account Picker
            val swapCreditIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_SWAP_CREDIT_TRANSFER
                putExtra(EXTRA_PENDING_ID, pending.id)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_AMOUNT, pending.amount)
                putExtra(EXTRA_BANK_NAME, pending.bankName)
            }
            val swapCreditPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 2,
                swapCreditIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "⇄ Transfer In", swapCreditPendingIntent)

            // Button 3: [ 🔄 More Income... ] -> Paginate
            val paginateIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_PAGINATE_NOTIFICATION
                putExtra(EXTRA_PENDING_ID, pending.id)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_PAGE_INDEX, pageIndex + 1)
                putExtra(EXTRA_PAGINATION_MODE, "INCOME")
            }
            val paginatePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 3,
                paginateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "🔄 More Income...", paginatePendingIntent)

        } else {
            title = "💸 Payment Detected: $formattedAmount"
            val merchantStr = if (!pending.merchantOrPayee.isNullOrEmpty()) " for ${pending.merchantOrPayee}" else ""
            body = "Debited from ${pending.bankName}${if (!pending.accountLast4.isNullOrEmpty()) " (A/C **${pending.accountLast4})" else ""}$merchantStr"
            builder.setContentTitle(title).setContentText(body)

            val expenseCategories = categoryDao.getAllSync().filter { it.type == TransactionType.EXPENSE }
            val totalCategories = if (expenseCategories.isNotEmpty()) expenseCategories.size else 4

            val idx1 = (pageIndex * 2) % totalCategories
            val idx2 = (pageIndex * 2 + 1) % totalCategories

            val cat1 = if (expenseCategories.isNotEmpty()) expenseCategories[idx1].name else "Groceries & Utilities"
            val cat2 = if (expenseCategories.isNotEmpty()) expenseCategories[idx2].name else "Shopping"

            // Button 1: Category 1
            builder.addAction(
                createLogAction(context, notificationId, pending.id, cat1, "EXPENSE", null, pending.amount, pending.bankName, "🛒 $cat1")
            )
            // Button 2: Category 2
            builder.addAction(
                createLogAction(context, notificationId, pending.id, cat2, "EXPENSE", null, pending.amount, pending.bankName, "🛍️ $cat2")
            )

            // Button 3: [ 🔄 More... ] -> Paginate categories in pairs
            val paginateIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_PAGINATE_NOTIFICATION
                putExtra(EXTRA_PENDING_ID, pending.id)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_PAGE_INDEX, pageIndex + 1)
                putExtra(EXTRA_PAGINATION_MODE, "EXPENSE")
            }
            val paginatePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 3,
                paginateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "🔄 More...", paginatePendingIntent)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * Credit Inward Transfer Source Account Picker Notification (Paginated 2 Accounts + [ 🔄 More... ])
     */
    suspend fun swapToCreditTransferAccountsNotification(
        context: Context,
        notificationId: Int,
        pendingId: Long,
        amount: Double,
        creditedBankName: String,
        pageIndex: Int = 0
    ) {
        val db = HisabDatabase.getDatabase(context)
        val accounts = db.accountDao().getAllSync()
        val availableSources = accounts.filter { !it.bankCode.equals(creditedBankName, ignoreCase = true) }
        val total = if (availableSources.isNotEmpty()) availableSources.size else 2

        val idx1 = (pageIndex * 2) % total
        val idx2 = (pageIndex * 2 + 1) % total

        val acc1Name = if (availableSources.isNotEmpty()) availableSources[idx1].name else "Secondary Bank"
        val acc2Name = if (availableSources.isNotEmpty()) availableSources[idx2].name else "Savings"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val formattedAmount = CurrencyFormatter.format(amount)

        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⇄ Transfer In: $formattedAmount")
            .setContentText("Select source account transferred to $creditedBankName:")
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Action 1: Source Account 1
        builder.addAction(
            createInwardTransferAction(context, notificationId, pendingId, acc1Name, creditedBankName, amount, "🏦 $acc1Name")
        )
        // Action 2: Source Account 2
        builder.addAction(
            createInwardTransferAction(context, notificationId, pendingId, acc2Name, creditedBankName, amount, "🐷 $acc2Name")
        )

        // Action 3: [ 🔄 More Accounts... ] -> Paginate source accounts
        val paginateIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_PAGINATE_NOTIFICATION
            putExtra(EXTRA_PENDING_ID, pendingId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_PAGE_INDEX, pageIndex + 1)
            putExtra(EXTRA_PAGINATION_MODE, "TRANSFER_CREDIT")
            putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_BANK_NAME, creditedBankName)
        }
        val paginatePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 3,
            paginateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, "🔄 More Accounts...", paginatePendingIntent)

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * Auto-Merge Toast Notification with 3-Second Timeout & [ Undo ] Action
     */
    fun postAutoMergeSuccessNotification(
        context: Context,
        transactionId: Long,
        sourceAccount: String,
        targetAccount: String,
        amount: Double
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (transactionId xor System.currentTimeMillis()).toInt() and 0x7FFFFFFF
        val formattedAmount = CurrencyFormatter.format(amount)

        val undoIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_UNDO_AUTO_MERGE
            putExtra(EXTRA_TX_ID, transactionId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val undoPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 99,
            undoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("✓ Auto-Detected Transfer ($formattedAmount)")
            .setContentText("$sourceAccount → $targetAccount")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(5000)
            .addAction(0, "↩️ Undo", undoPendingIntent)

        notificationManager.notify(notificationId, builder.build())
    }

    fun postSuccessNotification(context: Context, notificationId: Int, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("✓ Transaction Logged!")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setTimeoutAfter(3000)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun createLogAction(
        context: Context,
        notificationId: Int,
        pendingId: Long,
        categoryName: String,
        transactionType: String,
        toAccount: String?,
        amount: Double,
        bankName: String,
        buttonLabel: String
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_LOG_TRANSACTION
            putExtra(EXTRA_PENDING_ID, pendingId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_CATEGORY_NAME, categoryName)
            putExtra(EXTRA_TRANSACTION_TYPE, transactionType)
            putExtra(EXTRA_TO_ACCOUNT, toAccount)
            putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_BANK_NAME, bankName)
        }
        val requestCode = (notificationId + buttonLabel.hashCode()) and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, buttonLabel, pendingIntent).build()
    }

    private fun createInwardTransferAction(
        context: Context,
        notificationId: Int,
        pendingId: Long,
        sourceAccount: String,
        targetBankName: String,
        amount: Double,
        buttonLabel: String
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_LOG_INWARD_TRANSFER
            putExtra(EXTRA_PENDING_ID, pendingId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_SOURCE_ACCOUNT, sourceAccount)
            putExtra(EXTRA_BANK_NAME, targetBankName)
            putExtra(EXTRA_AMOUNT, amount)
        }
        val requestCode = (notificationId + buttonLabel.hashCode()) and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, buttonLabel, pendingIntent).build()
    }
}
