/*
 * 来源: src/app/com/github/mytv/p004dv/widget/
 *       ContinueWidget.java / WatchLaterWidget.java / MixWidget.java /
 *       LiveWidget.java / SearchWidget.java / NowPlayingWidget.java （六个 provideGlance 分支链）
 *       DesktopWidgetsKt.java (1457) —— Compact/Expanded/Small/Large/Idle/Content 全部组件实现
 *       WidgetUiKt.java (4353) —— WidgetStatus/Error/Guide/Locked/Header/Cover/Meta/Progress/Theme/Surface
 * 原职责: Glance/Compose 渲染代码无法用纯 Java 绘制，但其承载的行为规格必须完整保留
 * 重构说明: 把「显示哪些字段、什么条件下隐藏、点了做什么、多大尺寸、什么条件下切布局」
 *           全部落成可执行的纯 Java 模型：ComponentSpec（逐组件规格表）+ resolve（分支判定）+ Route/ClickRegion（动作表）。
 *           仅「像素怎么画」交给 Android 侧（见 NOTES.md 待迁移）。
 */
package axtv.widget;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 小组件 UI 行为规格（可执行模型，不依赖 Android SDK）。 */
public final class WidgetRenderSpec {

    private WidgetRenderSpec() {
    }

    // ================================================================ 尺寸 / 阈值 / 圆角常量
    /** 全部来自 DesktopWidgetsKt.java:84-87 与六个 Widget 的 sizeMode 构造。 */
    public static final class Dimens {
        private Dimens() {
        }

        /** DpSize(widthDp, heightDp)；vt0.m11298a(f,f2) 中 f=宽(高32位) f2=高(低32位)。 */
        public static final DpSize SIZE_22 = new DpSize(156.0f, 192.0f);
        public static final DpSize SIZE_42 = new DpSize(320.0f, 192.0f);
        public static final DpSize SIZE_43 = new DpSize(320.0f, 292.0f);
        public static final DpSize SIZE_CAR = new DpSize(536.0f, 260.0f);
        /** LiveWidget.java:28 vt0.m11298a(320.0f, 92.0f)。 */
        public static final DpSize SIZE_LIVE = new DpSize(320.0f, 92.0f);
        /** SearchWidget.java:40 vt0.m11298a(320.0f, 64.0f)。 */
        public static final DpSize SIZE_SEARCH = new DpSize(320.0f, 64.0f);

        // 布局切换阈值（r11.m9740b(v11.m11075a/11076b(size), X) < 0）
        /** m11076b=宽；ContinueWidget.java:115 宽 < 300 → ContinueSmall。 */
        public static final float CONTINUE_SMALL_WIDTH = 300.0f;
        /** m11075a=高；WatchLaterWidget.java:115 高 < 250 → LaterCompact。 */
        public static final float LATER_COMPACT_HEIGHT = 250.0f;
        /** MixWidget.java:71 高 < 250 → MixCompact（且切换调色板）。 */
        public static final float MIX_COMPACT_HEIGHT = 250.0f;
        /** LiveWidget.java:111 高 < 130 → LiveCompact。 */
        public static final float LIVE_COMPACT_HEIGHT = 130.0f;
        /** SearchWidget.java:112 高 < 130 → SearchCompact。 */
        public static final float SEARCH_COMPACT_HEIGHT = 130.0f;
        /** NowPlayingWidget.java:101 宽 >= 480 → NowPlayingContent(wide=true)。 */
        public static final float NOWPLAYING_WIDE_WIDTH = 480.0f;
        /** WidgetUiKt.java:3175 高 < 140 → WidgetStatus 紧凑(竖排)布局。 */
        public static final float STATUS_COMPACT_HEIGHT = 140.0f;

        // WidgetSurface 圆角（m12611WidgetSurfaceorJrPs 第一参）
        public static final float CORNER_CONTINUE = 8.0f;
        public static final float CORNER_SEARCH = 8.0f;
        public static final float CORNER_FLAT = 0.0f;
        /** LiveWidget.java:78 高<130 ? 10 : 12。 */
        public static final float CORNER_LIVE_SMALL = 10.0f;
        public static final float CORNER_LIVE_BIG = 12.0f;
        /** NowPlayingWidget.java:80 宽>=480 ? 16 : 12。 */
        public static final float CORNER_NOW_WIDE = 16.0f;
        public static final float CORNER_NOW_SMALL = 12.0f;
        /** WidgetUiKt.java:3649 Surface 默认圆角（mask bit1）。 */
        public static final float CORNER_SURFACE_DEFAULT = 12.0f;
        /** Android 12+ system_app_widget_background_radius 上限（:3653-3659）。 */
        public static final float SYSTEM_CORNER_MAX = 28.0f;

