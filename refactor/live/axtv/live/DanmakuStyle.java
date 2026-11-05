/*
 * 来源: src/app/p000/wo0.java（轨道分配）+ to0.java（布局参数）+ uo0.java（布局结果）
 *       + src/app/p000/i42.java 第 350-620 行（叠加层参数与绘制位移公式）
 * 原职责: 弹幕叠加层的几何计算：字号/行高/内边距/水平间距/时长/速度基准宽度、轨道占用表、
 *         每条弹幕的进入与退出时间、任意时刻的 x 坐标
 * 重构说明: 纯算法抽离，不依赖 Canvas / Compose；绘制像素测量通过 {@link TextMeasurer} 注入。
 *           所有常量、公式、取整方向与原实现逐项一致，见 NOTES 第 4 节。
 */
package axtv.live;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 弹幕布局与运动学。
 *
 * <h2>核心公式（原实现）</h2>
 * <pre>
 * fontSize      = sp(16) * fontSizeScale
 * lineHeight    = clamp(verticalSpacing, 0.5, 2.0) * dp(30) * fontSizeScale
 * padding       = dp(8)
 * horizontalGap = clamp(horizontalSpacing, 0.5, 2.0) * 4 * padding
 * durationMs    = 8000 / clamp(speed, 0.5, 2.0)        // 速度倍率越大，单条越短
 * pxPerMs       = (speedReferenceWidth &gt; 0 ? speedReferenceWidth : canvasWidth) / durationMs
 * 轨道数         = max(1, (int)((canvasHeight * displayArea) / lineHeight))
 * 轨道释放时间    = offsetTime + (2*textWidth + horizontalGap) / pxPerMs
 * 完全离场时间    = offsetTime + (2*textWidth + canvasWidth)   / pxPerMs
 * x(t)          = (textWidth + canvasWidth) - pxPerMs * max(0, t - offsetTime)
 * 基线 y         = track * lineHeight + padding + fontSize
 * </pre>
 *
 * <p>注意 {@code 2*textWidth} 的系数来自原实现（{@code 2.0f * xw2.f25049a}），
 * 语义上等价于「文字从右侧完全进场再离场」，未做修正，见 NOTES「存疑」。
 *
 * <p>{@code speedReferenceWidth} 即版本日志提到的「弹幕速度基准改为窗口宽度」：
 * 取屏幕宽度像素，保证不同分辨率下 8 秒横穿屏幕的观感一致；为 0 时回退到实际画布宽度。
 */
public final class DanmakuStyle {

    /** 原 {@code oh4.m8585j(16)}。 */
    public static final float BASE_TEXT_SIZE_SP = 16f;

    /** 原 {@code iu0.mo391P(30f)}。 */
    public static final float LINE_HEIGHT_DP = 30f;

    /** 原 {@code iu0.mo391P(8f)}。 */
    public static final float PADDING_DP = 8f;

    /** 原 {@code 4.0f * padding}。 */
    public static final float HORIZONTAL_GAP_FACTOR = 4f;

    /** 原 {@code 8000.0f / f4}。 */
    public static final float BASE_DURATION_MS = 8000f;

    /** 原 {@code paint.setStrokeWidth(0.12f * fontSize)}。 */
    public static final float STROKE_WIDTH_RATIO = 0.12f;

    /** 原 {@code ut0.m10995r((int)(255f * opacity), 0, 255)} 的上界。 */
    public static final int MAX_ALPHA = 255;

    /** 原 {@code wo0.m11617b} 里清理已离场弹幕的窗口：{@code now - 1000}。 */
    public static final long ACTIVE_RETENTION_MS = 1000L;

    /** 原 {@code (long) 2.0f * textWidth} 里的系数。 */
    public static final float TRAVEL_WIDTH_FACTOR = 2f;

    /** 原绘制前的裁剪余量：{@code -500 / +500}。 */
    public static final long CULL_MARGIN_MS = 500L;

    private DanmakuStyle() {
    }

    /** 像素密度换算（Android 侧用 {@code TypedValue.applyDimension} 或 Compose 的 {@code sp}/{@code dp}）。 */
    public interface Density {

        float sp(float sp);

        float dp(float dp);
    }

