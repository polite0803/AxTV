/*
 * 来源: src/app/p000/ct5.java
 * 原职责: web 面板模板渲染 —— 读 assets/web/*.html、缓存、替换 {{KEY}} 占位符、
 *         注入主题 CSS（THEME_CSS）与公共样式（PANEL_HEAD）、HTML 转义、Cache-Control: no-store
 * 重构说明: 拆成 ThemeRenderer（模板缓存 + 占位符替换 + 转义）+ AssetSource / ThemeColors 两个平台接口。
 *           模板变量名与 assets/web/*.html 里的占位符一一对应：
 *           {{THEME_CSS}} {{PANEL_HEAD}} {{DEVICE_NAME}}；未知占位符原样保留（对应原 replace 回调）。
 */
package axtv.panel;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * web 面板模板渲染。
 *
 * <p>模板来自 APK 的 {@code assets/web/}，两台服务器共用：Cookie 推送页 {@code submit.html}、
 * 搜索输入页 {@code search.html}，以及被两者包含的 {@code panel-head.html}。
 */
public final class ThemeRenderer {

    /** 占位符：{@code {{THEME_CSS}}} → {@code THEME_CSS}。与原实现的正则完全一致。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Z_]+)\\}\\}");

    private static final String THEME_CSS = "THEME_CSS";
    private static final String PANEL_HEAD = "PANEL_HEAD";
    private static final String PANEL_HEAD_ASSET = "panel-head.html";

    /** APK 资源访问。Android 侧实现：{@code context.getAssets().open("web/" + name)}。 */
    public interface AssetSource {
        byte[] read(String path) throws IOException;
    }

    /**
     * 主题色。原实现在 Android 侧用 MaterialKolor 的 dynamic scheme 生成，
     * 这里只要有 31 个 CSS 变量的取值即可（顺序见 {@link #CSS_VARIABLES}）。
     */
    public interface ThemeColors {
        /** 浅色配色：变量名（不含 {@code --}）→ {@code #RRGGBB}。 */
        Map<String, String> light();

        /** 深色配色，变量集合与 {@link #light()} 相同。 */
        Map<String, String> dark();
    }

    /**
     * 主题 CSS 变量的顺序（对应原 {@code ct5.m2614e} 里 uj3 列表的顺序）。
     * 顺序会影响生成的 CSS，按原样保留。
     */
    public static final java.util.List<String> CSS_VARIABLES = java.util.List.of(
            "primary", "on-primary", "primary-container", "on-primary-container",
            "secondary", "on-secondary", "secondary-container", "on-secondary-container",
            "tertiary", "on-tertiary", "tertiary-container", "on-tertiary-container",
            "error", "on-error", "error-container", "on-error-container",
            "surface", "surface-dim", "surface-bright",
            "sc-lowest", "sc-low", "sc", "sc-high", "sc-highest",
            "on-surface", "on-surface-variant", "outline", "outline-variant",
            "inverse-surface", "inverse-on-surface", "inverse-primary");

    private final AssetSource assets;
    private final ThemeColors colors;
    private final Map<String, String> assetCache = new ConcurrentHashMap<>();
    private final Logger log = Logger.getLogger("WebPanelPage");

    public ThemeRenderer(AssetSource assets, ThemeColors colors) {
        this.assets = assets;
        this.colors = colors;
    }

    /**
     * 渲染模板并包成响应：{@code text/html; charset=utf-8} + {@code Cache-Control: no-store}。
     * 失败时返回 500 纯文本「页面加载失败: …」（与原实现一致）。
     *
     * @param template 模板文件名，如 {@code submit.html}
     * @param variables 业务变量，值会被 HTML 转义
     */
    public PanelRoutes.Response render(String template, Map<String, String> variables) {
        try {
            return PanelRoutes.Response.text(PanelRoutes.SC_OK, PanelRoutes.TEXT_HTML_UTF8, renderToString(template, variables))
                    .withHeader("Cache-Control", "no-store");
        } catch (Exception e) {
            this.log.log(Level.SEVERE, "渲染 " + template + " 失败", e);
            return PanelRoutes.Response.text(PanelRoutes.SC_INTERNAL_ERROR, PanelRoutes.TEXT_PLAIN_UTF8,
                    "页面加载失败: " + e.getMessage());
        }
    }

