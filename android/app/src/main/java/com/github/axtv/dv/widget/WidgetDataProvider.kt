/*
 * Android 适配层 —— widget 模块：小组件数据契约与占位实现。
 * 规格出处: refactor/widget/NOTES.md §2（字段表）/ §3（六组件显示字段）/ §6（刷新）
 * 说明: 渲染层只依赖本文件的接口，不依赖 refactor/api 或 FeedDataSource。
 *       接入真实数据源时替换 WidgetDataProviderHolder.provider 即可，渲染代码不动。
 */
package com.github.axtv.dv.widget

import axtv.widget.WidgetRenderSpec

/** 快照状态（对应核心层 WidgetLoadState）。 */
enum class WidgetDataState { LOADING, READY, ERROR }

/** 视频条目：Note §2 WidgetVideo 字段（id/title/author/duration/likes）。 */
data class WidgetVideoItem(
    val id: String,
    val title: String,
    val author: String,
    val duration: String = "",
    val likes: String = "",
    val coverUrl: String = "",
)

/** 合集选集（Note §2 WidgetEpisode: number + awemeId）。 */
data class WidgetEpisodeRef(val number: Int, val awemeId: String)

/** 合集条目：Note §2 WidgetMixItem 字段。 */
data class WidgetMixItem(
    val id: String,
    val name: String,
    val author: String,
    val episode: Int,
    val total: Int,
    val resumeAwemeId: String,
    val nextEpisodes: List<WidgetEpisodeRef> = emptyList(),
    val coverUrl: String = "",
)

/** 直播条目：Note §2 WidgetLiveItem 字段。 */
data class WidgetLiveItem(
    val roomId: String,
    val author: String,
    val title: String,
    val viewers: String,
    val avatarUrl: String = "",
)

/** 热搜词：Note §2 WidgetHotWord（label 1/2/3 → 新/热/荐）。 */
data class WidgetHotWord(val word: String, val label: Int = 0)

/** 正在播放：Note §2 WidgetPlayback（纯内存，不持久化）。 */
data class WidgetPlaybackItem(
    val id: String,
    val title: String,
    val author: String,
    val isPlaying: Boolean,
    val isLive: Boolean,
    val source: String,
    val coverUrl: String = "",
)

/** 一次 widget 渲染所需的全部字段；未使用的集合为空。 */
data class WidgetData(
    val state: WidgetDataState = WidgetDataState.READY,
    val videos: List<WidgetVideoItem> = emptyList(),
    val mixes: List<WidgetMixItem> = emptyList(),
    val lives: List<WidgetLiveItem> = emptyList(),
    val hotWords: List<WidgetHotWord> = emptyList(),
    val playback: WidgetPlaybackItem? = null,
    val updatedAt: Long = 0L,
    /** 这份数据抓取时登录的账号 id；用于切换账号后触发 LOADING 而不是展示他人数据。 */
    val accountId: String = "",
)

/** 小组件数据源契约。返回值只用于渲染，不做持久化。 */
interface WidgetDataProvider {
    fun snapshot(widgetId: WidgetRenderSpec.WidgetId): WidgetData

    /** 请求刷新数据；占位实现为空操作，接入真实数据源时在此发起异步拉取。 */
    fun refresh(widgetId: WidgetRenderSpec.WidgetId) = Unit
}

/**
 * 占位数据源：静态样例数据，覆盖内容态 / 空态 / 失败态 / 加载态四种可验证场景。
 * 调试时把 [mode] 换成 EMPTY / ERROR / LOADING 即可看到对应状态页。
 */
