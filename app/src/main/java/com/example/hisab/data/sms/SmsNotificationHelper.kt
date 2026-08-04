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
import com.example.hisab.data.db.entity.PendingTransactionEntity
import com.example.hisab.util.CurrencyFormatter

object SmsNotificationHelper {

    const val CHANNEL_ID = "bank_transactions_channel"
    const val ACTION_LOG_TRANSACTION = "com.example.hisab.ACTION_LOG_TRANSACTION"
    const val ACTION_SWAP_TRANSFER_ACCOUNTS = "com.example.hisab.ACTION_SWAP_TRANSFER_ACCOUNTS"
    const val ACTION_DISMISS_NOTIFICATION = "com.example.hisab.ACTION_DISMISS_NOTIFICATION"

    const val EXTRA_PENDING_ID = "extra_pending_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_CATEGORY_NAME = "extra_category_name"
    const val EXTRA_TRANSACTION_TYPE = "extra_transaction_type" // "EXPENSE", "INCOME", "TRANSFER"
    const val EXTRA_TO_ACCOUNT = "extra_to_account"
    const val EXTRA_AMOUNT = "extra_amount"
    const val EXTRA_BANK_NAME = "extra_bank_name"

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

    fun postBankTransactionNotification(context: Context, pending: PendingTransactionEntity) {
        createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (pending.id xor System.currentTimeMillis()).toInt() and 0x7FFFFFFF

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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        if (pending.type == "CREDIT") {
            title = "💰 Income Received: $formattedAmount"
            body = "Credited to ${pending.bankName}${if (!pending.accountLast4.isNullOrEmpty()) " (A/C **${pending.accountLast4})" else ""}. Tap to log category:"
            builder.setContentTitle(title).setContentText(body)

            // Button 1: Salary
            builder.addAction(
                createAction(context, notificationId, pending.id, "Salary", "INCOME", null, pending.amount, pending.bankName, "💼 Salary")
            )
            // Button 2: Business
            builder.addAction(
                createAction(context, notificationId, pending.id, "Freelance", "INCOME", null, pending.amount, pending.bankName, "💻 Business")
            )
            // Button 3: Other Income
            builder.addAction(
                createAction(context, notificationId, pending.id, "Other Income", "INCOME", null, pending.amount, pending.bankName, "🎁 Other")
            )
        } else {
            title = "💸 Payment Detected: $formattedAmount"
            val merchantStr = if (!pending.merchantOrPayee.isNullOrEmpty()) " for ${pending.merchantOrPayee}" else ""
            body = "Debited from ${pending.bankName}${if (!pending.accountLast4.isNullOrEmpty()) " (A/C **${pending.accountLast4})" else ""}$merchantStr"
            builder.setContentTitle(title).setContentText(body)

            // Button 1: Groceries
            builder.addAction(
                createAction(context, notificationId, pending.id, "Groceries & Utilities", "EXPENSE", null, pending.amount, pending.bankName, "🛒 Groceries")
            )
            // Button 2: Shopping
            builder.addAction(
                createAction(context, notificationId, pending.id, "Shopping", "EXPENSE", null, pending.amount, pending.bankName, "🛍️ Shopping")
            )

            // Button 3: [ ⇄ Transfer ] -> Triggers In-Place Swapping
            val swapIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_SWAP_TRANSFER_ACCOUNTS
                putExtra(EXTRA_PENDING_ID, pending.id)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_AMOUNT, pending.amount)
                putExtra(EXTRA_BANK_NAME, pending.bankName)
            }
            val swapPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 3,
                swapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "⇄ Transfer", swapPendingIntent)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    fun swapToTransferAccountsNotification(context: Context, notificationId: Int, pendingId: Long, amount: Double, bankName: String) {
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
            .setContentTitle("⇄ Transfer $formattedAmount")
            .setContentText("Select target account to transfer from $bankName:")
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Action 1: Secondary Bank
        builder.addAction(
            createAction(context, notificationId, pendingId, "Savings", "TRANSFER", "Secondary Bank", amount, bankName, "🏦 Secondary Bank")
        )
        // Action 2: Savings
        builder.addAction(
            createAction(context, notificationId, pendingId, "Savings", "TRANSFER", "Savings", amount, bankName, "🐷 Savings")
        )

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

    private fun createAction(
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
}
