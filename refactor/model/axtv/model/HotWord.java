/*
 * 来源: src/app/com/github/mytv/p004dv/model/HotWord.java + HotWord$$serializer.java
 * 原职责: 一个热搜词（词、热度、名次、句子 id、标签）
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param word       热搜词（{@code word}）
 * @param hotValue   热度值（{@code hot_value}）
 * @param position   榜上名次，从 1 开始（{@code position}，接口可能返回字符串）
 * @param sentenceId 话题句子 id（{@code sentence_id}）
 * @param label      标签位掩码（{@code label}，如「新」「热」，接口可能返回字符串）
 */
public record HotWord(
        @JsonKey("word") String word,
        @JsonKey("hot_value") long hotValue,
        @JsonKey("position") int position,
        @JsonKey("sentence_id") String sentenceId,
        @JsonKey("label") int label) {

    public HotWord {
        word = word == null ? "" : word;
        sentenceId = sentenceId == null ? "" : sentenceId;
    }

    public HotWord() {
        this("", 0L, 0, "", 0);
    }
}
