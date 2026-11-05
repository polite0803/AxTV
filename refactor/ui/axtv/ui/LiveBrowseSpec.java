/*
 * 来源: src/app/p000/he4.java (2,193 行，直播分区/搜索结果 UI)
 *   辅证: re4.java(搜索频道枚举：视频/用户/直播)、SearchUiSpec(ye4 状态层文案)、
 *         axtv.model.DataModels.formatCount、axtv.model.LiveRoomUtils
 * 原职责: 搜索结果页 UI（隐藏竖屏筛选/三频道 Tab/结果卡/空态）+
 *         直播结果卡 + 用户卡 + 结果条目去重键 —— 显示字段、可见条件、点击动作
 * 重构说明: 纯 Java 行为规格；文案逐字保留（含标点与空格），数字常量注明来源行号。
 *
 * he4 分区归属（详见 refactor/ui/NOTES-eu5-he4.md）：
 *  m5083a 隐藏竖屏chip / m5084b 直播结果卡 / m5085c 直播结果列表 / m5086d 搜索页脚手架 /
 *  m5087e 视频结果瀑布流 / m5088f 结果页头部 / m5089g 结果标题 / m5090h 间隔 /
 *  m5091i 空态占位 / m5092j 搜索页根 / m5093k 频道chip / m5094l 频道行 /
 *  m5095m 搜索输入行 / m5096n 建议行 / m5097o 建议/历史行 / m5098p 用户卡 /
 *  m5099q 用户结果列表 / m5100r 视频宽高比。
 */
package axtv.ui;

import axtv.model.Aweme;
import axtv.model.DataModels;

import java.util.ArrayList;
import java.util.List;

/** 直播分区 / 搜索结果页行为规格（he4）。 */
public final class LiveBrowseSpec {

    private LiveBrowseSpec() {
    }

    // ===== 搜索频道（原 re4 枚举，re4:19-21，顺序即 Tab 顺序）=====
    /** 搜索频道：名称与 Tab 文案。 */
    public record SearchTab(String name, String label) {
        /** 构造 Tab 顺序表：视频 → 用户 → 直播（re4.values()）。 */
        public static List<SearchTab> tabs() {
            return List.of(new SearchTab("VIDEO", "视频"),
                    new SearchTab("USER", "用户"),
                    new SearchTab("LIVE", "直播"));
        }
    }

    // ===== 文案（逐字，来源行号见注释）=====
    /** 结果页筛选 chip（he4:115）。 */
    public static final String TEXT_HIDE_PORTRAIT = "隐藏竖屏";
    /** 直播结果卡徽标（he4:239）。 */
    public static final String TEXT_LIVE_BADGE = "LIVE";
    /** 直播结果卡主播名兜底（he4:255）。 */
    public static final String TEXT_ANCHOR_FALLBACK = "主播";
    /** 用户结果卡昵称兜底（he4:2023）。 */
    public static final String TEXT_USER_FALLBACK = "抖音用户";
    /** 用户结果卡粉丝行前缀（he4:2034，"粉丝 1.2万"）。 */
    public static final String TEXT_FANS_PREFIX = "粉丝 ";
    /** 结果页主标题（he4:774，"「关键词」的搜索结果"）。 */
    public static final String TEXT_RESULT_TITLE_PREFIX = "「";
    public static final String TEXT_RESULT_TITLE_SUFFIX = "」的搜索结果";
    /** 搜索 body 状态文案（he4:1138/1147/1154/1205）。 */
    public static final String TEXT_SEARCHING = "搜索中...";
    public static final String TEXT_SEARCH_FAILED = "搜索失败";
    public static final String TEXT_INPUT_HINT = "输入关键词开始搜索";
    /** 空态文案复用 SearchUiSpec（ye4 已定义；he4:293/1233/2086 打印同串）。 */
    public static final String EMPTY_VIDEO = SearchUiSpec.EMPTY_VIDEO;
    public static final String EMPTY_USER = SearchUiSpec.EMPTY_USER;
    public static final String EMPTY_LIVE = SearchUiSpec.EMPTY_LIVE;
    /** 结果页返回钮 contentDescription（he4:731）。 */
    public static final String TEXT_BACK = "返回";
    /** 加载更多 footer（he4:599/6877 对应物）：列表尾 item key 与其取值。 */
    public static final String KEY_LOADING_MORE = "loading_more";
    public static final String VALUE_LOADING = "loading";

    // ===== 宽高比 / 竖屏筛选 =====

