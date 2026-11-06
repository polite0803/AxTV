/*
 * 作者频道 / 作者连播两条新路由的解析与往返。
 *
 * 关键语义：
 *  - sec_user_id 是作者频道与 aweme/post 的唯一凭据，缺失时必须落首页而不是进空页面；
 *  - 昵称、起播作品 id 都要能原样带过去（百分号编码后再解回）。
 */
package com.github.axtv.dv.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class NavRoutesAuthorTest {

    @Test
    fun `作者频道 route 往返`() {
        val route = NavRoutes.author("MS4wLjABAAAA-test", "测试作者")
        assertEquals(
            NavDestination.AuthorChannel("MS4wLjABAAAA-test", "测试作者"),
            NavRoutes.parse(route),
        )
        assertEquals(route, NavRoutes.routeOf(NavRoutes.parse(route)))
    }

    @Test
    fun `作者连播 route 保留起播作品`() {
        val route = NavRoutes.authorPlay("MS4wLjABAAAA-test", "7300000000000000000")
        assertEquals(
            NavDestination.AuthorPlay("MS4wLjABAAAA-test", "7300000000000000000"),
            NavRoutes.parse(route),
        )
        assertEquals(route, NavRoutes.routeOf(NavRoutes.parse(route)))
    }

    @Test
    fun `作者连播不带起播作品时从第一条开始`() {
        assertEquals(
            NavDestination.AuthorPlay("MS4wLjABAAAA-test", ""),
            NavRoutes.parse("author/play?secUid=MS4wLjABAAAA-test"),
        )
    }

    @Test
    fun `缺少 sec_user_id 时落首页`() {
        assertEquals(NavDestination.Home(), NavRoutes.parse("author"))
        assertEquals(NavDestination.Home(), NavRoutes.parse("author/play?aweme=1"))
    }

    @Test
    fun `新路由被识别为已知页面`() {
        assertEquals(true, NavRoutes.isKnown("author?secUid=x"))
        assertEquals(true, NavRoutes.isKnown("author/play?secUid=x&aweme=1"))
    }
}
