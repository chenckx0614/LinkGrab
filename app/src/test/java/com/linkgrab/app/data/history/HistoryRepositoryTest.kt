package com.linkgrab.app.data.history

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun record(url: String = "https://example.com/one", title: String = "作品") =
        HistoryItem(platform = "douyin", title = title, url = url, type = "video", author = "作者")

    private suspend fun <T> withRepository(file: File, block: suspend (HistoryRepository) -> T): T {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { file },
        )
        return try {
            block(HistoryRepository(store))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun repeatedParseRetainsIdentityFavoriteDownloadAndCache() = runBlocking {
        withRepository(File(temporaryFolder.root, "repeat.preferences_pb")) { repo ->
            repo.add(record())
            val result = MediaResult(MediaType.VIDEO, title = "作品", author = "作者", videoUrl = "https://cdn/video")
            repo.cacheResult(record().url, result, 60_000)
            repo.markDownloadedByUrl(record().url, "content://saved/original")
            repo.toggleFavorite(repo.allHistory.first().single().id, true)
            val before = repo.allHistory.first().single()
            val newUrl = "https://example.com/one?token=new"

            repo.add(record(url = newUrl).copy(author = ""))
            val after = repo.allHistory.first().single()
            assertEquals(before.id, after.id)
            assertEquals(before.copy(url = newUrl, parseTime = after.parseTime), after)
            assertEquals(result, repo.cachedResult(newUrl))
            repo.markDownloadedByUrl(newUrl, "content://saved/second")
            assertEquals("content://saved/original", repo.allHistory.first().single().localPath)
            repo.toggleFavorite(after.id, false)
            assertFalse(repo.allHistory.first().single().favorite)
        }
    }

    @Test fun legacyJsonDefaultsAndCachedAuthorAreRecovered() = runBlocking {
        val file = File(temporaryFolder.root, "legacy.preferences_pb")
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file },
        )
        try {
            val snapshot = Json.encodeToString(MediaResult(MediaType.VIDEO, author = "老作者"))
            store.edit {
                it[stringPreferencesKey("history_list")] = """
                    [{"id":7,"platform":"douyin","title":"旧记录","url":"old","type":"video"},
                     {"id":8,"platform":"web","title":"缓存记录","url":"cached","type":"video",
                      "resultJson":${Json.encodeToString(snapshot)},"futureField":true},
                     {"platform":"web","title":"无旧ID","url":"missing-id","type":"image"}]
                """.trimIndent()
            }
            val repo = HistoryRepository(store)
            val records = repo.allHistory.first()
            assertEquals("", records[0].author)
            assertFalse(records[0].favorite)
            assertFalse(records[0].downloaded)
            assertEquals("", records[0].resultJson)
            assertEquals(0L, records[0].cacheExpireAt)
            assertEquals("老作者", records[1].author)
            val recoveredId = records[2].id
            assertEquals(recoveredId, repo.allHistory.first()[2].id)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun favoritesAndDownloadedSurvivePruning() = runBlocking {
        withRepository(File(temporaryFolder.root, "prune.preferences_pb")) { repo ->
            repeat(210) { index ->
                repo.add(record(url = "https://example.com/$index", title = "作品 $index"))
            }
            assertEquals(200, repo.allHistory.first().size)
            val survivor = repo.allHistory.first().first()
            repo.toggleFavorite(survivor.id, true)
            repeat(5) { index ->
                repo.add(record(url = "https://example.com/new/$index", title = "新作品 $index"))
            }
            val records = repo.allHistory.first()
            assertEquals(200, records.size)
            assertTrue(records.any { it.id == survivor.id && it.favorite })
        }
    }

    @Test fun concurrentMutationsAndImmediateStartupDoNotLoseData() = runBlocking {
        val file = File(temporaryFolder.root, "races.preferences_pb")
        withRepository(file) { repo -> repo.add(record()) }
        withRepository(file) { repo ->
            // No initial flow subscription: mutation must read the persisted record.
            coroutineScope {
                repeat(20) { index ->
                    launch { repo.add(record(url = "https://example.com/$index", title = "作品 $index")) }
                }
                launch { repo.markDownloadedByUrl(record().url, "content://saved/original") }
                launch { repo.cacheResult(record().url, MediaResult(MediaType.VIDEO, author = "并发作者"), 60_000) }
            }
            val records = repo.allHistory.first()
            assertEquals(21, records.size)
            assertEquals(21, records.map { it.id }.toSet().size)
            val original = records.single { it.url == record().url }
            assertTrue(original.downloaded)
            assertEquals("并发作者", original.author)
            assertNotNull(repo.cachedResult(original.url))
        }
    }

    @Test fun exactUrlDownloadMarkingOnlyHitsMatchingRecord() = runBlocking {
        withRepository(File(temporaryFolder.root, "validation.preferences_pb")) { repo ->
            repo.add(record(url = "first").copy(author = "甲"))
            repo.add(record(url = "second").copy(author = "乙"))
            repo.markDownloadedByUrl("first", "content://saved/first")
            val records = repo.allHistory.first()
            assertTrue(records.single { it.url == "first" }.downloaded)
            assertFalse(records.single { it.url == "second" }.downloaded)
            repo.markDownloadedByUrl("", "content://saved/blank")
            assertEquals(2, repo.allHistory.first().size)
        }
    }
}
