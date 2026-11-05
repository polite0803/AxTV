/*
 * 来源: src/app/p000/ro5.java（Compose 内联的播放器生命周期：m9950a / m9955f / m9956g / m9957h）、
 *       mo5.java（换源 + 预缓冲复用 + 重复派发去重）、no5.java（isPlaying 三态驱动）、
 *       ko5.java（Player.Listener：状态/错误重试/首帧/尺寸）、jo5.java（解绑保留最后一帧）、
 *       oo5.java + C1771we.java（引用计数）、sn5.java（预载下一个）
 * 原职责: 播放器准备、起播、暂停、seek、错误重试、降档、自动下一集、首帧静音
 * 重构说明: 把散落在 63 个 Compose 分支里的同一套逻辑收成一台显式状态机
 */
package axtv.player;

import java.util.logging.Logger;

/**
 * 播放器引擎：生命周期与状态机。
 *
 * <h2>一次正常起播</h2>
 * <pre>
 * open(url, seekMs)           → 设置媒体、prepare、（等首帧期间静音）
 * attachSurface(surface)      → 绑定输出
 * play()                      → 有 surface 才真正 play
 * onFirstFrameRendered        → setVolume(1)
 * </pre>
 *
 * <h2>错误处理（{@code ko5.java:154-198}）</h2>
 * <pre>
 * onError:
 *   1. 打 error 日志 + 通知重试钩子
 *   2. 本条未重试过 → prepare() 重试一次
 *   3. 已重试过 或 重试抛异常 → 若是解码器异常且降档回调返回 true → 静默（"已切换到 H.264 档位重载"）
 *   4. 否则 → 状态置 ERROR，onError 上报
 * </pre>
 *
 * <h2>自动下一集</h2>
 * <pre>
 * onPlaybackStateChanged(STATE_ENDED) → queue.onPlaybackEnded() → 有新条目则 open 之
 * </pre>
 *
 * <p>线程模型：所有公开方法都要求主线程调用（与 media3 一致），内部不加锁。
 */
public final class PlayerEngine implements MediaEngine.Listener {

    private static final Logger LOG = Logger.getLogger("VideoPlayer");

    /** 日志里 URL 截断长度：普通日志 60，详细日志 120（{@code ro5.java} 的 {@code jx4.m6886f0(60|120, url)}）。 */
    private static final int URL_LOG_SHORT = 60;
    private static final int URL_LOG_LONG = 120;

    /** 延时调度（Android 侧用 {@code Handler(mainLooper)} 实现）。 */
    public interface Scheduler {
        void postDelayed(Runnable task, long delayMs);

        void remove(Runnable task);
    }

    private final SharedPlayerPool pool;
    private final PlaybackQueue queue;
    private final ResumeStore resume;
    private final VideoMediaCache cache;
    private final CodecFallback codecFallback;
    private final Scheduler scheduler;

    /** 共享播放器身份标识：OkHttpClient 实例（{@code ro5.java:36047} 用 identityHashCode 比较）。 */
    private final Object clientKey;

    private MediaEngine engine;
    private PlayerEventListener listener = new PlayerEventListener() {
    };

    private PlayerState state = PlayerState.UNPREPARED;
    private String currentUrl;
    private String currentItemId;
    private int retryCount;
    private boolean firstFrameUnlocked;
    private boolean surfaceBound;
    private boolean wantPlaying;
    private boolean released;
    private boolean sharedLease;
    private float speed = 1f;

    /** 起播位置，等 surface 就绪后应用一次。 */
    private long pendingSeekMs = -1L;
    private long lastAppliedSeekMs = -1L;

    /** 配置开关。 */
    private boolean muteUntilFirstFrame = true;
    private boolean useCache = true;
    private boolean independent;
    private boolean autoPlay = true;

    private Runnable pendingTransitionTask;

    public PlayerEngine(SharedPlayerPool pool, PlaybackQueue queue, ResumeStore resume,
                        VideoMediaCache cache, CodecFallback codecFallback, Scheduler scheduler) {
        this(pool, queue, resume, cache, codecFallback, scheduler, null);
    }