    /** 横屏比例缺省值（he4:2188）。 */
    public static final float ASPECT_LANDSCAPE = 1.7777778f;

    /**
     * 视频宽高比（m5100r，he4:2184-2191）：width/height；任一 &le;0 → 16:9。
     */
    public static float aspectRatio(int width, int height) {
        if (width <= 0 || height <= 0) {
            return ASPECT_LANDSCAPE;
        }
        return (float) width / (float) height;
    }

    /**
     * "隐藏竖屏"筛选（he4:1219）：开启时仅保留 height &le; width（横屏）的视频。
     */
    public static boolean keepVideo(boolean hidePortrait, int width, int height) {
        return !hidePortrait || height <= width;
    }

    /** 隐藏竖屏筛选（he4:1214-1225）。 */
    public static List<Aweme> applyHidePortrait(List<Aweme> results, boolean hidePortrait) {
        if (!hidePortrait) {
            return results;
        }
        List<Aweme> kept = new ArrayList<>();
        for (Aweme aweme : results) {
            if (keepVideo(hidePortrait, aweme.video().width(), aweme.video().height())) {
                kept.add(aweme);
            }
        }
        return kept;
    }

    // ===== 结果条目去重键（m5087e，he4:424-449）=====

    /**
     * 视频结果的去重键（he4:428-444）：
     * aweme_id 空白 → {@code "fallback:" + uid长度 + ":" + uid + ":" + createTime + ":"
     * + desc长度 + ":" + desc}；
     * 否则 → {@code "id:" + id长度 + ":" + id}。
     */
    public static String dedupeKey(String awemeId, String uid, long createTime, String desc) {
        if (awemeId == null || awemeId.isBlank()) {
            return "fallback:" + safeLength(uid) + ":" + safe(uid) + ":" + createTime
                    + ":" + safeLength(desc) + ":" + safe(desc);
        }
        return "id:" + awemeId.length() + ":" + awemeId;
    }

