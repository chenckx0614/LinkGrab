package com.linkgrab.app.data.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * 用 Cronet（Chromium 网络栈）抓页面。
 *
 * 为什么不用 OkHttp：小红书 Shield 会对网络客户端做 **TLS 指纹检测**，
 * OkHttp / HttpURLConnection / Python urllib 访问笔记页都会被 302 到登录页；
 * 而 curl、真实浏览器（Chrome/Cronet）能直接拿到完整 SSR 页面。
 *
 * 这正是高星项目 JoeanAmier/XHS-Downloader 的做法：
 * `curl_cffi.get(url, impersonate="chrome146")` —— 用 Chrome 同源的
 * TLS 指纹发普通 GET，不需要任何签名，Cookie 也是可选项。
 * Android 上没有 curl_cffi，等价的 Chrome TLS 实现就是 Cronet。
 */
class CronetFetcher(context: Context) {

    private val appContext = context.applicationContext

    /** Cronet 回调线程。单线程即可，Cronet 内部另有自己的网络线程。 */
    private val callbackExecutor: Executor =
        Executors.newSingleThreadExecutor { r -> Thread(r, "cronet-cb") }

    /**
     * 引擎懒加载：Cronet 首次构建要加载原生库（几十毫秒），
     * 只有真正用到这条通道时才付这个成本。构建失败返回 null（不抛异常，
     * 让调用方继续走其它通道）。
     */
    private val engine: CronetEngine? by lazy {
        runCatching {
            CronetEngine.Builder(appContext)
                .enableHttp2(true)
                .enableQuic(false)
                .build()
        }.getOrNull()
    }

    val available: Boolean get() = engine != null

    /**
     * GET 一个页面，返回响应体文本；失败返回 null。
     * 自动跟随重定向（与 curl_cffi 的 allow_redirects=True 一致）。
     */
    suspend fun getHtml(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String? {
        val cronet = engine ?: return null
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val buffer = ByteArrayOutputStream()
                val readBuffer = ByteBuffer.allocateDirect(64 * 1024)
                var request: UrlRequest? = null

                val callback = object : UrlRequest.Callback() {
                    override fun onRedirectReceived(
                        req: UrlRequest,
                        info: UrlResponseInfo,
                        newLocationUrl: String,
                    ) {
                        req.followRedirect()
                    }

                    override fun onResponseStarted(req: UrlRequest, info: UrlResponseInfo) {
                        req.read(readBuffer)
                    }

                    override fun onReadCompleted(
                        req: UrlRequest,
                        info: UrlResponseInfo,
                        byteBuffer: ByteBuffer,
                    ) {
                        byteBuffer.flip()
                        val chunk = ByteArray(byteBuffer.remaining())
                        byteBuffer.get(chunk)
                        buffer.write(chunk)
                        byteBuffer.clear()
                        req.read(byteBuffer)
                    }

                    override fun onSucceeded(req: UrlRequest, info: UrlResponseInfo) {
                        if (cont.isActive) cont.resume(buffer.toString("UTF-8"))
                    }

                    override fun onFailed(
                        req: UrlRequest,
                        info: UrlResponseInfo,
                        error: CronetException,
                    ) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onCanceled(req: UrlRequest, info: UrlResponseInfo) {
                        if (cont.isActive) cont.resume(null)
                    }
                }

                val builder = cronet
                    .newUrlRequestBuilder(url, callback, callbackExecutor)
                    .setHttpMethod("GET")
                headers.forEach { (name, value) -> builder.addHeader(name, value) }

                val built = runCatching { builder.build() }.getOrNull()
                    ?: run {
                        if (cont.isActive) cont.resume(null)
                        return@suspendCancellableCoroutine
                    }
                request = built

                try {
                    built.start()
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }

                cont.invokeOnCancellation {
                    runCatching { request?.cancel() }
                }
            }
        }
    }

    /** 浏览器形态的请求头（与 curl_cffi 的 Chrome 模拟一致：真实 UA + 导航语义头）。 */
    fun browserHeaders(userAgent: String): Map<String, String> = mapOf(
        "User-Agent" to userAgent,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "zh-CN,zh;q=0.9",
        "sec-fetch-dest" to "document",
        "sec-fetch-mode" to "navigate",
        "sec-fetch-site" to "none",
        "upgrade-insecure-requests" to "1",
    )

    companion object {
        /**
         * UA 版本要与 Cronet 内置的 Chromium 主版本保持一致（当前依赖内置 151），
         * 否则 UA 与 TLS/HTTP2 指纹对不上，反而容易被风控识别。
         * 升级 Cronet 依赖时记得同步这里。
         */
        const val DESKTOP_CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"

        /** 移动端 Chrome UA：部分笔记只对移动端渲染完整 SSR 数据，作为重试档。 */
        const val MOBILE_CHROME_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Mobile Safari/537.36"
    }
}
