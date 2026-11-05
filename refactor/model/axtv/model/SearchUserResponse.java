/*
 * 来源: src/app/com/github/mytv/p004dv/model/SearchUserResponse.java
 * 原职责: 用户搜索接口返回体
 * 重构说明: 数据类 → record；cursor/hasMore 在原模型里是 int。$$serializer 未导出，key 按惯例推定
 */
package axtv.model;

import java.util.List;

/**
 * @param userList   用户条目（推定 {@code user_list}）；<b>可空</b>
 * @param hasMore    是否有下一页（推定 {@code has_more}，0/1）
 * @param cursor     游标（推定 {@code cursor}）
 * @param statusCode 状态码（推定 {@code status_code}）
 * @param logPb      埋点（推定 {@code log_pb}）；<b>可空</b>
 */
public record SearchUserResponse(
        @JsonKey("user_list") List<SearchUserItem> userList,
        @JsonKey("has_more") int hasMore,
        @JsonKey("cursor") int cursor,
        @JsonKey("status_code") int statusCode,
        @JsonKey("log_pb") LogPb logPb) {

    public SearchUserResponse {
        userList = userList == null ? null : List.copyOf(userList);
    }

    public SearchUserResponse() {
        this(null, 0, 0, 0, null);
    }

    public boolean hasMorePages() {
        return hasMore != 0;
    }
}
