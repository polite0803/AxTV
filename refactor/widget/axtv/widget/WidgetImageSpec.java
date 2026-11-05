/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetImages.java (454 行)
 *       load$default:113-130、load:288-428、render:133-166、solidShape:430-453
 * 原职责: 一次图片加载的完整规格（URL / dp 尺寸 / 圆角 / 形状 / 描边色 / 内衬色 / 密度 → 像素尺寸 / 缓存键）
 * 重构说明: 核心层不解码 Bitmap，把「加载什么、裁成多大、用什么键缓存」算成不可变规格对象，
 *           连同形状路径采样点一起交给 WidgetEnvironment.ImageLoader（Android 侧 Coil + Canvas 实现）。
 */
package axtv.widget;

import java.util.Objects;

/** 一次图片加载的完整规格（纯数据，可执行计算）。 */
public final class WidgetImageSpec {

    /** LruCache 容量（WidgetImages.java:37 new LruCache(48)）。 */
    public static final int CACHE_MAX_ENTRIES = 48;
    /** 像素尺寸下限（:361-365 / :434-436）。 */
    public static final int MIN_PIXEL_SIZE = 24;
    /** load$default 默认圆角 dp（:118）。 */
    public static final int DEFAULT_CORNER_DP = 12;
    /** load$default 默认形状（:121）。 */
    public static final WidgetShape DEFAULT_SHAPE = WidgetShape.ROUNDED;
    /** solidShape$default 默认边长 dp（:271）。 */
    public static final int DEFAULT_SOLID_SIZE_DP = 48;
    /** 形状极坐标采样点数（:176-177 / :244）。 */
    public static final int SHAPE_SAMPLES = 120;
    /** 描边/内衬 inset 基准：density * 2（:157 f5 = density*2，随后再叠加一次）。 */
    public static final float INSET_STEP_DENSITY_FACTOR = 2.0f;

    /** load$default 的位掩码含义（:114-129）：bit8 高=宽、bit16 圆角=12、bit32 形状=ROUNDED、bit64/128 两个颜色=null。 */
    public static final int MASK_HEIGHT_EQUALS_WIDTH = 8;
    public static final int MASK_DEFAULT_CORNER = 16;
    public static final int MASK_DEFAULT_SHAPE = 32;
    public static final int MASK_NO_OUTLINE = 64;
    public static final int MASK_NO_INNER = 128;

    public final String url;
    public final int widthDp;
    public final int heightDp;
    public final int cornerDp;
    public final WidgetShape shape;
    /** 描边（外圈）颜色 ARGB；null 表示无（原 num / ringColor）。 */
    public final Integer outlineColor;
    /** 内衬（内圈）颜色 ARGB；null 表示无（原 num2 / ringBackground）。 */
    public final Integer innerColor;
    public final float density;

    public final int widthPx;
    public final int heightPx;
    public final float cornerPx;
    /** 逐字还原的缓存键（:367）：url|wPx|hPx|cornerPx|shape|outline|inner。 */
    public final String cacheKey;

    private WidgetImageSpec(String url, int widthDp, int heightDp, int cornerDp,
                            WidgetShape shape, Integer outlineColor, Integer innerColor,
                            float density) {
        this.url = url == null ? "" : url;
        this.widthDp = widthDp;
        this.heightDp = heightDp;
        this.cornerDp = cornerDp;
        this.shape = Objects.requireNonNull(shape, "shape");
        this.outlineColor = outlineColor;
        this.innerColor = innerColor;
        this.density = density;
        this.widthPx = Math.max((int) (widthDp * density), MIN_PIXEL_SIZE);
        this.heightPx = Math.max((int) (heightDp * density), MIN_PIXEL_SIZE);
        this.cornerPx = cornerDp * density;
        this.cacheKey = this.url + "|" + widthPx + "|" + heightPx + "|" + cornerPx
                + "|" + shape + "|" + outlineColor + "|" + innerColor;
    }

    /** 对应 load(context, url, wDp, hDp, cornerDp, shape, num, num2) 的完整规格。 */
    public static WidgetImageSpec of(String url, int widthDp, int heightDp, int cornerDp,
                                     WidgetShape shape, Integer outlineColor, Integer innerColor,
                                     float density) {
        return new WidgetImageSpec(url, widthDp, heightDp, cornerDp, shape,
                outlineColor, innerColor, density);
    }

    /**
     * 还原 load$default 的默认值代入（WidgetImages.java:113-129）。
     *
     * @param mask 原 i4 位掩码（NowPlayingWidget 调用均传 224 = 32|64|128）
     */
    public static WidgetImageSpec ofDefault(String url, int widthDp, int heightDp, int cornerDp,
                                            WidgetShape shape, Integer outlineColor,
                                            Integer innerColor, float density, int mask) {
        int h = (mask & MASK_HEIGHT_EQUALS_WIDTH) != 0 ? widthDp : heightDp;
        int c = (mask & MASK_DEFAULT_CORNER) != 0 ? DEFAULT_CORNER_DP : cornerDp;
        WidgetShape s = (mask & MASK_DEFAULT_SHAPE) != 0 ? DEFAULT_SHAPE : shape;
        Integer o = (mask & MASK_NO_OUTLINE) != 0 ? null : outlineColor;
        Integer in = (mask & MASK_NO_INNER) != 0 ? null : innerColor;
        return of(url, widthDp, h, c, s, o, in, density);
    }

    /** URL 空白时原 load 直接返回 null（:358 / :404），由 WidgetCover 显示占位图标。 */
    public boolean isBlankUrl() {
        return url.isEmpty() || url.isBlank();
    }

    /** Coil ImageRequest.size(max(wPx, hPx))（:372）。 */
    public int coilRequestSizePx() {
        return Math.max(widthPx, heightPx);
    }

    /** 原 render 的中心裁剪缩放系数 max(wPx/srcW, hPx/srcH)（:142）。 */
    public static float centerCropScale(int srcW, int srcH, int dstW, int dstH) {
        if (srcW <= 0 || srcH <= 0) {
            return 1.0f;
        }
        return Math.max((float) dstW / srcW, (float) dstH / srcH);
    }

    /** solidShape 的缓存键（:437）：solid|px|shape|color|cornerDp。 */
    public static String solidCacheKey(int sizePx, WidgetShape shape, int color, int cornerDp) {
        return "solid|" + sizePx + "|" + shape + "|" + color + "|" + cornerDp;
    }

    /** solidShape 的像素边长（:433-436）。 */
    public static int solidSizePx(int sizeDp, float density) {
        return Math.max((int) (sizeDp * density), MIN_PIXEL_SIZE);
    }

    // ---- 已知调用点规格（来自 6 个 Widget 的 provideGlance） ----

    /** NowPlayingWidget:284 宽版封面 224×126×16（原字面量 126 被 jadx 折叠为 WebSocketProtocol.PAYLOAD_SHORT）。 */
    public static WidgetImageSpec nowPlayingWide(String url, float density) {
        return ofDefault(url, 224, 126, 16, null, null, null, density,
                MASK_DEFAULT_SHAPE | MASK_NO_OUTLINE | MASK_NO_INNER);
    }

    /** NowPlayingWidget:198 / :320 紧凑版封面 90×90×16。 */
    public static WidgetImageSpec nowPlayingCompact(String url, float density) {
        return ofDefault(url, 90, 90, 16, null, null, null, density,
                MASK_DEFAULT_SHAPE | MASK_NO_OUTLINE | MASK_NO_INNER);
    }

    @Override
    public String toString() {
        return "WidgetImageSpec(" + cacheKey + ")";
    }
}
