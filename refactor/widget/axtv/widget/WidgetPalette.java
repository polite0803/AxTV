/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetPalette.java (91 行)
 *       + WidgetThemeKt.java:157-167 (widgetPalette$scheme)
 * 原职责: 持有亮/暗两套 Material3 ColorScheme 与 isDark；
 *          每个颜色令牌都包装成 Glance 的 ColorProvider(day, night)，并额外暴露 6 个 surface/fixed 令牌
 * 重构说明: 核心层不依赖 Compose/Glance，用 Scheme(令牌→ARGB) + ColorProvider(day/night) 表达；
 *          TOKENS 全表逐项对应原 k80 ColorScheme 字段，字段编号对照见 NOTES.md 颜色令牌表。
 */
package axtv.widget;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 小组件调色板（亮/暗双方案 + 逐令牌 day/night 供应器）。 */
public final class WidgetPalette {

    /** Material3 ColorScheme 全部令牌；顺序对应原 k80 的 f11896a…f11921z / f11871A / f11876F…f11883M。 */
    public static final List<String> TOKENS = List.of(
            "primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "inversePrimary",
            "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
            "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
            "background", "onBackground", "surface", "onSurface",
            "surfaceVariant", "onSurfaceVariant", "surfaceTint",
            "inverseSurface", "inverseOnSurface",
            "error", "onError", "errorContainer", "onErrorContainer",
            "outline", "outlineVariant", "scrim",
            "surfaceBright", "surfaceDim",
            "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest", "surfaceContainerLow",
            "surfaceContainerLowest",
            "primaryFixed", "primaryFixedDim", "onPrimaryFixed", "onPrimaryFixedVariant",
            "secondaryFixed", "secondaryFixedDim", "onSecondaryFixed", "onSecondaryFixedVariant",
            "tertiaryFixed", "tertiaryFixedDim", "onTertiaryFixed", "onTertiaryFixedVariant");

    /** 一套 ColorScheme：令牌名 → ARGB long（原 p000.k80）。 */
    public static final class Scheme {
        private final Map<String, Long> colors;

        public Scheme(Map<String, Long> colors) {
            Map<String, Long> m = new HashMap<>();
            for (String t : TOKENS) {
                Long v = colors != null ? colors.get(t) : null;
                m.put(t, v != null ? v : 0L);
            }
            this.colors = Collections.unmodifiableMap(m);
        }

        public static Scheme blank() {
            return new Scheme(Map.of());
        }

        public long get(String token) {
            Long v = colors.get(token);
            return v != null ? v : 0L;
        }

        public Map<String, Long> asMap() {
            return colors;
        }
    }

    /** Glance ColorProvider：白天/夜间两个 ARGB 值（原 p000.pp0）。 */
    public record ColorProvider(long day, long night) {
        public long resolve(boolean dark) {
            return dark ? night : day;
        }
    }

    /** 全令牌的 day/night 供应器集合（原 p000.l51 ColorProviders）。 */
    public static final class Providers {
        private final Map<String, ColorProvider> map;

        public Providers(Scheme light, Scheme dark,
                         java.util.function.LongUnaryOperator widgetBackgroundDeriver) {
            Map<String, ColorProvider> m = new HashMap<>();
            for (String t : TOKENS) {
                m.put(t, new ColorProvider(light.get(t), dark.get(t)));
            }
            // 原 WidgetPalette.java:39 最后一个 pp0：jl2.m6711k(secondaryContainer) 派生的 widgetBackground。
            // 该 HCT 亮度变换属 material-color-utilities，核心层用可注入算子表达（默认恒等）。
            m.put("widgetBackground", new ColorProvider(
                    widgetBackgroundDeriver.applyAsLong(light.get("secondaryContainer")),
                    widgetBackgroundDeriver.applyAsLong(dark.get("secondaryContainer"))));
            this.map = Collections.unmodifiableMap(m);
        }

        public ColorProvider get(String token) {
            return map.getOrDefault(token, new ColorProvider(0L, 0L));
        }

        /**
         * 原 j80/l51（Glance ColorProviders）27 个字段 → 令牌映射。
         * 顺序对照 WidgetPalette.java:31-39 的 pp0 实参与 j80 构造器 :89-115。
         */
        public static final String[] FIELD_TOKENS = {
                "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",       // a,b,c,d
                "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer", // e,f,g,h
                "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",   // i,j,k,l
                "error", "errorContainer", "onError", "onErrorContainer",               // m,n,o,p
                "background", "onBackground", "surface", "onSurface",                   // q,r,s,t
                "surfaceVariant", "onSurfaceVariant", "outline",                        // u,v,w
                "inverseOnSurface", "inverseSurface", "inversePrimary",                 // x,y,z
                "widgetBackground"                                                      // A（由 secondaryContainer 派生，见 NOTES）
        };

