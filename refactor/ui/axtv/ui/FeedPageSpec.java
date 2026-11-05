/*
 * 来源: src/app/p000/on5.java (反编译自 com.github.axtv.dv 的 R8 混淆类)
 *   辅证: tg0.java(case 5/default 评论分页), kq5.java(m7267c 回复 / m7268d 加载更多),
 *         lq5/so3/pn5 枚举, PlayerMenuSpec/PlayerGestureSpec(菜单文案与滑杆模型)
 * 原职责: 主页信息流 + 相关推荐/合集卡片 + 快捷菜单装配 + 互动反馈 + 评论分页 +
 *         弹幕轨道配置 + 占位封面 —— 显示字段、可见条件、点击动作、状态流转
 * 重构说明: 纯 Java 行为规格；文案逐字保留（含标点与空格）。
 */
package axtv.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 主页信息流 / 播放页壳行为规格（on5）。 */
public final class FeedPageSpec {

    // ===== 倍速档位（on5:56 f16125c，速度段选行用）=====
    public static final List<Float> SPEED_OPTIONS =
            List.of(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f);

    // ===== 弹幕轨道参数（on5:59 f16126d，cb1 字段：(偏移dp, 数量, 不透明度, 类型)）=====
    public static final float[][] DANMAKU_LANES = {
            {3.0f, 4.0f, 0.0f},
            {7.0f, 12.0f, 0.35f},
            {11.0f, 20.0f, 0.7f},
            {15.0f, 12.0f, 0.15f},
            {19.0f, 4.0f, 0.55f},
    };
    /** 轨道生成器区间（on5:50/53：180..6，步进 2，两组 hf5 lambda） */
    public static final int DANMAKU_LANE_RANGE_HIGH = 180;
    public static final int DANMAKU_LANE_RANGE_LOW = 6;
    public static final int DANMAKU_LANE_STEP = 2;

    // ===== 文案（逐字保留）=====
    public static final String TEXT_LOAD_MORE = "上滑加载更多";        // on5:333
    public static final String TEXT_MIX_EPISODE_HEADER = "第 N 集";   // on5:10175（带空格，相关卡）
    public static final String TEXT_MIX_PROGRESS_PREFIX = "第";      // on5:6634（无空格，合集头）
    public static final String TEXT_MIX_PROGRESS_MID = "集";          // on5:6634
    public static final String TEXT_MIX_PROGRESS_SEP = " / ";        // on5:6637
    public static final String TEXT_MIX_TOTAL_PREFIX = "共";          // on5:6640
    public static final String TEXT_MIX_TOTAL_SUFFIX = "集";          // on5:6640
    public static final String TEXT_MIX_UNTITLED = "未命名合集";       // on5:6691
    public static final String TEXT_MIX_TITLE_PREFIX = "合集 · ";     // on5:6694
    public static final String TEXT_NOW_PLAYING = "正在播放";          // on5:10200
    public static final String TEXT_PLAY_NOW = "立即播放";            // on5:10202
    public static final String TEXT_RELATED = "相关推荐";             // on5:10204
    public static final String TEXT_UNTITLED_VIDEO = "无标题视频";     // on5:10361
    public static final String TEXT_DESCRIPTION_TITLE = "视频简介";    // on5:6032
    public static final String TEXT_SCREENCASTING = "正在外屏放映";    // on5:8646/9415
    public static final String TEXT_STOP_SCREENCAST = "结束放映";      // on5:8660
    public static final String TEXT_LIKE = "点赞";                    // on5:8035
    public static final String TEXT_UNLIKE = "取消点赞";               // on5:8035
    public static final String TEXT_COMMENT = "评论";                 // on5:8041
    public static final String TEXT_COLLECT = "收藏";                 // on5:8044
    public static final String TEXT_UNCOLLECT = "取消收藏";            // on5:8044
    public static final String TEXT_MORE = "更多";                    // on5:8050
    public static final String TEXT_NET_SPEED_LABEL = "网速";          // on5:6759
    public static final String TEXT_NET_SPEED_DOT = "·";             // on5:6758
    public static final String TEXT_META_SEPARATOR = " · ";          // on5:10198/10200/10202
    public static final String TEXT_AVATAR = "头像";                  // on5:3545 AsyncImage contentDescription
    public static final String TEXT_DANMAKU_ON = "弹幕开";            // on5:1335
    public static final String TEXT_DANMAKU_OFF = "弹幕关";           // on5:1337
    public static final String TEXT_BLOCK_WORDS = "屏蔽词";           // on5:1348（快捷项）
    public static final String TEXT_BRIEF = "简介";                   // on5:1350（快捷项）
    public static final String TEXT_SCREENCAST = "外屏放映";           // on5:1357（快捷项）
    public static final String TEXT_PROCESSING = "处理中";             // on5:1305/1307（快捷项，无省略号）
    public static final String TEXT_LIKED = "已点赞";                 // on5:1310
    public static final String TEXT_COLLECTED = "已收藏";              // on5:1326

