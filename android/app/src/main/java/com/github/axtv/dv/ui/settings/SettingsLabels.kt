/*
 * 设置页的文案映射（从 SettingsScreen.kt 拆出）。
 * 拆分原因: SettingsScreen.kt 超 800 行；key→中文标签映射是纯数据，与控件渲染无耦合。
 */
package com.github.axtv.dv.ui.settings

/** 凭据类值只做截断预览，避免整段 cookie / 私钥铺满整行。 */
internal fun preview(value: String): String =
    if (value.length <= 28) value else value.take(28) + "…"

internal val LABELS = mapOf(
    // 主设置页 62 项
    "settings_title" to "设置",
    "settings_account" to "账号",
    "live_resolution_header" to "直播设置",
    "danmaku_header" to "弹幕",
    "video_danmaku" to "视频弹幕",
    "live_danmaku" to "直播弹幕",
    "live_danmaku_style" to "直播弹幕样式",
    "danmaku_emoji" to "弹幕表情",
    "danmaku_blocklist" to "弹幕屏蔽词",
    "danmaku_layout" to "弹幕布局",
    "auth_status_header" to "登录状态",
    "auth_status" to "当前登录状态",
    "account_management_header" to "账号管理",
    "account_management" to "切换 / 删除账号",
    "auth_qr_header" to "扫码登录",
    "auth_login_mode" to "登录方式",
    "auth_cookie_header" to "Cookie 登录",
    "auth_cookie" to "Cookie",
    "auth_security_header" to "互动安全",
    "auth_security_status" to "安全凭据状态",
    "auth_security_payload" to "安全载荷",
    "auth_private_key" to "私钥",
    "auth_config_actions" to "凭据操作",
    "about_update" to "检查更新",
    "about_github" to "GitHub 主页",
    "about_qr_links" to "二维码链接",
    "about_version" to "版本",
    "interface_identity_header" to "接口身份",
    "client_identity_branch" to "客户端身份分支",
    "form_factor_header" to "显示与字体",
    "form_factor_note" to "三米外观看时可以调大一档",
    "app_font_scale" to "字体大小",
    "default_home" to "默认首页",
    "theme_mode" to "主题模式",
    "color_source_header" to "色彩来源",
    "color_style" to "色彩风格",
    "color_contrast" to "色彩对比度",
    "ambient_motion_header" to "氛围动效",
    "ambient_backdrop" to "氛围背景",
    "color_transition" to "色彩过渡动画",
    "video_resolution_header" to "视频清晰度",
    "video_codec_header" to "视频解码",
    "filter_mobile_video" to "过滤手机视频",
    "pip_enabled" to "画中画",
    "reverse_navigation" to "反向导航",
    "reverse_profile_playlist" to "个人页倒序播放列表",
    "profile_history_tab" to "个人页默认历史标签",
    "disable_double_back_exit" to "禁用双击返回退出",
    "show_details_on_start" to "启动时显示详情",
    "player_progress_bar" to "播放器进度条",
    "clean_pause" to "纯净暂停",
    "tv_launcher_channel" to "TV 桌面频道",
    "player_surface_type" to "播放器画面类型",
    "surface_buffer_align" to "Surface 缓冲区对齐",
    "audio_balance" to "音量均衡",
    "playback_clock" to "播放时钟",
    "playback_completion" to "播放结束行为",
    "video_load_timeout" to "视频加载超时",
    "separate_video_fetch" to "音视频分离拉取",
    "video_start_prefetch" to "起播预加载",
    "mix_playlist_panel" to "合集播放列表面板",
    "featured_auto_play" to "精选自动播放",
    "feed_history_capacity" to "历史记录容量",
    // 弹幕样式面板 15 项
    "style_header" to "弹幕样式",
    "style_opacity" to "不透明度",
    "style_area" to "显示区域",
    "style_vspacing" to "行间距",
    "style_hspacing" to "水平间距",
    "style_font" to "字号",
    "style_speed" to "速度",
    "header" to "弹幕样式 · 更多",
    "quality" to "画质",
    "camera" to "取色 / 预览相机",
    "profile" to "配置管理",
    "danmaku" to "弹幕总开关",
    "filter_package" to "过滤弹幕包",
    "blocklist" to "屏蔽词",
    "style" to "样式配置操作",
)

internal fun labelOf(key: String): String = LABELS[key] ?: key

internal val SECTION_LABELS = mapOf(
    "live_resolution_header" to "直播设置",
    "danmaku_header" to "弹幕",
    "auth_status_header" to "登录状态",
    "account_management_header" to "账号管理",
    "auth_qr_header" to "扫码登录",
    "auth_cookie_header" to "Cookie 登录",
    "auth_security_header" to "互动安全",
    "interface_identity_header" to "接口身份",
    "form_factor_header" to "显示与字体",
    "color_source_header" to "色彩来源",
    "ambient_motion_header" to "氛围动效",
    "video_resolution_header" to "视频清晰度",
    "video_codec_header" to "视频解码",
    "style_header" to "弹幕样式面板",
    "header" to "弹幕样式 · 更多",
)

internal fun sectionLabel(key: String): String = SECTION_LABELS[key] ?: key

internal fun noteOf(key: String): String =
    if (key == "form_factor_note") "三米外观看时可以调大一档" else key

internal fun cardHintOf(key: String): String = when (key) {
    "auth_status" -> "未登录"
    "about_qr_links" -> "二维码"
    else -> "账号"
}
