/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetHotWord.java (116 行)
 *       + WidgetHotWord$$serializer.java:29-31 (descriptor 元素名 word / label)
 * 原职责: 热搜词；word(String, 默认 "") + label(int, 默认 0)
 * 重构说明: 精简为 record；保留默认值与 HotWordRow 里 label→徽章映射的语义来源。
 */
package axtv.widget;

/** 热搜词条目。 */
public record WidgetHotWord(
        @JsonKey("word")  String word,
        @JsonKey("label") int label) {

    /** 原 descriptor 的 serial name（WidgetHotWord$$serializer.java:29）。 */
    public static final String SERIAL_NAME = "com.github.axtv.dv.widget.WidgetHotWord";

    public WidgetHotWord {
        word = WidgetVideo.nz(word);
    }

    /** 原无参构造：word=""、label=0。 */
    public WidgetHotWord() {
        this("", 0);
    }
}
