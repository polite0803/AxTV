/*
 * 来源: src/app/com/github/mytv/p004dv/model/FeedDataItem.java + FeedDataItem$$serializer.java
 * 原职责: 推荐流 data 数组的元素包装（一层 aweme 壳，部分接口多了这一层）
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param aweme 作品（{@code aweme}）；<b>可空</b>，空元素在流里用作占位
 */
public record FeedDataItem(@JsonKey("aweme") Aweme aweme) {

    public FeedDataItem() {
        this(null);
    }
}
