/*
 * 首页 Feed 的子组件与文案工具（从 HomeFeedScreen.kt 拆出）。
 * 拆分原因: HomeFeedScreen.kt 超 800 行；卡片/状态面板/格式化只被主页面调用。
 */
package com.github.axtv.dv.ui.home

import axtv.api.ApiException
import axtv.model.Aweme
import axtv.ui.DetailPageSpec
import axtv.ui.FeedPageSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.ui.home.HomeFeedRepository
import java.util.Locale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage


/** 把 ApiException 的种类翻译成可读文案；未识别的异常保留原始 message。 */
internal fun describeFeedError(t: Throwable): String = when (t) {
    is ApiException -> when (t.kind()) {
        ApiException.Kind.NETWORK -> "网络请求失败：${t.message.orEmpty()}"
        ApiException.Kind.HTTP_STATUS -> "服务端返回 HTTP ${t.httpStatus()}"
        ApiException.Kind.EMPTY_BODY -> "空响应：签名参数失效或 Cookie 已过期"
        ApiException.Kind.HEADER_ERROR -> "请求被拦截：${t.message.orEmpty()}"
        ApiException.Kind.API_STATUS -> "接口状态异常：status_code=${t.apiStatus()}"
        ApiException.Kind.LOGIN_REQUIRED -> t.message.orEmpty()
        ApiException.Kind.RATE_LIMITED -> t.message.orEmpty()
        ApiException.Kind.DECODE -> "响应解析失败：${t.message.orEmpty()}"
        ApiException.Kind.MISSING_CREDENTIAL -> t.message.orEmpty()
        else -> "请求失败：${t.message.orEmpty()}"
    }

    else -> t.message ?: t.javaClass.simpleName
}

internal fun formatCount(value: Long): String = when {
    value >= 100_000_000L -> String.format(Locale.US, "%.1f亿", value / 100_000_000.0)
    value >= 10_000L -> String.format(Locale.US, "%.1f万", value / 10_000.0)
    else -> value.toString()
}

internal fun Aweme.cardTitle(): String = desc().ifBlank { FeedPageSpec.TEXT_UNTITLED_VIDEO }

/** 「已看」角标文案：来源是本地环形缓冲（ViewedVideoTracker），不是服务端历史。 */
private const val TEXT_VIEWED = "已看"

internal fun Aweme.cardAuthor(): String =
    author().nickname().takeIf { it.isNotBlank() }?.let { "@$it" } ?: "未知作者"

internal fun Aweme.cardMeta(): String {
    val stat = statistics()
    return listOf(
        "点赞 ${formatCount(stat.diggCount())}",
        "评论 ${formatCount(stat.commentCount())}",
        "收藏 ${formatCount(stat.collectCount())}",
    ).joinToString(FeedPageSpec.TEXT_META_SEPARATOR)
}

internal fun Aweme.durationLabel(): String {
    val totalSeconds = (video().duration().toLong() / 1000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
internal fun FeedStatePanel(
    title: String,
    detail: String,
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
        Text(
            text = detail,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
        if (actionLabel != null && onAction != null) {
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = (if (actionRequester != null) Modifier.focusRequester(actionRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(onClick = onAction)
                    .background(AxTvColors.SurfaceHigh, RoundedCornerShape(8.dp))
                    .focusRing(focused, RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    text = actionLabel,
                    color = if (focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
internal fun HomeTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                color = if (selected) AxTvColors.Accent.copy(alpha = 0.16f) else AxTvColors.Surface,
                shape = RoundedCornerShape(8.dp),
            )
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected || focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
        )
    }
}

@Composable
internal fun RelatedCard(item: Aweme, viewed: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(240.dp)
            .height(150.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(AxTvColors.Surface, RoundedCornerShape(12.dp))
            .focusRing(focused, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CoverThumb(
            url = item.coverUrl(),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Text(
            text = item.cardTitle(),
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = "${item.cardAuthor()} · ${item.durationLabel()}",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 12.sp),
        )
        if (viewed) ViewedBadge()
    }
}

/**
 * 首页封面 URL：cover → dynamic_cover（原版口径），再兜一层 origin_cover。
 *
 * 推荐流接口不一定带齐前两者，只带 origin_cover 时如果不兜底，卡片就只剩文字。
 */
internal fun Aweme.coverUrl(): String {
    val primary = DetailPageSpec.coverUrl(video())
    if (primary.isNotEmpty()) return primary
    return video().originCover().urlList()?.firstOrNull().orEmpty()
}

/**
 * 卡片封面。
 *
 * 首页的封面区原本是一块纯色占位 Box（从未接图），所以整页只有文字。
 * 这里按精选 / 直播页同款写法用 Coil 加载：URL 为空或加载失败时保留占位底色，不留空洞。
 * URL 统一走 [DetailPageSpec.coverUrl]（cover → dynamicCover 回退，与原版一致）。
 */
@Composable
internal fun CoverThumb(
    url: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Box(modifier = modifier.clip(shape).background(AxTvColors.SurfaceHigh)) {
        if (url.isNotEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun FeedRow(
    item: Aweme,
    viewed: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick)
            .background(AxTvColors.Surface, RoundedCornerShape(12.dp))
            .focusRing(focused, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoverThumb(
            url = item.coverUrl(),
            modifier = Modifier
                .width(160.dp)
                .height(90.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = item.cardTitle(),
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = "${item.cardAuthor()} · ${item.durationLabel()}",
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 13.sp),
            )
            Text(
                text = item.cardMeta(),
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
            if (viewed) ViewedBadge()
        }
    }
}

/** 已看角标：小胶囊，取自本地 viewed_video_ids 清单。 */
@Composable
internal fun ViewedBadge() {
    Box(
        modifier = Modifier
            .background(AxTvColors.SurfaceHigh, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = TEXT_VIEWED,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
internal fun FeedFooter(
    loadingMore: Boolean,
    error: String?,
    hasMore: Boolean,
    loadedCount: Int,
    onLoadMore: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    when {
        loadingMore -> Text(
            text = "加载更多中…",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )

        error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "加载更多失败：$error",
                color = AxTvColors.AccentWarm,
                style = TextStyle(fontSize = 13.sp),
            )
            Box(
                modifier = Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(onClick = onLoadMore)
                    .background(AxTvColors.Surface, RoundedCornerShape(8.dp))
                    .focusRing(focused, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "重试",
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }

        hasMore -> Text(
            // 原来是这里一个可点击的「上滑加载更多」：电视没有上滑手势，焦点走到末尾还会
            // 撞上一个要按确认才生效的按钮。翻页现在由上面的 LaunchedEffect 在焦点/滚动
            // 接近末尾时自动发生，这里只留状态说明。
            text = "继续下移自动加载（已加载 $loadedCount 条）",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )

        else -> Text(
            text = "已加载全部 $loadedCount 条",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
    }
}
