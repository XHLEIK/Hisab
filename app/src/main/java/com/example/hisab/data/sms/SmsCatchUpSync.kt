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

object SmsCatchUpSync {

    private const val TAG = "SmsCatchUpSync"

    /**
     * Scans device SMS inbox for bank transactions from the last 24 hours.
     * Safely guarded with READ_SMS permission check to prevent SecurityException crashes.
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
            val prefs = context.getSharedPreferences("sms_processed_hashes", Context.MODE_PRIVATE)

            val twentyFourHoursAgo = System.currentTimeMillis() - 86_400_000L
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date")
            val selection = "date >= ?"
            val selectionArgs = arrayOf(twentyFourHoursAgo.toString())
            val sortOrder = "date DESC"

            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val addressIdx = cursor.getColumnIndex("address")
                val bodyIdx = cursor.getColumnIndex("body")
                val dateIdx = cursor.getColumnIndex("date")

                val existingPending = pendingDao.getAllPendingSync()
                val existingPendingFingerprints = existingPending.map { "${it.amount}_${it.rawSmsBody}" }.toSet()

                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIdx) ?: ""
                    val body = cursor.getString(bodyIdx) ?: ""
                    val timestamp = cursor.getLong(dateIdx)

                    if (address.isEmpty() || body.isEmpty()) continue

                    val parsed = SmsBankParser.parse(address, body) ?: continue

                    // Hash Check
                    val msgHash = computeHash("$address-${parsed.amount}-${parsed.type}-${body.take(30)}")
                    if (prefs.contains(msgHash)) continue

                    // Manual Transaction Auto-Reconciliation (Past 24 hours)
                    val txType = if (parsed.type == "CREDIT") TransactionType.INCOME else TransactionType.EXPENSE
                    val matchingManual = transactionDao.findMatchingManualTransaction(
                        parsed.amount,
                        txType.name,
                        LocalDate.now().minusDays(1)
                    )

                    if (matchingManual != null) {
                        prefs.edit().putBoolean(msgHash, true).apply()
                        continue
                    }

                    // Check if already in pending table
                    val pf = "${parsed.amount}_${parsed.rawBody}"
                    if (existingPendingFingerprints.contains(pf)) {
                        prefs.edit().putBoolean(msgHash, true).apply()
                        continue
                    }

                    // Add to pending_transactions for 1-tap review on Dashboard
                    val pendingEntity = PendingTransactionEntity(
                        amount = parsed.amount,
                        type = parsed.type,
                        bankName = parsed.bankName,
                        accountLast4 = parsed.accountLast4,
                        merchantOrPayee = parsed.merchantOrPayee,
                        rawSmsBody = parsed.rawBody,
                        senderHeader = address,
                        timestamp = timestamp
                    )

                    pendingDao.insert(pendingEntity)
                    prefs.edit().putBoolean(msgHash, true).apply()
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
