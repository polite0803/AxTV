/*
 * 来源: src/app/com/github/mytv/p004dv/model/SearchLivePage.java
 * 原职责: 直播搜索的分页状态（内存态，不参与 JSON）
 * 重构说明: 数据类 → record
 */
package axtv.model;

import java.util.List;

/**
 * @param rooms    已加载的直播
 * @param hasMore  是否还有下一页
 * @param searchId 本次搜索会话 id，翻页要带上
 */
public record SearchLivePage(List<LiveRoom> rooms, boolean hasMore, String searchId) {

    public SearchLivePage {
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
        searchId = searchId == null ? "" : searchId;
    }

    public SearchLivePage() {
        this(List.of(), false, "");
    }
}
