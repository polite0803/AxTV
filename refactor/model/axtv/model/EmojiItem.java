/*
 * 来源: src/app/com/github/mytv/p004dv/model/EmojiItem.java + EmojiItem$$serializer.java
 * 原职责: 评论区一个表情（小黄脸等的展示名 + 图片地址）
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param displayName 展示名（{@code display_name}），输入框里以 [名字] 形式插入
 * @param originUri   原始 uri（{@code origin_uri}），用来做去重/匹配
 * @param hide        是否隐藏（{@code hide}，接口可能返回字符串；非 0 表示隐藏）
 * @param emojiUrl    图片地址（{@code emoji_url}）；<b>可空</b>
 */
public record EmojiItem(
        @JsonKey("display_name") String displayName,
        @JsonKey("origin_uri") String originUri,
        @JsonKey("hide") int hide,
        @JsonKey("emoji_url") EmojiUrl emojiUrl) {

    public EmojiItem {
        displayName = displayName == null ? "" : displayName;
        originUri = originUri == null ? "" : originUri;
    }

    public EmojiItem() {
        this("", "", 0, null);
    }

    public boolean isHidden() {
        return hide != 0;
    }
}
