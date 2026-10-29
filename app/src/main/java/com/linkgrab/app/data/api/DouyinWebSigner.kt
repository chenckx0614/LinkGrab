package com.linkgrab.app.data.api

import java.security.MessageDigest

/**
 * 抖音 web 接口签名套件（a_bogus + x-secsdk-web-signature）的 Kotlin 移植。
 *
 * 参考实现：Evil0ctal/Douyin_TikTok_Download_API v5（Apache-2.0），
 * `src/dtk/signing/native/{abogus,websign,sm3}.py` —— 该项目 2026-09-08/09
 * 用真实浏览器逐字节验证过（detail 接口 24/24 通过）。本移植与 Python 版
 * 在相同随机序列（[Lcg]）与相同时钟下逐字节一致，单测以黄金向量钉死。
 *
 * a_bogus 的算法概要（bdms SDK 1.0.1.19-fix.01）：
 *   输入 query/body/UA → SM3 双重哈希（盐 "dhzx"）→ 组装 50 个标量字段 +
 *   浏览器几何 + 校验和 → 位掩码 3→4 扩展（注入噪声）→ RC4 变体
 *   （S-box 降序 + KSA 含乘法，非标准 RC4）→ 自定义 base64（s4 表）。
 */
object DouyinWebSigner {

    // ------------------------------------------------------------------
    // SM3 (GB/T 32905-2016)
    // ------------------------------------------------------------------

    private val SM3_IV = intArrayOf(
        0x7380166F, 0x4914B2B9, 0x172442D7, -0x2575FA00, // 0xDA8A0600
        -0x5690CF44, 0x163138AA, -0x1C7211B3, -0x4F04F1B2, // 0xE38DEE4D, 0xB0FB0E4E
    )
    private const val T0 = 0x79CC4519
    private const val T1 = 0x7A879D8A

    private fun rotl(v: Int, bits: Int): Int {
        val b = bits % 32
        return (v shl b) or (v ushr (32 - b))
    }

    private fun ff(j: Int, x: Int, y: Int, z: Int) = if (j < 16) x xor y xor z else (x and y) or (x and z) or (y and z)

    private fun gg(j: Int, x: Int, y: Int, z: Int) = if (j < 16) x xor y xor z else (x and y) or (x.inv() and z)

    /** SM3 摘要，32 字节。 */
    fun sm3Hash(message: ByteArray): ByteArray {
        val state = SM3_IV.copyOf()
        val padded = pad(message)
        var offset = 0
        while (offset < padded.size) {
            compress(state, padded, offset)
            offset += 64
        }
        val out = ByteArray(32)
        for (i in 0 until 8) {
            out[i * 4] = (state[i] ushr 24).toByte()
            out[i * 4 + 1] = (state[i] ushr 16).toByte()
            out[i * 4 + 2] = (state[i] ushr 8).toByte()
            out[i * 4 + 3] = state[i].toByte()
        }
        return out
    }