        // 主要 WidgetCover / WidgetAvatar / tintedBackground 尺寸（dp）
        public static final float COVER_CONTINUE_LARGE = 99.0f;
        public static final float COVER_CONTINUE_SMALL = 100.0f;
        public static final float COVER_CONTINUE_EARLIER = 56.0f;
        public static final float COVER_LATER_EXPANDED = 140.0f;
        public static final float COVER_MIX_COMPACT = 64.0f;
        public static final float COVER_MIX_EXPANDED = 52.0f;
        public static final float COVER_NOW_WIDE_W = 224.0f;
        public static final float COVER_NOW_WIDE_H = 126.0f;
        public static final float COVER_NOW_COMPACT = 90.0f;
        public static final float AVATAR_META_SMALL = 16.0f;
        public static final float AVATAR_META_BIG = 20.0f;
        public static final float AVATAR_LATER = 16.0f;
        public static final float AVATAR_LIVE_COMPACT = 48.0f;
        public static final float AVATAR_LIVE_EXPANDED = 36.0f;
        public static final float TILE_STATUS = 44.0f;
        public static final float TILE_HEADER = 28.0f;
        public static final float TILE_NOW_IDLE = 56.0f;
        public static final float BADGE_LIVE_COMPACT = 72.0f;
        public static final float BAR_SEARCH_EXPANDED = 52.0f;
        public static final float ROW_LIVE_EXPANDED = 42.0f;
        public static final float ROW_MIX_EXPANDED = 72.0f;
        public static final float ROW_HOTWORD = 36.0f;
        public static final float BUTTON_NOW_WIDE = 76.0f;
        public static final float BUTTON_NOW_SMALL = 52.0f;
        public static final float BUTTON_MIX_CONTINUE = 48.0f;
        public static final float BUTTON_MIX_EPISODE = 46.0f;
        public static final float BUTTON_MIX_PLAY = 40.0f;
        /** WidgetUiKt.java:3654 SDK_INT>=31 才读系统圆角。 */
        public static final int SYSTEM_CORNER_MIN_SDK = 31;
    }

    /** 全部 UI 文案（源码为硬编码中文，非 R.string；逐条对应各分支实参）。 */
    public static final class Strings {
        private Strings() {
        }

