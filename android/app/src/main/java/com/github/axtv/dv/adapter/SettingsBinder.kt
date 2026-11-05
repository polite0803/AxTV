/*
 * Android 适配层 —— settings：UI 键 → 核心层 Settings 强类型读写。
 *
 * 设置页全量 62 项 + 弹幕样式面板 15 项共用本映射表；未登记的键返回 null，由 UI 回落为静态展示。
 * 枚举顺序与 Settings 内部枚举声明顺序一致，UI 选项文案同序。
 *
 * 面板总开关 `danmaku` 在 j16 里写的是 LiveDanmakuSettings.enabled，
 * 对应核心层 `live_danmaku_enabled`（无独立总开关写入方），因此与 `live_danmaku` 同源。
 *
 * 动态枚举行（画质/编码/形态/取色来源）使用不落盘的「基名」作为 UI 键：
 * `video_resolution` / `live_video_resolution` / `video_codec` / `form_factor` / `color_source`。
 */
package com.github.axtv.dv.adapter

import axtv.api.ClientIdentityBranch
import axtv.settings.Settings
import com.github.axtv.dv.ui.appearance.AxAppearance
import kotlin.math.abs

class SettingsBinder(private val settings: Settings) {

    fun readBoolean(key: String): Boolean? = when (key) {
        // ---- 主设置页 62 项中的开关 ----
        "video_danmaku" -> settings.videoDanmakuEnabled()
        "live_danmaku" -> settings.liveDanmakuEnabled()
        "danmaku_emoji" -> settings.danmakuEmojiEnabled()
        "danmaku_blocklist" -> settings.danmakuBlockEnabled()
        "ambient_backdrop" -> settings.ambientBackdrop()
        "filter_mobile_video" -> settings.filterMobileVideo()
        "pip_enabled" -> settings.pipEnabled()
        "reverse_navigation" -> settings.reverseNavigation()
        "reverse_profile_playlist" -> settings.reversePlaylistInProfile()
        "profile_history_tab" -> settings.openOwnProfileOnHistoryTab()
        "disable_double_back_exit" -> settings.disableDoubleBackExit()
        "show_details_on_start" -> settings.showDetailsOnStart()
        "player_progress_bar" -> settings.playerProgressBar()
        "clean_pause" -> settings.cleanPause()
        "tv_launcher_channel" -> settings.tvLauncherChannelEnabled()
        "surface_buffer_align" -> settings.surfaceBufferAlign()
        "separate_video_fetch" -> settings.separateVideoFetch()
        "video_start_prefetch" -> settings.videoStartPrefetch()
        "mix_playlist_panel" -> settings.mixPlaylistPanelEnabled()
        "featured_auto_play" -> settings.featuredAutoPlay()
        // ---- 弹幕样式面板 15 项中的开关 ----
        "danmaku" -> settings.liveDanmakuEnabled()
        "filter_package" -> settings.danmakuFilterPackage()
        "blocklist" -> settings.danmakuBlockEnabled()
        else -> null
    }

    fun writeBoolean(key: String, value: Boolean) {
        when (key) {
            "video_danmaku" -> settings.setVideoDanmakuEnabled(value)
            "live_danmaku" -> settings.setLiveDanmakuEnabled(value)
            "danmaku_emoji" -> settings.setDanmakuEmojiEnabled(value)
            "danmaku_blocklist" -> settings.setDanmakuBlockEnabled(value)
            "ambient_backdrop" -> settings.setAmbientBackdrop(value)
            "filter_mobile_video" -> settings.setFilterMobileVideo(value)
            "pip_enabled" -> settings.setPipEnabled(value)
            "reverse_navigation" -> settings.setReverseNavigation(value)
            "reverse_profile_playlist" -> settings.setReversePlaylistInProfile(value)
            "profile_history_tab" -> settings.setOpenOwnProfileOnHistoryTab(value)
            "disable_double_back_exit" -> settings.setDisableDoubleBackExit(value)
            "show_details_on_start" -> settings.setShowDetailsOnStart(value)
            "player_progress_bar" -> settings.setPlayerProgressBar(value)
            "clean_pause" -> settings.setCleanPause(value)
            "tv_launcher_channel" -> settings.setTvLauncherChannelEnabled(value)
            "surface_buffer_align" -> settings.setSurfaceBufferAlign(value)
            "separate_video_fetch" -> settings.setSeparateVideoFetch(value)
            "video_start_prefetch" -> settings.setVideoStartPrefetch(value)
            "mix_playlist_panel" -> settings.setMixPlaylistPanelEnabled(value)
            "featured_auto_play" -> settings.setFeaturedAutoPlay(value)
            "danmaku" -> settings.setLiveDanmakuEnabled(value)
            "filter_package" -> settings.setDanmakuFilterPackage(value)
            "blocklist" -> settings.setDanmakuBlockEnabled(value)
            else -> Unit
        }
        syncAppearance(key)
    }

