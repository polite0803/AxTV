/*
 * Android 适配层 —— 画中画操作接收器。
 * Manifest: PIP_PAUSE / PIP_PLAY / PIP_NEXT 三个 action（与 PipSpec.params 的 3 个 action 对应）。
 * 行为: 收到媒体键意图后桥到全局播放器状态（原 wi4.g，装配根 on5.H）；
 *       完整接线待 Media3Engine 落地（ADAPTERS.md §3），当前先打日志。
 */
package com.github.axtv.dv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PIP_PAUSE -> Log.i(TAG, "PIP pause requested")
            ACTION_PIP_PLAY -> Log.i(TAG, "PIP play requested")
            ACTION_PIP_NEXT -> Log.i(TAG, "PIP next requested")
        }
        // TODO(适配层收尾): Media3Engine 落地后转发给 PlayerCoordinator。
    }

    companion object {
        private const val TAG = "PipActionReceiver"
        const val ACTION_PIP_PAUSE = "com.github.axtv.dv.PIP_PAUSE"
        const val ACTION_PIP_PLAY = "com.github.axtv.dv.PIP_PLAY"
        const val ACTION_PIP_NEXT = "com.github.axtv.dv.PIP_NEXT"
    }
}
