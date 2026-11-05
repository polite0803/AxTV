/*
 * 导航接线用的装配页：不在任何功能页内部改动，只把「网络数据 → 播放器数据源」接起来。
 *
 * - NavMessageScreen：加载中 / 失败 / 空队列的统一画面（可返回、可重试）。
 * - PlayerFromFeedScreen：首页队列（HomeFeedRepository）直接播放；队列为空时先拉首屏。
 * - PlayerFromAwemeScreen：按 awemeId 走 VideoApi.detail 拉作品后单条播放，
 *   供详情页、历史、稍后再看复用（合集外单集没有整队列可播）。
 * - PlayerFromMixScreen：合集内单条播放 + 分集列表（设置项 mix_playlist_panel 的数据源）。
 */
package com.github.axtv.dv.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import axtv.api.VideoApi
import axtv.model.Aweme
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.collection.CollectionEpisode
import com.github.axtv.dv.ui.collection.CollectionRepository
import com.github.axtv.dv.ui.featured.FeaturedRepository
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.ui.home.HomeFeedRepository
import com.github.axtv.dv.ui.player.PlayerOverlayScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 统一提示页：加载中 / 失败 / 空态，首帧把焦点交给「返回」或「重试」。 */
@Composable
fun NavMessageScreen(
    title: String,
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val actionRequester = remember { FocusRequester() }

    LaunchedEffect(retryLabel) {
        withFrameNanos { }
        runCatching { actionRequester.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = message,
            color = if (onRetry != null) AxTvColors.AccentWarm else AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 14.sp),
        )
        if (retryLabel != null && onRetry != null) {
            NavAction(label = retryLabel, requester = actionRequester, onClick = onRetry)
        }
        NavAction(
            label = "返回",
            requester = if (retryLabel == null) actionRequester else null,
            onClick = onBack,
        )
    }
}