    /**
     * @param clientKey 共享播放器身份（通常是 OkHttpClient），用于判断是否需要重建内核
     */
    public PlayerEngine(SharedPlayerPool pool, PlaybackQueue queue, ResumeStore resume,
                        VideoMediaCache cache, CodecFallback codecFallback, Scheduler scheduler,
                        Object clientKey) {
        this.pool = pool;
        this.queue = queue;
        this.resume = resume;
        this.cache = cache;
        this.codecFallback = codecFallback;
        this.scheduler = scheduler;
        this.clientKey = clientKey;
    }

    // ------------------------------------------------------------------ 配置

    public PlayerEngine muteUntilFirstFrame(boolean mute) {
        this.muteUntilFirstFrame = mute;
        return this;
    }

    public PlayerEngine useCache(boolean useCache) {
        this.useCache = useCache;
        return this;
    }

    /** 独立播放器（PIP/小窗）不进共享池，销毁时直接 release。 */
    public PlayerEngine independent(boolean independent) {
        this.independent = independent;
        return this;
    }

    public PlayerEngine autoPlay(boolean autoPlay) {
        this.autoPlay = autoPlay;
        return this;
    }

    public void setListener(PlayerEventListener listener) {
        this.listener = listener == null ? new PlayerEventListener() {
        } : listener;
    }

    public PlayerState state() {
        return state;
    }

    public boolean isSurfaceBound() {
        return surfaceBound;
    }

    public String currentUrl() {
        return currentUrl;
    }

    public MediaEngine engine() {
        return engine;
    }

    // ------------------------------------------------------------------ 引擎获取

    /**
     * 取播放内核。共享模式走 {@link SharedPlayerPool#obtain}，独立模式直接新建。
     *
     * <p>日志 {@code "创建播放器实例"} 对应 {@code ro5.java:827}。
     */
    private MediaEngine ensureEngine() {
        if (engine != null) {
            return engine;
        }
        if (independent) {
            LOG.fine("创建播放器实例");
            engine = pool.newIndependent();
        } else {
            engine = pool.obtain(clientKey);
            sharedLease = true;
            pool.addRef();
        }
        engine.addListener(this);
        return engine;
    }

    // ------------------------------------------------------------------ 打开媒体

    /** 打开当前队列项（带进度恢复）。 */
    public void openCurrentItem() {
        PlaybackQueue.Item item = queue.current();
        if (item == null) {
            LOG.warning("队列为空，无法播放");
            return;
        }
        openItem(item);
    }

    /** 打开指定队列项。 */
    public void openItem(PlaybackQueue.Item item) {
        if (item == null) {
            return;
        }
        String url = queue.resolveUrl(item);
        if (url == null || url.isEmpty()) {
            listener.onError(new PlaybackError(0, "解析播放地址失败", false, null));
            return;
        }
        long start = resume == null ? 0L : resume.positionOf(item.id());
        currentItemId = item.id();
        listener.onItemChanged(item, queue.index());
        open(url, start, false);
        // 提前把下一条的头部 2 MiB 抓到缓存（jj5.java:74 / wf5.java:73）
        preloadNext();
    }

