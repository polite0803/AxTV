/*
 * Android 适配层 —— widget 模块：真实数据源。
 *
 * 规格出处: refactor/widget/NOTES.md §3（六组件显示字段）/ §6（刷新）
 * 数据来源:
 *   CONTINUE     VideoApi.historyRead(maxCursor=0, count=20, status=0)
 *   WATCH_LATER  VideoApi.watchLater(offset=0, count=20, operateType=0)
 *   MIX          CollectionApi.mixListCollection + mixDetail + mixAwemes
 *   LIVE         LiveApi.followFeed("aweme_pc_follow_top")
 *   SEARCH       SearchApi.hotSearch()
 *   NOW_PLAYING  WidgetRuntime.publishPlayback 进程内快照（未装配 → IDLE，不伪造数据）
 *
 * 失败落 ERROR、未登录落 READY 空数据（由 WidgetRenderSpec 的 GUIDE 分支展示登录引导），
 * 不做假数据兜底。快照只读本地缓存，网络请求只发生在 WidgetRefreshWorker 的后台线程。
 */
package com.github.axtv.dv.widget

import android.content.Context
import android.util.Log
import axtv.api.CollectionApi
import axtv.api.LiveApi
import axtv.api.SearchApi
import axtv.api.VideoApi
import axtv.model.Aweme
import axtv.model.DataModels
import axtv.model.MixInfo
import axtv.model.MixStatis
import axtv.widget.WidgetRenderSpec
import com.github.axtv.dv.adapter.AppRuntime
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "WidgetData"
private const val PREFS = "widget_data_cache"
private const val MAX_VIDEOS = 5
private const val MAX_MIXES = 3
private const val MAX_LIVES = 3
private const val MAX_WORDS = 6

/** 关注的直播场景值，取自反编译调用点 kq5.m8025Q("aweme_pc_follow_top")。 */
private const val LIVE_FOLLOW_SCENE = "aweme_pc_follow_top"

class WidgetRealDataProvider(context: Context) : WidgetDataProvider {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- snapshot

    override fun snapshot(widgetId: WidgetRenderSpec.WidgetId): WidgetData {
        if (widgetId == WidgetRenderSpec.WidgetId.NOW_PLAYING) {
            return WidgetRuntime.playbackData()
        }
        val raw = prefs.getString(key(widgetId), null) ?: return WidgetData(state = WidgetDataState.LOADING)
        return runCatching { decode(widgetId, JSONObject(raw)) }
            .getOrElse {
                Log.w(TAG, "cache decode failed for $widgetId: ${it.javaClass.simpleName}")
                WidgetData(state = WidgetDataState.ERROR)
            }
    }

    // ---------------------------------------------------------------- refresh

    override fun refresh(widgetId: WidgetRenderSpec.WidgetId) {
        if (widgetId == WidgetRenderSpec.WidgetId.NOW_PLAYING) return

        val settings = AppRuntime.settings
        val api = AppRuntime.api
        val accountId = settings?.currentAccountId().orEmpty()

        // 未登录时不下发请求；写入空数据，交给 RenderSpec 的 GUIDE 分支。
        if (settings == null || api == null || !settings.isLoggedIn()) {
            save(widgetId, WidgetData(accountId = accountId))
            return
        }
        syncCookieFromSettings(settings.cookie())

        val data = try {
            when (widgetId) {
                WidgetRenderSpec.WidgetId.CONTINUE -> WidgetData(
                    videos = fetchVideos(history = true),
                    accountId = accountId,
                )

                WidgetRenderSpec.WidgetId.WATCH_LATER -> WidgetData(
                    videos = fetchVideos(history = false),
                    accountId = accountId,
                )

                WidgetRenderSpec.WidgetId.MIX -> WidgetData(
                    mixes = fetchMixes(),
                    accountId = accountId,
                )

                WidgetRenderSpec.WidgetId.LIVE -> WidgetData(
                    lives = fetchLives(),
                    accountId = accountId,
                )

                WidgetRenderSpec.WidgetId.SEARCH -> WidgetData(
                    hotWords = fetchHotWords(),
                    accountId = accountId,
                )

                WidgetRenderSpec.WidgetId.NOW_PLAYING -> WidgetData()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "refresh $widgetId failed: ${t.javaClass.simpleName}")
            WidgetData(state = WidgetDataState.ERROR, accountId = accountId)
        }

        save(widgetId, data.copy(updatedAt = System.currentTimeMillis()))
    }

    // ---------------------------------------------------------------- 数据映射

    private fun fetchVideos(history: Boolean): List<WidgetVideoItem> {
        val api = VideoApi(AppRuntime.api ?: return emptyList())
        val response = if (history) {
            api.historyRead(0L, 20, 0)
        } else {
            api.watchLater(0, 20, 0)
        }
        return DataModels.filterPrimaryPlayableAwemes(response.works().orEmpty())
            .take(MAX_VIDEOS)
            .map { it.toVideoItem() }
            .also { preloadCovers(it.map(WidgetVideoItem::coverUrl)) }
    }

