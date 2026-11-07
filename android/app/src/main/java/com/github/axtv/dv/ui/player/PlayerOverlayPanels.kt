/*
 * 播放器浮层的头部/底部/菜单面板与时钟工具（从 PlayerOverlayScreen.kt 拆出）。
 * 拆分原因: PlayerOverlayScreen.kt 超 800 行；这些组件只被主页面调用，无独立状态。
 */
package com.github.axtv.dv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import axtv.player.PlaybackError
import axtv.player.PlayerState
import axtv.settings.SettingsTypes
import axtv.ui.PlayerMenuSpec
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.danmaku.DanmakuComment
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.collection.CollectionEpisode
import com.github.axtv.dv.ui.focusRing
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** 播放时钟取时间隔（整点 / 半点模式最多滞后一分钟）。 */
internal const val CLOCK_TICK_MS = 15_000L

@Composable
internal fun PlayerHeader(
    session: PlayerSession,
    clockText: String?,
    authorName: String = "",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 标题吃掉剩余宽度并省略号收尾：抖音标题普遍很长，不加权重要把条数/时钟/进度挤出屏幕。
        Text(
            text = session.currentTitle.ifEmpty { "播放器浮层" },
            color = AxTvColors.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )
        if (authorName.isNotBlank()) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = "@$authorName",
                color = AxTvColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 13.sp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "第 ${session.playlistIndex + 1}/${session.itemCount} 条",
            color = AxTvColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 13.sp),
        )
        Spacer(Modifier.width(12.dp))
        if (clockText != null) {
            Text(
                text = clockText,
                color = AxTvColors.TextSecondary,
                maxLines = 1,
                style = TextStyle(fontSize = 13.sp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = "${formatTime(session.positionMs)} / ${formatTime(session.durationMs)}",
            color = AxTvColors.TextSecondary,
            maxLines = 1,
            style = TextStyle(fontSize = 13.sp),
        )
    }
}

@Composable
internal fun PlayerHint(text: String) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text = text, color = Color.White.copy(alpha = 0.85f), style = TextStyle(fontSize = 16.sp))
    }
}

@Composable
internal fun PlayerSkipNotice(text: String) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "⏭",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 18.sp),
        )
        Text(
            text = text,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 16.sp),
        )
    }
}

@Composable
internal fun PlayerErrorCard(
    error: PlaybackError,
    retryRequester: FocusRequester,
    hasNext: Boolean,
    onRetry: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .background(AxTvColors.SurfaceHigh, RoundedCornerShape(12.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "这一条暂时播不了",
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = friendlyErrorText(error),
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 14.sp),
        )
        Text(
            text = if (hasNext) {
                "别担心，可以先试试重试这一条，或者直接跳到下一条继续看。"
            } else {
                "这已经是最后一条了。可以先试试重试，或者返回挑点别的看。"
            },
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
        // 播放列表为空时重试也没有可重开的条目，不渲染按了没反应的按钮。
        val canRetry = error.message() != "播放列表为空"
        if (canRetry || hasNext) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canRetry) {
                    OverlayAction(label = "再试一次", modifier = Modifier.focusRequester(retryRequester), onClick = onRetry)
                }
                // 队尾没有下一条时不渲染死按钮（按了 playNext 只会返回 false）。
                if (hasNext) {
                    OverlayAction(label = "播放下一条", onClick = onNext)
                }
            }
        }
    }
}

/**
 * 错误卡片的人话文案：按错误类别给一句解释，不再向用户裸抛底层错误信息与错误码
 * （原始信息仍会进日志，排障不受影响）。
 */
