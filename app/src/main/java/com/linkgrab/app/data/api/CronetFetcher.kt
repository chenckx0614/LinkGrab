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

/** Cronet 一次 GET 的结果：响应体 + 跟随完重定向后的最终 URL。 */
data class CronetPage(val html: String, val finalUrl: String)

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
    ): String? = getPage(url, headers)?.html

    /**
     * GET 一个页面，同时返回**最终 URL**（跟随完所有重定向后的地址）。
     *
     * 短链必须靠它展开：xhslink 的 302 链会把 xsec_token 拼进最终地址，
     * 只看 body 拿不到。另外小红书对「不认识的客户端」会把短链降级跳到
     * /explore 信息流页，只有比对最终 URL 才能发现、换通道重试。
     */
    suspend fun getPage(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): CronetPage? {
        val cronet = engine ?: return null
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val buffer = ByteArrayOutputStream()
                val readBuffer = ByteBuffer.allocateDirect(64 * 1024)
                var request: UrlRequest? = null
                // 重定向链上每一跳都更新，最后一个非空值即最终 URL
                var lastUrl = url

                val callback = object : UrlRequest.Callback() {
                    override fun onRedirectReceived(
                        req: UrlRequest,
                        info: UrlResponseInfo,
                        newLocationUrl: String,
                    ) {
                        lastUrl = newLocationUrl
                        req.followRedirect()
                    }

                    override fun onResponseStarted(req: UrlRequest, info: UrlResponseInfo) {
                        info.url.takeIf { it.isNotBlank() }?.let { lastUrl = it }
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
                        info.url.takeIf { it.isNotBlank() }?.let { lastUrl = it }
                        if (cont.isActive) {
                            cont.resume(CronetPage(html = buffer.toString("UTF-8"), finalUrl = lastUrl))
                        }
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

    companion object {
        private val CHROME_VERSION_PATTERN = """Chrome/(\d+)""".toRegex()

        /**
         * Cronet 内置的 Chromium 版本（如 "151.0.7922.47"）。
         * 从 Cronet 自己读，升级依赖时 UA 会自动跟上，不用手改常量。
         */
        val chromiumVersion: String by lazy {
            runCatching { org.chromium.net.ApiVersion.getCronetVersion() }
                .getOrDefault("151.0.0.0")
        }

        /** Chromium 主版本，如 151。 */
        val chromiumMajor: Int by lazy {
            chromiumVersion.substringBefore('.').toIntOrNull() ?: 151
        }

        /** 桌面 Chrome UA。版本号跟随 Cronet 内置的 Chromium。 */
        val DESKTOP_CHROME_UA: String get() =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromiumMajor.0.0.0 Safari/537.36"

        /** 移动端 Chrome UA：部分笔记只对移动端渲染完整 SSR 数据，作为重试档。 */
        val MOBILE_CHROME_UA: String get() =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromiumMajor.0.0.0 Mobile Safari/537.36"

        /**
         * 浏览器形态的请求头（与 curl_cffi 的 Chrome 模拟一致：真实 UA + 导航语义头）。
         *
         * **sec-ch-ua 三件套是必需的，不是可选装饰。** 2026 年小红书 Shield 会把
         * 「UA 自称 Chrome，却不带 Client Hints」的请求判为非浏览器：它只看 UA 会
         * 得到「Chrome 151」，但真正的 Chrome 151 一定会同时发
         * `sec-ch-ua: "Chromium";v="151"`，两组信息对不上就是伪造。
         * 实测（curl_cffi，TLS 指纹固定为 Chrome 131）：
         *   UA 151 + 无 sec-ch-ua   → 302 登录页
         *   UA 151 + sec-ch-ua 131  → 302 登录页  ← 版本号对不上同样被拦
         *   UA 151 + sec-ch-ua 151  → 200 完整 SSR
         *   UA 131 + sec-ch-ua 131  → 200 完整 SSR
         * 也就是说**UA 与 sec-ch-ua 的版本号必须一致**，与 TLS 指纹版本是否一致无关。
         *
         * 这里不假设调用方传进来的 UA 是什么版本：直接从中解析 Chrome 主版本再拼
         * sec-ch-ua，保证两者永远自洽。
         */
        fun browserHeaders(userAgent: String): Map<String, String> {
            val major = CHROME_VERSION_PATTERN.find(userAgent)?.groupValues?.get(1)?.toIntOrNull()
                ?: chromiumMajor
            val mobile = "Mobile" in userAgent || "Android" in userAgent
            return mapOf(
                "User-Agent" to userAgent,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Accept-Language" to "zh-CN,zh;q=0.9",
                // Client Hints：必须与 UA 里的 Chrome 主版本一致
                "sec-ch-ua" to """"Google Chrome";v="$major", "Chromium";v="$major", "Not_A Brand";v="24"""",
                "sec-ch-ua-mobile" to if (mobile) "?1" else "?0",
                "sec-ch-ua-platform" to if (mobile) "\"Android\"" else "\"Windows\"",
                "sec-fetch-dest" to "document",
                "sec-fetch-mode" to "navigate",
                "sec-fetch-site" to "none",
                "sec-fetch-user" to "?1",
                "priority" to "u=0, i",
                "upgrade-insecure-requests" to "1",
            )
        }
    }
}
