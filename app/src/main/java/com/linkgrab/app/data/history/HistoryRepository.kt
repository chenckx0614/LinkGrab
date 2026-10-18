package com.linkgrab.app.data.history

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.linkgrab.app.data.model.MediaResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class HistoryItem(
    val id: Long = System.currentTimeMillis(),
    val platform: String,
    val title: String,
    val cover: String = "",
    val url: String,
    val type: String,
    val parseTime: Long = System.currentTimeMillis(),
    val downloaded: Boolean = false,
    val favorite: Boolean = false,
    val localPath: String = "",
    /**
     * 解析结果的 JSON 快照（[com.linkgrab.app.data.model.MediaResult]）。
     * 有了它，同一链接再粘一次可以**秒开**、不再走网络；媒体直链有时效，
     * 所以和 [cacheExpireAt] 一起用，过期就当没有。
     */
    val resultJson: String = "",
    /** 缓存过期时间（epoch ms）；0 表示没缓存。 */
    val cacheExpireAt: Long = 0L,
)

private val Context.historyDataStore by preferencesDataStore("history")

class HistoryRepository(private val context: Context) {

    companion object {
        private const val MAX_ITEMS = 200
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val historyKey = stringPreferencesKey("history_list")

    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val allHistory: Flow<List<HistoryItem>> = _items.asStateFlow()

    init {
        scope.launch {
            val data = context.historyDataStore.data.first()
            val jsonStr = data[historyKey] ?: "[]"
            try {
                _items.value = json.decodeFromString<List<HistoryItem>>(jsonStr)
            } catch (e: Exception) {
                _items.value = emptyList()
            }
        }
    }

    suspend fun add(item: HistoryItem) {
        val current = _items.value.toMutableList()
        // 同一条内容只保留最新一条：先按去重键把旧记录摘掉，再把新的插到
        // 最前并刷新时间，避免反复解析同一个作品刷出一串重复项。
        val key = dedupKey(item)
        current.removeAll { dedupKey(it) == key }
        current.add(
            0,
            item.copy(id = System.currentTimeMillis(), parseTime = System.currentTimeMillis())
        )
        while (current.size > MAX_ITEMS) {
            current.removeLast()
        }
        _items.value = current
        save()
    }

    /**
     * 去重键：分享链接每次都可能带不同的 token（小红书 xsec_token、抖音短链
     * 后缀），直接拿 url 比会漏；标题为空或占位值时退回用 url。
     */
    private fun dedupKey(item: HistoryItem): String {
        val title = item.title.trim()
        return if (title.isEmpty() || title == "未命名") {
            "url:${item.url}"
        } else {
            "${item.platform}|$title"
        }
    }

    suspend fun delete(item: HistoryItem) {
        _items.value = _items.value.filter { it.id != item.id }
        save()
    }

    suspend fun deleteMany(items: List<HistoryItem>) {
        if (items.isEmpty()) return
        val ids = items.map { it.id }.toSet()
        _items.value = _items.value.filterNot { it.id in ids }
        save()
    }

    suspend fun toggleFavorite(id: Long, favorite: Boolean) {
        _items.value = _items.value.map {
            if (it.id == id) it.copy(favorite = favorite) else it
        }
        save()
    }

    // ==================== 解析缓存 & 已保存标记（3.0.16） ====================

    /**
     * 同一链接、缓存仍在有效期内 → 直接返回上次的解析结果，不再走网络。
     *
     * 按**原始分享链接**精确匹配：重新粘同一段分享文案（剪贴板/分享入口最常见
     * 的重复场景）时命中。换了 token 的同一条内容命中不了，那种情况靠
     * [findDownloadedByKey] 在解析之后补一个「已保存过」标记。
     */
    suspend fun cachedResult(url: String, ignoreTtl: Boolean = false): MediaResult? {
        val now = System.currentTimeMillis()
        val item = _items.value.firstOrNull {
            it.url == url && (ignoreTtl || it.cacheExpireAt > now)
        } ?: return null
        if (item.resultJson.isBlank()) return null
        return try {
            json.decodeFromString<MediaResult>(item.resultJson)
        } catch (_: Exception) {
            null
        }
    }

    /** 解析成功：把结果快照挂到对应的历史记录上（等 [add] 落库之后再调用）。 */
    suspend fun cacheResult(url: String, result: MediaResult, ttlMs: Long) {
        val jsonStr = try {
            json.encodeToString(result)
        } catch (_: Exception) {
            return
        }
        _items.value = _items.value.map {
            if (it.url == url) {
                it.copy(resultJson = jsonStr, cacheExpireAt = System.currentTimeMillis() + ttlMs)
            } else {
                it
            }
        }
        save()
    }

    /** 这条作品之前是否已经存过（按平台 + 标题去重，token 变了也认得）。 */
    suspend fun findDownloadedByKey(platform: String, title: String): HistoryItem? {
        if (title.isBlank() || title == "未命名") return null
        val key = "$platform|${title.trim()}"
        return _items.value.firstOrNull { dedupKey(it) == key && it.downloaded }
    }

    /** 下载成功：给这条作品打上「已保存」，并记下首个落盘路径。 */
    suspend fun markDownloaded(platform: String, title: String, localPath: String) {
        if (title.isBlank() || title == "未命名") return
        val key = "$platform|${title.trim()}"
        _items.value = _items.value.map {
            if (dedupKey(it) == key) {
                it.copy(downloaded = true, localPath = it.localPath.ifEmpty { localPath })
            } else {
                it
            }
        }
        save()
    }

    private suspend fun save() {
        context.historyDataStore.edit { prefs ->
            prefs[historyKey] = json.encodeToString(_items.value)
        }
    }
}