internal fun friendlyErrorText(error: PlaybackError): String = when {
    error.message() == "播放列表为空" -> "播放列表是空的，先去挑几集再回来吧。"
    error.decoderFailure() -> "这条视频的编码格式设备解不动，可能是源视频用了较新的编码。"
    error.message().contains("网络", ignoreCase = true) ||
        error.message().contains("timeout", ignoreCase = true) ||
        error.message().contains("connect", ignoreCase = true) ->
        "网络似乎不太顺畅，没能把这条视频加载出来。可以检查一下网络，或者稍后再试。"
    error.message().contains("404", ignoreCase = true) ||
        error.message().contains("not found", ignoreCase = true) ->
        "这条视频的地址好像失效了，可能已经被删除或下架。"
    else -> "这条视频出了点小问题，暂时没能播出来。"
}

@Composable
internal fun PlayerBottomBar(
    session: PlayerSession,
    bottomBarFocusRequester: FocusRequester,
    chromeFocused: Boolean,
    showProgress: Boolean,
    episodeCount: Int,
    danmakuOn: Boolean,
    onToggleDanmaku: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenEpisodes: () -> Unit,
    /** 当前条目的作者 sec_uid；为空时不出现「作者」入口（而不是点了才报错）。 */
    authorSecUid: String = "",
    authorName: String = "",
    onOpenAuthor: () -> Unit = {},
) {
    val progress = if (session.durationMs > 0L) {
        (session.positionMs.toFloat() / session.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showProgress) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(AxTvColors.Divider, RoundedCornerShape(2.dp)),
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .background(AxTvColors.Accent, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OverlayAction(
                label = if (session.playingIntent) "暂停" else "播放",
                modifier = Modifier.focusRequester(bottomBarFocusRequester),
                onClick = session::togglePlayPause,
            )
            OverlayAction(label = PlayerMenuSpec.TEXT_MENU_TITLE, onClick = onOpenMenu)
            // 画质/倍速不在这里重复一遍：它们都在「播放菜单」面板里，多两个焦点落点只会更乱。
            OverlayAction(
                label = "${PlayerMenuSpec.LABEL_DANMAKU}·${if (danmakuOn) "开" else "关"}",
                onClick = onToggleDanmaku,
            )
            // 合集播放列表面板入口（设置项 mix_playlist_panel + 当前条目属于合集时才出现）。
            if (episodeCount > 0) {
                OverlayAction(label = "合集（$episodeCount 集）", onClick = onOpenEpisodes)
            }
            // 「进入发布者频道」：携带当前条目的 sec_uid，落地后可从「播放全部」连续看完作者作品。
            if (authorSecUid.isNotBlank()) {
                OverlayAction(
                    label = when {
                        authorName.isBlank() -> "作者频道"
                        authorName.length <= 8 -> "作者：$authorName"
                        else -> "作者：${authorName.take(7)}…"
                    },
                    onClick = onOpenAuthor,
                )
            }
        }
        // 焦点在哪一层必须写在界面上：否则用户只知道"按了没反应"，不知道要长按。
        Text(
            text = if (chromeFocused) {
                "控制栏操作中：方向键选择，确认键执行，BACK 返回画面"
            } else {
                "上/下换条，左/右微调，长按↓ 进入下方控制栏"
            },
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 12.sp),
        )
    }
}