    private fun fetchMixes(): List<WidgetMixItem> {
        val api = AppRuntime.api ?: return emptyList()
        val collectionApi = CollectionApi(api)
        val list = collectionApi.mixListCollection(0L, 20)
        val infos = list.mixInfos().orEmpty()
            .filter { it.mixId().isNotEmpty() }
            .take(MAX_MIXES)

        val items = infos.mapIndexed { index, info ->
            // 合集列表项的 statis 可能不含进度，首项补一次 mixDetail；
            // 首项同时拉 mixAwemes 以拿到「继续第 N 集」的真实 awemeId 和后续集数。
            if (index != 0) {
                info.toMixItem(statis = info.statis(), resumeAwemeId = "", next = emptyList())
            } else {
                val detail = runCatching { collectionApi.mixDetail(info.mixId()) }.getOrNull()
                val statis = detail?.mixInfo()?.statis() ?: info.statis()
                val episodes = runCatching { collectionApi.mixAwemes(info.mixId(), 0L, 20) }
                    .getOrNull()
                    ?.flatAwemes()
                    ?.let(DataModels::filterPrimaryPlayableAwemes)
                    .orEmpty()
                val current = statis.currentEpisode().coerceAtLeast(1)
                val resume = episodes.getOrNull(current - 1)?.awemeId()
                    ?: episodes.firstOrNull()?.awemeId().orEmpty()
                val next = episodes.drop(current).take(3).mapIndexed { offset, aweme ->
                    WidgetEpisodeRef(current + offset + 1, aweme.awemeId())
                }
                info.toMixItem(statis, resume, next).copy(
                    author = episodes.firstOrNull()?.author()?.nickname().orEmpty(),
                )
            }
        }
        preloadCovers(items.map(WidgetMixItem::coverUrl))
        return items
    }

    private fun fetchLives(): List<WidgetLiveItem> {
        val api = LiveApi(AppRuntime.api ?: return emptyList())
        val response = api.followFeed(LIVE_FOLLOW_SCENE)
        val items = response.data()?.data().orEmpty()
            .mapNotNull { it?.room() }
            .filter { it.status() == 2 }
            .take(MAX_LIVES)
            .map { room ->
                WidgetLiveItem(
                    roomId = room.effectiveWebRid(),
                    author = room.owner().nickname(),
                    title = room.title(),
                    viewers = room.userCount(),
                    avatarUrl = room.owner().bestAvatar().firstUrl(),
                )
            }
        preloadCovers(items.map(WidgetLiveItem::avatarUrl))
        return items
    }

    private fun fetchHotWords(): List<WidgetHotWord> {
        val api = SearchApi(AppRuntime.api ?: return emptyList())
        val list = api.hotSearch().data()?.wordList().orEmpty()
        return list.take(MAX_WORDS).map { word ->
            // HotWord.label 是位掩码；规格层只认 1/2/3（新/热/荐），其余一律映射为无角标。
            WidgetHotWord(word.word(), word.label().takeIf { it in 1..3 } ?: 0)
        }
    }

    private fun Aweme.toVideoItem(): WidgetVideoItem = WidgetVideoItem(
        id = awemeId(),
        title = desc(),
        author = author().nickname(),
        duration = formatDuration(video().duration()),
        likes = DataModels.formatCount(statistics().diggCount()),
        coverUrl = video().originCover().firstUrl().ifEmpty { video().cover().firstUrl() },
    )

    private fun MixInfo.toMixItem(
        statis: MixStatis,
        resumeAwemeId: String,
        next: List<WidgetEpisodeRef>,
    ): WidgetMixItem = WidgetMixItem(
        id = mixId(),
        name = mixName(),
        author = "",
        episode = statis.currentEpisode(),
        total = if (statis.totalEpisode() > 0) statis.totalEpisode() else statis.updatedToEpisode(),
        resumeAwemeId = resumeAwemeId,
        nextEpisodes = next,
        coverUrl = coverUrl().firstUrl(),
    )

