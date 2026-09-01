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
    // ── NEW: 2-Stage Pipeline Actions ────────────────────────────────
    const val ACTION_SELECT_EXPENSE_CATEGORY = "com.example.hisab.ACTION_SELECT_EXPENSE_CATEGORY"
    const val ACTION_SELECT_INCOME_CATEGORY = "com.example.hisab.ACTION_SELECT_INCOME_CATEGORY"
    const val ACTION_SELECT_SPLIT_CATEGORY = "com.example.hisab.ACTION_SELECT_SPLIT_CATEGORY"
    const val ACTION_LOG_SPLIT = "com.example.hisab.ACTION_LOG_SPLIT"

    const val EXTRA_PENDING_ID = "extra_pending_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_CATEGORY_NAME = "extra_category_name"
    const val EXTRA_CATEGORY_ID = "extra_category_id"
    const val EXTRA_TRANSACTION_TYPE = "extra_transaction_type" // "EXPENSE", "INCOME", "TRANSFER"
    const val EXTRA_SOURCE_ACCOUNT = "extra_source_account"
    const val EXTRA_TO_ACCOUNT = "extra_to_account"
    const val EXTRA_AMOUNT = "extra_amount"
    const val EXTRA_BANK_NAME = "extra_bank_name"
    const val EXTRA_PAGE_INDEX = "extra_page_index"
    const val EXTRA_PAGINATION_MODE = "extra_pagination_mode" // "EXPENSE", "INCOME", "TRANSFER_DEBIT", "TRANSFER_CREDIT", "SPLIT"
    const val EXTRA_TX_ID = "extra_tx_id"
    const val EXTRA_IS_DEBIT = "extra_is_debit"

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

    // ══════════════════════════════════════════════════════════════════════
    //  STAGE 1: Intent Selection Notification (Expense / Income / Transfer)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Posts Stage 1 Bank Transaction Notification with 3 Intent Buttons:
     *
     * DEBIT:  [ 💸 Expense ] [ ⇄ Transfer ] [ ❌ Dismiss ]
     * CREDIT: [ 💰 Income ] [ ⇄ Transfer In ] [ ❌ Dismiss ]
     */
    suspend fun postBankTransactionNotification(
        context: Context,
        pending: PendingTransactionEntity
    ) {
        createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (pending.id * 1000L).toInt() and 0x7FFFFFFF

        val formattedAmount = CurrencyFormatter.format(pending.amount)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action (shared by both DEBIT and CREDIT)
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_DISMISS_NOTIFICATION
            putExtra(EXTRA_PENDING_ID, pending.id)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 100,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (pending.type == "CREDIT") {
            // ── CREDIT Stage 1: [ 💰 Income ] [ ⇄ Transfer In ] [ 🧾 Split ] ──
            // Dismiss is carried as deleteIntent (swipe), not a 4th visible action — keeps 3 visible actions
            // within the per-notification cap while preserving exactly-once handling.
            val title = "💰 Income Received: $formattedAmount"
            val body = "Credited to ${pending.bankName}${if (!pending.accountLast4.isNullOrEmpty()) " (A/C **${pending.accountLast4})" else ""}. Select type:"
            builder.setContentTitle(title).setContentText(body)

            // Button 1: [ 💰 Income ] → Stage 2 Income Category Picker
            val incomeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_SELECT_INCOME_CATEGORY
                putExtra(EXTRA_PENDING_ID, pending.id)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_AMOUNT, pending.amount)
                putExtra(EXTRA_BANK_NAME, pending.bankName)
            }
            val incomePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 1,
                incomeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "💰 Income", incomePendingIntent)

            // Button 2: [ ⇄ Transfer In ] → Source Account Picker
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

            // Button 3: [ 🧾 Split ] → Stage 2 Split reimbursement picker (never income)
            val splitIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_SELECT_SPLIT_CATEGORY
                putExtra(EXTRA_PENDING_ID, pending.id)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_AMOUNT, pending.amount)
                putExtra(EXTRA_BANK_NAME, pending.bankName)
            }
            val splitPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 3,
                splitIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "🧾 Split", splitPendingIntent)

        } else {
            // ── DEBIT Stage 1: [ 💸 Expense ] [ ⇄ Transfer ] [ ❌ Dismiss ] ──
            val merchantStr = if (!pending.merchantOrPayee.isNullOrEmpty()) " for ${pending.merchantOrPayee}" else ""
            val title = "💸 Payment Detected: $formattedAmount"
            val body = "Debited from ${pending.bankName}${if (!pending.accountLast4.isNullOrEmpty()) " (A/C **${pending.accountLast4})" else ""}$merchantStr. Select type:"
            builder.setContentTitle(title).setContentText(body)

            // Button 1: [ 💸 Expense ] → Stage 2 Expense Category Picker
            val expenseIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_SELECT_EXPENSE_CATEGORY
                putExtra(EXTRA_PENDING_ID, pending.id)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_AMOUNT, pending.amount)
                putExtra(EXTRA_BANK_NAME, pending.bankName)
            }
            val expensePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 1,
                expenseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "💸 Expense", expensePendingIntent)

            // Button 2: [ ⇄ Transfer ] → Transfer Account Picker
            val transferIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_SWAP_TRANSFER_ACCOUNTS
                putExtra(EXTRA_PENDING_ID, pending.id)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_AMOUNT, pending.amount)
                putExtra(EXTRA_BANK_NAME, pending.bankName)
            }
            val transferPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 2,
                transferIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "⇄ Transfer", transferPendingIntent)

            // Button 3: [ ❌ Dismiss ]
            builder.addAction(0, "❌ Dismiss", dismissPendingIntent)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STAGE 2: Category Picker Notification (Paginated 2 Categories + More)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Posts Stage 2 Category Picker Notification with dynamic per-category emoji:
     * [ ☕ Coffee ] [ 🍽️ Dining Out ] [ 🔄 More... ]
     */
    suspend fun postCategoryPickerNotification(
        context: Context,
        pendingId: Long,
        notificationId: Int,
        transactionType: String, // "EXPENSE" or "INCOME"
        amount: Double,
        bankName: String,
        pageIndex: Int = 0
    ) {
        createNotificationChannel(context)

        val db = HisabDatabase.getDatabase(context)
        val categoryDao = db.categoryDao()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val formattedAmount = CurrencyFormatter.format(amount)
        val txType = if (transactionType == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
        val categories = categoryDao.getAllSync().filter { it.type == txType }
        val totalCategories = if (categories.isNotEmpty()) categories.size else 2

        val idx1 = (pageIndex * 2) % totalCategories
        val idx2 = (pageIndex * 2 + 1) % totalCategories

        val cat1 = if (categories.isNotEmpty()) categories[idx1] else null
        val cat2 = if (categories.isNotEmpty() && idx1 != idx2) categories[idx2] else null

        val cat1Name = cat1?.name ?: if (transactionType == "INCOME") "Salary" else "Groceries & Utilities"
        val cat2Name = cat2?.name ?: if (transactionType == "INCOME") "Freelance" else "Shopping"

        val cat1Emoji = getCategoryEmoji(cat1?.iconName)
        val cat2Emoji = getCategoryEmoji(cat2?.iconName)

        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val typeLabel = if (transactionType == "INCOME") "Income" else "Expense"
        val typeEmoji = if (transactionType == "INCOME") "💰" else "💸"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$typeEmoji Select $typeLabel Category: $formattedAmount")
            .setContentText("Pick a category for this transaction:")
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        // Button 1: Category 1 with dynamic emoji
        builder.addAction(
            createLogAction(context, notificationId, pendingId, cat1Name, transactionType, null, amount, bankName, "$cat1Emoji $cat1Name")
        )

        // Button 2: Category 2 with dynamic emoji (only if different from cat1)
        if (cat2Name != cat1Name) {
            builder.addAction(
                createLogAction(context, notificationId, pendingId, cat2Name, transactionType, null, amount, bankName, "$cat2Emoji $cat2Name")
            )
        }

        // Button 3: [ 🔄 More... ] → Paginate to next categories
        val paginateIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_PAGINATE_NOTIFICATION
            putExtra(EXTRA_PENDING_ID, pendingId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_PAGE_INDEX, pageIndex + 1)
            putExtra(EXTRA_PAGINATION_MODE, transactionType) // "EXPENSE" or "INCOME"
            putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_BANK_NAME, bankName)
        }
        val paginatePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 3,
            paginateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, "🔄 More...", paginatePendingIntent)

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * Posts Stage-2 Split reimbursement picker — shows only expense categories that can absorb the credit
     * without going negative, ranked: remaining > 0 first, then most recently used, then largest gross.
     * One tap on a category directly materialises an EXPENSE/SPLIT_REIMBURSEMENT.
     */
    suspend fun postSplitCategoryPickerNotification(
        context: Context,
        pendingId: Long,
        notificationId: Int,
        amount: Double,
        bankName: String,
        pageIndex: Int = 0
    ) {
        createNotificationChannel(context)

        val db = HisabDatabase.getDatabase(context)
        val categoryDao = db.categoryDao()
        val transactionDao = db.transactionDao()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val formattedAmount = CurrencyFormatter.format(amount)
        val allExpenseCats = categoryDao.getAllSync().filter { it.type == TransactionType.EXPENSE }
        val allTx = transactionDao.getAllTransactionsSync()

        data class Ranked(val cat: CategoryEntity, val gross: Double, val reimbursed: Double, val remaining: Double, val lastUsed: Long)

        val ranked = allExpenseCats.mapNotNull { cat ->
            val gross = allTx.filter { it.categoryId == cat.id && it.type == TransactionType.EXPENSE && it.subtype != "SPLIT_REIMBURSEMENT" }.sumOf { it.amount }
            if (gross <= 0.0) return@mapNotNull null
            val reimbursed = allTx.filter { it.categoryId == cat.id && it.subtype == "SPLIT_REIMBURSEMENT" }.sumOf { it.amount }
            val remaining = gross - reimbursed
            if (remaining <= 0.0) return@mapNotNull null
            val lastUsed = allTx.filter { it.categoryId == cat.id && it.type == TransactionType.EXPENSE }.maxOfOrNull { it.createdAt } ?: 0L
            Ranked(cat, gross, reimbursed, remaining, lastUsed)
        }.sortedWith(compareByDescending<Ranked> { it.remaining }.thenByDescending { it.lastUsed }.thenByDescending { it.gross })

        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (ranked.isEmpty()) {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("🧾 Split — ₹200 split repayment")
                .setContentText("No eligible expense categories yet. Create an expense first.")
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
            notificationManager.notify(notificationId, builder.build())
            return
        }

        val total = ranked.size
        val idx1 = (pageIndex * 2) % total
        val idx2 = (pageIndex * 2 + 1) % total
        val r1 = ranked[idx1]
        val r2 = if (total > 1 && idx1 != idx2) ranked[idx2] else null

        val r1Emoji = getCategoryEmoji(r1.cat.iconName)
        val r2Emoji = r2?.let { getCategoryEmoji(it.cat.iconName) }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🧾 Split repayment — $formattedAmount")
            .setContentText("Choose expense category for this split:")
            .setSubText("Tap to reimburse ${r1.cat.name} — ${CurrencyFormatter.format(r1.remaining)} remaining")
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        builder.addAction(
            createSplitAction(context, notificationId, pendingId, r1.cat.id, r1.cat.name, amount, bankName, "$r1Emoji ${r1.cat.name}")
        )
        if (r2 != null && r2.cat.id != r1.cat.id) {
            builder.addAction(
                createSplitAction(context, notificationId, pendingId, r2.cat.id, r2.cat.name, amount, bankName, "$r2Emoji ${r2.cat.name}")
            )
        }

        val paginateIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_PAGINATE_NOTIFICATION
            putExtra(EXTRA_PENDING_ID, pendingId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_PAGE_INDEX, pageIndex + 1)
            putExtra(EXTRA_PAGINATION_MODE, "SPLIT")
            putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_BANK_NAME, bankName)
        }
        val paginatePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 3,
            paginateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, "🔄 More...", paginatePendingIntent)

        notificationManager.notify(notificationId, builder.build())
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Credit Inward Transfer Source Account Picker (Unchanged Architecture)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Credit Inward Transfer Source Account Picker Notification (Paginated 2 Accounts + [ 🔄 More... ])
     */
    suspend fun swapToCreditTransferAccountsNotification(
        context: Context,
        notificationId: Int,
        pendingId: Long,
        amount: Double,
        creditedBankName: String,
        pageIndex: Int = 0,
        isDebit: Boolean = false
    ) {
        val db = HisabDatabase.getDatabase(context)
        val accounts = db.accountDao().getAllSync()
        val availableSources = accounts.filter { !BankAliasRegistry.matches(it.bankCode, creditedBankName, null) }
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

        val titleText: String
        val bodyText: String
        if (isDebit) {
            titleText = "⇄ Transfer: $formattedAmount"
            bodyText = "Debited from $creditedBankName. Select where money was sent:"
        } else {
            titleText = "⇄ Transfer In: $formattedAmount"
            bodyText = "Select source account transferred to $creditedBankName:"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Action 1: Account 1
        builder.addAction(
            createInwardTransferAction(context, notificationId, pendingId, acc1Name, creditedBankName, amount, "🏦 $acc1Name", isDebit)
        )
        // Action 2: Account 2 (only if different)
        if (acc1Name != acc2Name) {
            builder.addAction(
                createInwardTransferAction(context, notificationId, pendingId, acc2Name, creditedBankName, amount, "🐷 $acc2Name", isDebit)
            )
        }

        // Action 3: [ 🔄 More Accounts... ] — only if there are more accounts than shown
        val remainingAfterPage = total - ((pageIndex + 1) * 2)
        if (remainingAfterPage > 0) {
            val paginateIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_PAGINATE_NOTIFICATION
                putExtra(EXTRA_PENDING_ID, pendingId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_PAGE_INDEX, pageIndex + 1)
                putExtra(EXTRA_PAGINATION_MODE, "TRANSFER_CREDIT")
                putExtra(EXTRA_AMOUNT, amount)
                putExtra(EXTRA_BANK_NAME, creditedBankName)
                putExtra(EXTRA_IS_DEBIT, isDebit)
            }
            val paginatePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 3,
                paginateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "🔄 More Accounts...", paginatePendingIntent)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Auto-Merge & Success Notifications
    // ══════════════════════════════════════════════════════════════════════

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

    /**
     * Posts a high-priority alert when a balance discrepancy indicates an unlogged transaction occurred without an SMS.
     */
    fun postMissedTransactionNotification(
        context: Context,
        pending: PendingTransactionEntity,
        actualBalance: Double,
        expectedBalance: Double
    ) {
        createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (pending.id * 1000L).toInt() and 0x7FFFFFFF

        val formattedAmount = CurrencyFormatter.format(pending.amount)
        val formattedActual = CurrencyFormatter.format(actualBalance)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            // The discrepancy is a *net*: it can be one transaction or several, in either direction.
            // The old copy ("an unlogged transaction of ₹X") asserted a count the arithmetic cannot
            // support, which is the same claim the dashboard card was fixed to stop making.
            .setContentTitle("⚠️ $formattedAmount of unlogged activity")
            .setContentText("Your $formattedActual bank balance doesn't match Hisab. Tap to review.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${pending.bankName} reports a balance of $formattedActual, which is $formattedAmount away from what Hisab expected. That gap is a net total — it may be one transaction or several. Tap to review and record it.")
            )

        notificationManager.notify(notificationId, builder.build())
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Dynamic Category Emoji Mapper
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Returns the category emoji directly, or maps legacy icon names to emojis.
     */
    fun getCategoryEmoji(iconName: String?): String {
        if (iconName.isNullOrBlank()) return "📋"

        // If it's already an emoji (non-ASCII character or length <= 4), return as-is
        val isEmoji = iconName.any { Character.isSurrogate(it) || Character.getType(it) == Character.OTHER_SYMBOL.toInt() || it.code > 127 }
        if (isEmoji) return iconName.trim()

        return when (iconName) {
            // Food & Beverage
            "ShoppingCart", "LocalGroceryStore", "GroceriesStore" -> "🛒"
            "Restaurant" -> "🍽️"
            "Fastfood", "Snacks" -> "🍔"
            "Coffee", "Cafe", "Tea" -> "☕"

            // Shopping & Fashion
            "ShoppingBag" -> "🛍️"
            "LocalMall", "Mall" -> "🏬"
            "Storefront", "Store" -> "🏪"
            "Checkroom", "TShirt", "Apparel", "Clothing" -> "👕"

            // Transport & Travel
            "DirectionsCar" -> "🚗"
            "DirectionsBus", "Bus" -> "🚌"
            "TwoWheeler", "Bike" -> "🏍️"
            "LocalGasStation", "Fuel", "Petrol" -> "⛽"
            "Flight" -> "✈️"
            "Hotel", "Stay" -> "🏨"

            // Home & Utilities
            "Home" -> "🏠"
            "Receipt", "ReceiptLong" -> "🧾"
            "ElectricalServices", "Electricity" -> "⚡"
            "WaterDrop", "Water" -> "💧"
            "Wifi", "Internet" -> "📶"
            "Lightbulb" -> "💡"
            "LocalLaundryService", "WashingMachine", "Laundry" -> "🧺"

            // Health & Fitness
            "LocalHospital" -> "🏥"
            "MedicalServices", "Pharmacy", "Medicine" -> "💊"
            "FitnessCenter" -> "💪"
            "DirectionsRun" -> "🏃"

            // Entertainment & Leisure
            "Movie" -> "🎬"
            "SportsEsports", "Gaming" -> "🎮"
            "Headphones", "Music" -> "🎧"
            "Tv", "Television" -> "📺"

            // Education & Work
            "School" -> "🎓"
            "Book", "Books" -> "📚"
            "Work" -> "💼"
            "Laptop" -> "💻"

            // Tech & Communication
            "Smartphone", "Subscriptions" -> "📱"
            "CameraAlt", "Photography" -> "📷"

            // Finance & Investment
            "AccountBalance" -> "🏦"
            "Savings" -> "🐷"
            "Stocks", "ShowChart" -> "📈"
            "TrendingUp" -> "📊"
            "PieChart" -> "🥧"
            "CreditCard" -> "💳"
            "Payments" -> "💵"
            "AccountBalanceWallet" -> "👛"
            "AutoGraph" -> "✨"
            "Lock" -> "🔒"

            // Social & Personal
            "People" -> "👥"
            "ChildCare", "Baby", "Kids" -> "👶"
            "Pets", "Animals" -> "🐾"
            "VolunteerActivism", "Charity", "Donation" -> "❤️"
            "CardGiftcard" -> "🎁"
            "ContentCut", "Salon", "Grooming" -> "✂️"

            // Repairs & DIY
            "Build", "Repairs", "Hardware" -> "🔧"

            // Transfer & Misc
            "SwapHoriz" -> "🔄"
            "AddCircle" -> "➕"
            "MoreHoriz" -> "📌"

            else -> iconName.take(2)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Private Action Builders
    // ══════════════════════════════════════════════════════════════════════

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
        buttonLabel: String,
        isDebit: Boolean = false
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_LOG_INWARD_TRANSFER
            putExtra(EXTRA_PENDING_ID, pendingId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_SOURCE_ACCOUNT, sourceAccount)
            putExtra(EXTRA_BANK_NAME, targetBankName)
            putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_IS_DEBIT, isDebit)
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

    private fun createSplitAction(
        context: Context,
        notificationId: Int,
        pendingId: Long,
        categoryId: Long,
        categoryName: String,
        amount: Double,
        bankName: String,
        buttonLabel: String
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_LOG_SPLIT
            putExtra(EXTRA_PENDING_ID, pendingId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_CATEGORY_ID, categoryId)
            putExtra(EXTRA_CATEGORY_NAME, categoryName)
            putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_BANK_NAME, bankName)
        }
        val requestCode = (notificationId + buttonLabel.hashCode() + categoryId.hashCode()) and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, buttonLabel, pendingIntent).build()
    }
}
