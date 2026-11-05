/*
 * 来源: src/app/p000/qg5.java (反编译自 com.github.axtv.dv 的 R8 混淆类)
 *   辅证: b50.java(分区分组模型), f50.java(精选频道仓储), m30/q30/r30/s30/x30/y30/o20/u30/
 *         C1439o1/C0385ao/a91/p30/o30/uc0/w30 lambda 类, h30(C0412be 相关, jadx 反编译失败)
 * 原职责: 精选频道页(分区内容列表) —— 分区状态机/分页/频道 Tab/播放页信息面板/重播倒计时;
 *         以及精选播放页 m9540m 的状态装配。像素绘制留给 Android Compose 侧。
 * 重构说明: 纯 Java 行为规格；文案逐字保留（含标点与空格）。
 */
package axtv.ui;

import java.util.ArrayList;
import java.util.List;

/** 精选频道 / 分区内容列表行为规格（qg5）。 */
public final class ChannelListSpec {

    // ===== 文案（逐字保留）=====
    public static final String TEXT_LOADING_FEATURED = "正在加载精选视频...";   // qg5:2142/2518/3403
    public static final String TEXT_SECTION_LOADING = "正在加载该分区视频...";  // qg5:3286
    public static final String TEXT_SECTION_LOAD_FAILED = "该分区加载失败";    // qg5:3183
    public static final String TEXT_SECTION_EMPTY = "该分区暂无内容";          // qg5:3189
    public static final String TEXT_FEATURED_CHANNEL_TITLE = "精选频道";       // qg5:3398
    public static final String TEXT_NO_VIDEOS = "暂无视频";                   // qg5:3411
    public static final String TEXT_NO_VIDEOS_HINT = "请检查网络或认证配置";    // qg5:3411
    public static final String TEXT_CONFIGURE_COOKIE = "请先配置Cookie";       // qg5:3416
    public static final String TEXT_RETRY = "重试";                           // a91:case3 / p30:case0 / o30:case1
    public static final String TEXT_SETTINGS = "设置";                        // p30:case0
    public static final String TEXT_GO_SETTINGS = "前往设置";                  // o30:case0
    public static final String TEXT_CONFIG_MANAGE = "配置管理";                // uc0:case0（图标 Filled.SwitchAccount）
    public static final String TEXT_APP_TITLE = "AxTV";                       // qg5:3396（图标旁标题）
    public static final String TEXT_FEATURED_FALLBACK = "精选";               // qg5:2777/2822/2898/2971/3019/3021/3097
    public static final String TEXT_VIDEO_COUNT_SUFFIX = " 个视频";            // qg5:2834/3029（pt1.m9212t = 前缀拼接）
    public static final String TEXT_REPLAY_TOAST = "即将自动重播当前视频";       // qg5:3464
    public static final String TEXT_SECTION_POS_PREFIX = "分区 ";              // qg5:3574
    public static final String TEXT_SECTION_POS_MID = " / ";                  // qg5:3574
    public static final String TEXT_SECTION_POS_HINT = " · ↑↓ 换分区";         // qg5:3574
    public static final String TEXT_AUTHOR_PREFIX = "@";                      // qg5:3576（hf1.m5131w("@", nickname)）
    public static final String TEXT_EPISODE_PREFIX = "第 ";                   // w30:122（视频墙集数 Tab）
    public static final String TEXT_EPISODE_SUFFIX = " 集";                   // w30:122

