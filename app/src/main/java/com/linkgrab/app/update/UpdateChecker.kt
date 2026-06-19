package com.linkgrab.app.update

import android.util.Log
import com.linkgrab.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Checks for updates from Gitee releases.
 */
class UpdateChecker {

    companion object {
        private const val TAG = "UpdateChecker"
        private const val GITEE_API = "https://gitee.com/api/v5/repos/chenckx0614/LinkGrab/releases/latest"
        private const val GITEE_RELEASES_URL = "https://gitee.com/chenckx0614/LinkGrab/releases"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Check for updates. Returns UpdateInfo if new version available, null if up-to-date.
     */
    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val currentVersion = BuildConfig.VERSION_NAME
            Log.d(TAG, "Current version: $currentVersion")

            // Try Gitee API first, fallback to GitHub
            val result = tryGiteeApi(currentVersion)
            if (result != null) return@withContext result

            // Fallback: try GitHub API
            tryGithubApi(currentVersion)
        } catch (e: Exception) {
            Log.e(TAG, "Check failed: ${e.message}")
            UpdateResult.CheckFailed(error = e.message ?: "网络连接失败")
        }
    }

    private fun tryGiteeApi(currentVersion: String): UpdateResult? {
        return try {
            val request = Request.Builder()
                .url(GITEE_API)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            if (response.code != 200) return null

            val release = json.decodeFromString<GiteeRelease>(body)
            val latestVersion = release.tag_name.removePrefix("v")

            // Skip if tag is not a valid version (e.g. "apk")
            if (!latestVersion.contains(".")) return null

            val releaseNotes = release.body ?: ""
            val downloadUrl = release.html_url ?: GITEE_RELEASES_URL

            Log.d(TAG, "Gitee latest: $latestVersion")

            if (isNewerVersion(latestVersion, currentVersion)) {
                UpdateResult.UpdateAvailable(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion,
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl,
                )
            } else {
                UpdateResult.UpToDate(currentVersion = currentVersion)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Gitee API failed: ${e.message}")
            null
        }
    }

    private fun tryGithubApi(currentVersion: String): UpdateResult {
        val request = Request.Builder()
            .url("https://api.github.com/repos/chenckx0614/LinkGrab/releases/latest")
            .header("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")

        if (response.code != 200) throw Exception("HTTP ${response.code}")

        val release = json.decodeFromString<GiteeRelease>(body)
        val latestVersion = release.tag_name.removePrefix("v")
        val releaseNotes = release.body ?: ""
        val downloadUrl = release.html_url ?: "https://github.com/chenckx0614/LinkGrab/releases"

        Log.d(TAG, "GitHub latest: $latestVersion")

        return if (isNewerVersion(latestVersion, currentVersion)) {
            UpdateResult.UpdateAvailable(
                currentVersion = currentVersion,
                latestVersion = latestVersion,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl,
            )
        } else {
            UpdateResult.UpToDate(currentVersion = currentVersion)
        }
    }

    /**
     * Compare version strings (e.g. "1.2.1" vs "1.2.0").
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

        val maxSize = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxSize) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}

@Serializable
data class GiteeRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String? = "",
    val html_url: String? = "",
    val published_at: String = "",
)

sealed class UpdateResult {
    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val releaseNotes: String,
        val downloadUrl: String,
    ) : UpdateResult()

    data class UpToDate(val currentVersion: String) : UpdateResult()

    data class CheckFailed(val error: String) : UpdateResult()
}
