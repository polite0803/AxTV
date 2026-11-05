/*
 * 来源: src/app/p000/fl4.java（全部键名）+ src/app/p000/wm4.java / ll4.java / i82.java（UI 侧直写的键）
 *       + src/app/p000/t34.java（"remote_config" 授权/公告键）
 * 原职责: 配置键名散落在字符串字面量里，这里集中成常量
 * 重构说明: 只做键名/文件名归档，不改变任何字符串内容
 */
package axtv.settings;

/**
 * {@code SharedPreferences} 键名与文件名常量。
 *
 * <p>文件一：{@value #PREFS_SETTINGS}（用户配置，主要）；文件二：{@value #PREFS_REMOTE_CONFIG}
 * （远端许可协议 / 公告，由 MainActivity 与远端配置模块共写）。
 */
public final class SettingsKeys {

    private SettingsKeys() {
    }

    /** 用户配置文件（原 {@code fl4} 构造时 {@code context.getSharedPreferences("douyin_auth", 0)}）。 */
    public static final String PREFS_SETTINGS = "douyin_auth";

    /** 远端配置 / 协议同意状态文件（原 {@code t34.f20456i}）。 */
    public static final String PREFS_REMOTE_CONFIG = "remote_config";

    // ─────────────── 账号与 Cookie ───────────────
    public static final String COOKIE_STRING = "cookie_string";
    public static final String ACCOUNTS_LIST = "accounts_list";
    public static final String CURRENT_ACCOUNT_ID = "current_account_id";
    public static final String SELF_USER_ID = "self_user_id";
    public static final String USER_UNIQUE_ID = "user_unique_id";
    public static final String INTERACTION_SECURITY_PAYLOAD = "interaction_security_payload";
    public static final String INTERACTION_TICKET = "interaction_ticket";
    public static final String INTERACTION_TS_SIGN = "interaction_ts_sign";
    public static final String INTERACTION_PRIVATE_KEY = "interaction_private_key";
    public static final String INTERACTION_SERVER_CERT = "interaction_server_cert";

    // ─────────────── 播放 ───────────────
    public static final String VIDEO_RESOLUTION = "video_resolution";
    public static final String LIVE_VIDEO_RESOLUTION = "live_video_resolution";
    public static final String VIDEO_CODEC_PREFERENCE = "video_codec_preference";
    public static final String SEPARATE_VIDEO_FETCH = "separate_video_fetch";
    public static final String VIDEO_START_PREFETCH = "video_start_prefetch";
    public static final String VIDEO_LOAD_TIMEOUT_SECONDS = "video_load_timeout_seconds";
    public static final String PLAYER_SURFACE_TYPE = "player_surface_type";
    public static final String SURFACE_BUFFER_ALIGN = "surface_buffer_align";
    public static final String PLAYBACK_CLOCK_DISPLAY_MODE = "playback_clock_display_mode";
    public static final String PLAYBACK_COMPLETION_ACTION = "playback_completion_action";
    /** 旧键，仅在被 {@link #PLAYBACK_COMPLETION_ACTION} 缺省时读取。 */
    public static final String AUTO_PLAY_NEXT_VIDEO = "auto_play_next_video";
    public static final String PLAYBACK_SPEED = "playback_speed";
    public static final String PLAYER_PROGRESS_BAR = "player_progress_bar";
    public static final String PIP_ENABLED = "pip_enabled";
    public static final String CLEAN_PAUSE = "clean_pause";
    public static final String FEATURED_AUTO_PLAY = "featured_auto_play";

    // ─────────────── 观看记录 / 列表 ───────────────
    public static final String VIEWED_VIDEO_IDS = "viewed_video_ids";
    public static final String FEED_HISTORY_CAPACITY = "feed_history_capacity";
    public static final String OPEN_OWN_PROFILE_ON_HISTORY_TAB = "open_own_profile_on_history_tab";
    public static final String REVERSE_PLAYLIST_IN_PROFILE = "reverse_playlist_in_profile";
    public static final String MIX_PLAYLIST_PANEL_ENABLED = "mix_playlist_panel_enabled";
    public static final String SHOW_DETAILS_ON_START = "show_details_on_start";