    /**
     * 打开一个直链。
     *
     * @param url        直链
     * @param startMs    起播位置，0 表示从头
     * @param forceReload 强制重新设源（切码率/降档时用）
     */
    public void open(String url, long startMs, boolean forceReload) {
        if (released || url == null || url.isEmpty()) {
            return;
        }
        MediaEngine engine = ensureEngine();
        boolean sameUrl = url.equals(currentUrl);

        // ---- 重复派发去重 / 预缓冲复用（mo5.java:118-167）
        if (sameUrl && !forceReload && engine.getPlaybackState() != PlayerState.IDLE.media3Code()) {
            if (retryCount > 0) {
                LOG.fine("跳过重复派发: url=" + truncate(url, URL_LOG_SHORT) + ", attempt=" + retryCount);
            } else {
                LOG.fine("复用预缓冲: url=" + truncate(url, URL_LOG_SHORT));
            }
            if (!engine.isPlaying() && startMs > 0) {
                engine.seekTo(startMs);
            }
            if (wantPlaying) {
                drivePlay();
            }
            return;
        }

        LOG.fine("加载视频: " + truncate(url, URL_LOG_LONG) + ", attempt=" + retryCount);

        // ---- 换源前清理（mo5.java:169-174）
        if (retryCount > 0) {
            engine.stopAndClear();
        } else if (forceReload) {
            engine.stop();
        }

        currentUrl = url;
        retryCount = 0;
        firstFrameUnlocked = false;

        // ---- 等首帧期间静音（ro5.java:1010-1014）
        if (muteUntilFirstFrame && !firstFrameUnlocked) {
            engine.setVolume(PlayerConfig.VOLUME_MUTED);
            LOG.fine("等待首帧，暂时静音: url=" + truncate(url, URL_LOG_SHORT));
        } else {
            engine.setVolume(PlayerConfig.VOLUME_UNMUTED);
        }

        engine.setMediaItem(url, useCache);
        engine.prepare();
        setState(PlayerState.PREPARING);

        pendingSeekMs = startMs > 0 ? startMs : -1L;
        lastAppliedSeekMs = -1L;
        if (startMs > 0) {
            engine.seekTo(startMs);
        }
        if (speed != 1f) {
            engine.setPlaybackSpeed(speed);
        }
        wantPlaying = autoPlay;
        if (wantPlaying) {
            drivePlay();
        }
    }

    /** 预载下一条（不阻塞、失败不报错）。 */
    public void preloadNext() {
        if (cache == null) {
            return;
        }
        PlaybackQueue.Item next = queue.peekNext();
        if (next == null) {
            return;
        }
        String url = queue.resolveUrl(next);
        if (url != null && !url.isEmpty()) {
            cache.requestPreload(url);
        }
    }

    // ------------------------------------------------------------------ 播放控制

    /** 请求播放。没有 surface 时只记下意愿，等绑定后自动起播（{@code no5.java:74-76}）。 */
    public void play() {
        if (released) {
            return;
        }
        wantPlaying = true;
        drivePlay();
    }

    public void pause() {
        wantPlaying = false;
        if (engine != null) {
            engine.setPlayWhenReady(false);
            engine.pause();
        }
        saveResume();
    }

    public void togglePlayPause() {
        if (wantPlaying) {
            pause();
        } else {
            play();
        }
    }

    /**
     * isPlaying 三态驱动（{@code no5.java:56-79}）。
     *
     * <p>"三态"指 {@code isPlaying / surfaceBound / shared}：三者任一变化都会触发重算。
     */
    private void drivePlay() {
        if (engine == null) {
            return;
        }
        LOG.fine("isPlaying changed to: " + wantPlaying + ", surfaceBound=" + surfaceBound
                + ", shared=" + sharedLease + ", url=" + truncate(currentUrl, URL_LOG_SHORT));
        if (!wantPlaying) {
            engine.setPlayWhenReady(false);
            engine.pause();
            return;
        }
        if (!surfaceBound) {
            return;
        }
        if (sharedLease) {
            // 共享播放器上可能残留上一条的进度，先归零（no5.java:79 seekToDefaultPosition）
            engine.seekToDefaultPosition();
        }
        if (pendingSeekMs >= 0 && pendingSeekMs != lastAppliedSeekMs) {
            // 恢复进度：原实现从 zd1 进度库取位再 seek，这里由 open() 传入
            lastAppliedSeekMs = pendingSeekMs;
            engine.seekTo(pendingSeekMs);
        }
        engine.setPlayWhenReady(true);
        engine.play();
    }

    public void seekTo(long positionMs) {
        if (engine == null || released) {
            return;
        }
        engine.seekTo(Math.max(0L, positionMs));
    }

    /** 播放速度，下限 {@link PlayerConfig#MIN_PLAYBACK_SPEED}（{@code ro5.java:1173-1177}）。 */
    public void setSpeed(float speed) {
        this.speed = Math.max(PlayerConfig.MIN_PLAYBACK_SPEED, speed);
        if (engine != null) {
            engine.setPlaybackSpeed(this.speed);
        }
    }

    public float speed() {
        return speed;
    }

