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
import kotlinx.coroutines.flow.map
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val historyKey = stringPreferencesKey("history_list")

    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val allHistory: Flow<List<HistoryItem>> = _items.asStateFlow()
    val favorites: Flow<List<HistoryItem>> = _items.map { list -> list.filter { it.favorite } }
    val downloaded: Flow<List<HistoryItem>> = _items.map { list -> list.filter { it.downloaded } }

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
        current.add(0, item)
        _items.value = current
        save()
    }

    suspend fun delete(item: HistoryItem) {
        _items.value = _items.value.filter { it.id != item.id }
        save()
    }

    suspend fun deleteById(id: Long) {
        _items.value = _items.value.filter { it.id != id }
        save()
    }

    suspend fun toggleFavorite(id: Long, favorite: Boolean) {
        _items.value = _items.value.map {
            if (it.id == id) it.copy(favorite = favorite) else it
        }
        save()
    }

    suspend fun markDownloaded(id: Long, localPath: String = "") {
        _items.value = _items.value.map {
            if (it.id == id) it.copy(downloaded = true, localPath = localPath) else it
        }
        save()
    }

    private suspend fun save() {
        context.historyDataStore.edit { prefs ->
            prefs[historyKey] = json.encodeToString(_items.value)
        }
    }
}
