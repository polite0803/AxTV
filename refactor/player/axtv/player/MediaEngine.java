/*
 * 来源: src/app/p000/ro5.java（ExoPlayer 全部调用点）、mo5.java、no5.java、ko5.java、jo5.java
 * 原职责: 直接对 ExoPlayer 编程（创建、换源、prepare、播放、seek、音量、变速、挂 surface）
 * 重构说明: 抽象为接口，Android 侧用 media3 ExoPlayer 实现；核心层只依赖此接口
 */
package axtv.player;

/**
 * 播放内核抽象，一对一映射项目用到的 ExoPlayer API。
 *
 * <p>Android 侧实现建议：
 * <pre>
 * ExoPlayer.Builder(ctx)
 *   .setRenderersFactory(customRenderersFactory)          // po5.java：自定义 AudioSink + sy5 渲染器
 *   .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
 *   .build()
 * </pre>
 * 见 {@code ro5.java:35916-35929}。
 *
 * <p>所有方法都必须在主线程调用（media3 的线程约束），实现类不需要额外加锁。
 */
public interface MediaEngine {

    // ------------------------------------------------------------------ 换源

    /**
     * 设置媒体源。
     *
     * @param uri      直链
     * @param useCache true 时走 {@code CacheDataSource}（{@code mo5.java:181-188} 的两个分支：
     *                 有 OkHttpClient 就 {@code DefaultMediaSourceFactory(new OkHttpDataSource.Factory(client))}
     *                 建 MediaSource，否则退回 {@code MediaItem.fromUri}）
     */
    void setMediaItem(String uri, boolean useCache);

    /** {@code exoPlayer.prepare()}。 */
    void prepare();

    /** 丢弃当前媒体与缓冲（{@code mo5.java:170-174} {@code stop()+clearMediaItems()}）。 */
    void stopAndClear();

    /** 仅 stop，保留 mediaItem（{@code mo5.java:173}）。 */
    void stop();

    // ------------------------------------------------------------------ 播放控制

    void play();

    void pause();

    void setPlayWhenReady(boolean playWhenReady);

    boolean isPlaying();

    /** media3 {@code Player.STATE_*}。 */
    int getPlaybackState();

    void seekTo(long positionMs);

    /** {@code seekToDefaultPosition()}，共享播放器复用时用来抹掉上一条的进度（{@code no5.java:79}）。 */
    void seekToDefaultPosition();

    long getCurrentPosition();

    long getDuration();

    // ------------------------------------------------------------------ 参数

    void setVolume(float volume);

    void setPlaybackSpeed(float speed);

    // ------------------------------------------------------------------ 画面

    /**
     * 绑定/解绑视频输出。
     *
     * @param surface Android {@code android.view.Surface} 实例；null 表示解绑
     *                （{@code lp1.java:37} / {@code lp1.java:58}）
     */
    void setVideoSurface(Object surface);

    VideoSize getVideoSize();

    String getCurrentMediaUri();

    // ------------------------------------------------------------------ 监听

    void addListener(Listener listener);

    void removeListener(Listener listener);

    void release();

    /**
     * 播放事件回调，等价于 {@code Player.Listener} 中项目实际用到的 5 个方法
     * （{@code ko5.java} 其余 30 个空实现的方法不再保留）。
     */
    interface Listener {

        void onPlaybackStateChanged(int state);

        void onIsPlayingChanged(boolean isPlaying);

        void onPlayerError(PlaybackError error);

        void onRenderedFirstFrame();

        void onVideoSizeChanged(VideoSize videoSize);
    }
}
