/*
 * 线 C —— 直播模块：数据装配（UI 侧薄封装，只读调用 axtv.api.LiveApi）。
 *
 * 规格出处：
 * - refactor/ui/axtv/ui/DetailPageSpec.java（直播间卡片槽位、resolvedRoomId、
 *   Section/LiveRoomCard/viewerLine）
 * - refactor/ui/axtv/ui/LivePageSpec.java（在线人数标签、错误覆盖层文案）
 * - refactor/ui/axtv/ui/LiveBrowseSpec.java（TEXT_LIVE_BADGE）
 * - refactor/live/axtv/live/LiveStreamResolver.java（选流顺序）
 *
 * 本文件只做三件事：
 * 1. 把接口模型映射成 UI 卡片；
 * 2. 在 IO 线程执行阻塞的 LiveApi 调用（沿用 HomeFeedRepository 的写法）；
 * 3. 把 ApiException 翻译成可读的错误文案（含接口身份提示）。
 */
package com.github.axtv.dv.ui.live

import axtv.api.ApiException
import axtv.api.ClientIdentityBranch
import axtv.api.LiveApi
import axtv.live.LiveQualityPreference
import axtv.live.LiveStreamInfo
import axtv.live.LiveStreamResolver
import axtv.live.LiveStreamQuality
import axtv.model.LiveFeedResponse
import axtv.model.LiveFeedResponseHome
import axtv.model.LiveRoom
import axtv.model.LiveRoomUtils
import axtv.settings.Settings
import axtv.settings.SettingsTypes
import axtv.ui.DetailPageSpec
import com.github.axtv.dv.adapter.AppRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 单次请求条数（与原版直播列表首屏量级一致，不引入分页参数）。 */
private const val PAGE_SIZE = 20

/**
 * 直播列表 Tab。
 *
 * 注：分区 id / partition_type 尚未从反编译产物取证到确切值，集中放在这里，
 * 后续只需改这一张表；推荐与关注不依赖分区 id。
 */
internal enum class LiveTab(val label: String, val partitionId: String?) {
    FEED("推荐", null),
    FOLLOW("关注", null),
    HOT("热门", "1"),
    GAME("游戏", "2"),
    ENTERTAINMENT("娱乐", "3"),
}

/** 列表卡片的显示槽位（对应 DetailPageSpec.LiveRoomCard 的可显示字段）。 */
internal data class LiveCardUi(
    val roomId: String,
    val webRid: String,
    val title: String,
    val ownerName: String,
    val avatarUrl: String,
    val coverUrl: String,
    val viewerCount: String,
    val isLive: Boolean,
)

internal object LiveRepository {

