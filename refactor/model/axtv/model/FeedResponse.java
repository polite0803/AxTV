/*
 * 来源: src/app/com/github/mytv/p004dv/model/FeedResponse.java
 * 原职责: 推荐/关注流接口返回体（同时兼容 aweme_list 与 data[].aweme 两种形态）
 * 重构说明: 数据类 → record；hasMore 走 FlexibleBoolean（抖音会用 "true"/1 混合表达）。
 *           原类的 $$serializer 未被 jadx 导出，JSON key 按同模块 snake_case 惯例推定，见 NOTES.md「存疑」。
 */
package axtv.model;

import java.util.List;

/**
 * @param awemeList  作品列表（推定 {@code aweme_list}）；老接口形态，<b>可空</b>
 * @param data       作品包装列表（推定 {@code data}）；新接口形态，<b>可空</b>
 * @param hasMore    是否还有更多（推定 {@code has_more}，接口可能返回字符串/数字）
 * @param maxCursor  下一页游标（推定 {@code max_cursor}）
 * @param cursor     当前游标（推定 {@code cursor}）
 * @param statusCode 状态码，0 = 成功（推定 {@code status_code}）
 */
public record FeedResponse(
        @JsonKey("aweme_list") List<Aweme> awemeList,
        @JsonKey("data") List<FeedDataItem> data,
        @JsonKey("has_more") boolean hasMore,
        @JsonKey("max_cursor") long maxCursor,
        @JsonKey("cursor") long cursor,
        @JsonKey("status_code") int statusCode,
        /** module/feed 的分块响应把完整作品放在这里（顶层 aweme_list 可能只有骨架）。 */
        @JsonKey("preload_awemes") List<Aweme> preloadAwemes) {

    public FeedResponse {
        awemeList = awemeList == null ? null : List.copyOf(awemeList);
        data = data == null ? null : List.copyOf(data);
        preloadAwemes = preloadAwemes == null ? null : List.copyOf(preloadAwemes);
    }

    public FeedResponse() {
        this(null, null, false, 0L, 0L, 0, null);
    }

    /**
     * 归一化取作品列表：优先 {@code aweme_list}，为空则展开 {@code data[].aweme} 并丢掉空壳；
     * 存在 {@code preload_awemes} 时按 awemeId 合并去重（分块响应里完整数据常在这里）。
     */
    public List<Aweme> flatAwemes() {
        List<Aweme> base;
        if (awemeList != null && !awemeList.isEmpty()) {
            base = awemeList;
        } else if (data == null) {
            base = List.of();
        } else {
            base = data.stream().map(FeedDataItem::aweme).filter(a -> a != null).toList();
        }
        if (preloadAwemes == null || preloadAwemes.isEmpty()) {
            return base;
        }
        java.util.LinkedHashMap<String, Aweme> merged = new java.util.LinkedHashMap<>();
        for (Aweme a : base) {
            if (a != null) merged.putIfAbsent(a.awemeId(), a);
        }
        for (Aweme a : preloadAwemes) {
            if (a != null) merged.putIfAbsent(a.awemeId(), a);
        }
        return new java.util.ArrayList<>(merged.values());
    }
}
