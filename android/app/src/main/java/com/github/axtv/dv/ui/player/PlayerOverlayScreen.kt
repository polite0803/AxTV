/*
 * 样板页 3：播放器浮层（画面用 AndroidView 包 SurfaceView，浮层菜单用 Compose）。
 * 规格出处：refactor/ui/NOTES-player-ui.md（lq2.java 菜单装配）、
 *           refactor/ui/axtv/ui/PlayerMenuSpec.java（文案/倍速/画质/行规格）、
 *           PlayerUiPalette.java（角色 → 用色）。
 * 原版画面层是 Media3 PlayerView + SurfaceView（src/app/p000/bp0.java:113-125），
 * 这里只做占位 SurfaceView，不接 ExoPlayer；真实接入按 ADAPTERS.md §播放器 走 Media3Engine。
 */
package com.github.axtv.dv.ui.player

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import axtv.ui.PlayerMenuSpec
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing

@Composable
fun PlayerOverlayScreen(modifier: Modifier = Modifier) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val menuTriggerRequester = remember { FocusRequester() }
    val firstMenuItemRequester = remember { FocusRequester() }

    // 菜单开合时把焦点交给对应区域；下一帧再请求，避免节点尚未 attach。
    LaunchedEffect(menuOpen) {
        withFrameNanos { }
        runCatching {
            if (menuOpen) firstMenuItemRequester.requestFocus() else menuTriggerRequester.requestFocus()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // 画面层：真实实现替换为 PlayerView（Media3），浮层保持 Compose。
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SurfaceView(context).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "播放器浮层",
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "画面：AndroidView(SurfaceView) · 浮层：Compose · 规格 NOTES-player-ui.md",
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 13.sp),
                )
                Spacer(Modifier.weight(1f))
                Text("00:00 / 00:00", color = AxTvColors.TextSecondary, style = TextStyle(fontSize = 13.sp))
            }

            Text(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                text = "画面层占位（未接 Media3）",
                color = Color.White.copy(alpha = 0.35f),
                style = TextStyle(fontSize = 16.sp),
            )

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(AxTvColors.Divider, RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(4.dp)
                            .background(AxTvColors.Accent, RoundedCornerShape(2.dp)),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverlayAction(
                        label = PlayerMenuSpec.TEXT_MENU_TITLE,
                        modifier = Modifier.focusRequester(menuTriggerRequester),
                        onClick = { menuOpen = true },
                    )
                    OverlayAction(label = PlayerMenuSpec.LABEL_DANMAKU, onClick = {})
                    OverlayAction(label = PlayerMenuSpec.LABEL_QUALITY, onClick = { menuOpen = true })
                    OverlayAction(label = PlayerMenuSpec.LABEL_SPEED, onClick = { menuOpen = true })
                }
            }
        }

        if (menuOpen) {
            PlayerMenuPanel(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                firstRequester = firstMenuItemRequester,
                onClose = { menuOpen = false },
            )
        }
    }
}

@Composable
private fun OverlayAction(
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
private fun PlayerMenuPanel(
    modifier: Modifier = Modifier,
    firstRequester: FocusRequester,
    onClose: () -> Unit,
) {
    var qualityIndex by rememberSaveable { mutableStateOf(0) }
    var speedIndex by rememberSaveable { mutableStateOf(3) }
    var danmakuOn by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .width(360.dp)
            .background(AxTvColors.SurfaceHigh, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerMenuSpec.QUALITY_OPTIONS.forEachIndexed { index, quality ->
                SegmentChip(
                    label = quality.label(),
                    selected = index == qualityIndex,
                    modifier = if (index == 0) Modifier.focusRequester(firstRequester) else Modifier,
                    onClick = { qualityIndex = index },
                )
            }
        }

        Text(
            text = PlayerMenuSpec.LABEL_SPEED,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerMenuSpec.SPEED_OPTIONS.forEachIndexed { index, speed ->
                SegmentChip(
                    label = PlayerMenuSpec.speedLabel(speed),
                    selected = index == speedIndex,
                    onClick = { speedIndex = index },
                )
            }
        }

        MenuRow(
            label = PlayerMenuSpec.LABEL_DANMAKU,
            value = if (danmakuOn) "开启" else "关闭",
            onClick = { danmakuOn = !danmakuOn },
        )
        MenuRow(label = PlayerMenuSpec.LABEL_BLOCK_WORDS, value = "0 条", onClick = {})
        MenuRow(label = PlayerMenuSpec.LABEL_FULL_DESCRIPTION, value = "", onClick = {})
        MenuRow(label = PlayerMenuSpec.LABEL_BACK, value = "", onClick = onClose)

        Text(
            text = PlayerMenuSpec.TEXT_SLIDER_HINT,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 12.sp),
        )
    }
}

@Composable
private fun SegmentChip(
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
private fun MenuRow(label: String, value: String, onClick: () -> Unit) {
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
