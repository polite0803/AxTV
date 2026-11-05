package com.github.axtv.dv.adapter

import axtv.api.ApiRequest
import axtv.api.ApiResponse
import axtv.api.CookieStore
import axtv.api.DeviceProfile
import axtv.api.DouyinApi
import axtv.api.HttpEngine
import axtv.api.JsonCodec
import axtv.api.SignProvider
import axtv.api.TicketGuardCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 真实 JUnit：验证 api 的 URL/query 拼装与 a_bogus 注入位置（纯 JVM，无需 Android）。 */
class ApiUrlAssemblyTest {

    private val recordedUrls = mutableListOf<String>()

    private val fakeHttp = HttpEngine { method, url, _, _, _, _ ->
        recordedUrls += "$method $url"
        ApiResponse(200, "{}".toByteArray(), emptyMap())
    }

    private val fakeCookies = object : CookieStore {
        override fun cookieHeader(): String = ""
        override fun setCookieHeader(cookieHeader: String) = Unit
        override fun cookies(): Map<String, String> = emptyMap()
        override fun persistentDeviceId(key: String): String = "1000000000000000"
        override fun userUniqueId(): String = ""
        override fun setUserUniqueId(value: String) = Unit
        override fun selfUserId(): String = ""
        override fun applySelfUserId(userId: String) = Unit
        override fun ticketGuard(): TicketGuardCredentials = TicketGuardCredentials.empty()
    }

    private val fakeSigner = object : SignProvider {
        override fun signABogus(path: String?, query: String, body: String, userAgent: String?): String = "TEST_SIG"
        override fun ticketGuardHeaders(path: String?, credentials: TicketGuardCredentials?): Map<String, String> = emptyMap()
    }

    private val fakeJson = object : JsonCodec {
        override fun <T : Any?> decode(json: String, type: Class<T>): T =
            throw UnsupportedOperationException("测试未使用 callFor")
    }

    private fun api(): DouyinApi =
        DouyinApi(fakeHttp, fakeSigner, fakeCookies, DeviceProfile("16", "Pixel 10 Pro", 35), fakeJson)

    @Test
    fun aBogusIsAppendedAfterExplicitParams() {
        api().call(
            ApiRequest.get("/aweme/v1/web/general/search/single/")
                .param("keyword", "abc")
                .param("offset", "0")
                .sign(),
        )

        assertEquals(1, recordedUrls.size)
        val url = recordedUrls.single()
        assertTrue("应使用 GET", url.startsWith("GET "))
        assertTrue("应包含显式参数 keyword", url.contains("keyword=abc"))
        assertTrue("应包含显式参数 offset", url.contains("offset=0"))
        assertTrue("应注入 a_bogus", url.contains("a_bogus=TEST_SIG"))
    }

    @Test
    fun unsignedRequestDoesNotContainABogus() {
        api().call(ApiRequest.get("/aweme/v1/web/general/search/single/").param("keyword", "abc"))

        val url = recordedUrls.single()
        assertTrue("未请求签名时不应出现 a_bogus", !url.contains("a_bogus="))
    }
}
