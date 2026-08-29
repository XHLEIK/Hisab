package com.example.hisab.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans the SMS inbox for bank messages from the last 24 hours and feeds each one to
 * [TransactionProcessor] with [SmsOrigin.CATCHUP].
 *
 * Two things changed and both were defects, not features:
 *
 * 1. **It notifies now.** This scanner used to insert a pending row and post nothing, so a message it
 *    reached before the receiver did became a dashboard row the user was never told about. Since it
 *    also wrote the receiver's exact dedup keys into the receiver's exact `SharedPreferences` file,
 *    winning that race permanently suppressed the receiver's notification. That is root cause #9.
 * 2. **It shares one pipeline and one lock.** The duplicated gate logic is gone; the processor's
 *    class-level mutex serialises this loop against live broadcasts, so the two can no longer
 *    interleave on the same message.
 */
object SmsCatchUpSync {

    private const val TAG = "SmsCatchUpSync"
    private const val LOOKBACK_MS = 86_400_000L

    suspend fun runSync(context: Context) = withContext(Dispatchers.IO) {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                // Note that recovery of unnotified claims deliberately does NOT live here — it must
                // work on an install that only ever granted RECEIVE_SMS.
                Log.d(TAG, "READ_SMS not granted; skipping catch-up scan.")
                return@withContext
            }

            val processor = buildTransactionProcessor(context)
            val cutoff = System.currentTimeMillis() - LOOKBACK_MS

            context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("_id", "address", "body", "date"),
                "date >= ?",
                arrayOf(cutoff.toString()),
                "date DESC"
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndex("address")
                val bodyIdx = cursor.getColumnIndex("body")
                val dateIdx = cursor.getColumnIndex("date")

                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIdx) ?: ""
                    val body = cursor.getString(bodyIdx) ?: ""
                    val timestamp = cursor.getLong(dateIdx)
                    if (address.isEmpty() || body.isEmpty()) continue

                    // Body and address are passed through untouched: the legacy upgrade keys are
                    // computed from these exact strings (see SmsHash.legacyBodyKey).
                    when (val outcome = processor.process(address, body, timestamp, SmsOrigin.CATCHUP)) {
                        is ProcessingOutcome.NotATransaction -> Unit // the inbox is mostly not banks
                        else -> Log.d(TAG, "Catch-up: $address -> $outcome")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SmsCatchUpSync failed", e)
        }
    }
}
