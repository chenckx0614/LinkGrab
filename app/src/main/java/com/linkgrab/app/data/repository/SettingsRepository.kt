package com.linkgrab.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val COLOR_MODE_KEY = intPreferencesKey("color_mode")
        private val PREDICTIVE_BACK_KEY = intPreferencesKey("predictive_back")
        private val LIVE_UPDATES_KEY = intPreferencesKey("live_updates")
        private val GUIDE_SHOWN_KEY = intPreferencesKey("guide_shown")
        private val BOTTOM_BAR_STYLE_KEY = intPreferencesKey("bottom_bar_style")
        private val AUTO_CHECK_UPDATE_KEY = intPreferencesKey("auto_check_update")
        private val UPDATE_REMINDER_KEY = intPreferencesKey("update_reminder")
    }

    val colorMode: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[COLOR_MODE_KEY] ?: 0
    }

    val predictiveBack: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PREDICTIVE_BACK_KEY] ?: 2
    }

    val liveUpdatesEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LIVE_UPDATES_KEY] == 1
    }

    val guideShown: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[GUIDE_SHOWN_KEY] == 1
    }

    val bottomBarStyle: Flow<Int> = context.dataStore.data.map { preferences ->
        // 默认 Nexio 液态玻璃（0）；1-3 为 HyperOS4 / HarmonyOS7 / Material3
        preferences[BOTTOM_BAR_STYLE_KEY] ?: 0
    }

    val autoCheckUpdate: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_CHECK_UPDATE_KEY] != 0 // 默认开
    }

    val updateReminder: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[UPDATE_REMINDER_KEY] != 0 // 默认开
    }

    suspend fun setColorMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[COLOR_MODE_KEY] = mode
        }
    }

    suspend fun setPredictiveBack(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[PREDICTIVE_BACK_KEY] = mode
        }
    }

    suspend fun setLiveUpdatesEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LIVE_UPDATES_KEY] = if (enabled) 1 else 0
        }
    }

    suspend fun setGuideShown() {
        context.dataStore.edit { preferences ->
            preferences[GUIDE_SHOWN_KEY] = 1
        }
    }

    suspend fun setBottomBarStyle(style: Int) {
        context.dataStore.edit { preferences ->
            preferences[BOTTOM_BAR_STYLE_KEY] = style
        }
    }

    suspend fun setAutoCheckUpdate(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CHECK_UPDATE_KEY] = if (enabled) 1 else 0
        }
    }

    suspend fun setUpdateReminder(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[UPDATE_REMINDER_KEY] = if (enabled) 1 else 0
        }
    }
}