    private fun formatDuration(millis: Int): String {
        if (millis <= 0) return ""
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    private fun preloadCovers(urls: List<String>) {
        WidgetCoverCache.prefetch(appContext, urls)
    }

    // ---------------------------------------------------------------- 登录态

    /**
     * 设置页的 cookie_string 与 API 层的 douyin_auth.cookie_header 目前没有同步点，
     * 而历史/稍后再看/合集等接口依赖 Cookie。这里只在 API 侧为空时做一次最小补齐，
     * 不覆盖 API 层已有值。
     */
    private fun syncCookieFromSettings(cookie: String) {
        if (cookie.isBlank()) return
        val auth = appContext.getSharedPreferences("douyin_auth", Context.MODE_PRIVATE)
        if ((auth.getString("cookie_header", "") ?: "").isNotBlank()) return
        auth.edit().putString("cookie_header", cookie).apply()
    }

    // ---------------------------------------------------------------- 缓存编解码

    private fun key(widgetId: WidgetRenderSpec.WidgetId): String = "snapshot_${widgetId.name}"

    private fun save(widgetId: WidgetRenderSpec.WidgetId, data: WidgetData) {
        runCatching { prefs.edit().putString(key(widgetId), encode(widgetId, data)).apply() }
            .onFailure { Log.w(TAG, "cache write failed for $widgetId: ${it.javaClass.simpleName}") }
    }

    private fun encode(widgetId: WidgetRenderSpec.WidgetId, data: WidgetData): String {
        val root = JSONObject()
            .put("state", data.state.name)
            .put("updatedAt", data.updatedAt)
            .put("accountId", data.accountId)
        when (widgetId) {
            WidgetRenderSpec.WidgetId.CONTINUE,
            WidgetRenderSpec.WidgetId.WATCH_LATER -> {
                val array = JSONArray()
                data.videos.forEach { video ->
                    array.put(
                        JSONObject()
                            .put("id", video.id)
                            .put("title", video.title)
                            .put("author", video.author)
                            .put("duration", video.duration)
                            .put("likes", video.likes)
                            .put("cover", video.coverUrl)
                    )
                }
                root.put("videos", array)
            }

            WidgetRenderSpec.WidgetId.MIX -> {
                val array = JSONArray()
                data.mixes.forEach { mix ->
                    val nextArray = JSONArray()
                    mix.nextEpisodes.forEach { episode ->
                        nextArray.put(JSONObject().put("n", episode.number).put("id", episode.awemeId))
                    }
                    array.put(
                        JSONObject()
                            .put("id", mix.id)
                            .put("name", mix.name)
                            .put("author", mix.author)
                            .put("episode", mix.episode)
                            .put("total", mix.total)
                            .put("resume", mix.resumeAwemeId)
                            .put("next", nextArray)
                            .put("cover", mix.coverUrl)
                    )
                }
                root.put("mixes", array)
            }

            WidgetRenderSpec.WidgetId.LIVE -> {
                val array = JSONArray()
                data.lives.forEach { live ->
                    array.put(
                        JSONObject()
                            .put("roomId", live.roomId)
                            .put("author", live.author)
                            .put("title", live.title)
                            .put("viewers", live.viewers)
                            .put("avatar", live.avatarUrl)
                    )
                }
                root.put("lives", array)
            }

            WidgetRenderSpec.WidgetId.SEARCH -> {
                val array = JSONArray()
                data.hotWords.forEach { word ->
                    array.put(JSONObject().put("word", word.word).put("label", word.label))
                }
                root.put("hotWords", array)
            }

            WidgetRenderSpec.WidgetId.NOW_PLAYING -> Unit
        }
        return root.toString()
    }

    private fun decode(widgetId: WidgetRenderSpec.WidgetId, root: JSONObject): WidgetData {
        val common = WidgetData(
            state = runCatching {
                WidgetDataState.valueOf(root.optString("state", WidgetDataState.READY.name))
            }.getOrDefault(WidgetDataState.READY),
            updatedAt = root.optLong("updatedAt", 0L),
            accountId = root.optString("accountId", ""),
        )
        return when (widgetId) {
            WidgetRenderSpec.WidgetId.CONTINUE,
            WidgetRenderSpec.WidgetId.WATCH_LATER -> common.copy(
                videos = root.optJSONArray("videos").objects().map { item ->
                    WidgetVideoItem(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        author = item.optString("author"),
                        duration = item.optString("duration"),
                        likes = item.optString("likes"),
                        coverUrl = item.optString("cover"),
                    )
                },
            )

            WidgetRenderSpec.WidgetId.MIX -> common.copy(
                mixes = root.optJSONArray("mixes").objects().map { item ->
                    WidgetMixItem(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        author = item.optString("author"),
                        episode = item.optInt("episode"),
                        total = item.optInt("total"),
                        resumeAwemeId = item.optString("resume"),
                        nextEpisodes = item.optJSONArray("next").objects().map { episode ->
                            WidgetEpisodeRef(episode.optInt("n"), episode.optString("id"))
                        },
                        coverUrl = item.optString("cover"),
                    )
                },
            )

            WidgetRenderSpec.WidgetId.LIVE -> common.copy(
                lives = root.optJSONArray("lives").objects().map { item ->
                    WidgetLiveItem(
                        roomId = item.optString("roomId"),
                        author = item.optString("author"),
                        title = item.optString("title"),
                        viewers = item.optString("viewers"),
                        avatarUrl = item.optString("avatar"),
                    )
                },
            )

            WidgetRenderSpec.WidgetId.SEARCH -> common.copy(
                hotWords = root.optJSONArray("hotWords").objects().map { item ->
                    WidgetHotWord(item.optString("word"), item.optInt("label"))
                },
            )

            WidgetRenderSpec.WidgetId.NOW_PLAYING -> WidgetRuntime.playbackData()
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.let(::add)
            }
        }
    }
}
