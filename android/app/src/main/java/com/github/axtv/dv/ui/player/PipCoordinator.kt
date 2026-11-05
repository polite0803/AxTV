/*
 * 播放页画中画协调器（设置项 pip_enabled 的唯一消费点）。
 *
 * 证据：src/app/com/github/mytv/p004dv/MainActivity.java:347-429
 *  - 判定顺序：无 coordinator → 返回；屏幕熄灭（PowerManager.isInteractive 为假）→ 只暂停；
 *    `pip_enabled`（src/app/p000/wm4.java:430 的状态流）为假或当前没有播放态 → 只暂停；
 *    SDK<26 → 返回（MainActivity.java:383）；
 *  - 参数：宽高比 clamp 0.47..2.39 后 `(int)(ratio*1000)/1000`，未知 16:9；SDK>=31 打开自动进入；
 *  - 动作：PIP_PAUSE / PIP_PLAY / PIP_NEXT 三个广播 PendingIntent（requestCode 0/1/2）；
 *  - 失败：enterPictureInPictureMode 抛异常时走协调器重置（m5244z）。
 * 核心层把参数与判定收敛成 axtv.player.PipController（refactor/player/NOTES.md §4.7），
 * 本类只做 Android 侧执行：真正调用系统 API，并把小窗动作接回 PlayerSession。
 *
 * 与原版差异：原版用 R.drawable.pip_pause/play/next，本应用 res/ 没有这三张图（不在本次改动范围），
 * 改用框架内置的 ic_media_pause / ic_media_play / ic_media_next。
 */
package com.github.axtv.dv.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.util.Rational
import axtv.player.PipController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.axtv.dv.adapter.AppRuntime

/**
 * 会话侧的画中画执行器。
 *
 * 只持有「当前播放会话」和一个调用期间的 Activity 引用（[PipController.enter] 同步返回后即清空），
 * 不长期保存 Activity，避免泄漏。
 */
object PipCoordinator : PipController.PipHost {

    private const val TAG = "PipActionReceiver"

    /** 当前播放会话；进入播放页注册、释放时注销。 */
    @Volatile
    var session: PlayerSession? = null
        private set

    /** 是否处于画中画：小窗里只保留画面，浮层与弹幕层都要让位（原版把 PIP 态写回协调器供 UI 读）。 */
    var inPipMode by mutableStateOf(false)
        private set

    private var pendingActivity: Activity? = null

    fun register(playerSession: PlayerSession) {
        session = playerSession
    }

    fun unregister(playerSession: PlayerSession) {
        if (session === playerSession) {
            session = null
            inPipMode = false
        }
    }

    fun onPipModeChanged(isInPictureInPictureMode: Boolean) {
        inPipMode = isInPictureInPictureMode
    }

    /**
     * `onUserLeaveHint` 入口：离开应用时按需进入画中画。
     *
     * 设置关闭时什么都不做（原版此时只重置协调器；本应用的暂停由 ON_STOP 生命周期负责）。
     */
    fun onUserLeaveHint(activity: Activity) {
        val settings = AppRuntime.settings ?: return
        if (!settings.pipEnabled()) {
            return
        }
        val current = session ?: return
        if (!deviceSupportsPip(activity)) {
            Log.i(TAG, "设备不支持画中画，忽略进入请求")
            return
        }
        val power = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power != null && !power.isInteractive) {
            // 屏幕已熄灭：只暂停，不进小窗（MainActivity.java:355-362）。
            current.onHostStop()
            return
        }
        pendingActivity = activity
        try {
            PipController.enter(this, current.videoAspectRatio(), Build.VERSION.SDK_INT, true)
        } finally {
            pendingActivity = null
        }
    }

    /** 设备能力门：多数 Android TV 盒子没有 FEATURE_PICTURE_IN_PICTURE。 */
    fun deviceSupportsPip(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    override fun enterPip(params: PipController.Params) {
        val activity = pendingActivity ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // 与 PipController.isSupported 同一口径（MainActivity.java:383）；这里再挡一次，
            // 因为 PictureInPictureParams / RemoteAction 都是 API 26 才有的类。
            onPipEnterFailed()
            return
        }
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(params.ratioNum, params.ratioDen))
            .setActions(params.actions.map { action -> action.toRemoteAction(activity) })
        if (params.autoEnter && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
        }
        // 返回 false 表示系统拒绝进窗（例如当前窗口不允许），统一转成异常走 onPipEnterFailed。
        if (!activity.enterPictureInPictureMode(builder.build())) {
            throw IllegalStateException("系统拒绝进入画中画")
        }
        Log.i(TAG, "进入画中画: ratio=${params.ratioNum}/${params.ratioDen}")
    }

    override fun onPipEnterFailed() {
        // 对应原版 m5244z()：进不去就把播放停下来，不留后台无声播放。
        session?.onHostStop()
    }

    /** 三个小窗动作：广播 PendingIntent，由 PipActionReceiver 接回会话。 */
    private fun PipController.Action.toRemoteAction(activity: Activity): RemoteAction {
        val iconId = when (iconName) {
            "pip_pause" -> android.R.drawable.ic_media_pause
            "pip_play" -> android.R.drawable.ic_media_play
            else -> android.R.drawable.ic_media_next
        }
        val pendingIntent = PendingIntent.getBroadcast(
            activity,
            requestCode,
            Intent(action).setPackage(activity.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(Icon.createWithResource(activity, iconId), title, description, pendingIntent)
    }
}
