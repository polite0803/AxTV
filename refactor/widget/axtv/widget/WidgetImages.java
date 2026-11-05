/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetImages.java (454 行) + WidgetImages$load$bitmap$1.java (60 行)
 * 原职责: 用 Coil 拉图 → 中心裁剪 → 按 WidgetShape 绘制成 Bitmap → LruCache(48) 缓存；
 *         另提供纯色形状 solidShape；空 URL / 加载失败返回 null（由 WidgetCover 显示占位）
 * 重构说明: 核心层不解码 Bitmap、不发网络请求，用 WidgetEnvironment.ImageLoader 接口隔离；
 *           缓存键、像素尺寸钳制、LruCache 容量、空值/失败分支、渲染图层顺序全部还原为可执行逻辑。
 */
package axtv.widget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 小组件图片加载：规格计算 + 缓存 + 委托 Android 侧解码/绘制。 */
public final class WidgetImages {

    private final WidgetEnvironment env;
    /** 访问顺序的 LRU（原 android.util.LruCache，容量 48 条）。 */
    private final LinkedHashMap<String, Object> cache =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
                    return size() > WidgetImageSpec.CACHE_MAX_ENTRIES;
                }
            };

    public WidgetImages(WidgetEnvironment env) {
        this.env = env;
    }

    /**
     * 对应 load(context, url, wDp, hDp, cornerDp, shape, outline, inner)（:288-428）。
     * URL 空白、或 Android 侧 ImageLoader 返回 null（Coil 非 SuccessResult / toBitmap 为 null）→ 返回 null。
     */
    public synchronized Object load(String url, int widthDp, int heightDp, int cornerDp,
                                    WidgetShape shape, Integer outlineColor, Integer innerColor) {
        if (url == null || url.isBlank()) {
            return null;                                     // :358 / :404
        }
        WidgetImageSpec spec = WidgetImageSpec.of(url, widthDp, heightDp, cornerDp,
                shape, outlineColor, innerColor, env.settings.density());
        Object cached = cache.get(spec.cacheKey);            // :368-370
        if (cached != null) {
            return cached;
        }
        float[] points = WidgetShapeGeometry.shapePoints(shape, spec.widthPx, spec.heightPx,
                spec.cornerPx);
        Object bitmap = env.imageLoader.load(spec, points);
        if (bitmap == null) {
            return null;                                      // :405-427 失败不落缓存
        }
        cache.put(spec.cacheKey, bitmap);                     // :354
        return bitmap;
    }

    /** load$default 语义（:113-130），见 WidgetImageSpec.ofDefault。 */
    public Object loadDefault(String url, int widthDp, int heightDp, int cornerDp,
                              WidgetShape shape, Integer outlineColor, Integer innerColor, int mask) {
        if (url == null || url.isBlank()) {
            return null;
        }
        WidgetImageSpec spec = WidgetImageSpec.ofDefault(url, widthDp, heightDp, cornerDp,
                shape, outlineColor, innerColor, env.settings.density(), mask);
        Object cached = cache.get(spec.cacheKey);
        if (cached != null) {
            return cached;
        }
        float[] points = WidgetShapeGeometry.shapePoints(spec.shape, spec.widthPx, spec.heightPx,
                spec.cornerPx);
        Object bitmap = env.imageLoader.load(spec, points);
        if (bitmap == null) {
            return null;
        }
        cache.put(spec.cacheKey, bitmap);
        return bitmap;
    }

    /** 对应 solidShape(context, shape, color, sizeDp, cornerDp)（:430-453）。 */
    public synchronized Object solidShape(WidgetShape shape, int color, int sizeDp, int cornerDp) {
        int sizePx = WidgetImageSpec.solidSizePx(sizeDp, env.settings.density());
        String key = WidgetImageSpec.solidCacheKey(sizePx, shape, color, cornerDp);
        Object cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        float[] points = WidgetShapeGeometry.shapePoints(shape, sizePx, sizePx,
                cornerDp * env.settings.density());
        Object bitmap = env.imageLoader.solidShape(shape, color, sizePx, points);
        if (bitmap != null) {
            cache.put(key, bitmap);
        }
        return bitmap;
    }

    /** solidShape$default：mask bit8 → sizeDp=48，bit16 → cornerDp=12（:269-278）。 */
    public Object solidShapeDefault(WidgetShape shape, int color, int sizeDp, int cornerDp, int mask) {
        int s = (mask & 8) != 0 ? WidgetImageSpec.DEFAULT_SOLID_SIZE_DP : sizeDp;
        int c = (mask & 16) != 0 ? WidgetImageSpec.DEFAULT_CORNER_DP : cornerDp;
        return solidShape(shape, color, s, c);
    }

    /** 清空缓存（供预览/测试；原 LruCache.evictAll 等价）。 */
    public synchronized void evictAll() {
        cache.clear();
    }

    public synchronized int cacheSize() {
        return cache.size();
    }

    /** 原 render 的分层绘制规格（:148-165），Android 侧按此顺序画 Canvas。 */
    public record RenderLayer(String kind, float insetPx, int color, float[] points) {
    }

    /** 计算 render 的图层序列；无描边时只有 1 层图像。 */
    public List<RenderLayer> renderPlan(WidgetImageSpec spec) {
        float w = spec.widthPx;
        float h = spec.heightPx;
        float[] base = WidgetShapeGeometry.shapePoints(spec.shape, w, h, spec.cornerPx);
        if (spec.outlineColor == null) {
            return List.of(new RenderLayer("image", 0.0f, 0, base));
        }
        float first = WidgetShapeGeometry.firstInset(spec.density);
        float second = WidgetShapeGeometry.secondInset(spec.density);
        java.util.ArrayList<RenderLayer> layers = new java.util.ArrayList<>();
        layers.add(new RenderLayer("outline-fill", 0.0f, spec.outlineColor, base));
        if (spec.innerColor != null) {
            layers.add(new RenderLayer("inner-fill", first, spec.innerColor,
                    WidgetShapeGeometry.insetPoints(base, w, h, first)));
        }
        layers.add(new RenderLayer("image", second, 0,
                WidgetShapeGeometry.insetPoints(base, w, h, second)));
        return List.copyOf(layers);
    }
}
