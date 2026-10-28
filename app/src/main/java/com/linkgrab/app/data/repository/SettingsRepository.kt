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
        private val TOOLBOX_BROWSER_KEY = intPreferencesKey("toolbox_open_in_browser")
        private val CLIPBOARD_DETECT_KEY = intPreferencesKey("clipboard_auto_detect")
        private val SWIPE_SWITCH_KEY = intPreferencesKey("swipe_switch_pages")
        private val QUALITY_LABEL_KEY = stringPreferencesKey("preferred_quality_label")
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
     * 引导页（欢迎页）只在**第一次安装**时展示：本地没有任何记录（key 不存在）
     * 视为全新安装；看过一次就写入 1，之后升级、重装数据保留都不再出现。
     */
    val guideNeeded: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[GUIDE_SHOWN_KEY] == null
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

    /** 工具箱网页类工具是否用系统浏览器打开；默认关（应用内打开）。 */
    val toolboxOpenInBrowser: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[TOOLBOX_BROWSER_KEY] == 1
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
            preferences[GUIDE_SHOWN_KEY] = 1 // 看过欢迎页
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

    suspend fun setToolboxOpenInBrowser(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TOOLBOX_BROWSER_KEY] = if (enabled) 1 else 0
        }
    }

    /** 回前台时是否自动识别剪贴板里的分享链接（默认开）。 */
    val clipboardDetect: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CLIPBOARD_DETECT_KEY] != 0 // 默认开
    }

    suspend fun setClipboardDetect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CLIPBOARD_DETECT_KEY] = if (enabled) 1 else 0
        }
    }

    /** 主界面左右滑动手势是否切换页面（默认开；关掉后只能点底栏切页）。 */
    val swipeSwitchPages: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SWIPE_SWITCH_KEY] != 0 // 默认开
    }

    suspend fun setSwipeSwitchPages(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SWIPE_SWITCH_KEY] = if (enabled) 1 else 0
        }
    }

    /** 上次选的保存清晰度（按 label 存，如 "1080P"；空串表示没选过 → 用最高档）。 */
    val preferredQuality: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[QUALITY_LABEL_KEY] ?: ""
    }

    suspend fun setPreferredQuality(label: String) {
        context.dataStore.edit { preferences ->
            preferences[QUALITY_LABEL_KEY] = label
        }
    }
}
