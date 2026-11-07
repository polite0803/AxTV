/*
 * 设置页的显示顺序（按功能分组，而非原版注册顺序）。
 * 规格数组 SettingsPageSpec 仍按原版 m8567Z 调用顺序保留出处；本文件只决定界面呈现顺序。
 * 重排不得丢项：覆盖性由 SettingsLayoutOrderTest 守住。
 *
 * 两项有意不在顺序里：settings_title（页面顶部单独渲染标题）、about_telegram
 * （仓库没有本项目群组地址，整行移除；地址来源见 src/app/p000/h30.java:200）。
 */
package com.github.axtv.dv.ui.settings

/** 合成分组标题：播放类开关在原版里直接挂在「视频解码」分组下，重排后单列一组。 */
internal const val PLAYBACK_HEADER = "playback_header"

internal val SETTINGS_UI_ORDER: List<String> = listOf(
    // 显示与外观
    "form_factor_header",
    "form_factor_note",
    "app_font_scale",
    "default_home",
    "theme_mode",
    "color_source_header",
    "color_style",
    "color_contrast",
    "ambient_motion_header",
    "ambient_backdrop",
    "color_transition",
    // 播放与画质
    "video_resolution_header",
    "video_codec_header",
    PLAYBACK_HEADER,
    "filter_mobile_video",
    "pip_enabled",
    "reverse_navigation",
    "reverse_profile_playlist",
    "profile_history_tab",
    "disable_double_back_exit",
    "show_details_on_start",
    "player_progress_bar",
    "clean_pause",
    "tv_launcher_channel",
    "player_surface_type",
    "surface_buffer_align",
    "audio_balance",
    "playback_clock",
    "playback_completion",
    "video_load_timeout",
    "separate_video_fetch",
    "video_start_prefetch",
    "mix_playlist_panel",
    "featured_auto_play",
    "feed_history_capacity",
    // 弹幕（样式面板紧跟弹幕开关）
    "danmaku_header",
    "video_danmaku",
    "live_danmaku",
    "live_danmaku_style",
    "danmaku_emoji",
    "danmaku_blocklist",
    "danmaku_layout",
    "style_header",
    "style_opacity",
    "style_area",
    "style_vspacing",
    "style_hspacing",
    "style_font",
    "style_speed",
    "header",
    "quality",
    "camera",
    "profile",
    "danmaku",
    "filter_package",
    "blocklist",
    "style",
    // 直播
    "live_resolution_header",
    // 账号与登录
    "settings_account",
    "auth_status_header",
    "auth_status",
    "account_management_header",
    "account_management",
    "auth_qr_header",
    "auth_login_mode",
    "auth_cookie_header",
    "auth_cookie",
    "auth_security_header",
    "auth_security_status",
    "auth_security_payload",
    "auth_private_key",
    "auth_config_actions",
    // 接口身份
    "interface_identity_header",
    "client_identity_branch",
    // 关于
    "about_update",
    "about_github",
    "about_qr_links",
    "about_version",
)
