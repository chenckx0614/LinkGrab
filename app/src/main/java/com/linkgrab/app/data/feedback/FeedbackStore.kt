package com.linkgrab.app.data.feedback

import android.content.Context
import android.os.Build
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * 反馈功能的数据层：记录最近一次解析失败，供反馈页一键附带。
 *
 * 只存一条（新的覆盖旧的），且只存「诊断必需」的四样：平台、链接、错误、时间。
 * 不碰剪贴板和历史记录——反馈是给用户的，不是给 App 收集数据的。
 */
object FeedbackStore {

    data class Failure(
        val platform: String,
        val url: String,
        val error: String,
        val timestamp: Long,
    )

    private const val PREFS = "feedback"
    private const val KEY_LAST_FAILURE = "last_failure"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordFailure(context: Context, platform: String, url: String, error: String) {
        val json = JSONObject().apply {
            put("platform", platform)
            put("url", url)
            put("error", error.take(300))
            put("timestamp", System.currentTimeMillis())
        }
        prefs(context).edit().putString(KEY_LAST_FAILURE, json.toString()).apply()
    }

    fun lastFailure(context: Context): Failure? {
        val raw = prefs(context).getString(KEY_LAST_FAILURE, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            Failure(
                platform = json.optString("platform"),
                url = json.optString("url"),
                error = json.optString("error"),
                timestamp = json.optLong("timestamp"),
            )
        }.getOrNull()
    }

    fun clearFailure(context: Context) {
        prefs(context).edit().remove(KEY_LAST_FAILURE).apply()
    }

    /** 设备诊断行（不含任何标识符）：App 版本、系统版本、机型。 */
    fun deviceInfo(context: Context): List<String> {
        val appVersion = runCatching {
            val pm = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pm.versionName} (${pm.longVersionCode})"
        }.getOrDefault("unknown")
        return listOf(
            "App: $appVersion",
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }
}
