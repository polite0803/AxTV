/*
 * 弹幕浮层 ↔ 播放器的接口定义（不修改 playerUI/播放器/... 包）。
 *
 * 播放器线只需实现 [DanmakuPlaybackSource]（或在播放器回调里转调），
 * 然后把 [DanmakuOverlayController] 叠到画面层之上即可。
 *
 * Media3 接线示例（伪代码，放在播放器线自己的适配层里，不要写进 player）：
 * <pre>
 * class Media3DanmakuSource(private val player: Player) : DanmakuPlaybackSource {
 *     override fun positionMs() = player.currentPosition
 *     override fun isPlaying() = player.isPlaying
 *     override fun viewportWidthPx() = player.videoSize.width
 *     override fun viewportHeightPx() = player.videoSize.height
 *     override fun addListener(l: DanmakuPlaybackSource.Listener) { ... player.addListener(...) }
 *     override fun removeListener(l: DanmakuPlaybackSource.Listener) { ... }
 * }
 * </pre>
 *
 * 播放器回调映射：
 * - onIsPlayingChanged → [DanmakuPlaybackSource.Listener.onPlaybackStateChanged]
 * - onPositionDiscontinuity / seekTo → [DanmakuPlaybackSource.Listener.onSeek]
 * - onVideoSizeChanged → [DanmakuPlaybackSource.Listener.onVideoSizeChanged]
 */
package com.github.axtv.dv.danmaku

/**
 * 播放器需要向弹幕浮层提供的全部信息。
 *
 * 浮层每帧主动拉取 [positionMs] / [isPlaying]，事件监听只用于「暂停后恢复」「seek」
 * 「视频尺寸变化」这类需要立即重绘的时机，避免长时间空转。
 */
interface DanmakuPlaybackSource {

    /** 当前播放位置（毫秒）。暂停时返回值应保持不变。 */
    fun positionMs(): Long

    /** 是否正在播放。 */
    fun isPlaying(): Boolean

    /** 播放器画面宽（px）；返回 0 表示未知，浮层回落到自身 View 尺寸。 */
    fun viewportWidthPx(): Int = 0

    /** 播放器画面高（px）；返回 0 表示未知。 */
    fun viewportHeightPx(): Int = 0

    fun addListener(listener: Listener) {}

    fun removeListener(listener: Listener) {}

    interface Listener {
        fun onPlaybackStateChanged(isPlaying: Boolean) {}

        /** seek 完成后的新位置。 */
        fun onSeek(positionMs: Long) {}

        fun onVideoSizeChanged(widthPx: Int, heightPx: Int) {}
    }
}

/** 浮层对外控制面；播放器线 / Compose 宿主只需依赖这个接口。 */
interface DanmakuOverlayController {

    /** 绑定播放器数据源；传 null 等同于 [detach]。 */
    fun attach(source: DanmakuPlaybackSource?)

    fun detach()

    fun setEnabled(enabled: Boolean)

    /** 整片弹幕列表（可分批调用；已上屏条目不会被清掉）。 */
    fun setComments(comments: List<DanmakuComment>)

    fun setConfig(config: DanmakuConfig)

    /**
     * 本地发送（占位方案）：立即上屏，不做任何后端请求。
     *
     * @return 文本为空或弹幕关闭时返回 false
     */
    fun sendLocal(
        text: String,
        type: DanmakuType = DanmakuType.SCROLL,
        colorArgb: Int = DanmakuComment.DEFAULT_COLOR_ARGB,
    ): Boolean

    /** 播放器 seek 后主动通知，立即按新位置重建时间轴。 */
    fun seekTo(positionMs: Long)
}
