/*
 * 来源: src/app/com/github/mytv/p004dv/model/CollectItem.java + CollectItem$$serializer.java
 * 原职责: 收藏夹条目（收藏夹不是作品，是「合集」）
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param collectId    收藏夹 id（{@code collect_id}）
 * @param collectName  收藏夹名（{@code collect_name}）
 * @param collectCount 收藏夹内作品数（{@code collect_count}，接口可能返回字符串）
 */
public record CollectItem(
        @JsonKey("collect_id") String collectId,
        @JsonKey("collect_name") String collectName,
        @JsonKey("collect_count") int collectCount) {

    public CollectItem {
        collectId = collectId == null ? "" : collectId;
        collectName = collectName == null ? "" : collectName;
    }

    public CollectItem() {
        this("", "", 0);
    }
}
