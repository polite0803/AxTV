/*
 * 来源: src/app/p000/sn5.java（SimpleCache + 预载调度）、rn5.java（预载任务包装）、
 *       mo5.java:181-188（带缓存建 MediaSource）、ro5.java:35922-35924
 * 原职责: 视频缓存目录/容量/LRU 淘汰/头部 2MiB 预载/单线程串行 + 取消
 * 重构说明: 保留全部策略与去重语义，把 SimpleCache 换成 CacheStore 接口
 */
package axtv.player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * 视频介质缓存。
 *
 * <p>策略（{@code sn5.java}）：
 * <ul>
 *   <li>目录 {@code <cacheDir>/video-media}，LRU 上限 96 MiB；</li>
 *   <li>播放走 {@code CacheDataSource}，flags=2（出错忽略缓存直接回源）；</li>
 *   <li>预载只抓头部 2 MiB，单线程串行，新请求取消旧请求；</li>
 *   <li>同一 url 已在预载 / 已缓存 2 MiB / url 为空 → 直接返回。</li>
 * </ul>
 */
public final class VideoMediaCache {

    private static final Logger LOG = Logger.getLogger("VideoPlayer");

    /** 缓存后端。Android 侧由 SimpleCache + CacheDataSource.Factory 实现。 */
    public interface CacheStore {

        /** {@code SimpleCache.isCached(uri, position, length)}。 */
        boolean isCached(String uri, long position, long length);

        /** 启动一次区间下载，返回的句柄用于取消。 */
        Handle startPreload(String uri, long length);

        /**
         * 建带缓存的 MediaSource 工厂，供 {@link MediaEngine#setMediaItem} 的缓存分支使用。
         *
         * <p>Android 侧返回 {@code CacheDataSource.Factory}，实现要点见 {@link VideoMediaCache} 注释。
         *
         * @param okHttpClient Android {@code okhttp3.OkHttpClient}
         */
        Object createDataSourceFactory(Object okHttpClient);

        /** 释放缓存实例（进程退出 / 切换账号）。 */
        void close();
    }

    /** 一次预载任务。 */
    public interface Handle {
        void cancel();
    }

    private final CacheStore store;
    private final ExecutorService executor;

    /** 正在预载的 url（对应 {@code sn5.f20063e} 里保存的 uri）。 */
    private volatile String preloadingUrl;
    private volatile Handle currentHandle;
    private volatile Future<?> currentTask;

    public VideoMediaCache(CacheStore store) {
        this.store = store;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "video-media-cache");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 请求预载 {@code url} 的头部 2 MiB。
     *
     * <p>对应 {@code sn5.m10277d}：url 为空直接返回；已缓存 2 MiB 直接返回；
     * 同一 url 已在预载则去重；否则取消上一个任务再提交（单线程串行）。
     */
    public synchronized void requestPreload(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        if (store.isCached(url, 0L, PlayerConfig.PRELOAD_BYTES)) {
            LOG.fine("跳过预载: 已缓存 " + PlayerConfig.PRELOAD_BYTES + " 字节, url=" + url);
            return;
        }
        if (url.equals(preloadingUrl)) {
            return;
        }
        cancelLocked();
        preloadingUrl = url;
        currentTask = executor.submit(() -> runPreload(url));
    }

    private void runPreload(String url) {
        try {
            Handle handle = store.startPreload(url, PlayerConfig.PRELOAD_BYTES);
            synchronized (this) {
                // 提交后可能已被新请求顶掉，此时立刻取消，避免两条流同时下
                if (url.equals(preloadingUrl) && currentTask != null && !currentTask.isCancelled()) {
                    currentHandle = handle;
                } else {
                    handle.cancel();
                }
            }
        } catch (RuntimeException e) {
            LOG.warning("预载失败: url=" + url + ", " + e);
        }
    }

    /** 取消当前预载（{@code sn5.m10274a}）。 */
    public synchronized void cancelPreload() {
        cancelLocked();
    }

    private void cancelLocked() {
        Handle handle = currentHandle;
        if (handle != null) {
            try {
                handle.cancel();
            } catch (RuntimeException e) {
                LOG.warning("取消预载失败: " + e);
            }
        }
        currentHandle = null;
        Future<?> task = currentTask;
        if (task != null) {
            task.cancel(true);
        }
        currentTask = null;
        preloadingUrl = null;
    }

    /** 当前预载的 url，无则 null。 */
    public String preloadingUrl() {
        return preloadingUrl;
    }

    /** 缓存是否已覆盖从 {@code position} 起的 {@code length} 字节。 */
    public boolean isCached(String url, long position, long length) {
        return url != null && store.isCached(url, position, length);
    }

    /**
     * 建"带缓存"的 MediaSource 工厂，对应 {@code sn5.m10275b}：
     * <pre>
     * OkHttpDataSource.Factory upstream = new OkHttpDataSource.Factory(client)
     *         .setDefaultRequestProperties(HEADERS);
     * return new CacheDataSource.Factory()
     *         .setCache(simpleCache)
     *         .setUpstreamDataSourceFactory(upstream)
     *         .setFlags(2);   // IGNORE_CACHE_ON_ERROR
     * </pre>
     */
    public Object dataSourceFactory(Object okHttpClient) {
        return store.createDataSourceFactory(okHttpClient);
    }

    /** 关停线程池并释放缓存（进程退出）。 */
    public void shutdown() {
        cancelPreload();
        executor.shutdownNow();
        store.close();
    }

    /** 预载与播放请求共用的请求头（{@code sn5.f20064f}，顺序保持一致）。 */
    public static Map<String, String> requestHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", PlayerConfig.CACHE_USER_AGENT);
        headers.put("Referer", PlayerConfig.CACHE_REFERER);
        headers.put("Accept", "*/*");
        headers.put("Accept-Language", PlayerConfig.CACHE_ACCEPT_LANGUAGE);
        headers.put("sec-ch-ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"147\", \"Google Chrome\";v=\"147\"");
        headers.put("sec-ch-ua-mobile", "?0");
        headers.put("sec-ch-ua-platform", "\"Windows\"");
        return Collections.unmodifiableMap(headers);
    }
}
