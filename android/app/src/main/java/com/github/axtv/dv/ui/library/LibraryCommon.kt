/*
 * 个人中心 / 历史 / 收藏 / 稍后再看 的共享 UI 与加载状态。
 *
 * 设计要点：
 *  - 所有页面统一用 LoadState 表达「加载中 / 成功 / 失败（含未登录）」，禁止把失败吞成空列表；
 *  - 未登录优先在请求前短路（Cookie 里没有 sessionid 时直接给「去登录」提示），
 *    同时把 ApiException.Kind.LOGIN_REQUIRED / EMPTY_BODY / status_code=8 映射为登录态；
 *  - 数据模型沿用核心层 axtv.model（Java record），计数格式化复用 DataModels.formatCount。
 */
package com.github.axtv.dv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import axtv.api.ApiException
import axtv.model.Aweme
import com.github.axtv.dv.ui.FocusMemory
import axtv.model.Author
import axtv.model.DataModels
import axtv.ui.DetailPageSpec
import axtv.ui.HistoryGroupSpec
import coil.compose.AsyncImage
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
import java.util.Locale

/** 页面级加载状态。Failed 单独携带 loginRequired，未登录时据此渲染「去登录」提示。 */
internal sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(
        val loginRequired: Boolean,
        val title: String,
        val detail: String,
    ) : LoadState<Nothing>
}

internal const val LOGIN_TITLE = "需要登录"
internal const val LOGIN_DETAIL = "请前往「设置 → Cookie 登录」完成登录后，回到本页点击「重试」。"
internal const val LOGIN_ACTION_HINT = "去登录：设置 → Cookie 登录"

/**
 * 请求前判断是否具备登录态。
 *
 * 运行时 CookieStore 与设置里保存的 Cookie 串都可能承载登录态（两者的同步尚未接线），
 * 任一存在非空 sessionid / sessionid_ss 就放行，避免误把已登录用户挡在登录提示页。
 */
internal fun isLoggedIn(): Boolean {
    val jar = AppRuntime.api?.cookies()?.cookies().orEmpty()
    val cookieLoggedIn = jar["sessionid"]?.isNotBlank() == true ||
        jar["sessionid_ss"]?.isNotBlank() == true
    return cookieLoggedIn || AppRuntime.settings?.isLoggedIn == true
}

/** 业务状态码归一：8 = 未登录。 */
internal fun statusFailure(statusCode: Int, page: String): LoadState.Failed? = when {
    statusCode == 0 -> null
    statusCode == 8 -> LoadState.Failed(true, LOGIN_TITLE, "「$page」需要登录。$LOGIN_DETAIL")
    else -> LoadState.Failed(false, "接口返回异常", "「$page」返回 status_code=$statusCode，请稍后重试。")
}

/** ApiException 按 Kind 给可读文案；EMPTY_BODY 最常见的原因是 Cookie 过期，按登录态提示。 */
internal fun Throwable.toFailure(page: String): LoadState.Failed {
    val api = this as? ApiException
    if (api == null) {
        return LoadState.Failed(false, "加载失败", "「$page」加载失败：${message ?: javaClass.simpleName}")
    }
    return when (api.kind()) {
        ApiException.Kind.LOGIN_REQUIRED -> LoadState.Failed(true, LOGIN_TITLE, LOGIN_DETAIL)
        ApiException.Kind.EMPTY_BODY -> LoadState.Failed(
            true,
            "未登录或登录已失效",
            "服务器返回空响应，通常是 Cookie 已过期。请前往「设置 → Cookie 登录」重新登录后重试。",
        )

        ApiException.Kind.RATE_LIMITED -> LoadState.Failed(false, "请求过于频繁", "请稍后再试。")
        ApiException.Kind.NETWORK -> LoadState.Failed(
            false,
            "网络连接失败",
            api.message ?: "请检查网络连接后重试。",
        )

        ApiException.Kind.HTTP_STATUS -> {
            val detail = api.message ?: "HTTP ${api.httpStatus()}"
            // Argus 403 有两种：a_bogus 没生成（签名器失败）或生成了但被拒。
            // DouyinApi 会附 [a_bogus=...]，签名器原因再补一行，界面截图即可定位。
            val signerError = if (detail.contains("ArgusSecurityPlugin")) {
                AppRuntime.signer?.lastError()?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            LoadState.Failed(
                false,
                "服务器返回错误",
                if (signerError != null) "$detail\n签名器：$signerError" else detail,
            )
        }

        ApiException.Kind.HEADER_ERROR -> LoadState.Failed(false, "请求被拦截", api.message ?: "请稍后重试。")
        ApiException.Kind.API_STATUS -> LoadState.Failed(false, "接口返回异常", api.message ?: "请稍后重试。")
        ApiException.Kind.DECODE -> LoadState.Failed(false, "数据解析失败", api.message ?: "响应格式与预期不符。")
        ApiException.Kind.MISSING_CREDENTIAL -> LoadState.Failed(
            false,
            "缺少互动凭据",
            api.message ?: "请先在设置中补充互动安全凭据。",
        )

        else -> LoadState.Failed(false, "加载失败", api.message ?: "请稍后重试。")
    }
}

/** 返回按钮：所有本组页面共用，进入页面时由调用方请求首次焦点。 */
@Composable
internal fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onBack)
            .background(AxTvColors.Surface, RoundedCornerShape(8.dp))
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "‹ 返回",
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
internal fun LoadingPane(modifier: Modifier = Modifier) {
    Text(
        text = HistoryGroupSpec.LABEL_LOADING,
        color = AxTvColors.TextSecondary,
        style = TextStyle(fontSize = 14.sp),
        modifier = modifier.padding(vertical = 12.dp),
    )
}

