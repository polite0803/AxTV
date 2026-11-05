/*
 * Android 适配层 —— 主入口 Activity。
 * Manifest: LAUNCHER + LEANBACK_LAUNCHER + axtv://douyin 深链。
 * 行为: 深链解析走核心层 AppRoutes.parseAwemeId（extra aweme_id 优先于 /video/(\d+) 路径），
 *       解析结果打日志；Compose UI 为占位（页面绘制按 ui/NOTES 逐页落地，ADAPTERS.md §9）。
 */
package com.github.axtv.dv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import axtv.app.AppRoutes
import com.github.axtv.dv.ui.AxTvApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        setContent {
            AxTvApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
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
            // TODO(适配层收尾): 路由到视频详情页装配根（原 on5.H，见 ADAPTERS.md §3/§9）。
        }
        val route = AppRoutes.takeRoute(extras)
        if (route != null) {
            Log.i(TAG, "internal route -> $route")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
