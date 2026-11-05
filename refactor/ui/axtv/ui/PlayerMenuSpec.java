/*
 * 来源: src/app/p000/lq2.java（22,457 行，R8 打平的播放器菜单组件库）及其直接调用方
 *       （on5/a91/ku3/ld4/vk5/xn2/ao2/vv0.java —— 菜单装配层）。
 * 原职责: lq2 是「播放菜单」可组合组件库 + 若干被 R8 并入的工具函数，分区见 NOTES-player-ui.md：
 *         m7503a 菜单面板头 / m7505b 图标按钮 / m7507c 选项组 / m7509d 菜单行 / m7511e 分组小标题 /
 *         m7513f+m7515g 段选行 / m7517h 滑杆行 / m7519i 步进辅助 / m7527m 开关 / m7529n 开关行 /
 *         m7486J/K/L 图标（火焰/解锁/重播）。
 * 重构说明: 行为规格模式 —— 控件显示字段、可见性条件、状态→颜色、步进规则落成纯 Java；
 *         像素绘制归 Android 侧 Compose。文案逐字保留，数字常量注明来源行号。
 */
package axtv.ui;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 播放器菜单（「播放菜单」面板 + 弹幕设置 + 倍速/画质/播放完后）的行为规格。 */
public final class PlayerMenuSpec {

    private PlayerMenuSpec() {
    }

    // ==================== 文案（逐字，来源行号） ====================

    /** 菜单面板标题（lq2:624）。 */
    public static final String TEXT_MENU_TITLE = "播放菜单";
    /** 滑杆行操作提示，字号 12sp，色 onSurfaceVariant（lq2:20025）。 */
    public static final String TEXT_SLIDER_HINT = "左右键微调，长按加速";
    /** 图标矢量名（Material Icons），非用户可见文案（lq2:139/182/227）。 */
    public static final String ICON_FIRE = "Filled.LocalFireDepartment";
    public static final String ICON_UNLOCK = "Filled.LockOpen";
    public static final String ICON_REPLAY = "Filled.Replay";

    // 菜单装配层文案（调用方，行为规格一并保留）
    public static final String LABEL_BACK = "返回";                       // ao2:71/218
    public static final String TITLE_DANMAKU_STYLE = "弹幕样式";          // ao2:72
    public static final String TITLE_DANMAKU_SETTINGS = "弹幕设置";       // ao2:219
    public static final String LABEL_DANMAKU = "弹幕";                    // xn2:50 / on5:1657
    public static final String VALUE_DANMAKU_LIST_STYLE = "列表样式";     // xn2:50
    public static final String VALUE_DANMAKU_SCROLL_STYLE = "滚动样式";   // xn2:50
    public static final String LABEL_FILTER_GIFT_COMMENT = "过滤福袋评论"; // xn2:97
    public static final String LABEL_DISPLAY_AREA = "显示区域";           // xn2:117 / vv0:56
    public static final String LABEL_V_SPACING = "垂直间距";              // xn2:137 / vk5:54
    public static final String LABEL_H_SPACING = "水平间距";              // xn2:157 / vk5:76
    public static final String LABEL_SPEED = "倍速";                      // vk5:105 / ld4:84
    public static final String LABEL_QUALITY = "画质";                    // ku3:363 / on5:1445 / a91:617
    public static final String LABEL_AFTER_PLAYBACK = "播放完后";         // a91:587（图标=ICON_REPLAY）
    public static final String LABEL_BLOCK_WORDS = "弹幕屏蔽词";          // a91:170 / on5:1696
    public static final String LABEL_DANMAKU_SETTINGS_ENTRY = "弹幕设置"; // on5:1667 图标按钮
    public static final String LABEL_FULL_DESCRIPTION = "完整简介";       // on5:1479
    public static final String LABEL_VIEW_AUTHOR_HOME = "查看发布者主页"; // on5:1490
    public static final String LABEL_DISLIKE = "不喜欢";                  // on5:1501
    public static final String LABEL_SCREENCAST = "外屏放映";             // on5:1760
    public static final String LABEL_STOP_SCREENCAST = "结束外屏放映";    // on5:1760
    public static final String LABEL_LIKE = "点赞";                       // on5:1575
    public static final String LABEL_UNLIKE = "取消点赞";                 // on5:1575
    public static final String LABEL_COLLECT = "收藏";                    // on5:1603
    public static final String LABEL_UNCOLLECT = "取消收藏";              // on5:1603
    public static final String LABEL_PROCESSING = "处理中...";            // on5:1573
    public static final String VALUE_UNIT_COUNT = "条";                   // on5:1696（屏蔽词数量单位）
    public static final String VALUE_UNIT_X = "×";                       // on5:4762（倍速值后缀）

    // ==================== 菜单数据 ====================

    /** 倍速列表（on5.java:56，Float 列表按原顺序）。 */
    public static final List<Float> SPEED_OPTIONS =
            List.of(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f);

