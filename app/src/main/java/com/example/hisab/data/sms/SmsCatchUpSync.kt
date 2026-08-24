package com.example.hisab.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.hisab.data.db.HisabDatabase
import com.example.hisab.data.db.entity.PendingTransactionEntity
import com.example.hisab.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.math.abs

object SmsCatchUpSync {

    private const val TAG = "SmsCatchUpSync"

    /**
     * Scans device SMS inbox for bank transactions from the last 24 hours.
     * Safely guarded with READ_SMS permission check to prevent SecurityException crashes.
     * Enforces a strict 3-tier exclusion filter + linked-account verification to guarantee zero duplicate entries.
     */
    suspend fun runSync(context: Context) = withContext(Dispatchers.IO) {
        try {
            // READ_SMS Permission Guard
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "READ_SMS permission not granted. Skipping catch-up sync.")
                return@withContext
            }

            val db = HisabDatabase.getDatabase(context)
            val pendingDao = db.pendingTransactionDao()
            val transactionDao = db.transactionDao()
            val accountDao = db.accountDao()
            val prefs = context.getSharedPreferences("sms_processed_hashes", Context.MODE_PRIVATE)

            // ── Pre-load linked accounts for whitelist verification ────
            val linkedAccounts = accountDao.getAllSync()

            val twentyFourHoursAgo = System.currentTimeMillis() - 86_400_000L
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date")
            val selection = "date >= ?"
            val selectionArgs = arrayOf(twentyFourHoursAgo.toString())
            val sortOrder = "date DESC"

            val existingPending = pendingDao.getAllPendingSync()
            val recentTransactions = transactionDao.getTransactionsBetweenSync(
                LocalDate.now().minusDays(3),
                LocalDate.now()
            )

            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val addressIdx = cursor.getColumnIndex("address")
                val bodyIdx = cursor.getColumnIndex("body")
                val dateIdx = cursor.getColumnIndex("date")

                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIdx) ?: ""
                    val body = cursor.getString(bodyIdx) ?: ""
                    val timestamp = cursor.getLong(dateIdx)

                    if (address.isEmpty() || body.isEmpty()) continue

                    val parsed = SmsBankParser.parse(address, body) ?: continue

                    // ── Linked-Account Verification Gate ─────────────────────
                    val matchedAccount = linkedAccounts.firstOrNull { account ->
                        val bankMatch = BankAliasRegistry.matches(account.bankCode, parsed.bankName, address)
                        val last4Match = account.accountLast4 != null && parsed.accountLast4 != null &&
                                account.accountLast4 == parsed.accountLast4
                        bankMatch || last4Match
                    } ?: if (linkedAccounts.size == 1) linkedAccounts.first() else null

                    if (matchedAccount == null && linkedAccounts.isNotEmpty()) {
                        val anyBankMatch = linkedAccounts.any {
                            BankAliasRegistry.matches(it.bankCode, parsed.bankName, address)
                        }
                        if (!anyBankMatch) {
                            continue
                        }
                    }

                    // ── Tier 1: Hash Check ──────────────────────────────────────
                    val msgHash1 = computeHash("$address-${parsed.amount}-${parsed.type}-$timestamp")
                    val msgHash2 = computeHash("$address-${parsed.amount}-${parsed.type}-${body.take(30)}")
                    if (prefs.contains(msgHash1) || prefs.contains(msgHash2)) {
                        continue
                    }

                    // ── Tier 2: 30-Minute Transaction & Pending Window Match ─────
                    val has30MinPendingMatch = existingPending.any { pending ->
                        pending.amount == parsed.amount &&
                                pending.type.equals(parsed.type, ignoreCase = true) &&
                                abs(pending.timestamp - timestamp) <= 30 * 60 * 1000L
                    }

                    val has30MinLoggedMatch = recentTransactions.any { tx ->
                        val txTypeStr = if (parsed.type == "CREDIT") "INCOME" else "EXPENSE"
                        tx.amount == parsed.amount &&
                                (tx.type.name.equals(txTypeStr, ignoreCase = true) || tx.type == TransactionType.TRANSFER) &&
                                abs(tx.createdAt - timestamp) <= 30 * 60 * 1000L
                    }

                    if (has30MinPendingMatch || has30MinLoggedMatch) {
                        prefs.edit().putBoolean(msgHash1, true).putBoolean(msgHash2, true).apply()
                        continue
                    }

                    // ── Manual Entry Auto-Reconciliation (Account-Aware) ──────
                    val txType = if (parsed.type == "CREDIT") TransactionType.INCOME else TransactionType.EXPENSE
                    val matchingManual = transactionDao.findMatchingManualTransaction(
                        parsed.amount,
                        txType.name,
                        LocalDate.now().minusDays(1)
                    )

                    if (matchingManual != null) {
                        val targetAcc = matchedAccount ?: linkedAccounts.firstOrNull {
                            BankAliasRegistry.matches(it.bankCode, parsed.bankName, address)
                        }
                        if (targetAcc == null || matchingManual.account == targetAcc.name) {
                            prefs.edit().putBoolean(msgHash1, true).putBoolean(msgHash2, true).apply()
                            continue
                        }
                    }

                    // ── Tier 3: Balance Verification ─────────────────────────────
                    if (parsed.endingBalance != null) {
                        val targetAcc = matchedAccount ?: linkedAccounts.firstOrNull {
                            BankAliasRegistry.matches(it.bankCode, parsed.bankName, address)
                        }
                        if (targetAcc != null) {
                            val accountName = targetAcc.name
                            val accountTxs = recentTransactions.filter { it.account == accountName || it.toAccount == accountName }
                            val totalIncome = accountTxs.filter { it.type == TransactionType.INCOME || (it.type == TransactionType.TRANSFER && it.toAccount == accountName) }.sumOf { it.amount }
                            val totalExpense = accountTxs.filter { it.type == TransactionType.EXPENSE || (it.type == TransactionType.TRANSFER && it.account == accountName) }.sumOf { it.amount }
                            val netBalance = totalIncome - totalExpense
                            if (abs(netBalance - parsed.endingBalance) < 1.0) {
                                prefs.edit().putBoolean(msgHash1, true).putBoolean(msgHash2, true).apply()
                                continue
                            }
                        }
                    }

                    // Add to pending_transactions for 1-tap review on Dashboard
                    val pendingEntity = PendingTransactionEntity(
                        amount = parsed.amount,
                        type = parsed.type,
                        bankName = parsed.bankName,
                        accountLast4 = parsed.accountLast4,
                        merchantOrPayee = parsed.merchantOrPayee,
                        endingBalance = parsed.endingBalance,
                        rawSmsBody = parsed.rawBody,
                        senderHeader = address,
                        timestamp = timestamp
                    )

                    pendingDao.insert(pendingEntity)
                    prefs.edit().putBoolean(msgHash1, true).putBoolean(msgHash2, true).apply()
                    Log.d(TAG, "Catch-up sync added unlogged bank transaction: ${parsed.amount} (${parsed.bankName})")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SmsCatchUpSync failed", e)
        }
    }

    private fun computeHash(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
