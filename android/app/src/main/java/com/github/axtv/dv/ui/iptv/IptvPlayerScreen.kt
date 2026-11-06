/*
 * 电视台直播 —— 单频道播放页。
 *
 * 播放内核复用直播间线的 [com.github.axtv.dv.ui.live.LivePlaybackController]（其内部即
 * Media3Engine.setMediaItem(url, useCache = false)，对齐 D5：直播不走磁盘缓存）。本页不改内核，只决定候选顺序。
 *
 * 候选回退（对齐 PLAN §3.3「禁止静默降级」）：同一频道在多份清单里各有地址，按顺序切换；
 * 全部候选失败必须落到显式错误卡片（可重试 / 可返回），不允许黑屏转圈。
 */
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.github.axtv.dv.ui.iptv

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.ui.live.LivePlaybackController
import com.github.axtv.dv.ui.live.describePlaybackError

/** 电视台播放页（供导航接线）。channelId 为归一化频道名，候选地址从已加载目录反查。 */
@Composable
fun IptvPlayerScreen(
    channelId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val controller = remember { LivePlaybackController(context) }

    val candidates = remember(channelId) { IptvRepository.candidatesForChannel(channelId) }
    val channelName = remember(channelId) { IptvRepository.channelName(channelId) }

    var candidateIndex by remember { mutableIntStateOf(0) }
    var playFailure by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }

    val retryRequester = remember { FocusRequester() }

    BackHandler(onBack = onBack)

    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    LaunchedEffect(channelId, reloadToken) {
        if (candidates.isEmpty()) {
            playFailure = "该频道暂无可播放地址"
            return@LaunchedEffect
        }
        playFailure = null
        candidateIndex = 0
        controller.play(candidates.first())
    }

    // 当前地址失败 → 按顺序切下一个候选；候选耗尽才落错误卡片。
    LaunchedEffect(controller.playerError) {
        val error = controller.playerError ?: return@LaunchedEffect
        val next = candidateIndex + 1
        if (next < candidates.size) {
            candidateIndex = next
            controller.play(candidates[next])
        } else {
            playFailure = describePlaybackError(error)
        }
    }

    LaunchedEffect(playFailure) {
        if (playFailure != null) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { retryRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    controller.attachSurface(this)
                }
            },
        )

        if (!controller.firstFrame && playFailure == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (candidates.isEmpty()) channelName else "正在连接 $channelName…",
                    color = Color.White,
                    style = TextStyle(fontSize = 16.sp),
                )
            }
        }

        if (playFailure != null) {
            IptvPlaybackErrorCard(
                name = channelName,
                message = playFailure.orEmpty(),
                canRetry = candidates.isNotEmpty(),
                retryRequester = retryRequester,
                onRetry = { reloadToken += 1 },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun IptvPlaybackErrorCard(
    name: String,
    message: String,
    canRetry: Boolean,
    retryRequester: FocusRequester,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AxTvColors.SurfaceHigh, RoundedCornerShape(14.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "$name 无法播放",
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = message,
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 13.sp),
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IptvErrorAction(label = "返回", requester = null, onClick = onBack)
                if (canRetry) {
                    IptvErrorAction(label = "重试", requester = retryRequester, onClick = onRetry)
                }
            }
        }
    }
}

@Composable
private fun IptvErrorAction(
    label: String,
    requester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(AxTvColors.Accent.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
            .focusRing(focused, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}
