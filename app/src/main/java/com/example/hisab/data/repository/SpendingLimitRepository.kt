package com.example.hisab.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

val Context.limitDataStore by preferencesDataStore(name = "hisab_limit_settings")

enum class LimitType(val displayName: String) {
    DAILY("Daily Limit"),
    WEEKLY("Weekly Limit"),
    MONTHLY("Monthly Limit"),
    SPECIFIC_DAY("Specific Day Limit")
}

data class SpendingLimitConfig(
    val type: LimitType = LimitType.DAILY,
    val amount: Double = 1000.0,
    val specificDate: LocalDate = LocalDate.now(),
    val isEnabled: Boolean = false
)

class SpendingLimitRepository(private val context: Context) {

    private object Keys {
        val LIMIT_TYPE = stringPreferencesKey("limit_type")
        val LIMIT_AMOUNT = doublePreferencesKey("limit_amount")
        val LIMIT_DATE = stringPreferencesKey("limit_date")
        val LIMIT_ENABLED = booleanPreferencesKey("limit_enabled")
    }

    val limitConfig: Flow<SpendingLimitConfig> = context.limitDataStore.data.map { prefs ->
        val typeStr = prefs[Keys.LIMIT_TYPE] ?: LimitType.DAILY.name
        val type = try { LimitType.valueOf(typeStr) } catch (e: Exception) { LimitType.DAILY }
        val amount = prefs[Keys.LIMIT_AMOUNT] ?: 1000.0
        val dateStr = prefs[Keys.LIMIT_DATE] ?: LocalDate.now().toString()
        val date = try { LocalDate.parse(dateStr) } catch (e: Exception) { LocalDate.now() }
        val isEnabled = prefs[Keys.LIMIT_ENABLED] ?: false

        SpendingLimitConfig(
            type = type,
            amount = amount,
            specificDate = date,
            isEnabled = isEnabled
        )
    }

    suspend fun saveLimitConfig(config: SpendingLimitConfig) {
        context.limitDataStore.edit { prefs ->
            prefs[Keys.LIMIT_TYPE] = config.type.name
            prefs[Keys.LIMIT_AMOUNT] = config.amount
            prefs[Keys.LIMIT_DATE] = config.specificDate.toString()
            prefs[Keys.LIMIT_ENABLED] = config.isEnabled
        }
    }

    suspend fun clearLimit() {
        context.limitDataStore.edit { prefs ->
            prefs[Keys.LIMIT_ENABLED] = false
        }
    }
}
