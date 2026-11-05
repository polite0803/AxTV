/*
 * 来源: 新增（依赖聚合器）
 * 原职责: 无（把散落在各原类里对 Android/第三方能力的直接调用集中成可注入的依赖）
 * 重构说明: 本模块所有需要「系统能力」的类只依赖本对象；纯 JDK 核心层不 import 任何 Android/第三方类。
 *           Android 侧实现下列嵌套接口后注入即可。
 */
package axtv.widget;

import java.util.function.LongSupplier;

/**
 * 小组件模块的依赖集合。所有核心类通过它取得外部能力，从而保持纯 JDK 可编译、可执行。
 */
public final class WidgetEnvironment {

    /** 触发 Glance 更新（原 WidgetUpdater.updateKind / k21.m7006u0）。 */
    public interface UpdateSink {
        /** 更新单个 kind 对应的 GlanceAppWidget。 */
        void updateKind(WidgetKind kind);
        /** 更新所有 kind 对应的 widget，外加 NowPlayingWidget。 */
        void updateAll();
    }

    /** WorkManager 约束（原 androidx.work.Constraints / p000.ch0）。 */
    public static final class Constraints {
        /** 需要联网（原 ch0 第二参 x93.f24443J = NetworkType.CONNECTED）。 */
        public final boolean requiresNetwork;
        /** 需要充电（原 ch0 第三参 false）。 */
        public final boolean requiresCharging;
        /** 需要空闲（原 ch0 第四参 false）。 */
        public final boolean requiresDeviceIdle;
        /** 需要电量不低（原 ch0 第五参 false）。 */
        public final boolean requiresBatteryNotLow;
        /** 需要存储不低（原 ch0 第六参 false）。 */
        public final boolean requiresStorageNotLow;

        public Constraints(boolean requiresNetwork, boolean requiresCharging,
                           boolean requiresDeviceIdle, boolean requiresBatteryNotLow,
                           boolean requiresStorageNotLow) {
            this.requiresNetwork = requiresNetwork;
            this.requiresCharging = requiresCharging;
            this.requiresDeviceIdle = requiresDeviceIdle;
            this.requiresBatteryNotLow = requiresBatteryNotLow;
            this.requiresStorageNotLow = requiresStorageNotLow;
        }

        /** 原代码 ch0 构造里唯一的非默认项：仅要求联网。 */
        public static Constraints networkOnly() {
            return new Constraints(true, false, false, false, false);
        }
    }

    /** 周期工作规格：原始分钟数 + WorkManager 钳制后的 interval/flex 毫秒值。 */
    public record PeriodicSpec(long intervalMinutes, long intervalMillis, long flexMillis) {
    }

    /** WorkManager 抽象（原 androidx.work.WorkManager / p000.ux5）。 */
    public interface WorkScheduler {
        /** enqueueUniqueWork(onceName, REPLACE, OneTimeWorkRequest)，同 kind 的立即刷新互相替换（去抖）。 */
        void enqueueOnce(String uniqueName, WidgetKind kind, Constraints constraints);
        /** enqueueUniquePeriodicWork(periodicName, policy, PeriodicWorkRequest)。 */
        void enqueuePeriodic(String uniqueName, WidgetKind kind, PeriodicSpec spec, Constraints constraints);
        /** cancelUniqueWork(name)。 */
        void cancel(String uniqueName);
        /** AppWidgetManager.getAppWidgetIds(ComponentName(context, receiver(kind))).length > 0。 */
        boolean hasWidgets(WidgetKind kind);
    }

    /** 图片加载/绘制抽象（原 Coil ImageLoader + Canvas + BitmapShader）。 */
    public interface ImageLoader {
        /**
         * 按完整规格加载并裁剪图片，返回不透明句柄（Android 侧为 Bitmap）；失败返回 null。
         * 缓存键、像素尺寸、形状路径均由 WidgetImages / WidgetImageSpec 计算后传入。
         */
        Object load(WidgetImageSpec spec, float[] shapePoints);
        /** 生成纯色形状位图句柄（原 WidgetImages.solidShape）。 */
        Object solidShape(WidgetShape shape, int color, int sizePx, float[] shapePoints);
    }

    /** 媒体键投递（原 WidgetMediaActionReceiver → Activity.decorView.post(dispatchKeyEvent)）。 */
    public interface MediaSink {
        void dispatchMediaKey(int keyCode);
    }

    /** 点赞数格式化（原 DataModelKt.formatCount，不在本模块，Android 侧注入等价实现）。 */
    public interface CountFormatter {
        String formatCount(long count);
    }

    /**
     * 主题取色/配色（原 WidgetThemeKt.widgetPalette$scheme：va4 缓存 + c74.m2048a）。
     * imageSeedArgb 非空时走 va4.m11129b（封面取色 + LruCache），否则走 c74.m2048a（按 themeSource/style/contrast 生成）。
     */
    public interface ColorSchemeFactory {
        /**
         * @param imageSeedArgb 从封面图提取的种子色（ri0.m9896c），无则 null
         * @param seedArgb      SharedPreferences global_seed_argb，0 表示未设置
         * @param dark          是否生成深色方案
         * @param themeSource   原 fl4.m4198e()（t80，"CONTENT" 表示封面取色）
         * @param style         原 fl4.m4199f()（s90 调色板风格）
         * @param contrast      原 fl4.m4197d()（z70 对比度）
         */
        WidgetPalette.Scheme scheme(Long imageSeedArgb, long seedArgb, boolean dark,
                                    String themeSource, String style, String contrast);
    }

    public final WidgetStorage storage;
    public final JsonCodec json;
    public final WidgetSettings settings;
    public final WidgetDataSource dataSource;
    public final UpdateSink updateSink;
    public final WorkScheduler workScheduler;
    public final ImageLoader imageLoader;
    public final MediaSink mediaSink;
    public final CountFormatter countFormatter;
    public final ColorSchemeFactory colorSchemeFactory;
    /** 当前时间（毫秒），默认 System::currentTimeMillis；测试可注入。 */
    public final LongSupplier clock;

    public WidgetEnvironment(WidgetStorage storage, JsonCodec json, WidgetSettings settings,
                             WidgetDataSource dataSource, UpdateSink updateSink,
                             WorkScheduler workScheduler, ImageLoader imageLoader,
                             MediaSink mediaSink, CountFormatter countFormatter,
                             ColorSchemeFactory colorSchemeFactory, LongSupplier clock) {
        this.storage = storage;
        this.json = json;
        this.settings = settings;
        this.dataSource = dataSource;
        this.updateSink = updateSink;
        this.workScheduler = workScheduler;
        this.imageLoader = imageLoader;
        this.mediaSink = mediaSink;
        this.countFormatter = countFormatter;
        this.colorSchemeFactory = colorSchemeFactory;
        this.clock = clock != null ? clock : System::currentTimeMillis;
    }
}