@Composable
internal fun OverlayAction(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(AxTvColors.Surface, RoundedCornerShape(8.dp))
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
internal fun PlayerMenuPanel(
    modifier: Modifier = Modifier,
    firstRequester: FocusRequester,
    qualityTracks: List<QualityTrack>,
    selectedQuality: Int,
    onQualitySelected: (Int) -> Unit,
    speed: Float,
    onSpeedSelected: (Float) -> Unit,
    danmakuOn: Boolean,
    onDanmakuToggle: () -> Unit,
    dislikeEnabled: Boolean,
    dislikeBusy: Boolean,
    dislikeNotice: String,
    onDislike: () -> Unit,
    onClose: () -> Unit,
    /** 当前条目有 author.sec_uid 时才出现「作者频道」行。 */
    authorEnabled: Boolean = false,
    authorName: String = "",
    onOpenAuthor: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .width(360.dp)
            .focusGroup()
            .background(AxTvColors.SurfaceHigh, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            // 大字号/低分辨率下内容可能超出一屏：可纵向滚动，焦点移动会自动带出视口。
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = PlayerMenuSpec.TEXT_MENU_TITLE,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )

        Text(
            text = PlayerMenuSpec.LABEL_QUALITY,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
        // FlowRow 而非 Row：画质档位多时换行，不把 chips 挤出 360dp 面板。
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            qualityTracks.forEachIndexed { index, track ->
                SegmentChip(
                    label = track.label,
                    selected = index == selectedQuality,
                    modifier = if (index == 0) Modifier.focusRequester(firstRequester) else Modifier,
                    onClick = { onQualitySelected(index) },
                )
            }
        }

        Text(
            text = PlayerMenuSpec.LABEL_SPEED,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
        // 7 档倍速在 312dp 内容宽里排不下（原 Row 直接溢出面板），换行排布。
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerMenuSpec.SPEED_OPTIONS.forEach { option ->
                SegmentChip(
                    label = PlayerMenuSpec.speedLabel(option),
                    selected = abs(option - speed) < 0.01f,
                    onClick = { onSpeedSelected(option) },
                )
            }
        }

        MenuRow(
            label = PlayerMenuSpec.LABEL_DANMAKU,
            value = if (danmakuOn) "开启" else "关闭",
            onClick = onDanmakuToggle,
        )
        // 「不喜欢」= 负反馈，接 commit/dislike/item（文案 on5:1501，请求 n11.java:5384）。
        // 值位三态：处理中 / 结论（已标记或失败原因）。只在当前条目真是作品接口回来的
        // aweme 时渲染——演示数据源没有 aweme_id，留着只会按了没反应（同一不渲染「屏蔽词」的理由）。
        if (dislikeEnabled) {
            MenuRow(
                label = PlayerMenuSpec.LABEL_DISLIKE,
                value = if (dislikeBusy) PlayerMenuSpec.LABEL_PROCESSING else dislikeNotice,
                onClick = onDislike,
            )
        }
        // 「作者频道」：观看中直接进入发布者主页，落地后可「播放全部」连续看完该作者作品。
        if (authorEnabled) {
            MenuRow(
                label = "作者频道",
                value = authorName.ifBlank { "进入 ›" },
                onClick = onOpenAuthor,
            )
        }
        // 原版菜单里还有「屏蔽词」「完整简介」两行（PlayerMenuSpec.LABEL_BLOCK_WORDS /
        // LABEL_FULL_DESCRIPTION）。两者都没有后端可接（屏蔽词只在弹幕过滤器里读设置，
        // 本应用没有编辑入口；完整简介要跳详情页但面板拿不到 awemeId），
        // 面板现在焦点可达了，留着只会按了没反应，先不渲染。
        MenuRow(label = PlayerMenuSpec.LABEL_BACK, value = "", onClick = onClose)

        Text(
            text = PlayerMenuSpec.TEXT_SLIDER_HINT,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 12.sp),
        )
    }
}

/**
 * 合集分集面板（设置项 mix_playlist_panel 的界面落点）。
 *
 * 数据由调用方注入（ui/nav/PlayerFromMixScreen 走 CollectionRepository），
 * 本页只负责展示与"选哪一集"，不直接访问接口。
 */
