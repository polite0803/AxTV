/*
 * 来源: src/app/com/github/mytv/p004dv/model/SuggestWordsResponse.java
 * 原职责: 搜索框「猜你想搜」接口返回体
 * 重构说明: 数据类 → record；$$serializer 未导出，key 按惯例推定
 */
package axtv.model;

import java.util.List;

/**
 * @param data       联想词组（推定 {@code data}）；<b>可空</b>
 * @param statusCode 状态码（推定 {@code status_code}）
 */
public record SuggestWordsResponse(
        @JsonKey("data") List<SuggestWordGroup> data,
        @JsonKey("status_code") int statusCode) {

    public SuggestWordsResponse {
        data = data == null ? null : List.copyOf(data);
    }

    public SuggestWordsResponse() {
        this(null, 0);
    }
}
