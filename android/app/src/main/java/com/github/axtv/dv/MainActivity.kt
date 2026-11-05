/*
 * Android 适配层 —— 主入口 Activity。
 * Manifest: LAUNCHER + LEANBACK_LAUNCHER + axtv://douyin 深链。
 * 行为: 深链解析走核心层 AppRoutes.parseAwemeId（extra aweme_id 优先于 /video/(\d+) 路径）；
 *       EXTRA_ROUTE（小组件点击）经 AppRoutes.takeRoute 取出后送入统一路由表（ui/nav/NavRoutes）。
 */
package com.github.axtv.dv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import axtv.app.AppRoutes
import com.github.axtv.dv.ui.AxTvApp
import com.github.axtv.dv.ui.nav.NavRoutes
import com.github.axtv.dv.ui.nav.RouteRequest

class MainActivity : ComponentActivity() {

    private var routeSeq = 0L

    /** 外部路由请求：setContent 后由 onNewIntent 继续投递，Compose 侧消费后清空。 */
    private val routeRequest = mutableStateOf<RouteRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var initialRoute: String? = null
        handleDeepLink(intent, onRoute = { initialRoute = it })
        setContent {
            AxTvApp(
                initialRoute = initialRoute,
                routeRequest = routeRequest.value,
                onRouteHandled = { handled ->
                    if (routeRequest.value?.seq == handled.seq) {
                        routeRequest.value = null
                    }
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent, onRoute = { route ->
            routeSeq += 1
            routeRequest.value = RouteRequest(route, routeSeq)
        })
    }

    private fun handleDeepLink(intent: Intent?, onRoute: (String) -> Unit) {
        if (intent == null) return
        // AppRoutes.parseAwemeId 需要可变 Map（内部会 remove extra），故用 mutableMapOf。
        val extras: MutableMap<String, String> = mutableMapOf<String, String>().apply {
            intent.extras?.keySet()?.forEach { key ->
                intent.extras?.getString(key)?.let { put(key, it) }
            }
        }
        val path = intent.data?.path
        val awemeId = AppRoutes.parseAwemeId(extras, path)
        if (awemeId != null) {
            Log.i(TAG, "deep link -> aweme_id=$awemeId")
        }
        // 小组件 EXTRA_ROUTE 优先；没有 route 但命中了作品 id 时落到详情页。
        val route = AppRoutes.takeRoute(extras)
            ?: awemeId?.let { NavRoutes.detail(it) }
            ?: return
        if (!NavRoutes.isKnown(route)) {
            Log.w(TAG, "unknown route -> home: $route")
            onRoute(NavRoutes.HOME)
            return
        }
        Log.i(TAG, "internal route -> $route")
        onRoute(route)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
