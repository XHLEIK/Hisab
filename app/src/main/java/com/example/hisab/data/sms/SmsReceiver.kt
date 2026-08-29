package com.example.hisab.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reassembles multipart SMS and hands each message to [TransactionProcessor]. Nothing else.
 *
 * Everything this class used to do — the linked-account gate, the `SharedPreferences` dedup, the
 * manual-reconciliation check, the reconciliation key, the auto-merge engine, the balance sync, the
 * permission-gated notify — lived here in one ~200-line `onReceive`, duplicated in spirit inside
 * [SmsCatchUpSync], and produced nine distinct paths on which a valid bank SMS silently vanished.
 * All of it now lives in the processor, exercised by JVM tests. What remains is PDU handling, which
 * cannot be tested off-device, and the [handleSms] seam, which can.
 */
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

        // Parsing deliberately happens inside the processor, not here: a broadcast that returned
        // early on an unparseable body left no record of having seen the message at all.
        val appContext = context.applicationContext
        val timestamp = System.currentTimeMillis()
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outcome = handleSms(appContext, sender, rawBody, timestamp)
                Log.d(TAG, "SMS from $sender -> $outcome")
            } catch (e: Exception) {
                // The processor already converts throws into ProcessingOutcome.Failed; this is the
                // outer belt for anything thrown while constructing it.
                Log.e(TAG, "SMS processing failed for $sender", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"

        /**
         * The testable seam. An emulator/instrumentation test can drive the entire pipeline through
         * this without fabricating a PDU, and it is the same entry point the catch-up scanner uses.
         */
        internal suspend fun handleSms(
            context: Context,
            sender: String,
            body: String,
            timestamp: Long
        ): ProcessingOutcome = buildTransactionProcessor(context)
            .process(sender, body, timestamp, SmsOrigin.REALTIME)
    }
}
