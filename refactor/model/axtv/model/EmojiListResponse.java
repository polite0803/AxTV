/*
 * 来源: src/app/com/github/mytv/p004dv/model/EmojiListResponse.java
 * 原职责: 表情列表接口返回体
 * 重构说明: 数据类 → record；$$serializer 未导出，key 按惯例推定
 */
package axtv.model;

import java.util.List;

/**
 * @param emojiList  表情列表（推定 {@code emoji_list}）；<b>可空</b>
 * @param statusCode 状态码（推定 {@code status_code}）
 * @param version    表情库版本号（推定 {@code version}），可用于本地缓存失效判断
 */
public record EmojiListResponse(
        @JsonKey("emoji_list") List<EmojiItem> emojiList,
        @JsonKey("status_code") int statusCode,
        @JsonKey("version") long version) {

    public EmojiListResponse {
        emojiList = emojiList == null ? null : List.copyOf(emojiList);
    }

    public EmojiListResponse() {
        this(null, 0, 0L);
    }
}
