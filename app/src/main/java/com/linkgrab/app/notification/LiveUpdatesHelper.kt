package com.linkgrab.app.notification

import android.content.Context
import android.content.Intent

/**
 * Helper for Live Updates notifications.
 * Uses foreground service for Android 16+ status bar island support.
 */
class LiveUpdatesHelper(private val context: Context) {

    /**
     * Start a progress notification (foreground service).
     */
    fun startProgress(
        title: String = "LinkGrab",
        content: String = "处理中...",
        progress: Int = 0,
        maxProgress: Int = 100,
        indeterminate: Boolean = false,
    ) {
        val intent = Intent(context, LiveUpdatesService::class.java).apply {
            action = LiveUpdatesService.ACTION_START
            putExtra(LiveUpdatesService.EXTRA_TITLE, title)
            putExtra(LiveUpdatesService.EXTRA_CONTENT, content)
            putExtra(LiveUpdatesService.EXTRA_PROGRESS, progress)
            putExtra(LiveUpdatesService.EXTRA_MAX_PROGRESS, maxProgress)
            putExtra(LiveUpdatesService.EXTRA_INDETERMINATE, indeterminate)
        }
        context.startForegroundService(intent)
    }

    /**
     * Update progress.
     */
    fun updateProgress(
        title: String = "LinkGrab",
        content: String = "处理中...",
        progress: Int = 0,
        maxProgress: Int = 100,
        indeterminate: Boolean = false,
    ) {
        val intent = Intent(context, LiveUpdatesService::class.java).apply {
            action = LiveUpdatesService.ACTION_UPDATE
            putExtra(LiveUpdatesService.EXTRA_TITLE, title)
            putExtra(LiveUpdatesService.EXTRA_CONTENT, content)
            putExtra(LiveUpdatesService.EXTRA_PROGRESS, progress)
            putExtra(LiveUpdatesService.EXTRA_MAX_PROGRESS, maxProgress)
            putExtra(LiveUpdatesService.EXTRA_INDETERMINATE, indeterminate)
        }
        context.startForegroundService(intent)
    }

    /**
     * Show finish notification and auto-remove.
     */
    fun finish(
        title: String = "LinkGrab",
        content: String = "完成",
    ) {
        val intent = Intent(context, LiveUpdatesService::class.java).apply {
            action = LiveUpdatesService.ACTION_FINISH
            putExtra(LiveUpdatesService.EXTRA_TITLE, title)
            putExtra(LiveUpdatesService.EXTRA_CONTENT, content)
        }
        context.startForegroundService(intent)
    }

    /**
     * Stop the foreground service immediately.
     */
    fun stop() {
        val intent = Intent(context, LiveUpdatesService::class.java).apply {
            action = LiveUpdatesService.ACTION_STOP
        }
        context.startService(intent)
    }
}