    /** 文本宽度测量（Android 侧用 {@code Paint.measureText}）。 */
    public interface TextMeasurer {

        /** 单行宽度，单位 px。 */
        float measure(String text);
    }

    /** 原 {@code to0}：布局参数。 */
    public record LayoutParams(float displayArea,
                               float lineHeight,
                               float padding,
                               float horizontalGap,
                               float durationMs,
                               float speedReferenceWidth) {

        /** 原 {@code fFloatValue = (speedReferenceWidth > 0 ? speedReferenceWidth : width) / durationMs}。 */
        public float pixelsPerMs(float canvasWidth) {
            float reference = speedReferenceWidth > 0f ? speedReferenceWidth : canvasWidth;
            return reference / durationMs;
        }

        /** 轨道数：{@code max(1, (int)((height * displayArea) / lineHeight))}。 */
        public int trackCount(float canvasHeight) {
            int count = (int) ((canvasHeight * displayArea) / lineHeight);
            return Math.max(1, count);
        }
    }

    /** 原 {@code uo0}：一条已排队的弹幕及其几何信息。 */
    public record Placed(LiveDanmakuMessage message,
                         float textWidth,
                         int track,
                         float baselineY,
                         float pixelsPerMs,
                         long exitTimeMs,
                         long releaseTimeMs) {

        /** 原 {@code j3 = now - offsetTime}。 */
        public long elapsedMs(long now) {
            return now - message.timestamp();
        }
    }

    /** 原 {@code i42} 里的布局参数组装。 */
    public static LayoutParams layoutParams(LiveDanmakuSettings settings, Density density, float screenWidthPx) {
        float fontScale = settings.fontSize();
        float lineHeight = clamp(settings.verticalSpacingScale(),
                LiveDanmakuSettings.SPACING_MIN, LiveDanmakuSettings.SPACING_MAX) * density.dp(LINE_HEIGHT_DP) * fontScale;
        float padding = density.dp(PADDING_DP);
        float horizontalGap = clamp(settings.horizontalSpacingScale(),
                LiveDanmakuSettings.SPACING_MIN, LiveDanmakuSettings.SPACING_MAX) * HORIZONTAL_GAP_FACTOR * padding;
        float durationMs = BASE_DURATION_MS / clamp(settings.speed(),
                LiveDanmakuSettings.SPEED_MIN, LiveDanmakuSettings.SPEED_MAX);
        float displayArea = clamp(settings.displayAreaPercent(), LiveDanmakuSettings.AREA_PERCENT_MIN,
                LiveDanmakuSettings.AREA_PERCENT_MAX) / 100f;
        return new LayoutParams(displayArea, lineHeight, padding, horizontalGap, durationMs, screenWidthPx);
    }

    /** 字号像素：{@code sp(16) * fontSizeScale}。 */
    public static float fontSizePx(LiveDanmakuSettings settings, Density density) {
        return density.sp(BASE_TEXT_SIZE_SP) * settings.fontSize();
    }

    /** 描边宽度：{@code 0.12 * fontSize}。 */
    public static float strokeWidthPx(float fontSizePx) {
        return STROKE_WIDTH_RATIO * fontSizePx;
    }

    /** 文字颜色 alpha：{@code clamp((int)(255 * opacity), 0, 255)}。 */
    public static int alpha(LiveDanmakuSettings settings) {
        return clamp((int) (MAX_ALPHA * settings.opacity()), 0, MAX_ALPHA);
    }

    /**
     * 单条弹幕在某一时刻的绘制 x（文字左边缘）。
     *
     * @return 已离场 / 尚未进场时返回 {@link Float#NaN}
     */
    public static float xAt(Placed placed, float canvasWidth, long now) {
        long elapsed = placed.elapsedMs(now);
        long lifetime = placed.exitTimeMs() - placed.message().timestamp();
        if (elapsed < -CULL_MARGIN_MS || elapsed > lifetime + CULL_MARGIN_MS) {
            return Float.NaN;
        }
        float x = (placed.textWidth() + canvasWidth) - placed.pixelsPerMs() * Math.max(0L, elapsed);
        // 原实现：文字右边缘仍在画布内才绘制
        if (placed.textWidth() + x <= 0f) {
            return Float.NaN;
        }
        return x;
    }