    // ===== 合集进度行（on5.m8740m:6630-6641）=====

    /** "第N集 / 共M集"（无空格；currentEpisode/maxEpisode 均 &gt;0 才输出对应段） */
    public static String mixProgressText(int currentEpisode, int maxEpisode) {
        StringBuilder sb = new StringBuilder();
        if (currentEpisode > 0) {
            sb.append(TEXT_MIX_PROGRESS_PREFIX).append(currentEpisode).append(TEXT_MIX_PROGRESS_MID);
        }
        if (currentEpisode > 0 && maxEpisode > 0) {
            sb.append(TEXT_MIX_PROGRESS_SEP);
        }
        if (maxEpisode > 0) {
            sb.append(TEXT_MIX_TOTAL_PREFIX).append(maxEpisode).append(TEXT_MIX_TOTAL_SUFFIX);
        }
        return sb.toString();
    }

    /** 合集总集数（on5:6630/120）：max(statis.totalEpisode, statis.updatedToEpisode) */
    public static int mixTotalEpisodes(int totalEpisode, int updatedToEpisode) {
        return Math.max(totalEpisode, updatedToEpisode);
    }

    /** 合集标题（on5:6689-6694）："合集 · name"，空名→"未命名合集" */
    public static String mixTitleText(String mixName) {
        String name = mixName == null || mixName.isEmpty() ? TEXT_MIX_UNTITLED : mixName;
        return TEXT_MIX_TITLE_PREFIX + name;
    }

    /** 相关卡集数标（on5:10175）："第 N 集"（带空格），episode&lt;=0 → null */
    public static String relatedEpisodeText(Integer episode) {
        return episode != null && episode > 0 ? "第 " + episode + " 集" : null;
    }

    /** 相关卡标题前缀（on5:10199-10205）：选中→"正在播放"；悬停→"立即播放"；无集数→"相关推荐" */
    public static String relatedHeaderPrefix(boolean selected, boolean focused, boolean hasEpisode) {
        if (selected) {
            return TEXT_NOW_PLAYING;
        }
        if (focused) {
            return TEXT_PLAY_NOW;
        }
        return hasEpisode ? null : TEXT_RELATED;
    }

    /** 相关卡描述截断（on5:10155）：&gt;50 字符截断加 "..." */
    public static String truncateDesc(String desc) {
        if (desc == null) {
            return null;
        }
        String head = desc.length() > 50 ? desc.substring(0, 50) : desc;
        return desc.length() > 50 ? head + "..." : head;
    }

    /** 相关卡封面地址（on5:10151-10153）：cover.urlList 末位 → dynamicCover.urlList 末位 → "" */
    public static String pickCoverUrl(List<String> coverUrls, List<String> dynamicCoverUrls) {
        String url = lastOf(coverUrls);
        if (url == null) {
            url = lastOf(dynamicCoverUrls);
        }
        return url == null ? "" : url;
    }

