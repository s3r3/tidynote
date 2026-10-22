package com.notarapi.tidynote.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.prefsStore: DataStore<Preferences> by preferencesDataStore(name = "tidynote_prefs")

/**
 * Preferensi terpusat di atas DataStore (plan §8).
 * Sekali jalan akan memigrasikan nilai lama dari SharedPreferences agar
 * onboarding/opt-in AI/token backend pengguna tidak hilang.
 */
class AppPreferences(
    private val context: Context,
) {
    val onboardingDone: Flow<Boolean> = context.prefsStore.data.map { it[KEY_ONBOARDING] ?: false }
    val cloudAiEnabled: Flow<Boolean> = context.prefsStore.data.map { it[KEY_AI] ?: false }
    val backendUrl: Flow<String> = context.prefsStore.data.map { it[KEY_URL].orEmpty() }
    val backendToken: Flow<String> =
        context.prefsStore.data.map {
            it[KEY_TOKEN] ?: it[KEY_LEGACY].orEmpty()
        }
    val currency: Flow<String> = context.prefsStore.data.map { it[KEY_CURRENCY] ?: "IDR" }

    /**
     * Pilihan tema disimpan sebagai nama enum (SYSTEM/LIGHT/DARK).
     * Nilai tak dikenal — mis. enum dihapus di versi berikutnya — jatuh ke SYSTEM
     * lewat [ThemeMode.fromStorage], bukan melempar.
     */
    val themeMode: Flow<ThemeMode> =
        context.prefsStore.data.map { ThemeMode.fromStorage(it[KEY_THEME]) }

    fun setOnboardingDone(done: Boolean) = edit { it[KEY_ONBOARDING] = done }

    fun setThemeMode(mode: ThemeMode) = edit { it[KEY_THEME] = mode.name }

    fun setCloudAiEnabled(enabled: Boolean) = edit { it[KEY_AI] = enabled }

    fun setBackendConfig(
        baseUrl: String,
        token: String,
    ) = edit {
        it[KEY_URL] = baseUrl.trim()
        it[KEY_TOKEN] = token
        it.remove(KEY_LEGACY)
    }

    fun setCurrency(code: String) = edit { it[KEY_CURRENCY] = code }

    suspend fun snapshot(): PrefsSnapshot {
        val d = context.prefsStore.data.first()
        return PrefsSnapshot(
            onboardingDone = d[KEY_ONBOARDING] ?: false,
            cloudAiEnabled = d[KEY_AI] ?: false,
            backendUrl = d[KEY_URL].orEmpty(),
            backendToken = d[KEY_TOKEN] ?: d[KEY_LEGACY].orEmpty(),
            currency = d[KEY_CURRENCY] ?: "IDR",
        )
    }

    /** Pindahkan nilai lama dari SharedPreferences sekali (aman dipanggil berulang). */
    suspend fun migrateFromSharedPrefs() {
        val legacy = context.getSharedPreferences("tidynote_prefs", Context.MODE_PRIVATE)
        if (!legacy.contains("datastore_migrated")) {
            context.prefsStore.edit { store ->
                legacy.getBoolean("onboarding_done", false).let { store[KEY_ONBOARDING] = it }
                legacy.getBoolean("cloud_ai_enabled", false).let { store[KEY_AI] = it }
                legacy.getString("llm_backend_url", null)?.let { store[KEY_URL] = it }
                val token =
                    legacy.getString("llm_backend_token", null)
                        ?: legacy.getString("llm_api_key", null)
                token?.let { store[KEY_TOKEN] = it }
                legacy.getString("currency", null)?.let { store[KEY_CURRENCY] = it }
            }
            legacy.edit().putBoolean("datastore_migrated", true).apply()
        }
    }

    private fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            context.prefsStore.edit(block)
        }
    }

    companion object {
        private val KEY_ONBOARDING = booleanPreferencesKey("onboarding_done")
        private val KEY_AI = booleanPreferencesKey("cloud_ai_enabled")
        private val KEY_URL = stringPreferencesKey("llm_backend_url")
        private val KEY_TOKEN = stringPreferencesKey("llm_backend_token")
        private val KEY_LEGACY = stringPreferencesKey("llm_api_key")
        private val KEY_CURRENCY = stringPreferencesKey("currency")
        private val KEY_THEME = stringPreferencesKey("theme_mode")
    }
}

data class PrefsSnapshot(
    val onboardingDone: Boolean = false,
    val cloudAiEnabled: Boolean = false,
    val backendUrl: String = "",
    val backendToken: String = "",
    val currency: String = "IDR",
)
