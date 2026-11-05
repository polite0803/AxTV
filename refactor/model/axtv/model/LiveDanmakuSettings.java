/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveDanmakuSettings.java（内含 p000.an2 弹幕样式枚举）
 * 原职责: 直播弹幕的本地渲染设置（开关、样式、速度、字号、透明度、显示区域、屏蔽词）
 * 重构说明: 数据类 → record；an2 还原为 {@link DanmakuStyle}。
 *           displayAreaPercent 保留 1..100 的百分比存储，展示时换算成 0..1。
 */
package axtv.model;

import java.util.List;

/**
 * @param enabled               弹幕总开关，默认 true
 * @param emojiEnabled          是否显示表情，默认 true
 * @param style                 滚动 / 列表样式，默认 {@link DanmakuStyle#SCROLL}
 * @param speed                 滚动速度倍率，默认 1.0
 * @param fontSize              字号倍率，默认 1.0
 * @param opacity               不透明度 0..1，默认 0.8
 * @param displayAreaPercent    显示区域占屏高的百分比 1..100，默认 50
 * @param verticalSpacingScale  行间距倍率，默认 1.0
 * @param horizontalSpacingScale 字间距倍率，默认 1.0
 * @param filterPackageComment  是否过滤「xxx 送给主播 x 个礼物」这类系统消息，默认 true
 * @param blockEnabled          是否启用屏蔽词，默认 false
 * @param blockWords            屏蔽词表，默认空
 */
public record LiveDanmakuSettings(
        boolean enabled,
        boolean emojiEnabled,
        DanmakuStyle style,
        float speed,
        float fontSize,
        float opacity,
        int displayAreaPercent,
        float verticalSpacingScale,
        float horizontalSpacingScale,
        boolean filterPackageComment,
        boolean blockEnabled,
        List<String> blockWords) {

    /** 弹幕样式，对应反编译的 {@code p000.an2}：SCROLL（滚动）/ LIST（列表）。 */
    public enum DanmakuStyle {
        SCROLL("scroll"),
        LIST("list");

        private final String value;

        DanmakuStyle(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static DanmakuStyle fromValue(String value) {
            for (DanmakuStyle s : values()) {
                if (s.value.equalsIgnoreCase(value)) {
                    return s;
                }
            }
            return SCROLL;
        }
    }

    public LiveDanmakuSettings {
        style = style == null ? DanmakuStyle.SCROLL : style;
        blockWords = blockWords == null ? List.of() : List.copyOf(blockWords);
    }

    /** 反编译默认值（原无参构造器走 4095 全掩码，等价于下面这组值）。 */
    public LiveDanmakuSettings() {
        this(true, true, DanmakuStyle.SCROLL, 1.0f, 1.0f, 0.8f, 50, 1.0f, 1.0f, true, false, List.of());
    }

    /** 原 {@code getDisplayArea()}：百分比夹到 1..100 后换算成 0.01..1 的比率。 */
    public float displayArea() {
        int clamped = Math.min(Math.max(displayAreaPercent, 1), 100);
        return clamped / 100.0f;
    }
}