    // ===== 常量（数字均注明来源行号）=====
    /** 分组预取条数：当前分组起最多加载 3 个分组（q30 case0: 循环 i<3；case1: min(idx+3, size)） */
    public static final int PREFETCH_GROUP_COUNT = 3;
    /** 播放页加载更多 UI 触发阈值：页索引 >= size-5（y30 case0, C1439o1 case2 collect） */
    public static final int LOAD_MORE_UI_THRESHOLD = 5;
    /** 仓储层接受加载更多阈值：index < size-3 直接忽略（f50.m4016s:207） */
    public static final int LOAD_MORE_REPO_THRESHOLD = 3;
    /** 重播倒计时提示出现阈值 5000ms（qg5:3464） */
    public static final long REPLAY_TOAST_WINDOW_MS = 5000L;
    /** 重播倒计时提示强调阈值 2000ms（qg5:3464，on5.m8750r 第 4 参） */
    public static final long REPLAY_TOAST_STRONG_MS = 2000L;
    /** 分区间跳转点击去抖 300ms（qg5.m9560w:7426） */
    public static final long SECTION_JUMP_DEBOUNCE_MS = 300L;
    /** 简介行数：选中态 2 行 / 常规 3 行（qg5:3622，z2?2:3） */
    public static final int DESC_MAX_LINES_SELECTED = 2;
    public static final int DESC_MAX_LINES = 3;
    /** 简介最大宽度 460dp（qg5:3622 nq4.m8247p(width=460)） */
    public static final int DESC_MAX_WIDTH_DP = 460;
    /** 播放页信息面板动画标记 "infoAnimatedContent"（qg5:4695 等） */
    public static final String INFO_PANEL_ANIMATION_TAG = "infoAnimatedContent";
    /** 频道 chip 底/前景色 token（qg5:3686-3687） */
    public static final String COLOR_TOKEN_CHIP_BG = "featured-chip-bg";
    public static final String COLOR_TOKEN_CHIP_FG = "featured-chip-fg";
    /** 文本 Tab 底/前景色 token（qg5:4125-4126） */
    public static final String COLOR_TOKEN_TEXT_TAB_BG = "featured-text-tab-bg";
    public static final String COLOR_TOKEN_TEXT_TAB_FG = "featured-text-tab-fg";

    // ===== 分区分组模型（b50.java，字段名 f1763a..f1768f）=====
    public static final class ChannelGroup {
        /** 分区名（f1763a）：Tab 文本、定位键（q30 case0 按 f1764b 定位、f50.m4017t 按 f1764b 定位） */
        public final String name;
        /** 分区标题（f1764b）：播放页信息面板标题、仓储定位键 */
        public final String title;
        /** 分区视频（f1765c，Aweme 列表） */
        public final List<?> videos;
        /** 加载中（f1766d） */
        public final boolean loading;
        /** 错误信息（f1767e，非空即加载失败） */
        public final String error;
        /** 可再加载标记（f1768f；q30 case0 跳过 f1768f&&空 的分组，语义存疑，见 NOTES） */
        public final boolean loadable;

        public ChannelGroup(String name, String title, List<?> videos,
                            boolean loading, String error, boolean loadable) {
            this.name = name;
            this.title = title;
            this.videos = videos == null ? List.of() : videos;
            this.loading = loading;
            this.error = error;
            this.loadable = loadable;
        }

        /** b50.m1616a 拷贝语义（bit3=videos, bit4=loading, bit5=error, bit6=loadable） */
        public ChannelGroup withLoading(boolean newLoading) {
            return new ChannelGroup(name, title, videos, newLoading, error, loadable);
        }
    }

    // ===== 分区状态机（m9520c:3172-3234）=====
    public enum SectionState {
        /** b50 == null：仅渲染占位（p70.m8988o(null,...) qg5:3179/3232） */
        PLACEHOLDER,
        /** 空 && (loading || !loadable)：加载圈（qg5:3177-3179） */
        LOADING,
        /** 空 && error != null："该分区加载失败" + 重试按钮（qg5:3181-3183，重试 a91 case3 → C1296ka(hu2,b50,10) 重新拉取该分区） */
        ERROR,
        /** 空 && error == null："该分区暂无内容"（qg5:3186-3189，无按钮） */
        EMPTY,
        /** 非空：视频网格 + 分页（qg5:3191-3229） */
        CONTENT
    }

    /** m9520c:3174-3190 判定顺序即优先级（b50==null → LOADING → ERROR → EMPTY）。 */
    public static SectionState sectionState(ChannelGroup group) {
        if (group == null) {
            return SectionState.PLACEHOLDER;
        }
        if (group.videos.isEmpty()) {
            if (group.loading || !group.loadable) {
                return SectionState.LOADING;
            }
            return group.error != null ? SectionState.ERROR : SectionState.EMPTY;
        }
        return SectionState.CONTENT;
    }

    // ===== 精选频道页空态（m9524e:3395-3421）=====
    public enum FeaturedPageState {
        /** loading：文案 "正在加载精选视频..."（qg5:3401-3403） */
        LOADING,
        /** !hasCookie："请先配置Cookie" + 按钮 "前往设置"（qg5:3414-3416） */
        NO_COOKIE,
        /** 其余（hasCookie 但无内容）："暂无视频" + 副标题 "请检查网络或认证配置" + "重试"/"设置"（qg5:3408-3411） */
        NO_VIDEOS
    }