        /** 原 j80 字段名（f10826a…f10825A），与 FIELD_TOKENS 一一对应。 */
        public static final String[] FIELD_NAMES = {
                "f10826a", "f10827b", "f10828c", "f10829d", "f10830e", "f10831f", "f10832g",
                "f10833h", "f10834i", "f10835j", "f10836k", "f10837l", "f10838m", "f10839n",
                "f10840o", "f10841p", "f10842q", "f10843r", "f10844s", "f10845t", "f10846u",
                "f10847v", "f10848w", "f10849x", "f10850y", "f10851z", "f10825A"
        };

        /** 按原 j80 字段名取色（供渲染规格逐字段引用）。 */
        public ColorProvider byField(String j80Field) {
            for (int i = 0; i < FIELD_NAMES.length; i++) {
                if (FIELD_NAMES[i].equals(j80Field)) {
                    return get(FIELD_TOKENS[i]);
                }
            }
            return get(j80Field);
        }

        // 语义取值器（供 WidgetRenderSpec 使用），注释为原 j80 字段名
        public ColorProvider primary()              { return get("primary"); }              // f10826a
        public ColorProvider primaryContainer()     { return get("primaryContainer"); }     // f10828c
        public ColorProvider onPrimaryContainer()   { return get("onPrimaryContainer"); }   // f10829d
        public ColorProvider secondary()            { return get("secondary"); }            // f10830e
        public ColorProvider onSecondary()          { return get("onSecondary"); }          // f10831f
        public ColorProvider secondaryContainer()   { return get("secondaryContainer"); }   // f10832g
        public ColorProvider onSecondaryContainer() { return get("onSecondaryContainer"); } // f10833h
        public ColorProvider tertiary()             { return get("tertiary"); }             // f10834i
        public ColorProvider onTertiary()           { return get("onTertiary"); }           // f10835j
        public ColorProvider tertiaryContainer()    { return get("tertiaryContainer"); }    // f10836k
        public ColorProvider onTertiaryContainer()  { return get("onTertiaryContainer"); }  // f10837l
        public ColorProvider onSurface()            { return get("onSurface"); }            // f10845t
        public ColorProvider onSurfaceVariant()     { return get("onSurfaceVariant"); }     // f10847v
        public ColorProvider errorContainer()       { return get("errorContainer"); }       // f11920y（k80 字段，WidgetStatus 错误态用）
        public ColorProvider onErrorContainer()     { return get("onErrorContainer"); }     // f11921z

        public Map<String, ColorProvider> asMap() {
            return map;
        }
    }

    private final Scheme light;
    private final Scheme dark;
    private final boolean isDark;
    private final Providers providers;
    private final ColorProvider surfaceContainerLow;
    private final ColorProvider surfaceContainer;
    private final ColorProvider surfaceContainerHigh;
    private final ColorProvider surfaceContainerHighest;
    private final ColorProvider primaryFixed;
    private final ColorProvider onPrimaryFixed;

    public WidgetPalette(Scheme light, Scheme dark, boolean isDark) {
        this(light, dark, isDark, java.util.function.LongUnaryOperator.identity());
    }

    /**
     * @param widgetBackgroundDeriver 原 jl2.m6711k 的等价实现：以 secondaryContainer 的 ARGB
     *                                算出 Glance widgetBackground（HCT 亮度 +5/-10）。
     *                                Android 侧应注入 material-color-utilities 的等价函数。
     */
    public WidgetPalette(Scheme light, Scheme dark, boolean isDark,
                         java.util.function.LongUnaryOperator widgetBackgroundDeriver) {
        this.light = light;
        this.dark = dark;
        this.isDark = isDark;
        this.providers = new Providers(light, dark, widgetBackgroundDeriver);
        this.surfaceContainerLow = providers.get("surfaceContainerLow");
        this.surfaceContainer = providers.get("surfaceContainer");
        this.surfaceContainerHigh = providers.get("surfaceContainerHigh");
        this.surfaceContainerHighest = providers.get("surfaceContainerHighest");
        this.primaryFixed = providers.get("primaryFixed");
        this.onPrimaryFixed = providers.get("onPrimaryFixed");
    }

    /** getCurrent()：isDark ? dark : light（原 :48-50）。 */
    public Scheme getCurrent() {
        return isDark ? dark : light;
    }

    public Scheme getLight() {
        return light;
    }

    public Scheme getDark() {
        return dark;
    }

    public boolean isDark() {
        return isDark;
    }

    public Providers getProviders() {
        return providers;
    }

    public ColorProvider getSurfaceContainerLow()     { return surfaceContainerLow; }
    public ColorProvider getSurfaceContainer()        { return surfaceContainer; }
    public ColorProvider getSurfaceContainerHigh()    { return surfaceContainerHigh; }
    public ColorProvider getSurfaceContainerHighest() { return surfaceContainerHighest; }
    public ColorProvider getPrimaryFixed()            { return primaryFixed; }
    public ColorProvider getOnPrimaryFixed()          { return onPrimaryFixed; }
}
