package com.example.hisab.data.backup

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.autoBackupDataStore by preferencesDataStore(name = "hisab_auto_backup_prefs")

class BackupPreferences(private val context: Context) {

    private object Keys {
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")
        val BACKUP_CARD_DISMISSED_MONTH = stringPreferencesKey("backup_card_dismissed_year_month")
    }

    val isAutoBackupEnabled: Flow<Boolean> = context.autoBackupDataStore.data.map { prefs ->
        prefs[Keys.AUTO_BACKUP_ENABLED] ?: true
    }

    val lastBackupTime: Flow<Long> = context.autoBackupDataStore.data.map { prefs ->
        prefs[Keys.LAST_BACKUP_TIME] ?: 0L
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.autoBackupDataStore.edit { prefs ->
            prefs[Keys.AUTO_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun updateLastBackupTime(timestamp: Long = System.currentTimeMillis()) {
        context.autoBackupDataStore.edit { prefs ->
            prefs[Keys.LAST_BACKUP_TIME] = timestamp
        }
    }

    val dismissedBackupCardMonth: Flow<String> = context.autoBackupDataStore.data.map { prefs ->
        prefs[Keys.BACKUP_CARD_DISMISSED_MONTH] ?: ""
    }

    suspend fun setDismissedBackupCardMonth(yearMonth: String) {
        context.autoBackupDataStore.edit { prefs ->
            prefs[Keys.BACKUP_CARD_DISMISSED_MONTH] = yearMonth
        }
    }

    suspend fun clearDismissedBackupCardMonth() {
        context.autoBackupDataStore.edit { prefs ->
            prefs.remove(Keys.BACKUP_CARD_DISMISSED_MONTH)
        }
    }
}
