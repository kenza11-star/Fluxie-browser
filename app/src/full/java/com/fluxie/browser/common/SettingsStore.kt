package com.fluxie.browser.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// One DataStore per process: this delegate must live at top level.
private val Context.fluxieDataStore: DataStore<Preferences> by preferencesDataStore(name = "fluxie_settings")

/**
 * Normal/Lite mode and appearance, persisted in DataStore. Writing here touches nothing else:
 * bookmarks, history and the other preferences live in their own stores, so switching mode
 * can never delete them.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.fluxieDataStore)

    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            AppSettings(
                mode = parseEnum(prefs[KEY_MODE], BrowserMode.NORMAL),
                themeMode = parseEnum(prefs[KEY_THEME], ThemeMode.SYSTEM),
                dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
                adultFilter = prefs[KEY_ADULT_FILTER] ?: false,
                adBlock = prefs[KEY_AD_BLOCK] ?: true
            )
        }

    suspend fun setMode(mode: BrowserMode) {
        dataStore.edit { it[KEY_MODE] = mode.name }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = themeMode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAdultFilter(enabled: Boolean) {
        dataStore.edit { it[KEY_ADULT_FILTER] = enabled }
    }

    suspend fun setAdBlock(enabled: Boolean) {
        dataStore.edit { it[KEY_AD_BLOCK] = enabled }
    }

    private companion object {
        val KEY_ADULT_FILTER = booleanPreferencesKey("adult_filter")
        val KEY_AD_BLOCK = booleanPreferencesKey("ad_block")
        val KEY_MODE = stringPreferencesKey("browser_mode")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
