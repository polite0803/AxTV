/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveFeedResponseHome.java
 * 原职责: 首页直播流接口返回体
 * 重构说明: 数据类 → record；$$serializer 未导出，key 按 snake_case 惯例推定
 */
package axtv.model;

import java.util.List;

/**
 * @param data       首页直播条目（推定 {@code data}）；<b>可空</b>
 * @param statusCode 状态码（推定 {@code status_code}）
 */
public record LiveFeedResponseHome(@JsonKey("data") List<LiveFeedDataHome> data,
                                   @JsonKey("status_code") int statusCode) {

    public LiveFeedResponseHome {
        data = data == null ? null : List.copyOf(data);
    }

    public LiveFeedResponseHome() {
        this(null, 0);
    }
}
