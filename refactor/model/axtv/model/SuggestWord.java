/*
 * 来源: src/app/com/github/mytv/p004dv/model/SuggestWord.java + SuggestWord$$serializer.java
 * 原职责: 一个「猜你想搜」词条
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param word     词条文本（{@code word}）
 * @param wordType 词条类型（{@code word_type}，接口可能返回字符串）
 */
public record SuggestWord(
        @JsonKey("word") String word,
        @JsonKey("word_type") int wordType) {

    public SuggestWord {
        word = word == null ? "" : word;
    }

    public SuggestWord() {
        this("", 0);
    }
}
