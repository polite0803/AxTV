/*
 * 来源: src/app/com/github/mytv/dv/model/LiveDanmakuSettings.java
 *       + src/app/p000/co2.java / ip2.java / wm4.java 里的写入与 clamp 规则
 * 原职责: 直播弹幕偏好：开关 / 表情 / 样式 / 速度 / 字号 / 透明度 / 显示区域 / 行距列距 / 屏蔽词
 * 重构说明: 去掉 copy$default 与掩码构造器，改为 with* 方法；默认值与原 Kotlin 默认参数一致
 */
package axtv.live;

import java.util.List;
import java.util.Objects;

/** 直播弹幕设置（不可变）。 */
public final class LiveDanmakuSettings {

    /** 弹幕样式（原 {@code an2}）。 */
    public enum Style {
        SCROLL("scroll"),
        LIST("list");

        private final String key;

        Style(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Style fromKey(String key) {
            for (Style style : values()) {
                if (style.key.equals(key)) {
                    return style;
                }
            }
            return SCROLL;
        }
    }

    // ---- clamp 区间（co2 / ip2 原样） ----
    public static final float SPEED_MIN = 0.5f;
    public static final float SPEED_MAX = 2.0f;
    public static final float FONT_SIZE_MIN = 0.8f;
    public static final float FONT_SIZE_MAX = 1.5f;
    public static final float OPACITY_MIN = 0.01f;
    public static final float OPACITY_MAX = 1.0f;
    public static final float SPACING_MIN = 0.5f;
    public static final float SPACING_MAX = 2.0f;
    public static final int AREA_PERCENT_MIN = 1;
    public static final int AREA_PERCENT_MAX = 100;

    /** 原 {@code vo0}：慢 / 中 / 快。 */
    public static final float SPEED_PRESET_SLOW = 0.5f;
    public static final float SPEED_PRESET_NORMAL = 1.0f;
    public static final float SPEED_PRESET_FAST = 1.5f;

    /** 原 {@code so0}：超小 / 小 / 中 / 大 / 超大。 */
    public static final float FONT_PRESET_EXTRA_SMALL = 1.0f;
    public static final float FONT_PRESET_SMALL = 1.5f;
    public static final float FONT_PRESET_MEDIUM = 2.0f;
    public static final float FONT_PRESET_LARGE = 2.5f;
    public static final float FONT_PRESET_EXTRA_LARGE = 3.0f;

    private final boolean enabled;
    private final boolean emojiEnabled;
    private final Style style;
    private final float speed;
    private final float fontSize;
    private final float opacity;
    private final int displayAreaPercent;
    private final float verticalSpacingScale;
    private final float horizontalSpacingScale;
    private final boolean filterPackageComment;
    private final boolean blockEnabled;
    private final List<String> blockWords;

    public LiveDanmakuSettings(boolean enabled,
                               boolean emojiEnabled,
                               Style style,
                               float speed,
                               float fontSize,
                               float opacity,
                               int displayAreaPercent,
                               float verticalSpacingScale,
                               float horizontalSpacingScale,
                               boolean filterPackageComment,
                               boolean blockEnabled,
                               List<String> blockWords) {
        this.enabled = enabled;
        this.emojiEnabled = emojiEnabled;
        this.style = style == null ? Style.SCROLL : style;
        this.speed = speed;
        this.fontSize = fontSize;
        this.opacity = opacity;
        this.displayAreaPercent = displayAreaPercent;
        this.verticalSpacingScale = verticalSpacingScale;
        this.horizontalSpacingScale = horizontalSpacingScale;
        this.filterPackageComment = filterPackageComment;
        this.blockEnabled = blockEnabled;
        this.blockWords = blockWords == null ? List.of() : List.copyOf(blockWords);
    }

