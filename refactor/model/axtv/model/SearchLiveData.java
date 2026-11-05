/*
 * 来源: src/app/com/github/mytv/p004dv/model/SearchLiveData.java + SearchLiveData$$serializer.java
 * 原职责: 直播搜索条目包装
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param lives 直播对象（{@code lives}）；<b>可空</b>
 */
public record SearchLiveData(@JsonKey("lives") SearchLiveLives lives) {

    public SearchLiveData() {
        this(null);
    }
}