    // ─────────────── 弹幕 ───────────────
    /** 旧键，video/live 两项的默认值来源。 */
    public static final String DANMAKU_ENABLED = "danmaku_enabled";
    public static final String VIDEO_DANMAKU_ENABLED = "video_danmaku_enabled";
    public static final String LIVE_DANMAKU_ENABLED = "live_danmaku_enabled";
    public static final String DANMAKU_EMOJI_ENABLED = "danmaku_emoji_enabled";
    public static final String LIVE_DANMAKU_STYLE = "live_danmaku_style";
    public static final String DANMAKU_OPACITY = "danmaku_opacity";
    /** 新版百分比（1..100），优先于旧键 {@link #DANMAKU_AREA}。 */
    public static final String DANMAKU_AREA_PERCENT = "danmaku_area_percent";
    /** 旧键（0.01..1.0 的比例），仅迁移读取，写入时与 percent 同步。 */
    public static final String DANMAKU_AREA = "danmaku_area";
    public static final String DANMAKU_VERTICAL_SPACING = "danmaku_vertical_spacing";
    public static final String DANMAKU_HORIZONTAL_SPACING = "danmaku_horizontal_spacing";
    public static final String DANMAKU_FONT_SIZE = "danmaku_font_size";
    public static final String DANMAKU_SPEED = "danmaku_speed";
    public static final String DANMAKU_BLOCK_ENABLED = "danmaku_block_enabled";
    public static final String DANMAKU_BLOCK_WORDS = "danmaku_block_words";
    public static final String DANMAKU_FILTER_PACKAGE = "danmaku_filter_package";

    // ─────────────── 接口 ───────────────
    /** 客户端身份分支（0 = Web 接口 / 1 = Webcast / 2 = App Feed），下次启动生效。 */
    public static final String CLIENT_IDENTITY_BRANCH = "client_identity_branch";

    // ─────────────── 界面 / 主题 ───────────────
    public static final String UI_FORM_FACTOR = "ui_form_factor";
    public static final String APP_FONT_SCALE = "app_font_scale";
    public static final String THEME_MODE = "theme_mode";
    public static final String COLOR_SOURCE = "color_source";
    /** 旧键，仅用于 color_source 的一次性迁移。 */
    public static final String DYNAMIC_COLOR = "dynamic_color";
    public static final String COLOR_STYLE = "color_style";
    public static final String COLOR_CONTRAST = "color_contrast";
    public static final String COLOR_TRANSITION = "color_transition";
    public static final String GLOBAL_SEED_ARGB = "global_seed_argb";
    public static final String AMBIENT_BACKDROP = "ambient_backdrop";

    // ─────────────── 交互 / 导航 ───────────────
    public static final String DEFAULT_HOME_SCREEN = "default_home_screen";
    public static final String REVERSE_NAVIGATION = "reverse_navigation";
    public static final String DISABLE_DOUBLE_BACK_EXIT = "disable_double_back_exit";
    public static final String FILTER_MOBILE_VIDEO = "filter_mobile_video";
    public static final String TV_LAUNCHER_CHANNEL_ENABLED = "tv_launcher_channel_enabled";
    public static final String AUDIO_BALANCE_LEVEL = "audio_balance_level";

    // ─────────────── 设备标识（16 位纯数字，惰性生成） ───────────────
    public static final String MOBILE_FEED_DEVICE_ID = "mobile_feed_device_id";
    public static final String MOBILE_FEED_INSTALL_ID = "mobile_feed_install_id";

    // ─────────────── remote_config ───────────────
    public static final String LICENSE_AGREED = "license_agreed";
    public static final String LICENSE_AGREED_VERSION = "license_agreed_version";
    public static final String LICENSE_REQUIRED_VERSION = "license_required_version";
    public static final String LAST_ANNOUNCEMENT_ID = "last_announcement_id";
}
