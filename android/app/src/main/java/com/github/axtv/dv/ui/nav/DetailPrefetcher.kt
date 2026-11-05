/*
 * 「音视频分离拉取」（设置项 separate_video_fetch）的消费点。
 *
 * 原版语义（不是"音频/视频分轨"，而是**把取流信息从信息流里拆开单独拉**）：
 * - src/app/p000/kq5.java:360 `m7255H()` 读 `fl4.f6276s`（= `separate_video_fetch`，
 *   见 src/app/p000/fl4.java:336，缺省 true）；
 * - 消费点 src/app/p000/yd1.java:178-215（FeedViewModel）：开关关闭时直接
 *   `Log.d("FeedViewModel", "skip detail 预取: separateVideoFetch=false")`，
 *   开启时对「当前条之后的 N 条」各发一次作品详情请求，结果按 awemeId 存进 map；
 *   同一一处写法见 src/app/p000/ij1.java:157、src/app/p000/gx3.java:828；
 * - 取回的 aweme_detail 交给 `VideoPlayUrlResolverKt.resolveFinalPlayUrl(video, gear, detailAweme)`
 *   （src/app/p000/jj5.java:74）—— 也就是给"下一条"补齐信息流里缺失的取流档位。
 *
 * 本应用对应实现：信息流条目解析不出直链时（VideoPlaylistProvider.resolveUrl 返回 null），
 * 按 id 单发一次 VideoApi.detail，把返回的 video 回主线程交给 provider.adoptDetail。
 * 窗口取 2 条：原版 count 由调用方传入（列表预取条数），此处按"下一条 + 再下一条"取，属推断值。
 */
package com.github.axtv.dv.ui.nav

import android.os.Handler
import axtv.api.VideoApi
import axtv.model.Video
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.player.PlaylistProvider
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap

object DetailPrefetcher {

    /** 预取窗口：当前条之后几条。 */
    const val WINDOW = 2

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "detail-prefetch").apply { isDaemon = true }
    }
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /**
     * 为 [fromIndex] 之后的 [WINDOW] 条补拉作品详情。
     *
     * 跳过条件：设置关闭 / 接口未装配 / 该条已能解析出直链 / 同一条已在请求中。
     * 结果只在主线程落地，避免后台线程改播放列表状态。
     */
    fun prefetchUpcoming(provider: PlaylistProvider, fromIndex: Int, mainHandler: Handler) {
        if (AppRuntime.settings?.separateVideoFetch() != true) {
            return
        }
        val api = AppRuntime.api ?: return
        val upcoming = provider.items().drop(fromIndex + 1).take(WINDOW)
        for (item in upcoming) {
            val id = item.id
            if (id.isBlank() || provider.resolveUrl(id) != null || !inFlight.add(id)) {
                continue
            }
            executor.execute {
                try {
                    val video = runCatching { VideoApi(api).detail(id).awemeDetail()?.video() }
                        .getOrNull()
                    if (video != null && hasPlayableAddress(video)) {
                        mainHandler.post { provider.adoptDetail(id, video) }
                    }
                } finally {
                    inFlight.remove(id)
                }
            }
        }
    }

    private fun hasPlayableAddress(video: Video): Boolean =
        video.bitRate().any { it.hasUrls() } || video.playAddr().urlList().isNotEmpty()
}
