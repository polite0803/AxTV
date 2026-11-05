/*
 * 来源: src/app/com/github/mytv/p004dv/model/SuggestWordGroup.java + SuggestWordGroup$$serializer.java
 * 原职责: 一组联想词（一个话题/卡片）
 * 重构说明: 数据类 → record
 */
package axtv.model;

import java.util.List;

/**
 * @param words 组内联想词（{@code words}）；<b>可空</b>
 */
public record SuggestWordGroup(@JsonKey("words") List<SuggestWord> words) {

    public SuggestWordGroup {
        words = words == null ? null : List.copyOf(words);
    }

    public SuggestWordGroup() {
        this(null);
    }
}
