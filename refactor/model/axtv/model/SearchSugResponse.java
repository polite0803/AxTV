/*
 * 来源: src/app/com/github/mytv/p004dv/model/SearchSugResponse.java
 * 原职责: 搜索联想词接口返回体
 * 重构说明: 数据类 → record；$$serializer 未导出，key 按惯例推定
 */
package axtv.model;

import java.util.List;

/**
 * @param sugList    联想词条目（推定 {@code sug_list}）；<b>可空</b>
 * @param statusCode 状态码（推定 {@code status_code}）
 */
public record SearchSugResponse(
        @JsonKey("sug_list") List<SearchSugItem> sugList,
        @JsonKey("status_code") int statusCode) {

    public SearchSugResponse {
        sugList = sugList == null ? null : List.copyOf(sugList);
    }

    public SearchSugResponse() {
        this(null, 0);
    }
}