    /** 倍速选项显示文本（on5.m8694J0/m8692I0：值 + "×"，如 "2×"）。 */
    public static String speedLabel(float speed) {
        return trimFloat(speed) + VALUE_UNIT_X;
    }

    /** 画质档位（lq5.java:21-28：code=存储值，label=显示值）。 */
    public record Quality(String code, String label) {
    }

    public static final List<Quality> QUALITY_OPTIONS = List.of(
            new Quality("auto", "自动"),      // lq5:21
            new Quality("highest", "最高"),   // lq5:22-23
            new Quality("1080p", "1080P"),    // lq5:24
            new Quality("720p", "720P"),      // lq5:26
            new Quality("480p", "540P"));     // lq5:28（code 480p，显示 540P —— 原样保留）

    /** 播放完后模式（so3.java:19-21，显示值 = 存储键，a91:587 直接展示 f20085I）。 */
    public static final List<String> AFTER_PLAYBACK_MODES =
            List.of("auto_next", "pause", "replay");

    /** 弹幕间距滑杆参数（vk5:47/54、76：范围 0.5–2.0，步数 14，单击步长 0.1，长按步长 0.2，值格式 %.1fx）。 */
    public static final float DANMAKU_SPACING_MIN = 0.5f;
    public static final float DANMAKU_SPACING_MAX = 2.0f;
    public static final int DANMAKU_SPACING_STEPS = 14;   // vk5:54（滑杆 steps）
    public static final float DANMAKU_SPACING_CLICK_STEP = 0.1f;  // vk5:54
    public static final float DANMAKU_SPACING_HOLD_STEP = 0.2f;   // vk5:76

    /** 间距值显示格式（vk5:46/68：String.format("%.1fx", v)）。 */
    public static String spacingLabel(float v) {
        return String.format(Locale.ROOT, "%.1fx", v);
    }