    /** 相关卡 meta 行（on5:10198）：[发布时间, 时长] 用 " · " 连接，空段剔除 */
    public static String joinMeta(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(TEXT_META_SEPARATOR);
            }
            sb.append(p);
        }
        return sb.toString();
    }

    // ===== 合集选择面板（on5.m8675A:62-375，modal bottom sheet）=====

    /**
     * 当前集在列表中的序号（on5:125-143）：按 awemeId 匹配，未命中 → 0。
     * 面板槽位：header / loading(列表空且加载中) / empty(列表空) / 网格项 / related_more。
     * 网格项集数 = 面板头序号(i) + 网格下标（on5:271）。
     */
    public static int mixCurrentIndex(List<String> awemeIds, String currentAwemeId) {
        for (int i = 0; i < awemeIds.size(); i++) {
            if (awemeIds.get(i).equals(currentAwemeId)) {
                return i;
            }
        }
        return 0;
    }

    /** related_more 尾槽（on5:326-336）：加载中→32dp 圆形进度(底距48)；否则文案"上滑加载更多" */
    public static boolean isLoadMoreSpinner(boolean loading) {
        return loading;
    }

    // ===== 启动占位大图卡（on5.m8677B:388-556）=====

    /** 封面卡进度文本（on5:125 区域）："MM:SS / MM:SS"（ControlLayerSpec.formatClock） */
    public static String splashProgressText(long currentMs, long totalMs) {
        return ControlLayerSpec.formatClock(currentMs) + " / " + ControlLayerSpec.formatClock(totalMs);
    }

    /**
     * 封面地址清洗（on5:108-138）：含 "+" 的地址视为无效置 ""。
     * （qx4.m9702y(url, "+", false) 命中 → ""）
     */
    public static String sanitizeThumbUrl(String url) {
        return url != null && url.contains("+") ? "" : url;
    }

    // ===== 互动反馈（on5.m8736k:6467-6477 文案→图标映射）=====

    /** 点赞/收藏浮层图标映射（on5:6468-6477）：e21.t(动画态)/k21/ sp0 / kb4 / wi4 */
    public static String feedbackIcon(String text, boolean processing) {
        if (processing) {
            return "e21.m3297t";          // 处理中动画图标
        }
        return switch (text) {
            case TEXT_UNLIKE -> "k21.m6951G";   // 取消点赞图标
            case TEXT_LIKE -> "sp0.m10294O";     // 点赞图标
            case TEXT_UNCOLLECT -> "kb4.m7092A"; // 取消收藏图标
            case TEXT_COLLECT -> "wi4.m11576o";  // 收藏图标
            default -> "AbstractC0656g4.m4426B"; // 兜底
        };
    }

    // ===== 评论分页（tg0 case=5 回复 / case=default 加载更多，kq5 承载请求）=====

    /**
     * 加载更多结果合并（tg0 default, tg0.java:586-609）：
     * 按 cid 去重（空 cid 剔除）追加到已有列表；cursor ← response.cursor；
     * hasMore ← response.hasMore == 1；loading ← false。
     */
    public static final class CommentPageResult {
        public List<Object> merged = new ArrayList<>();
        public int cursor;
        public boolean hasMore;
    }

    /** 请求协议面（Android 侧由 kq5 实现；核心层不绑 HTTP） */
    public interface CommentApi {
        /** kq5.m7268d(cursor, awemeId) → {statusCode, comments, cursor, hasMore} */
        Map<String, Object> loadMore(int cursor, String awemeId);

        /** kq5.m7267c(awemeId, cid) → {statusCode, comments} */
        Map<String, Object> loadReplies(String awemeId, String cid);
    }

    /** 失败日志（tg0.java:613）TAG="Comments" */
    public static String loadMoreErrorLog(String message) {
        return "Failed to load more: " + message;
    }

    /** 失败日志（tg0.java:547/554）TAG="Comments"；status 为 null 时仅 "Failed to load replies: " + msg */
    public static String loadRepliesErrorLog(Integer statusCode, String cid, String message) {
        return statusCode == null
                ? "Failed to load replies: " + message
                : "Failed to load replies: status=" + statusCode + ", cid=" + cid;
    }

    /** 回复装载（tg0 case=5, tg0.java:522-543）：statusCode==0 → map[cid]=comments（空 map 用 singletonMap） */
    public static Map<String, List<Object>> mergeReplies(Map<String, List<Object>> existing,
                                                         String cid, List<Object> comments) {
        if (existing.isEmpty()) {
            return Collections.singletonMap(cid, comments);
        }
        Map<String, List<Object>> merged = new LinkedHashMap<>(existing);
        merged.put(cid, comments);
        return merged;
    }

    /** cid 去重追加（tg0 default, tg0.java:590-607）：剔除空 cid 与已有 cid */
    public static List<String> appendUniqueByCid(List<String> existingCids, List<String> incoming) {
        Set<String> seen = new LinkedHashSet<>(existingCids);
        List<String> out = new ArrayList<>();
        for (String cid : incoming) {
            if (cid != null && !cid.isEmpty() && !seen.contains(cid)) {
                seen.add(cid);
                out.add(cid);
            }
        }
        return out;
    }

    // ===== 简单工具 =====

    private static String lastOf(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        return urls.get(urls.size() - 1);
    }

    private FeedPageSpec() {
    }
}