    /** 纯渲染，便于测试。 */
    public String renderToString(String template, Map<String, String> variables) throws IOException {
        Map<String, String> values = new HashMap<>();
        if (variables != null) {
            variables.forEach((key, value) -> values.put(key, escapeHtml(value)));
        }
        // 主题与公共样式是可信的内部 HTML，不做转义（原实现同样在转义之后才放入）。
        values.put(THEME_CSS, themeCss());
        values.put(PANEL_HEAD, asset(PANEL_HEAD_ASSET));
        String source = asset(template);
        Matcher matcher = PLACEHOLDER.matcher(source);
        // StringBuffer：Matcher.appendReplacement/appendTail 的 StringBuilder 重载是 API 34，
        // minSdk 24 的电视上没有；StringBuffer 重载从 Java 1.4 起就有，输出逐字相同。
        StringBuffer out = new StringBuffer(source.length() + 2048);
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = values.get(key);
            // 未知占位符原样保留（如注释里的 {{KEY}} 说明文字）。
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement == null ? matcher.group() : replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * 主题 CSS：浅色 + 深色两段。
     * 原实现生成 {@code :root{color-scheme:light;--primary:#RRGGBB;…}@media (prefers-color-scheme:dark){…}}。
     */
    public String themeCss() throws IOException {
        String light = cssVariables(this.colors.light());
        String dark = cssVariables(this.colors.dark());
        return ":root{color-scheme:light;" + light
                + "}@media (prefers-color-scheme:dark){:root{color-scheme:dark;" + dark + "}}";
    }

    private static String cssVariables(Map<String, String> palette) {
        StringBuilder out = new StringBuilder(31 * 24);
        for (String name : CSS_VARIABLES) {
            String value = palette == null ? null : palette.get(name);
            if (value == null || value.isEmpty()) {
                continue;
            }
            out.append("--").append(name).append(':').append(value).append(';');
        }
        return out.toString();
    }

    /** 读资源并按文件名缓存（原实现用 ConcurrentHashMap + putIfAbsent）。 */
    public String asset(String name) throws IOException {
        String cached = this.assetCache.get(name);
        if (cached != null) {
            return cached;
        }
        byte[] bytes = this.assets.read("web/" + name);
        if (bytes == null) {
            throw new IOException("模板不存在: web/" + name);
        }
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        String previous = this.assetCache.putIfAbsent(name, content);
        return previous != null ? previous : content;
    }

    /** 与原 {@code ct5.m2610a} 完全一致的转义表（含单引号）。 */
    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("&quot;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** 便于 Android 侧把 ARGB int 转成 CSS 颜色（原实现：取低 24 位、补足 6 位、大写）。 */
    public static String cssColor(int argb) {
        return "#" + String.format("%06X", argb & 0xFFFFFF);
    }

    /** 默认主题：Material 3 基线配色，仅用于在没有动态取色时兜底。 */
    public static ThemeColors defaultColors() {
        Map<String, String> light = new LinkedHashMap<>();
        Map<String, String> dark = new LinkedHashMap<>();
        // 浅色
        light.put("primary", "#6750A4");
        light.put("on-primary", "#FFFFFF");
        light.put("primary-container", "#EADDFF");
        light.put("on-primary-container", "#21005D");
        light.put("secondary", "#625B71");
        light.put("on-secondary", "#FFFFFF");
        light.put("secondary-container", "#E8DEF8");
        light.put("on-secondary-container", "#1D192B");
        light.put("tertiary", "#7D5260");
        light.put("on-tertiary", "#FFFFFF");
        light.put("tertiary-container", "#FFD8E4");
        light.put("on-tertiary-container", "#31111D");
        light.put("error", "#B3261E");
        light.put("on-error", "#FFFFFF");
        light.put("error-container", "#F9DEDC");
        light.put("on-error-container", "#410E0B");
        light.put("surface", "#FEF7FF");
        light.put("surface-dim", "#DED8E1");
        light.put("surface-bright", "#FEF7FF");
        light.put("sc-lowest", "#FFFFFF");
        light.put("sc-low", "#F7F2FA");
        light.put("sc", "#F3EDF7");
        light.put("sc-high", "#ECE6F0");
        light.put("sc-highest", "#E6E0E9");
        light.put("on-surface", "#1D1B20");
        light.put("on-surface-variant", "#49454F");
        light.put("outline", "#79747E");
        light.put("outline-variant", "#CAC4D0");
        light.put("inverse-surface", "#322F35");
        light.put("inverse-on-surface", "#F5EFF7");
        light.put("inverse-primary", "#D0BCFF");
        // 深色
        dark.put("primary", "#D0BCFF");
        dark.put("on-primary", "#381E72");
        dark.put("primary-container", "#4F378B");
        dark.put("on-primary-container", "#EADDFF");
        dark.put("secondary", "#CCC2DC");
        dark.put("on-secondary", "#332D41");
        dark.put("secondary-container", "#4A4458");
        dark.put("on-secondary-container", "#E8DEF8");
        dark.put("tertiary", "#EFB8C8");
        dark.put("on-tertiary", "#492532");
        dark.put("tertiary-container", "#633B48");
        dark.put("on-tertiary-container", "#FFD8E4");
        dark.put("error", "#F2B8B5");
        dark.put("on-error", "#601410");
        dark.put("error-container", "#8C1D18");
        dark.put("on-error-container", "#F9DEDC");
        dark.put("surface", "#141218");
        dark.put("surface-dim", "#141218");
        dark.put("surface-bright", "#3B383E");
        dark.put("sc-lowest", "#0F0D13");
        dark.put("sc-low", "#1D1B20");
        dark.put("sc", "#211F26");
        dark.put("sc-high", "#2B2930");
        dark.put("sc-highest", "#36343B");
        dark.put("on-surface", "#E6E0E9");
        dark.put("on-surface-variant", "#CAC4D0");
        dark.put("outline", "#938F99");
        dark.put("outline-variant", "#49454F");
        dark.put("inverse-surface", "#E6E0E9");
        dark.put("inverse-on-surface", "#322F35");
        dark.put("inverse-primary", "#6750A4");
        return new ThemeColors() {
            @Override
            public Map<String, String> light() {
                return light;
            }

            @Override
            public Map<String, String> dark() {
                return dark;
            }
        };
    }
}
