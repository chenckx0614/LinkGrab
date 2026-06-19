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
    }

    val colorMode: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[COLOR_MODE_KEY] ?: 0
    }

    val predictiveBack: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PREDICTIVE_BACK_KEY] ?: 2 // 默认关闭
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
}
