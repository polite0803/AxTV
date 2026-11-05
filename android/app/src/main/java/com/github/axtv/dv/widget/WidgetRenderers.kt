/*
 * Android 适配层 —— widget 模块：RemoteViews 渲染层（6 个小组件）。
 * 规格出处: refactor/widget/NOTES.md §3（逐组件分支/字段/点击）、§5（路由与媒体键）
 * 分支判定直接复用核心层 WidgetRenderSpec.resolve（与反编译源码逐行对应的可执行模型），
 * 本文件只负责「像素怎么画」：按 Screen/Variant 选布局，绑定字段与点击 PendingIntent。
 * 空态/失败态/加载态/锁定态/引导态统一走 widget_status.xml，不会白屏。
 */
package com.github.axtv.dv.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import axtv.widget.WidgetActions
import axtv.widget.WidgetLoadState
import axtv.widget.WidgetRenderSpec
import axtv.widget.WidgetRoutes
import axtv.widget.WidgetMediaActionReceiver as CoreMediaActions
import com.github.axtv.dv.MainActivity
import com.github.axtv.dv.R
import com.github.axtv.dv.adapter.AppRuntime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 小组件当前实际尺寸（dp），来自 AppWidgetManager 的 options。 */
internal data class WidgetDpSize(val width: Int, val height: Int)

internal object WidgetRenderers {

    // ---------------------------------------------------------------- 入口
    /** 刷新全部或单个小组件；provider / Worker / 数据变化都走这里。 */
    fun refresh(context: Context, widgetId: WidgetRenderSpec.WidgetId? = null) {
        if (widgetId != null) {
            updateWidget(context, widgetId)
            return
        }
        WidgetRenderSpec.WidgetId.values().forEach { updateWidget(context, it) }
    }

