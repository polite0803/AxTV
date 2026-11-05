/*
 * 弹幕屏蔽词过滤（纯 JVM）。
 *
 * 证据：
 * - 屏蔽词列表来自 Settings.danmakuBlockWords()：refactor/settings/axtv/settings/Settings.java:926-948
 *   （JSON 数组 → trim → 去空 → 保序去重），写入时已归一化。
 * - 开关 danmaku_block_enabled 默认 false：Settings.java:917-924。
 * - 原版过滤算法本身没有落到 refactor/（DanmakuStyle 只做几何），本实现按「忽略大小写的包含匹配」，
 *   与原版 addDanmakuBlockWord 的去重口径一致（Settings.java:950-966）。
 * - 正则：refactor/ 内没有任何正则屏蔽的证据，故不实现，标记为「待补」。
 */
package com.github.axtv.dv.danmaku

object DanmakuFilter {

    /**
     * 是否允许上屏。
     *
     * 规则：
     * 1. 空文本不上屏；
     * 2. emoji 开关关闭时，[DanmakuComment.hasEmoji] 为真的条目不上屏（推断，原版 UI 只有开关）；
     * 3. 屏蔽总开关打开时，文本包含任一屏蔽词（忽略大小写）即不上屏。
     *
     * 说明：`danmaku_filter_package`（粉丝团/等级包）只对直播 chatBy 字段有意义
     * （refactor/live/axtv/live/LiveDanmakuSettings.java:149-152），录播条目没有该字段，
     * 因此本过滤器不使用 [DanmakuConfig.filterPackage]，该开关保留给直播弹幕线。
     */
    fun accepts(comment: DanmakuComment, config: DanmakuConfig): Boolean {
        if (comment.text.isBlank()) {
            return false
        }
        if (comment.hasEmoji && !config.emojiEnabled) {
            return false
        }
        if (!config.blockEnabled || config.blockWords.isEmpty()) {
            return true
        }
        return config.blockWords.none { word -> comment.text.contains(word, ignoreCase = true) }
    }
}
