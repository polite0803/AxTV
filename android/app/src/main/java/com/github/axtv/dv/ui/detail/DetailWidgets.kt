/*
 * 详情页的子组件（从 DetailScreen.kt 拆出）。
 * 拆分原因: DetailScreen.kt 超 800 行；状态提示/按钮/评论区组件只被主页面调用。
 */
package com.github.axtv.dv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import axtv.model.Author
import axtv.model.Aweme
import axtv.model.Comment
import axtv.ui.CommentUiSpec
import axtv.ui.DetailPageSpec
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing

/** 作者头像：取缩略图首 URL，缺失时为空串（Avatar.firstUrl 已做空列表兜底）。 */
internal fun authorAvatarUrl(author: Author): String = author.avatarThumb().firstUrl()

@Composable
internal fun DetailStateMessage(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, color = AxTvColors.TextPrimary, style = TextStyle(fontSize = 18.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (actionLabel != null && onAction != null) {
                    DetailButton(label = actionLabel, onClick = onAction)
                }
                if (secondaryLabel != null && onSecondary != null) {
                    DetailButton(label = secondaryLabel, onClick = onSecondary)
                }
            }
        }
    }
}

@Composable
internal fun DetailButton(
    label: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    active: Boolean = false,
    enabled: Boolean = true,
    /** 紧凑内边距：详情页六个操作要挤在同一排。 */
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) AxTvColors.Accent.copy(alpha = 0.18f) else AxTvColors.Surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = if (active) AxTvColors.Accent else AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
        if (count != null) {
            Text(
                text = count,
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
        }
    }
}

@Composable
internal fun DetailStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = label,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 11.sp),
        )
    }
}

@Composable
internal fun HintText(text: String) {
    Text(
        text = text,
        color = AxTvColors.TextSecondary,
        style = TextStyle(fontSize = 12.sp),
    )
}

/** 远程图片的加载状态（仅服务详情页占位/兜底）。 */
private enum class DetailImageState { Loading, Success, Error }

/**
 * 详情页图片统一入口：封面、作者头像、相关推荐封面都走这里。
 * 加载中与加载失败都退回 SurfaceHigh 圆角块——颜色/圆角与 ui/home 的卡片占位块一致，
 * 不引入新配色；仅在尺寸足够时叠一行 TextSecondary 小字区分「加载中 / 加载失败」。
 */
@Composable
internal fun DetailRemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    errorLabel: String? = null,
) {
    var state by remember(url) {
        mutableStateOf(if (url.isEmpty()) DetailImageState.Error else DetailImageState.Loading)
    }
    Box(
        modifier = modifier.clip(shape).background(AxTvColors.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { painterState ->
                    state = when (painterState) {
                        is AsyncImagePainter.State.Success -> DetailImageState.Success
                        is AsyncImagePainter.State.Error -> DetailImageState.Error
                        else -> DetailImageState.Loading
                    }
                },
            )
        }
        if (state != DetailImageState.Success && errorLabel != null) {
            Text(
                text = if (state == DetailImageState.Loading) "加载中…" else errorLabel,
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
        }
    }
}

@Composable
internal fun RelatedCard(item: Aweme, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val coverUrl = DetailPageSpec.coverUrl(item.video())
    Column(
        modifier = modifier
            .width(176.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused)
            .clip(RoundedCornerShape(12.dp))
            .background(AxTvColors.Surface)
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DetailRemoteImage(
            url = coverUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(96.dp),
            shape = RoundedCornerShape(8.dp),
            errorLabel = "封面加载失败",
        )
        Text(
            text = DetailPageSpec.cardTitle(item.desc(), item.author().nickname()),
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 12.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun CommentItem(
    comment: Comment,
    isReply: Boolean,
    expanded: Boolean,
    loadingReplies: Boolean,
    onToggleReplies: () -> Unit,
) {
    var focused by remember(comment.cid(), comment.text()) { mutableStateOf(false) }
    val diggLabel = CommentUiSpec.diggLabel(comment)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 28.dp else 0.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isReply) AxTvColors.Surface else AxTvColors.SurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = CommentUiSpec.displayName(comment.user().nickname()),
            color = AxTvColors.Accent,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
        Text(
            text = comment.text(),
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 13.sp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (diggLabel != null) HintText("赞 $diggLabel")
            if (comment.ipLabel().isNotBlank()) HintText(comment.ipLabel())
            if (!isReply && comment.replyCommentTotal() > 0) {
                Text(
                    text = if (expanded) "收起回复" else "展开 ${comment.replyCommentTotal()} 条回复",
                    color = AxTvColors.Accent,
                    style = TextStyle(fontSize = 11.sp),
                    modifier = Modifier
                        .onFocusChanged { focused = it.isFocused }
                        .focusRing(focused, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onToggleReplies)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (loadingReplies) HintText("正在加载回复…")
        }
    }
}
