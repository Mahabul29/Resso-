package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.data.model.EqSettings
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random

private val Context.evaPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "eva_user_prefs")

class UserPreferencesDataStore(private val context: Context) {
    private val displayNameKey = stringPreferencesKey("display_name")
    private val hasSwipedKey = booleanPreferencesKey("has_swiped_music_feed")
    private val usernameKey = stringPreferencesKey("username")
    private val musicLanguagesKey = stringSetPreferencesKey("music_languages")
    private val eqEnabledKey = booleanPreferencesKey("eq_enabled")
    private val eqPresetKey = stringPreferencesKey("eq_preset")
    private val eqLevelsKey = stringPreferencesKey("eq_levels")

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

    val username: Flow<String> = context.evaPrefsDataStore.data.map { prefs ->
        prefs[usernameKey].orEmpty()
    }

    /** Languages the listener picked in Settings. Empty = no preference (default mix). */
    val musicLanguages: Flow<Set<String>> = context.evaPrefsDataStore.data.map { prefs ->
        prefs[musicLanguagesKey] ?: emptySet()
    }

    val eqSettings: Flow<EqSettings> = context.evaPrefsDataStore.data.map { prefs ->
        EqSettings(
            enabled = prefs[eqEnabledKey] ?: false,
            preset = prefs[eqPresetKey] ?: "Normal",
            levels = prefs[eqLevelsKey]
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                .orEmpty()
        )
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

    /** Username is stored without "@"; only a-z, 0-9, "_" and "." are kept (max 20). */
    suspend fun updateUsername(username: String) {
        val cleaned = username.trim().removePrefix("@").lowercase()
            .filter { it.isLetterOrDigit() && it.code < 128 || it == '_' || it == '.' }
            .take(20)
        context.evaPrefsDataStore.edit { prefs ->
            if (cleaned.isEmpty()) prefs.remove(usernameKey) else prefs[usernameKey] = cleaned
        }
    }

    suspend fun saveMusicLanguages(languages: Set<String>) {
        context.evaPrefsDataStore.edit { prefs ->
            if (languages.isEmpty()) prefs.remove(musicLanguagesKey) else prefs[musicLanguagesKey] = languages
        }
    }

    suspend fun saveEqSettings(settings: EqSettings) {
        context.evaPrefsDataStore.edit { prefs ->
            prefs[eqEnabledKey] = settings.enabled
            prefs[eqPresetKey] = settings.preset
            prefs[eqLevelsKey] = settings.levels.joinToString(",")
        }
    }

    /** Used by "Delete account": wipes every saved preference. */
    suspend fun clearAll() {
        context.evaPrefsDataStore.edit { it.clear() }
    }

    suspend fun markSwipedMusicFeed() {
        context.evaPrefsDataStore.edit { prefs ->
            prefs[hasSwipedKey] = true
        }
    }
}
