/*
 * 来源: src/app/p000/qm4.java（m8567Z 注册调用，62 项，按出现顺序去重）
 *       src/app/p000/j16.java:1084-1356（弹幕样式面板的 m8567Z，15 项）
 *       src/app/p000/qm4.java:2143-2199 / 2281-2283（画质选项文案）
 *       src/app/p000/lq5.java（点播清晰度枚举，jadx 枚举还原失败但常量可读）
 * 原职责: 设置页的控件注册表、分组、控件类型、画质选项与文案
 * 重构说明: Compose 可组合函数用「数据规格」表达；每一项都有源码里的 key 与 type 实证，
 *           不臆造控件。绘制与事件 lambda 留给 Android 侧（见 NOTES.md）
 */
package axtv.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 设置页的完整 UI 行为规格。 */
public final class SettingsPageSpec {

    /** {@code m8567Z} 第二个参数的所有取值（qm4 + j16 合并，26 种，与源码一一对应）。 */
    public enum ControlType {
        TITLE("title"),
        SECTION_HEADER("section_header"),
        SWITCH_ROW("switch_row"),
        ANIMATED_SWITCH_ROW("animated_switch_row"),
        ENUM_ROW("enum_row"),
        ANIMATED_ENUM_ROW("animated_enum_row"),
        ACCOUNT_CARD("account_card"),
        ACCOUNT_MANAGEMENT("account_management"),
        AUTH_STATUS_CARD("auth_status_card"),
        AUTH_LOGIN_CARD("auth_login_card"),
        AUTH_SECURITY_CARD("auth_security_card"),
        AUTH_TEXT_FIELD("auth_text_field"),
        AUTH_CONFIG_ACTIONS("auth_config_actions"),
        ACTION_BUTTON("action_button"),
        QR_LINKS_CARD("qr_links_card"),
        APP_VERSION("app_version"),
        NOTE("note"),
        DANMAKU_LAYOUT_CONTROLS("danmaku_layout_controls"),
        SWITCH("switch"),
        SLIDER("slider"),
        OPTION("option"),
        HEADER("header"),
        QUALITY("quality"),
        CAMERA("camera"),
        ACTION("action"),
        STYLE_HEADER("style-header");

        private final String raw;

        ControlType(String raw) {
            this.raw = raw;
        }

        public String raw() {
            return raw;
        }

