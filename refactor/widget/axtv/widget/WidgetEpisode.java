/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetEpisode.java (117 行)
 *       + WidgetEpisode$$serializer.java:28-30 (descriptor 元素名 number / awemeId，均为必填)
 * 原职责: 合集选集条目；number(int) + awemeId(String)，两个字段都无默认值（deserializer 缺字段抛异常）
 * 重构说明: 精简为 record；awemeId null 归一为空串（原 Kotlin 非空类型）。
 */
package axtv.widget;

/** 合集选集条目。 */
public record WidgetEpisode(
        @JsonKey("number")   int number,
        @JsonKey("awemeId")  String awemeId) {

    /** 原 descriptor 的 serial name（WidgetEpisode$$serializer.java:28）。 */
    public static final String SERIAL_NAME = "com.github.axtv.dv.widget.WidgetEpisode";

    public WidgetEpisode {
        awemeId = WidgetVideo.nz(awemeId);
    }
}