    private static String trimFloat(float v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    // ==================== 组件规格 ====================

    /** 行内状态组合（决定颜色，见 PlayerUiPalette.menuRoleTable）。 */
    public record RowState(boolean selected, boolean enabled, boolean pressed, boolean focused) {
    }

    /**
     * 菜单行颜色解析（m7509d，lq2:1434-1455/1886-1611 两个重复块语义一致）：
     * enabled:
     *   selected → bg=PRIMARY_CONTAINER, fg=ON_PRIMARY_CONTAINER
     *   else     → bg=调用方传入（缺省 f11877G）, fg=ON_SURFACE
     * disabled → fg=ON_SURFACE_VARIANT@0.7（bg 按调用方）
     * pressed 时标题字重切换（qk1.f18059P↔f18056M，lq2:1538-1542）。
     */
    public static String rowForegroundRole(RowState s) {
        if (!s.enabled()) {
            return PlayerUiPalette.ON_SURFACE_VARIANT + "@0.7";
        }
        if (s.selected()) {
            return PlayerUiPalette.ON_PRIMARY_CONTAINER;
        }
        return PlayerUiPalette.ON_SURFACE;
    }

    public static String rowBackgroundRole(RowState s) {
        if (s.enabled() && s.selected()) {
            return PlayerUiPalette.PRIMARY_CONTAINER;
        }
        return "f11877G";
    }

    /** 菜单行布局常量（m7509d）。 */
    public static final float ROW_MIN_WIDTH_DP = 44.0f;      // lq2:1846/22026（宽度参数缺省）
    public static final float ROW_CORNER_DP = 12.0f;         // lq2:1458 l74.m7382a(12.0f)
    public static final float ROW_PAD_H_DP = 16.0f;          // lq2:1404/1856 li3(16,6,16,6)
    public static final float ROW_PAD_V_DP = 6.0f;
    public static final int ROW_TITLE_MAX_LINES = 1;         // lq2:1409/1861
    public static final int ROW_VALUE_MAX_LINES = 2;         // lq2:1414/1866
    public static final float ROW_SUBTITLE_ALPHA = 0.78f;    // lq2:1457 副标题透明度
    public static final float ROW_ICON_PAD_DP = 20.0f;       // lq2:1514 行内图标 padding

    /** 菜单行内容模型（m7509d 参数语义化）。 */
    public record MenuRow(String icon, String title, String value, String subtitle,
                          boolean selected, boolean enabled, Runnable onClick) {
        /** 副标题可空即不渲染（lq2:1569/1577 有值才画，透明度 0.78）。 */
        public boolean hasSubtitle() {
            return subtitle != null && !subtitle.isEmpty();
        }
    }

    /** 开关行（m7529n：菜单行 + 尾部 Switch + 点击切换）。 */
    public static final class SwitchRow {
        private final String title;
        private final String value;     // 右侧值文本（如 弹幕样式→"列表样式"/"滚动样式"）
        private final String icon;      // 可空
        private boolean checked;

        public SwitchRow(String title, String value, String icon, boolean checked) {
            this.title = Objects.requireNonNull(title, "title");
            this.value = value;
            this.icon = icon;
            this.checked = checked;
        }

        public boolean checked() {
            return checked;
        }

        public String title() {
            return title;
        }

        public String value() {
            return value;
        }

        public String icon() {
            return icon;
        }

        /** 点击整行或开关 → 取反（m7529n 的 uj0(tm1, z)：onToggle(!checked)，lq2:22055-22059）。 */
        public void toggle() {
            checked = !checked;
        }
    }

    /** 段选行（m7515g + m7513f）：单选，横向排布。 */
    public static final class SegmentRow<T> {
        private final List<T> options;
        private T selected;

        public SegmentRow(List<T> options, T selected) {
            this.options = List.copyOf(options);
            this.selected = selected;
        }

        public List<T> options() {
            return options;
        }

        public T selected() {
            return selected;
        }

        /** 选中即切换（m7507c/m7515g 的 onSelect：tm1.invoke(item)，lq2:932/17252）。 */
        public boolean select(T option) {
            if (!options.contains(option)) {
                return false;
            }
            selected = option;
            return true;
        }

        /** 选中态判定：equals（j42.m6486c，lq2:888/17217）。 */
        public static boolean isSelected(Object option, Object selected) {
            return Objects.equals(option, selected);
        }
    }

    // 段选行布局常量（m7513f/m7515g）
    public static final float SEGMENT_SPACING_DP = 8.0f;        // lq2:868 C1315kp(8.0f)
    public static final float SEGMENT_SELECTED_CORNER_DP = 12.0f; // lq2:16906（选中或按下 → 12）
    public static final float SEGMENT_CORNER_DP = 4.0f;         // lq2:16906（未选且未按下 → 4）
    public static final int SEGMENT_TEXT_SP = 14;               // lq2:962 oh4.m8585j(14)

    /** 分组小标题（m7511e）：色 ON_SURFACE_VARIANT，排版 f13038n，padding(8, top, 0, 6)。 */
    public record SectionLabel(String text, float topPaddingDp) {
    }

    /** 图标按钮（m7505b）：图标 + 下方文案，整块可点击。 */
    public static final class IconButton {
        private final String icon;
        private final String label;
        private final Runnable onClick;

        public IconButton(String icon, String label, Runnable onClick) {
            this.icon = Objects.requireNonNull(icon, "icon");
            this.label = label;
            this.onClick = onClick;
        }

        public void click() {
            onClick.run();
        }

        public String icon() {
            return icon;
        }

        public String label() {
            return label;
        }
    }

    // 图标按钮布局常量（m7505b）
    public static final float ICON_BUTTON_SIZE_DP = 44.0f;  // lq2:706 缺省尺寸
    public static final float ICON_BUTTON_SPACING_DP = 12.0f; // lq2:732
    public static final float ICON_BUTTON_ICON_PAD_DP = 20.0f; // lq2:750

    /** 选项组（m7507c）：标题 + 横向滚动的选项 chip 行。 */
    public static final float OPTION_CHIP_HEIGHT_DP = 44.0f; // lq2:916
    public static final float OPTION_CHIP_SPACING_DP = 12.0f; // lq2:944

    /**
     * 面板头（m7503a）：背景 TERTIARY_CONTAINER 圆角卡，图标（tint ON_TERTIARY_CONTAINER，
     * padding 16，lq2:622）+ 标题「播放菜单」（排版 f11912q/色 f13031g，lq2:624），
     * 外层纵向滚动列表项间距 10dp（lq2:579 C1315kp(10.0f)），尾 padding 为参数（dd0 调用传 8.0f）。
     */
    public static final float HEADER_BODY_SPACING_DP = 10.0f; // lq2:579
    public static final float HEADER_ICON_PAD_DP = 16.0f;     // lq2:622
    public static final float HEADER_DEFAULT_END_PAD_DP = 8.0f; // dd0:69

    /** 开关布局常量（m7527m）。 */
    public static final float SWITCH_ROW_ICON_PAD_DP = 16.0f; // m7529n 缺省 padding(16,6,16,6)

    // ==================== 弹幕设置页装配（xn2 / a91 / ao2，行为规格） ====================

    /** 弹幕行的右侧值文案（xn2:50）：关 → null（不渲染），开 → 样式名。 */
    public static String danmakuRowValue(boolean enabled, boolean listStyle) {
        if (!enabled) {
            return null;
        }
        return listStyle ? VALUE_DANMAKU_LIST_STYLE : VALUE_DANMAKU_SCROLL_STYLE;
    }

    /** 屏蔽词行的右侧值（on5:1696 / a91:170："N 条"）。 */
    public static String blockWordValue(int count) {
        return count + VALUE_UNIT_COUNT;
    }
}