        // 状态页
        public static final String LOCKED_TITLE = "配置已上锁";
        public static final String LOCKED_SUB = "设了 PIN 的配置不在桌面显示内容";
        public static final String LOCKED_BTN = "打开应用";
        public static final String GUIDE_TITLE = "登录后显示";
        public static final String GUIDE_BTN = "去登录";
        public static final String LOADING_TITLE = "正在加载";
        public static final String LOADING_SUB = "数据加载后会自动更新";
        public static final String ERROR_TITLE = "没能刷新";
        public static final String ERROR_SUB = "网络不可用，稍后自动重试";
        public static final String ERROR_BTN = "重试";
        // 引导文案（各 Widget 不同）
        public static final String GUIDE_CONTINUE = "继续看要用你的观看历史";
        public static final String GUIDE_LATER = "稍后再看要用你的列表";
        public static final String GUIDE_MIX = "合集追更要用你的观看历史";
        public static final String GUIDE_LIVE = "登录后显示关注的主播";
        public static final String GUIDE_SEARCH = "搜索和热搜需要登录";
        // 空态
        public static final String EMPTY_CONTINUE_TITLE = "还没有观看历史";
        public static final String EMPTY_CONTINUE_SUB = "看过的视频会出现在这里";
        public static final String EMPTY_LATER_TITLE = "稍后再看是空的";
        public static final String EMPTY_LATER_SUB = "加入稍后再看的视频会出现在这里";
        public static final String EMPTY_MIX_TITLE = "没有合集追更";
        public static final String EMPTY_MIX_SUB = "看过合集里的视频后会出现在这里";
        public static final String EMPTY_LIVE_TITLE = "关注的主播没在直播";
        public static final String EMPTY_LIVE_SUB = "开播后会在这里显示";
        public static final String EMPTY_SEARCH_TITLE = "热搜暂时为空";
        public static final String EMPTY_SEARCH_SUB = "仍然可以打开搜索页输入";
        public static final String EMPTY_GO_HOME = "去推荐看看";
        public static final String EMPTY_OPEN_LIVE = "打开直播";
        public static final String EMPTY_OPEN_SEARCH = "打开搜索";
        // 内容态
        public static final String RECENT = "最近看过";
        public static final String EARLIER = "更早看过";
        public static final String HEADER_LATER = "稍后再看";
        public static final String HEADER_MIX = "合集追更";
        public static final String HEADER_LIVE = "关注的直播";
        public static final String COUNT_SUFFIX = " 条";
        public static final String LIVE_COUNT_SUFFIX = " 人在播";
        public static final String UPDATED_SUFFIX = " 更新";
        public static final String WATCHING = "在看";
        public static final String TOTAL_PREFIX = "共 ";
        public static final String TOTAL_SUFFIX = " 集";
        public static final String EPISODE_PREFIX = "第 ";
        public static final String EPISODE_SUFFIX = " 集";
        public static final String CONTINUE_EPISODE = "继续第 ";
        public static final String SEPARATOR_DOT = " · ";
        public static final String PLAY = "播放";
        public static final String SEARCH_HINT = "搜索视频、用户、直播";
        public static final String EVERYONE_SEARCH = "大家都在搜";
        public static final String OPEN_HOT = "打开热搜";
        public static final String NOT_PLAYING = "没有在播放";
        public static final String NOT_PLAYING_SUB = "点继续看，从观看历史第一条开始";
        // NowPlayingIdle 底部两个按钮（ComposableSingletons$DesktopWidgetsKt.java:31-35）
        public static final String IDLE_CONTINUE = "继续看";
        public static final String IDLE_OPEN_RECOMMEND = "打开推荐";
        public static final String PLAYING = "正在播放";
        public static final String PAUSED = "已暂停";
        public static final String PAUSE_BTN = "暂停";
        public static final String RESUME_BTN = "继续";
        public static final String PREV_BTN = "上一条";
        public static final String NEXT_BTN = "下一条";
        // 热词徽章
        public static final String BADGE_NEW = "新";
        public static final String BADGE_HOT = "热";
        public static final String BADGE_RECOMMEND = "荐";
        // 数据兜底文案（WidgetDataKt）
        public static final String FALLBACK_TITLE = "无标题视频";
        public static final String FALLBACK_LIVE_TITLE = "正在直播";
        public static final String FALLBACK_AUTHOR = "主播";
        public static final String FALLBACK_VIEWERS = "0";
    }

    /** 全部 R.drawable 资源名（源码以 R.drawable.* 引用）。 */
    public static final class Icons {
        private Icons() {
        }

        public static final String INBOX = "R.drawable.widget_ic_inbox";
        public static final String ERROR = "R.drawable.widget_ic_error";
        public static final String LOGIN = "R.drawable.widget_ic_login";
        public static final String LOCK = "R.drawable.widget_ic_lock";
        public static final String HISTORY = "R.drawable.widget_ic_history";
        public static final String SCHEDULE = "R.drawable.widget_ic_schedule";
        public static final String LIVE = "R.drawable.widget_ic_live";
        public static final String SEARCH = "R.drawable.widget_ic_search";
        public static final String VIDEO_LIBRARY = "R.drawable.widget_ic_video_library";
        public static final String PLAY = "R.drawable.widget_ic_play";
        public static final String PAUSE = "R.drawable.widget_ic_pause";
        public static final String SKIP_PREVIOUS = "R.drawable.widget_ic_skip_previous";
        public static final String SKIP_NEXT = "R.drawable.widget_ic_skip_next";
        public static final String WHATS_HOT = "R.drawable.widget_ic_whatshot";
        public static final String FAVORITE = "R.drawable.widget_ic_favorite";
        // tintedBackground 的 shape drawable（WidgetUiKt.java:4331-4341）
        public static final String SHAPE_4 = "R.drawable.widget_shape_4";
        public static final String SHAPE_8 = "R.drawable.widget_shape_8";
        public static final String SHAPE_12 = "R.drawable.widget_shape_12";
        public static final String SHAPE_20 = "R.drawable.widget_shape_20";
        public static final String SHAPE_28 = "R.drawable.widget_shape_28";
        public static final String SHAPE_PILL = "R.drawable.widget_shape_pill";

