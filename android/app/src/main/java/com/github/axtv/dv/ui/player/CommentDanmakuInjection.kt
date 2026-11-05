/*
 * 点播评论 → 弹幕的取数与降级入口。
 *
 * 链路：
 *   InteractionApi.comments(awemeId, cursor, count) → CommentListResponse.comments
 *   → CommentDanmakuMapper（时间轴 / 去重 / 限流）→ DanmakuComment
 *   → PlayerOverlayScreen → DanmakuOverlayHost → DanmakuEngine。
 *
 * 降级口径（不打断播放）：
 * - 接口未装配（AppRuntime.api == null）、未登录、awemeId 为空、时长未知 → 直接返回空表，不发请求；
 * - 请求失败 / 解析失败 / status_code != 0 → 捕获后返回空表，只丢弹幕，不向播放层抛异常；
 * - 只有协程取消会继续向上抛，避免把「切条目」误判成失败。
 *
 * 评论的登录态判定与 ui/library/LibraryCommon.kt:78-83 一致：
 * Cookie 里存在非空 sessionid / sessionid_ss，或设置页记录的登录态为 true。
 *
 * 只读接口、不写任何设置键：弹幕开关 / 透明度 / 字号 / 速度 / 屏蔽词仍由
 * DanmakuSettingsBinder → DanmakuConfig 控制（见 DanmakuInjection.kt）。
 */
package com.github.axtv.dv.ui.player

import axtv.api.CookieStore
import axtv.api.DouyinApi
import axtv.api.InteractionApi
import axtv.model.Comment
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.danmaku.CommentDanmakuPolicy
import com.github.axtv.dv.danmaku.DanmakuComment
import com.github.axtv.dv.danmaku.toDanmakuComments
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 拉取当前条目的一级评论并转换为弹幕时间轴。
 *
 * @param douyinApi 已装配的接口门面；null 时返回空表。
 * @param awemeId   当前播放条目的作品 id（与 PlaylistItem.id 一致）。
 * @param durationMs 视频时长（毫秒）；≤ 0 时不发请求（等待播放器回报时长后重试）。
 */
suspend fun loadCommentDanmakuComments(
    douyinApi: DouyinApi?,
    awemeId: String,
    durationMs: Long,
    pageSize: Int = CommentDanmakuPolicy.PAGE_SIZE,
    maxPages: Int = CommentDanmakuPolicy.MAX_PAGES,
): List<DanmakuComment> {
    if (douyinApi == null || awemeId.isBlank() || durationMs <= 0L) {
        return emptyList()
    }
    if (!isLoggedIn(douyinApi.cookies())) {
        return emptyList()
    }
    return try {
        val comments = withContext(Dispatchers.IO) {
            fetchComments(douyinApi, awemeId, pageSize.coerceAtLeast(1), maxPages.coerceAtLeast(1))
        }
        comments.toDanmakuComments(durationMs)
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Throwable) {
        emptyList()
    }
}

/** 顺序翻页；游标不前进或接口声明没有下一页时停止，防止死循环。 */
private fun fetchComments(
    douyinApi: DouyinApi,
    awemeId: String,
    pageSize: Int,
    maxPages: Int,
): List<Comment> {
    val api = InteractionApi(douyinApi)
    val collected = ArrayList<Comment>(pageSize * maxPages)
    var cursor = 0L
    var page = 0
    while (page < maxPages && collected.size < CommentDanmakuPolicy.MAX_COMMENTS) {
        val response = api.comments(awemeId, cursor, pageSize)
        val pageComments = response.comments().orEmpty()
        if (pageComments.isEmpty()) {
            break
        }
        collected.addAll(pageComments)
        if (!response.hasMorePages()) {
            break
        }
        val nextCursor = response.cursor()
        if (nextCursor <= cursor) {
            break
        }
        cursor = nextCursor
        page++
    }
    return collected
}

/** 与设置页 / 组件页共用的登录态口径；不再重复发起请求。 */
private fun isLoggedIn(cookies: CookieStore): Boolean {
    val jar = cookies.cookies()
    val cookieLoggedIn = jar["sessionid"]?.isNotBlank() == true ||
        jar["sessionid_ss"]?.isNotBlank() == true
    return cookieLoggedIn || AppRuntime.settings?.isLoggedIn == true
}
