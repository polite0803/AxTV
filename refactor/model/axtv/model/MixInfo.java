/*
 * 来源: src/app/com/github/mytv/p004dv/model/MixInfo.java + MixInfo$$serializer.java
 * 原职责: 合集（抖音叫「系列」）的元信息，挂在 aweme 上或作为合集接口的返回体
 * 重构说明: 数据类 → record；mix_id/mix_name 另有 series_id/series_name 备用 key
 */
package axtv.model;

/**
 * @param mixId   合集 id（{@code mix_id}，备用：{@code series_id}）
 * @param mixName 合集名（{@code mix_name}，备用：{@code series_name}）
 * @param desc    合集简介（{@code desc}）
 * @param coverUrl 合集封面（{@code cover_url}，注意是 Cover 对象而非字符串）
 * @param statis  合集统计（{@code statis}）
 */
public record MixInfo(
        @JsonKey(value = "mix_id", alternates = {"series_id"}) String mixId,
        @JsonKey(value = "mix_name", alternates = {"series_name"}) String mixName,
        @JsonKey("desc") String desc,
        @JsonKey("cover_url") Cover coverUrl,
        @JsonKey("statis") MixStatis statis) {

    public MixInfo {
        mixId = mixId == null ? "" : mixId;
        mixName = mixName == null ? "" : mixName;
        desc = desc == null ? "" : desc;
        coverUrl = coverUrl == null ? new Cover() : coverUrl;
        statis = statis == null ? new MixStatis() : statis;
    }

    public MixInfo() {
        this("", "", "", new Cover(), new MixStatis());
    }
}
