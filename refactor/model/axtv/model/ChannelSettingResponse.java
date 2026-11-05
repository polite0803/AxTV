/*
 * 来源: /aweme/v1/web/home/channel/setting/ 的真实响应（2026-11-05 模拟器抓包）
 *       + src/app/p000/f50.java（频道的服务端列表与内置兜底表）
 * 原职责: 频道设置接口返回体
 * 重构说明: 早期版本按惯例推定成 data[{ch_id,ch_name,tag_id}]，真实响应是
 *           config_info[{key,label,is_active_tab}] + status_code。已按实测更正。
 */
package axtv.model;

import java.util.List;

/**
 * @param configInfo 分区列表（{@code config_info}）；<b>可空</b>
 * @param statusCode 状态码（{@code status_code}）
 */
public record ChannelSettingResponse(@JsonKey("config_info") List<ChannelConfigItem> configInfo,
                                     @JsonKey("status_code") int statusCode) {

    public ChannelSettingResponse {
        configInfo = configInfo == null ? null : List.copyOf(configInfo);
    }

    public ChannelSettingResponse() {
        this(null, 0);
    }
}