    private fun updateWidget(context: Context, widgetId: WidgetRenderSpec.WidgetId) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, providerClassFor(widgetId)))
        updateWidgets(context, widgetId, ids)
    }

    /** onUpdate 传入的 ids 直接使用，避免再查一次。 */
    fun updateWidgets(context: Context, widgetId: WidgetRenderSpec.WidgetId, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        appWidgetIds.forEach { appWidgetId ->
            manager.updateAppWidget(appWidgetId, build(context, widgetId, appWidgetId))
        }
    }

    // ---------------------------------------------------------------- 构建
    private fun build(context: Context, widgetId: WidgetRenderSpec.WidgetId, appWidgetId: Int): RemoteViews {
        WidgetDataProviderHolder.installRealData(context)
        val data = WidgetDataProviderHolder.provider.snapshot(widgetId)
        val size = widgetSize(context, widgetId, appWidgetId)
        val resolution = WidgetRenderSpec.resolve(widgetId, viewInput(widgetId, data, size))
        return when (resolution.screen()) {
            WidgetRenderSpec.Screen.IDLE -> nowIdle(context)
            WidgetRenderSpec.Screen.CONTENT -> content(context, widgetId, resolution.variant(), data)
            else -> status(context, widgetId, resolution.status())
        }
    }

    private fun widgetSize(
        context: Context,
        widgetId: WidgetRenderSpec.WidgetId,
        appWidgetId: Int,
    ): WidgetDpSize {
        val options = AppWidgetManager.getInstance(context)
            .getAppWidgetOptions(appWidgetId)
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val def = defaultSize(widgetId)
        return WidgetDpSize(if (w > 0) w else def.width, if (h > 0) h else def.height)
    }

    /** 首次添加、launcher 未上报 options 时的兜底尺寸（四列宽 736dp，与 info xml 对齐）。 */
    private fun defaultSize(widgetId: WidgetRenderSpec.WidgetId): WidgetDpSize =
        when (widgetId) {
            WidgetRenderSpec.WidgetId.CONTINUE -> WidgetDpSize(736, 192)
            WidgetRenderSpec.WidgetId.WATCH_LATER -> WidgetDpSize(320, 192)
            WidgetRenderSpec.WidgetId.MIX -> WidgetDpSize(320, 192)
            WidgetRenderSpec.WidgetId.LIVE -> WidgetDpSize(320, 92)
            WidgetRenderSpec.WidgetId.SEARCH -> WidgetDpSize(320, 64)
            WidgetRenderSpec.WidgetId.NOW_PLAYING -> WidgetDpSize(320, 192)
        }

    private fun viewInput(
        widgetId: WidgetRenderSpec.WidgetId,
        data: WidgetData,
        size: WidgetDpSize,
    ): WidgetRenderSpec.ViewInput {
        val count = when (widgetId) {
            WidgetRenderSpec.WidgetId.CONTINUE,
            WidgetRenderSpec.WidgetId.WATCH_LATER -> data.videos.size
            WidgetRenderSpec.WidgetId.MIX -> data.mixes.size
            WidgetRenderSpec.WidgetId.LIVE -> data.lives.size
            WidgetRenderSpec.WidgetId.SEARCH -> data.hotWords.size
            WidgetRenderSpec.WidgetId.NOW_PLAYING -> if (data.playback != null) 1 else 0
        }
        val firstNull = count == 0
        val state = when (data.state) {
            WidgetDataState.LOADING -> WidgetLoadState.LOADING
            WidgetDataState.ERROR -> WidgetLoadState.ERROR
            WidgetDataState.READY -> WidgetLoadState.READY
        }
        // 锁 / 登录 / 账号匹配：来自 Settings 与账号模块；取不到时保持既有默认（不锁、已登录、匹配）。
        val settings = AppRuntime.settings
        val loggedIn = settings?.isLoggedIn ?: true
        val locked = settings?.currentAccount()?.hasPin() == true
        val currentAccountId = if (loggedIn) settings?.currentAccountId().orEmpty() else ""
        val accountMatches = currentAccountId == data.accountId
        return WidgetRenderSpec.ViewInput(
            locked,                      // locked：当前配置设了 PIN
            loggedIn,                    // historyEnabled：无独立开关，按登录态映射
            accountMatches,              // accountMatches：缓存数据归属当前账号
            state,
            count,
            firstNull,
            data.playback == null,
            data.playback?.isPlaying == true,
            size.width.toFloat(),
            size.height.toFloat(),
            data.updatedAt,
            data.playback?.source ?: "",
        )
    }

    // ---------------------------------------------------------------- 状态页（锁定/引导/加载/错误/空）
    private fun status(
        context: Context,
        widgetId: WidgetRenderSpec.WidgetId,
        status: WidgetRenderSpec.StatusSpec?,
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_status)
        if (status == null) {
            rv.setViewVisibility(R.id.widget_status_icon, View.GONE)
            return rv
        }
        rv.setImageViewResource(R.id.widget_status_icon, iconRes(status.icon()))
        rv.setTextViewText(R.id.widget_status_title, status.title())
        rv.setTextViewText(R.id.widget_status_subtitle, status.subtitle())

        val buttonText = status.buttonText()
        if (status.showButton() && buttonText != null) {
            rv.setViewVisibility(R.id.widget_status_action, View.VISIBLE)
            rv.setTextViewText(R.id.widget_status_action, buttonText)
            val pending = when (status.buttonRoute()) {
                WidgetRenderSpec.Route.REFRESH -> refreshIntent(context, widgetId)
                else -> statusRoute(status.buttonRoute())?.let { routeIntent(context, it) }
            }
            if (pending != null) {
                rv.setOnClickPendingIntent(R.id.widget_status_action, pending)
                rv.setOnClickPendingIntent(R.id.widget_root, pending)
            }
        } else {
            rv.setViewVisibility(R.id.widget_status_action, View.GONE)
        }
        return rv
    }

    private fun statusRoute(route: WidgetRenderSpec.Route): String? =
        when (route) {
            WidgetRenderSpec.Route.HOME -> WidgetRoutes.home()
            WidgetRenderSpec.Route.LIVE -> WidgetRoutes.live()
            WidgetRenderSpec.Route.SEARCH -> WidgetRoutes.search()
            WidgetRenderSpec.Route.LOGIN -> WidgetRoutes.login()
            else -> null
        }

    // ---------------------------------------------------------------- 内容态
    private fun content(
        context: Context,
        widgetId: WidgetRenderSpec.WidgetId,
        variant: WidgetRenderSpec.Variant,
        data: WidgetData,
    ): RemoteViews = when (widgetId) {
        // 继续看：Small(宽<300dp) 运行时分支已移除——四列宽 736dp 恒 ≥300dp，Small 不可达。
        // 统一走 Large；widget_continue.xml 仅作 initialLayout 首帧占位，不参与宽度判定。
        WidgetRenderSpec.WidgetId.CONTINUE -> continueLarge(context, data)
        WidgetRenderSpec.WidgetId.WATCH_LATER ->
            if (variant == WidgetRenderSpec.Variant.COMPACT) {
                laterCompact(context, data)
            } else {
                laterExpanded(context, data)
            }
        WidgetRenderSpec.WidgetId.MIX ->
            if (variant == WidgetRenderSpec.Variant.COMPACT) {
                mixCompact(context, data)
            } else {
                mixExpanded(context, data)
            }
        WidgetRenderSpec.WidgetId.LIVE ->
            if (variant == WidgetRenderSpec.Variant.COMPACT) {
                liveCompact(context, data)
            } else {
                liveExpanded(context, data)
            }
        WidgetRenderSpec.WidgetId.SEARCH ->
            if (variant == WidgetRenderSpec.Variant.COMPACT) {
                searchCompact(context, data)
            } else {
                searchExpanded(context, data)
            }
        WidgetRenderSpec.WidgetId.NOW_PLAYING -> nowContent(context, data)
    }

    // ---- 继续看：仅 Large（规格的 Small 分支在装配层被收口，见 content() 注释）
    private fun continueLarge(context: Context, data: WidgetData): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_continue_large)
        val first = data.videos.firstOrNull() ?: return rv
        rv.setTextViewText(R.id.continue_title, first.title)
        rv.setTextViewText(R.id.continue_author, first.author)
        rv.setTextViewText(R.id.continue_duration, first.duration)
        rv.setTextViewText(R.id.continue_label, WidgetRenderSpec.Strings.RECENT)
        bindCover(context, rv, R.id.continue_cover, first.coverUrl)
        rv.setOnClickPendingIntent(R.id.continue_cover, routeIntent(context, WidgetRoutes.playHistory(first.id)))

        val earlier = data.videos.drop(1).take(3)
        val slots = intArrayOf(R.id.earlier_cover_1, R.id.earlier_cover_2, R.id.earlier_cover_3)
        slots.forEachIndexed { index, slotId ->
            val video = earlier.getOrNull(index)
            if (video == null) {
                rv.setViewVisibility(slotId, View.GONE)
            } else {
                rv.setViewVisibility(slotId, View.VISIBLE)
                bindCover(context, rv, slotId, video.coverUrl)
                rv.setOnClickPendingIntent(slotId, routeIntent(context, WidgetRoutes.playHistory(video.id)))
            }
        }
        val showEarlier = earlier.isNotEmpty()
        rv.setViewVisibility(R.id.continue_earlier_label, if (showEarlier) View.VISIBLE else View.GONE)
        return rv
    }

    // ---- 稍后再看：Compact(高<250) / Expanded
    private fun laterCompact(context: Context, data: WidgetData): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_later)
        rv.setTextViewText(
            R.id.later_count,
            WidgetRenderSpec.countText(data.videos.size, WidgetRenderSpec.Strings.COUNT_SUFFIX),
        )
        rv.setOnClickPendingIntent(R.id.widget_root, routeIntent(context, WidgetRoutes.later()))
        val covers = intArrayOf(R.id.later_cover_1, R.id.later_cover_2, R.id.later_cover_3, R.id.later_cover_4)
        covers.forEachIndexed { index, coverId ->
            val video = data.videos.getOrNull(index)
            if (video == null) {
                rv.setViewVisibility(coverId, View.GONE)
            } else {
                rv.setViewVisibility(coverId, View.VISIBLE)
                bindCover(context, rv, coverId, video.coverUrl)
                rv.setOnClickPendingIntent(coverId, routeIntent(context, WidgetRoutes.playLater(video.id)))
            }
        }
        return rv
    }

    private fun laterExpanded(context: Context, data: WidgetData): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_later_expanded)
        rv.setTextViewText(
            R.id.later_count,
            WidgetRenderSpec.countText(data.videos.size, WidgetRenderSpec.Strings.COUNT_SUFFIX),
        )
        rv.setOnClickPendingIntent(R.id.widget_root, routeIntent(context, WidgetRoutes.later()))
        val items = data.videos.take(3)
        val rows = intArrayOf(R.id.later_row_1, R.id.later_row_2, R.id.later_row_3)
        val titles = intArrayOf(R.id.later_title_1, R.id.later_title_2, R.id.later_title_3)
        val metas = intArrayOf(R.id.later_meta_1, R.id.later_meta_2, R.id.later_meta_3)
        rows.forEachIndexed { index, rowId ->
            val video = items.getOrNull(index)
            if (video == null) {
                rv.setViewVisibility(rowId, View.GONE)
            } else {
                rv.setViewVisibility(rowId, View.VISIBLE)
                rv.setTextViewText(titles[index], video.title)
                rv.setTextViewText(metas[index], video.author)
                rv.setOnClickPendingIntent(rowId, routeIntent(context, WidgetRoutes.playLater(video.id)))
            }
        }
        return rv
    }

    // ---- 合集追更：Compact(高<250) / Expanded
    private fun mixCompact(context: Context, data: WidgetData): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_mix)
        val mix = data.mixes.firstOrNull() ?: return rv
        rv.setTextViewText(R.id.mix_name, mix.name)
        rv.setTextViewText(R.id.mix_sub, WidgetRenderSpec.mixSubtitle(mix.author, mix.total))
        rv.setTextViewText(R.id.mix_progress, WidgetRenderSpec.mixProgressText(mix.episode, mix.total))
        bindCover(context, rv, R.id.mix_cover, mix.coverUrl)
        rv.setOnClickPendingIntent(R.id.mix_cover, routeIntent(context, WidgetRoutes.mixDetail(mix.id)))

        val continueText = WidgetRenderSpec.Strings.CONTINUE_EPISODE + mix.episode +
            WidgetRenderSpec.Strings.EPISODE_SUFFIX
        rv.setTextViewText(R.id.mix_continue, continueText)
        // 列表接口没给 awemeId 时不能拼出无效的 playMix 路由，退到合集详情页。
        val continueTarget = if (mix.resumeAwemeId.isNotBlank()) {
            WidgetRoutes.playMix(mix.id, mix.resumeAwemeId)
        } else {
            WidgetRoutes.mixDetail(mix.id)
        }
        rv.setOnClickPendingIntent(R.id.mix_continue, routeIntent(context, continueTarget))

        val next = mix.nextEpisodes.take(WidgetRenderSpec.MAX_NEXT_EPISODES)
        val buttons = intArrayOf(R.id.mix_ep_1, R.id.mix_ep_2, R.id.mix_ep_3)
        buttons.forEachIndexed { index, buttonId ->
            val episode = next.getOrNull(index)
            if (episode == null) {
                rv.setViewVisibility(buttonId, View.GONE)
            } else {
                rv.setViewVisibility(buttonId, View.VISIBLE)
                rv.setTextViewText(buttonId, "${episode.number} 集")
                rv.setOnClickPendingIntent(
                    buttonId,
                    routeIntent(context, WidgetRoutes.playMix(mix.id, episode.awemeId)),
                )
            }
        }
        return rv
    }

    private fun mixExpanded(context: Context, data: WidgetData): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_mix_expanded)
        val items = data.mixes.take(WidgetRenderSpec.MAX_MIX_ITEMS)
        val rows = intArrayOf(R.id.mix_row_1, R.id.mix_row_2, R.id.mix_row_3)
        val names = intArrayOf(R.id.mix_name_1, R.id.mix_name_2, R.id.mix_name_3)
        val progress = intArrayOf(R.id.mix_progress_1, R.id.mix_progress_2, R.id.mix_progress_3)
        val plays = intArrayOf(R.id.mix_play_1, R.id.mix_play_2, R.id.mix_play_3)
        rows.forEachIndexed { index, rowId ->
            val mix = items.getOrNull(index)
            if (mix == null) {
                rv.setViewVisibility(rowId, View.GONE)
            } else {
                rv.setViewVisibility(rowId, View.VISIBLE)
                rv.setTextViewText(names[index], mix.name)
                rv.setTextViewText(
                    progress[index],
                    WidgetRenderSpec.mixExpandedProgressText(mix.episode, mix.total),
                )
                rv.setOnClickPendingIntent(rowId, routeIntent(context, WidgetRoutes.mixDetail(mix.id)))
                val playTarget = if (mix.resumeAwemeId.isNotBlank()) {
                    WidgetRoutes.playMix(mix.id, mix.resumeAwemeId)
                } else {
                    WidgetRoutes.mixDetail(mix.id)
                }
                rv.setOnClickPendingIntent(plays[index], routeIntent(context, playTarget))
            }
        }
        return rv
    }

    // ---- 关注的直播：Compact(高<130) / Expanded
    private fun liveCompact(context: Context, data: WidgetData): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_live)
        rv.setTextViewText(
            R.id.live_badge,
            WidgetRenderSpec.countText(data.lives.size, WidgetRenderSpec.Strings.LIVE_COUNT_SUFFIX),
        )
        rv.setOnClickPendingIntent(R.id.live_badge, routeIntent(context, WidgetRoutes.live()))
        val slots = data.lives.take(3)
        val avatars = intArrayOf(R.id.live_avatar_1, R.id.live_avatar_2, R.id.live_avatar_3)
        val authors = intArrayOf(R.id.live_author_1, R.id.live_author_2, R.id.live_author_3)
        val columns = intArrayOf(R.id.live_col_1, R.id.live_col_2, R.id.live_col_3)
        columns.forEachIndexed { index, colId ->
            val live = slots.getOrNull(index)
            if (live == null) {
                rv.setViewVisibility(colId, View.GONE)
            } else {
                rv.setViewVisibility(colId, View.VISIBLE)
                rv.setTextViewText(authors[index], live.author)
                bindCover(context, rv, avatars[index], live.avatarUrl)
                val click = routeIntent(context, WidgetRoutes.liveRoom(live.roomId))
                rv.setOnClickPendingIntent(colId, click)
                rv.setOnClickPendingIntent(avatars[index], click)
            }
        }
        return rv
    }

    private fun liveExpanded(context: Context, data: WidgetData): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_live_expanded)
        val time = widgetTime(data.updatedAt)
        rv.setTextViewText(
            R.id.live_time,
            if (time.isEmpty()) "" else time + WidgetRenderSpec.Strings.UPDATED_SUFFIX,
        )
        val items = data.lives.take(3)
        val rows = intArrayOf(R.id.live_row_1, R.id.live_row_2, R.id.live_row_3)
        val authors = intArrayOf(R.id.live_author_1, R.id.live_author_2, R.id.live_author_3)
        val titles = intArrayOf(R.id.live_title_1, R.id.live_title_2, R.id.live_title_3)
        val viewers = intArrayOf(R.id.live_viewers_1, R.id.live_viewers_2, R.id.live_viewers_3)
        rows.forEachIndexed { index, rowId ->
            val live = items.getOrNull(index)
            if (live == null) {
                rv.setViewVisibility(rowId, View.GONE)
            } else {
                rv.setViewVisibility(rowId, View.VISIBLE)
                rv.setTextViewText(authors[index], live.author)
                rv.setTextViewText(titles[index], live.title)
                rv.setTextViewText(viewers[index], live.viewers + WidgetRenderSpec.Strings.WATCHING)
                rv.setOnClickPendingIntent(rowId, routeIntent(context, WidgetRoutes.liveRoom(live.roomId)))
            }
        }
        return rv
    }

    // ---- 搜索热搜：Compact(高<130，先于空态) / Expanded
    private fun searchCompact(context: Context, data: WidgetData): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_search)
        val word = data.hotWords.firstOrNull()?.word ?: WidgetRenderSpec.Strings.SEARCH_HINT
        rv.setTextViewText(R.id.search_word, word)
        val click = routeIntent(context, WidgetRoutes.search())
        rv.setOnClickPendingIntent(R.id.search_text, click)
        rv.setOnClickPendingIntent(R.id.search_open, click)
        rv.setOnClickPendingIntent(R.id.widget_root, click)
        return rv
    }

    private fun searchExpanded(context: Context, data: WidgetData): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_search_expanded)
        rv.setOnClickPendingIntent(R.id.search_bar, routeIntent(context, WidgetRoutes.search()))
        val words = data.hotWords.take(WidgetRenderSpec.MAX_HOT_WORDS)
        val rows = intArrayOf(
            R.id.hot_row_1, R.id.hot_row_2, R.id.hot_row_3,
            R.id.hot_row_4, R.id.hot_row_5, R.id.hot_row_6,
        )
        val texts = intArrayOf(
            R.id.hot_word_1, R.id.hot_word_2, R.id.hot_word_3,
            R.id.hot_word_4, R.id.hot_word_5, R.id.hot_word_6,
        )
        val badges = intArrayOf(
            R.id.hot_badge_1, R.id.hot_badge_2, R.id.hot_badge_3,
            R.id.hot_badge_4, R.id.hot_badge_5, R.id.hot_badge_6,
        )
        rows.forEachIndexed { index, rowId ->
            val word = words.getOrNull(index)
            if (word == null) {
                rv.setViewVisibility(rowId, View.GONE)
            } else {
                rv.setViewVisibility(rowId, View.VISIBLE)
                rv.setTextViewText(texts[index], word.word)
                val badge = WidgetRenderSpec.hotWordBadge(word.label)
                if (badge == null) {
                    rv.setViewVisibility(badges[index], View.GONE)
                } else {
                    rv.setViewVisibility(badges[index], View.VISIBLE)
                    rv.setTextViewText(badges[index], badge.text())
                }
                rv.setOnClickPendingIntent(
                    rowId,
                    routeIntent(context, WidgetRoutes.searchResult(word.word)),
                )
            }
        }
        return rv
    }

    // ---- 正在播放：Idle / Content
    private fun nowIdle(context: Context): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_now_idle)
        rv.setOnClickPendingIntent(
            R.id.now_idle_continue,
            routeIntent(context, WidgetRoutes.history()),
        )
        rv.setOnClickPendingIntent(
            R.id.now_idle_home,
            routeIntent(context, WidgetRoutes.home()),
        )
        return rv
    }

    private fun nowContent(context: Context, data: WidgetData): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_now)
        val playback = data.playback ?: return rv
        rv.setTextViewText(R.id.now_title, playback.title)
        rv.setTextViewText(R.id.now_author, playback.author)
        rv.setTextViewText(
            R.id.now_status,
            WidgetRenderSpec.nowPlayingStatusText(playback.isPlaying, playback.source),
        )
        rv.setTextViewText(
            R.id.now_toggle_label,
            WidgetRenderSpec.nowPlayingToggleText(playback.isPlaying),
        )
        rv.setImageViewResource(
            R.id.now_toggle_icon,
            if (playback.isPlaying) R.drawable.widget_ic_pause else R.drawable.widget_ic_play,
        )
        bindCover(context, rv, R.id.now_cover, playback.coverUrl)
        rv.setOnClickPendingIntent(R.id.now_cover, routeIntent(context, WidgetRoutes.home()))
        rv.setOnClickPendingIntent(
            R.id.now_prev,
            mediaIntent(context, WidgetActions.KEYCODE_MEDIA_PREVIOUS),
        )
        rv.setOnClickPendingIntent(
            R.id.now_toggle,
            mediaIntent(context, WidgetActions.KEYCODE_MEDIA_PLAY_PAUSE),
        )
        rv.setOnClickPendingIntent(
            R.id.now_next,
            mediaIntent(context, WidgetActions.KEYCODE_MEDIA_NEXT),
        )
        return rv
    }

    // ---------------------------------------------------------------- 封面
    /**
     * 本进程解码缩略位图后用 setImageViewBitmap 交付桌面：位图随 RemoteViews 传输，
     * 不依赖 launcher 能否打开 content://。文件缺失/下载失败时不设置，
     * 布局里原有的纯色占位（widget_cover / widget_avatar_icon）继续生效。
     */
    private fun bindCover(context: Context, rv: RemoteViews, viewId: Int, url: String) {
        if (url.isBlank()) return
        val bitmap = WidgetCoverCache.bitmapFor(context, url) ?: return
        rv.setImageViewBitmap(viewId, bitmap)
    }

    // ---------------------------------------------------------------- 尺寸 / 时间
    private fun widgetTime(millis: Long): String {
        if (millis <= 0L) return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }

    // ---------------------------------------------------------------- 图标映射
    private fun iconRes(iconSpec: String?): Int =
        when (iconSpec?.substringAfterLast('.')) {
            "widget_ic_lock" -> R.drawable.widget_ic_lock
            "widget_ic_login" -> R.drawable.widget_ic_login
            "widget_ic_history" -> R.drawable.widget_ic_history
            "widget_ic_error" -> R.drawable.widget_ic_error
            "widget_ic_live" -> R.drawable.widget_ic_live
            "widget_ic_search" -> R.drawable.widget_ic_search
            else -> R.drawable.widget_ic_inbox
        }

    // ---------------------------------------------------------------- PendingIntent
    /** 路由点击 → 启动 MainActivity 并带 EXTRA_ROUTE（与原版 Intent flags 872415232 一致）。 */
    private fun routeIntent(context: Context, route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(WidgetRoutes.EXTRA_ROUTE, route)
            // 与原版 872415232 等价：NEW_TASK | SINGLE_TOP | CLEAR_TOP
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        return PendingIntent.getActivity(
            context,
            route.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 媒体键点击 → 广播到 WidgetMediaActionReceiver（keyCode 85/87/88）。 */
    private fun mediaIntent(context: Context, keyCode: Int): PendingIntent {
        val intent = Intent(context, WidgetMediaActionReceiver::class.java)
            .setAction(CoreMediaActions.ACTION_MEDIA)
            .putExtra(CoreMediaActions.EXTRA_KEY_CODE, keyCode)
        return PendingIntent.getBroadcast(
            context,
            0x1000 + keyCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 失败态「重试」→ 广播回 provider，先发起一次立即刷新再重绘。 */
    private fun refreshIntent(context: Context, widgetId: WidgetRenderSpec.WidgetId): PendingIntent {
        val intent = Intent(context, providerClassFor(widgetId))
            .setAction(BaseWidgetProvider.ACTION_REFRESH)
        return PendingIntent.getBroadcast(
            context,
            0x2000 + widgetId.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
