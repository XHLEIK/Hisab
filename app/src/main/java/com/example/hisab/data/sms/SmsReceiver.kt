package com.example.hisab.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.example.hisab.data.backup.AutoBackupManager
import com.example.hisab.data.db.HisabDatabase
import com.example.hisab.data.db.entity.PendingTransactionEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.LocalDate

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pdus = intent.extras?.get("pdus") as? Array<*> ?: return
        val format = intent.extras?.getString("format")

        val fullSmsBody = StringBuilder()
        var sender = ""

        for (pdu in pdus) {
            val sms = if (format != null) {
                SmsMessage.createFromPdu(pdu as ByteArray, format)
            } else {
                @Suppress("DEPRECATION")
                SmsMessage.createFromPdu(pdu as ByteArray)
            }
            fullSmsBody.append(sms.displayMessageBody)
            if (sender.isEmpty()) {
                sender = sms.displayOriginatingAddress ?: ""
            }
        }

        val rawBody = fullSmsBody.toString()
        if (rawBody.isEmpty() || sender.isEmpty()) return

        val parsed = SmsBankParser.parse(sender, rawBody) ?: return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = HisabDatabase.getDatabase(context)
                val pendingDao = db.pendingTransactionDao()
                val transactionDao = db.transactionDao()
                val accountDao = db.accountDao()
                val categoryDao = db.categoryDao()

                // ── Linked-Account Verification Gate ─────────────────────
                // Matches via BankAliasRegistry (e.g. "BOB" <-> "Bank of Baroda" / "BOBTXN"),
                // accountLast4, or fallback to single linked account.
                val linkedAccounts = accountDao.getAllSync()
                val matchedAccount = linkedAccounts.firstOrNull { account ->
                    val bankMatch = BankAliasRegistry.matches(account.bankCode, parsed.bankName, sender)
                    val last4Match = account.accountLast4 != null && parsed.accountLast4 != null &&
                            account.accountLast4 == parsed.accountLast4
                    bankMatch || last4Match
                } ?: if (linkedAccounts.size == 1) linkedAccounts.first() else null

                if (matchedAccount == null && linkedAccounts.isNotEmpty()) {
                    // Check if any account has bank code matching
                    val anyBankMatch = linkedAccounts.any {
                        BankAliasRegistry.matches(it.bankCode, parsed.bankName, sender)
                    }
                    if (!anyBankMatch) {
                        return@launch
                    }
                }

                // De-duplication Hash Check (7-day window)
                val msgHash = computeHash("$sender-${parsed.amount}-${parsed.type}-${rawBody.take(30)}")
                val prefs = context.getSharedPreferences("sms_processed_hashes", Context.MODE_PRIVATE)

                if (prefs.contains(msgHash)) {
                    return@launch
                }

                // ── Ending Balance Sync & Missed Transaction Detection ──
                if (parsed.endingBalance != null && matchedAccount != null) {
                    val prevBalance = matchedAccount.lastKnownBalance
                    if (prevBalance != null && prevBalance > 0) {
                        val expectedBalance = if (parsed.type == "DEBIT") {
                            prevBalance - parsed.amount
                        } else {
                            prevBalance + parsed.amount
                        }
                        val discrepancy = kotlin.math.abs(expectedBalance - parsed.endingBalance)
                        if (discrepancy >= 1.0) {
                            // A previous transaction occurred without an SMS alert!
                            val missedType = if (parsed.endingBalance < expectedBalance) "DEBIT" else "CREDIT"
                            val missedPending = PendingTransactionEntity(
                                amount = kotlin.math.round(discrepancy * 100.0) / 100.0,
                                type = missedType,
                                bankName = parsed.bankName,
                                accountLast4 = parsed.accountLast4 ?: matchedAccount.accountLast4,
                                merchantOrPayee = "Missed Transaction (Balance Sync)",
                                endingBalance = parsed.endingBalance,
                                rawSmsBody = "Auto-detected unlogged transaction via AvlBal discrepancy (Expected: ₹$expectedBalance, Actual: ₹${parsed.endingBalance})",
                                senderHeader = sender,
                                timestamp = System.currentTimeMillis() - 1000
                            )
                            val missedId = pendingDao.insert(missedPending)
                            val savedMissed = missedPending.copy(id = missedId)

                            if (android.os.Build.VERSION.SDK_INT < 33 ||
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                SmsNotificationHelper.postMissedTransactionNotification(
                                    context,
                                    savedMissed,
                                    parsed.endingBalance,
                                    expectedBalance
                                )
                            }
                        }
                    }

                    // Update account's last known balance in DB
                    val updatedAccount = matchedAccount.copy(
                        lastKnownBalance = parsed.endingBalance,
                        lastBalanceTimestamp = System.currentTimeMillis()
                    )
                    accountDao.update(updatedAccount)
                }

                // 1. Manual Entry Auto-Reconciliation Check (Past 24 Hours)
                val txType = if (parsed.type == "CREDIT") TransactionType.INCOME else TransactionType.EXPENSE
                val matchingManual = transactionDao.findMatchingManualTransaction(
                    parsed.amount,
                    txType.name,
                    LocalDate.now().minusDays(1)
                )

                if (matchingManual != null) {
                    val targetAcc = matchedAccount ?: linkedAccounts.firstOrNull {
                        BankAliasRegistry.matches(it.bankCode, parsed.bankName, sender)
                    }
                    if (targetAcc == null || matchingManual.account == targetAcc.name) {
                        // Suppress alert — this manual entry corresponds to this SMS
                        prefs.edit().putBoolean(msgHash, true).apply()
                        return@launch
                    }
                }

                // 2. Reconciliation Hash Check (Suppresses redundant CREDIT SMS if transfer was already logged)
                val reconKey = "recon_${parsed.amount}_${parsed.type}"
                if (prefs.contains(reconKey)) {
                    prefs.edit().remove(reconKey).putBoolean(msgHash, true).apply()
                    return@launch
                }

                // 3. 120-Second Inter-Account Transfer Auto-Merge Engine
                val oppositeType = if (parsed.type == "DEBIT") "CREDIT" else "DEBIT"
                val twoMinAgo = System.currentTimeMillis() - 120_000
                val matchingOppositePending = pendingDao.findMatchingOppositePending(parsed.amount, oppositeType, twoMinAgo)

                if (matchingOppositePending != null && !matchingOppositePending.bankName.equals(parsed.bankName, ignoreCase = true)) {
                    // Auto-Merge DEBIT + CREDIT alerts into a single TRANSFER transaction!
                    val accounts = linkedAccounts
                    val debitBankName = if (parsed.type == "DEBIT") parsed.bankName else matchingOppositePending.bankName
                    val creditBankName = if (parsed.type == "CREDIT") parsed.bankName else matchingOppositePending.bankName

                    val sourceAccount = accounts.firstOrNull { BankAliasRegistry.matches(it.bankCode, debitBankName) }
                        ?: accounts.firstOrNull { it.isPrimary } ?: accounts.firstOrNull()
                    val targetAccount = accounts.firstOrNull { BankAliasRegistry.matches(it.bankCode, creditBankName) }
                        ?: accounts.firstOrNull { it.name.contains("Savings", ignoreCase = true) } ?: accounts.lastOrNull()

                    val sourceName = sourceAccount?.name ?: debitBankName
                    val targetName = targetAccount?.name ?: creditBankName

                    val transferCategories = categoryDao.getAllSync().filter { it.type == TransactionType.TRANSFER }
                    val categoryId = transferCategories.firstOrNull()?.id ?: 1L

                    val transferTx = TransactionEntity(
                        amount = parsed.amount,
                        type = TransactionType.TRANSFER,
                        categoryId = categoryId,
                        date = LocalDate.now(),
                        account = sourceName,
                        toAccount = targetName,
                        notes = "Auto-merged inter-account transfer ($debitBankName -> $creditBankName)"
                    )

                    val newTxId = transactionDao.insert(transferTx)

                    // Delete the opposite pending transaction from DB
                    pendingDao.deleteById(matchingOppositePending.id)
                    prefs.edit().putBoolean(msgHash, true).apply()

                    // Auto-backup
                    val autoBackupManager = AutoBackupManager(context, db)
                    autoBackupManager.performBackup()

                    // Post Auto-Merge Notification with [ Undo ] button
                    SmsNotificationHelper.postAutoMergeSuccessNotification(
                        context,
                        newTxId,
                        sourceName,
                        targetName,
                        parsed.amount
                    )
                    return@launch
                }

                // If not auto-merged, save hash and insert pending transaction
                prefs.edit().putBoolean(msgHash, true).apply()

                val pendingEntity = PendingTransactionEntity(
                    amount = parsed.amount,
                    type = parsed.type,
                    bankName = parsed.bankName,
                    accountLast4 = parsed.accountLast4,
                    merchantOrPayee = parsed.merchantOrPayee,
                    endingBalance = parsed.endingBalance,
                    rawSmsBody = parsed.rawBody,
                    senderHeader = sender,
                    timestamp = System.currentTimeMillis()
                )

                val pendingId = pendingDao.insert(pendingEntity)
                val savedEntity = pendingEntity.copy(id = pendingId)

                // Post 2-Stage Interactive Heads-Up Notification
                if (android.os.Build.VERSION.SDK_INT < 33 ||
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    SmsNotificationHelper.postBankTransactionNotification(context, savedEntity)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun computeHash(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
