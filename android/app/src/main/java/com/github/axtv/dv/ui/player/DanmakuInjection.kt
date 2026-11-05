/*
 * 播放器弹幕接线（可选数据源之一）：
 * axtv.api.VideoApi.danmaku(itemId, groupId, duration) → DanmakuResponse.danmakuList
 * → com.github.axtv.dv.danmaku.DanmakuComment → 已有的 DanmakuOverlayHost。
 *
 * 注意：PlayerOverlayScreen 的默认数据源已改为「点播评论 → 弹幕」
 * （见 CommentDanmakuInjection.kt）；本文件的 loadDanmakuComments 保留为显式覆盖入口，
 * 需要录播弹幕接口时再传入。
 *
 * 接线方式（不改动 PlayerOverlayScreen 之外的调用方）：
 *   PlayerOverlayScreen(
 *       playlistProvider = provider,
 *       danmakuCommentsLoader = { itemId, durationMs ->
 *           loadDanmakuComments(AppRuntime.api, itemId, durationMs)
 *       },
 *   )
 * PlayerOverlayScreen 会按当前播放条目重新调用 loader，把结果传给 DanmakuOverlayHost；
 * 未设置 loader 时：静态 danmakuComments 非空则沿用原行为，否则自动走点播评论默认链路。
 *
 * 弹幕开关/样式：读取既有设置键（DanmakuSettingsBinder，键名见 refactor/settings/axtv/settings/Settings.java），
 * 不新增键。rememberDanmakuConfig 在进入播放页时快照一次，播放中没有监听设置变更（未验证项）。
 */
package com.github.axtv.dv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import axtv.api.DouyinApi
import axtv.api.VideoApi
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.danmaku.DanmakuComment
import com.github.axtv.dv.danmaku.DanmakuConfig
import com.github.axtv.dv.danmaku.DanmakuSettingsBinder
import com.github.axtv.dv.danmaku.toDanmakuComments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 拉取并映射一条视频的全部录播弹幕。
 *
 * 入参与 VideoApi.danmaku(itemId, groupId, durationMs) 一致；groupId 为 null 时由 VideoApi
 * 回退到 itemId（refactor/api/axtv/api/VideoApi.java 的 danmaku 说明）。
 * 网络/解析失败直接抛出 ApiException，由调用方决定是否静默降级。
 *
 * @param durationMs 视频时长（毫秒），同时作为接口的 end_time；&le; 0 或 id 为空时直接返回空列表，
 *                   不发请求。
 */
suspend fun loadDanmakuComments(
    douyinApi: DouyinApi?,
    awemeId: String,
    durationMs: Long,
    groupId: String? = null,
): List<DanmakuComment> {
    if (douyinApi == null || awemeId.isBlank() || durationMs <= 0L) {
        return emptyList()
    }
    val response = withContext(Dispatchers.IO) {
        VideoApi(douyinApi).danmaku(awemeId, groupId, durationMs)
    }
    return response.danmakuList()?.toDanmakuComments().orEmpty()
}

/**
 * 弹幕配置快照：开关/字号/速度/透明度/显示区域全部来自既有 DanmakuSettingsBinder。
 * AppRuntime.settings 未安装时回退 DanmakuConfig 默认值（默认开启弹幕）。
 */
@Composable
fun rememberDanmakuConfig(): DanmakuConfig {
    val settings = AppRuntime.settings
    return remember(settings) {
        settings?.let { DanmakuSettingsBinder(it).read() } ?: DanmakuConfig()
    }
}
