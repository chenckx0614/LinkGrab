package com.linkgrab.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        /** 3.0 正式版的引导门槛：与本版 versionCode 一致（302）。 */
        const val GUIDE_VERSION_3_0 = 302

        private val COLOR_MODE_KEY = intPreferencesKey("color_mode")
        private val PREDICTIVE_BACK_KEY = intPreferencesKey("predictive_back")
        private val LIVE_UPDATES_KEY = intPreferencesKey("live_updates")
        private val GUIDE_SHOWN_KEY = intPreferencesKey("guide_shown")
        private val BOTTOM_BAR_STYLE_KEY = intPreferencesKey("bottom_bar_style")
        private val AUTO_CHECK_UPDATE_KEY = intPreferencesKey("auto_check_update")
        private val UPDATE_REMINDER_KEY = intPreferencesKey("update_reminder")
        private val DOWNLOAD_SOURCE_KEY = stringPreferencesKey("download_source")
        private val DOWNLOAD_CONCURRENCY_KEY = intPreferencesKey("download_concurrency")
        private val AUTO_ORGANIZE_KEY = intPreferencesKey("auto_organize")
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

    /**
     * 引导页展示版本：key 沿用 guide_shown，但语义改为「已看过的最新大版本号」。
     * 旧版本写入 1；3.0 正式版起写入 [GUIDE_VERSION_3_0]（versionCode 302）。
     * 小于门槛就要再展示一次——老用户升级 3.0 会看到新欢迎页，之后不再出现。
     * 门槛必须等于正式发布的 versionCode：3.0.0/3.0.1 测试包（301）已写过 300，
     * 若门槛停在 300，正式版会被误判为「已看过」。
     */
    val guideVersion: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[GUIDE_SHOWN_KEY] ?: 0
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

    /** 更新下载源："gitee"（默认）/ "github"。 */
    val downloadSource: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DOWNLOAD_SOURCE_KEY] ?: "gitee"
    }

    /** 下载队列并发数，默认 2（3.0 起可调 1-4）。 */
    val downloadConcurrency: Flow<Int> = context.dataStore.data.map { preferences ->
        (preferences[DOWNLOAD_CONCURRENCY_KEY] ?: 2).coerceIn(1, 4)
    }

    /** 下载完成按平台/日期自动归档子目录，默认开。 */
    val autoOrganize: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_ORGANIZE_KEY] != 0 // 默认开
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
            preferences[GUIDE_SHOWN_KEY] = GUIDE_VERSION_3_0 // 看完 3.0 欢迎页
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

    suspend fun setDownloadSource(source: String) {
        context.dataStore.edit { preferences ->
            preferences[DOWNLOAD_SOURCE_KEY] = source
        }
    }

    suspend fun setDownloadConcurrency(n: Int) {
        context.dataStore.edit { preferences ->
            preferences[DOWNLOAD_CONCURRENCY_KEY] = n.coerceIn(1, 4)
        }
    }

    suspend fun setAutoOrganize(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_ORGANIZE_KEY] = if (enabled) 1 else 0
        }
    }
}
