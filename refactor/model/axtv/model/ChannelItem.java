/*
 * 来源: src/app/com/github/mytv/p004dv/model/ChannelItem.java + ChannelItem$$serializer.java
 * 原职责: 电视剧/综艺的频道条目
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param chName 频道名（{@code ch_name}）
 * @param tagId  标签 id（{@code tag_id}）
 * @param chId   频道 id（{@code ch_id}）
 */
public record ChannelItem(
        @JsonKey("ch_name") String chName,
        @JsonKey("tag_id") String tagId,
        @JsonKey("ch_id") String chId) {

    public ChannelItem {
        chName = chName == null ? "" : chName;
        tagId = tagId == null ? "" : tagId;
        chId = chId == null ? "" : chId;
    }

    public ChannelItem() {
        this("", "", "");
    }
}
