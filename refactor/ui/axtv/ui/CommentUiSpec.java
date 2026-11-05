/*
 * 来源: src/app/p000/cb0.java (823 行，评论面板渲染)
 * 原职责: 评论项 UI —— 相对时间文案(cb0:147-160)、作者行("匿名用户"/" · " 分隔, cb0:170-195)、
 *         评论区标题行("评论" + formatCount, cb0:532)、LazyList 去重 key 生成(m2151g, cb0:797-823)、
 *         点赞数 > 0 才显示(formatCount(diggCount), m2149e, cb0:703 附近)
 * 重构说明: Compose 渲染样板不翻译，只落可执行的行为计算。绘制归 Android 侧。
 */
package axtv.ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import axtv.model.Comment;
import axtv.model.DataModels;

/** 评论面板的行为规格（纯函数 + 常量）。 */
public final class CommentUiSpec {

    /** 分隔符（cb0:186 {@code " · ".concat(str4)}）。 */
    public static final String SEPARATOR = " · ";
    /** 匿名兜底名（cb0:170，cid 为空时按昵称 blank 判定）。 */
    public static final String ANONYMOUS = "匿名用户";
    /** 评论区标题（cb0:532）。 */
    public static final String HEADER_LABEL = "评论";
    /** 一分钟（毫秒）。 */
    public static final long MIN_MS = 60_000L;
    /** 一小时（毫秒）。 */
    public static final long HOUR_MS = 3_600_000L;
    /** 一天（毫秒）。 */
    public static final long DAY_MS = 86_400_000L;
    /** 相对时间上限 = 30 天（cb0:158 常量 2_592_000_000L），超过走 SimpleDateFormat。 */
    public static final long MAX_RELATIVE_MS = 2_592_000_000L;

    private CommentUiSpec() {
    }

    /**
     * 相对时间文案，逐分支还原 cb0:147-160。
     *
     * @param createTimeSeconds 评论创建时间（秒，原 {@code create_time} 字段）
     * @param nowMillis         当前毫秒（原实现取 state 里的时钟值，测试可注入）
     * @param fmt               超过 30 天用的日期格式（原实现由调用方传入，保持一致）
     * @return 文案；createTime &le; 0 返回空串（不显示时间）
     */
    public static String relativeTime(long createTimeSeconds, long nowMillis, SimpleDateFormat fmt) {
        if (createTimeSeconds <= 0) {
            return "";
        }
        long delta = nowMillis - createTimeSeconds * 1000L;
        if (delta < MIN_MS) {
            return "刚刚";
        }
        if (delta < HOUR_MS) {
            return (delta / MIN_MS) + "分钟前";
        }
        if (delta < DAY_MS) {
            return (delta / HOUR_MS) + "小时前";
        }
        if (delta < MAX_RELATIVE_MS) {
            return (delta / DAY_MS) + "天前";
        }
        return fmt.format(new Date(createTimeSeconds * 1000L));
    }

    /**
     * 作者行：昵称 blank 时显示「匿名用户」（cb0:170
     * {@code jx4.m6866L(str2) ? "匿名用户" : str2}，m6866L 是 isBlank 判断）。
     */
    public static String displayName(String nickname) {
        return nickname == null || nickname.isBlank() ? ANONYMOUS : nickname;
    }

    /** 作者行整行：{@code 昵称} + 可选 {@code " · " + 相对时间}（cb0:186-195，时间为空不拼接）。 */
    public static String authorLine(String nickname, long createTimeSeconds, long nowMillis,
                                    SimpleDateFormat fmt) {
        String name = displayName(nickname);
        String time = relativeTime(createTimeSeconds, nowMillis, fmt);
        return time.isEmpty() ? name : name + SEPARATOR + time;
    }

    /** 点赞数展示规则（m2149e）：&gt; 0 才显示，格式走 {@link DataModels#formatCount}。 */
    public static String diggLabel(Comment comment) {
        return comment.diggCount() > 0 ? DataModels.formatCount(comment.diggCount()) : null;
    }