@Composable
internal fun MixEpisodePanel(
    modifier: Modifier = Modifier,
    firstRequester: FocusRequester,
    episodes: List<CollectionEpisode>,
    currentEpisodeId: String,
    onEpisodeSelected: (CollectionEpisode) -> Unit,
    onClose: () -> Unit,
) {
    val currentIndex = episodes.indexOfFirst { it.awemeId == currentEpisodeId }
    val listState = rememberLazyListState()
    // 打开面板先滚到正在播的那一集：几十集的合集从第 1 集开始翻才找得到当前集。
    LaunchedEffect(currentEpisodeId, episodes.size) {
        if (currentIndex >= 0) {
            runCatching { listState.scrollToItem(currentIndex) }
        }
    }
    Column(
        modifier = modifier
            .width(400.dp)
            .focusGroup()
            .background(AxTvColors.SurfaceHigh, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "合集播放列表",
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = if (currentIndex >= 0) {
                "共 ${episodes.size} 集 · 当前第 ${episodes[currentIndex].number} 集"
            } else {
                "共 ${episodes.size} 集"
            },
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(episodes, key = { _, episode -> episode.awemeId }) { index, episode ->
                EpisodeRow(
                    label = "第 ${episode.number} 集 · ${episode.title}",
                    selected = episode.awemeId == currentEpisodeId,
                    // 焦点给当前集（找不到时退回第一集），与滚动落点一致。
                    modifier = if (index == currentIndex.coerceAtLeast(0)) {
                        Modifier.focusRequester(firstRequester)
                    } else {
                        Modifier
                    },
                    onClick = { onEpisodeSelected(episode) },
                )
            }
        }
        MenuRow(label = "关闭", value = "", onClick = onClose)
    }
}

@Composable
internal fun EpisodeRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                color = if (selected) AxTvColors.Accent.copy(alpha = 0.2f) else AxTvColors.Surface,
                shape = RoundedCornerShape(8.dp),
            )
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (selected || focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
internal fun SegmentChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                color = if (selected) AxTvColors.Accent.copy(alpha = 0.2f) else AxTvColors.Surface,
                shape = RoundedCornerShape(8.dp),
            )
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected || focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
        )
    }
}

@Composable
internal fun MenuRow(label: String, value: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(AxTvColors.Surface, RoundedCornerShape(8.dp))
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 14.sp),
        )
        Spacer(Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(text = value, color = AxTvColors.Accent, style = TextStyle(fontSize = 13.sp))
        }
    }
}

/**
 * 内置弹幕数据源：点播评论 → 弹幕。
 *
 * 走 [loadCommentDanmakuComments]（InteractionApi.comments → CommentDanmakuMapper）；
 * 失败 / 未登录返回空表。调用方未显式注入 loader 且没有静态弹幕时自动使用。
 */
internal val defaultCommentDanmakuLoader: suspend (itemId: String, durationMs: Long) -> List<DanmakuComment> =
    { itemId, durationMs -> loadCommentDanmakuComments(AppRuntime.api, itemId, durationMs) }

internal fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}

/**
 * 播放时钟（设置页『播放时钟』的唯一消费点）。
 *
 * 模式语义：ALWAYS 常显、HOURLY 只在整点显示、HOURLY_AND_HALF 在整点与半点显示、NEVER 不显示。
 * 每 [CLOCK_TICK_MS] 取一次时间，保证整点 / 半点后的一分钟内出现，不必逐秒刷新。
 * 不显示时返回 null，由调用方决定是渲染在标题栏还是浮在右上角。
 */
@Composable
internal fun rememberPlayerClock(mode: SettingsTypes.PlaybackClock): String? {
    if (mode == SettingsTypes.PlaybackClock.NEVER) return null
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(mode) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(CLOCK_TICK_MS)
        }
    }
    val calendar = Calendar.getInstance().apply { timeInMillis = nowMs }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    if (!clockVisibleAt(mode, minute)) return null
    return String.format(Locale.US, "%02d:%02d", hour, minute)
}

internal fun clockVisibleAt(mode: SettingsTypes.PlaybackClock, minute: Int): Boolean = when (mode) {
    SettingsTypes.PlaybackClock.ALWAYS -> true
    SettingsTypes.PlaybackClock.HOURLY -> minute == 0
    SettingsTypes.PlaybackClock.HOURLY_AND_HALF -> minute == 0 || minute == 30
    SettingsTypes.PlaybackClock.NEVER -> false
}
