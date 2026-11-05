/*
 * 来源: src/app/com/github/mytv/p004dv/model/ChannelSettingResponse.java
 * 原职责: 频道列表接口返回体
 * 重构说明: 数据类 → record；$$serializer 未导出，key 按惯例推定
 */
package axtv.model;

import java.util.List;

/**
 * @param data       频道列表（推定 {@code data}）；<b>可空</b>
 * @param statusCode 状态码（推定 {@code status_code}）
 */
public record ChannelSettingResponse(@JsonKey("data") List<ChannelItem> data,
                                     @JsonKey("status_code") int statusCode) {

    public ChannelSettingResponse {
        data = data == null ? null : List.copyOf(data);
    }

    public ChannelSettingResponse() {
        this(null, 0);
    }
}