        /** tintedBackground(cornerInt) 的圆角→drawable 映射（:4327-4342）。 */
        public static String shapeFor(int corner) {
            switch (corner) {
                case 4: return SHAPE_4;
                case 8: return SHAPE_8;
                case 12: return SHAPE_12;
                case 20: return SHAPE_20;
                case 28: return SHAPE_28;
                default: return SHAPE_PILL;
            }
        }
    }

    // ================================================================ 枚举与基础记录
    public enum WidgetId { CONTINUE, WATCH_LATER, MIX, LIVE, SEARCH, NOW_PLAYING }

    public enum Screen { LOCKED, GUIDE, LOADING, ERROR, EMPTY, CONTENT, IDLE }

    public enum Variant {
        NONE, SMALL, LARGE, COMPACT, EXPANDED, CONTENT_WIDE, CONTENT_COMPACT
    }

    /** DpSize（vt0.m11298a 打包前的宽高）。 */
    public record DpSize(float widthDp, float heightDp) {
    }

    /** 点击目标路由模板：给定实参即可构造真实 WidgetAction。 */
    public enum Route {
        HOME, LIVE, SEARCH, LOGIN,
        HISTORY_LIST, LATER_LIST,
        LIVE_ROOM, MIX_DETAIL, PLAY_HISTORY, PLAY_LATER, PLAY_MIX, SEARCH_RESULT,
        MEDIA_KEY, REFRESH, NONE;

        /** 该路由需要的字符串实参个数。 */
        public int arity() {
            switch (this) {
                case LIVE_ROOM: case MIX_DETAIL: case PLAY_HISTORY: case PLAY_LATER:
                case SEARCH_RESULT: return 1;
                case PLAY_MIX: return 2;
                default: return 0;
            }
        }
    }

    /** 一个可点击区域：label + 路由模板 + 附加参数（媒体键 keyCode / 刷新 kind）+ 来源行号。 */
    public record ClickRegion(String label, Route route, int keyCode, WidgetKind refreshKind,
                              String source) {
        public static ClickRegion route(String label, Route r, String source) {
            return new ClickRegion(label, r, -1, null, source);
        }

        public static ClickRegion media(String label, int keyCode, String source) {
            return new ClickRegion(label, Route.MEDIA_KEY, keyCode, null, source);
        }

        public static ClickRegion refresh(String label, WidgetKind kind, String source) {
            return new ClickRegion(label, Route.REFRESH, -1, kind, source);
        }

        /** 用真实实参物化出可执行的 WidgetAction（args 按 Route.arity() 提供）。 */
        public WidgetAction materialize(String... args) {
            switch (route) {
                case HOME: return WidgetAction.openRoute(WidgetRoutes.home());
                case LIVE: return WidgetAction.openRoute(WidgetRoutes.live());
                case SEARCH: return WidgetAction.openRoute(WidgetRoutes.search());
                case LOGIN: return WidgetAction.openRoute(WidgetRoutes.login());
                case HISTORY_LIST: return WidgetAction.openRoute(WidgetRoutes.history());
                case LATER_LIST: return WidgetAction.openRoute(WidgetRoutes.later());
                case LIVE_ROOM: return WidgetAction.openRoute(WidgetRoutes.liveRoom(arg(args, 0)));
                case MIX_DETAIL: return WidgetAction.openRoute(WidgetRoutes.mixDetail(arg(args, 0)));
                case PLAY_HISTORY: return WidgetAction.openRoute(WidgetRoutes.playHistory(arg(args, 0)));
                case PLAY_LATER: return WidgetAction.openRoute(WidgetRoutes.playLater(arg(args, 0)));
                case PLAY_MIX: return WidgetAction.openRoute(WidgetRoutes.playMix(arg(args, 0), arg(args, 1)));
                case SEARCH_RESULT: return WidgetAction.openRoute(WidgetRoutes.searchResult(arg(args, 0)));
                case MEDIA_KEY: return WidgetAction.mediaKey(keyCode);
                case REFRESH: return WidgetAction.refresh(refreshKind);
                case NONE:
                default: return null;
            }
        }

        private static String arg(String[] a, int i) {
            return a != null && i < a.length && a[i] != null ? a[i] : "";
        }
    }