    /**
     * m9524e:3401-3418。loading = f5847g(页级加载态) || 任一分组 loading（m9516a:1886-1901 r12）；
     * hasCookie = !isEmpty(hu2.f8493d.m4200g())（m9516a:1902，jx4.m6866L = isNullOrEmpty 取反）。
     */
    public static FeaturedPageState featuredPageState(boolean loading, boolean hasCookie) {
        if (loading) {
            return FeaturedPageState.LOADING;
        }
        return hasCookie ? FeaturedPageState.NO_VIDEOS : FeaturedPageState.NO_COOKIE;
    }

    // ===== 可见分组过滤与加载 footer（m9516a:1762-1780 / 1698-1712 / 2140-2142）=====

    /** m9516a:1767：保留 !videos.isEmpty() || loading 的分组（Tab 与页签均基于过滤后列表）。 */
    public static List<ChannelGroup> visibleGroups(List<ChannelGroup> groups) {
        List<ChannelGroup> out = new ArrayList<>();
        for (ChannelGroup g : groups) {
            if (!g.videos.isEmpty() || g.loading) {
                out.add(g);
            }
        }
        return out;
    }

    /**
     * m9516a:1698-1712 z3（"正在加载精选视频..." footer 的显示条件，qg5:2140-2142）：
     * channelsLoaded(f5847g) 且所有分区的 videos 均为空（null 列表视为空）。
     */
    public static boolean shouldShowFooterLoading(boolean channelsLoaded, List<ChannelGroup> groups) {
        if (!channelsLoaded) {
            return false;
        }
        for (ChannelGroup g : groups) {
            if (!g.videos.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** m9516a:1894/1955/2026：任一分组 loading（用于页级 LOADING 判定的兜底分支）。 */
    public static boolean anyGroupLoading(List<ChannelGroup> groups) {
        for (ChannelGroup g : groups) {
            if (g.loading) {
                return true;
            }
        }
        return false;
    }

    // ===== 频道卡片（m9518b:2610-3136）=====

    /** qg5:2821-2824：标题 = 分区名，null 回退 "精选"。 */
    public static String groupTitle(ChannelGroup group) {
        if (group == null || group.name == null) {
            return TEXT_FEATURED_FALLBACK;
        }
        return group.name;
    }

    /** qg5:2826-2837：计数标签 "{size} 个视频"，仅当分区非空时渲染（空分区不渲染该行）。 */
    public static String videoCountText(ChannelGroup group) {
        if (group == null || group.videos.isEmpty()) {
            return null;
        }
        return group.videos.size() + TEXT_VIDEO_COUNT_SUFFIX;   // pt1.m9212t(size, " 个视频")
    }

    /** qg5:2680-2690：列数由布局枚举 ww5 的 ordinal 决定：0→2, 1→3, 2→4, 其他→5。 */
    public static int columnCount(int layoutModeOrdinal) {
        switch (layoutModeOrdinal) {
            case 0: return 2;
            case 1: return 3;
            case 2: return 4;
            default: return 5;
        }
    }

    /** qg5:2676/2737：z4 = 布局模式 == ww5.f24105I（紧凑/TV 模式）；z（另一种态）= cj1.f3037a == br3.f2435K。 */
    public static boolean isCompactLayout(int layoutModeOrdinal, int ww5CompactOrdinal) {
        return layoutModeOrdinal == ww5CompactOrdinal;
    }

    // ===== 频道 Tab（x30:51-80 / m9530h / m9534j / m9536k）=====

    /** x30:58/74：选中 = 当前索引相等。 */
    public static boolean isTabSelected(int index, int currentIndex) {
        return index == currentIndex;
    }

    /**
     * x30:56-80：布局标志 z2 决定 Tab 形态——true 用文本 Tab（m9536k，token featured-text-tab-bg/fg），
     * false 用 chip（m9530h，token featured-chip-bg/fg）。点击（u30）均回调 tm1.invoke(索引) 切换分区。
     */
    public static boolean useTextTab(boolean layoutFlag) {
        return layoutFlag;
    }

    // ===== 分页 / 仓储（f50:195-255 / y30 case0 / q30 case0/case1）=====

    /** UI 触发：y30 case0（C1439o1 case2 收集翻页流）：页索引 >= size-5 → 调 f50.m4016s(index, name)。 */
    public static boolean shouldRequestLoadMore(int lastPageIndex, int listSize) {
        return lastPageIndex >= listSize - LOAD_MORE_UI_THRESHOLD;
    }

    /** 仓储复核：f50.m4016s:207 —— index < size-3 或 loading 中 → 忽略；否则 m4017t(title)。 */
    public static boolean repoAcceptsLoadMore(int index, ChannelGroup group) {
        return group != null && index >= group.videos.size() - LOAD_MORE_REPO_THRESHOLD && !group.loading;
    }

    /** f50.m4018u:245-255：分区初载守卫 —— 索引在名称表范围内、分组存在、videos 空、非 loading。 */
    public static boolean canInitialLoad(ChannelGroup group) {
        return group != null && group.videos.isEmpty() && !group.loading;
    }

    /** q30 case1（m9518b 的预取）：从当前索引起加载 [cur, min(cur+3, total))。返回起始索引（无则 -1）。 */
    public static int prefetchStartIndex(int currentIndex, int total) {
        return currentIndex >= 0 && currentIndex < total ? currentIndex : -1;
    }

    /** q30 case1 的上界：min(cur+3, total)。 */
    public static int prefetchEndIndexExclusive(int currentIndex, int total) {
        return Math.min(currentIndex + PREFETCH_GROUP_COUNT, total);
    }

    /**
     * q30 case0（m9516a 头部回调，mw2.m7934i(name, groups, q30)）：按 title(f1764b) 定位分区索引，
     * 从该索引起对最多 3 个「可加载」分组（跳过 loadable && videos 空）调 f50.m4018u(索引)。
     * 返回实际触发加载的索引列表（找不到目标返回空表）。
     */
    public static List<Integer> locateAndPrefetch(String channelTitle, List<ChannelGroup> groups) {
        List<Integer> triggered = new ArrayList<>();
        if (channelTitle == null || groups == null) {
            return triggered;
        }
        int target = -1;
        for (int i = 0; i < groups.size(); i++) {
            ChannelGroup g = groups.get(i);
            if (channelTitle.equals(g.title)) {
                target = i;
                break;
            }
        }
        if (target < 0) {
            return triggered;
        }
        int loaded = 0;
        for (int i = target; i < groups.size() && loaded < PREFETCH_GROUP_COUNT; i++) {
            ChannelGroup g = groups.get(i);
            // q30 case0: 跳过 f1768f && videos 空 的分组
            if (g.loadable && g.videos.isEmpty()) {
                continue;
            }
            triggered.add(i);
            loaded++;
        }
        return triggered;
    }

    /** f50.m4017t:214-242 加载前置：置 loading=true（b50.m1616a copy，bit 39 → 仅改 loading）。 */
    public static ChannelGroup markLoading(ChannelGroup group) {
        return group.withLoading(true);
    }

    // ===== 自动推进 / 翻页副作用（s30 case0:93-100 / r30 case0:199-214）=====

    /**
     * s30 case0:99-100：z(开关) && z2(触达边缘) && !z3(未禁用) && !alreadyTriggered
     * → alreadyTriggered=true; currentIndex += 1。
     * z2(边缘判定)的原始语义存疑（m9516a:1831-1833 反编译失真），见 NOTES。
     */
    public static boolean shouldAutoAdvance(boolean enabled, boolean atEdge,
                                            boolean disabled, boolean alreadyTriggered) {
        return enabled && atEdge && !disabled && !alreadyTriggered;
    }

    /** r30 case0:199-214：页索引 > 0 且分组列表非空 → 遍历分组并推进 currentIndex（反编译失真，见 NOTES）。 */
    public static boolean shouldAdvanceOnPageChange(int pageIndex, int groupCount) {
        return pageIndex > 0 && groupCount > 0;
    }

    // ===== 重播倒计时（m9526f:3448-3474）=====

    /** qg5:3454-3463：剩余 = end - current（end<=0 → Long.MAX_VALUE；差值为负按 0 计）。 */
    public static long replayRemainingMs(long endMs, long currentMs) {
        if (endMs <= 0) {
            return Long.MAX_VALUE;
        }
        long remaining = endMs - currentMs;
        return Math.max(remaining, 0L);
    }

    /**
     * qg5:3464：0=不显示；1=普通（enabled && total>0 && remaining<=5000，文案 "即将自动重播当前视频"）；
     * 2=强调（remaining<=2000）。委托 on5.m8750r(..., so3.REPLAY, ...) 渲染。
     */
    public static int replayToastLevel(boolean enabled, long totalMs, long remainingMs) {
        if (enabled && totalMs > 0 && remainingMs <= REPLAY_TOAST_WINDOW_MS) {
            return remainingMs <= REPLAY_TOAST_STRONG_MS ? 2 : 1;
        }
        return 0;
    }

    // ===== 播放页信息面板（m9528g:3484-3645）=====

    /** qg5:3574：hf1.m5130v("分区 ", i+1, " / ", i2, " · ↑↓ 换分区")。 */
    public static String sectionPositionText(int index, int total) {
        return TEXT_SECTION_POS_PREFIX + (index + 1) + TEXT_SECTION_POS_MID + total + TEXT_SECTION_POS_HINT;
    }

    /** qg5:3576：hf1.m5131w("@", nickname)。 */
    public static String authorText(String nickname) {
        return TEXT_AUTHOR_PREFIX + nickname;
    }

    /** qg5:3607-3614：播放量仅在 playCount > 0 时渲染（点赞/评论恒渲染）。 */
    public static boolean showPlayCount(long playCount) {
        return playCount > 0;
    }

    /** qg5:3616-3626：简介为空（isNullOrEmpty）不渲染；非空 maxLines = 选中?2:3，宽 460dp。 */
    public static boolean showDescription(String desc) {
        return desc != null && !desc.isEmpty();
    }

    // ===== 精选播放页装配（m9540m:4247-5225）=====

    /** qg5:4459-4467：播放意图键 = awemeId ?: "" + ":" + tabIndex + ":" + scrollIndex。 */
    public static String playIntentKey(String awemeId, int tabIndex, int scrollIndex) {
        return (awemeId == null ? "" : awemeId) + ":" + tabIndex + ":" + scrollIndex;
    }

    /** qg5:4474-4477：封面取 cover.urlList 首项，空串视为 null。 */
    public static String resolveCoverUrl(List<String> coverUrlList) {
        String url = (coverUrlList == null || coverUrlList.isEmpty()) ? null : coverUrlList.get(0);
        return url == null || url.isEmpty() ? null : url;
    }

    /** qg5:4513-4528：按 awemeId 在列表中定位（找不到返回 -1）。 */
    public static int indexOfAweme(List<String> awemeIds, String awemeId) {
        if (awemeId == null) {
            return -1;
        }
        for (int i = 0; i < awemeIds.size(); i++) {
            if (awemeId.equals(awemeIds.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * m9468B:355-387（m9540m:4529 调用）：播放副作用参数组
     * {当前索引, 分区视频列表, 是否内容模式(r80.f18701a==t80.CONTENT), r80.f18702b, r80.f18703c}，
     * 实际执行体为 o40（去重/跳转逻辑在 o40 内，未展开）。
     */
    public static Object[] playSideEffectArgs(int index, List<?> videos, boolean contentMode,
                                              Object routeArgB, Object routeArgC) {
        return new Object[] {index, videos, contentMode, routeArgB, routeArgC};
    }

    // ===== 播放页交互辅助 =====

    /** m9560w:7421-7430：跳转去抖 300ms；索引合法（0<=i<size）才触发。 */
    public static boolean acceptsJump(long nowMs, long lastJumpMs, int index, int size) {
        return nowMs - lastJumpMs >= SECTION_JUMP_DEBOUNCE_MS && index >= 0 && index < size;
    }

    /** m9524e 空态页头部：图标 80dp（qg5:3396 nq4.m8242k(80.0f)）、圆角 20dp（l74.m7382a(20.0f)）。 */
    public static final int EMPTY_HEADER_ICON_SIZE_DP = 80;
    public static final int EMPTY_HEADER_ICON_CORNER_DP = 20;
    /** 标题 "精选频道" 字号 32（qg5:3398 oh4.m8585j(32)）。 */
    public static final int FEATURED_TITLE_SP = 32;

    private ChannelListSpec() {
    }
}
