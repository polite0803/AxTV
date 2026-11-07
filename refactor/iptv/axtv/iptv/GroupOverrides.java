/*
 * 来源: 新建（电视台分组手动修正，无原版对应）。
 * 原职责: 无。
 * 重构说明: 频道 id（归一化名）→ 用户指定分组。与目录快照分开存：
 *           目录几天重下一次，用户的修正必须活过每一次重下（PLAN 需求 3）。
 *           因此快照里保留清单原始 group，覆盖只在读取时套用，绝不写回快照。
 */
package axtv.iptv;

import axtv.panel.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** 用户手动修正的分组表。不可变。 */
public record GroupOverrides(Map<String, String> byChannelId) {

    /** 清单没写 group-title 时的落栏名。全工程只此一处定义。 */
    public static final String DEFAULT_GROUP = "未分组";

    public GroupOverrides {
        byChannelId = byChannelId == null ? Map.of() : Map.copyOf(byChannelId);
    }

    public static GroupOverrides empty() {
        return new GroupOverrides(Map.of());
    }

    /** 解析 Settings 里的 JSON 对象文本。空白或损坏一律当空表（用户修正丢失可重设，不该让整页崩）。 */
    public static GroupOverrides parse(String text) {
        if (text == null || text.isBlank()) {
            return empty();
        }
        Map<String, Object> raw;
        try {
            raw = Json.parseObject(text);
        } catch (IllegalArgumentException e) {
            return empty();
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String group = Json.optString(raw, e.getKey());
            if (!e.getKey().isBlank() && !group.isBlank()) {
                parsed.put(e.getKey(), group);
            }
        }
        return new GroupOverrides(parsed);
    }

    /** 序列化为 JSON 对象文本（键按字典序，保证同一份修正每次落盘字节一致）。 */
    public String write() {
        Map<String, Object> sorted = new java.util.TreeMap<>(this.byChannelId);
        return Json.write(sorted);
    }

    /** 新增/改一条修正；group 为空白等价于撤销该条。 */
    public GroupOverrides with(String channelId, String group) {
        if (channelId == null || channelId.isBlank()) {
            return this;
        }
        if (group == null || group.isBlank()) {
            return without(channelId);
        }
        Map<String, String> next = new LinkedHashMap<>(this.byChannelId);
        next.put(channelId, group.trim());
        return new GroupOverrides(next);
    }

    public GroupOverrides without(String channelId) {
        if (channelId == null || !this.byChannelId.containsKey(channelId)) {
            return this;
        }
        Map<String, String> next = new LinkedHashMap<>(this.byChannelId);
        next.remove(channelId);
        return new GroupOverrides(next);
    }

    /** 读取时套用：有修正用修正，否则用清单原始 group，再否则落默认栏。 */
    public String groupFor(String channelId, String rawGroup) {
        String override = this.byChannelId.get(channelId);
        if (override != null && !override.isBlank()) {
            return override;
        }
        return rawGroup == null || rawGroup.isBlank() ? DEFAULT_GROUP : rawGroup;
    }

    public int size() {
        return this.byChannelId.size();
    }
}