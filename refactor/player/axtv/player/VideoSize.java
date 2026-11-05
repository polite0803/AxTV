/*
 * 来源: androidx.media3.common.VideoSize 的消费点 ro5.java:1388-1395、ko5.java:218-251
 * 原职责: 视频宽高 + 像素宽高比
 * 重构说明: 脱离 media3 的小值对象，附带宽高比计算
 */
package axtv.player;

/**
 * 视频尺寸。
 *
 * @param width                显示宽（像素）
 * @param height               显示高（像素）
 * @param pixelWidthHeightRatio 像素宽高比，非方形像素时 &ne; 1（如 1440x1080 的 4:3 素材给 1.333）
 */
public record VideoSize(int width, int height, float pixelWidthHeightRatio) {

    /** 无效尺寸（{@code ro5.java:35850}/{@code ko5.java:222} 都是 w&lt;=0 || h&lt;=0 直接 return）。 */
    public static final VideoSize UNKNOWN = new VideoSize(0, 0, 1f);

    public VideoSize {
        if (pixelWidthHeightRatio <= 0f) {
            pixelWidthHeightRatio = 1f;
        }
    }

    public static VideoSize of(int width, int height) {
        return new VideoSize(width, height, 1f);
    }

    /** w&gt;0 且 h&gt;0。所有缩放/比例计算的前置条件。 */
    public boolean isValid() {
        return width > 0 && height > 0;
    }

    /**
     * 送进 {@code AspectRatioFrameLayout} 的宽高比，见 {@code ro5.java:35853}：
     * {@code (i * f) / i2}，即 {@code width * pixelRatio / height}。
     */
    public float aspectRatio() {
        if (!isValid()) {
            return 0f;
        }
        return (width * pixelWidthHeightRatio) / height;
    }

    /** 是否竖屏素材（高 &gt; 宽 × 像素比）。 */
    public boolean isPortrait() {
        return isValid() && height > width * pixelWidthHeightRatio;
    }
}