    /** WidgetStatus 的六元组（WidgetUiKt.java:3075 签名）。 */
    public record StatusSpec(String icon, String title, String subtitle, String buttonText,
                             Route buttonRoute, WidgetKind refreshKind, boolean errorTint,
                             boolean showButton) {
        public static StatusSpec of(String icon, String title, String sub, String btn,
                                    Route route, WidgetKind refreshKind, boolean errorTint) {
            return new StatusSpec(icon, title, sub, btn, route, refreshKind, errorTint, btn != null);
        }
    }

    /** 一个分支：条件（源码表达式原文）→ 屏幕状态 / 布局变体 / 状态页 / 组件函数。 */
    public record Branch(String condition, Screen screen, Variant variant, StatusSpec status,
                         String componentFn, String source) {
    }

    /** 内容态规格：显示字段 + 点击区域表 + 条件可见性说明。 */
    public record ContentSpec(Variant variant, String componentFn, List<String> displayFields,
                              List<ClickRegion> clicks, List<String> conditionNotes) {
    }

    /** 逐组件完整规格表。 */
    public record ComponentSpec(WidgetId id, String glanceWidget, String receiver, WidgetKind kind,
                                List<DpSize> sizeModes, String cornerRule, String paletteRule,
                                String guideText, WidgetKind errorKind, StatusSpec emptyStatus,
                                StatusSpec lockedStatus, StatusSpec loadingStatus,
                                List<Branch> branches, List<ContentSpec> contents,
                                String dataSource, List<String> notes) {
    }

    // ================================================================ 输入 / 输出
    /** 一次渲染的输入（由 Android 侧 provideGlance 从 Store/Settings/PlaybackStore 填充）。 */
    public record ViewInput(
            boolean locked,
            boolean historyEnabled,     // fl4.m4214v()
            boolean accountMatches,     // z = j42.m6486c(snapshot.accountId, 当前账户)
            WidgetLoadState state,
            int itemCount,
            boolean firstItemNull,      // ContinueWidget 的 widgetVideo == null
            boolean playbackNull,       // NowPlayingWidget 的 widgetPlayback == null
            boolean isPlaying,
            float widthDp,
            float heightDp,
            long updatedAt,
            String source               // NowPlaying 的 playback.source
    ) {
        public static ViewInput ofDefault() {
            return new ViewInput(false, true, true, WidgetLoadState.READY, 1, false, false,
                    false, 320.0f, 192.0f, 0L, "");
        }
    }

    /** 分支判定结果（可执行的「UI 该显示什么」）。 */
    public record Resolution(WidgetId id, DpSize size, float surfaceCornerDp, Screen screen,
                             Variant variant, Branch branch, StatusSpec status, String componentFn,
                             List<ClickRegion> clicks, List<String> displayFields,
                             String timeText, String statusCompactLayout) {
    }

    // ================================================================ 逐组件规格（见 WidgetSpecTables）
    /** 取某组件的完整规格表（供 NOTES 逐行生成 / Android 侧驱动渲染）。 */
    public static ComponentSpec spec(WidgetId id) {
        return WidgetSpecTables.spec(id);
    }

    public static List<ComponentSpec> allSpecs() {
        return WidgetSpecTables.allSpecs();
    }


