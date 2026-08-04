package com.example.hisab.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.example.hisab.data.db.HisabDatabase
import com.example.hisab.data.db.entity.PendingTransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest

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

                // De-duplication Hash Check (7-day window)
                val msgHash = computeHash("$sender-${parsed.amount}-${parsed.type}-${rawBody.take(30)}")
                val prefs = context.getSharedPreferences("sms_processed_hashes", Context.MODE_PRIVATE)

                if (prefs.contains(msgHash)) {
                    // Duplicate SMS, ignore
                    return@launch
                }

                // Save hash
                prefs.edit().putBoolean(msgHash, true).apply()

                val pendingEntity = PendingTransactionEntity(
                    amount = parsed.amount,
                    type = parsed.type,
                    bankName = parsed.bankName,
                    accountLast4 = parsed.accountLast4,
                    merchantOrPayee = parsed.merchantOrPayee,
                    rawSmsBody = parsed.rawBody,
                    senderHeader = sender,
                    timestamp = System.currentTimeMillis()
                )

                val pendingId = pendingDao.insert(pendingEntity)
                val savedEntity = pendingEntity.copy(id = pendingId)

                // Post 3-Action Interactive Notification
                SmsNotificationHelper.postBankTransactionNotification(context, savedEntity)

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
