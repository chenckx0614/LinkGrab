package com.linkgrab.app.data.download

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.linkgrab.app.viewmodel.DownloadTaskItem
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.downloadTaskDataStore: DataStore<Preferences> by preferencesDataStore(name = "download_tasks")

/**
 * 下载任务的持久层（3.3.10）：只保存**未完结**的任务到 DataStore。
 * 进程被杀（用户直接退出）后重启，能把没下完的任务捞回来接着下。
 * 半成品文件本身在 MediaStore（IS_PENDING=1），断点续传靠 partialUri + Range。
 */
class DownloadTaskStore(private val context: Context) {

    companion object {
        private val KEY = stringPreferencesKey("unfinished_tasks")
        private val json = Json { ignoreUnknownKeys = true }
    }

    suspend fun save(tasks: List<DownloadTaskItem>) {
        if (tasks.isEmpty()) {
            context.downloadTaskDataStore.edit { it.remove(KEY) }
            return
        }
        val data = json.encodeToString(tasks)
        context.downloadTaskDataStore.edit { it[KEY] = data }
    }

    suspend fun load(): List<DownloadTaskItem> {
        val raw = context.downloadTaskDataStore.data.first()[KEY] ?: return emptyList()
        // 结构不兼容/损坏时宁可丢任务列表，也别让启动崩掉
        return runCatching { json.decodeFromString<List<DownloadTaskItem>>(raw) }.getOrDefault(emptyList())
    }
}
