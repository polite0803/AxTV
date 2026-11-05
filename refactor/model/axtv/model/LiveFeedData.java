/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveFeedData.java + LiveFeedData$$serializer.java
 * 原职责: 直播推荐流 data 节点
 * 重构说明: 数据类 → record
 */
package axtv.model;

import java.util.List;

/**
 * @param data 直播列表（{@code data}）；<b>可空</b>
 */
public record LiveFeedData(@JsonKey("data") List<LiveRoomData> data) {

    public LiveFeedData {
        data = data == null ? null : List.copyOf(data);
    }

    public LiveFeedData() {
        this(null);
    }
}
