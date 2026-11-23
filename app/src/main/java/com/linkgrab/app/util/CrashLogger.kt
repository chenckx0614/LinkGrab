package com.linkgrab.app.util

import android.content.Context
import com.linkgrab.app.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃落盘：把未捕获异常连同版本、机型信息写进 `filesDir/crash/`。
 *
 * 不做联网上报，只留本地让用户导出 —— 线上闪退如果连一行堆栈都没有，
 * 只能靠猜是解析、下载还是某个机型的问题。
 */
object CrashLogger {
    private const val DIR_NAME = "crash"
    private const val MAX_FILES = 3

    /** 在 Application.onCreate 里装一次即可；会把异常继续交给系统处理（照常弹崩溃）。 */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(app, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 最近的崩溃，新的在前。 */
    fun files(context: Context): List<File> =
        dir(context).listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun latest(context: Context): File? = files(context).firstOrNull()

    fun read(context: Context): String? = latest(context)?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        files(context).forEach { runCatching { it.delete() } }
    }

    private fun dir(context: Context): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val now = Date()
        val fileName = "crash-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(now)}.txt"
        val body = buildString {
            appendLine("LinkGrab ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Device ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Thread ${thread.name}")
            appendLine("Time ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(now)}")
            appendLine()
            appendLine(throwable.stackTraceToString())
        }
        runCatching { File(dir(context), fileName).writeText(body) }
        // 只留最近三份，防止长期使用越攒越多
        files(context).drop(MAX_FILES).forEach { runCatching { it.delete() } }
    }
}
