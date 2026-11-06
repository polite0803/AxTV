/*
 * 外观调色板模型与推导引擎 —— theme_mode / color_contrast / color_style / color_source 的读取方。
 *
 * 设计（为什么这样做）：
 * - 调色板是纯数据 + 纯函数：输入（深浅、对比度、风格、来源、种子）唯一决定输出，
 *   方便「色彩过渡动画」做逐色线性插值（lerp），也让设置页预览与全站颜色共享一份推导口径。
 * - HSL 转换手写：不依赖 Compose 内部实验 API，转换口径固定、可在任意版本编译。
 *
 * 色彩风格映射（color_style 四档，作用对象 = 强调色族 Accent/AccentWarm）：
 * - BY_SCENE 按场景：种子色经 HSL 梯度整理成可读的强调色（饱和度、亮度按深浅底归位），品牌色保持出厂值。
 * - VIBRANT  鲜艳：饱和度 ×1.35（上限 1.0），不改亮度。
 * - SOFT     柔和：饱和度 ×0.55，并与表面色混合 12%（压暗刺眼的高饱和）。
 * - FIDELITY 还原：取到什么用什么——种子色原样作为强调色；品牌色下与 BY_SCENE 相同。
 *
 * 取色种子梯度（color_source = 画面取色/设备取色 且 Settings.globalSeedArgb() 非 0 时）：
 * - 主强调色：seed 的色相，饱和度 max(seed, 0.60)，亮度 暗底 0.62 / 浅底 0.42；
 * - 暖强调色：色相 +30°（对应原版抖音红位），饱和度 max(seed, 0.72)，亮度 暗底 0.60 / 浅底 0.46；
 * - 表面四件套（bg/surface/surfaceHigh/divider）：seed 色相 + 低饱和（≤0.16）轻染，只动亮度，
 *   保证正文对比度不被染色破坏。
 */
package com.github.axtv.dv.ui.appearance

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import axtv.settings.Settings
import axtv.settings.SettingsTypes

/** 一套完整可绘制的调色板；dark 标记供 TV Material3 选 dark/light colorScheme。 */
internal data class AxPalette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentWarm: Color,
    val divider: Color,
)

/** 逐色插值（color_transition=自动 的动画基元）；dark 在过半时翻转。 */
internal fun lerpPalette(from: AxPalette, to: AxPalette, fraction: Float): AxPalette = AxPalette(
    dark = if (fraction < 0.5f) from.dark else to.dark,
    bg = lerp(from.bg, to.bg, fraction),
    surface = lerp(from.surface, to.surface, fraction),
    surfaceHigh = lerp(from.surfaceHigh, to.surfaceHigh, fraction),
    textPrimary = lerp(from.textPrimary, to.textPrimary, fraction),
    textSecondary = lerp(from.textSecondary, to.textSecondary, fraction),
    accent = lerp(from.accent, to.accent, fraction),
    accentWarm = lerp(from.accentWarm, to.accentWarm, fraction),
    divider = lerp(from.divider, to.divider, fraction),
)

internal object AxPaletteEngine {

    // ---- 品牌基线：深色即改造前 AxTvColors 的出厂占位表，浅色为逐项对照值 ----
    private val DARK_BG = Color(0xFF0B0B0E)
    private val DARK_SURFACE = Color(0xFF16161C)
    private val DARK_SURFACE_HIGH = Color(0xFF202028)
    private val DARK_TEXT = Color(0xFFF5F5F7)
    private val DARK_DIVIDER = Color(0xFF2C2C34)
    private val BRAND_ACCENT = Color(0xFF25F4EE)      // 抖音青
    private val BRAND_ACCENT_WARM = Color(0xFFFE2C55) // 抖音红

    private val LIGHT_BG = Color(0xFFF4F5F8)
    private val LIGHT_SURFACE = Color(0xFFFFFFFF)
    private val LIGHT_SURFACE_HIGH = Color(0xFFE6E7EE)
    private val LIGHT_TEXT = Color(0xFF15151A)
    private val LIGHT_DIVIDER = Color(0xFFD3D4DD)

