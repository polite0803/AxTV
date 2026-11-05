/*
 * 来源: src/app/com/github/mytv/p004dv/model/ResolvedStreamQuality.java（原为包私有 final class）
 * 原职责: 一个已解析出地址的直播档位（名字 + level + 地址）
 * 重构说明: 数据类 → record；提升为 public，供直播模块跨包使用
 */
package axtv.model;

import java.util.List;

/**
 * @param name  档位名（如「蓝光」「超清」）
 * @param level 档位等级，越大越高（接口给的数字）
 * @param urls  该档位地址列表
 */
public record ResolvedStreamQuality(String name, int level, List<String> urls) {

    public ResolvedStreamQuality {
        urls = urls == null ? List.of() : List.copyOf(urls);
        name = name == null ? "" : name;
    }

    /** 原 {@code toResolvedLiveStream()}：名字为空白时显示「默认」。 */
    public ResolvedLiveStream toResolvedLiveStream() {
        String display = name.isBlank() ? "默认" : name;
        return new ResolvedLiveStream(urls, display);
    }
}
