/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveFeedDataHome.java + LiveFeedDataHome$$serializer.java
 * 原职责: 首页直播流的单条（web_rid + 房间）
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param webRid web 房间号（{@code web_rid}）
 * @param data   房间详情（{@code data}）；<b>可空</b>
 */
public record LiveFeedDataHome(
        @JsonKey("web_rid") String webRid,
        @JsonKey("data") LiveRoom data) {

    public LiveFeedDataHome {
        webRid = webRid == null ? "" : webRid;
    }

    public LiveFeedDataHome() {
        this("", null);
    }
}
