/*
 * Android 适配层 —— widget 模块：6 个 Glance 桌面小组件接收器 + 媒体键桥。
 * Manifest: widget.ContinueWidgetReceiver 等 6 receiver + widget.WidgetMediaActionReceiver。
 * 行为: 渲染规格在核心层 WidgetRenderSpec（933 行，ADAPTERS.md §8 / widget/NOTES.md §3.1-3.6）；
 *       本文件为可编译骨架：Glance 占位 UI（kind 名 + 提示），数据管线
 *       （WidgetDataSource → WidgetRenderSpec → Compose 树）按规格逐组件落地。
 * 调度: WidgetRefreshScheduler → WorkManager（核心层已含 clamp 15min/5min 规则）。
 */
package com.github.axtv.dv.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import axtv.widget.WidgetMediaActionReceiver as CoreMediaActionReceiver

/** 全局播放器媒体键桥（原 wi4.g 挂点；Media3Engine 落地后由 MyApplication 装配）。 */
object WidgetRuntime {
    @Volatile
    var mediaAction: CoreMediaActionReceiver? = null
}

/** 广播桥：把 android intent 翻译为核心层 WidgetMediaActionReceiver.onReceive(keyCode)。 */
class WidgetMediaActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CoreMediaActionReceiver.ACTION_MEDIA) return
        val keyCode = intent.getIntExtra(
            CoreMediaActionReceiver.EXTRA_KEY_CODE, CoreMediaActionReceiver.KEY_CODE_DEFAULT
        )
        WidgetRuntime.mediaAction?.onReceive(keyCode)
            ?: Log.w(TAG, "media key $keyCode dropped: runtime not attached")
    }

    companion object {
        private const val TAG = "WidgetMediaAction"
    }
}

/** 占位 Glance 面板：数据管线接入后替换为 WidgetRenderSpec 绘制。 */
private class PlaceholderWidget(private val label: String) : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box(
                modifier = GlanceModifier.fillMaxSize().background(android.graphics.Color.DKGRAY),
                contentAlignment = Alignment.Center,
            ) {
                Text("$label\n（渲染规格 WidgetRenderSpec 待落地）")
            }
        }
    }
}

class ContinueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaceholderWidget("继续看")
}

class MixWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaceholderWidget("合集追更")
}

class WatchLaterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaceholderWidget("稍后再看")
}

class LiveWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaceholderWidget("关注的直播")
}

class SearchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaceholderWidget("搜索")
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaceholderWidget("正在播放")
}
