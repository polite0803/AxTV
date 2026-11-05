/*
 * 来源: src/app/com/github/mytv/p004dv/model/DanmakuResponse.java
 * 原职责: 录播弹幕接口返回体
 * 重构说明: 数据类 → record；$$serializer 未导出，key 按惯例推定
 */
package axtv.model;

import java.util.List;

/**
 * @param danmakuList 弹幕列表（推定 {@code danmaku_list}）；<b>可空</b>
 * @param statusCode  状态码（推定 {@code status_code}）
 */
public record DanmakuResponse(
        @JsonKey("danmaku_list") List<DanmakuItem> danmakuList,
        @JsonKey("status_code") int statusCode) {

    public DanmakuResponse {
        danmakuList = danmakuList == null ? null : List.copyOf(danmakuList);
    }

    public DanmakuResponse() {
        this(null, 0);
    }
}
