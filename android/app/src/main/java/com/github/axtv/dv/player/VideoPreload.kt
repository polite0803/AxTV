/*
 * Android 适配层 —— 「起播预载」（设置项 video_start_prefetch）。
 *
 * 证据：
 * - src/app/p000/sn5.java:78-121（m10277d：`isCached(url,0,2MiB)` 去重 + CacheWriter + 单线程 executor + 取消旧任务）；
 *   日志 TAG `VideoMediaCache`、文案「预取完成 / 预取失败」在 src/app/p000/n95.java:55/103。
 * - 调用点 src/app/p000/jj5.java:64-76 与 wf5.java:73：`if (预载开关) 取下一条 → resolveFinalPlayUrl → sn5.m10277d(...)`。
 * - 核心层把这套策略收敛成 axtv.player.VideoMediaCache（refactor/player/NOTES.md §4.4），
 *   PlayerEngine.openItem 结尾就是它的预载点（PlayerEngine.java:206 → preloadNext）。
 *
 * 本文件只做 Android 侧的 CacheStore：SimpleCache + OkHttp 数据源都在 Media3Resources 里，
 * 与播放用的缓存是同一份，所以预载下来的 2 MiB 下一条起播时能直接命中。
 */
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.github.axtv.dv.player

import android.content.Context
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import axtv.player.PlayerConfig
import axtv.player.VideoMediaCache
import com.github.axtv.dv.adapter.AppRuntime
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 进程级预载器（对应原版 sn5 的静态单例 + 单线程 executor）。
 *
 * 设置项 `video_start_prefetch` 的消费点在 [createIfNeeded]：关闭时返回 null，
 * PlayerEngine 拿不到 cache 就不会发起预载；开启时预载「下一条」的头部
 * [PlayerConfig.PRELOAD_BYTES] 字节。与「视频加载超时」同口径 —— 档位在下一次进播放器时生效。
 */
object VideoPreload {

    @Volatile
    private var cache: VideoMediaCache? = null

    fun createIfNeeded(context: Context): VideoMediaCache? {
        if (AppRuntime.settings?.videoStartPrefetch() != true) {
            return null
        }
        return cache ?: synchronized(this) {
            cache ?: VideoMediaCache(Store(context.applicationContext)).also { cache = it }
        }
    }

    /** 退出播放会话时取消在跑的预载，避免离开页面后还在拉流。 */
    fun cancelPending() {
        cache?.cancelPreload()
    }

    /** SimpleCache + CacheWriter 版缓存后端。 */
    private class Store(private val appContext: Context) : VideoMediaCache.CacheStore {

        override fun isCached(uri: String, position: Long, length: Long): Boolean =
            Media3Resources.cache(appContext).isCached(uri, position, length)

        override fun startPreload(uri: String, length: Long): VideoMediaCache.Handle {
            // createDataSourceForDownloading：只写缓存、不走缓存读，等价 sn5.m10275b + CacheWriter。
            val dataSource = CacheDataSource.Factory()
                .setCache(Media3Resources.cache(appContext))
                .setUpstreamDataSourceFactory(Media3Resources.plainFactory())
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSourceForDownloading()
            val writer = CacheWriter(
                dataSource,
                DataSpec.Builder()
                    .setUri(uri)
                    .setPosition(0L)
                    .setLength(length)
                    .build(),
                null,
                null,
            )
            val cancelled = AtomicBoolean(false)
            val startedAt = System.currentTimeMillis()
            try {
                writer.cache()
                if (!cancelled.get()) {
                    log("预取完成: ${uri.take(60)}, ${System.currentTimeMillis() - startedAt}ms")
                }
            } catch (t: Throwable) {
                // InterruptedException 需要还原中断标记（原版 n95 同样处理）。
                if (t is InterruptedException) Thread.currentThread().interrupt()
                log("预取失败: ${uri.take(60)}（${t.javaClass.simpleName}）")
            }
            return object : VideoMediaCache.Handle {
                override fun cancel() {
                    cancelled.set(true)
                    runCatching { writer.cancel() }
                }
            }
        }

        override fun createDataSourceFactory(okHttpClient: Any?): Any =
            Media3Resources.cachedFactory(appContext)

        // SimpleCache 是进程级单例（同目录不可二次实例化），会话退出只取消任务、不关缓存。
        override fun close() = Unit

        private fun log(message: String) {
            android.util.Log.d("VideoMediaCache", message)
        }
    }
}