@Composable
internal fun EmptyPane(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AxTvColors.Surface, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = text,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = "列表为空。",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
    }
}

/** 失败态：标题 + 明细 +（可选）去登录提示 + 重试按钮，保证不会出现白屏。 */
@Composable
internal fun StatusPane(
    failed: LoadState.Failed,
    onAction: () -> Unit,
    actionLabel: String = "重试",
    modifier: Modifier = Modifier,
    onLogin: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AxTvColors.Surface, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = failed.title,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = failed.detail,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 14.sp),
        )
        if (failed.loginRequired) {
            Text(
                text = LOGIN_ACTION_HINT,
                color = AxTvColors.Accent,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(label = actionLabel, onClick = onAction)
            // 未登录时提供可直接到达登录页的入口；无登录需求时不渲染，保持原有布局。
            if (failed.loginRequired && onLogin != null) {
                ActionButton(label = "去登录", onClick = onLogin)
            }
        }
    }
}

@Composable
internal fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(AxTvColors.SurfaceHigh, RoundedCornerShape(8.dp))
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

/** 列表行：封面 + 文案 + 作者 + 互动/进度信息，整行可聚焦点击。 */
@Composable
internal fun AwemeRow(
    aweme: Aweme,
    onClick: (Aweme) -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val cover = coverUrlOf(aweme)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable { onClick(aweme) }
            .background(AxTvColors.Surface, RoundedCornerShape(12.dp))
            .focusRing(focused, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(AxTvColors.SurfaceHigh),
        ) {
            if (cover.isNotEmpty()) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = titleOf(aweme),
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${authorNameOf(aweme)}",
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 13.sp),
            )
            Text(
                text = metaOf(aweme),
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
        }
    }
}

/** 网格卡片：个人页作品使用，宽度由外层 Row 的 weight 决定。 */
@Composable
internal fun AwemeCard(
    aweme: Aweme,
    onClick: (Aweme) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val cover = coverUrlOf(aweme)
    Column(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick(aweme) }
            .background(AxTvColors.Surface, RoundedCornerShape(12.dp))
            .focusRing(focused, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(AxTvColors.SurfaceHigh),
        ) {
            if (cover.isNotEmpty()) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = titleOf(aweme),
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = metaOf(aweme),
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 关注/粉丝列表行：只做展示，可聚焦以便 TV 方向键滚动列表。 */
@Composable
internal fun AuthorRow(author: Author, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .background(AxTvColors.Surface, RoundedCornerShape(12.dp))
            .focusRing(focused, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        UserAvatar(author = author, size = 48.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = author.nickname().ifBlank { HistoryGroupSpec.LABEL_ANONYMOUS_USER },
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = author.signature().ifBlank { "暂无签名" },
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "粉丝 ${DataModels.formatCount(author.followerCount())}",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 12.sp),
        )
    }
}

@Composable
internal fun UserAvatar(author: Author, size: Dp, modifier: Modifier = Modifier) {
    val url = author.bestAvatar().firstUrl()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(AxTvColors.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = author.nickname().take(1).ifBlank { "?" },
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

// ------------------------------------------------------------------ 展示工具

internal fun coverUrlOf(aweme: Aweme): String {
    val video = aweme.video()
    return video.cover().firstUrl()
        .ifEmpty { video.dynamicCover().firstUrl() }
        .ifEmpty { video.originCover().firstUrl() }
}

/** 原版标题截断口径：超过 50 字截断加省略号（refactor/ui/NOTES-on5.md）。 */
internal fun titleOf(aweme: Aweme): String {
    val desc = aweme.desc().trim()
    return when {
        desc.isEmpty() -> DetailPageSpec.TEXT_UNTITLED_VIDEO
        desc.length > 50 -> desc.take(50) + "…"
        else -> desc
    }
}

internal fun authorNameOf(aweme: Aweme): String =
    aweme.author().nickname().ifBlank { HistoryGroupSpec.LABEL_ANONYMOUS_USER }

internal fun metaOf(aweme: Aweme): String {
    val duration = formatDuration(aweme.video().duration().toLong())
    val progress = aweme.playProgress().playProgress()
    val timer = if (progress > 0 && duration.isNotEmpty()) {
        "看到 ${formatDuration(progress)} / $duration"
    } else {
        duration
    }
    val digg = "${DataModels.formatCount(aweme.statistics().diggCount())} 赞"
    return if (timer.isEmpty()) digg else "$digg · $timer"
}

internal fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
