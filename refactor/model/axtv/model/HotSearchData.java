/*
 * 来源: src/app/com/github/mytv/p004dv/model/HotSearchData.java + HotSearchData$$serializer.java
 * 原职责: 热搜榜数据节点
 * 重构说明: 数据类 → record
 */
package axtv.model;

import java.util.List;

/**
 * @param wordList 热搜词列表（{@code word_list}）；<b>可空</b>
 */
public record HotSearchData(@JsonKey("word_list") List<HotWord> wordList) {

    public HotSearchData {
        wordList = wordList == null ? null : List.copyOf(wordList);
    }

    public HotSearchData() {
        this(null);
    }
}
