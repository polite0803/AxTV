/*
 * 来源: src/app/com/github/mytv/dv/model/LiveStreamQuality.java
 * 原职责: 清晰度条目：level / sdkKey / vCodec / name / resolution
 * 重构说明: 去掉 kotlinx.serialization 的 $stable / serializer / write$Self；
 *           默认值（level=0, sdkKey="", vCodec=null, name="", resolution=""）保留
 */
package axtv.live;

import java.util.Objects;

/**
 * 一路清晰度。{@code level} 越大越清晰，{@code sdkKey} 是 {@code stream_data.data} 里的键
 * （如 {@code FULL_HD1} / {@code HD1} / {@code SD2} / {@code SD1}）。
 */
public final class LiveStreamQuality {

    private final int level;
    private final String sdkKey;
    private final String vCodec;
    private final String name;
    private final String resolution;

    public LiveStreamQuality(int level, String sdkKey, String vCodec, String name, String resolution) {
        this.level = level;
        this.sdkKey = nullToEmpty(sdkKey);
        this.vCodec = vCodec;
        this.name = nullToEmpty(name);
        this.resolution = nullToEmpty(resolution);
    }

    public int level() {
        return level;
    }

    public String sdkKey() {
        return sdkKey;
    }

    /** 可能为 null（原字段是 {@code String?}）。 */
    public String vCodec() {
        return vCodec;
    }

    public String name() {
        return name;
    }

    public String resolution() {
        return resolution;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LiveStreamQuality other)) {
            return false;
        }
        return level == other.level
                && sdkKey.equals(other.sdkKey)
                && Objects.equals(vCodec, other.vCodec)
                && name.equals(other.name)
                && resolution.equals(other.resolution);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, sdkKey, vCodec, name, resolution);
    }

    @Override
    public String toString() {
        return "LiveStreamQuality(level=" + level + ", sdkKey=" + sdkKey + ", vCodec=" + vCodec
                + ", name=" + name + ", resolution=" + resolution + ")";
    }
}