class PlaceholderWidgetDataProvider(
    private val mode: Mode = Mode.CONTENT,
) : WidgetDataProvider {

    enum class Mode { CONTENT, EMPTY, ERROR, LOADING }

    override fun snapshot(widgetId: WidgetRenderSpec.WidgetId): WidgetData =
        when (mode) {
            Mode.LOADING -> WidgetData(state = WidgetDataState.LOADING)
            Mode.ERROR -> WidgetData(state = WidgetDataState.ERROR)
            Mode.EMPTY -> emptyOf(widgetId)
            Mode.CONTENT -> contentOf(widgetId)
        }

    private fun emptyOf(widgetId: WidgetRenderSpec.WidgetId): WidgetData =
        WidgetData(
            state = WidgetDataState.READY,
            // NowPlaying 的空态是 playback == null（独立 IDLE 分支，不是 EMPTY）。
            playback = null,
        )

    private fun contentOf(widgetId: WidgetRenderSpec.WidgetId): WidgetData =
        when (widgetId) {
            WidgetRenderSpec.WidgetId.CONTINUE -> WidgetData(videos = videos())
            WidgetRenderSpec.WidgetId.WATCH_LATER -> WidgetData(videos = videos().drop(1))
            WidgetRenderSpec.WidgetId.MIX -> WidgetData(mixes = mixes())
            WidgetRenderSpec.WidgetId.LIVE -> WidgetData(
                lives = lives(),
                updatedAt = System.currentTimeMillis(),
            )
            WidgetRenderSpec.WidgetId.SEARCH -> WidgetData(hotWords = hotWords())
            WidgetRenderSpec.WidgetId.NOW_PLAYING -> WidgetData(
                playback = WidgetPlaybackItem(
                    id = "7300000000000000001",
                    title = "城市夜行 · 第一集",
                    author = "AxTV 示例作者",
                    isPlaying = true,
                    isLive = false,
                    source = "推荐",
                )
            )
        }

    private fun videos(): List<WidgetVideoItem> = listOf(
        WidgetVideoItem("7300000000000000001", "城市夜行 · 第一集", "AxTV 示例作者", "03:12", "1.2 万"),
        WidgetVideoItem("7300000000000000002", "周末去山里走一走", "山野记录", "05:48", "8,532"),
        WidgetVideoItem("7300000000000000003", "三分钟学会一道家常菜", "小厨房", "02:35", "2.4 万"),
        WidgetVideoItem("7300000000000000004", "旧相机里的夏天", "胶片日记", "01:56", "6,120"),
        WidgetVideoItem("7300000000000000005", "深夜电台：城市声音", "午夜频道", "12:04", "3,208"),
    )

    private fun mixes(): List<WidgetMixItem> = listOf(
        WidgetMixItem(
            id = "7400000000000000001",
            name = "城市夜行",
            author = "AxTV 示例作者",
            episode = 3,
            total = 12,
            resumeAwemeId = "7300000000000000001",
            nextEpisodes = listOf(
                WidgetEpisodeRef(4, "7300000000000000011"),
                WidgetEpisodeRef(5, "7300000000000000012"),
                WidgetEpisodeRef(6, "7300000000000000013"),
            ),
        ),
        WidgetMixItem(
            id = "7400000000000000002",
            name = "周末去山里",
            author = "山野记录",
            episode = 1,
            total = 8,
            resumeAwemeId = "7300000000000000002",
        ),
        WidgetMixItem(
            id = "7400000000000000003",
            name = "三分钟家常菜",
            author = "小厨房",
            episode = 2,
            total = 0,
            resumeAwemeId = "7300000000000000003",
        ),
    )

    private fun lives(): List<WidgetLiveItem> = listOf(
        WidgetLiveItem("7500000000000000001", "山野记录", "夜爬看日出", "1.2 万"),
        WidgetLiveItem("7500000000000000002", "小厨房", "今天的晚饭", "3,204"),
        WidgetLiveItem("7500000000000000003", "午夜频道", "深夜连麦", "856"),
    )

    private fun hotWords(): List<WidgetHotWord> = listOf(
        WidgetHotWord("城市夜行", 2),
        WidgetHotWord("周末去哪儿", 1),
        WidgetHotWord("家常菜教程", 3),
        WidgetHotWord("胶片摄影", 2),
        WidgetHotWord("深夜电台", 0),
        WidgetHotWord("日出机位", 0),
    )
}

/** 数据源注册点：小组件入口首次调用时把占位实现换成真实数据源。 */
object WidgetDataProviderHolder {
    @Volatile
    var provider: WidgetDataProvider = PlaceholderWidgetDataProvider()

    /** 幂等：只在仍是占位实现时替换，显式注入的调试数据源不会被覆盖。 */
    fun installRealData(context: android.content.Context) {
        if (provider is PlaceholderWidgetDataProvider) {
            provider = WidgetRealDataProvider(context.applicationContext)
        }
    }
}
