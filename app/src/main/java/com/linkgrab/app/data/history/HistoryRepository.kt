package com.linkgrab.app.data.history

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.linkgrab.app.data.model.MediaResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    /** MediaResult snapshot; direct media URLs may expire. */
    val resultJson: String = "",
    val cacheExpireAt: Long = 0L,
    val author: String = "",
)

private val Context.historyDataStore by preferencesDataStore("history")

class HistoryRepository internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.historyDataStore)

    companion object {
        private const val MAX_ITEMS = 200
    }

    // Persist every field, including timestamp-based defaults. Otherwise a value
    // matching the serializer's freshly evaluated default can be omitted and a
    // later read silently generates a different record ID.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val historyKey = stringPreferencesKey("history_list")

    // DataStore is the source of truth: no asynchronous hydration can overwrite a
    // mutation. edit serializes read/modify/write, including across repo instances.
    val allHistory: Flow<List<HistoryItem>> = dataStore.data
        .map { readItems(it) }.distinctUntilChanged()

    private fun readItems(prefs: Preferences): List<HistoryItem> {
        val items = try {
            val stored = json.parseToJsonElement(prefs[historyKey] ?: "[]").jsonArray
            // Older releases could omit a timestamp-based default ID. Give such
            // entries a stable identity until the next edit persists every field.
            val normalized = stored.mapIndexed { index, element ->
                val fields = element.jsonObject
                if ("id" in fields) element else {
                    val url = fields["url"]?.jsonPrimitive?.content.orEmpty()
                    val id = UUID.nameUUIDFromBytes("$index|$url".toByteArray(Charsets.UTF_8))
                        .mostSignificantBits and Long.MAX_VALUE
                    JsonObject(fields + ("id" to JsonPrimitive(id)))
                }
            }
            json.decodeFromJsonElement<List<HistoryItem>>(JsonArray(normalized))
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
        // Older versions only stored the author inside the cached result.
        return items.map { item ->
            if (item.author.isNotBlank() || item.resultJson.isBlank()) item
            else item.copy(author = decodeResult(item.resultJson)?.author.orEmpty())
        }
    }

    private fun decodeResult(value: String): MediaResult? = try {
        json.decodeFromString<MediaResult>(value)
    } catch (_: SerializationException) {
        null
    }

    private suspend fun updateItems(transform: (List<HistoryItem>) -> List<HistoryItem>) {
        dataStore.edit { prefs ->
            prefs[historyKey] = json.encodeToString(transform(readItems(prefs)))
        }
    }

    suspend fun add(item: HistoryItem) {
        dataStore.edit { prefs ->
            val current = readItems(prefs).toMutableList()
            val old = current.firstOrNull {
                it.url == item.url || (dedupKey(it) == dedupKey(item) &&
                    (it.author.isBlank() || item.author.isBlank() || it.author == item.author))
            }
            val now = System.currentTimeMillis()
            // Reuse identity so pending selection/download/favorite actions still
            // refer to the same record after parsing it again.
            val id = old?.id ?: maxOf(now, (current.maxOfOrNull { it.id } ?: 0L) + 1)
            val hasNewCache = item.resultJson.isNotBlank()
            val merged = item.copy(
                id = id,
                parseTime = now,
                favorite = old?.favorite == true || item.favorite,
                downloaded = old?.downloaded == true || item.downloaded,
                localPath = old?.localPath?.takeIf { it.isNotBlank() } ?: item.localPath,
                author = item.author.ifBlank { old?.author.orEmpty() },
                cover = item.cover.ifBlank { old?.cover.orEmpty() },
                resultJson = if (hasNewCache) item.resultJson else old?.resultJson.orEmpty(),
                cacheExpireAt = if (hasNewCache) item.cacheExpireAt else old?.cacheExpireAt ?: 0L,
            )
            current.removeAll { it.id == id }
            current.add(0, merged)
            // Favorited items and saved-file metadata survive routine history pruning.
            while (current.size > MAX_ITEMS) {
                val index = current.indexOfLast { !it.favorite && !it.downloaded }
                if (index < 0) break
                current.removeAt(index)
            }
            prefs[historyKey] = json.encodeToString(current)
        }
    }

    private fun dedupKey(item: HistoryItem): String {
        val title = item.title.trim()
        return if (title.isEmpty() || title == "未命名") "url:${item.url}"
        else "${item.platform}|$title"
    }

    suspend fun delete(item: HistoryItem) = updateItems { items -> items.filterNot { it.id == item.id } }

    suspend fun deleteMany(items: List<HistoryItem>) {
        val ids = items.map { it.id }.toSet()
        if (ids.isNotEmpty()) updateItems { current -> current.filterNot { it.id in ids } }
    }

    suspend fun toggleFavorite(id: Long, favorite: Boolean) = updateItems { items ->
        items.map { if (it.id == id) it.copy(favorite = favorite) else it }
    }

    suspend fun cachedResult(url: String, ignoreTtl: Boolean = false): MediaResult? {
        val now = System.currentTimeMillis()
        val item = readItems(dataStore.data.first()).firstOrNull {
            it.url == url && (ignoreTtl || it.cacheExpireAt > now)
        } ?: return null
        return if (item.resultJson.isBlank()) null else decodeResult(item.resultJson)
    }

    suspend fun cacheResult(url: String, result: MediaResult, ttlMs: Long) {
        val encoded = json.encodeToString(result)
        updateItems { items ->
            items.map {
                if (it.url == url) it.copy(
                    resultJson = encoded,
                    cacheExpireAt = System.currentTimeMillis() + ttlMs,
                    author = result.author.ifBlank { it.author },
                ) else it
            }
        }
    }

    suspend fun findDownloadedByKey(platform: String, title: String): HistoryItem? {
        if (title.isBlank() || title.trim() == "未命名") return null
        val key = "$platform|${title.trim()}"
        return readItems(dataStore.data.first()).firstOrNull { dedupKey(it) == key && it.downloaded }
    }

    suspend fun markDownloaded(platform: String, title: String, localPath: String) {
        if (title.isBlank() || title.trim() == "未命名") return
        val key = "$platform|${title.trim()}"
        updateItems { items ->
            items.map {
                if (dedupKey(it) == key) it.copy(downloaded = true, localPath = it.localPath.ifEmpty { localPath }) else it
            }
        }
    }

    /** Download completion uses its enqueued URL, never the currently open result. */
    suspend fun markDownloadedByUrl(url: String, localPath: String) {
        if (url.isBlank()) return
        updateItems { items ->
            items.map {
                if (it.url == url) it.copy(downloaded = true, localPath = it.localPath.ifEmpty { localPath }) else it
            }
        }
    }
}
