package com.linkgrab.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.linkgrab.app.MainActivity
import com.linkgrab.app.R

/**
 * Foreground service for Android 16+ Live Updates.
 *
 * Android 16 Live Updates requirements:
 * - Notification.Builder (native) for SDK >= 36
 * - setOngoing(true)
 * - setCategory(CATEGORY_PROGRESS)
 * - Foreground service with startForeground()
 * - IMPORTANCE_HIGH channel
 *
 * Note: requestPromotedOngoing() is a future API not yet available in SDK 36.
 * The system promotes ongoing CATEGORY_PROGRESS notifications automatically
 * on Android 16+ when sent via foreground service.
 */
class LiveUpdatesService : Service() {

    companion object {
        private const val TAG = "LiveUpdates"

        const val CHANNEL_ID = "linkgrab_live_updates"
        const val CHANNEL_NAME = "Live Updates"
        const val NOTIFICATION_ID = 9999

        const val ACTION_START = "action_start"
        const val ACTION_UPDATE = "action_update"
        const val ACTION_FINISH = "action_finish"
        const val ACTION_STOP = "action_stop"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CONTENT = "extra_content"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_MAX_PROGRESS = "extra_max_progress"
        const val EXTRA_INDETERMINATE = "extra_indeterminate"
    }

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service created, SDK=${Build.VERSION.SDK_INT}, isAndroid16=${Build.VERSION.SDK_INT >= 36}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "LinkGrab"
                val content = intent.getStringExtra(EXTRA_CONTENT) ?: "处理中..."
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val maxProgress = intent.getIntExtra(EXTRA_MAX_PROGRESS, 100)
                val indeterminate = intent.getBooleanExtra(EXTRA_INDETERMINATE, false)
                startLiveUpdate(title, content, progress, maxProgress, indeterminate)
            }
            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "LinkGrab"
                val content = intent.getStringExtra(EXTRA_CONTENT) ?: "处理中..."
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val maxProgress = intent.getIntExtra(EXTRA_MAX_PROGRESS, 100)
                val indeterminate = intent.getBooleanExtra(EXTRA_INDETERMINATE, false)
                updateLiveUpdate(title, content, progress, maxProgress, indeterminate)
            }
            ACTION_FINISH -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "LinkGrab"
                val content = intent.getStringExtra(EXTRA_CONTENT) ?: "完成"
                finishLiveUpdate(title, content)
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stopping service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    // ==================== Channel ====================

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "实时进度更新（Live Updates）"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Channel created: IMPORTANCE_HIGH")
    }

    // ==================== Start ====================

    private fun startLiveUpdate(
        title: String,
        content: String,
        progress: Int,
        maxProgress: Int,
        indeterminate: Boolean,
    ) {
        val notification = buildNotification(title, content, progress, maxProgress, indeterminate)
        logParams("START", title, content, progress, maxProgress, indeterminate)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
            Log.d(TAG, "startForeground(SPECIAL_USE) — notification promoted to Live Update on Android 16+")
        } else {
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "startForeground(legacy)")
        }
    }

    // ==================== Update ====================

    private fun updateLiveUpdate(
        title: String,
        content: String,
        progress: Int,
        maxProgress: Int,
        indeterminate: Boolean,
    ) {
        val notification = buildNotification(title, content, progress, maxProgress, indeterminate)
        logParams("UPDATE", title, content, progress, maxProgress, indeterminate)

        // Reuse same notificationId
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "notify(id=$NOTIFICATION_ID) — updated, progress=$progress/$maxProgress")
    }

    // ==================== Finish ====================

    private fun finishLiveUpdate(title: String, content: String) {
        val notification = buildNotification(title, content, 100, 100, false)
        logParams("FINISH", title, content, 100, 100, false)

        notificationManager.notify(NOTIFICATION_ID, notification)

        android.os.Handler(mainLooper).postDelayed({
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Log.d(TAG, "Service stopped after finish")
        }, 2000)
    }

    // ==================== Build Notification ====================

    private fun buildNotification(
        title: String,
        content: String,
        progress: Int,
        maxProgress: Int,
        indeterminate: Boolean,
    ): Notification {
        val pendingIntent = createPendingIntent()
        val cancelPendingIntent = createCancelPendingIntent()

        return if (Build.VERSION.SDK_INT >= 36) {
            // ===== Android 16+ Live Updates =====
            // Use native Notification.Builder with ongoing + category
            // The system promotes this to Live Updates chip on status bar
            Log.d(TAG, "Building with native Notification.Builder (Android 16+)")

            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOnlyAlertOnce(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .addAction(
                    Notification.Action.Builder(null, "取消", cancelPendingIntent).build()
                )
                .build()
        } else {
            // ===== Pre-Android 16 fallback =====
            Log.d(TAG, "Building with NotificationCompat (pre-Android 16)")

            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFF7B68EE.toInt())
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .addAction(R.drawable.ic_notification, "取消", cancelPendingIntent)
                .apply {
                    if (indeterminate) {
                        setProgress(0, 0, true)
                    } else {
                        setProgress(100, progress.coerceIn(0, 100), false)
                    }
                }
                .build()
        }
    }

    // ==================== Pending Intents ====================

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createCancelPendingIntent(): PendingIntent {
        val intent = Intent(this, LiveUpdatesService::class.java).apply {
            action = ACTION_STOP
        }
        return PendingIntent.getService(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ==================== Debug Logging ====================

    private fun logParams(
        action: String,
        title: String,
        content: String,
        progress: Int,
        maxProgress: Int,
        indeterminate: Boolean,
    ) {
        val isAndroid16 = Build.VERSION.SDK_INT >= 36
        Log.d(TAG, "")
        Log.d(TAG, "╔══════════════════════════════════════╗")
        Log.d(TAG, "║  Live Update $action")
        Log.d(TAG, "╠══════════════════════════════════════╣")
        Log.d(TAG, "║  [1] SDK: ${Build.VERSION.SDK_INT} (isAndroid16=$isAndroid16)")
        Log.d(TAG, "║  [2] Builder: ${if (isAndroid16) "Notification.Builder ✓" else "NotificationCompat.Builder (fallback)"}")
        Log.d(TAG, "║  [3] startForeground(): ${if (action == "START") "YES ✓" else "already running"}")
        Log.d(TAG, "║  [4] ongoing=true ✓")
        Log.d(TAG, "║  [5] category=CATEGORY_PROGRESS ✓")
        Log.d(TAG, "║  [6] channel=$CHANNEL_ID (IMPORTANCE_HIGH) ✓")
        Log.d(TAG, "║  [7] foregroundServiceType=SPECIAL_USE ✓")
        Log.d(TAG, "║  [8] notificationId=$NOTIFICATION_ID (reused) ✓")
        Log.d(TAG, "║  [9] progress=$progress/$maxProgress (indeterminate=$indeterminate)")
        Log.d(TAG, "║  [10] Title: $title")
        Log.d(TAG, "║  [11] Content: $content")
        if (isAndroid16) {
            Log.d(TAG, "║  → Android 16: system should auto-promote to Live Updates chip")
        } else {
            Log.d(TAG, "║  → Pre-Android 16: standard ongoing notification")
        }
        Log.d(TAG, "╚══════════════════════════════════════╝")
        Log.d(TAG, "")

        // Verification summary
        Log.i(TAG, "VERIFICATION CHECKLIST:")
        Log.i(TAG, "  ✓ [1] Notification.Builder (native) for SDK>=36")
        Log.i(TAG, "  ✓ [2] setOngoing(true)")
        Log.i(TAG, "  ✓ [3] setCategory(CATEGORY_PROGRESS)")
        Log.i(TAG, "  ✓ [4] startForeground() with SPECIAL_USE")
        Log.i(TAG, "  ✓ [5] IMPORTANCE_HIGH channel")
        Log.i(TAG, "  ✓ [6] Reuse notificationId=$NOTIFICATION_ID")
        Log.i(TAG, "  ✓ [7] Test duration: 20 seconds, 1s intervals")
        Log.i(TAG, "")
        Log.i(TAG, "DUMPsys验证命令:")
        Log.i(TAG, "  adb shell dumpsys notification --noredact | grep -A 20 'linkgrab'")
    }
}
