/*
 * 来源: src/app/p000/ko5.java（Player.Listener → 状态/错误/首帧/尺寸）、
 *       ro5.java:35890-35913（首帧解锁音频 m9953d / 首帧回调 m9954e）、
 *       no5.java（isPlaying 三态驱动播放）
 * 原职责: 播放器对外事件面
 * 重构说明: 统一成一组默认空实现的方法，UI 层只覆写关心的几个
 */
package axtv.player;

/**
 * 播放器对外事件。
 *
 * <p>与反编译产物的对应关系：
 * <table border="1">
 *   <caption>事件映射</caption>
 *   <tr><th>本接口</th><th>原实现</th></tr>
 *   <tr><td>{@link #onStateChanged}</td><td>{@code ko5.onPlaybackStateChanged}</td></tr>
 *   <tr><td>{@link #onIsPlayingChanged}</td><td>{@code ko5.onIsPlayingChanged}</td></tr>
 *   <tr><td>{@link #onFirstFrameRendered}</td><td>{@code ko5.onRenderedFirstFrame} + {@code ro5.m9953d/9954e}</td></tr>
 *   <tr><td>{@link #onVideoSizeChanged}</td><td>{@code ko5.onVideoSizeChanged}</td></tr>
 *   <tr><td>{@link #onError}</td><td>{@code ko5.onPlayerError} 尾部上报</td></tr>
 *   <tr><td>{@link #onRetrying}</td><td>{@code ko5.onPlayerError} 的 {@code prepare()} 分支</td></tr>
 *   <tr><td>{@link #onEnded}</td><td>{@code ko5.onPlaybackStateChanged(STATE_ENDED)} 回调</td></tr>
 * </table>
 */
public interface PlayerEventListener {

    /** 状态机变化。 */
    default void onStateChanged(PlayerState state) {
    }

    /** 播放/暂停变化（含"等 surface"期间的期望态）。 */
    default void onIsPlayingChanged(boolean isPlaying) {
    }

    /**
     * 首帧已渲染：此时解除静音、隐藏 loading、上报起播耗时。
     *
     * @param url 当前直链
     */
    default void onFirstFrameRendered(String url) {
    }

    /** 视频尺寸变化（含 pixelWidthHeightRatio）。 */
    default void onVideoSizeChanged(VideoSize size) {
    }

    /** 不可恢复错误（已重试、已尝试降档）。 */
    default void onError(PlaybackError error) {
    }

    /** 正在重试。 */
    default void onRetrying(int attempt) {
    }

    /** 当前条目播放完毕（自动连播前）。 */
    default void onEnded() {
    }

    /** 切换到了新的条目。 */
    default void onItemChanged(PlaybackQueue.Item item, int index) {
    }

    /** surface 绑定状态变化（GL 模式下尤其重要：未绑定不能 play）。 */
    default void onSurfaceBound(boolean bound) {
    }
}
