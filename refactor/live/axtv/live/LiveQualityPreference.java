/*
 * 来源: src/app/p000/lq5.java（清晰度偏好枚举，中文标签见常量）
 * 原职责: 自动 / 最高画质 / 1080p / 720p / 540p 五档，参与 selectQualityIndex 计算
 * 重构说明: 枚举顺序必须保持（DataModelKt.WhenMappings 用 ordinal 映射到 1..5）；
 *           label/key/shortLabel 三个字符串原样保留，其中 key 会写进 SharedPreferences
 */
package axtv.live;

/** 清晰度偏好。 */
public enum LiveQualityPreference {

    /** 自动：取最高。 */
    AUTO("自动", "auto", "自动"),
    /** 最高画质：取最高（与 AUTO 行为一致）。 */
    HIGHEST("最高画质", "highest", "最高"),
    /** 1080p：取倒数三分之一位置。 */
    P1080("高清 1080p", "1080p", "1080P"),
    /** 720p：取中间位置。 */
    P720("标清 720p", "720p", "720P"),
    /** 540p：取最低。 */
    P540("流畅 540p", "480p", "540P");

    private final String label;
    private final String key;
    private final String shortLabel;

    LiveQualityPreference(String label, String key, String shortLabel) {
        this.label = label;
        this.key = key;
        this.shortLabel = shortLabel;
    }

    public String label() {
        return label;
    }

    /** 持久化用的键（原 {@code f13467J}）。 */
    public String key() {
        return key;
    }

    public String shortLabel() {
        return shortLabel;
    }

    /** 按持久化的 key 反查，未知返回 {@link #AUTO}。 */
    public static LiveQualityPreference fromKey(String key) {
        for (LiveQualityPreference preference : values()) {
            if (preference.key.equals(key)) {
                return preference;
            }
        }
        return AUTO;
    }
}
