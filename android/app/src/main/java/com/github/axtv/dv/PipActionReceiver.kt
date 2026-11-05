/*
 * Android 适配层 —— 画中画操作接收器。
 * Manifest: PIP_PAUSE / PIP_PLAY / PIP_NEXT 三个 action（与 PipController.standardActions 的 3 个 action 对应）。
 * 行为: 把小窗动作接回当前播放会话 —— 对应原版 PipActionReceiver.java:29-45
 *       （PAUSE 与 PLAY 都落到同一个"播放暂停计数器"，NEXT 走下一条），
 *       动作→命令的映射用核心层 axtv.player.PipController.commandOf。
 */
package com.github.axtv.dv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import axtv.player.PipController
import com.github.axtv.dv.ui.player.PipCoordinator

class PipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = PipCoordinator.session
        if (session == null) {
            Log.i(TAG, "无播放会话，忽略 ${intent.action}")
            return
        }
        when (PipController.commandOf(intent.action)) {
            PipController.Command.TOGGLE_PLAYBACK -> session.togglePlayPause()
            PipController.Command.NEXT -> session.playNext()
            null -> Log.w(TAG, "未知画中画动作: ${intent.action}")
        }
    }

    companion object {
        private const val TAG = "PipActionReceiver"
        const val ACTION_PIP_PAUSE = "com.github.axtv.dv.PIP_PAUSE"
        const val ACTION_PIP_PLAY = "com.github.axtv.dv.PIP_PLAY"
        const val ACTION_PIP_NEXT = "com.github.axtv.dv.PIP_NEXT"
    }
}