    /** 直播推荐 / 关注 / 分区列表。失败原样抛出，由 UI 展示可重试的错误态。 */
    suspend fun rooms(tab: LiveTab): Result<List<LiveCardUi>> = withContext(Dispatchers.IO) {
        try {
            val api = AppRuntime.api
                ?: return@withContext Result.failure(
                    IllegalStateException("接口组件尚未就绪，请重启应用后重试"),
                )
            val liveApi = LiveApi(api)
            val rooms = when (tab) {
                LiveTab.FEED -> liveApi.feed(PAGE_SIZE, 0L, "").toCards()
                LiveTab.FOLLOW -> liveApi.followFeed("").toCards()
                else -> liveApi.partitionRooms(tab.partitionId.orEmpty(), PARTITION_TYPE, 0, PAGE_SIZE)
                    .toCards()
            }
            Result.success(rooms)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * 进入直播间。原版 op2 的顺序：有 webRid 时先走 enterRoom（推流地址主入口），
     * 拿不到可用房间再退回 roomInfoByScene（按场景取房间信息）；没有 webRid 就直接走
     * roomInfoByScene。enterRoom 只认 web_rid，传数字 room_id 会被服务端判参数错误。
     *
     * @param roomId 解析后的房间 id（卡片上的 resolvedRoomId），供 info_by_scene 使用
     * @param webRid 列表接口下发的 web_rid；空串表示调用方没有（小组件 deep link 等）
     *
     * 返回 Result.success(null) 表示两条链路都成功但接口没给房间（例如已下播）。
     */
    suspend fun room(roomId: String, webRid: String = ""): Result<LiveRoom?> = withContext(Dispatchers.IO) {
        var failure: Throwable? = null
        try {
            val api = AppRuntime.api
                ?: return@withContext Result.failure(
                    IllegalStateException("接口组件尚未就绪，请重启应用后重试"),
                )
            val liveApi = LiveApi(api)
            // 实测（2026-11-06）：/webcast/room/info_by_scene/ 是当前唯一稳定的取房间接口，
            // 传数字 id（列表里的 id_str）返回 data=房间对象；旧的 enter 接口现在返回空响应体。
            // 所以顺序反过来：先 info_by_scene，进房接口只做兜底。
            val sceneIds = listOf(roomId.trim(), webRid.trim()).filter { it.isNotEmpty() }.distinct()
            for (id in sceneIds) {
                try {
                    val room = liveApi.roomInfoByScene(id).room()
                    if (room != null) {
                        return@withContext Result.success(room)
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (t: Throwable) {
                    if (failure == null) failure = t
                }
            }
            if (webRid.isNotBlank()) {
                try {
                    val fromEnter = liveApi.enterRoom(webRid).firstRoom()
                    if (fromEnter != null) {
                        return@withContext Result.success(fromEnter)
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (t: Throwable) {
                    if (failure == null) failure = t
                }
            }
            if (failure != null) {
                Result.failure(failure)
            } else {
                Result.success(null)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

/** partition_type 取值沿用原接口的 web 分区（1）；未能从证据里找到第二取值。 */
private const val PARTITION_TYPE = 1

internal fun LiveFeedResponse.toCards(): List<LiveCardUi> =
    data()?.data().orEmpty()
        // web_rid 在外层条目上，房间对象自身通常不带；丢了它就会拿数字 id 去进房（服务端判参数错误）。
        .mapNotNull { item -> item?.room()?.let { room -> item.webRid() to room } }
        .distinctBy { DetailPageSpec.resolvedRoomId(it.second) }
        .mapIndexed { index, pair -> pair.second.toCard(index, pair.first) }

internal fun LiveFeedResponseHome.toCards(): List<LiveCardUi> =
    data().orEmpty()
        .mapNotNull { item -> item?.data()?.let { room -> item.webRid() to room } }
        .distinctBy { DetailPageSpec.resolvedRoomId(it.second) }
        .mapIndexed { index, pair -> pair.second.toCard(index, pair.first) }

private fun LiveRoom.toCard(index: Int, webRidFromList: String = ""): LiveCardUi = LiveCardUi(
    roomId = DetailPageSpec.resolvedRoomId(this),
    webRid = webRidFromList.ifBlank { effectiveWebRid() },
    title = DetailPageSpec.programName(title(), false) ?: DetailPageSpec.TEXT_NO_PROGRAM,
    ownerName = DetailPageSpec.ownerName(owner().nickname(), index),
    avatarUrl = owner().bestAvatar().firstUrl(),
    coverUrl = cover().firstUrl(),
    viewerCount = LiveRoomUtils.displayViewerCount(this),
    // 原版直播卡片（he4.m5084b）无条件渲染 "LIVE"：列表只装正在直播的房间，
    // 且 webcast 列表下发的是精简房间对象（status 恒为 0），不能拿它判开播。
    isLive = true,
)

/**
 * 拉流候选地址：先按原版选流顺序（FULL_HD1 → HD1 → SD2 → SD1 → 键并集）挑一档，
 * 再用 flv/hls 全部取值做兜底，供失败重试时切下一个地址。
 * 挑哪一档由设置页『直播清晰度』决定（原实现硬编码 AUTO，所以那 4 行点了没反应）。
 */
internal fun LiveRoom.streamCandidates(): List<String> {
    val streamUrl = streamUrl() ?: return emptyList()
    val flv = streamUrl.flvPullUrl().orEmpty()
    val hls = streamUrl.hlsPullUrlMap().orEmpty()
    val info = LiveStreamInfo(
        flv,
        hls,
        emptyList<LiveStreamQuality>(),
        emptyMap<String, List<String>>(),
    )
    val primary = LiveStreamResolver
        .resolveWithFallback(info, AppRuntime.settings.liveQualityPreference())
        ?.urls()
        .orEmpty()
    return (primary + flv.values + hls.values)
        .filter { it.isNotBlank() }
        .distinct()
}

/**
 * 『直播清晰度』→ 取流偏好。
 *
 * 直播没有「自动」档（qm4 遍历时就过滤掉了 AUTO），所以 AUTO 与 HIGHEST 都取最高；
 * 流畅 540p 在核心层枚举里叫 P540。settings 为 null 时保持历史的 AUTO 行为。
 */
private fun Settings?.liveQualityPreference(): LiveQualityPreference = when (this?.liveVideoResolution()) {
    SettingsTypes.VideoResolution.P1080 -> LiveQualityPreference.P1080
    SettingsTypes.VideoResolution.P720 -> LiveQualityPreference.P720
    SettingsTypes.VideoResolution.P480 -> LiveQualityPreference.P540
    else -> LiveQualityPreference.HIGHEST
}

/** 把 ApiException 的种类翻译成可读文案；无法归类时保留原始 message。 */
internal fun describeLiveError(t: Throwable, branch: ClientIdentityBranch?): String {
    val base = when (t) {
        is ApiException -> when (t.kind()) {
            ApiException.Kind.NETWORK -> "网络请求失败：${t.message.orEmpty()}"
            ApiException.Kind.HTTP_STATUS -> "服务端返回 HTTP ${t.httpStatus()}"
            ApiException.Kind.EMPTY_BODY -> "空响应：签名参数失效、Cookie 已过期，或该接口拒绝了当前客户端身份"
            ApiException.Kind.HEADER_ERROR -> "请求被拦截：${t.message.orEmpty()}"
            ApiException.Kind.API_STATUS -> "接口状态异常：status_code=${t.apiStatus()}"
            ApiException.Kind.LOGIN_REQUIRED -> "需要登录：${t.message.orEmpty()}"
            ApiException.Kind.RATE_LIMITED -> "请求过于频繁：${t.message.orEmpty()}"
            ApiException.Kind.DECODE -> "响应解析失败：${t.message.orEmpty()}"
            ApiException.Kind.MISSING_CREDENTIAL -> "缺少本地交互凭据：${t.message.orEmpty()}"
        }

        else -> t.message ?: t.javaClass.simpleName
    }
    return if (isBusinessRejection(t) && branch != null && branch != ClientIdentityBranch.WEBCAST) {
        base + "\n" + identityHint(branch)
    } else {
        base
    }
}

/**
 * 直播接口在原版使用 Webcast 身份；其它身份很可能拿到业务错误。
 * 这里只提示，不偷偷替换 UA（UA 始终由统一的 UserAgentProvider 按设置分支生成）。
 */
internal fun identityHint(branch: ClientIdentityBranch): String =
    "当前接口身份为「${branch.displayName}」，直播接口可能被拒绝，" +
        "可在设置「接口身份 → 客户端身份分支」切换为 Webcast（重启后生效）。"

private fun isBusinessRejection(t: Throwable): Boolean = when (t) {
    is ApiException -> when (t.kind()) {
        ApiException.Kind.API_STATUS,
        ApiException.Kind.HEADER_ERROR,
        ApiException.Kind.EMPTY_BODY,
        ApiException.Kind.LOGIN_REQUIRED,
        ApiException.Kind.RATE_LIMITED,
        ApiException.Kind.DECODE,
        -> true

        else -> false
    }

    else -> false
}