    // ------------------------------------------------------------------ 画面

    /**
     * 绑定输出 surface。
     *
     * <p>{@code lp1.m7472a}：先比对 {@code (player, surface)} 组合，变了才 {@code setVideoSurface}。
     */
    public void attachSurface(Object surface) {
        if (engine == null || released) {
            return;
        }
        engine.setVideoSurface(surface);
        surfaceBound = surface != null;
        listener.onSurfaceBound(surfaceBound);
        if (surfaceBound && wantPlaying) {
            drivePlay();
        }
    }

    /**
     * 解绑 surface，保留最后一帧（{@code jo5.java:120-130}）。
     *
     * <p>Android 侧要配合 {@code PlayerView.setKeepContentOnPlayerReset(true)} +
     * {@code setPlayer(null)}，否则会黑屏闪一下。
     */
    public void detachSurfaceKeepLastFrame() {
        if (engine == null) {
            return;
        }
        engine.setVideoSurface(null);
        surfaceBound = false;
        listener.onSurfaceBound(false);
        LOG.fine("解绑 Surface 保留最后一帧: url=" + truncate(currentUrl, URL_LOG_SHORT));
    }

    // ------------------------------------------------------------------ 转场预缓冲

    /**
     * 转场动画期间准备下一条（{@code ro5.m9956g}，"只下载不解码"）。
     *
     * <p>策略：旧片还在播时最多等 {@link PlayerConfig#ANIM_PREPARE_DEADLINE_MS}（每
     * {@link PlayerConfig#ANIM_PREPARE_SLICE_MS} 检查一次），然后
     * {@code pause → setMediaItem → prepare → seekTo}，但不 play。
     */
    public void prepareDuringTransition(String url, long seekMs) {
        if (released || url == null || url.isEmpty()) {
            return;
        }
        MediaEngine engine = ensureEngine();
        if (url.equals(currentUrl) && engine.getPlaybackState() != PlayerState.IDLE.media3Code()) {
            return;
        }
        scheduleTransitionPrepare(url, seekMs, 0L);
    }

    private void scheduleTransitionPrepare(String url, long seekMs, long elapsedMs) {
        MediaEngine engine = ensureEngine();
        boolean shouldWait = engine.isPlaying() && elapsedMs < PlayerConfig.ANIM_PREPARE_DEADLINE_MS;
        if (!shouldWait) {
            doTransitionPrepare(url, seekMs);
            return;
        }
        Runnable task = () -> scheduleTransitionPrepare(url, seekMs,
                elapsedMs + PlayerConfig.ANIM_PREPARE_SLICE_MS);
        pendingTransitionTask = task;
        scheduler.postDelayed(task, PlayerConfig.ANIM_PREPARE_SLICE_MS);
    }

    private void doTransitionPrepare(String url, long seekMs) {
        MediaEngine engine = ensureEngine();
        LOG.fine("动画期间准备视频(只下载不解码): " + truncate(url, URL_LOG_LONG));
        engine.setPlayWhenReady(false);
        engine.pause();
        engine.setMediaItem(url, useCache);
        engine.prepare();
        if (seekMs > 0) {
            engine.seekTo(seekMs);
        }
        currentUrl = url;
    }

    private void cancelPendingTransition() {
        if (pendingTransitionTask != null) {
            scheduler.remove(pendingTransitionTask);
            pendingTransitionTask = null;
        }
    }

    // ------------------------------------------------------------------ 队列

    /** 下一条：优先队列推进；到底则停。 */
    public boolean playNext() {
        if (!queue.hasNext()) {
            return false;
        }
        PlaybackQueue.Item next = queue.next();
        if (next == null) {
            return false;
        }
        openItem(next);
        return true;
    }

    public boolean playPrevious() {
        if (!queue.hasPrevious()) {
            return false;
        }
        PlaybackQueue.Item prev = queue.previous();
        if (prev == null) {
            return false;
        }
        openItem(prev);
        return true;
    }

    // ------------------------------------------------------------------ MediaEngine.Listener

