/*
 * 搜索页的输入栏与搜索前列表行（从 SearchScreen.kt 拆出）。
 * 拆分原因: SearchScreen.kt 超 800 行；这些组件只被主页面调用，无独立状态。
 */
package com.github.axtv.dv.ui.search

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing

// ------------------------------------------------------------------ 输入栏

@Composable
internal fun SearchBar(
    field: TextFieldValue,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onFieldChange: (TextFieldValue) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    requester: FocusRequester,
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AxTvColors.Surface, RoundedCornerShape(12.dp))
            .focusRing(focused, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BarChip("返回", onBack)

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            BasicTextField(
                value = field,
                onValueChange = onFieldChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(requester)
                    .onFocusChanged { onFocusChange(it.isFocused) }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        when {
                            // 上下键直接跳出输入框，交给下方的 tab / 热搜列表。
                            event.key == Key.DirectionDown ->
                                focusManager.moveFocus(FocusDirection.Down)

                            event.key == Key.DirectionUp ->
                                focusManager.moveFocus(FocusDirection.Up)

                            // 光标已在最右侧时，右方向键交给「搜索」按钮。
                            event.key == Key.DirectionRight &&
                                field.selection.collapsed &&
                                field.selection.end >= field.text.length ->
                                focusManager.moveFocus(FocusDirection.Right)

                            // 光标已在最左侧时，左方向键交给「返回」按钮。
                            event.key == Key.DirectionLeft &&
                                field.selection.collapsed &&
                                field.selection.start <= 0 ->
                                focusManager.moveFocus(FocusDirection.Left)

                            event.key == Key.Enter -> {
                                onSubmit()
                                true
                            }

                            else -> false
                        }
                    }
                    .padding(vertical = 10.dp),
                singleLine = true,
                textStyle = TextStyle(color = AxTvColors.TextPrimary, fontSize = 18.sp),
                cursorBrush = SolidColor(AxTvColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (field.text.isEmpty()) {
                            Text(
                                text = "搜索视频 / 用户 / 直播",
                                color = AxTvColors.TextSecondary,
                                style = TextStyle(fontSize = 16.sp),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        if (field.text.isNotEmpty()) {
            BarChip("清空", onClear)
        }
        BarChip("搜索", onSubmit)
    }
}

@Composable
internal fun BarChip(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(AxTvColors.SurfaceHigh, RoundedCornerShape(8.dp))
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
internal fun TabChip(
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
                color = if (selected) AxTvColors.Accent.copy(alpha = 0.16f) else AxTvColors.Surface,
                shape = RoundedCornerShape(8.dp),
            )
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = when {
                selected -> AxTvColors.TextPrimary
                focused -> AxTvColors.Accent
                else -> AxTvColors.TextSecondary
            },
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
        )
    }
}

// ------------------------------------------------------------------ 搜索前列表

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        color = AxTvColors.TextSecondary,
        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
internal fun HotWordRow(position: Int, word: String, hotValue: Long, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(AxTvColors.Surface, RoundedCornerShape(10.dp))
            .focusRing(focused, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = position.toString().padStart(2, '0'),
            color = if (focused) AxTvColors.Accent else AxTvColors.AccentWarm,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = word,
            modifier = Modifier.weight(1f),
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 15.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "热度 ${formatCount(hotValue)}",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 12.sp),
        )
    }
}

@Composable
internal fun SuggestionRow(word: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) AxTvColors.SurfaceHigh else AxTvColors.Surface, RoundedCornerShape(8.dp))
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = word,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 15.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 搜索历史 / 「清空历史」标签：D-pad 可聚焦，Enter 触发。 */
@Composable
internal fun HistoryChip(label: String, accent: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) AxTvColors.SurfaceHigh else AxTvColors.Surface, RoundedCornerShape(18.dp))
            .focusRing(focused, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                accent -> AxTvColors.AccentWarm
                focused -> AxTvColors.Accent
                else -> AxTvColors.TextPrimary
            },
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = if (accent) FontWeight.Medium else FontWeight.Normal,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
