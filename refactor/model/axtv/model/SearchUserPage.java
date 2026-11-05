/*
 * 来源: src/app/com/github/mytv/p004dv/model/SearchUserPage.java
 * 原职责: 用户搜索的分页状态（内存态，不参与 JSON）
 * 重构说明: 数据类 → record
 */
package axtv.model;

import java.util.List;

/**
 * @param users    已加载的用户
 * @param hasMore  是否还有下一页
 * @param searchId 本次搜索会话 id，翻页要带上
 */
public record SearchUserPage(List<Author> users, boolean hasMore, String searchId) {

    public SearchUserPage {
        users = users == null ? List.of() : List.copyOf(users);
        searchId = searchId == null ? "" : searchId;
    }

    public SearchUserPage() {
        this(List.of(), false, "");
    }
}
