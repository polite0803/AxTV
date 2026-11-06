/*
 * 外观运行时状态中枢 —— 全站唯一「读设置算调色板」的快照状态持有者。
 *
 * 接线关系（谁读谁）：
 * - AxTvColors 的每个属性 → currentPalette（快照状态），组合期读取即订阅，调色板一变全站重组；
 * - AxTvTheme → targetPalette()/animateTo()（theme_mode、color_transition 的消费点）；
 * - SettingsBinder 写外观相关键后 notifyChanged()（version 自增）→ 所有订阅方重算；
 * - HomeFeedScreen / DetailScreen → sampleCoverSeed()（color_source=画面取色 的取色入口，
 *   种子同时落 Settings.globalSeedArgb()，重启后由 currentPalette 初值直接恢复）；
 * - SettingsScreen「取色 / 预览」行 → PalettePreviewDialog 读 seedArgb/seedSourceLabel。
 */
package com.github.axtv.dv.ui.appearance

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import axtv.settings.Settings
import axtv.settings.SettingsTypes
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.github.axtv.dv.adapter.AppRuntime
import android.graphics.drawable.BitmapDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object AxAppearance {

    /** 外观设置写入计数：SettingsBinder 写 theme/font/color/ambient 键后 +1，组合期读它即订阅变更。 */
    val version = mutableIntStateOf(0)

    /** 当前取色种子（ARGB，0=未取色）；与 Settings.globalSeedArgb() 双向同步。 */
    val seedArgb = mutableIntStateOf(0)

    /** 种子来源说明（「画面取色」/「设备取色」/回落说明），供取色预览页展示。 */
    var seedSourceLabel by mutableStateOf<String?>(null)
        private set

    /** 正在绘制的调色板：AxTvColors 的唯一数据源，动画期间为插值中间态。 */
    val currentPalette = mutableStateOf(initialPalette())

    fun notifyChanged() {
        version.intValue++
    }

    /** theme_mode=自动 时按系统 uiMode 判深浅；其余按显式档位。 */
    fun resolveDark(settings: Settings?, uiMode: Int): Boolean = when (settings?.themeMode()) {
        SettingsTypes.ThemeMode.LIGHT -> false
        SettingsTypes.ThemeMode.DARK -> true
        else -> (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /** 按当前设置与种子算目标调色板（组合期调用，输入全部来自快照状态或 settings 现值）。 */
    fun targetPalette(uiMode: Int): AxPalette {
        val settings = AppRuntime.settings
        return AxPaletteEngine.compute(
            dark = resolveDark(settings, uiMode),
            contrast = settings?.colorContrast() ?: SettingsTypes.ColorContrast.STANDARD,
            style = settings?.colorStyle() ?: SettingsTypes.ColorStyle.BY_SCENE,
            source = settings?.colorSource() ?: SettingsTypes.ColorSource.BRAND,
            seedArgb = seedArgb.intValue,
        )
    }

    /** color_transition 消费点：自动=逐色 380ms 插值；关闭=立即替换。 */
    suspend fun animateTo(target: AxPalette, animate: Boolean) {
        val from = currentPalette.value
        if (from == target) return
        if (!animate) {
            currentPalette.value = target
            return
        }
        Animatable(0f).animateTo(1f, tween(380)) {
            currentPalette.value = lerpPalette(from, target, value)
        }
        currentPalette.value = target
    }

    /** 画面取色：Coil 拉封面小尺寸位图 → Palette 取鲜艳主色 → 落种子并持久化。 */
    suspend fun sampleCoverSeed(context: Context, url: String) {
        val settings = AppRuntime.settings ?: return
        if (settings.colorSource() != SettingsTypes.ColorSource.CONTENT || url.isBlank()) return
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    // Palette 要逐像素读色，硬件位图拿不到像素。
                    .allowHardware(false)
                    // 取色只需要缩略尺寸；与背景 AsyncImage 的原尺寸分列两条缓存，互不影响。
                    .size(128)
                    .build()
                (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
            }.getOrNull()
        } ?: return
        val swatches = runCatching {
            withContext(Dispatchers.Default) {
                Palette.from(bitmap).maximumColorCount(24).generate()
            }
        }.getOrNull() ?: return
        val argb = (swatches.vibrantSwatch ?: swatches.dominantSwatch)?.rgb ?: return
        applySeed(argb, "画面取色")
    }

    /** 设备取色：系统壁纸主色；TV 上多数 ROM 无壁纸服务数据，null/异常一律回落品牌色。 */
    fun sampleDeviceSeed(context: Context) {
        val settings = AppRuntime.settings ?: return
        if (settings.colorSource() != SettingsTypes.ColorSource.DEVICE) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            // getWallpaperColors 需要 API 27（minSdk 24）：低版本明确告知回落，不装作取到色。
            applySeed(0, "设备取色不可用（系统低于 8.1），回落品牌色")
            return
        }
        val argb = runCatching {
            androidx.core.content.ContextCompat.getSystemService(context, android.app.WallpaperManager::class.java)
                ?.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
                ?.primaryColor
                ?.toArgb()
        }.getOrNull()
        if (argb == null) {
            applySeed(0, "设备取色不可用（无壁纸数据），回落品牌色")
        } else {
            applySeed(argb, "设备取色")
        }
    }

    /** ambient_backdrop 开关读取点；读 version 订阅「氛围背景」开关的实时变化。 */
    fun ambientEnabled(): Boolean {
        version.intValue
        return AppRuntime.settings?.ambientBackdrop() == true
    }

    private fun applySeed(argb: Int, label: String) {
        if (seedArgb.intValue == argb && seedSourceLabel == label) return
        seedArgb.intValue = argb
        seedSourceLabel = label
        AppRuntime.settings?.setGlobalSeedArgb(argb)
    }

    /** 冷启动初值：不经过动画直接按持久化种子与系统 uiMode 算好，避免首帧闪旧色。 */
    private fun initialPalette(): AxPalette {
        val settings = AppRuntime.settings
        seedArgb.intValue = settings?.globalSeedArgb() ?: 0
        // 对象初始化阶段不能碰 Resources.getSystem()：它在 JVM 单测里是抛异常的 stub，
        // 会让整个 AxAppearance 类初始化失败（ExceptionInInitializerError），
        // 连带 SettingsBinder 的外观同步分支一起红。
        // 这里按「非显式浅色就先用暗色基线」占位，AxTvTheme 组合期会用
        // LocalConfiguration.current.uiMode 立刻算出真值。
        val dark = settings?.themeMode() != SettingsTypes.ThemeMode.LIGHT
        return AxPaletteEngine.compute(
            dark = dark,
            contrast = settings?.colorContrast() ?: SettingsTypes.ColorContrast.STANDARD,
            style = settings?.colorStyle() ?: SettingsTypes.ColorStyle.BY_SCENE,
            source = settings?.colorSource() ?: SettingsTypes.ColorSource.BRAND,
            seedArgb = seedArgb.intValue,
        )
    }
}