    /**
     * 轨道分配器（原 {@code wo0}）。
     *
     * <p>非线程安全，由渲染线程独占调用。
     */
    public static final class TrackLayout {

        private final TextMeasurer measurer;
        private final List<Placed> active = new ArrayList<>();
        private final Set<String> seen = new HashSet<>();
        private long[] trackFree = new long[0];
        private float lastWidth = -1f;
        private float lastHeight = -1f;
        private LayoutParams lastParams;
        private long lastFrameTime = -1L;
        private boolean rebuild = true;
        private boolean listChanged;

        public TrackLayout(TextMeasurer measurer) {
            this.measurer = measurer;
        }

        /** 原 {@code wo0.m11618c}：消息列表变化时调用；返回是否发生变化。 */
        public boolean updateItems(List<LiveDanmakuMessage> items) {
            listChanged = true;
            seen.clear();
            return true;
        }

        /**
         * 原 {@code wo0.m11617b}：为当前帧计算可见弹幕。
         *
         * @param items 待排队的弹幕（已按时间升序）
         * @param now   当前帧时间（毫秒，原实现用 {@code SystemClock.elapsedRealtime() - base}）
         */
        public List<Placed> layout(float width, float height, LayoutParams params,
                                   List<LiveDanmakuMessage> items, long now) {
            if (width <= 0f || height <= 0f) {
                return List.of();
            }
            if (Float.compare(lastWidth, width) != 0
                    || Float.compare(lastHeight, height) != 0
                    || !params.equals(lastParams)
                    || (lastFrameTime >= 0 && now < lastFrameTime)) {
                rebuild = true;
            }
            lastWidth = width;
            lastHeight = height;
            lastParams = params;
            lastFrameTime = now;

            long minTime = now - ACTIVE_RETENTION_MS;
            if (rebuild) {
                active.clear();
                seen.clear();
                trackFree = new long[params.trackCount(height)];
            } else {
                active.removeIf(placed -> placed.message().timestamp() < minTime);
                if (listChanged) {
                    Arrays.fill(trackFree, 0L);
                    for (Placed placed : active) {
                        trackFree[placed.track()] = Math.max(trackFree[placed.track()], placed.releaseTimeMs());
                    }
                }
            }

            float pixelsPerMs = params.pixelsPerMs(width);
            List<Placed> frame = rebuild ? new ArrayList<>() : new ArrayList<>(active);
            if (rebuild) {
                frame.clear();
            }

            int trackCount = trackFree.length;
            for (LiveDanmakuMessage item : items) {
                if (item.isOnline() || LiveLogFormat.isBlank(item.content())) {
                    continue;
                }
                if (!seen.add(Long.toString(item.messageId()))) {
                    continue;
                }
                float textWidth = measurer.measure(item.content());
                int bestTrack = -1;
                long bestGap = Long.MAX_VALUE;
                for (int track = 0; track < trackCount; track++) {
                    long gap = item.timestamp() - trackFree[track];
                    if (gap >= 0 && gap < bestGap) {
                        bestTrack = track;
                        bestGap = gap;
                    }
                }
                if (bestTrack < 0) {
                    continue;
                }
                long release = item.timestamp()
                        + (long) (((TRAVEL_WIDTH_FACTOR * textWidth) + params.horizontalGap()) / pixelsPerMs);
                trackFree[bestTrack] = release;
                long exit = item.timestamp()
                        + (long) (((TRAVEL_WIDTH_FACTOR * textWidth) + width) / pixelsPerMs);
                if (exit >= minTime) {
                    frame.add(new Placed(item, textWidth, bestTrack,
                            (bestTrack * params.lineHeight()) + params.padding(),
                            pixelsPerMs, exit, release));
                }
            }

            active.clear();
            active.addAll(frame);
            rebuild = false;
            listChanged = false;
            return List.copyOf(frame);
        }
    }

    private static float clamp(float value, float min, float max) {
        if (min > max) {
            throw new IllegalArgumentException("Cannot coerce value to an empty range: maximum " + max
                    + " is less than minimum " + min + '.');
        }
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private static int clamp(int value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("Cannot coerce value to an empty range: maximum " + max
                    + " is less than minimum " + min + '.');
        }
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
