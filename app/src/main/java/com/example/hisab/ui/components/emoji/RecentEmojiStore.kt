package com.example.hisab.ui.components.emoji

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val MAX_RECENT = 24
private const val STORE_NAME = "hisab_recent_emoji"

val Context.recentEmojiDataStore by preferencesDataStore(name = STORE_NAME)

class RecentEmojiStore(private val context: Context) {

    private object Keys {
        val RECENT = stringPreferencesKey("recent_emojis_csv")
    }

    val recentEmojis: Flow<List<String>> = context.recentEmojiDataStore.data.map { prefs ->
        val csv = prefs[Keys.RECENT].orEmpty()
        if (csv.isBlank()) emptyList()
        else csv.split(",").filter { it.isNotBlank() }.take(MAX_RECENT)
    }

    suspend fun addRecent(emoji: String) {
        if (emoji.isBlank()) return
        val normalized = emoji.trim()
        context.recentEmojiDataStore.edit { prefs ->
            val current = prefs[Keys.RECENT]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toMutableList() ?: mutableListOf()
            current.remove(normalized)
            current.add(0, normalized)
            if (current.size > MAX_RECENT) {
                current.subList(MAX_RECENT, current.size).clear()
            }
            prefs[Keys.RECENT] = current.joinToString(",")
        }
    }

    suspend fun clear() {
        context.recentEmojiDataStore.edit { prefs ->
            prefs.remove(Keys.RECENT)
        }
    }
}
