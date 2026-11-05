/*
 * 来源: src/app/p000/ro5.java:35848-35887（宽高比 / 固定尺寸）、
 *       ro5.java:1398-1426（TextureView/SurfaceView 缩放矩阵）、
 *       pq5.java:22-95（pinch 缩放与偏移钳制）、j16.java:677-690（点击归位）
 * 原职责: 把"视频尺寸 + 用户缩放/平移"算成 Android Matrix 或 View 变换参数
 * 重构说明: 全部抽成纯函数，不依赖 Android，可直接单测
 */
package axtv.player;

/**
 * 画面缩放矩阵。
 *
 * <h2>1. 适配比例（AspectRatioFrameLayout）</h2>
 * <pre>
 * aspect = videoWidth * pixelRatio / videoHeight          // ro5.java:35853
 * </pre>
 * 只在 {@code w>0 && h>0} 时下发；与上次值相同则跳过（{@code ro5.java:35859}）。
 * {@code PlayerView} 的 resizeMode 固定 0（RESIZE_MODE_FIT），因此竖屏视频会在
 * 横屏容器里按 aspect 居中留黑边——这就是"横竖屏处理"的全部：不解锁旋转，
 * 只按素材比例适配。
 *
 * <h2>2. SurfaceView 固定缓冲区</h2>
 * <pre>
 * fixedSize ? holder.setFixedSize((w + 63) &amp; ~63, h) : holder.setSizeFromLayout()
 * </pre>
 * 来源 {@code ro5.java:35881-35885}。去重键为三元组 {@code (w, h, fixedSize)}。
 *
 * <h2>3. 手势缩放</h2>
 * <pre>
 * s' = clamp(s * factor, 1.0, 3.0)                         // pq5.java:54
 * k  = s' / s
 * O' = O * k + (C - viewportCenter) * (1 - k) + pan         // pq5.java:57
 * O' = clampOffset(O', content, viewport, s')               // pq5.java:59
 * </pre>
 * {@code C} 是手势中心（双指中点 / 双击点），{@code pan} 是惯性拖动增量。
 *
 * <h2>4. 偏移钳制</h2>
 * <pre>
 * maxX = max(0, (contentW * s - viewportW) / 2)             // pq5.java:26-31
 * maxY = max(0, (contentH * s - viewportH) / 2)
 * O    = (clamp(O.x, -maxX, maxX), clamp(O.y, -maxY, maxY))
 * </pre>
 * 缩放回到 1.0 时偏移必然是 0，画面不会跑偏。
 *
 * <h2>5. 落地</h2>
 * <pre>
 * TextureView: matrix.setScale(s, s, w/2, h/2); matrix.postTranslate(tx, ty)
 * SurfaceView: setScaleX/Y(s); setTranslationX/Y(tx, ty)     // API >= 24
 * </pre>
 * 来源 {@code ro5.java:1412-1422}。
 */
public final class VideoScaleMatrix {

    private VideoScaleMatrix() {
    }

    /** 二维尺寸（像素）。 */
    public record Size(float width, float height) {

        public boolean isValid() {
            return width > 0f && height > 0f;
        }
    }

    /** 平移偏移（像素）。 */
    public record Offset(float x, float y) {

        public static final Offset ZERO = new Offset(0f, 0f);

        public boolean isZero() {
            return x == 0f && y == 0f;
        }

        public Offset plus(Offset other) {
            return new Offset(x + other.x, y + other.y);
        }

        public Offset times(float k) {
            return new Offset(x * k, y * k);
        }
    }

    /**
     * 最终下发给视图的变换。
     *
     * @param scale        缩放倍数
     * @param translationX 水平平移
     * @param translationY 垂直平移
     */
    public record Transform(float scale, float translationX, float translationY) {

        public static final Transform IDENTITY = new Transform(1f, 0f, 0f);

        /** 是否需要做变换（1.0 + 零偏移时可以不碰视图，省一次重绘）。 */
        public boolean isIdentity() {
            return scale == 1f && translationX == 0f && translationY == 0f;
        }
    }

    /** 缩放结果：新的倍率与钳制后的偏移。 */
    public record Zoom(float scale, Offset offset) {
    }

    // ------------------------------------------------------------------ 比例

    /**
     * 视频宽高比的百分比字符串，仅用于日志（{@code ko5.java:231} 打印 "Video size changed"）。
     */
    public static String describe(VideoSize size) {
        return size.width() + "x" + size.height() + "@" + size.pixelWidthHeightRatio();
    }

