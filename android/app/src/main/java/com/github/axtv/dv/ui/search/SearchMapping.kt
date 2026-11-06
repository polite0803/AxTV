/*
 * 搜索页的模型映射与文案格式化（从 SearchState.kt 拆出）。
 * 拆分原因: SearchState.kt 超 800 行；映射/格式化是纯函数，与状态机无耦合。
 */
package com.github.axtv.dv.ui.search

import axtv.api.ApiException
import axtv.model.Author
import axtv.model.SearchLiveLives
import axtv.model.SearchVideoWorkData
import axtv.ui.FeedPageSpec
import axtv.ui.SearchUiSpec
import java.util.Locale

// ------------------------------------------------------------------ 模型映射

internal fun SearchVideoWorkData.toVideoResult(): VideoResult? {
    val aweme = awemeInfo()
    val id = aweme.awemeId()
    if (id.isEmpty()) {
        return null
    }
    val video = aweme.video()
    val cover = video.cover().firstUrl()
        .ifBlank { video.dynamicCover().firstUrl() }
        .ifBlank { video.originCover().firstUrl() }
    return VideoResult(
        id = id,
        title = aweme.desc().ifBlank { FeedPageSpec.TEXT_UNTITLED_VIDEO },
        author = aweme.author().nickname().takeIf { it.isNotBlank() }?.let { "@$it" } ?: "未知作者",
        coverUrl = cover,
        durationMs = video.duration(),
    )
}

internal fun Author.toUserResult(): UserResult? {
    val id = uid().ifBlank { secUid() }
    if (id.isEmpty()) {
        return null
    }
    return UserResult(
        id = id,
        name = nickname().ifBlank { "未知用户" },
        signature = signature(),
        avatarUrl = bestAvatar().firstUrl(),
        followerCount = followerCount(),
    )
}

internal fun SearchLiveLives.toLiveResult(): LiveResult? {
    if (awemeId().isEmpty()) {
        return null
    }
    return LiveResult(
        id = awemeId(),
        name = author().nickname().ifBlank { "未知主播" },
        avatarUrl = author().bestAvatar().firstUrl(),
    )
}

// ------------------------------------------------------------------ 文案 / 格式化

/**
 * 把 ApiException.Kind 翻译成用户可读文案；业务码仍交给 SearchUiSpec（逐字规格）。
 * 非 ApiException 保留原始 message，避免吞掉装配错误等本地异常。
 */
internal fun describeSearchError(t: Throwable, channelPrefix: String = ""): String = when (t) {
    is ApiException -> when (t.kind()) {
        ApiException.Kind.NETWORK -> "网络连接失败，请检查网络后重试"
        ApiException.Kind.HTTP_STATUS -> "服务端返回异常（HTTP ${t.httpStatus()}），请稍后重试"
        ApiException.Kind.EMPTY_BODY ->
            "服务器返回空响应，可能是签名失效或 Cookie 过期，请在设置中更新 Cookie"
        ApiException.Kind.HEADER_ERROR -> "请求被服务端拦截，请稍后重试"
        ApiException.Kind.API_STATUS ->
            SearchUiSpec.errorLabel(channelPrefix, t.apiStatus().toString(), t.message.orEmpty())
        ApiException.Kind.LOGIN_REQUIRED -> "搜索需要登录态，请更新 Cookie"
        ApiException.Kind.RATE_LIMITED -> "请求过于频繁，请稍后重试"
        ApiException.Kind.MISSING_CREDENTIAL -> "缺少互动安全凭据，请在设置中补充"
        ApiException.Kind.DECODE -> "搜索结果解析失败，请稍后重试"
    }

    else -> {
        val message = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
        when (channelPrefix) {
            SearchUiSpec.CHANNEL_USER -> SearchUiSpec.channelErrorLabel(true, message)
            SearchUiSpec.CHANNEL_LIVE -> SearchUiSpec.channelErrorLabel(false, message)
            else -> "搜索失败: $message"
        }
    }
}

/** 与首页一致的计数缩写：1.2万 / 1.0亿。 */
internal fun formatCount(value: Long): String = when {
    value >= 100_000_000L -> String.format(Locale.US, "%.1f亿", value / 100_000_000.0)
    value >= 10_000L -> String.format(Locale.US, "%.1f万", value / 10_000.0)
    else -> value.toString()
}

/** 毫秒 → mm:ss / h:mm:ss。 */
internal fun formatDuration(durationMs: Int): String {
    val totalSeconds = (durationMs.toLong() / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
