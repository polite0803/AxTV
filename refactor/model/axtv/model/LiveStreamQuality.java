/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveStreamQuality.java + LiveStreamQuality$$serializer.java
 * 原职责: 直播 SDK（live_core_sdk_data）里的一档清晰度描述
 * 重构说明: 数据类 → record；level 走 FlexibleInt（接口可能返回字符串）
 */
package axtv.model;

/**
 * @param level      档位等级，越大越清晰（{@code level}，接口可能返回字符串）
 * @param sdkKey     在 stream_data.data 里的键名，用它去取该档位的地址（{@code sdk_key}）
 * @param vCodec     视频编码标记（{@code v_codec}）；原代码只做透传，不参与选档
 * @param name       档位显示名，如「蓝光」（{@code name}）；<b>可空</b>
 * @param resolution 分辨率字符串（{@code resolution}），如 1920x1080；原代码只透传
 */
public record LiveStreamQuality(
        @JsonKey("level") int level,
        @JsonKey("sdk_key") String sdkKey,
        @JsonKey("v_codec") String vCodec,
        @JsonKey("name") String name,
        @JsonKey("resolution") String resolution) {

    public LiveStreamQuality {
        sdkKey = sdkKey == null ? "" : sdkKey;
        vCodec = vCodec == null ? "" : vCodec;
        resolution = resolution == null ? "" : resolution;
    }

    /** 反编译默认值：level 0、字符串 ""、name 为 null。 */
    public LiveStreamQuality() {
        this(0, "", "", null, "");
    }
}
