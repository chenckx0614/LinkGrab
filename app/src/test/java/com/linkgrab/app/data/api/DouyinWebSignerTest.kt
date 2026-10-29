package com.linkgrab.app.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 黄金向量由参考实现（Evil0ctal/Douyin_TikTok_Download_API 的
 * abogus.py / websign.py / sm3.py，Apache-2.0）在本机生成，
 * 随机源用双方一致的 LCG 钉死。向量见 build 生成的 golden.json 同款常量。
 */
class DouyinWebSignerTest {

    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private val query = "device_platform=webapp&aid=6383&channel=channel_pc_web" +
        "&aweme_id=7700635801125808369&msToken=AbCdEf123456Zz=="
    private val nowMs = 1730000000000L

    @Test
    fun `sm3 matches standard vectors`() {
        val abc = DouyinWebSigner.sm3Hash("abc".toByteArray())
        assertEquals("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0", abc.toHex())
        val empty = DouyinWebSigner.sm3Hash(ByteArray(0))
        assertEquals("1ab21d8355cfa17f8e61194831e81a8f22bec8c728fefb747ed035eb5082aa2b", empty.toHex())
    }

    @Test
    fun `a_bogus matches reference output with pinned rng`() {
        val signer = DouyinWebSigner.ABogus(ua, rng = RngForTest(DouyinWebSigner.Lcg(42)))
        val value = signer.getValue(query, nowMs = nowMs)
        assertEquals(188, value.length)
        assertEquals(
            "YJ45g7tyYZR5FVMS8cjct7dUn9n/rT8yszTQSwMTSOFvbhFOWbNOxNGYGoLi4dVw0SpzhHVH7nMAbxdcPtUkZenkqmkkumhWMzVc9g6LZqq4TFJQErbkeu6FFwMrU5Gql554i14I8UrH6jxAirdD/QA9eKoFQbuBM1xjk2Tci9sXZFgADZcePBtkLh4B",
            value,
        )
    }

    @Test
    fun `a_bogus matches reference for second query with same seed`() {
        val signer = DouyinWebSigner.ABogus(ua, rng = RngForTest(DouyinWebSigner.Lcg(42)))
        val query2 = query + "&version_code=290100"
        val value = signer.getValue(query2, nowMs = nowMs)
        assertEquals(
            "YJ45g7tyYZR5FVMS8cjct7dUn9n/rT8ym0TQSwHTSOKUbhzPWbNOxNGYGoLi4dVw0SpzhHVH7nMAbxdcPtUkZenkqmkkumhWMzVc9g6LZqq4TFJQErbkeu6FFwMrU5Gql554i14I8UrH6jxAirdD/QA9eKoFQbuBM1xjk2Tci9sXZFgADZcePBtkLh5/",
            value,
        )
    }

    @Test
    fun `a_bogus matches reference for post body with different seed`() {
        val signer = DouyinWebSigner.ABogus(ua, rng = RngForTest(DouyinWebSigner.Lcg(7)))
        val value = signer.getValue("aid=6383", body = "hello", nowMs = nowMs)
        assertEquals(
            "QyU5DqtEdN/5adFbuObCtSplj9n/rTSy94T2Wa2TSOOYbqUORuNxxLzYnFxjsDnwTuphhH3Hen0MYdjOMGW9ZCrkumpfuYXWFG5C98vo8qwRTMkQDNjwez6zFwMN05GqeA57iA4IhUro6nnAiqd8/Qly9KLKQbuBB3xfk/YbE9sXZMyAD1crPBbkKhRl",
            value,
        )
    }

    @Test
    fun `a_bogus default rng produces structurally valid 192-char signatures`() {
        repeat(8) {
            val signer = DouyinWebSigner.ABogus(ua)
            val value = signer.getValue(query)
            assertEquals(192, value.length)
            // s4 base64：主体字符全部在表内，padding 只能是 '='
            assertTrue(value.dropLast(2).all { it != '=' })
        }
    }

    @Test
    fun `webSign matches reference query and signature`() {
        val pairs = listOf("aid" to "6383", "aweme_id" to "123", "a_bogus" to "AbC/dE-F=")
        val (query, headers) = DouyinWebSigner.webSign(pairs, "UIFID_TEMP_test_1234567890", 1730000000L)
        assertEquals(
            "aid=6383&aweme_id=123&a_bogus=AbC%2FdE-F%3D&uifid=UIFID_TEMP_test_1234567890&timestamp=1730000000&x-secsdk-web-signature=5230cf46710a319ca55cf033dbf67063",
            query,
        )
        assertEquals("5230cf46710a319ca55cf033dbf67063", headers["x-secsdk-web-signature"])
        assertEquals("1730000000", headers["x-secsdk-web-expire"])
    }

    @Test
    fun `pythonQuote escapes like urllib quote`() {
        assertEquals("AbC%2FdE-F%3D", DouyinWebSigner.pythonQuote("AbC/dE-F="))
        assertEquals("abc123._-~*", DouyinWebSigner.pythonQuote("abc123._-~*"))
        assertEquals("a%20b", DouyinWebSigner.pythonQuote("a b"))
    }

    /** 把 [DouyinWebSigner.Lcg] 适配成签名器内部的随机接口。 */
    private class RngForTest(private val lcg: DouyinWebSigner.Lcg) : () -> Double {
        override fun invoke(): Double = lcg.random()
    }

    private fun ByteArray.toHex(): String = joinToString("") { String.format("%02x", it) }
}
