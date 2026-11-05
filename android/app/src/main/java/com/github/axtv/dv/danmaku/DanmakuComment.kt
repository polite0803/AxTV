/*
 * 弹幕数据模型（纯 JVM，不依赖 Android）。
 *
 * 规格出处：
 * - 录播弹幕条目字段：refactor/model/axtv/model/DanmakuItem.java:27-55
 *   （offset_time 为相对视频起点的时间轴，text / danmaku_type / has_emoji / is_ad）
 * - 直播弹幕模型：refactor/live/axtv/live/LiveDanmakuMessage.java:20-72
 * - 时间轴 → 屏幕坐标：refactor/live/axtv/live/DanmakuStyle.java:157-174
 */
package com.github.axtv.dv.danmaku

import java.util.UUID

/**
 * 弹幕展示形态。
 *
 * 证据等级（2026-11-05 取证，见 DanmakuMapper.kt 头注释）：
 * - [SCROLL]：原版唯一实现的形态。原版渲染链（src/app/p000/wo0.java:139-157、
 *   src/app/p000/om5.java、src/app/p000/i42.java:553-608）从不读取
 *   DanmakuItem.danmaku_type / danmaku_type_bits，所有条目一律按滚动处理（证实）。
 * - [TOP] / [BOTTOM]：通用弹幕语义预留值，仅本地发送 API 可传入；映射端
 *   从不产出，调度端回落 [SCROLL]（原版无固定弹幕停留时长的证据值，不许占位）。
 */
enum class DanmakuType {
    SCROLL,
    TOP,
    BOTTOM,
}

/**
 * 一条待渲染弹幕。
 *
 * @param id            去重键（同一接口分批返回时用于防止重复上屏）
 * @param timeMs        相对视频起点的时间（毫秒）；对应 DanmakuItem.offset_time
 * @param text          文本
 * @param colorArgb     文字颜色（ARGB）；原版录播条目未下发颜色，默认白色
 * @param type          展示形态
 * @param fontSizeScale 单条字号倍率（默认 1.0，叠加在全局字号档位上）
 * @param hasEmoji      是否含表情；对应 DanmakuItem.has_emoji
 * @param isLocal       是否为本地发送（占位，无后端）
 */
data class DanmakuComment(
    val id: String,
    val timeMs: Long,
    val text: String,
    val colorArgb: Int = DEFAULT_COLOR_ARGB,
    val type: DanmakuType = DanmakuType.SCROLL,
    val fontSizeScale: Float = 1f,
    val hasEmoji: Boolean = false,
    val isLocal: Boolean = false,
) {
    companion object {
        /** 原版弹幕文字默认白色（截图与播放器弹幕层未见彩色条目）。 */
        const val DEFAULT_COLOR_ARGB: Int = 0xFFFFFFFF.toInt()

        /** 构造一条本地弹幕；id 使用随机 UUID 以避免与接口条目冲突。 */
        fun local(
            text: String,
            timeMs: Long,
            type: DanmakuType = DanmakuType.SCROLL,
            colorArgb: Int = 0xFFFFFFFF.toInt(),
        ): DanmakuComment = DanmakuComment(
            id = "local-" + UUID.randomUUID(),
            timeMs = timeMs,
            text = text,
            colorArgb = colorArgb,
            type = type,
            isLocal = true,
        )
    }
}

/**
 * 某一帧要交给 Canvas 绘制的一条弹幕。
 *
 * @param x            文字左边缘（px）
 * @param baselineY    文字基线（px）
 * @param textWidthPx  文字宽度（px）
 * @param fontSizePx   字号（px）
 * @param alpha        0..255
 */
data class DanmakuRender(
    val id: String,
    val text: String,
    val x: Float,
    val baselineY: Float,
    val textWidthPx: Float,
    val fontSizePx: Float,
    val alpha: Int,
    val colorArgb: Int,
    val type: DanmakuType,
)