    /** 枚举项的显示文案；顺序必须与 Settings 内枚举 values() 一致。 */
    fun enumLabels(key: String): List<String>? = when (key) {
        "live_danmaku_style" -> listOf("滚动样式", "列表样式")
        "app_font_scale" -> listOf("标准", "大", "特大", "超大")
        // 第 4 档「移动推荐」是原版另一套信息流接口，本应用未实现，冷启动按首页处理（见 AxTvApp.defaultHomeRoute）。
        "default_home" -> listOf("推荐", "精选", "直播", "移动推荐（未实现·落首页）")
        "client_identity_branch" -> ClientIdentityBranch.values().map { it.displayName }
        "theme_mode" -> listOf("自动", "浅色", "暗色")
        "player_surface_type" -> listOf("SurfaceView", "TextureView", "GL SurfaceView")
        "color_style" -> listOf("按场景", "鲜艳", "柔和", "还原")
        "color_contrast" -> listOf("标准", "中等", "高")
        "color_transition" -> listOf("自动", "关闭")
        "audio_balance" -> listOf("关闭", "低", "中", "高")
        "playback_clock" -> listOf("始终", "整点", "整点半", "不显示")
        "playback_completion" -> listOf("自动下一个", "暂停", "重播")
        "video_load_timeout" -> listOf("5 秒", "10 秒", "15 秒", "30 秒", "60 秒")
        "feed_history_capacity" -> listOf("50 条", "100 条", "200 条", "500 条", "1000 条")
        // j16 面板的 rm2 / zm2 档位（不是 Settings.DanmakuFontSize / DanmakuSpeed 的 5/3 档）。
        "style_font" -> LIVE_FONT_LABELS
        "style_speed" -> LIVE_SPEED_LABELS
        // ---- 动态枚举行（qm4 运行时插入）----
        "video_resolution" -> VIDEO_RESOLUTION_LABELS
        "live_video_resolution" -> LIVE_RESOLUTION_LABELS
        "video_codec" -> listOf("自动", "H.264", "H.265")
        "form_factor" -> FORM_FACTOR_LABELS
        "color_source" -> COLOR_SOURCE_LABELS
        else -> null
    }

