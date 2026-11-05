/*
 * 来源: src/app/com/github/mytv/p004dv/model/SearchUserItem.java + SearchUserItem$$serializer.java
 * 原职责: 用户搜索结果条目
 * 重构说明: 数据类 → record；userInfo 默认是空 Author（非 null），rawAdCommon 可空
 */
package axtv.model;

/**
 * @param userInfo    用户资料（{@code user_info}）
 * @param rawAdCommon 广告原始数据（{@code raw_ad_common}）；<b>可空</b>，仅广告条目有
 */
public record SearchUserItem(
        @JsonKey("user_info") Author userInfo,
        @JsonKey("raw_ad_common") String rawAdCommon) {

    public SearchUserItem {
        userInfo = userInfo == null ? new Author() : userInfo;
    }

    public SearchUserItem() {
        this(new Author(), null);
    }
}