    fun compute(
        dark: Boolean,
        contrast: SettingsTypes.ColorContrast,
        style: SettingsTypes.ColorStyle,
        source: SettingsTypes.ColorSource,
        seedArgb: Int,
    ): AxPalette {
        val textPrimary = if (dark) DARK_TEXT else LIGHT_TEXT
        var bg = if (dark) DARK_BG else LIGHT_BG
        var surface = if (dark) DARK_SURFACE else LIGHT_SURFACE
        var surfaceHigh = if (dark) DARK_SURFACE_HIGH else LIGHT_SURFACE_HIGH
        var divider = if (dark) DARK_DIVIDER else LIGHT_DIVIDER
        var accent = BRAND_ACCENT
        var accentWarm = BRAND_ACCENT_WARM

        // 种子只在非品牌来源且已取到色（0 = 未设置，见 Settings.globalSeedArgb 注释）时参与推导。
        val seed = if (source != SettingsTypes.ColorSource.BRAND && seedArgb != 0) argbToColor(seedArgb) else null
        if (seed != null) {
            val hsl = seed.toHsl()
            val tintS = (hsl[1] * 0.22f).coerceAtMost(0.16f)
            bg = hslToColor(hsl[0], tintS, if (dark) 0.045f else 0.96f)
            surface = hslToColor(hsl[0], tintS * 1.1f, if (dark) 0.09f else 0.99f)
            surfaceHigh = hslToColor(hsl[0], tintS * 1.3f, if (dark) 0.15f else 0.92f)
            divider = hslToColor(hsl[0], tintS * 1.6f, if (dark) 0.22f else 0.85f)
            when (style) {
                SettingsTypes.ColorStyle.FIDELITY -> {
                    accent = seed
                    accentWarm = seed
                }
                else -> {
                    accent = hslToColor(hsl[0], hsl[1].coerceAtLeast(0.6f), if (dark) 0.62f else 0.42f)
                    accentWarm = hslToColor(hsl[0] + 30f, hsl[1].coerceAtLeast(0.72f), if (dark) 0.60f else 0.46f)
                }
            }
        }

        when (style) {
            SettingsTypes.ColorStyle.VIBRANT -> {
                accent = accent.boostSaturation(1.35f)
                accentWarm = accentWarm.boostSaturation(1.35f)
            }
            SettingsTypes.ColorStyle.SOFT -> {
                accent = accent.softenOnto(surface)
                accentWarm = accentWarm.softenOnto(surface)
            }
            // BY_SCENE 的「整理」已经发生在梯度推导里；FIDELITY 语义即不加工，此处不再动。
            SettingsTypes.ColorStyle.BY_SCENE, SettingsTypes.ColorStyle.FIDELITY -> Unit
        }

        // 对比度三档：抬高次要文字的不透明度与分隔线向正文色的混合比，三档肉眼可分。
        val secondaryAlpha = when (contrast) {
            SettingsTypes.ColorContrast.STANDARD -> if (dark) 0.70f else 0.62f
            SettingsTypes.ColorContrast.MEDIUM -> if (dark) 0.84f else 0.80f
            SettingsTypes.ColorContrast.HIGH -> if (dark) 0.96f else 0.94f
        }
        val dividerMix = when (contrast) {
            SettingsTypes.ColorContrast.STANDARD -> 0f
            SettingsTypes.ColorContrast.MEDIUM -> 0.35f
            SettingsTypes.ColorContrast.HIGH -> 0.60f
        }
        return AxPalette(
            dark = dark,
            bg = bg,
            surface = surface,
            surfaceHigh = surfaceHigh,
            textPrimary = textPrimary,
            textSecondary = textPrimary.copy(alpha = secondaryAlpha),
            accent = accent,
            accentWarm = accentWarm,
            divider = lerp(divider, textPrimary, dividerMix),
        )
    }

    /** 未取色时的品牌色回退（source=BRAND 或种子为 0）。 */
    fun brand(dark: Boolean, contrast: SettingsTypes.ColorContrast): AxPalette = compute(
        dark = dark,
        contrast = contrast,
        style = SettingsTypes.ColorStyle.BY_SCENE,
        source = SettingsTypes.ColorSource.BRAND,
        seedArgb = 0,
    )

    // ──────────────── 自用的 RGB ↔ HSL（口径：h 0..360，s/l 0..1）────────────────

    private fun argbToColor(argb: Int): Color = Color(
        red = ((argb shr 16) and 0xFF) / 255f,
        green = ((argb shr 8) and 0xFF) / 255f,
        blue = (argb and 0xFF) / 255f,
    )

    private fun Color.toHsl(): FloatArray {
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val lightness = (max + min) / 2f
        if (max == min) return floatArrayOf(0f, 0f, lightness)
        val delta = max - min
        val saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
        val hue = when (max) {
            red -> (green - blue) / delta + (if (green < blue) 6f else 0f)
            green -> (blue - red) / delta + 2f
            else -> (red - green) / delta + 4f
        } * 60f
        return floatArrayOf(hue, saturation, lightness)
    }

    private fun hslToColor(hueDeg: Float, saturation: Float, lightness: Float): Color {
        val h = ((hueDeg % 360f) + 360f) / 360f
        val s = saturation.coerceIn(0f, 1f)
        val l = lightness.coerceIn(0f, 1f)
        if (s == 0f) return Color(l, l, l)
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        return Color(
            red = hueToChannel(p, q, h + 1f / 3f),
            green = hueToChannel(p, q, h),
            blue = hueToChannel(p, q, h - 1f / 3f),
        )
    }

    private fun hueToChannel(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    private fun Color.boostSaturation(factor: Float): Color {
        val hsl = toHsl()
        return hslToColor(hsl[0], (hsl[1] * factor).coerceAtMost(1f), hsl[2])
    }

    private fun Color.softenOnto(surface: Color): Color {
        val hsl = toHsl()
        val base = hslToColor(hsl[0], hsl[1] * 0.55f, hsl[2])
        // 与表面混 12%：低对比环境下强调色不「炸眼」，与柔和档文案一致。
        return lerp(base, surface, 0.12f)
    }
}