    @Override
    public void onPlaybackStateChanged(int media3State) {
        PlayerState newState = PlayerState.fromMedia3(media3State);
        LOG.fine("playbackState=" + media3State + ", url=" + truncate(currentUrl, URL_LOG_LONG));
        setState(newState);
        if (newState == PlayerState.ENDED) {
            listener.onEnded();
            saveResume();
            PlaybackQueue.Item next = queue.onPlaybackEnded();
            if (next != null) {
                openItem(next);
            }
        }
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        listener.onIsPlayingChanged(isPlaying);
    }

    @Override
    public void onPlayerError(PlaybackError error) {
        LOG.severe(error.toLogLine());

        // 1) 本条尚未重试 → 直接 prepare 重试一次（ko5.java:184-194）
        if (retryCount < PlayerConfig.MAX_RETRY_PER_ITEM) {
            retryCount++;
            try {
                engine.prepare();
                listener.onRetrying(retryCount);
                return;
            } catch (RuntimeException e) {
                LOG.severe("重试也失败: " + e.getMessage());
                reportFatal(error, e.getMessage());
                return;
            }
        }
        // 2) 已重试过 → 尝试降档，失败则上报
        reportFatal(error, null);
    }

    /**
     * 不可恢复：先尝试降档到 H.264，再上报。
     *
     * @param overrideMessage 重试时捕获到的异常信息，优先级高于原始错误信息
     */
    private void reportFatal(PlaybackError error, String overrideMessage) {
        if (error.decoderFailure() && codecFallback != null && codecFallback.switchToH264(currentUrl)) {
            LOG.warning("视频解码失败，已切换到 H.264 档位重载");
            return;
        }
        setState(PlayerState.ERROR);
        PlaybackError reported = overrideMessage == null
                ? error
                : new PlaybackError(error.errorCode(), overrideMessage, error.decoderFailure(), error.cause());
        listener.onError(reported);
    }

    @Override
    public void onRenderedFirstFrame() {
        String playingUrl = engine == null ? null : engine.getCurrentMediaUri();
        LOG.fine("First frame rendered for url=" + truncate(playingUrl, URL_LOG_SHORT));
        if (playingUrl != null && currentUrl != null && !playingUrl.equals(currentUrl)) {
            return;
        }
        if (!firstFrameUnlocked) {
            firstFrameUnlocked = true;
            if (engine != null) {
                engine.setVolume(PlayerConfig.VOLUME_UNMUTED);
            }
            LOG.fine("首帧音频解锁(rendered): url=" + truncate(currentUrl, URL_LOG_SHORT));
        }
        listener.onFirstFrameRendered(currentUrl);
    }

    @Override
    public void onVideoSizeChanged(VideoSize size) {
        if (size == null || !size.isValid()) {
            return;
        }
        LOG.fine("Video size changed to " + size.width() + "x" + size.height()
                + " for url=" + truncate(currentUrl, URL_LOG_SHORT));
        listener.onVideoSizeChanged(size);
    }

    // ------------------------------------------------------------------ 生命周期

    private void setState(PlayerState newState) {
        if (state == newState) {
            return;
        }
        state = newState;
        listener.onStateChanged(newState);
    }

    private void saveResume() {
        if (resume == null || currentItemId == null || engine == null) {
            return;
        }
        resume.savePosition(currentItemId, engine.getCurrentPosition());
    }

    /**
     * 释放。共享播放器只退引用计数（真正暂停发生在计数归零时），
     * 独立播放器直接 release（{@code oo5.dispose}）。
     */
    public void release() {
        if (released) {
            return;
        }
        saveResume();
        cancelPendingTransition();
        released = true;
        if (engine != null) {
            engine.removeListener(this);
            if (independent) {
                SharedPlayerPool.releaseIndependent(engine);
            } else if (sharedLease) {
                pool.releaseRef(true);
            }
            engine = null;
        }
        surfaceBound = false;
        setState(PlayerState.RELEASED);
    }

    /** 日志截断，等价 {@code jx4.m6886f0(n, url)}。 */
    private static String truncate(String url, int max) {
        if (url == null) {
            return "null";
        }
        return url.length() <= max ? url : url.substring(0, max);
    }
}