    fun readEnumIndex(key: String): Int? = when (key) {
        "live_danmaku_style" ->
            Settings.DanmakuStyle.values().indexOf(settings.liveDanmakuStyle())
        "app_font_scale" ->
            Settings.FontScale.values().indexOf(settings.fontScalePreset())
        "default_home" ->
            Settings.HomeScreen.values().indexOf(settings.defaultHomeScreen())
        "client_identity_branch" ->
            ClientIdentityBranch.values().indexOf(settings.clientIdentityBranch())
        "theme_mode" ->
            Settings.ThemeMode.values().indexOf(settings.themeMode())
        "player_surface_type" ->
            Settings.SurfaceType.values().indexOf(settings.surfaceType())
        "color_style" ->
            Settings.ColorStyle.values().indexOf(settings.colorStyle())
        "color_contrast" ->
            Settings.ColorContrast.values().indexOf(settings.colorContrast())
        "color_transition" ->
            Settings.ColorTransition.values().indexOf(settings.colorTransition())
        "audio_balance" ->
            Settings.AudioBalance.values().indexOf(settings.audioBalance())
        "playback_clock" ->
            Settings.PlaybackClock.values().indexOf(settings.playbackClock())
        "playback_completion" ->
            Settings.CompletionAction.values().indexOf(settings.completionAction())
        "video_load_timeout" ->
            Settings.LoadTimeout.values().indexOf(settings.loadTimeout())
        "feed_history_capacity" ->
            Settings.FeedHistoryCapacity.values().indexOf(settings.feedHistoryCapacity())
        "style_font" ->
            nearestIndex(LIVE_FONT_SCALES, settings.danmakuFontScale())
        "style_speed" ->
            nearestIndex(LIVE_SPEED_SCALES, settings.danmakuSpeedScale())
        // ---- 动态枚举行 ----
        "video_resolution" ->
            Settings.VideoResolution.values().indexOf(settings.videoResolution())
        "live_video_resolution" ->
            LIVE_RESOLUTIONS.indexOf(settings.liveVideoResolution()).coerceAtLeast(0)
        "video_codec" ->
            Settings.VideoCodec.values().indexOf(settings.videoCodec())
        "form_factor" ->
            Settings.UiFormFactor.values().indexOf(settings.formFactor())
        "color_source" ->
            Settings.ColorSource.values().indexOf(settings.colorSource())
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
            "client_identity_branch" ->
                settings.setClientIdentityBranch(ClientIdentityBranch.values()[index])
            "theme_mode" ->
                settings.setThemeMode(Settings.ThemeMode.values()[index])
            "player_surface_type" ->
                settings.setSurfaceType(Settings.SurfaceType.values()[index])
            "color_style" ->
                settings.setColorStyle(Settings.ColorStyle.values()[index])
            "color_contrast" ->
                settings.setColorContrast(Settings.ColorContrast.values()[index])
            "color_transition" ->
                settings.setColorTransition(Settings.ColorTransition.values()[index])
            "audio_balance" ->
                settings.setAudioBalance(Settings.AudioBalance.values()[index])
            "playback_clock" ->
                settings.setPlaybackClock(Settings.PlaybackClock.values()[index])
            "playback_completion" ->
                settings.setCompletionAction(Settings.CompletionAction.values()[index])
            "video_load_timeout" ->
                settings.setLoadTimeout(Settings.LoadTimeout.values()[index])
            "feed_history_capacity" ->
                settings.setFeedHistoryCapacity(Settings.FeedHistoryCapacity.values()[index])
            "style_font" ->
                settings.setDanmakuFontScale(LIVE_FONT_SCALES[index])
            "style_speed" ->
                settings.setDanmakuSpeedScale(LIVE_SPEED_SCALES[index])
            // ---- 动态枚举行 ----
            "video_resolution" ->
                settings.setVideoResolution(Settings.VideoResolution.values()[index])
            "live_video_resolution" ->
                settings.setLiveVideoResolution(LIVE_RESOLUTIONS[index])
            "video_codec" ->
                settings.setVideoCodec(Settings.VideoCodec.values()[index])
            "form_factor" ->
                settings.setFormFactor(Settings.UiFormFactor.values()[index])
            "color_source" ->
                settings.setColorSource(Settings.ColorSource.values()[index])
        }
        syncAppearance(key)
    }

    /**
     * 外观相关键写入后通知 AxAppearance（version 自增）：
     * 消费点是 AxTvTheme/AmbientBackdrop，组合期读版本号即订阅，改完立即全站生效。
     */
    private fun syncAppearance(key: String) {
        if (key in APPEARANCE_KEYS) AxAppearance.notifyChanged()
    }

    /** 弹幕样式面板滑杆的原始值（夹取范围由 Settings 内部保证）。 */
    fun readFloat(key: String): Float? = when (key) {
        "style_opacity" -> settings.danmakuOpacity()
        "style_area" -> settings.danmakuAreaPercent().toFloat()
        "style_vspacing" -> settings.danmakuVerticalSpacing()
        "style_hspacing" -> settings.danmakuHorizontalSpacing()
        else -> null
    }

    fun writeFloat(key: String, value: Float) {
        when (key) {
            "style_opacity" -> settings.setDanmakuOpacity(value)
            "style_area" -> settings.setDanmakuAreaPercent(value.toInt())
            "style_vspacing" -> settings.setDanmakuVerticalSpacing(value)
            "style_hspacing" -> settings.setDanmakuHorizontalSpacing(value)
        }
    }

    /** 凭据类行的只读展示值。 */
    fun readText(key: String): String? = when (key) {
        "auth_cookie" -> settings.cookie()
        "auth_security_payload" -> settings.interaction().rawPayload()
        "auth_private_key" -> settings.interaction().privateKey()
        else -> null
    }

    /** 卡片 / 入口行的状态文案（只读，不写任何配置键）。 */
    fun readStatus(key: String): String? = when (key) {
        "settings_account" -> settings.currentAccount()?.let { "账号 ${it.id}" } ?: "未登录"
        "auth_status" -> if (settings.isLoggedIn()) {
            val uid = settings.selfUserId()
            if (uid.isBlank()) "已登录" else "已登录 · $uid"
        } else {
            "未登录"
        }
        "auth_security_status" ->
            if (settings.hasInteraction()) "互动凭据已就绪" else "未设置互动凭据"
        "account_management" -> {
            val count = settings.accounts().size
            val current = settings.currentAccountId().ifBlank { "无" }
            "$count 个账号 · 当前 $current"
        }
        "auth_login_mode" -> "扫码 / Cookie"
        // 手机推送地址在「二维码链接」对话框里（带 PanelAuth 令牌）；真正的推送在手机侧完成。
        // 核心层 CookiePushHandler（9978 端口）已由 AndroidPanelServer 接通。
        "auth_config_actions" ->
            if (settings.hasInteraction()) "凭据已就绪 · 保存 / 清除" else "未设置 · 保存 / 清除"
        "about_update" -> "检查更新"
        "about_github" -> "github.com/polite0803/AxTV"
        // 未接入：仓库内没有 AxTV 的 Telegram 群组地址（见 ui/settings/SettingsActionDialogs.kt 的 TELEGRAM_GROUP_URL）。
        "about_telegram" -> "待补群组地址"
        "about_qr_links" -> "GitHub · 手机面板二维码"
        "danmaku_layout" -> "透明度 · 区域 · 间距 · 字号 · 速度"
        "quality" -> "直播画质"
        "camera" -> "取色 / 预览"
        // 导出/导入已接：整份 SharedPreferences 落应用私有目录（见 adapter/SettingsConfigIO.kt）。
        "profile" -> "导出 · 导入（私有目录）"
        "style" -> "保存 · 删除 · 推送"
        else -> null
    }

    private companion object {
        /** 「写入即生效」的外观键；与 ui/appearance/AxAppearance 的读取清单一一对应。 */
        private val APPEARANCE_KEYS = setOf(
            "theme_mode", "app_font_scale", "color_style", "color_contrast",
            "color_transition", "color_source", "ambient_backdrop",
        )

        /** j16 的 rm2：小 0.8 / 中 1.0 / 大 1.2 / 超大 1.5。 */
        private val LIVE_FONT_SCALES = floatArrayOf(0.8f, 1.0f, 1.2f, 1.5f)
        private val LIVE_FONT_LABELS = listOf("小", "中", "大", "超大")

        /** j16 的 zm2：慢 0.5 / 中 1.0 / 快 1.5 / 极快 2.0。 */
        private val LIVE_SPEED_SCALES = floatArrayOf(0.5f, 1.0f, 1.5f, 2.0f)
        private val LIVE_SPEED_LABELS = listOf("慢", "中", "快", "极快")

        private val VIDEO_RESOLUTION_LABELS =
            listOf("自动", "最高画质", "高清 1080p", "标清 720p", "流畅 540p")

        /** 直播不支持「自动」（qm4 遍历时过滤 lq5.AUTO），可选 4 档。 */
        private val LIVE_RESOLUTIONS = Settings.VideoResolution.values()
            .filter { it != Settings.VideoResolution.AUTO }
        private val LIVE_RESOLUTION_LABELS =
            listOf("最高画质", "高挡位", "中挡位", "低挡位")

        private val FORM_FACTOR_LABELS =
            listOf("自动", "手机竖屏", "平板横屏", "TV 横屏")
        private val COLOR_SOURCE_LABELS =
            listOf("画面取色", "设备取色", "品牌色")

        private fun nearestIndex(scales: FloatArray, value: Float): Int {
            var best = 0
            var bestDiff = Float.MAX_VALUE
            scales.forEachIndexed { index, scale ->
                val diff = abs(scale - value)
                if (diff < bestDiff) {
                    bestDiff = diff
                    best = index
                }
            }
            return best
        }
    }
}
