/*
 * Settings → DanmakuConfig 映射。
 *
 * 只读取已有键，不新增键名。键的常量定义见 refactor/settings/axtv/settings/SettingsKeys.java:65-83，
 * 强类型读取见 refactor/settings/axtv/settings/Settings.java:779-998。
 */
package com.github.axtv.dv.danmaku

import axtv.settings.Settings

/**
 * 弹幕设置读取器。
 *
 * 已使用的键（全部为既有键）：
 * | 键 | Settings 读取方法 |
 * |---|---|
 * | `video_danmaku_enabled` | [Settings.videoDanmakuEnabled] |
 * | `danmaku_opacity` | [Settings.danmakuOpacity] |
 * | `danmaku_area_percent` | [Settings.danmakuAreaPercent] |
 * | `danmaku_vertical_spacing` | [Settings.danmakuVerticalSpacing] |
 * | `danmaku_horizontal_spacing` | [Settings.danmakuHorizontalSpacing] |
 * | `danmaku_font_size` | [Settings.danmakuFontScale] |
 * | `danmaku_speed` | [Settings.danmakuSpeedScale] |
 * | `danmaku_block_enabled` | [Settings.danmakuBlockEnabled] |
 * | `danmaku_block_words` | [Settings.danmakuBlockWords] |
 * | `danmaku_filter_package` | [Settings.danmakuFilterPackage]（直播专用，录播浮层不消费） |
 * | `danmaku_emoji_enabled` | [Settings.danmakuEmojiEnabled] |
 */
class DanmakuSettingsBinder(private val settings: Settings) {

    fun read(): DanmakuConfig = DanmakuConfig(
        enabled = settings.videoDanmakuEnabled(),
        opacity = settings.danmakuOpacity(),
        areaPercent = settings.danmakuAreaPercent(),
        verticalSpacing = settings.danmakuVerticalSpacing(),
        horizontalSpacing = settings.danmakuHorizontalSpacing(),
        fontScale = settings.danmakuFontScale(),
        speedScale = settings.danmakuSpeedScale(),
        blockEnabled = settings.danmakuBlockEnabled(),
        blockWords = settings.danmakuBlockWords(),
        filterPackage = settings.danmakuFilterPackage(),
        emojiEnabled = settings.danmakuEmojiEnabled(),
    ).normalized()
}