    /**
     * 瀑布流条目的稳定 key（he4:449）：{@code "result:" + 去重键 + ":" + 出现序号}
     * （同一去重键第 N 次出现序号为 N，从 0 起）。
     */
    public static String itemKey(String dedupeKey, int occurrence) {
        return "result:" + dedupeKey + ":" + occurrence;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static int safeLength(String s) {
        return s == null ? 0 : s.length();
    }

    // ===== 结果列表布局 =====

    /**
     * 结果网格列数（m5085c:306-318 / m5087e:458-475 / m5099q:2135-2142）：
     * TV 紧凑布局固定 2 列；否则按窗口宽度档位：0→2 列、1→3 列、2→4 列、其他→5 列。
     *
     * @param layoutOrdinal 原 ww5 布局档位 ordinal
     * @param tvCompact     TV 紧凑布局（原 br3.f2435K）
     */
    public static int columnCount(int layoutOrdinal, boolean tvCompact) {
        if (tvCompact) {
            return 2;
        }
        switch (layoutOrdinal) {
            case 0:
                return 2;
            case 1:
                return 3;
            case 2:
                return 4;
            default:
                return 5;
        }
    }

    // ===== 搜索 body 状态机（m5092j，he4:1136-1233）=====

    /** 搜索结果区显示什么（原 m5092j 的 if/else 链）。 */
    public enum SearchBody {
        /** 加载中：占位 "搜索中..."（he4:1138）。 */
        LOADING,
        /** 错误：显示 error 文案（null → "搜索失败"，he4:1145-1149）。 */
        ERROR,
        /** 提示："输入关键词开始搜索"（he4:1154/1205）。 */
        HINT,
        /** 用户结果列表（he4:1161-1180）。 */
        USER_LIST,
        /** 直播结果列表（he4:1182-1201）。 */
        LIVE_LIST,
        /** 视频结果瀑布流（he4:1208-1257）。 */
        VIDEO_LIST
    }

    /**
     * 搜索 body 状态机（he4:1136-1233 逐分支）：
     * <ol>
     *   <li>搜索中 → LOADING；</li>
     *   <li>error 非空 → ERROR；</li>
     *   <li>关键词空白 → HINT；</li>
     *   <li>Tab=用户 → USER_LIST；Tab=直播 → LIVE_LIST；</li>
     *   <li>Tab=视频：原始结果空 → HINT；否则经"隐藏竖屏"过滤后
     *       空 → 视频空态文案，非空 → VIDEO_LIST（he4:1203-1233）。</li>
     * </ol>
     *
     * @param tabName "VIDEO" / "USER" / "LIVE"
     */
    public static SearchBody resolveBody(
            boolean searching, String error, String query, String tabName, boolean videoResultsEmpty) {
        if (searching) {
            return SearchBody.LOADING;
        }
        if (error != null) {
            return SearchBody.ERROR;
        }
        if (query == null || query.isBlank()) {
            return SearchBody.HINT;
        }
        switch (tabName) {
            case "USER":
                return SearchBody.USER_LIST;
            case "LIVE":
                return SearchBody.LIVE_LIST;
            default:
                return videoResultsEmpty ? SearchBody.HINT : SearchBody.VIDEO_LIST;
        }
    }

    /** ERROR 态显示的文案（he4:1145-1149）。 */
    public static String errorText(String error) {
        return error == null ? TEXT_SEARCH_FAILED : error;
    }

    /** HINT/空态文案（he4:1154/1205/1233）。 */
    public static String hintText() {
        return TEXT_INPUT_HINT;
    }

    /**
     * 结果页头部筛选 chip 可见性（he4:1088-1090）：
     * 仅当 Tab=视频 且视频结果非空时显示"隐藏竖屏"chip。
     */
    public static boolean showHidePortraitChip(String tabName, boolean videoResultsEmpty) {
        return "VIDEO".equals(tabName) && !videoResultsEmpty;
    }

    /** 结果页主标题（he4:774）。 */
    public static String resultTitle(String query) {
        return TEXT_RESULT_TITLE_PREFIX + query + TEXT_RESULT_TITLE_SUFFIX;
    }

    // ===== 结果卡 =====

    /**
     * 直播结果卡显示槽位（m5084b，he4:136-279）。
     *
     * @param coverUrl       封面 URL（cover.urlList 首 URL）
     * @param ownerName      主播昵称（空白 → "主播"，he4:253-256）
     * @param title          直播标题（空白则整行不渲染，he4:260-270）
     * @param viewerText     在线人数文案（userCount 解析 &gt;0 才显示 "formatCount人"，he4:240-244）
     */
    public record LiveResultCard(String coverUrl, String ownerName, String title, String viewerText) {
    }

    /**
     * 直播结果卡的在线人数文案（he4:240-244）：人数解析失败或 &le;0 → null（不渲染）；
     * 否则 {@code formatCount(n) + "人"}（无空格）。
     */
    public static String liveViewerText(long viewerCount) {
        return viewerCount > 0 ? DataModels.formatCount(viewerCount) + "人" : null;
    }

    /** he4:240-241 的字符串人数解析（原 qx4.m9695B(userCount) → Long?，解析失败按 0）。 */
    public static long parseCount(String countText) {
        if (countText == null || countText.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(countText.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 直播结果卡主播名（he4:253-256）：空白 → "主播"。 */
    public static String anchorName(String nickname) {
        return nickname == null || nickname.isBlank() ? TEXT_ANCHOR_FALLBACK : nickname;
    }

    /**
     * 用户结果卡显示槽位（m5098p，he4:1922-2067）。
     *
     * @param avatarUrl 头像 URL（avatar_thumb 首 URL，he4:1989）
     * @param nickname  昵称（空白 → "抖音用户"，he4:2021-2024）
     * @param fansText  粉丝行（followerCount &gt;0 → "粉丝 N"，否则 null 不渲染，he4:2032-2040）
     * @param signature 个性签名（空白则整行不渲染，he4:2047-2057）
     */
    public record UserCard(String avatarUrl, String nickname, String fansText, String signature) {
    }

    /** 用户结果卡昵称（he4:2021-2024）。 */
    public static String userNickname(String nickname) {
        return nickname == null || nickname.isBlank() ? TEXT_USER_FALLBACK : nickname;
    }

    /** 用户结果卡粉丝行（he4:2032-2034）。 */
    public static String fansText(long followerCount) {
        return followerCount > 0 ? TEXT_FANS_PREFIX + DataModels.formatCount(followerCount) : null;
    }

    // ===== 建议 / 历史行（m5097o，he4:1691-1919）=====

    /**
     * 建议/历史行的排名徽标配色分支（he4:1795-1799）：排名 &le;3 用高亮前景，
     * 否则普通前景；排名数字原样显示（"1"…"N"）。
     */
    public static boolean rankHighlighted(int rank) {
        return rank <= 3;
    }
}
