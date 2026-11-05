/*
 * 来源: src/app/com/github/mytv/p004dv/model/DanmakuItem.java + DanmakuItem$$serializer.java
 * 原职责: 一条录播弹幕（抖音的弹幕是随视频下发的「时间轴条目」，不是实时弹幕）
 * 重构说明: 数据类 → record；score 是 double，status/show_* 等是数字与布尔的混合形态
 */
package axtv.model;

/**
 * @param danmakuId       弹幕 id（{@code danmaku_id}）
 * @param itemId          条目 id（{@code item_id}）
 * @param userId          发送者 id（{@code user_id}）
 * @param offsetTime      相对视频起点的出现时间，毫秒（{@code offset_time}）
 * @param text            弹幕文本（{@code text}）
 * @param status          审核/展示状态（{@code status}，接口可能返回字符串）
 * @param diggType        点赞类型（{@code digg_type}，接口可能返回字符串）
 * @param score           权重分，决定同屏优先级（{@code score}）
 * @param danmakuType     弹幕类型（{@code danmaku_type}，接口可能返回字符串）。
 *                        取证（2026-11-05）：src/ 反编译产物中该字段只有定义、无任何调用点
 *                        （渲染链 wo0/om5/i42/wx5 均不读取），取值语义未知；原版全部按滚动渲染
 * @param diggCount       点赞数（{@code digg_count}，接口可能返回字符串）
 * @param showDigg        是否显示点赞按钮（{@code show_digg}，接口可能返回字符串）
 * @param showCopy        是否显示复制（{@code show_copy}，接口可能返回字符串）
 * @param fromCopy        是否来自复制（{@code from_copy}，接口可能返回字符串）
 * @param danmakuTypeBits 类型位掩码（{@code danmaku_type_bits}，接口可能返回字符串）。
 *                        取证（2026-11-05）：src/ 中无任何读取点，不参与弹幕分类
 * @param hasEmoji        是否含表情（{@code has_emoji}，接口可能返回字符串）
 * @param dislikeType     不喜欢类型（{@code dislike_type}，接口可能返回字符串）
 * @param isAd            是否广告（{@code is_ad}，接口可能返回字符串）
 */
public record DanmakuItem(
        @JsonKey("danmaku_id") String danmakuId,
        @JsonKey("item_id") String itemId,
        @JsonKey("user_id") String userId,
        @JsonKey("offset_time") long offsetTime,
        @JsonKey("text") String text,
        @JsonKey("status") int status,
        @JsonKey("digg_type") int diggType,
        @JsonKey("score") double score,
        @JsonKey("danmaku_type") int danmakuType,
        @JsonKey("digg_count") int diggCount,
        @JsonKey("show_digg") boolean showDigg,
        @JsonKey("show_copy") boolean showCopy,
        @JsonKey("from_copy") boolean fromCopy,
        @JsonKey("danmaku_type_bits") int danmakuTypeBits,
        @JsonKey("has_emoji") boolean hasEmoji,
        @JsonKey("dislike_type") int dislikeType,
        @JsonKey("is_ad") boolean isAd) {

    public DanmakuItem {
        danmakuId = danmakuId == null ? "" : danmakuId;
        itemId = itemId == null ? "" : itemId;
        userId = userId == null ? "" : userId;
        text = text == null ? "" : text;
    }

    public DanmakuItem() {
        this("", "", "", 0L, "", 0, 0, 0.0d, 0, 0, false, false, false, 0, false, 0, false);
    }
}
