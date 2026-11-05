/*
 * 来源: src/app/p000/k80.java (211 行) + lq2.java 中的用色点
 * 原职责: k80 是 Material3 ColorScheme（48 个颜色 token，构造器 k80.java:160-209，
 *         toString k80.java:261-275 给出每个字段的标准 M3 角色名）。
 *         播放器菜单/弹幕设置 UI 全部从该 scheme 取色。
 * 重构说明: 行为规格模式 —— 本类只落「角色 → 播放器菜单内用途」映射与透明度换算规则，
 *         具体色值（long ARGB）由 Android 侧主题提供，不在纯 JDK 层固化。
 */
package axtv.ui;

import java.util.LinkedHashMap;
import java.util.Map;

/** 播放器菜单 UI 的配色角色表（k80 = Material3 ColorScheme）。 */
public final class PlayerUiPalette {

    // ---- k80 字段 → M3 角色（k80.java toString:261-275） ----
    public static final String PRIMARY = "f11896a";              // k80.java:83
    public static final String ON_PRIMARY = "f11897b";           // k80.java:86
    public static final String PRIMARY_CONTAINER = "f11898c";    // k80.java:89
    public static final String ON_PRIMARY_CONTAINER = "f11899d"; // k80.java:92
    public static final String INVERSE_PRIMARY = "f11900e";      // k80.java:95
    public static final String SECONDARY = "f11901f";            // k80.java:98
    public static final String ON_SECONDARY = "f11902g";         // k80.java:101
    public static final String SECONDARY_CONTAINER = "f11903h";  // k80.java:104
    public static final String ON_SECONDARY_CONTAINER = "f11904i"; // k80.java:107
    public static final String TERTIARY = "f11905j";             // k80.java:110
    public static final String ON_TERTIARY = "f11906k";          // k80.java:113
    public static final String TERTIARY_CONTAINER = "f11907l";   // k80.java:116
    public static final String ON_TERTIARY_CONTAINER = "f11908m"; // k80.java:119
    public static final String BACKGROUND = "f11909n";           // k80.java:122
    public static final String ON_BACKGROUND = "f11910o";        // k80.java:125
    public static final String SURFACE = "f11911p";              // k80.java:128
    public static final String ON_SURFACE = "f11912q";           // k80.java:131
    public static final String SURFACE_VARIANT = "f11913r";      // k80.java:134
    public static final String ON_SURFACE_VARIANT = "f11914s";   // k80.java:137
    public static final String SURFACE_TINT = "f11915t";         // k80.java:140
    public static final String INVERSE_SURFACE = "f11916u";      // k80.java:143
    public static final String INVERSE_ON_SURFACE = "f11917v";   // k80.java:146
    public static final String ERROR = "f11918w";                // k80.java:149
    public static final String ON_ERROR = "f11919x";             // k80.java:152
    public static final String ERROR_CONTAINER = "f11920y";      // k80.java:155
    public static final String ON_ERROR_CONTAINER = "f11921z";   // k80.java:158
    public static final String OUTLINE = "f11871A";              // k80.java:8
    public static final String OUTLINE_VARIANT = "f11872B";      // k80.java:11
    public static final String SCRIM = "f11873C";                // k80.java:14
    // f11874D..f11892V (k80.java:17-74) 为 M3 扩展 surface 系（surfaceBright/Container 系/surfaceDim），菜单未直接使用。

    private final Map<String, Long> scheme;

    /** Android 侧把主题色按角色注入；纯核心层不做颜色运算假设。 */
    public PlayerUiPalette(Map<String, Long> schemeByToken) {
        this.scheme = Map.copyOf(schemeByToken);
    }

    /** 取角色色值；未知角色抛 IllegalArgumentException（边界校验）。 */
    public long color(String role) {
        Long v = scheme.get(role);
        if (v == null) {
            throw new IllegalArgumentException("no color for role " + role);
        }
        return v;
    }

    /**
     * 透明度缩放（x70.m11833c(color, factor)，lq2 内多次出现）：
     * 结果 = (color & 0xFFFFFFFFFFFFFF00L) | round(alpha * factor)。
     * factor 取值：副标题/次要文案 0.78f（lq2:1457/1613/20189）、禁用态 0.7f（lq2:1443/1888）。
     */
    public static long withAlpha(long color, float factor) {
        int alpha = (int) ((color >>> 24) & 0xFFL);
        int scaled = Math.round(alpha * factor);
        if (scaled > 255) {
            scaled = 255;
        }
        return (color & 0x00FFFFFFL) | ((scaled & 0xFFL) << 24);
    }

    /** 默认行底色 token（lq2:1401/1851 未传色时 x70.f24403f = Color.Unspecified → 透明）。 */
    public static final long COLOR_UNSPECIFIED = 16L; // x70.f24403f 的哨兵值（Color.Unspecified）

    /** 菜单组件 → 颜色角色的映射表（来源行号为 lq2.java）。 */
    public static Map<String, String> menuRoleTable() {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("menu_row.selected.bg", PRIMARY_CONTAINER);        // lq2:1449/1904
        t.put("menu_row.selected.fg", ON_PRIMARY_CONTAINER);     // lq2:1437/1892
        t.put("menu_row.title.fg", ON_SURFACE);                  // lq2:1439/1894
        t.put("menu_row.value.fg", ON_SURFACE_VARIANT);          // lq2:1559/1562
        t.put("menu_row.disabled.fg", withAlphaToken(ON_SURFACE_VARIANT, 0.7f)); // lq2:1443
        t.put("menu_row.bg.default", "f11877G");                 // lq2:1606（surfaceContainer 系）
        t.put("menu_icon.bg.default", "f11877G");                // lq2:723
        t.put("menu_icon.fg.default", ON_SURFACE_VARIANT);       // lq2:724
        t.put("menu_option.selected.bg", PRIMARY_CONTAINER);     // lq2:914
        t.put("menu_option.selected.fg", ON_PRIMARY_CONTAINER);  // lq2:904
        t.put("menu_option.bg.default", "f11877G");              // lq2:914
        t.put("menu_option.fg.default", ON_SURFACE_VARIANT);     // lq2:907
        t.put("menu_segment.selected.bg", PRIMARY_CONTAINER);    // lq2:16908
        t.put("menu_segment.selected.fg", ON_PRIMARY_CONTAINER); // lq2:16907
        t.put("menu_segment.bg.default", "f11877G");
        t.put("menu_segment.fg.default", ON_SURFACE_VARIANT);    // lq2:16907 else 分支
        t.put("menu_header.bg", TERTIARY_CONTAINER);             // lq2:606
        t.put("menu_header.icon", ON_TERTIARY_CONTAINER);        // lq2:622
        t.put("menu_slider.bg.default", "f11877G");              // lq2:19899
        t.put("menu_slider.border", "f11878H");                  // lq2:19899
        t.put("row_switch.checked.track", PRIMARY);              // lq2:20490
        t.put("row_switch.unchecked.track", OUTLINE);            // lq2:20492
        t.put("row_switch.thumb", SURFACE);                      // lq2:20499 (f11911p)
        return t;
    }

    private static String withAlphaToken(String role, float f) {
        return role + "@" + f;
    }
}
