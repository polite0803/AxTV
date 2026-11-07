/*
 * 来源: 新建（电视台目录持久化，无原版对应）。
 * 原职责: 无。
 * 重构说明: 一次成功聚合后的目录快照，落盘后供启动/切页直接读，不再每次拉网（PLAN 需求 1、5）。
 *           条目里存的是清单【原始】group，用户修正与活性标注都在读取时套用，
 *           所以重下目录不会冲掉修正，也不会冲掉活性（两份文件各自独立）。
 *           schemaVersion 不认识就整份拒收，由调用方回落拉网——绝不把读不懂的快照
 *           渲染成「没有频道」的假空态（PLAN R1/R2）。
 */
package axtv.iptv;

import axtv.panel.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 目录快照。不可变；entries 顺序即 UI 展示顺序的基础（分组前）。 */
public record CatalogSnapshot(int schemaVersion, long fetchedAtEpochMs, List<Entry> entries) {

    /** 当前落盘格式版本。改字段含义必须升号，旧号一律拒收。 */
    public static final int SCHEMA_VERSION = 1;

    /**
     * 一个频道。
     *
     * @param id         归一化频道名，稳定、可直接进播放路由
     * @param name       展示名
     * @param rawGroup   清单原始 group-title（未套用户修正）
     * @param sourceName 提供展示信息的那个源的名字（内置优先）
     * @param candidates 有序候选播放地址，内置源在前
     */
    public record Entry(
        String id,
        String name,
        String logoUrl,
        String rawGroup,
        String sourceName,
        List<String> candidates
    ) {
        public Entry {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
            logoUrl = logoUrl == null ? "" : logoUrl;
            rawGroup = rawGroup == null ? "" : rawGroup;
            sourceName = sourceName == null ? "" : sourceName;
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public CatalogSnapshot {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static CatalogSnapshot of(long fetchedAtEpochMs, List<Entry> entries) {
        return new CatalogSnapshot(SCHEMA_VERSION, fetchedAtEpochMs, entries);
    }

    public int totalChannels() {
        return this.entries.size();
    }

    /** 按 id 反查（播放页取候选用）。 */
    public Map<String, Entry> byId() {
        Map<String, Entry> index = new LinkedHashMap<>();
        for (Entry e : this.entries) {
            index.putIfAbsent(e.id(), e);
        }
        return index;
    }

    /** 序列化。键序与条目顺序固定，保证 write→parse→write 逐字节一致。 */
    public String write() {
        List<Object> items = new ArrayList<>(this.entries.size());
        for (Entry e : this.entries) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.id());
            item.put("name", e.name());
            item.put("logo", e.logoUrl());
            item.put("group", e.rawGroup());
            item.put("source", e.sourceName());
            item.put("urls", e.candidates());
            items.add(item);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", (long) this.schemaVersion);
        root.put("fetchedAt", this.fetchedAtEpochMs);
        root.put("entries", items);
        return Json.write(root);
    }

    /**
     * 解析。损坏 JSON、版本不符、entries 不是数组、条目缺 id 一律抛
     * {@link IllegalArgumentException}，由调用方决定回落拉网。
     */
    public static CatalogSnapshot parse(String text) {
        Map<String, Object> root = Json.parseObject(text);
        long version = Json.optLong(root, "schemaVersion", -1L);
        if (version != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported catalog schemaVersion: " + version);
        }
        if (!root.containsKey("entries") || !(root.get("entries") instanceof List)) {
            throw new IllegalArgumentException("Catalog snapshot has no entries array");
        }
        List<Entry> entries = new ArrayList<>();
        for (Object item : Json.optList(root, "entries")) {
            if (!(item instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("Catalog entry is not an object");
            }
            Map<String, Object> map = asMap(item);
            String id = Json.optString(map, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("Catalog entry has blank id");
            }
            List<String> urls = new ArrayList<>();
            for (Object u : Json.optList(map, "urls")) {
                String url = u == null ? "" : String.valueOf(u);
                if (!url.isBlank()) {
                    urls.add(url);
                }
            }
            entries.add(new Entry(
                id,
                Json.optString(map, "name"),
                Json.optString(map, "logo"),
                Json.optString(map, "group"),
                Json.optString(map, "source"),
                urls
            ));
        }
        return new CatalogSnapshot(SCHEMA_VERSION, Json.optLong(root, "fetchedAt", 0L), entries);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}