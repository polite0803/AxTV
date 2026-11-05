/*
 * 来源: src/app/p000/fv4.java (1525 行，互动按钮 + 播放器侧边信息组件)
 * 原职责: 本类只覆盖「互动按钮行」这一段 ——
 *         m4264a(42-162): 单个按钮单元（图标/计数/标签竖排，激活变色，大小双态）
 *         m4265b(162-261): 状态按钮行（点赞 218 行、收藏 229 行，文案随激活态切换）
 *         m4275l(613 附近): 静态统计行（591/592/610/611 调用：点赞/评论/收藏/分享，无状态切换）
 *         fv4 其余部分（合集信息卡 m4268e、作者信息行、即将播放卡 m4271h、播放菜单标签）
 *         与 lq2（播放器 UI）/eu5（详情页）耦合，归对应轮次覆盖，见 ui/NOTES.md 边界表。
 * 重构说明: 行为规格模式 —— 文案/计数/激活态/尺寸双态落成纯 Java；图标与色值归 Android 侧。
 */
package axtv.ui;

/** 互动按钮行（点赞 / 收藏 / 静态统计行）的行为规格。 */
public final class InteractionBarSpec {

    // ---- 文案（fv4:218/229 逐字） ----
    public static final String LABEL_LIKE = "点赞";
    public static final String LABEL_LIKED = "已点赞";
    public static final String LABEL_COLLECT = "收藏";
    public static final String LABEL_COLLECTED = "已收藏";
    public static final String LABEL_COMMENT = "评论";
    public static final String LABEL_SHARE = "分享";

    // ---- 尺寸双态（m4264a:70-82，compact = 竖屏详情/小卡，large = TV 大按钮） ----
    /** 按钮总高 dp：large 56 / compact 36。 */
    public static final float HEIGHT_LARGE_DP = 56.0f;
    public static final float HEIGHT_COMPACT_DP = 36.0f;
    /** 图标边长 dp：large 16 / compact 10。 */
    public static final float ICON_LARGE_DP = 16.0f;
    public static final float ICON_COMPACT_DP = 10.0f;
    /** 圆角 dp：large 12 / compact 8（C1315kp(radius, true)）。 */
    public static final float CORNER_LARGE_DP = 12.0f;
    public static final float CORNER_COMPACT_DP = 8.0f;
    /** 水平 padding dp：large 20 / compact 12。 */
    public static final float H_PAD_LARGE_DP = 20.0f;
    public static final float H_PAD_COMPACT_DP = 12.0f;

    private InteractionBarSpec() {
    }

    /** 单个状态按钮的展示规格。 */
    public record Button(String label, String count, boolean active) {
    }

    /**
     * 点赞按钮（fv4:218）：
     * 计数恒显示（{@code formatCount(diggCount)}，0 也显示）；
     * 激活 → 图标切换 + 文案「已点赞」+ 高亮色（k80.f11899d）。
     */
    public static Button like(boolean liked, long diggCount) {
        return new Button(liked ? LABEL_LIKED : LABEL_LIKE,
                axtv.model.DataModels.formatCount(diggCount), liked);
    }

    /** 收藏按钮（fv4:229），规则同点赞。 */
    public static Button collect(boolean collected, long collectCount) {
        return new Button(collected ? LABEL_COLLECTED : LABEL_COLLECT,
                axtv.model.DataModels.formatCount(collectCount), collected);
    }

    /**
     * 静态统计行（fv4:591/592/610/611，m4275l）：无激活态与图标切换，
     * 标签恒定、计数恒显示。顺序为 点赞 → 评论 → 收藏 → 分享。
     */
    public static Button[] statsRow(long diggCount, long commentCount,
                                    long collectCount, long shareCount) {
        return new Button[]{
                new Button(LABEL_LIKE, axtv.model.DataModels.formatCount(diggCount), false),
                new Button(LABEL_COMMENT, axtv.model.DataModels.formatCount(commentCount), false),
                new Button(LABEL_COLLECT, axtv.model.DataModels.formatCount(collectCount), false),
                new Button(LABEL_SHARE, axtv.model.DataModels.formatCount(shareCount), false),
        };
    }

    /** 尺寸双态取值（m4264a 的 {@code z2 ? a : b} 模式）。 */
    public static float bySize(boolean large, float largeValue, float compactValue) {
        return large ? largeValue : compactValue;
    }
}