    // ================================================================ 分支判定（可执行）
    /**
     * 按源码分支优先级依次判定，返回该尺寸/状态下应显示的屏幕、组件函数、点击区域与显示字段。
     * 这是「行为规格」的可执行核心；Android 侧 provideGlance 只需把 Store/Settings 填入 ViewInput 并消费 Resolution。
     */
    public static Resolution resolve(WidgetId id, ViewInput in) {
        ComponentSpec s = spec(id);
        DpSize size = new DpSize(in.widthDp, in.heightDp);
        float corner = cornerDp(id, in);

        boolean locked = in.locked;
        boolean guide = !in.historyEnabled && id != WidgetId.NOW_PLAYING;
        boolean loading = !in.accountMatches || in.state == WidgetLoadState.LOADING;
        boolean error = in.state == WidgetLoadState.ERROR;
        boolean empty = in.itemCount == 0 || in.firstItemNull;
        boolean playbackNull = in.playbackNull;
        boolean compact;
        switch (id) {
            case CONTINUE: compact = in.widthDp < Dimens.CONTINUE_SMALL_WIDTH; break;
            case WATCH_LATER: compact = in.heightDp < Dimens.LATER_COMPACT_HEIGHT; break;
            case MIX: compact = in.heightDp < Dimens.MIX_COMPACT_HEIGHT; break;
            case LIVE: compact = in.heightDp < Dimens.LIVE_COMPACT_HEIGHT; break;
            case SEARCH: compact = in.heightDp < Dimens.SEARCH_COMPACT_HEIGHT; break;
            case NOW_PLAYING: compact = in.widthDp < Dimens.NOWPLAYING_WIDE_WIDTH; break;
            default: compact = true;
        }

        Branch matched = null;
        for (Branch b : s.branches()) {
            if (matches(b, id, locked, guide, loading, error, empty, playbackNull, compact)) {
                matched = b;
                break;
            }
        }
        if (matched == null) {
            matched = s.branches().get(s.branches().size() - 1);
        }

        StatusSpec status = matched.status();
        if (status == null) {
            switch (matched.screen()) {
                case LOCKED: status = s.lockedStatus(); break;
                case GUIDE: status = WidgetSpecTables.guideStatus(s.guideText()); break;
                case LOADING: status = WidgetSpecTables.loadingStatus(); break;
                case ERROR: status = WidgetSpecTables.errorStatus(s.errorKind()); break;
                case EMPTY: status = s.emptyStatus(); break;
                default: break;
            }
        }

        List<ClickRegion> clicks = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        if (matched.screen() == Screen.CONTENT || matched.screen() == Screen.IDLE) {
            for (ContentSpec c : s.contents()) {
                if (c.variant() == matched.variant()
                        || (matched.screen() == Screen.IDLE && c.variant() == Variant.NONE)) {
                    clicks.addAll(c.clicks());
                    fields.addAll(c.displayFields());
                    break;
                }
            }
        } else if (status != null && status.showButton()) {
            clicks.add(new ClickRegion(status.buttonText(), status.buttonRoute(), -1,
                    status.refreshKind(), "WidgetUiKt.WidgetStatus"));
        }

        if (matched.screen() == Screen.LOCKED) {
            fields.addAll(List.of(status.icon(), status.title(), status.subtitle(), status.buttonText()));
        } else if (status != null) {
            fields.addAll(List.of(status.icon(), status.title(), status.subtitle()));
            if (status.showButton()) {
                fields.add(status.buttonText());
            }
        }

        String timeText = "";
        if (id == WidgetId.LIVE && matched.variant() == Variant.EXPANDED) {
            String t = widgetTime(in.updatedAt);
            timeText = t.isEmpty() ? "" : t + Strings.UPDATED_SUFFIX;
        }

        String statusLayout = (status != null)
                ? (in.heightDp < Dimens.STATUS_COMPACT_HEIGHT ? "竖排(Column)" : "横排(Row)")
                : "";
        return new Resolution(id, size, corner, matched.screen(), matched.variant(), matched,
                status, matched.componentFn(), List.copyOf(clicks), List.copyOf(fields),
                timeText, statusLayout);
    }

    private static boolean matches(Branch b, WidgetId id, boolean locked, boolean guide,
                                   boolean loading, boolean error, boolean empty,
                                   boolean playbackNull, boolean compact) {
        switch (b.screen()) {
            case LOCKED: return locked;
            case GUIDE: return guide;
            case LOADING: return loading;
            case ERROR: return error;
            case EMPTY: return empty;
            case IDLE: return playbackNull;
            case CONTENT:
                if (id == WidgetId.NOW_PLAYING) {
                    return b.variant() == Variant.CONTENT_WIDE ? !compact : compact;
                }
                // SearchWidget：紧凑分支在空态之前，故 empty 为真且 compact 时仍命中紧凑分支
                if (id == WidgetId.SEARCH && b.variant() == Variant.COMPACT) {
                    return compact;
                }
                return !empty && (b.variant() == Variant.COMPACT
                        || b.variant() == Variant.SMALL ? compact : !compact);
            default: return false;
        }
    }

    /** 按组件计算 WidgetSurface 圆角（dp）。 */
    public static float cornerDp(WidgetId id, ViewInput in) {
        switch (id) {
            case CONTINUE: return Dimens.CORNER_CONTINUE;
            case SEARCH: return Dimens.CORNER_SEARCH;
            case WATCH_LATER:
            case MIX: return Dimens.CORNER_FLAT;
            case LIVE:
                return in.heightDp < Dimens.LIVE_COMPACT_HEIGHT
                        ? Dimens.CORNER_LIVE_SMALL : Dimens.CORNER_LIVE_BIG;
            case NOW_PLAYING:
                return in.widthDp >= Dimens.NOWPLAYING_WIDE_WIDTH
                        ? Dimens.CORNER_NOW_WIDE : Dimens.CORNER_NOW_SMALL;
            default: return Dimens.CORNER_SURFACE_DEFAULT;
        }
    }

