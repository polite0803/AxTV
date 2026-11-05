/*
 * 来源: src/app/com/github/mytv/p004dv/model/MixListResponse.java
 * 原职责: 合集列表接口返回体
 * 重构说明: 数据类 → record；cursor 的主键是 {@code cursor}，备用 {@code next_cursor}，
 *           mixInfos 的主键 {@code mix_infos}（来自 @c72 JsonNames 注解）
 */
package axtv.model;

import java.util.List;

/**
 * @param mixInfos   合集列表（{@code mix_infos}）
 * @param hasMore    是否有下一页（推定 {@code has_more}，接口可能返回字符串）
 * @param cursor     下一页游标（{@code cursor}，备用 key {@code next_cursor}）
 * @param statusCode 状态码（推定 {@code status_code}）
 */
public record MixListResponse(
        @JsonKey("mix_infos") List<MixInfo> mixInfos,
        @JsonKey("has_more") boolean hasMore,
        @JsonKey(value = "cursor", alternates = {"next_cursor"}) long cursor,
        @JsonKey("status_code") int statusCode) {

    public MixListResponse {
        mixInfos = mixInfos == null ? null : List.copyOf(mixInfos);
    }

    public MixListResponse() {
        this(null, false, 0L, 0);
    }
}
