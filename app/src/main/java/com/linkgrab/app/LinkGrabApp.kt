package com.linkgrab.app

import android.app.Application
import com.linkgrab.app.util.CrashLogger

class LinkGrabApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 尽早装：解析和下载都在 IO 线程跑，闪退时只有这里能留下线索
        CrashLogger.install(this)
    }
}
