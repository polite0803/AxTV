/*
 * Android 适配层 —— settings：UI 键 → 核心层 Settings 强类型读写。
 *
 * 设置页此前只用 remember 内存状态，未接 Settings，导致重启即回默认。
 * 本类把设置页的代表性项映射到 Settings 的 getter/setter，由 SharedPrefsSettingsStore 落盘。
 * 枚举顺序与 Settings 内部枚举声明顺序一致，UI 选项文案同序。
 */
package com.github.axtv.dv.adapter

import axtv.settings.Settings

class SettingsBinder(private val settings: Settings) {

    fun readBoolean(key: String): Boolean? = when (key) {
        "video_danmaku" -> settings.videoDanmakuEnabled()
        "live_danmaku" -> settings.liveDanmakuEnabled()
        "danmaku_emoji" -> settings.danmakuEmojiEnabled()
        "disable_double_back_exit" -> settings.disableDoubleBackExit()
        "surface_buffer_align" -> settings.surfaceBufferAlign()
        else -> null
    }

    fun writeBoolean(key: String, value: Boolean) {
        when (key) {
            "video_danmaku" -> settings.setVideoDanmakuEnabled(value)
            "live_danmaku" -> settings.setLiveDanmakuEnabled(value)
            "danmaku_emoji" -> settings.setDanmakuEmojiEnabled(value)
            "disable_double_back_exit" -> settings.setDisableDoubleBackExit(value)
            "surface_buffer_align" -> settings.setSurfaceBufferAlign(value)
        }
    }

    /** 枚举项的显示文案；顺序必须与 Settings 内枚举 values() 一致。 */
    fun enumLabels(key: String): List<String>? = when (key) {
        "live_danmaku_style" -> listOf("滚动样式", "列表样式")
        "app_font_scale" -> listOf("标准", "大", "特大", "超大")
        "default_home" -> listOf("推荐", "精选", "直播", "移动推荐")
        "theme_mode" -> listOf("自动", "浅色", "暗色")
        "player_surface_type" -> listOf("SurfaceView", "TextureView", "GL SurfaceView")
        else -> null
    }

    fun readEnumIndex(key: String): Int? = when (key) {
        "live_danmaku_style" ->
            Settings.DanmakuStyle.values().indexOf(settings.liveDanmakuStyle())
        "app_font_scale" ->
            Settings.FontScale.values().indexOf(settings.fontScalePreset())
        "default_home" ->
            Settings.HomeScreen.values().indexOf(settings.defaultHomeScreen())
        "theme_mode" ->
            Settings.ThemeMode.values().indexOf(settings.themeMode())
        "player_surface_type" ->
            Settings.SurfaceType.values().indexOf(settings.surfaceType())
        else -> null
    }

    fun writeEnumIndex(key: String, index: Int) {
        when (key) {
            "live_danmaku_style" ->
                settings.setLiveDanmakuStyle(Settings.DanmakuStyle.values()[index])
            "app_font_scale" ->
                settings.setFontScalePreset(Settings.FontScale.values()[index])
            "default_home" ->
                settings.setDefaultHomeScreen(Settings.HomeScreen.values()[index])
            "theme_mode" ->
                settings.setThemeMode(Settings.ThemeMode.values()[index])
            "player_surface_type" ->
                settings.setSurfaceType(Settings.SurfaceType.values()[index])
        }
    }
}
