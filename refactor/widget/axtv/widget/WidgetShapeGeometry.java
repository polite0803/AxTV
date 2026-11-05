/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetImages.java:168-267 (shapePath)
 *       + :102-111 (insetPath) + :133-166 (render 的中心裁剪)
 *       + WidgetShape.java:8-18 (形状枚举)
 * 原职责: 把 WidgetShape 变成 Canvas 裁剪路径（120 点极坐标 + 7 点两次平滑 + 归一化）
 * 重构说明: 路径绘制依赖 Android Canvas/Path；核心层把「形状长什么样」算成可执行的采样点数组，
 *           交给 WidgetEnvironment.ImageLoader 沿点连线裁剪。半径公式、平滑次数、归一化逐字还原。
 */
package axtv.widget;

/** 形状几何：极坐标半径 → 平滑 → 归一化的路径采样点（x,y 交替存放）。 */
public final class WidgetShapeGeometry {

    private WidgetShapeGeometry() {
    }

    /** 原 shapePath 中每种形状的极坐标半径公式（WidgetImages.java:244-265）。 */
    public static float radius(WidgetShape shape, float theta) {
        switch (shape) {
            case COOKIE4:
                // 0.9 + 0.1 * cos(4θ)
                return 0.9f + (float) (Math.cos(theta * 4.0f) * 0.1f);
            case COOKIE9:
                // 0.94 + 0.06 * cos(9θ)
                return 0.94f + (float) (Math.cos(theta * 9.0f) * 0.06f);
            case CLOVER4:
                // 0.62 + 0.38 * |cos(2θ)|^0.55
                return 0.62f + (float) (Math.pow(Math.abs(Math.cos(theta * 2.0f)), 0.55d) * 0.38f);
            case ROUNDED:
            default:
                // 圆角矩形分支在 shapePath 入口直接返回；此处 1.0 对应原 fFloatValue 初值。
                return 1.0f;
        }
    }

    /** 7 点滑动平均，做 2 次（:188-203）；数组长度恒为 SHAPE_SAMPLES。 */
    private static float[] smoothTwice(float[] src) {
        float[] cur = src;
        for (int pass = 0; pass < 2; pass++) {
            float[] next = new float[cur.length];
            for (int i = 0; i < cur.length; i++) {
                float sum = 0.0f;
                for (int j = -3; j <= 3; j++) {
                    sum += cur[((i + j) + cur.length) % cur.length];
                }
                next[i] = sum / 7.0f;
            }
            cur = next;
        }
        return cur;
    }

    /**
     * 生成形状裁剪路径采样点（x,y 交替，长度 = 2 * SHAPE_SAMPLES）。
     * ROUNDED 返回以 cornerRadiusPx 为圆角的矩形 4 角近似点（Android 侧仍应调用 addRoundRect）。
     *
     * @param wPx            目标宽（像素）
     * @param hPx            目标高（像素）
     * @param cornerRadiusPx 圆角半径（像素），仅 ROUNDED 使用
     */
    public static float[] shapePoints(WidgetShape shape, float wPx, float hPx, float cornerRadiusPx) {
        if (shape == WidgetShape.ROUNDED) {
            // addRoundRect(RectF(0,0,w,h), corner, corner, CW) 的等价折线（Android 侧优先用原生 RoundRect）。
            float r = Math.min(Math.min(cornerRadiusPx, wPx / 2.0f), hPx / 2.0f);
            return new float[]{
                    r, 0, wPx - r, 0, wPx, r, wPx, hPx - r,
                    wPx - r, hPx, r, hPx, 0, hPx - r, 0, r
            };
        }

        int n = WidgetImageSpec.SHAPE_SAMPLES;
        float[] radii = new float[n];
        for (int i = 0; i < n; i++) {
            float theta = (i / (float) n) * 6.2831855f;
            radii[i] = radius(shape, theta);
        }
        radii = smoothTwice(radii);

        float max = 0.0f;
        for (float v : radii) {
            max = Math.max(max, v);
        }
        if (max == 0.0f) {
            max = 1.0f;
        }

        float[] pts = new float[n * 2];
        for (int i = 0; i < n; i++) {
            double angle = ((double) (i / 120.0f) * 6.283185307179586d) - 1.5707963267948966d;
            float r = radii[i] / max;
            pts[2 * i] = (wPx / 2.0f) * r * (float) Math.cos(angle) + wPx / 2.0f;
            pts[2 * i + 1] = (hPx / 2.0f) * r * (float) Math.sin(angle) + hPx / 2.0f;
        }
        return pts;
    }

    /** 原 insetPath（:102-111）：以中心为锚把路径按 (w-2*inset)/w、(h-2*inset)/h 缩放。 */
    public static float[] insetPoints(float[] points, float wPx, float hPx, float inset) {
        float sx = (wPx - 2.0f * inset) / wPx;
        float sy = (hPx - 2.0f * inset) / hPx;
        float cx = wPx / 2.0f;
        float cy = hPx / 2.0f;
        float[] out = new float[points.length];
        for (int i = 0; i < points.length; i += 2) {
            out[i] = (points[i] - cx) * sx + cx;
            out[i + 1] = (points[i + 1] - cy) * sy + cy;
        }
        return out;
    }

    /** 描边/内衬两次 inset 的距离（:157-164）：density*2，再叠加一次得到 density*4。 */
    public static float firstInset(float density) {
        return density * WidgetImageSpec.INSET_STEP_DENSITY_FACTOR;
    }

    public static float secondInset(float density) {
        return firstInset(density) + firstInset(density);
    }

    /** 原 render 的 BitmapShader 平移量（:142-146）：居中裁剪。 */
    public static float[] centerCropTranslate(int srcW, int srcH, int dstW, int dstH) {
        float scale = WidgetImageSpec.centerCropScale(srcW, srcH, dstW, dstH);
        return new float[]{
                scale,
                (dstW - srcW * scale) / 2.0f,
                (dstH - srcH * scale) / 2.0f
        };
    }
}
