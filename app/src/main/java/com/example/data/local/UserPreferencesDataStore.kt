package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random

private val Context.evaPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "eva_user_prefs")

class UserPreferencesDataStore(private val context: Context) {
    private val displayNameKey = stringPreferencesKey("display_name")
    private val hasSwipedKey = booleanPreferencesKey("has_swiped_music_feed")

    private val defaultRandomName: String by lazy {
        "Listener${Random.nextInt(1000, 10000)}"
    }

    val displayName: Flow<String> = context.evaPrefsDataStore.data.map { prefs ->
        val existing = prefs[displayNameKey]
        if (existing.isNullOrBlank()) defaultRandomName else existing
    }

    val hasSwipedMusicFeed: Flow<Boolean> = context.evaPrefsDataStore.data.map { prefs ->
        prefs[hasSwipedKey] ?: false
    }

    suspend fun ensureInitialDisplayName() {
        context.evaPrefsDataStore.edit { prefs ->
            if (prefs[displayNameKey].isNullOrBlank()) {
                prefs[displayNameKey] = defaultRandomName
            }
        }
    }

    suspend fun updateDisplayName(name: String) {
        val cleaned = name.trim().take(40)
        if (cleaned.isNotEmpty()) {
            context.evaPrefsDataStore.edit { prefs ->
                prefs[displayNameKey] = cleaned
            }
        }
    }

    suspend fun markSwipedMusicFeed() {
        context.evaPrefsDataStore.edit { prefs ->
            prefs[hasSwipedKey] = true
        }
    }
}
