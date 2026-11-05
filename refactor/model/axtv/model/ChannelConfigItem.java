/*
 * 来源: /aweme/v1/web/home/channel/setting/ 的真实响应（2026-11-05 模拟器抓包）
 * 原职责: 频道设置里的一个分区项
 * 重构说明: 原 app 的 ChannelSettingResponse$$serializer 未被 jadx 导出，
 *           早期重构按惯例把返回体当作 data[{ch_id,ch_name,tag_id}]，与真实响应不符；
 *           实际下发的是 config_info[{key,label,is_active_tab}]。
 */
package axtv.model;

/**
 * @param key      分区 id，即分区内容接口的 {@code tag_id}；{@code "0"} 表示「全部」
 * @param label    分区名（Tab 文本）
 * @param activeTab 是否为当前激活 Tab（{@code is_active_tab}）
 */
public record ChannelConfigItem(
        @JsonKey("key") String key,
        @JsonKey("label") String label,
        @JsonKey("is_active_tab") boolean activeTab) {

    public ChannelConfigItem {
        key = key == null ? "" : key;
        label = label == null ? "" : label;
    }

    public ChannelConfigItem() {
        this("", "", false);
    }
}