        public static ControlType of(String raw) {
            for (ControlType t : values()) {
                if (t.raw.equals(raw)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("Unknown control type: " + raw);
        }
    }

    /**
     * 一个控件。
     *
     * @param key         {@code m8567Z} 的第一个参数（唯一标识）
     * @param type        控件类型
     * @param section     所属分组（上一个 {@code section_header}/{@code header} 的 key，无则 ""）
     * @param settingsKey 对应的持久化键；能从源码确认的才填，否则 null
     */
    public record Control(String key, ControlType type, String section, String settingsKey) {
    }

    /** 分组顺序（qm4 里 12 个 section_header 的 key，按出现顺序）。 */
    public static final List<String> SECTION_ORDER = List.of(
            "live_resolution_header",
            "danmaku_header",
            "auth_status_header",
            "account_management_header",
            "auth_qr_header",
            "auth_cookie_header",
            "auth_security_header",
            "form_factor_header",
            "color_source_header",
            "ambient_motion_header",
            "video_resolution_header",
            "video_codec_header");

    // ---- 能从源码确认的「控件 key → 配置键」映射 ----

    public static final Map<String, String> CONFIRMED_SETTINGS_KEYS;

    static {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        // 控件 key → 实际 SharedPreferences 键名（值取自 settings/SettingsKeys.java）
        m.put("app_font_scale", "app_font_scale");
        m.put("tv_launcher_channel", "tv_launcher_channel_enabled");
        m.put("video_start_prefetch", "video_start_prefetch");
        m.put("surface_buffer_align", "surface_buffer_align");
        m.put("separate_video_fetch", "separate_video_fetch");
        m.put("video_danmaku", "video_danmaku_enabled");
        m.put("live_danmaku", "live_danmaku_enabled");
        m.put("live_danmaku_style", "live_danmaku_style");
        m.put("danmaku_emoji", "danmaku_emoji_enabled");
        m.put("clean_pause", "clean_pause");
        m.put("color_contrast", "color_contrast");
        m.put("color_style", "color_style");
        m.put("color_transition", "color_transition");
        m.put("ambient_backdrop", "ambient_backdrop");
        m.put("default_home", "default_home_screen");
        m.put("disable_double_back_exit", "disable_double_back_exit");
        m.put("featured_auto_play", "featured_auto_play");
        m.put("feed_history_capacity", "feed_history_capacity");
        m.put("filter_mobile_video", "filter_mobile_video");
        m.put("pip_enabled", "pip_enabled");
        m.put("player_progress_bar", "player_progress_bar");
        m.put("player_surface_type", "player_surface_type");
        m.put("reverse_navigation", "reverse_navigation");
        m.put("show_details_on_start", "show_details_on_start");
        m.put("theme_mode", "theme_mode");
        m.put("client_identity_branch", "client_identity_branch");
        m.put("auth_cookie", "cookie_string");
        m.put("auth_security_payload", "interaction_security_payload");
        m.put("auth_private_key", "interaction_private_key");
        CONFIRMED_SETTINGS_KEYS = Collections.unmodifiableMap(m);
    }

    /** 主设置页 62 项，顺序与 {@code qm4.m8567Z} 调用顺序一致。 */
    public static final List<Control> MAIN_PAGE = buildMain();

    /** 弹幕样式面板 15 项，顺序与 {@code j16.m8567Z} 调用顺序一致。 */
    public static final List<Control> DANMAKU_STYLE_PANEL = buildDanmakuPanel();

    /** 点播清晰度选项（原 {@code lq5} 枚举，5 档）。 */
    public enum VideoQualityOption {
        AUTO("自动", "auto", "自动", "按服务端自动匹配"),
        HIGHEST("最高画质", "highest", "最高", "优先选择可用最高档位"),
        P1080("高清 1080p", "1080p", "1080P", null),
        P720("标清 720p", "720p", "720P", null),
        P540("流畅 540p", "480p", "540P", null);

        private final String label;
        private final String prefValue;
        private final String shortLabel;
        private final String description;

        VideoQualityOption(String label, String prefValue, String shortLabel, String description) {
            this.label = label;
            this.prefValue = prefValue;
            this.shortLabel = shortLabel;
            this.description = description;
        }

        public String label() { return label; }
        public String prefValue() { return prefValue; }
        public String shortLabel() { return shortLabel; }
        /** 仅 ordinal 0/1 在 qm4 里有描述，其余为 null。 */
        public String description() { return description; }

        public static VideoQualityOption fromPrefValue(String value) {
            for (VideoQualityOption o : values()) {
                if (o.prefValue.equals(value)) {
                    return o;
                }
            }
            return null;
        }
    }

    /**
     * 直播清晰度选项（qm4:2143-2199 的 5 个 ordinal 分支）。
     * 与点播共用 ordinal 0/1 的名字，但 2/3/4 是「按直播间可用挡位」的相对档位。
     */
    public enum LiveQualityOption {
        AUTO("自动", "最高的 H.264 挡位，FLV 起播更稳（推荐）"),
        HIGHEST("最高画质", "无条件原画，HEVC 直播走 HLS，低端设备可能卡顿"),
        HIGH("高挡位", "按直播间可用挡位偏高选择"),
        MID("中挡位", "按直播间可用挡位居中选择"),
        LOW("低挡位", "直播间最低挡位，最流畅");

        private final String label;
        private final String description;

        LiveQualityOption(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() { return label; }
        public String description() { return description; }
    }

    /** 点播 / 直播清晰度的持久化键与联动（qm4:2215-2255 的 smali 片段）。 */
    public static final String KEY_VIDEO_RESOLUTION = "video_resolution";
    public static final String KEY_LIVE_VIDEO_RESOLUTION = "live_video_resolution";

    /**
     * 用户改点播清晰度后的联动：写 {@code video_resolution}；
     * 若 {@code live_video_resolution} 尚未单独设过，则直播跟随点播值，
     * 且点播为 AUTO 时直播取 HIGHEST（「自动」在直播里不合法）。
     */
    public record ResolutionChange(String videoResolution, String liveVideoResolution) {
    }

    public static ResolutionChange applyResolutionChange(String selectedPrefValue,
                                                          boolean liveResolutionExplicitlySet) {
        VideoQualityOption selected = VideoQualityOption.fromPrefValue(selectedPrefValue);
        VideoQualityOption effective = selected == null ? VideoQualityOption.AUTO : selected;
        String live = null;
        if (!liveResolutionExplicitlySet) {
            VideoQualityOption liveOption = effective == VideoQualityOption.AUTO
                    ? VideoQualityOption.HIGHEST : effective;
            live = liveOption.prefValue();
        }
        return new ResolutionChange(effective.prefValue(), live);
    }

    /** 设置页可见的全部中文文案（qm4 30 条 + j16 8 条，去重后 37 条，逐字保留）。 */
    public static final List<String> LABELS = List.of(
            "← → 选择，OK 进入", "主播头像", "删除", "字体大小", "弹幕速度",
            "点按选择要使用的配置", "直播设置", "谁在看？",
            "GitHub 主页", "Telegram 群组", "三米外观看时可以调大一档", "中挡位",
            "仅画面取色时生效", "优先选择可用最高档位", "低挡位", "保存配置",
            "加载视频", "左右键调节", "已保存", "已设置 PIN", "当前配置",
            "按服务端自动匹配", "按直播间可用挡位偏高选择", "按直播间可用挡位居中选择",
            "推送到配置", "文字大小", "无条件原画，HEVC 直播走 HLS，低端设备可能卡顿",
            "最高画质", "最高的 H.264 挡位，FLV 起播更稳（推荐）", "添加新配置",
            "清除", "直播间最低挡位，最流畅", "自动", "色彩过渡动画", "返回分类",
            "页面颜色与电视一致", "高挡位");

    private SettingsPageSpec() {
    }

    private static List<Control> buildMain() {
        String[][] rows = {
                {"settings_title", "title"},
                {"settings_account", "account_card"},
                {"live_resolution_header", "section_header"},
                {"danmaku_header", "section_header"},
                {"video_danmaku", "switch_row"},
                {"live_danmaku", "switch_row"},
                {"live_danmaku_style", "animated_enum_row"},
                {"danmaku_emoji", "animated_switch_row"},
                {"danmaku_blocklist", "switch_row"},
                {"danmaku_layout", "danmaku_layout_controls"},
                {"auth_status_header", "section_header"},
                {"auth_status", "auth_status_card"},
                {"account_management_header", "section_header"},
                {"account_management", "account_management"},
                {"auth_qr_header", "section_header"},
                {"auth_login_mode", "auth_login_card"},
                {"auth_cookie_header", "section_header"},
                {"auth_cookie", "auth_text_field"},
                {"auth_security_header", "section_header"},
                {"auth_security_status", "auth_security_card"},
                {"auth_security_payload", "auth_text_field"},
                {"auth_private_key", "auth_text_field"},
                {"auth_config_actions", "auth_config_actions"},
                {"about_update", "action_button"},
                {"about_github", "action_button"},
                {"about_telegram", "action_button"},
                {"about_qr_links", "qr_links_card"},
                {"about_version", "app_version"},
                {"interface_identity_header", "section_header"},
                {"client_identity_branch", "enum_row"},
                {"form_factor_header", "section_header"},
                {"form_factor_note", "note"},
                {"app_font_scale", "enum_row"},
                {"default_home", "enum_row"},
                {"theme_mode", "enum_row"},
                {"color_source_header", "section_header"},
                {"color_style", "enum_row"},
                {"color_contrast", "enum_row"},
                {"ambient_motion_header", "section_header"},
                {"ambient_backdrop", "switch_row"},
                {"color_transition", "enum_row"},
                {"video_resolution_header", "section_header"},
                {"video_codec_header", "section_header"},
                {"filter_mobile_video", "switch_row"},
                {"pip_enabled", "switch_row"},
                {"reverse_navigation", "switch_row"},
                {"reverse_profile_playlist", "switch_row"},
                {"profile_history_tab", "switch_row"},
                {"disable_double_back_exit", "switch_row"},
                {"show_details_on_start", "switch_row"},
                {"player_progress_bar", "switch_row"},
                {"clean_pause", "switch_row"},
                {"tv_launcher_channel", "switch_row"},
                {"player_surface_type", "enum_row"},
                {"surface_buffer_align", "switch_row"},
                {"audio_balance", "enum_row"},
                {"playback_clock", "enum_row"},
                {"playback_completion", "enum_row"},
                {"video_load_timeout", "enum_row"},
                {"separate_video_fetch", "switch_row"},
                {"video_start_prefetch", "switch_row"},
                {"mix_playlist_panel", "switch_row"},
                {"featured_auto_play", "switch_row"},
                {"feed_history_capacity", "enum_row"},
        };
        ArrayList<Control> out = new ArrayList<>(rows.length);
        String section = "";
        for (String[] row : rows) {
            ControlType type = ControlType.of(row[1]);
            if (type == ControlType.SECTION_HEADER) {
                section = row[0];
            }
            out.add(new Control(row[0], type, section, CONFIRMED_SETTINGS_KEYS.get(row[0])));
        }
        return List.copyOf(out);
    }

    private static List<Control> buildDanmakuPanel() {
        String[][] rows = {
                {"style_header", "style-header"},
                {"style_opacity", "slider"},
                {"style_area", "slider"},
                {"style_vspacing", "slider"},
                {"style_hspacing", "slider"},
                {"style_font", "option"},
                {"style_speed", "option"},
                {"header", "header"},
                {"quality", "quality"},
                {"camera", "camera"},
                {"profile", "action"},
                {"danmaku", "switch"},
                {"filter_package", "switch"},
                {"blocklist", "switch"},
                {"style", "action"},
        };
        ArrayList<Control> out = new ArrayList<>(rows.length);
        String section = "";
        for (String[] row : rows) {
            ControlType type = ControlType.of(row[1]);
            if (type == ControlType.HEADER || type == ControlType.STYLE_HEADER) {
                section = row[0];
            }
            out.add(new Control(row[0], type, section, null));
        }
        return List.copyOf(out);
    }

    /** 按分组把主设置页控件切开，供 UI 层按 section 渲染。 */
    public static Map<String, List<Control>> grouped() {
        LinkedHashMap<String, List<Control>> groups = new LinkedHashMap<>();
        for (Control c : MAIN_PAGE) {
            groups.computeIfAbsent(c.section(), k -> new ArrayList<>()).add(c);
        }
        LinkedHashMap<String, List<Control>> result = new LinkedHashMap<>();
        groups.forEach((k, v) -> result.put(k, List.copyOf(v)));
        return Collections.unmodifiableMap(result);
    }
}
