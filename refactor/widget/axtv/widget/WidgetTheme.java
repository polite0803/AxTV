/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetThemeKt.java (168 行) + WidgetPalette.java (91 行)
 *       + WidgetUiKt.java:4277-4325 (WidgetTheme 提供 LocalWidgetPalette / ColorProviders)
 * 原职责: widgetPalette(context, coverUrl) —— 读设置(主题来源/风格/对比度/全局种子色/夜间模式)，
 *         CONTENT 且封面非空时经 ri0 取封面种子色，生成亮/暗两套 ColorScheme 包成 WidgetPalette；
 *         WidgetTheme Composable 把 ColorProviders + LocalWidgetPalette 注入组件树
 * 重构说明: 取色与配色用 WidgetEnvironment.ColorSchemeFactory / SeedExtractor 接口隔离；
 *           分支、默认值、双方案与 isDark 判定逐字还原。Glance 的 CompositionLocal 注入属绘制，见 NOTES 待迁移。
 */
package axtv.widget;

/** 小组件主题构建（原 WidgetThemeKt.widgetPalette）。 */
public final class WidgetTheme {

    /** 主题来源：CONTENT 表示按封面图动态取色（WidgetThemeKt.java:96）。 */
    public static final String SOURCE_CONTENT = "CONTENT";

    /** 原 ri0.m9896c：用 Coil 加载封面并提取主色，返回 ARGB；无结果返回 null。 */
    @FunctionalInterface
    public interface SeedExtractor {
        Long extract(String coverUrl);
    }

    private final WidgetEnvironment env;
    private final SeedExtractor seedExtractor;

    public WidgetTheme(WidgetEnvironment env) {
        this(env, url -> null);
    }

    public WidgetTheme(WidgetEnvironment env, SeedExtractor seedExtractor) {
        this.env = env;
        this.seedExtractor = seedExtractor != null ? seedExtractor : url -> null;
    }

    /** 对应 widgetPalette$default(context, null, cont, 2, null)：不传封面 URL。 */
    public WidgetPalette palette() {
        return palette(null);
    }

    /**
     * 对应 widgetPalette(context, coverUrl)（:59-148）：
     * 1) 读 themeSource / style / contrast / global_seed_argb / isDark；
     * 2) themeSource==CONTENT 且 coverUrl 非空白 → 取封面种子色（否则为 null）；
     * 3) 分别生成 light(false) / dark(true) 两套 Scheme；
     * 4) new WidgetPalette(light, dark, isDark)。
     */
    public WidgetPalette palette(String coverUrl) {
        WidgetSettings s = env.settings;
        String source = s.themeSource();
        String style = s.paletteStyle();
        String contrast = s.contrast();
        long seedArgb = s.globalSeedArgb();
        boolean dark = s.isNightMode();

        Long imageSeed = null;
        if (SOURCE_CONTENT.equals(source) && coverUrl != null && !coverUrl.isBlank()) {
            imageSeed = seedExtractor.extract(coverUrl);
        }

        WidgetPalette.Scheme light = env.colorSchemeFactory.scheme(
                imageSeed, seedArgb, false, source, style, contrast);
        WidgetPalette.Scheme darkScheme = env.colorSchemeFactory.scheme(
                imageSeed, seedArgb, true, source, style, contrast);
        return new WidgetPalette(light, darkScheme, dark);
    }

    /** uiMode & 48 == 32 的夜间判定（:95），由 WidgetSettings.isNightMode 提供。 */
    public boolean isDark() {
        return env.settings.isNightMode();
    }

    /** 原 WidgetTheme Composable 的语义：把 providers 与 palette 暴露给组件树（Android 侧 CompositionLocal）。 */
    public WidgetPalette.Providers providers(WidgetPalette palette) {
        return palette.getProviders();
    }
}
