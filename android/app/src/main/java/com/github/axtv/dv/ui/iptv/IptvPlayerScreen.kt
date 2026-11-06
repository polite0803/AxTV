/*
 * 电视台直播 —— 单频道播放页。
 *
 * 播放内核复用直播间线的 [com.github.axtv.dv.ui.live.LivePlaybackController]（内部即
 * Media3Engine.setMediaItem(url, useCache = false)，对齐 D5：直播不走磁盘缓存）。
 *
 * 遥控器操作（贴近看电视习惯）：
 *   ↑ / 频道+ / 下一媒体键    上一台（列表展示顺序，循环）
 *   ↓ / 频道- / 上一媒体键    下一台
 *   ← / →                     音量 - / +
 *   确认 / MENU / INFO        显示/隐藏信息浮层（无按键 4 秒自动隐藏）
 *   返回                      退出到电视台列表
 *
 * 候选回退（对齐 PLAN §3.3「禁止静默降级」）：同一频道多份清单各有地址，按序切换；
 * 全部候选失败落到显式错误卡片（可重试 / 可返回）。
 */
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.github.axtv.dv.ui.iptv

import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import kotlinx.coroutines.delay

/** 信息浮层无按键自动隐藏延时。 */
private const val IPTV_CHROME_AUTO_HIDE_MS = 4_000L

/** 电视台播放页（供导航接线）。channelId 为归一化频道名。 */
@Composable
fun IptvPlayerScreen(
    channelId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val controller = remember { LivePlaybackController(context) }

    // 扁平频道表：换台在同一份目录内循环，不压新路由。
    val ordered = remember { IptvRepository.orderedChannels() }
    var currentIndex by remember {
        mutableIntStateOf(
            ordered.indexOfFirst { it.id == channelId }.takeIf { it >= 0 } ?: 0,
        )
    }
    val current = ordered.getOrNull(currentIndex)
    val channelName = current?.name ?: IptvRepository.channelName(channelId)
    val candidates = remember(currentIndex, ordered) {
        current?.candidates ?: IptvRepository.candidatesForChannel(channelId)
    }

    var candidateIndex by remember { mutableIntStateOf(0) }
    var playFailure by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }

    var volumeUi by remember { mutableFloatStateOf(controller.volume()) }
    var chromeVisible by remember { mutableStateOf(true) }
    var chromeStamp by remember { mutableIntStateOf(0) }

    val surfaceFocus = remember { FocusRequester() }
    val retryRequester = remember { FocusRequester() }

    BackHandler(onBack = onBack)

    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    LaunchedEffect(currentIndex, reloadToken) {
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

    // 出错时把焦点交给「重试」，其余时候回到画面层（画面层是唯一接键的地方）。
    LaunchedEffect(playFailure) {
        withFrameNanos { }
        runCatching {
            if (playFailure != null) retryRequester.requestFocus() else surfaceFocus.requestFocus()
        }
    }

    // 信息浮层无按键 4 秒自动隐藏。
    LaunchedEffect(chromeVisible, chromeStamp) {
        if (!chromeVisible) return@LaunchedEffect
        delay(IPTV_CHROME_AUTO_HIDE_MS)
        chromeVisible = false
    }

    fun showChrome() {
        chromeVisible = true
        chromeStamp += 1
    }

    fun switchChannel(delta: Int) {
        if (ordered.size <= 1) return
        currentIndex = (currentIndex + delta + ordered.size) % ordered.size
        showChrome()
    }

    fun changeVolume(delta: Float) {
        controller.adjustVolume(delta)
        volumeUi = controller.volume()
        showChrome()
    }

    fun handleKey(event: KeyEvent): Boolean {
        // 错误卡片出现时把按键交回焦点系统，保证「重试/返回」可达。
        if (playFailure != null) return false
        val code = event.nativeKeyEvent.keyCode
        val isUp = code == AndroidKeyEvent.KEYCODE_DPAD_UP ||
            code == AndroidKeyEvent.KEYCODE_CHANNEL_UP ||
            code == AndroidKeyEvent.KEYCODE_MEDIA_NEXT
        val isDown = code == AndroidKeyEvent.KEYCODE_DPAD_DOWN ||
            code == AndroidKeyEvent.KEYCODE_CHANNEL_DOWN ||
            code == AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS
        val isLeft = code == AndroidKeyEvent.KEYCODE_DPAD_LEFT
        val isRight = code == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
        val isConfirm = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
            code == AndroidKeyEvent.KEYCODE_ENTER ||
            code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
        val isInfo = code == AndroidKeyEvent.KEYCODE_MENU || code == AndroidKeyEvent.KEYCODE_INFO
        if (!(isUp || isDown || isLeft || isRight || isConfirm || isInfo)) return false
        // KeyDown/KeyUp 都吃掉，避免 KeyUp 触发 Compose 默认焦点搜索把焦点挪走。
        if (event.type != KeyEventType.KeyDown) return true
        when {
            isUp -> switchChannel(+1)
            isDown -> switchChannel(-1)
            isLeft -> changeVolume(-0.1f)
            isRight -> changeVolume(+0.1f)
            else -> {
                chromeVisible = !chromeVisible
                if (chromeVisible) chromeStamp += 1
            }
        }
        return true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(surfaceFocus)
            .focusable()
            .onPreviewKeyEvent { event -> handleKey(event) },
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
                    text = "正在连接 $channelName…",
                    color = Color.White,
                    style = TextStyle(fontSize = 16.sp),
                )
            }
        }

        if (chromeVisible && playFailure == null) {
            IptvChrome(
                name = channelName,
                position = if (ordered.isEmpty()) "" else "第 ${currentIndex + 1} / ${ordered.size} 台",
                volume = volumeUi,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 32.dp, bottom = 28.dp),
            )
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
private fun IptvChrome(
    name: String,
    position: String,
    volume: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = name,
            color = Color.White,
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )
        if (position.isNotEmpty()) {
            Text(
                text = position,
                color = Color.LightGray,
                style = TextStyle(fontSize = 13.sp),
            )
        }
        Text(
            text = "音量 ${(volume * 100).toInt()}%",
            color = Color.LightGray,
            style = TextStyle(fontSize = 13.sp),
        )
        Text(
            text = "↑↓ 换台 · ←→ 音量 · 确认 信息 · 返回 退出",
            color = Color.LightGray,
            style = TextStyle(fontSize = 12.sp),
        )
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