    // ================================================================ 文案 / 格式化工具
    /** WidgetUiKt.widgetTime（:4345-4352）：updatedAt<=0 → ""，否则 SimpleDateFormat("HH:mm", Locale.getDefault())。 */
    public static String widgetTime(long millis) {
        if (millis <= 0) {
            return "";
        }
        return DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    /** pt1.m9212t(n, suffix)：数字拼接后缀，如 "6 条" / "3 人在播"。 */
    public static String countText(int n, String suffix) {
        return n + suffix;
    }

    /** DesktopWidgetsKt.java:885 author + " · " + "共 N 集"（跳过空白/空项）。 */
    public static String mixSubtitle(String author, int total) {
        List<String> parts = new ArrayList<>();
        if (author != null && !author.isBlank()) {
            parts.add(author);
        }
        if (total > 0) {
            parts.add(Strings.TOTAL_PREFIX + total + Strings.TOTAL_SUFFIX);
        }
        return String.join(Strings.SEPARATOR_DOT, parts);
    }

    /** MixCompact:905-911 进度与文案。 */
    public static String mixProgressText(int episode, int total) {
        return total > 0 ? episode + " / " + total
                : Strings.EPISODE_PREFIX + episode + Strings.EPISODE_SUFFIX;
    }

    /** MixExpanded:1041 进度文案。 */
    public static String mixExpandedProgressText(int episode, int total) {
        return Strings.EPISODE_PREFIX + episode
                + (total > 0 ? " / " + total : "") + Strings.EPISODE_SUFFIX;
    }

    /** WidgetProgress 的进度比例（:905 / :1041，coerceIn(0..1) 由 Android 侧 LinearProgressIndicator 处理）。 */
    public static float mixProgress(int episode, int total) {
        return total > 0 ? (float) episode / total : 0.0f;
    }

    /** HotWordRow:298-305 徽章映射（label → 文案 + 背景/前景令牌）。 */
    public record HotWordBadge(String text, String bgToken, String fgToken) {
    }

    public static HotWordBadge hotWordBadge(int label) {
        switch (label) {
            case 1: return new HotWordBadge(Strings.BADGE_NEW, "tertiaryContainer", "onTertiaryContainer");
            case 2: return new HotWordBadge(Strings.BADGE_HOT, "primaryContainer", "onPrimaryContainer");
            case 3: return new HotWordBadge(Strings.BADGE_RECOMMEND, "secondaryContainer",
                    "onSecondaryContainer");
            default: return null;
        }
    }

    /** NowPlayingContent:1149-1155 状态图标与文案。 */
    public static String nowPlayingStatusIcon(boolean isPlaying) {
        return isPlaying ? Icons.PLAY : Icons.PAUSE;
    }

    public static String nowPlayingStatusText(boolean isPlaying, String source) {
        return (isPlaying ? Strings.PLAYING : Strings.PAUSED)
                + Strings.SEPARATOR_DOT + (source == null ? "" : source);
    }

    public static String nowPlayingToggleText(boolean isPlaying) {
        return isPlaying ? Strings.PAUSE_BTN : Strings.RESUME_BTN;
    }

    /** SearchExpanded:1387 两列分栏：左 take(3)，右 drop(3).take(3)。 */
    public static List<List<String>> searchColumns(List<String> words) {
        List<String> left = words.size() > 3 ? new ArrayList<>(words.subList(0, 3))
                : new ArrayList<>(words);
        List<String> right = words.size() > 3
                ? new ArrayList<>(words.subList(3, Math.min(6, words.size()))) : List.of();
        return List.of(List.copyOf(left), List.copyOf(right));
    }

    /** SearchWidget.java:159 取前 6 条热词。 */
    public static int MAX_HOT_WORDS = 6;
    /** MixExpanded:976 取前 3 条合集；MixCompact:927 取前 3 个后续选集。 */
    public static int MAX_MIX_ITEMS = 3;
    public static int MAX_NEXT_EPISODES = 3;
}
