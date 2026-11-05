/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveRoomEnterData.java + LiveRoomEnterData$$serializer.java
 * 原职责: 进房接口 data 节点（房间数组，通常是单元素）
 * 重构说明: 数据类 → record
 */
package axtv.model;

import java.util.List;

/**
 * @param data 房间列表（{@code data}）；<b>可空</b>，接口异常时不给
 */
public record LiveRoomEnterData(@JsonKey("data") List<LiveRoom> data) {

    public LiveRoomEnterData {
        data = data == null ? null : List.copyOf(data);
    }

    public LiveRoomEnterData() {
        this(null);
    }

    /** 取第一个房间，没有则 null。 */
    public LiveRoom firstRoom() {
        return data == null || data.isEmpty() ? null : data.get(0);
    }
}