@Composable
private fun NavAction(
    label: String,
    requester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = (if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(AxTvColors.Surface, RoundedCornerShape(10.dp))
            .focusRing(focused, RoundedCornerShape(10.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * 首页队列播放：直接用 [HomeFeedRepository.cachedAwemes]（首页已加载过的真实列表）。
 * 若从小组件/深链直接进入播放器、首页尚未加载，则先拉首屏补齐队列。
 */
@Composable
fun PlayerFromFeedScreen(
    startIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var queue by remember { mutableStateOf(HomeFeedRepository.cachedAwemes) }
    var loading by remember { mutableStateOf(queue.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        if (queue.isNotEmpty()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        HomeFeedRepository.load(0L).fold(
            onSuccess = { page ->
                queue = page
                HomeFeedRepository.cache(page)
            },
            onFailure = { error = it.message ?: it.javaClass.simpleName },
        )
        loading = false
    }

    when {
        queue.isNotEmpty() -> PlayerOverlayScreen(
            modifier = modifier,
            playlistProvider = remember(queue, startIndex) {
                awemePlaylistProvider(queue, startIndex)
            },
        )

        loading -> NavMessageScreen(
            title = "播放器",
            message = "正在加载播放队列…",
            onBack = onBack,
            modifier = modifier,
        )

        else -> NavMessageScreen(
            title = "播放器",
            message = error ?: "播放队列为空，请先回到首页加载内容。",
            onBack = onBack,
            modifier = modifier,
            retryLabel = "重试",
            onRetry = { attempt += 1 },
        )
    }
}

/**
 * 精选队列播放：队列来自 FeaturedScreen 点击条目时写入的 [FeaturedRepository.cachedAwemes]。
 * 无队列（例如直接从外部 route 进入 featured/play）时只提示返回，不再拉数据。
 */
@Composable
fun PlayerFromFeaturedScreen(
    startIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val queue = FeaturedRepository.cachedAwemes
    when {
        queue.isNotEmpty() -> PlayerOverlayScreen(
            modifier = modifier,
            playlistProvider = remember(queue, startIndex) {
                awemePlaylistProvider(queue, startIndex)
            },
        )

        else -> NavMessageScreen(
            title = "播放器",
            message = "精选播放队列为空，请先在精选页选择视频。",
            onBack = onBack,
            modifier = modifier,
        )
    }
}

/** 单作品解析结果（详情接口 + 可重试），供 [PlayerFromAwemeScreen] 与 [PlayerFromMixScreen] 共用。 */
private data class PlaybackLoad(
    val aweme: Aweme?,
    val loading: Boolean,
    val error: String?,
    val retry: () -> Unit,
)

/** 按 awemeId 走 VideoApi.detail 解析作品；切 id 时自动重解析。 */
@Composable
private fun rememberAwemePlayback(awemeId: String): PlaybackLoad {
    var aweme by remember(awemeId) { mutableStateOf<Aweme?>(null) }
    var loading by remember(awemeId) { mutableStateOf(true) }
    var error by remember(awemeId) { mutableStateOf<String?>(null) }
    var attempt by remember(awemeId) { mutableIntStateOf(0) }

    LaunchedEffect(awemeId, attempt) {
        loading = true
        error = null
        val api = AppRuntime.api
        if (api == null) {
            error = "接口尚未装配，请稍后重试。"
            loading = false
            return@LaunchedEffect
        }
        try {
            val detail = withContext(Dispatchers.IO) { VideoApi(api).detail(awemeId) }
            val loaded = detail.awemeDetail()
            aweme = loaded
            if (loaded == null) {
                error = "未找到作品 $awemeId"
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
        }
        loading = false
    }
    return PlaybackLoad(aweme, loading, error) { attempt += 1 }
}

/** 按 awemeId 拉取作品并单条播放（详情 / 历史 / 稍后再看 / 合集单集共用）。 */
@Composable
fun PlayerFromAwemeScreen(
    awemeId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val load = rememberAwemePlayback(awemeId)
    val loadedAweme = load.aweme
    when {
        loadedAweme != null -> PlayerOverlayScreen(
            modifier = modifier,
            playlistProvider = remember(loadedAweme) {
                awemePlaylistProvider(listOf(loadedAweme), 0)
            },
        )

        load.loading -> NavMessageScreen(
            title = "播放器",
            message = "正在解析作品 $awemeId …",
            onBack = onBack,
            modifier = modifier,
        )

        else -> NavMessageScreen(
            title = "播放器",
            message = load.error ?: "无法播放作品 $awemeId",
            onBack = onBack,
            modifier = modifier,
            retryLabel = "重试",
            onRetry = load.retry,
        )
    }
}

/**
 * 合集内播放：在单集播放之上再给出「合集分集」列表（设置项 mix_playlist_panel 的数据源）。
 *
 * 分集列表来自 CollectionRepository.loadDetail(mixId)（mix/aweme 分页，与合集详情页同一入口），
 * 选一集只换 currentId → 重新解析作品 → 重建 provider 起播：
 * 不改 PlayerSession 的换档/续播/兜底逻辑，也不额外压导航栈（BACK 仍是一次退出合集）。
 * 分集列表拉取失败时只是没有面板，播放不受影响。
 */
@Composable
fun PlayerFromMixScreen(
    mixId: String,
    awemeId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentId by remember(mixId) { mutableStateOf(awemeId) }
    var episodes by remember(mixId) { mutableStateOf<List<CollectionEpisode>>(emptyList()) }
    val load = rememberAwemePlayback(currentId)

    LaunchedEffect(mixId) {
        episodes = runCatching { CollectionRepository.loadDetail(mixId).episodes }.getOrDefault(emptyList())
    }

    val loadedAweme = load.aweme
    when {
        loadedAweme != null -> PlayerOverlayScreen(
            modifier = modifier,
            playlistProvider = remember(loadedAweme) {
                awemePlaylistProvider(listOf(loadedAweme), 0)
            },
            mixEpisodes = episodes,
            mixCurrentEpisodeId = currentId,
            onMixEpisodeSelected = { episode ->
                if (episode.awemeId.isNotBlank() && episode.awemeId != currentId) {
                    currentId = episode.awemeId
                }
            },
        )

        load.loading -> NavMessageScreen(
            title = "合集播放",
            message = "正在解析合集作品 $currentId …",
            onBack = onBack,
            modifier = modifier,
        )

        else -> NavMessageScreen(
            title = "合集播放",
            message = load.error ?: "无法播放作品 $currentId",
            onBack = onBack,
            modifier = modifier,
            retryLabel = "重试",
            onRetry = load.retry,
        )
    }
}
