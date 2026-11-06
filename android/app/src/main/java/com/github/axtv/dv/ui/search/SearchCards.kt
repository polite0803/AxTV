/*
 * 搜索页的结果卡片与状态面板（从 SearchScreen.kt 拆出）。
 * 拆分原因: SearchScreen.kt 超 800 行；卡片/面板组件与页面编排无耦合，仅被其调用。
 */
package com.github.axtv.dv.ui.search

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing

// ------------------------------------------------------------------ 结果卡片

@Composable
internal fun VideoCard(
    item: VideoResult,
    modifier: Modifier = Modifier,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .clickable(onClick = onClick)
            .background(AxTvColors.Surface, RoundedCornerShape(12.dp))
            .focusRing(focused, RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(AxTvColors.SurfaceHigh, RoundedCornerShape(8.dp)),
        ) {
            if (item.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = formatDuration(item.durationMs),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 12.sp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.height(42.dp)) {
            Text(
                text = item.title,
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = item.author,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PersonRow(
    avatarUrl: String,
    title: String,
    subtitle: String,
    trailing: String?,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    clickable: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            // 不可点击的行只保留 D-pad 焦点（Enter 无动作），避免「看着能点却没反应」。
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier.focusable())
            .background(AxTvColors.Surface, RoundedCornerShape(10.dp))
            .focusRing(focused, RoundedCornerShape(10.dp))
            .then(if (clickable) Modifier else Modifier.alpha(0.62f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(AxTvColors.SurfaceHigh, RoundedCornerShape(8.dp)),
        ) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                color = if (clickable) AxTvColors.AccentWarm else AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
internal fun MoreRow(loading: Boolean, error: String?, onLoadMore: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = !loading, onClick = onLoadMore)
            .background(AxTvColors.Surface, RoundedCornerShape(10.dp))
            .focusRing(focused, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                loading -> "正在加载更多…"
                error != null -> "加载失败：$error（点此重试）"
                else -> "加载更多"
            },
            color = if (error != null) AxTvColors.AccentWarm else AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 14.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------------------------------------------ 状态面板

@Composable
internal fun StatePanel(
    title: String,
    detail: String,
    diagnostic: String? = null,
    actionLabel: String? = null,
    actionRequester: FocusRequester? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AxTvColors.Surface, RoundedCornerShape(12.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        )
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 13.sp),
            )
        }
        if (diagnostic != null && diagnostic.isNotBlank()) {
            Text(
                text = diagnostic,
                color = AxTvColors.TextSecondary.copy(alpha = 0.7f),
                style = TextStyle(fontSize = 11.sp),
            )
        }
        if (actionLabel != null && onAction != null) {
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .then(if (actionRequester != null) Modifier.focusRequester(actionRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(onClick = onAction)
                    .background(AxTvColors.SurfaceHigh, RoundedCornerShape(8.dp))
                    .focusRing(focused, RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    text = actionLabel,
                    color = if (focused) AxTvColors.Accent else AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

internal fun userSubtitle(item: UserResult): String {
    val parts = mutableListOf<String>()
    if (item.followerCount > 0L) {
        parts += "粉丝 ${formatCount(item.followerCount)}"
    }
    if (item.signature.isNotBlank()) {
        parts += item.signature
    }
    return parts.joinToString(" · ")
}
