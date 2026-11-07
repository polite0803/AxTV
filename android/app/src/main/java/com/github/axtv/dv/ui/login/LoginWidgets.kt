/*
 * 登录页的控件与小工具（从 LoginScreen.kt 拆出）。
 * 拆分原因: LoginScreen.kt 超 800 行；按钮/验证码键盘只被主页面调用，normalizeCookieInput 为包级复用。
 */
package com.github.axtv.dv.ui.login

import axtv.api.ApiException
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun LoginButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        !enabled -> AxTvColors.SurfaceHigh
        primary -> AxTvColors.Accent.copy(alpha = 0.18f)
        else -> AxTvColors.Surface
    }
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            // 不可用态保留焦点能力（便于 D-pad 逐行走查），但 OK 无动作。
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier.focusable())
            .background(background, RoundedCornerShape(10.dp))
            .focusRing(focused, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> AxTvColors.TextSecondary
                primary -> AxTvColors.Accent
                else -> AxTvColors.TextPrimary
            },
            style = TextStyle(fontSize = 14.sp, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal),
        )
    }
}

/**
 * 解析粘贴的 Cookie 串。
 *
 * 与 refactor/api/axtv/api/SetCookie.java:19-40 同一套「按 ";" 切分、首个 '=' 拆 name/value」规则
 * （SetCookie 只取第一段，这里对每一段都套用）；
 * 该类是包私有（`final class SetCookie`，非 public），Kotlin 侧（不同包）无法直接调用，
 * 故在此按同一算法逐项复刻，并额外剔除 Path/Domain/Expires 等属性项。
 */
internal fun normalizeCookieInput(raw: String): String {
    val cleaned = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .joinToString(";")
        .trim()
        .removePrefix("Cookie:")
        .removePrefix("cookie:")
        .removePrefix("Set-Cookie:")
        .removePrefix("set-cookie:")
        .trim()
    if (cleaned.isEmpty()) return ""
    val pairs = LinkedHashMap<String, String>()
    for (part in cleaned.split(";")) {
        val equals = part.indexOf('=')
        if (equals <= 0) continue
        val name = part.substring(0, equals).trim()
        val value = part.substring(equals + 1).trim()
        if (name.isEmpty()) continue
        if (name.lowercase(Locale.ROOT) in COOKIE_ATTRIBUTES) continue
        pairs[name] = value
    }
    return pairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
}

/** 二维码这一帧距现在多少秒（freshAt 为 0 表示还没拿到帧）。 */
internal fun qrAgeSeconds(freshAtMs: Long, nowMs: Long): Long =
    if (freshAtMs <= 0L) 0L else ((nowMs - freshAtMs).coerceAtLeast(0L)) / 1000L

/** 把 ApiException 转成可读原因（按 Kind 分档，不吞原始信息）。 */
internal fun describe(error: ApiException): String = when (error.kind()) {
    ApiException.Kind.NETWORK -> "网络请求失败，请检查网络后重试（${error.message}）"
    ApiException.Kind.EMPTY_BODY -> "接口返回空响应：Cookie 可能不完整或已失效，请重新复制完整 Cookie"
    ApiException.Kind.LOGIN_REQUIRED -> "需要完整的登录 Cookie（缺少 sessionid / sessionid_ss）"
    ApiException.Kind.RATE_LIMITED -> "请求频率过高，请稍后再试"
    ApiException.Kind.HTTP_STATUS -> "接口返回 HTTP 错误：${error.message}"
    ApiException.Kind.DECODE -> "接口响应解析失败：${error.message}"
    ApiException.Kind.HEADER_ERROR, ApiException.Kind.API_STATUS -> "接口拒绝请求：${error.message}"
    ApiException.Kind.MISSING_CREDENTIAL -> "缺少互动安全凭据：${error.message}"
}

/**
 * 短信验证码数字键盘：风控把扫码转成网页端二次验证、且页面上渲染了验证码输入框时弹出。
 *
 * 为什么不在网页里直接让用户输：电视没有输入法，页面 input 敲不进数字。原版同样是应用侧收码
 *（src/app/p000/in2.java 的数字键盘 + eg1 的注入脚本），收完写回页面 input 再点「验证」——
 * 注入脚本见 [QrLoginBridge.CODE_FILL_SCRIPT] 与 [QrLoginBridge.CODE_CLICK_SCRIPT]。
 * 滑块 / 刷脸这类代填不了的验证不走这里，由 [AndroidQrWebViewHost.setInteractive] 把登录页浮出来。
 */
@Composable
internal fun VerifyCodeKeypad(
    code: String,
    hint: String,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onOpenPage: () -> Unit,
) {
    val firstKeyRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstKeyRequester.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .background(AxTvColors.SurfaceHigh, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "该账号需要安全验证",
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = hint.ifBlank { "请输入手机上收到的短信验证码" },
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 13.sp),
                maxLines = 2,
            )
            Text(
                text = if (code.isEmpty()) "验证码：（尚未输入）" else "验证码：$code",
                color = AxTvColors.Accent,
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
            listOf("123", "456", "789").forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { index, digit ->
                        LoginButton(
                            label = digit.toString(),
                            modifier = if (rowIndex == 0 && index == 0) {
                                Modifier.weight(1f).focusRequester(firstKeyRequester)
                            } else {
                                Modifier.weight(1f)
                            },
                            onClick = { onDigit(digit) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LoginButton(label = "清空", modifier = Modifier.weight(1f), onClick = onClear)
                LoginButton(
                    label = "0",
                    modifier = Modifier.weight(1f),
                    onClick = { onDigit('0') },
                )
                LoginButton(label = "删除", modifier = Modifier.weight(1f), onClick = onDelete)
            }
            LoginButton(
                label = "提交验证码",
                modifier = Modifier.fillMaxWidth(),
                enabled = code.isNotEmpty(),
                primary = true,
                onClick = onSubmit,
            )
            // 页面上的验证如果不只是输入框（比如还要点“获取验证码”或换验证方式），
            // 直接把登录页拿出来操作，比在这个键盘上死磕可靠。
            LoginButton(
                label = "改为在登录页操作",
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenPage,
            )
            Text(
                text = "BACK 退出扫码登录（验证码提交后会自动继续等登录态）",
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
        }
    }
}
