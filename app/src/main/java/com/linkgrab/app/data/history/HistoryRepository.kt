package com.linkgrab.app.data.history

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    private suspend fun save() {
        context.historyDataStore.edit { prefs ->
            prefs[historyKey] = json.encodeToString(_items.value)
        }
    }
}