    private fun pad(message: ByteArray): ByteArray {
        val bitLength = message.size.toLong() * 8
        var paddedLen = message.size + 1
        paddedLen += ((56 - paddedLen % 64) + 64) % 64
        val padded = ByteArray(paddedLen + 8)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
        }
        return padded
    }

    private fun compress(state: IntArray, block: ByteArray, base: Int) {
        val w = IntArray(68)
        val w1 = IntArray(64)
        for (i in 0 until 16) {
            w[i] = ((block[base + i * 4].toInt() and 0xFF) shl 24) or
                ((block[base + i * 4 + 1].toInt() and 0xFF) shl 16) or
                ((block[base + i * 4 + 2].toInt() and 0xFF) shl 8) or
                (block[base + i * 4 + 3].toInt() and 0xFF)
        }
        for (i in 16 until 68) {
            var x = w[i - 16] xor w[i - 9] xor rotl(w[i - 3], 15)
            x = x xor rotl(x, 15) xor rotl(x, 23)
            w[i] = x xor rotl(w[i - 13], 7) xor w[i - 6]
        }
        for (i in 0 until 64) w1[i] = w[i] xor w[i + 4]

        var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
        var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]
        for (j in 0 until 64) {
            val t = if (j < 16) T0 else T1
            val ss1 = rotl(rotl(a, 12) + e + rotl(t, j), 7)
            val ss2 = ss1 xor rotl(a, 12)
            val tt1 = ff(j, a, b, c) + d + ss2 + w1[j]
            val tt2 = gg(j, e, f, g) + h + ss1 + w[j]
            d = c
            c = rotl(b, 9)
            b = a
            a = tt1
            h = g
            g = rotl(f, 19)
            f = e
            e = tt2 xor rotl(tt2, 9) xor rotl(tt2, 17)
        }
        state[0] = state[0] xor a; state[1] = state[1] xor b; state[2] = state[2] xor c; state[3] = state[3] xor d
        state[4] = state[4] xor e; state[5] = state[5] xor f; state[6] = state[6] xor g; state[7] = state[7] xor h
    }

    /** `SM3(SM3(text + SALT))`，32 个 0..255 整数。 */
    private fun digestOf(text: String): IntArray {
        val first = sm3Hash((text + SALT).toByteArray(Charsets.UTF_8))
        return sm3Hash(first).map { it.toInt() and 0xFF }.toIntArray()
    }

    // ------------------------------------------------------------------
    // 常量（与 Python 版逐一对应）
    // ------------------------------------------------------------------

    private const val SALT = "dhzx"

    private val ALPHABET_S3 = "ckdp1h4ZKsUB80/Mfvw36XIgR25+WQAlEi7NLboqYTOPuzmFjJnryx9HVGDaStCe"
    private val ALPHABET_S4 = "Dkdpgh2ZmsQB80/MfvV36XI1R45-WUAlEixNLwoqYTOPuzKFjJnry79HbGcaStCe"

    private val HEADER_MAGIC = intArrayOf(3, 82)
    private val SDK_VERSION = intArrayOf(1, 0, 1, 0)
    private const val PAYLOAD_KEY = 0xD3
    private const val FORTNIGHT_EPOCH_MS = 1_721_836_800_000L
    private const val PAGE_ID = 6241
    private const val AID = 6383

    private val NOISE_MASKS = intArrayOf(0x91, 0x42, 0x2C)
    private val DATA_MASKS = intArrayOf(0x6E, 0xBD, 0xD3)

    private val FIELD_ORDER = intArrayOf(
        34, 44, 56, 61, 73, 29, 70, 45, 35, 49,
        38, 66, 51, 68, 28, 48, 64, 47, 30, 71,
        26, 55, 31, 69, 59, 40, 62, 63, 27, 72,
        41, 74, 57, 52, 42, 39, 33, 67, 53, 43,
        65, 46, 36, 24, 60, 32, 79, 80, 84, 85,
    )

    private val CANARIES = arrayOf(intArrayOf(3, 11, 12), intArrayOf(4, 8, 9), intArrayOf(5, 12, 13))

    // 三条摘要链：slots 是字段号，indices 是摘要字节位，canary 是哨兵三元组。
    private val CHAIN_QUERY = Chain(slots = intArrayOf(48, 49, 51), indices = intArrayOf(9, 18), canary = CANARIES[0])
    private val CHAIN_BODY = Chain(slots = intArrayOf(52, 53, 55), indices = intArrayOf(10, 19), canary = CANARIES[1])
    private val CHAIN_UA = Chain(slots = intArrayOf(56, 57, 59), indices = intArrayOf(11, 21), canary = CANARIES[2])

    private class Chain(val slots: IntArray, val indices: IntArray, val canary: IntArray)

    private const val ENV_FLAGS = 1
    private const val DETECT_FLAGS = 14
    private const val NR_FLAGS = 0x21
    private val NR_TAG = intArrayOf(0, 0, 0, 0)
    private const val TRIPWIRE_LOCKED = 3
    private const val CALL_BUCKET = 6
    const val DEFAULT_BROWSER_INFO = "1920|947|1920|1032|1920|1032|1920|1080|Win32"

    private const val TRIPWIRE_SET = 0xB2
    private const val TRIPWIRE_FREE = 0x4D

    private val HEADER_NOISE_BANDS = mapOf(
        "chrome" to 0, "firefox" to 40, "safari" to 81,
        "edge" to 125, "huawei" to 170, "other" to 210,
    )

    // ------------------------------------------------------------------
    // 基础原语
    // ------------------------------------------------------------------

    /** bdms 变体 RC4：S-box 降序初始化，KSA 含 `j * S[i]` 乘法。 */
    private fun rc4(key: ByteArray, data: ByteArray): ByteArray {
        val box = IntArray(256)
        for (i in 0 until 256) box[255 - i] = i
        var j = 0
        for (i in 0 until 256) {
            j = (j * box[i] + j + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            val t = box[i]; box[i] = box[j]; box[j] = t
        }
        val out = ByteArray(data.size)
        var i = 0
        j = 0
        for (index in data.indices) {
            i = (i + 1) and 0xFF
            j = (j + box[i]) and 0xFF
            val t = box[i]; box[i] = box[j]; box[j] = t
            out[index] = (data[index].toInt() xor box[(box[i] + box[j]) and 0xFF]).toByte()
        }
        return out
    }

    /** 自定义字母表 base64（等价标准 base64，仅查表不同），字面 `=` 补位。 */
    private fun encodeBase64(data: ByteArray, alphabet: String): String {
        val table = alphabet
        val out = StringBuilder()
        var offset = 0
        while (offset < data.size) {
            val len = minOf(3, data.size - offset)
            var block = 0
            for (i in 0 until 3) {
                block = block shl 8
                if (i < len) block = block or (data[offset + i].toInt() and 0xFF)
            }
            val digits = intArrayOf((block shr 18) and 0x3F, (block shr 12) and 0x3F, (block shr 6) and 0x3F, block and 0x3F)
            for (i in 0..len) out.append(table[digits[i]])
            offset += 3
        }
        val padCount = (4 - out.length % 4) % 4
        repeat(padCount) { out.append('=') }
        return out.toString()
    }

    /** JS 字符串转字节：UTF-16 码元，< U+0100 一字节，否则两字节大端。 */
    private fun jsBytes(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (code in text) {
            val unit = code.code
            if (unit and 0xFF00 != 0) out.write(unit shr 8)
            out.write(unit and 0xFF)
        }
        return out.toByteArray()
    }

    private fun leBytes(value: Long, count: Int): IntArray =
        IntArray(count) { ((value shr (8 * it)) and 0xFF).toInt() }

    private fun canary(digest: IntArray, offset: Int, sentinel: Int, fallback: Int): Int {
        for (i in offset until digest.size) if (digest[i] != sentinel) return digest[i]
        return fallback
    }

    private fun uaFamily(userAgent: String): String {
        val ua = userAgent.lowercase()
        for (name in listOf("edg", "huawei", "firefox", "chrome", "safari")) {
            if (name in ua) return if (name == "edg") "edge" else name
        }
        return "other"
    }

    // ------------------------------------------------------------------
    // 随机源：确定性 LCG（与测试向量生成器共用），默认随机。
    // ------------------------------------------------------------------

    /** 确定性随机源，黄金向量测试用（与 Python 端 LCG 完全一致）。 */
    class Lcg(seed: Long) {
        var state = seed
        fun random(): Double {
            state = state * 6364136223846793005L + 1442695040888963407L
            return (state ushr 11).toDouble() / (1L shl 53).toDouble()
        }
    }

    private fun systemRng(): () -> Double {
        val random = java.util.Random()
        return { random.nextDouble() }
    }

    // ------------------------------------------------------------------
    // a_bogus
    // ------------------------------------------------------------------

    /**
     * 一次签名一个实例。噪声注入消费随机序列的顺序与 Python 版严格一致，
     * 换随机源会得到不同的合法签名（平台允许），但黄金向量要求一致。
     */
    class ABogus(
        private val userAgent: String,
        private val browserInfo: String = DEFAULT_BROWSER_INFO,
        private val pageId: Int = PAGE_ID,
        private val aid: Int = AID,
        rng: (() -> Double)? = null,
    ) {
        private val rng: () -> Double = rng ?: systemRng()
        private val uaDigest: IntArray = userAgentDigest(userAgent)

        private fun headerNoise(): Int {
            val base = HEADER_NOISE_BANDS[uaFamily(userAgent)] ?: HEADER_NOISE_BANDS.getValue("other")
            return ((base + (rng() * 40).toInt()) and 0xFF)
        }

        private fun probeNoise(): Int {
            val value = (rng() * 240).toInt()
            return if (value > 109) value + value % 2 + 1 else value
        }

        private fun tripwireNoise(): Int =
            ((rng() * 255).toInt() and TRIPWIRE_FREE) or TRIPWIRE_SET

        /** 两字节 → 四字节，0xAA/0x55 交错；始终消耗一次随机（与 Python 一致）。 */
        private fun maskPair(pair: IntArray, low: Int? = null, high: Int? = null): IntArray {
            val noise = (rng() * 65535).toInt()
            val lo = (low ?: noise) and 0xFF
            val hi = (high ?: (noise shr 8)) and 0xFF
            return intArrayOf(
                (lo and 0xAA) or (pair[0] and 0x55),
                (lo and 0x55) or (pair[0] and 0xAA),
                (hi and 0xAA) or (pair[1] and 0x55),
                (hi and 0x55) or (pair[1] and 0xAA),
            )
        }

        private fun expandNoise(body: ByteArray): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            var offset = 0
            while (offset < body.size) {
                val len = minOf(3, body.size - offset)
                if (len < 3) {
                    out.write(body[offset].toInt())
                    if (len > 1 && body[offset + 1].toInt() != 0) out.write(body[offset + 1].toInt())
                } else {
                    val noise = (rng() * 1000).toInt() and 0xFF
                    for (i in 0 until 3) out.write((noise and NOISE_MASKS[i]) or (body[offset + i].toInt() and DATA_MASKS[i]))
                    out.write(
                        (body[offset].toInt() and NOISE_MASKS[0]) or
                            (body[offset + 1].toInt() and NOISE_MASKS[1]) or
                            (body[offset + 2].toInt() and NOISE_MASKS[2])
                    )
                }
                offset += 3
            }
            return out.toByteArray()
        }

        fun getValue(query: String, body: String = "", contentType: String = "", nowMs: Long = System.currentTimeMillis()): String {
            var bodyText = body
            if (contentType.lowercase().contains("multipart/form-data")) bodyText = ""
            require(nowMs >= FORTNIGHT_EPOCH_MS) { "clock is before the a_bogus epoch: $nowMs" }

            val queryDigest = digestOf(query)
            val bodyDigest = digestOf(bodyText)
            val ink = nowMs - 1
            val fortnights = (nowMs - FORTNIGHT_EPOCH_MS) / (1000L * 60 * 60 * 24 * 14)
            val infoBytes = jsBytes(browserInfo)
            val tailBytes = jsBytes("${(nowMs + 3) and 0xFF},")

            val fields = IntArray(86)
            fields[24] = 41
            fields[26] = fortnights.toInt()
            fields[27] = CALL_BUCKET
            fields[28] = 3
            fields[35] = ENV_FLAGS and 0xFF
            fields[36] = (ENV_FLAGS shr 8) and 0xFF
            fields[38] = NR_FLAGS and 0xFF
            fields[39] = (NR_FLAGS shr 8) and 0xFF
            fields[66] = TRIPWIRE_LOCKED
            fields[79] = infoBytes.size and 0xFF
            fields[80] = (infoBytes.size shr 8) and 0xFF
            fields[84] = tailBytes.size and 0xFF
            fields[85] = (tailBytes.size shr 8) and 0xFF
            for ((i, byte) in leBytes(nowMs, 6).withIndex()) fields[29 + i] = byte
            for ((i, byte) in leBytes(DETECT_FLAGS.toLong(), 4).withIndex()) fields[44 + i] = byte
            for ((i, byte) in NR_TAG.withIndex()) fields[40 + i] = byte
            for ((i, byte) in leBytes(ink, 6).withIndex()) fields[60 + i] = byte
            for ((i, byte) in leBytes(pageId.toLong(), 4).withIndex()) fields[67 + i] = byte
            for ((i, byte) in leBytes(aid.toLong(), 4).withIndex()) fields[71 + i] = byte
            for ((chain, digest) in listOf(CHAIN_QUERY to queryDigest, CHAIN_BODY to bodyDigest, CHAIN_UA to uaDigest)) {
                val written = intArrayOf(digest[chain.indices[0]], digest[chain.indices[1]], canary(digest, chain.canary[0], chain.canary[1], chain.canary[2]))
                for ((slot, value) in chain.slots.zip(written)) fields[slot] = value
            }

            // 噪声消耗顺序与 Python 版一致：version 两段（4 次随机）
            val version = maskPair(intArrayOf(SDK_VERSION[0], SDK_VERSION[1])) +
                maskPair(
                    intArrayOf(SDK_VERSION[2], SDK_VERSION[3]),
                    low = probeNoise(),
                    high = tripwireNoise(),
                )

            var checksum = 0
            for (byte in version) checksum = checksum xor byte
            for (name in FIELD_ORDER) checksum = checksum xor fields[name]

            val bodyBytes = ByteArray(FIELD_ORDER.size + infoBytes.size + tailBytes.size + 1)
            var pos = 0
            for (name in FIELD_ORDER) {
                bodyBytes[pos++] = fields[name].toByte()
            }
            infoBytes.copyInto(bodyBytes, pos); pos += infoBytes.size
            tailBytes.copyInto(bodyBytes, pos); pos += tailBytes.size
            bodyBytes[pos] = checksum.toByte()

            // header：_header_noise 先消耗一次，maskPair 再消耗一次（共 2 次）
            val header = maskPair(HEADER_MAGIC, high = headerNoise())
            val frame = expandNoise(bodyBytes)
            val sealedInput = ByteArray(version.size + frame.size)
            version.forEachIndexed { i, b -> sealedInput[i] = b.toByte() }
            frame.copyInto(sealedInput, version.size)
            val sealed = rc4(byteArrayOf(PAYLOAD_KEY.toByte()), sealedInput)

            val total = ByteArray(header.size + sealed.size)
            header.forEachIndexed { i, b -> total[i] = b.toByte() }
            sealed.copyInto(total, header.size)
            return encodeBase64(total, ALPHABET_S4)
        }

        private fun userAgentDigest(userAgent: String): IntArray {
            val key = byteArrayOf(
                (ENV_FLAGS shr 8).toByte(),
                (ENV_FLAGS and 0xFF).toByte(),
                (DETECT_FLAGS and 0xFF).toByte(),
            )
            val sealed = rc4(key, jsBytes(userAgent.trim()))
            return sm3Hash(encodeBase64(sealed, ALPHABET_S3).toByteArray(Charsets.UTF_8))
                .map { it.toInt() and 0xFF }.toIntArray()
        }
    }

    // ------------------------------------------------------------------
    // x-secsdk-web-signature
    // ------------------------------------------------------------------

    private const val WEBSIGN_SALT = "A96D855A08C0A9707F8BEF0D9A527E4E"

    /**
     * Python `urllib.parse.quote(s, safe='*-._')` 等价编码：
     * 不转义 = 字母数字 + `_.-~` + `*`。
     */
    fun pythonQuote(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            val c = ch.code
            val keep = ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' ||
                c == '_'.code || c == '.'.code || c == '-'.code || c == '~'.code || c == '*'.code
            if (keep) sb.append(ch) else sb.append('%').append(String.format("%02X", c))
        }
        return sb.toString()
    }

    /** Python `urllib.parse.urlencode(pairs)`：quote_plus 语义（空格 → `+`）。 */
    fun pythonUrlEncode(pairs: List<Pair<String, String>>): String =
        pairs.joinToString("&") { (k, v) ->
            pythonQuote(k).replace(" ", "+") + "=" + pythonQuote(v).replace(" ", "+")
        }

    /**
     * 组装受保护端点的完整 query 与请求头。
     *
     * [pairs] 是 a_bogus 之前的业务参数（含 msToken）加上 a_bogus 本身；
     * 需要 uifid（WebView 访问抖音页面后由页面 JS 铸造的访客 id）。
     * 返回 `(query, headers)` —— headers 里的三个值必须随请求头发出，
     * 同时 uifid 也要出现在 Cookie 里。
     */
    fun webSign(
        pairs: List<Pair<String, String>>,
        uifid: String,
        timestampSeconds: Long = System.currentTimeMillis() / 1000,
    ): Pair<String, Map<String, String>> {
        val covered = pairs.toMutableList()
        if (covered.none { it.first == "uifid" }) covered.add("uifid" to uifid)
        covered.add("timestamp" to timestampSeconds.toString())
        val query = covered.joinToString("&") { (k, v) ->
            "${pythonQuote(k)}=${pythonQuote(v)}"
        }
        val signature = md5Hex("${uifid}_${timestampSeconds}_${WEBSIGN_SALT}_${query}")
        val headers = mapOf(
            "uifid" to uifid,
            "x-secsdk-web-signature" to signature,
            "x-secsdk-web-expire" to timestampSeconds.toString(),
        )
        return "$query&x-secsdk-web-signature=$signature" to headers
    }

    private fun md5Hex(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format("%02x", it) }
}
