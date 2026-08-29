package com.example.hisab.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.example.hisab.data.backup.AutoBackupManager
import com.example.hisab.data.db.HisabDatabase
import com.example.hisab.data.db.entity.PendingTransactionEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Android implementations of the [TransactionProcessor] gateways declared in `SmsGateways.kt`.
 *
 * This is the only file in the SMS pipeline that touches `Context`. Everything upstream of it —
 * parser, registry, identity, decision, processor — stays pure Kotlin and unit-testable (`AGENTS.md`).
 */

/**
 * [SmsHashCache] backed by the same `SharedPreferences` file the old pipeline used, so an upgraded
 * install still recognises the keys it wrote before v3.2.1 (see `SmsHash.legacyBodyKey`).
 *
 * The file name is deliberately unchanged. Renaming it would orphan every existing key and make one
 * upgrade re-notify the whole catch-up window.
 */
class PrefsSmsHashCache(context: Context) : SmsHashCache {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun peek(key: String): Boolean = prefs.contains(key)

    /** INV-7: only ever called from the processor's post-commit tail. */
    override fun mark(vararg keys: String) {
        if (keys.isEmpty()) return
        prefs.edit().apply { keys.forEach { putBoolean(it, true) } }.apply()
    }

    override fun forget(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        const val PREFS_NAME = "sms_processed_hashes"
    }
}

/** [AtomicDb] over Room (INV-3). Rolls the whole block back if anything inside it throws. */
class RoomAtomicDb(private val db: HisabDatabase) : AtomicDb {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = db.withTransaction { block() }
}

/**
 * [SmsNotifier] that posts through [SmsNotificationHelper], gated on notifications actually being
 * postable.
 *
 * Returns `false` rather than throwing when notifications cannot be posted, which makes the
 * condition **retryable**: the claimed row keeps `notificationPostedAt = null`, stays visible on the
 * dashboard, and `TransactionProcessor.recoverUnnotified()` picks it up on a later app open — by
 * which time the user may well have granted the permission. The old receiver instead burned the
 * message hash *before* its permission check (`SmsReceiver.kt:218` vs `:236`), so a message that
 * arrived while POST_NOTIFICATIONS was denied was dead forever.
 */
class RealSmsNotifier(context: Context) : SmsNotifier {

    private val appContext = context.applicationContext

    override suspend fun postBankTransaction(pending: PendingTransactionEntity): Boolean {
        if (!canPost()) return false
        SmsNotificationHelper.postBankTransactionNotification(appContext, pending)
        return true
    }

    override suspend fun postMissedTransaction(
        pending: PendingTransactionEntity,
        actualBalance: Double,
        expectedBalance: Double
    ): Boolean {
        if (!canPost()) return false
        SmsNotificationHelper.postMissedTransactionNotification(
            appContext, pending, actualBalance, expectedBalance
        )
        return true
    }

    override suspend fun postAutoMerge(
        transactionId: Long,
        sourceAccount: String,
        targetAccount: String,
        amount: Double
    ): Boolean {
        if (!canPost()) return false
        SmsNotificationHelper.postAutoMergeSuccessNotification(
            appContext, transactionId, sourceAccount, targetAccount, amount
        )
        return true
    }

    /**
     * Both gates are treated as retryable-not-fatal. `notify()` does not throw when the user has
     * turned notifications off, it just does nothing — reporting that as a success would let the row
     * be marked notified and never retried.
     */
    private fun canPost(): Boolean {
        val permitted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }
}

/**
 * [BackupTrigger] over [AutoBackupManager]. Always the last thing the processor does, and always
 * time-boxed by the caller: it serialises the whole database to disk, and it used to sit *between*
 * the auto-merge commit and the notification inside a ~10 s `goAsync` budget (`SmsReceiver.kt:204`),
 * where a slow or throwing backup cost the user their notification entirely.
 */
class AutoBackupTrigger(context: Context, private val db: HisabDatabase) : BackupTrigger {
    private val manager = AutoBackupManager(context.applicationContext, db)
    override suspend fun perform() {
        manager.performBackup()
    }
}

/**
 * The single construction site for the SMS pipeline. This project has no DI container, so the
 * receiver, the catch-up scanner and `MainActivity`'s recovery pass all come through here — which is
 * what makes "one pipeline" (design principle 2) structural rather than a convention.
 */
fun buildTransactionProcessor(context: Context): TransactionProcessor {
    val appContext = context.applicationContext
    val db = HisabDatabase.getDatabase(appContext)
    return TransactionProcessor(
        pendingDao = db.pendingTransactionDao(),
        transactionDao = db.transactionDao(),
        accountDao = db.accountDao(),
        categoryDao = db.categoryDao(),
        cache = PrefsSmsHashCache(appContext),
        db = RoomAtomicDb(db),
        notifier = RealSmsNotifier(appContext),
        backup = AutoBackupTrigger(appContext, db),
        diagnostics = PrefsSmsDiagnosticsLog(appContext)
    )
}

/**
 * [SmsDiagnosticsLog] as a fixed-size ring buffer in its own `SharedPreferences` file, newest first.
 *
 * A single JSON array under one key rather than a Room table: the log must be writable from a
 * broadcast receiver that may be killed seconds later, must never contend with the database
 * transaction the pipeline is in the middle of, and must be readable even if the database is the thing
 * that is broken. It is also the reason the ring is capped — an unbounded diagnostic log is a slow
 * disk leak, and entries older than the last few messages have never once been useful.
 *
 * Corrupt or unreadable JSON is treated as an empty log. Losing diagnostics is a nuisance; throwing
 * from a diagnostic write would be a bug that costs the very notification it was meant to explain.
 */
class PrefsSmsDiagnosticsLog(context: Context) : SmsDiagnosticsLog {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun record(entry: SmsDiagnosticEntry) = synchronized(lock) {
        val existing = readArray()
        val updated = JSONArray()
        updated.put(entry.toJson())
        for (i in 0 until minOf(existing.length(), CAPACITY - 1)) {
            updated.put(existing.optJSONObject(i) ?: continue)
        }
        prefs.edit().putString(KEY_ENTRIES, updated.toString()).apply()
    }

    override suspend fun recent(): List<SmsDiagnosticEntry> = synchronized(lock) {
        val array = readArray()
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.toEntry() }
    }

    fun clear() = synchronized(lock) {
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    private fun readArray(): JSONArray = try {
        prefs.getString(KEY_ENTRIES, null)?.let { JSONArray(it) } ?: JSONArray()
    } catch (e: JSONException) {
        JSONArray()
    }

    private fun SmsDiagnosticEntry.toJson(): JSONObject = JSONObject().apply {
        put("ts", timestamp)
        put("sender", sender)
        amount?.let { put("amount", it) }
        put("origin", origin)
        put("outcome", outcome)
        reason?.let { put("reason", it) }
    }

    private fun JSONObject.toEntry(): SmsDiagnosticEntry = SmsDiagnosticEntry(
        timestamp = optLong("ts"),
        sender = optString("sender"),
        amount = if (has("amount")) optDouble("amount") else null,
        origin = optString("origin"),
        outcome = optString("outcome"),
        reason = if (has("reason")) optString("reason") else null
    )

    companion object {
        const val PREFS_NAME = "sms_diagnostics"
        private const val KEY_ENTRIES = "entries"

        /** Enough to cover a catch-up pass plus the messages around a reported failure. */
        const val CAPACITY = 50

        /**
         * Process-wide, because the receiver, the catch-up scanner and the Settings viewer each build
         * their own instance over the same prefs file, and the read-modify-write above is not atomic.
         */
        private val lock = Any()
    }
}