    /** 固定缓冲区宽度 64 对齐：{@code (w + 63) & ~63}（{@code ro5.java:35882}）。 */
    public static int alignedSurfaceWidth(int width) {
        int align = PlayerConfig.SURFACE_WIDTH_ALIGN;
        return (width + align - 1) & ~(align - 1);
    }

    // ------------------------------------------------------------------ 偏移

    /**
     * 把偏移钳制到"画面不露出黑边"的范围（{@code pq5.m9170b}）。
     *
     * @param offset    当前偏移（相对视图中心）
     * @param content   内容（视频）尺寸
     * @param viewport  视口尺寸
     * @param scale     当前缩放
     */
    public static Offset clampOffset(Offset offset, Size content, Size viewport, float scale) {
        if (scale <= PlayerConfig.ZOOM_MIN || !content.isValid() || !viewport.isValid()) {
            return Offset.ZERO;
        }
        float maxX = Math.max(0f, (content.width() * scale - viewport.width()) / 2f);
        float maxY = Math.max(0f, (content.height() * scale - viewport.height()) / 2f);
        return new Offset(clamp(offset.x(), -maxX, maxX), clamp(offset.y(), -maxY, maxY));
    }

    /**
     * 双指缩放 / 双击缩放一步（{@code pq5.m9172a}）。
     *
     * @param offset   当前偏移
     * @param scale    当前缩放
     * @param factor   手势缩放因子（{@code >0}；等于 1 表示无缩放）
     * @param centroid 手势中心（相对视图左上角）
     * @param viewport 视口尺寸
     * @param content  内容尺寸
     * @param pan      本次拖拽增量
     * @return 新倍率 + 钳制后的偏移；入参非法时原样返回
     */
    public static Zoom zoom(Offset offset, float scale, float factor, Offset centroid,
                            Size viewport, Size content, Offset pan) {
        if (!(factor > 0f) || Float.isInfinite(factor) || Float.isNaN(factor)) {
            return new Zoom(scale, offset);
        }
        if (!viewport.isValid() || !content.isValid()) {
            return new Zoom(scale, offset);
        }
        float newScale = clamp(scale * factor, PlayerConfig.ZOOM_MIN, PlayerConfig.ZOOM_MAX);
        float k = newScale / scale;
        Offset center = new Offset(viewport.width() / 2f, viewport.height() / 2f);
        Offset moved = offset.times(k)
                .plus(centroid.plus(new Offset(-center.x(), -center.y())).times(1f - k))
                .plus(pan);
        return new Zoom(newScale, clampOffset(moved, content, viewport, newScale));
    }

    /**
     * 单击归位（{@code j16.m6389V}）：缩放不足 {@link PlayerConfig#ZOOM_SNAP_THRESHOLD}
     * 时回到 1.0，否则保持不变，两种情况都重新求一次偏移。
     */
    public static Zoom realignAfterTap(Offset offset, float scale, Size viewport, Size content) {
        float target = scale < PlayerConfig.ZOOM_SNAP_THRESHOLD ? PlayerConfig.ZOOM_MIN : scale;
        return new Zoom(target, clampOffset(offset, content, viewport, target));
    }

    /** 复位：倍率 1.0、偏移 0（{@code pq5.m9176f}）。 */
    public static Zoom reset() {
        return new Zoom(PlayerConfig.ZOOM_MIN, Offset.ZERO);
    }

    /** 是否处于放大状态（{@code pq5.m9174d}：{@code scale > 1.001f}）。 */
    public static boolean isZoomed(float scale) {
        return scale > PlayerConfig.ZOOM_ACTIVE_EPSILON;
    }

    /** 打包成下发视图的变换。 */
    public static Transform toTransform(Zoom zoom) {
        return new Transform(zoom.scale(), zoom.offset().x(), zoom.offset().y());
    }

    /**
     * TextureView 的 3×3 变换矩阵（行主序），等价于
     * {@code m.setScale(s, s, w/2, h/2); m.postTranslate(tx, ty);}
     *
     * <p>Android 侧直接把它填进 {@code android.graphics.Matrix#setValues}。
     */
    public static float[] toMatrix(Transform transform, float pivotX, float pivotY) {
        float s = transform.scale();
        // setScale(s, s, px, py) ⇒ 平移项为 (1-s)*pivot
        float tx = (1f - s) * pivotX + transform.translationX();
        float ty = (1f - s) * pivotY + transform.translationY();
        return new float[]{
                s, 0f, tx,
                0f, s, ty,
                0f, 0f, 1f
        };
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
