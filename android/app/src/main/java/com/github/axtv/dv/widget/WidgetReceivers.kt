/*
 * Android 适配层 —— widget 模块：6 个原生 AppWidgetProvider + 媒体键桥。
 * Manifest: widget.ContinueWidgetReceiver 等 6 个 receiver + widget.WidgetMediaActionReceiver
 *           （条目已在 AndroidManifest.xml 注册，本轮未改动 manifest）。
 * 渲染: RemoteViews（Android 唯一可用于桌面小组件的绘制方式；不引入 Glance/Compose 运行时）。
 *       分支与字段规格来自核心层 WidgetRenderSpec（widget/NOTES.md §3）。
 * 刷新: onEnabled/onUpdate → WorkManager 周期任务；「重试」按钮 → 一次性任务 + 立即重绘。
 */
package com.github.axtv.dv.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import axtv.widget.WidgetRenderSpec
import axtv.widget.WidgetMediaActionReceiver as CoreMediaActions

/**
 * 播放器控制契约：由播放器层（player 子包 或 ui/player 子包）在播放会话建立后装配，
 * 播放/暂停/上一条/下一条都返回是否已处理。实现方可在 widget 包外，故这里只留挂点。
 */
interface WidgetPlayerControls {
    fun handle(keyCode: Int): Boolean
}

/**
 * 小组件运行时挂点。
 * - [mediaAction]：原 wi4.g 挂点，外部已装配时优先走核心层实现；
 * - [playerControls]：播放器控制接口，未装配时媒体键记录 dropped；
 * - [playback]：正在播放快照（纯内存，进程结束即清空，避免展示过期数据）。
 */
/** 暂停态快照超过 30 分钟视为过期（播放器退出/进程残留），避免桌面长期显示旧条目。 */
internal const val PLAYBACK_STALE_MS = 30L * 60L * 1000L

/** 纯函数，便于单测：非播放态且超过 TTL 的快照应被清理。 */
internal fun isPlaybackStale(item: WidgetPlaybackItem?, publishedAt: Long, now: Long): Boolean =
    item != null && !item.isPlaying && publishedAt > 0L && now - publishedAt >= PLAYBACK_STALE_MS

object WidgetRuntime {
    @Volatile
    var mediaAction: CoreMediaActions? = null

    @Volatile
    var playerControls: WidgetPlayerControls? = null

    @Volatile
    private var playback: WidgetPlaybackItem? = null

    @Volatile
    private var playbackPublishedAt = 0L

    /** 播放器状态变化时调用；[context] 非空则立即重绘「正在播放」小组件。 */
    fun publishPlayback(context: Context?, item: WidgetPlaybackItem?) {
        playback = item
        playbackPublishedAt = if (item == null) 0L else System.currentTimeMillis()
        if (context != null) {
            WidgetRenderers.refresh(context, WidgetRenderSpec.WidgetId.NOW_PLAYING)
        }
    }

    fun playbackData(): WidgetData {
        val current = playback
        if (isPlaybackStale(current, playbackPublishedAt, System.currentTimeMillis())) {
            playback = null
            playbackPublishedAt = 0L
            return WidgetData(playback = null)
        }
        return WidgetData(playback = current)
    }

    /**
     * 媒体键分发：外部装配 → 播放器控制 → 均未装配则返回 false 并记录 dropped。
     * 不向系统广播媒体键，避免误控其它应用的播放。
     */
    fun dispatchMedia(keyCode: Int): Boolean {
        mediaAction?.let {
            it.onReceive(keyCode)
            return true
        }
        playerControls?.let { controls ->
            return controls.handle(keyCode)
        }
        return false
    }
}

/** 广播桥：把 android intent 翻译为核心层 WidgetMediaActionReceiver.onReceive(keyCode)。 */
class WidgetMediaActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CoreMediaActions.ACTION_MEDIA) return
        val keyCode = intent.getIntExtra(
            CoreMediaActions.EXTRA_KEY_CODE, CoreMediaActions.KEY_CODE_DEFAULT
        )
        if (!WidgetRuntime.dispatchMedia(keyCode)) {
            Log.w(TAG, "media key $keyCode dropped: player controls not attached")
        }
    }

    companion object {
        private const val TAG = "WidgetMediaAction"
    }
}

/**
 * 小组件基类：统一 onUpdate/onEnabled/onDisabled/刷新广播。
 * 子类只声明 widgetId，其余行为一致。
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    abstract val widgetId: WidgetRenderSpec.WidgetId

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetRenderers.updateWidgets(context, widgetId, appWidgetIds)
        WidgetRefreshScheduler.schedule(context, widgetId)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.schedule(context, widgetId)
        WidgetRenderers.refresh(context, widgetId)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler.cancel(context, widgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            WidgetRefreshScheduler.refreshNow(context, widgetId)
            WidgetRenderers.refresh(context, widgetId)
        }
    }

    companion object {
        /** 失败态「重试」按钮的广播 action（仅本包内使用）。 */
        const val ACTION_REFRESH = "com.github.axtv.dv.widget.REFRESH"
    }
}

class ContinueWidgetReceiver : BaseWidgetProvider() {
    override val widgetId = WidgetRenderSpec.WidgetId.CONTINUE
}

class MixWidgetReceiver : BaseWidgetProvider() {
    override val widgetId = WidgetRenderSpec.WidgetId.MIX
}

class WatchLaterWidgetReceiver : BaseWidgetProvider() {
    override val widgetId = WidgetRenderSpec.WidgetId.WATCH_LATER
}

class LiveWidgetReceiver : BaseWidgetProvider() {
    override val widgetId = WidgetRenderSpec.WidgetId.LIVE
}

class SearchWidgetReceiver : BaseWidgetProvider() {
    override val widgetId = WidgetRenderSpec.WidgetId.SEARCH
}

class NowPlayingWidgetReceiver : BaseWidgetProvider() {
    override val widgetId = WidgetRenderSpec.WidgetId.NOW_PLAYING
}

/** widgetId → provider 组件类（渲染层查 AppWidgetManager 用）。 */
internal fun providerClassFor(widgetId: WidgetRenderSpec.WidgetId): Class<out AppWidgetProvider> =
    when (widgetId) {
        WidgetRenderSpec.WidgetId.CONTINUE -> ContinueWidgetReceiver::class.java
        WidgetRenderSpec.WidgetId.WATCH_LATER -> WatchLaterWidgetReceiver::class.java
        WidgetRenderSpec.WidgetId.MIX -> MixWidgetReceiver::class.java
        WidgetRenderSpec.WidgetId.LIVE -> LiveWidgetReceiver::class.java
        WidgetRenderSpec.WidgetId.SEARCH -> SearchWidgetReceiver::class.java
        WidgetRenderSpec.WidgetId.NOW_PLAYING -> NowPlayingWidgetReceiver::class.java
    }