    /** 评论区标题计数（cb0:532）：可空则不显示，非空 formatCount。 */
    public static String headerCount(Long total) {
        return total == null ? null : DataModels.formatCount(total);
    }

    /**
     * LazyColumn item key，逐字还原 m2151g（cb0:797-823）：
     * <ul>
     *   <li>cid 为 blank：{@code missing:<uid长度>:<uid>:<createTime>:<text长度>:<text>}</li>
     *   <li>否则：{@code id:<cid长度>:<cid>}</li>
     * </ul>
     * 结果 = {@code parentKey + ":" + base}，同 key 再次出现时由 {@code counter}
     * 追加出现序号（0 起，原 {@code map.put(key, n+1)}）。行为目的：楼中楼/重复评论
     * 在 LazyList 里的唯一稳定 key。父 key 见 {@link #TOP_PREFIX} / {@link #replyPrefix(String)}。
     *
     * @param counter 调用方持有的计数表（原实现是跨组合保留的 HashMap）
     */
    public static String lazyKey(String parentKey, Comment comment, Map<String, Integer> counter) {
        String base;
        if (comment.cid() == null || comment.cid().isBlank()) {
            String uid = comment.user() == null ? "" : comment.user().uid();
            String text = comment.text() == null ? "" : comment.text();
            base = "missing:" + (uid == null ? 0 : uid.length()) + ":" + uid
                    + ":" + comment.createTime() + ":" + text.length() + ":" + text;
        } else {
            String cid = comment.cid();
            base = "id:" + cid.length() + ":" + cid;
        }
        String key = parentKey + ":" + base;
        int n = counter.getOrDefault(key, 0);
        counter.put(key, n + 1);
        return key + ":" + n;
    }

    /** 顶层评论 key 前缀（cb0:396 {@code m2151g(map, comment, "comment")}）。 */
    public static final String TOP_PREFIX = "comment";

    /**
     * 回复 key 前缀（cb0:408 {@code "reply:" + 顶层key.length() + ":" + 顶层key}）——
     * 把长度编进前缀是原实现防碰撞的手段，原样保留。
     */
    public static String replyPrefix(String topKey) {
        return "reply:" + topKey.length() + ":" + topKey;
    }

    /** 展平后的列表项（原 db0：key / comment / isReply / index）。 */
    public record Item(String key, Comment comment, boolean isReply) {
    }

    /**
     * 评论列表展平，还原 m2148d（cb0:391-412）：
     * 顶层评论全部入列；仅当 {@code selectedKey} 等于该评论的 key
     * 且其 cid 不在两个排除集合中时，才把内嵌回复展开入列（{@code isReply=true}）。
     * {@code replyComments == null} 按空列表处理。
     *
     * @param exclude1 / exclude2 原实现的双 Set 排除判定（cb0:399
     *                              {@code !set.contains(cid) && !set3.contains(cid)}；
     *                              两集合的确切语义存疑——可能是已展开/已收起状态，
     *                              调用方按原集合传入即可）
     */
    public static java.util.List<Item> flatten(java.util.List<Comment> comments,
                                               String selectedKey,
                                               java.util.Set<String> exclude1,
                                               java.util.Set<String> exclude2,
                                               Map<String, Integer> counter) {
        java.util.List<Item> out = new java.util.ArrayList<>();
        if (comments == null) {
            return out;
        }
        for (Comment c : comments) {
            String key = lazyKey(TOP_PREFIX, c, counter);
            out.add(new Item(key, c, false));
            if (key.equals(selectedKey) && !exclude1.contains(c.cid()) && !exclude2.contains(c.cid())) {
                java.util.List<Comment> replies = c.replyComments();
                if (replies == null) {
                    replies = java.util.List.of();
                }
                String prefix = replyPrefix(key);
                for (Comment r : replies) {
                    out.add(new Item(lazyKey(prefix, r, counter), r, true));
                }
            }
        }
        return out;
    }

    /** 便捷重载：内部建临时计数表（适合单条查询/测试）。 */
    public static String lazyKey(String parentKey, Comment comment) {
        return lazyKey(parentKey, comment, new HashMap<>());
    }
}
