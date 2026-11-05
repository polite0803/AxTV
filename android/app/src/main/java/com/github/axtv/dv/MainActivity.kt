/*
 * Android 适配层 —— 主入口 Activity。
 * Manifest: LAUNCHER + LEANBACK_LAUNCHER + axtv://douyin 深链。
 * 行为: 深链解析走核心层 AppRoutes.parseAwemeId（extra aweme_id 优先于 /video/(\d+) 路径）；
 *       EXTRA_ROUTE（小组件点击）经 AppRoutes.takeRoute 取出后送入统一路由表（ui/nav/NavRoutes）。
 */
package com.github.axtv.dv

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import axtv.app.AppRoutes
import axtv.app.RemoteKeyMap
import com.github.axtv.dv.ui.AxTvApp
import com.github.axtv.dv.ui.nav.NavRoutes
import com.github.axtv.dv.ui.nav.RouteRequest
import com.github.axtv.dv.ui.player.PipCoordinator

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

    /**
     * 离开应用时按需进入画中画（设置项 pip_enabled 的入口，判定与参数见 ui/player/PipCoordinator）。
     * 关闭该设置时什么都不做 —— 不进小窗、也不额外暂停（暂停由 ON_STOP 生命周期负责）。
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PipCoordinator.onUserLeaveHint(this)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        // 对应原版 MainActivity:289-298：把小窗态写回协调器，播放页据此收起浮层与弹幕。
        PipCoordinator.onPipModeChanged(isInPictureInPictureMode)
    }

    /**
     * 媒体键 → 方向键重映射（原版 MainActivity.dispatchKeyEvent:143-150，
     * 映射表落在 [axtv.app.RemoteKeyMap.DISPATCH_REMAP]）。
     *
     * 不少电视/盒子遥控器只有「上一首 / 下一首 / 播放暂停」而没有独立的 DPAD 语义：
     * 原版把它们换成上/下/左/右/确认再分发，否则信息流的上下换条、列表焦点移动直接失灵。
     * scanCode 固定为 0（原版行为）：Compose 侧按 scanCode 区分物理键，保留原值会被当成另一个键。
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val mapped = RemoteKeyMap.remapForDispatch(
            RemoteKeyMap.KeyEvent(
                event.downTime,
                event.eventTime,
                event.action,
                event.keyCode,
                event.repeatCount,
                event.metaState,
                event.deviceId,
                event.scanCode,
                event.flags,
            ),
        ) ?: return super.dispatchKeyEvent(event)
        val remapped = KeyEvent(
            mapped.downTime(),
            mapped.eventTime(),
            mapped.action(),
            mapped.keyCode(),
            mapped.repeatCount(),
            mapped.metaState(),
            mapped.deviceId(),
            mapped.scanCode(),
            mapped.flags(),
        )
        return super.dispatchKeyEvent(remapped)
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
