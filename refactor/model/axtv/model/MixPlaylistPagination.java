/*
 * 来源: src/app/com/github/mytv/p004dv/model/MixPlaylistPagination.java
 * 原职责: 合集播放页的分页缓存（内存态，不参与 JSON）：已加载的「页码 → 作品」
 * 重构说明: 数据类 → record
 */
package axtv.model;

import java.util.Map;

/**
 * @param mixId      合集 id
 * @param pages      页码 → 该页作品
 * @param nextCursor 下一页游标
 * @param hasMore    是否还有下一页
 */
public record MixPlaylistPagination(
        String mixId,
        Map<Integer, java.util.List<Aweme>> pages,
        int nextCursor,
        boolean hasMore) {

    public MixPlaylistPagination {
        mixId = mixId == null ? "" : mixId;
        pages = pages == null ? Map.of() : Map.copyOf(pages);
    }

    public MixPlaylistPagination() {
        this("", Map.of(), 0, false);
    }
}
