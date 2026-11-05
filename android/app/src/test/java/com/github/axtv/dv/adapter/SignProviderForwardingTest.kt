package com.github.axtv.dv.adapter

import org.junit.Assert.assertEquals
import org.junit.Test

/** 真实 JUnit：验证 SignProvider 与核心层 (query, data, uri, userAgent) 的实参顺序。 */
class SignProviderForwardingTest {

    @Test
    fun forwardsPathQueryBodyAndUserAgentInCoreOrder() {
        val calls = mutableListOf<List<String?>>()
        val provider = AndroidSignProvider { query, body, path, userAgent ->
            calls += listOf(query, body, path, userAgent)
            "SIGNED"
        }

        val result = provider.signABogus(
            path = "/aweme/v1/web/general/search/single/",
            query = "keyword=abc&offset=0",
            body = "param=1",
            userAgent = "UA/1.0",
        )

        assertEquals("SIGNED", result)
        assertEquals(
            listOf("keyword=abc&offset=0", "param=1", "/aweme/v1/web/general/search/single/", "UA/1.0"),
            calls.single(),
        )
    }

    @Test
    fun nullPathBecomesEmptyString() {
        val provider = AndroidSignProvider { _, _, path, _ -> path }

        assertEquals("", provider.signABogus(null, "q", "b", null))
    }
}
