/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveCorePullOptions.java
 * 原职责: live_core_sdk_data.pull_data.options 的强类型形态（只要档位列表）
 * 重构说明: 数据类 → record；原代码两条路径都走（先当字符串解包再反序列化成这个类），
 *           核心层由调用方决定用哪种形态，这里只保留结构
 */
package axtv.model;

import java.util.List;

/**
 * @param qualities 档位列表（{@code qualities}）
 */
public record LiveCorePullOptions(List<LiveStreamQuality> qualities) {

    public LiveCorePullOptions {
        qualities = qualities == null ? List.of() : List.copyOf(qualities);
    }

    public LiveCorePullOptions() {
        this(List.of());
    }
}
