package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.viewmodel.UserPreferencesState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.yemenDataStore: DataStore<Preferences> by preferencesDataStore(name = "yemen_manager_preferences")

data class PersistedAppConfig(
    val isArabicRtl: Boolean = true,
    val isDarkTheme: Boolean = false,
    val preferences: UserPreferencesState = UserPreferencesState()
)

/**
 * Offline persistent settings repository powered by Jetpack DataStore Preferences and Moshi JSON.
 * Guarantees instant, non-blocking startup and durable user settings without internet.
 */
class YemenPreferencesRepository(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val prefsJsonAdapter = moshi.adapter(UserPreferencesState::class.java)

    companion object {
        private val KEY_IS_ARABIC = booleanPreferencesKey("is_arabic_rtl")
        private val KEY_IS_DARK = booleanPreferencesKey("is_dark_theme")
        private val KEY_USER_PREFS_JSON = stringPreferencesKey("user_prefs_json")
    }

    val configFlow: Flow<PersistedAppConfig> = context.yemenDataStore.data.map { prefs ->
        val isArabic = prefs[KEY_IS_ARABIC] ?: true
        val isDark = prefs[KEY_IS_DARK] ?: false
        val json = prefs[KEY_USER_PREFS_JSON]
        val userPrefs = if (!json.isNullOrBlank()) {
            try {
                prefsJsonAdapter.fromJson(json) ?: UserPreferencesState()
            } catch (_: Exception) {
                UserPreferencesState()
            }
        } else {
            UserPreferencesState()
        }
        PersistedAppConfig(
            isArabicRtl = isArabic,
            isDarkTheme = isDark,
            preferences = userPrefs
        )
    }

    suspend fun saveLanguage(isArabic: Boolean) {
        context.yemenDataStore.edit { it[KEY_IS_ARABIC] = isArabic }
    }

    suspend fun saveTheme(isDark: Boolean) {
        context.yemenDataStore.edit { it[KEY_IS_DARK] = isDark }
    }

    suspend fun saveUserPreferences(state: UserPreferencesState) {
        val json = try {
            prefsJsonAdapter.toJson(state)
        } catch (_: Exception) {
            return
        }
        context.yemenDataStore.edit { it[KEY_USER_PREFS_JSON] = json }
    }

    fun exportPreferencesToJson(state: UserPreferencesState): String {
        return try {
            prefsJsonAdapter.indent("  ").toJson(state)
        } catch (_: Exception) {
            "{}"
        }
    }

    fun importPreferencesFromJson(json: String): UserPreferencesState? {
        return try {
            prefsJsonAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }
}