    /** 原 Kotlin 默认参数组合。 */
    public static LiveDanmakuSettings defaults() {
        return new LiveDanmakuSettings(true, true, Style.SCROLL,
                1.0f, 1.0f, 0.8f, 50, 1.0f, 1.0f, true, false, List.of());
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean emojiEnabled() {
        return emojiEnabled;
    }

    public Style style() {
        return style;
    }

    /** 速度倍率，clamp 到 [0.5, 2.0]。 */
    public float speed() {
        return speed;
    }

    /** 字号倍率。注意原实现有三处不同口径，见 NOTES「存疑」。 */
    public float fontSize() {
        return fontSize;
    }

    /** 不透明度 [0.01, 1.0]。 */
    public float opacity() {
        return opacity;
    }

    /** 弹幕显示区域百分比 [1, 100]，渲染时除以 100 得到 0 附近的占比。 */
    public int displayAreaPercent() {
        return displayAreaPercent;
    }

    public float verticalSpacingScale() {
        return verticalSpacingScale;
    }

    public float horizontalSpacingScale() {
        return horizontalSpacingScale;
    }

    /** 过滤 chatBy 为 9 / 10 的评论（原 {@code f3334p}）。 */
    public boolean filterPackageComment() {
        return filterPackageComment;
    }

    public boolean blockEnabled() {
        return blockEnabled;
    }

    public List<String> blockWords() {
        return blockWords;
    }

    public LiveDanmakuSettings withEnabled(boolean value) {
        return new LiveDanmakuSettings(value, emojiEnabled, style, speed, fontSize, opacity,
                displayAreaPercent, verticalSpacingScale, horizontalSpacingScale,
                filterPackageComment, blockEnabled, blockWords);
    }

    public LiveDanmakuSettings withEmojiEnabled(boolean value) {
        return new LiveDanmakuSettings(enabled, value, style, speed, fontSize, opacity,
                displayAreaPercent, verticalSpacingScale, horizontalSpacingScale,
                filterPackageComment, blockEnabled, blockWords);
    }

    public LiveDanmakuSettings withStyle(Style value) {
        return new LiveDanmakuSettings(enabled, emojiEnabled, value, speed, fontSize, opacity,
                displayAreaPercent, verticalSpacingScale, horizontalSpacingScale,
                filterPackageComment, blockEnabled, blockWords);
    }

    public LiveDanmakuSettings withSpeed(float value) {
        return new LiveDanmakuSettings(enabled, emojiEnabled, style, value, fontSize, opacity,
                displayAreaPercent, verticalSpacingScale, horizontalSpacingScale,
                filterPackageComment, blockEnabled, blockWords);
    }

    public LiveDanmakuSettings withFontSize(float value) {
        return new LiveDanmakuSettings(enabled, emojiEnabled, style, speed, value, opacity,
                displayAreaPercent, verticalSpacingScale, horizontalSpacingScale,
                filterPackageComment, blockEnabled, blockWords);
    }

    public LiveDanmakuSettings withOpacity(float value) {
        return new LiveDanmakuSettings(enabled, emojiEnabled, style, speed, fontSize, value,
                displayAreaPercent, verticalSpacingScale, horizontalSpacingScale,
                filterPackageComment, blockEnabled, blockWords);
    }

    public LiveDanmakuSettings withDisplayAreaPercent(int value) {
        return new LiveDanmakuSettings(enabled, emojiEnabled, style, speed, fontSize, opacity,
                value, verticalSpacingScale, horizontalSpacingScale,
                filterPackageComment, blockEnabled, blockWords);
    }

    public LiveDanmakuSettings withVerticalSpacingScale(float value) {
        return new LiveDanmakuSettings(enabled, emojiEnabled, style, speed, fontSize, opacity,
                displayAreaPercent, value, horizontalSpacingScale,
                filterPackageComment, blockEnabled, blockWords);
    }

    public LiveDanmakuSettings withHorizontalSpacingScale(float value) {
        return new LiveDanmakuSettings(enabled, emojiEnabled, style, speed, fontSize, opacity,
                displayAreaPercent, verticalSpacingScale, value,
                filterPackageComment, blockEnabled, blockWords);
    }

    public LiveDanmakuSettings withFilterPackageComment(boolean value) {
        return new LiveDanmakuSettings(enabled, emojiEnabled, style, speed, fontSize, opacity,
                displayAreaPercent, verticalSpacingScale, horizontalSpacingScale,
                value, blockEnabled, blockWords);
    }

    public LiveDanmakuSettings withBlockEnabled(boolean value) {
        return new LiveDanmakuSettings(enabled, emojiEnabled, style, speed, fontSize, opacity,
                displayAreaPercent, verticalSpacingScale, horizontalSpacingScale,
                filterPackageComment, value, blockWords);
    }

    public LiveDanmakuSettings withBlockWords(List<String> value) {
        return new LiveDanmakuSettings(enabled, emojiEnabled, style, speed, fontSize, opacity,
                displayAreaPercent, verticalSpacingScale, horizontalSpacingScale,
                filterPackageComment, blockEnabled, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LiveDanmakuSettings other)) {
            return false;
        }
        return enabled == other.enabled
                && emojiEnabled == other.emojiEnabled
                && Float.compare(speed, other.speed) == 0
                && Float.compare(fontSize, other.fontSize) == 0
                && Float.compare(opacity, other.opacity) == 0
                && displayAreaPercent == other.displayAreaPercent
                && Float.compare(verticalSpacingScale, other.verticalSpacingScale) == 0
                && Float.compare(horizontalSpacingScale, other.horizontalSpacingScale) == 0
                && filterPackageComment == other.filterPackageComment
                && blockEnabled == other.blockEnabled
                && style == other.style
                && blockWords.equals(other.blockWords);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, emojiEnabled, style, speed, fontSize, opacity, displayAreaPercent,
                verticalSpacingScale, horizontalSpacingScale, filterPackageComment, blockEnabled, blockWords);
    }

    @Override
    public String toString() {
        return "LiveDanmakuSettings(enabled=" + enabled + ", emojiEnabled=" + emojiEnabled
                + ", style=" + style + ", speed=" + speed + ", fontSize=" + fontSize
                + ", opacity=" + opacity + ", displayAreaPercent=" + displayAreaPercent
                + ", verticalSpacingScale=" + verticalSpacingScale
                + ", horizontalSpacingScale=" + horizontalSpacingScale
                + ", filterPackageComment=" + filterPackageComment
                + ", blockEnabled=" + blockEnabled + ", blockWords=" + blockWords.size() + ")";
    }
}
