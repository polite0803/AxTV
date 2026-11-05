/*
 * 来源: src/app/com/github/mytv/p004dv/model/RoomViewStats.java + RoomViewStats$$serializer.java
 * 原职责: 直播间观看人数的多套展示文案（短/中/长 + 主播视角版本）
 * 重构说明: 数据类 → record；isHidden/incremental 走 FlexibleBoolean，
 *           displayValue/displayVersion 走 FlexibleLong，displayType 走 FlexibleInt
 */
package axtv.model;

/**
 * @param isHidden            是否隐藏人数（{@code is_hidden}，接口可能返回字符串）
 * @param displayShort        短文案（{@code display_short}）
 * @param displayMiddle       中文案（{@code display_middle}）
 * @param displayLong         长文案（{@code display_long}）
 * @param displayValue        人数原始值（{@code display_value}，接口可能返回字符串）
 * @param displayVersion      展示版本号（{@code display_version}，接口可能返回字符串）
 * @param incremental         是否增量计数（{@code incremental}，接口可能返回字符串）
 * @param displayType         展示类型，1 = 用 anchor 版中文案（{@code display_type}，接口可能返回字符串）
 * @param displayShortAnchor  主播视角短文案（{@code display_short_anchor}）
 * @param displayMiddleAnchor 主播视角中文案（{@code display_middle_anchor}）
 * @param displayLongAnchor   主播视角长文案（{@code display_long_anchor}）
 */
public record RoomViewStats(
        @JsonKey("is_hidden") boolean isHidden,
        @JsonKey("display_short") String displayShort,
        @JsonKey("display_middle") String displayMiddle,
        @JsonKey("display_long") String displayLong,
        @JsonKey("display_value") long displayValue,
        @JsonKey("display_version") long displayVersion,
        @JsonKey("incremental") boolean incremental,
        @JsonKey("display_type") int displayType,
        @JsonKey("display_short_anchor") String displayShortAnchor,
        @JsonKey("display_middle_anchor") String displayMiddleAnchor,
        @JsonKey("display_long_anchor") String displayLongAnchor) {

    public RoomViewStats {
        displayShort = displayShort == null ? "" : displayShort;
        displayMiddle = displayMiddle == null ? "" : displayMiddle;
        displayLong = displayLong == null ? "" : displayLong;
        displayShortAnchor = displayShortAnchor == null ? "" : displayShortAnchor;
        displayMiddleAnchor = displayMiddleAnchor == null ? "" : displayMiddleAnchor;
        displayLongAnchor = displayLongAnchor == null ? "" : displayLongAnchor;
    }

    public RoomViewStats() {
        this(false, "", "", "", 0L, 0L, false, 0, "", "", "");
    }
}
