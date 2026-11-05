/*
 * 录播弹幕映射：axtv.model.DanmakuItem → 浮层条目 DanmakuComment。
 *
 * 字段对照与证据等级：
 * | DanmakuItem 字段（JSON key）        | DanmakuComment 字段 | 证据 |
 * |---|---|---|
 * | danmaku_id                          | id                  | 实证字段；非空直接复用，空则按 item_id+offset_time+下标合成（空 id 的兜底策略为推断） |
 * | offset_time                         | timeMs              | 实证字段，DanmakuItem.java:12 注释明确单位为「相对视频起点的毫秒」 |
 * | text                                | text                | 实证字段（DanmakuItem.java:13） |
 * | has_emoji                           | hasEmoji            | 实证字段；布尔由 ModelJsonCodec→FlexibleJson.toBoolean 解码，仅 "true"/"1" 为 true |
 * | （无）                               | colorArgb           | 原版录播条目未下发颜色，沿用 DanmakuComment 默认白色 |
 * | （无）                               | fontSizeScale       | 恒 1.0，字号由全局 DanmakuConfig 控制 |
 * | （无）                               | isLocal             | 恒 false（接口条目不是本地发送） |
 *
 * danmaku_type / danmaku_type_bits 取证结论（2026-11-05，全部为反编译实证）：
 * - 字段定义：src/app/com/github/mytv/p004dv/model/DanmakuItem.java:22-23（默认值 0：
 *   380-382、405-407）；JSON 键名仅出现在序列化器 DanmakuItem$$serializer.java:41、46。
 * - 全 src/ 搜索 getDanmakuType( / getDanmakuTypeBits(：只有定义、无任何调用点。
 * - 原版渲染链全部消费点（src/app/p000/）：wo0.java:139-157（轨道布局，只读
 *   offsetTime/text/status/isAd/danmakuId）、om5.java:160-380（加载合并，只按
 *   offset_time 窗口与 danmaku_id 去重）、i42.java:553-608（Canvas 绘制，只读
 *   offsetTime 与已放置条目 uo0 的几何字段）、wx5.java:1305-1324（直播侧构造
 *   DanmakuItem 时 danmaku_type/danmaku_type_bits 恒传 0）、xi1.java:161,187、
 *   i42.java:556、wx5.java:1314,1322（排序/步进，只读 offsetTime）。
 * - 原版布局参数 to0.java（displayArea/lineHeight/padding/horizontalGap/durationMs/
 *   speedReferenceWidth，1-32 行）与已放置条目 uo0.java（7-67 行）均无类型/固定弹幕字段；
 *   唯一时长常量是 i42.java:387 的 f7 = 8000.0f / speed（滚动弹幕）。
 *
 * 因此：
 * - danmaku_type 取值语义【未知】（原版从不消费，无从得知 1/2 等值对应何形态）；
 * - danmaku_type_bits【不参与分类】（无任何读取点，证实）；
 * - 映射端把所有条目（无论 danmaku_type 为何值）录为 DanmakuType.SCROLL，与原版
 *   「全按滚动渲染」的行为一致（证实）。若未来拿到服务端字段文档，只需改本函数。
 */
package com.github.axtv.dv.danmaku

import axtv.model.DanmakuItem

/**
 * 单条映射。
 *
 * @param index 该条目在接口返回列表中的下标，仅在 danmaku_id 为空时参与合成 id，
 *              保证 Engine.setItems 的 distinctBy(id) 不会把空 id 条目折叠成一条。
 */
fun DanmakuItem.toDanmakuComment(index: Int): DanmakuComment {
    val rawId = danmakuId()
    return DanmakuComment(
        id = if (rawId.isNotBlank()) rawId else "dm-${itemId()}-${offsetTime()}-$index",
        timeMs = offsetTime().coerceAtLeast(0L),
        text = text(),
        colorArgb = DanmakuComment.DEFAULT_COLOR_ARGB,
        // danmaku_type / danmaku_type_bits 语义未知（原版无消费点），一律回落滚动，
        // 见本文件头「取证结论」。
        type = DanmakuType.SCROLL,
        fontSizeScale = 1f,
        hasEmoji = hasEmoji(),
        isLocal = false,
    )
}

/**
 * 整片映射。空文本条目跳过（空弹幕无绘制意义；原版是否过滤未见证据，属实现层推断）。
 */
fun List<DanmakuItem>.toDanmakuComments(): List<DanmakuComment> =
    mapIndexedNotNull { index, item ->
        item.toDanmakuComment(index).takeIf { it.text.isNotBlank() }
    }
