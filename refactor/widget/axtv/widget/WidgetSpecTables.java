/*
 * 六个小组件的逐组件规格表（从 WidgetRenderSpec 拆出）。
 * 拆分原因: WidgetRenderSpec 超 800 行；规格表数据与分支判定/文案工具无耦合，
 *           WidgetRenderSpec.spec()/allSpecs() 继续作为唯一入口委托到本类。
 */
package axtv.widget;

import axtv.widget.WidgetRenderSpec.Branch;
import axtv.widget.WidgetRenderSpec.ContentSpec;
import axtv.widget.WidgetRenderSpec.ComponentSpec;
import axtv.widget.WidgetRenderSpec.Dimens;
import axtv.widget.WidgetRenderSpec.Icons;
import axtv.widget.WidgetRenderSpec.Screen;
import axtv.widget.WidgetRenderSpec.StatusSpec;
import axtv.widget.WidgetRenderSpec.Strings;
import axtv.widget.WidgetRenderSpec.Route;
import axtv.widget.WidgetRenderSpec.ClickRegion;
import axtv.widget.WidgetRenderSpec.Variant;
import axtv.widget.WidgetRenderSpec.WidgetId;

import java.util.List;
import java.util.Map;

/** 逐组件完整规格数据（状态页规格 + 六组件 branches/contents + 汇总表）。 */
final class WidgetSpecTables {

    private WidgetSpecTables() {
    }

    /** 取某组件的完整规格表（供 NOTES 逐行生成 / Android 侧驱动渲染）。 */
    public static ComponentSpec spec(WidgetId id) {
        return SPECS.get(id);
    }

    /** 全部六组件规格（顺序与 SPECS 一致）。 */
    public static List<ComponentSpec> allSpecs() {
        return List.of(continueSpec(), laterSpec(), mixSpec(), liveSpec(), searchSpec(),
                nowPlayingSpec());
    }


    // ================================================================ 共享状态页
    /** 共享「已锁定」状态页规格（WidgetRenderSpec.resolve 回退路径也用）。 */
    static StatusSpec lockedStatus() {
        return StatusSpec.of(Icons.LOCK, Strings.LOCKED_TITLE, Strings.LOCKED_SUB,
                Strings.LOCKED_BTN, Route.HOME, null, false);
    }

    /** 共享「引导」状态页规格（WidgetRenderSpec.resolve 回退路径也用）。 */
    static StatusSpec guideStatus(String text) {
        return StatusSpec.of(Icons.LOGIN, Strings.GUIDE_TITLE, text,
                Strings.GUIDE_BTN, Route.LOGIN, null, false);
    }

    /** 共享「加载中」状态页规格（WidgetRenderSpec.resolve 回退路径也用）。 */
    static StatusSpec loadingStatus() {
        return new StatusSpec(Icons.HISTORY, Strings.LOADING_TITLE, Strings.LOADING_SUB,
                null, Route.NONE, null, false, false);
    }

    /** 共享「错误」状态页规格（WidgetRenderSpec.resolve 回退路径也用）。 */
    static StatusSpec errorStatus(WidgetKind kind) {
        return StatusSpec.of(Icons.ERROR, Strings.ERROR_TITLE, Strings.ERROR_SUB,
                Strings.ERROR_BTN, Route.REFRESH, kind, true);
    }

    // ================================================================ 逐组件规格
    private static ComponentSpec continueSpec() {
        String c = "ContinueWidget.java";
        List<Branch> branches = List.of(
                new Branch("WidgetStore.INSTANCE.isLocked(fl4Var)", Screen.LOCKED, Variant.NONE,
                        lockedStatus(), "WidgetUiKt.WidgetLockedStatus", c + ":95"),
                new Branch("!fl4Var.m4214v()", Screen.GUIDE, Variant.NONE,
                        guideStatus(Strings.GUIDE_CONTINUE), "WidgetUiKt.WidgetGuideStatus", c + ":99"),
                new Branch("!z || snapshot.getState() == LOADING", Screen.LOADING, Variant.NONE,
                        loadingStatus(), "DesktopWidgetsKt.WidgetLoadingStatus", c + ":103"),
                new Branch("snapshot.getState() == ERROR", Screen.ERROR, Variant.NONE,
                        errorStatus(WidgetKind.HISTORY), "WidgetUiKt.WidgetErrorStatus", c + ":107"),
                new Branch("widgetVideo == null", Screen.EMPTY, Variant.NONE,
                        StatusSpec.of(Icons.INBOX, Strings.EMPTY_CONTINUE_TITLE,
                                Strings.EMPTY_CONTINUE_SUB, Strings.EMPTY_GO_HOME, Route.HOME, null, false),
                        "WidgetUiKt.WidgetStatus", c + ":111"),
                new Branch("r11.m9740b(v11.m11076b(size), 300.0f) < 0  // 宽 < 300",
                        Screen.CONTENT, Variant.SMALL, null, "DesktopWidgetsKt.ContinueSmall", c + ":115"),
                new Branch("else  // 宽 >= 300", Screen.CONTENT, Variant.LARGE, null,
                        "DesktopWidgetsKt.ContinueLarge", c + ":119")
        );
        List<ContentSpec> contents = List.of(
                new ContentSpec(Variant.SMALL, "DesktopWidgetsKt.ContinueSmall",
                        List.of("cover(bitmap)", "label:最近看过", "title", "author(avatar 16dp)", "duration"),
                        List.of(ClickRegion.route("封面", Route.PLAY_HISTORY, "DesktopWidgetsKt.java:256")),
                        List.of("WidgetCover 尺寸 100dp；顶部标签「最近看过」非空才显示（WidgetUiKt.java:1237-1245）",
                                "WidgetMeta(video, avatar, true)：author 恒显，duration 非空才显（:2976-2984）")),
                new ContentSpec(Variant.LARGE, "DesktopWidgetsKt.ContinueLarge",
                        List.of("cover(bitmap 99dp)", "label:最近看过", "title", "author(avatar 20dp)",
                                "duration", "label:更早看过", "earlierCovers(56dp)"),
                        List.of(ClickRegion.route("首条封面", Route.PLAY_HISTORY, "DesktopWidgetsKt.java:130"),
                                ClickRegion.route("更早看过封面(每条目)", Route.PLAY_HISTORY,
                                        "DesktopWidgetsKt.java:194")),
                        List.of("list 为空时隐藏「更早看过」分区（:154-162）",
                                "更早看过行按 list 顺序逐条 WidgetCover，条目间 8dp 间隔（:186-203）"))
        );
        return new ComponentSpec(WidgetId.CONTINUE, "ContinueWidget", "ContinueWidgetReceiver",
                WidgetKind.HISTORY, List.of(Dimens.SIZE_22, Dimens.SIZE_42),
                "8.0f 固定（ContinueWidget.java:82）", "WidgetThemeKt.widgetPalette(context, 首条封面)",
                Strings.GUIDE_CONTINUE, WidgetKind.HISTORY,
                StatusSpec.of(Icons.INBOX, Strings.EMPTY_CONTINUE_TITLE, Strings.EMPTY_CONTINUE_SUB,
                        Strings.EMPTY_GO_HOME, Route.HOME, null, false),
                lockedStatus(), loadingStatus(), branches, contents,
                "WidgetStore.history() → VideoListSnapshot.items（首条 + 其余）",
                List.of("sizeMode = [SIZE_22(156x192), SIZE_42(320x192)]（:28）",
                        "isLocked 优先级最高，先于历史开关/加载态",
                        "账户不匹配 z=false 时 provideGlance 会主动 refreshNow(HISTORY)（与 SearchWidget:155-158 同构）"));
    }

    private static ComponentSpec laterSpec() {
        String c = "WatchLaterWidget.java";
        List<Branch> branches = List.of(
                new Branch("WidgetStore.INSTANCE.isLocked(fl4Var)", Screen.LOCKED, Variant.NONE,
                        lockedStatus(), "WidgetUiKt.WidgetLockedStatus", c + ":95"),
                new Branch("!fl4Var.m4214v()", Screen.GUIDE, Variant.NONE,
                        guideStatus(Strings.GUIDE_LATER), "WidgetUiKt.WidgetGuideStatus", c + ":99"),
                new Branch("!z || snapshot.getState() == LOADING", Screen.LOADING, Variant.NONE,
                        loadingStatus(), "DesktopWidgetsKt.WidgetLoadingStatus", c + ":103"),
                new Branch("snapshot.getState() == ERROR", Screen.ERROR, Variant.NONE,
                        errorStatus(WidgetKind.LATER), "WidgetUiKt.WidgetErrorStatus", c + ":107"),
                new Branch("list.isEmpty()", Screen.EMPTY, Variant.NONE,
                        StatusSpec.of(Icons.INBOX, Strings.EMPTY_LATER_TITLE, Strings.EMPTY_LATER_SUB,
                                Strings.EMPTY_GO_HOME, Route.HOME, null, false),
                        "WidgetUiKt.WidgetStatus", c + ":111"),
                new Branch("r11.m9740b(v11.m11075a(size), 250.0f) < 0  // 高 < 250",
                        Screen.CONTENT, Variant.COMPACT, null, "DesktopWidgetsKt.LaterCompact", c + ":115"),
                new Branch("else  // 高 >= 250", Screen.CONTENT, Variant.EXPANDED, null,
                        "DesktopWidgetsKt.LaterExpanded", c + ":119")
        );
        List<ContentSpec> contents = List.of(
                new ContentSpec(Variant.COMPACT, "DesktopWidgetsKt.LaterCompact",
                        List.of("Header:稍后再看 + widget_ic_schedule + <n> 条", "covers(with likes overlay)"),
                        List.of(ClickRegion.route("每个封面", Route.PLAY_LATER, "DesktopWidgetsKt.java:426")),
                        List.of("Header 尾串 pt1.m9212t(i, \" 条\")，i = list.size()（:403）",
                                "封面行逐条 WidgetCover，条目间 8dp 间隔（:419-435）",
                                "WidgetSurface 圆角 0.0f（WatchLaterWidget.java:82）")),
                new ContentSpec(Variant.EXPANDED, "DesktopWidgetsKt.LaterExpanded",
                        List.of("Header:稍后再看 + <n> 条", "cover(140dp) + likes", "title",
                                "author + avatar(16dp)"),
                        List.of(ClickRegion.route("每个封面", Route.PLAY_LATER, "DesktopWidgetsKt.java:538")),
                        List.of("每条：WidgetCover → title → WidgetAvatar+author（:532-559）",
                                "条目间 8dp 间隔（:516-523）"))
        );
        return new ComponentSpec(WidgetId.WATCH_LATER, "WatchLaterWidget", "WatchLaterWidgetReceiver",
                WidgetKind.LATER, List.of(Dimens.SIZE_42, Dimens.SIZE_43),
                "0.0f 固定（WatchLaterWidget.java:82）", "WidgetThemeKt.widgetPalette(context, 首条封面)",
                Strings.GUIDE_LATER, WidgetKind.LATER,
                StatusSpec.of(Icons.INBOX, Strings.EMPTY_LATER_TITLE, Strings.EMPTY_LATER_SUB,
                        Strings.EMPTY_GO_HOME, Route.HOME, null, false),
                lockedStatus(), loadingStatus(), branches, contents,
                "WidgetStore.later() → VideoListSnapshot.items",
                List.of("sizeMode = [SIZE_42(320x192), SIZE_43(320x292)]（:28）",
                        "紧凑/展开按高度 250dp 切换：192→Compact，292→Expanded"));
    }

    private static ComponentSpec mixSpec() {
        String c = "MixWidget.java";
        List<Branch> branches = List.of(
                new Branch("WidgetStore.INSTANCE.isLocked(fl4Var)", Screen.LOCKED, Variant.NONE,
                        lockedStatus(), "WidgetUiKt.WidgetLockedStatus", c + ":102"),
                new Branch("!fl4Var.m4214v()", Screen.GUIDE, Variant.NONE,
                        guideStatus(Strings.GUIDE_MIX), "WidgetUiKt.WidgetGuideStatus", c + ":106"),
                new Branch("!z || mixSnapshot.getState() == LOADING", Screen.LOADING, Variant.NONE,
                        loadingStatus(), "DesktopWidgetsKt.WidgetLoadingStatus", c + ":110"),
                new Branch("mixSnapshot.getState() == ERROR", Screen.ERROR, Variant.NONE,
                        errorStatus(WidgetKind.MIX), "WidgetUiKt.WidgetErrorStatus", c + ":114"),
                new Branch("list.isEmpty()", Screen.EMPTY, Variant.NONE,
                        StatusSpec.of(Icons.INBOX, Strings.EMPTY_MIX_TITLE, Strings.EMPTY_MIX_SUB,
                                Strings.EMPTY_GO_HOME, Route.HOME, null, false),
                        "WidgetUiKt.WidgetStatus", c + ":118"),
                new Branch("z2 = r11.m9740b(v11.m11075a(size), 250.0f) < 0  // 高 < 250",
                        Screen.CONTENT, Variant.COMPACT, null, "DesktopWidgetsKt.MixCompact", c + ":122"),
                new Branch("else  // 高 >= 250", Screen.CONTENT, Variant.EXPANDED, null,
                        "DesktopWidgetsKt.MixExpanded", c + ":126")
        );
        List<ContentSpec> contents = List.of(
                new ContentSpec(Variant.COMPACT, "DesktopWidgetsKt.MixCompact",
                        List.of("Header:widget_ic_video_library + 合集追更", "cover(64dp)", "name",
                                "author · 共 N 集", "progress(E/T 或 第 E 集)", "按钮:继续第 E 集",
                                "按钮:后续选集(最多 3 个, '<n> 集')"),
                        List.of(ClickRegion.route("封面", Route.MIX_DETAIL, "DesktopWidgetsKt.java:866"),
                                ClickRegion.route("继续第 E 集", Route.PLAY_MIX, "DesktopWidgetsKt.java:926"),
                                ClickRegion.route("后续选集按钮 x<=3", Route.PLAY_MIX, "DesktopWidgetsKt.java:929")),
                        List.of("z2(高<250) 同时决定用哪套 WidgetPalette（MixWidget.java:71-72）",
                                "author 空白→隐藏；total>0 才显示「共 N 集」；两者用 \" · \" 连接（:881-892）",
                                "progress = episode/total（total>0）否则 0；文本 total>0 ? \"E / T\" : \"第 E 集\"（:905-911）",
                                "nextEpisodes 只保留 number > currentEpisode（WidgetStore.recordVideo:285-288），显示取前 3（:927）")),
                new ContentSpec(Variant.EXPANDED, "DesktopWidgetsKt.MixExpanded",
                        List.of("Header:合集追更 + widget_ic_video_library", "最多 3 个合集条目",
                                "每条 cover(52dp) + name + progress(第 E / T 集) + 播放图标按钮"),
                        List.of(ClickRegion.route("整行(72dp tinted)", Route.MIX_DETAIL, "DesktopWidgetsKt.java:983"),
                                ClickRegion.route("封面", Route.MIX_DETAIL, "DesktopWidgetsKt.java:1014"),
                                ClickRegion.route("播放按钮", Route.PLAY_MIX, "DesktopWidgetsKt.java:1016")),
                        List.of("取 list 前 3 条（:976），条目间 6dp 间隔（:984-989）",
                                "播放按钮 contentDescription = \"播放\" + name（:1016）",
                                "progress 文本 \"第 E\" + (total>0 ? \" / T\" : \"\") + \" 集\"（:1041）"))
        );
        return new ComponentSpec(WidgetId.MIX, "MixWidget", "MixWidgetReceiver",
                WidgetKind.MIX, List.of(Dimens.SIZE_42, Dimens.SIZE_43),
                "0.0f 固定（MixWidget.java:83）",
                "z2 ? widgetPalette : widgetPalette2（两套，按高<250 切换，MixWidget.java:71-72）",
                Strings.GUIDE_MIX, WidgetKind.MIX,
                StatusSpec.of(Icons.INBOX, Strings.EMPTY_MIX_TITLE, Strings.EMPTY_MIX_SUB,
                        Strings.EMPTY_GO_HOME, Route.HOME, null, false),
                lockedStatus(), loadingStatus(), branches, contents,
                "WidgetStore.mix() → MixSnapshot.items（含 nextEpisodes）",
                List.of("sizeMode = [SIZE_42(320x192), SIZE_43(320x292)]（:31）",
                        "唯一使用两套 WidgetPalette 的组件；紧凑态取 list 首条 + 首个 bitmap（:124）"));
    }

    private static ComponentSpec liveSpec() {
        String c = "LiveWidget.java";
        List<Branch> branches = List.of(
                new Branch("WidgetStore.INSTANCE.isLocked(fl4Var)", Screen.LOCKED, Variant.NONE,
                        lockedStatus(), "WidgetUiKt.WidgetLockedStatus", c + ":91"),
                new Branch("!fl4Var.m4214v()", Screen.GUIDE, Variant.NONE,
                        guideStatus(Strings.GUIDE_LIVE), "WidgetUiKt.WidgetGuideStatus", c + ":95"),
                new Branch("!z || liveSnapshot.getState() == LOADING", Screen.LOADING, Variant.NONE,
                        loadingStatus(), "DesktopWidgetsKt.WidgetLoadingStatus", c + ":99"),
                new Branch("liveSnapshot.getState() == ERROR", Screen.ERROR, Variant.NONE,
                        errorStatus(WidgetKind.LIVE), "WidgetUiKt.WidgetErrorStatus", c + ":103"),
                new Branch("list.isEmpty()", Screen.EMPTY, Variant.NONE,
                        StatusSpec.of(Icons.LIVE, Strings.EMPTY_LIVE_TITLE, Strings.EMPTY_LIVE_SUB,
                                Strings.EMPTY_OPEN_LIVE, Route.LIVE, null, false),
                        "WidgetUiKt.WidgetStatus", c + ":107"),
                new Branch("r11.m9740b(v11.m11075a(size), 130.0f) < 0  // 高 < 130",
                        Screen.CONTENT, Variant.COMPACT, null, "DesktopWidgetsKt.LiveCompact", c + ":111"),
                new Branch("else  // 高 >= 130", Screen.CONTENT, Variant.EXPANDED, null,
                        "DesktopWidgetsKt.LiveExpanded", c + ":115")
        );
        List<ContentSpec> contents = List.of(
                new ContentSpec(Variant.COMPACT, "DesktopWidgetsKt.LiveCompact",
                        List.of("徽章:widget_ic_live + <n> 人在播", "每个主播 avatar(48dp) + author"),
                        List.of(ClickRegion.route("徽章(72dp tinted)", Route.LIVE, "DesktopWidgetsKt.java:601"),
                                ClickRegion.route("每个主播行", Route.LIVE_ROOM, "DesktopWidgetsKt.java:610")),
                        List.of("徽章文字 pt1.m9212t(i, \" 人在播\")，i = items.size()（:625）",
                                "徽章底色 primaryContainer，圆角 20（:601）")),
                new ContentSpec(Variant.EXPANDED, "DesktopWidgetsKt.LiveExpanded",
                        List.of("Header:关注的直播 + widget_ic_live + <HH:mm> 更新",
                                "每条 avatar(36dp) + author + title + viewers + 在看"),
                        List.of(ClickRegion.route("每个主播行(42dp)", Route.LIVE_ROOM,
                                "DesktopWidgetsKt.java:704")),
                        List.of("Header 尾串 = widgetTime(updatedAt) + \" 更新\"；时间为空则隐藏（:695-699）",
                                "WidgetHeader 第 4 参 z=true → CookieTile 用 primaryContainer 配色（:699 / WidgetUiKt.java:1612）",
                                "每行左侧 author+title，右侧 viewers+\"在看\"（:763-786）"))
        );
        return new ComponentSpec(WidgetId.LIVE, "LiveWidget", "LiveWidgetReceiver",
                WidgetKind.LIVE, List.of(Dimens.SIZE_LIVE, Dimens.SIZE_42),
                "高<130 ? 10.0f : 12.0f（LiveWidget.java:78）",
                "WidgetThemeKt.widgetPalette(context, 首条封面)",
                Strings.GUIDE_LIVE, WidgetKind.LIVE,
                StatusSpec.of(Icons.LIVE, Strings.EMPTY_LIVE_TITLE, Strings.EMPTY_LIVE_SUB,
                        Strings.EMPTY_OPEN_LIVE, Route.LIVE, null, false),
                lockedStatus(), loadingStatus(), branches, contents,
                "WidgetStore.live() → LiveSnapshot.items + updatedAt",
                List.of("sizeMode = [DpSize(320x92), SIZE_42(320x192)]（:28）",
                        "紧凑分支 items.size() 传的是 liveSnapshot.getItems().size()（:113）",
                        "widgetTime(updatedAt) 用 SimpleDateFormat(\"HH:mm\", Locale.getDefault())（WidgetUiKt.java:4345-4352）"));
    }

    private static ComponentSpec searchSpec() {
        String c = "SearchWidget.java";
        List<Branch> branches = List.of(
                new Branch("WidgetStore.INSTANCE.isLocked(fl4Var)", Screen.LOCKED, Variant.NONE,
                        lockedStatus(), "WidgetUiKt.WidgetLockedStatus", c + ":96"),
                new Branch("!fl4Var.m4214v()", Screen.GUIDE, Variant.NONE,
                        guideStatus(Strings.GUIDE_SEARCH), "WidgetUiKt.WidgetGuideStatus", c + ":100"),
                new Branch("!z || searchSnapshot.getState() == LOADING", Screen.LOADING, Variant.NONE,
                        loadingStatus(), "DesktopWidgetsKt.WidgetLoadingStatus", c + ":104"),
                new Branch("searchSnapshot.getState() == ERROR", Screen.ERROR, Variant.NONE,
                        errorStatus(WidgetKind.SEARCH), "WidgetUiKt.WidgetErrorStatus", c + ":108"),
                new Branch("r11.m9740b(v11.m11075a(size), 130.0f) < 0  // 高 < 130（先于空态判定！）",
                        Screen.CONTENT, Variant.COMPACT, null, "DesktopWidgetsKt.SearchCompact", c + ":112"),
                new Branch("list.isEmpty()", Screen.EMPTY, Variant.NONE,
                        StatusSpec.of(Icons.SEARCH, Strings.EMPTY_SEARCH_TITLE, Strings.EMPTY_SEARCH_SUB,
                                Strings.EMPTY_OPEN_SEARCH, Route.SEARCH, null, false),
                        "WidgetUiKt.WidgetStatus", c + ":117"),
                new Branch("else", Screen.CONTENT, Variant.EXPANDED, null,
                        "DesktopWidgetsKt.SearchExpanded", c + ":121")
        );
        List<ContentSpec> contents = List.of(
                new ContentSpec(Variant.COMPACT, "DesktopWidgetsKt.SearchCompact",
                        List.of("widget_ic_search(24dp)", "大家都在搜", "首个热词 或 搜索视频、用户、直播",
                                "widget_ic_whatshot 按钮(打开热搜)"),
                        List.of(ClickRegion.route("文字区", Route.SEARCH, "DesktopWidgetsKt.java:1293"),
                                ClickRegion.route("热门按钮", Route.SEARCH, "DesktopWidgetsKt.java:1294")),
                        List.of("首个热词为 null 时回退 \"搜索视频、用户、直播\"（:1307-1310）",
                                "窄尺寸且列表为空时首个热词为 null → 仍走 SearchCompact(null)（分支顺序决定，:112 先于 :117）")),
                new ContentSpec(Variant.EXPANDED, "DesktopWidgetsKt.SearchExpanded",
                        List.of("搜索栏(52dp):widget_ic_search(22dp) + 搜索视频、用户、直播",
                                "两列热词：take(3) 与 drop(3).take(3)",
                                "每行:序号 + 热词 + 徽章(新/热/荐)"),
                        List.of(ClickRegion.route("搜索栏", Route.SEARCH, "DesktopWidgetsKt.java:1352"),
                                ClickRegion.route("每个热词行", Route.SEARCH_RESULT,
                                        "DesktopWidgetsKt.java:306 / :1421")),
                        List.of("provideGlance 只取前 6 条热词（SearchWidget.java:159 o70.m8500l1(items, 6)）",
                                "分列规则 p70.m8981k0(take(3), take(drop(3),3))（:1387），序号 = 列*3 + 行 + 1（:1421）",
                                "徽章：label=1→新(tertiaryContainer)，2→热(primaryContainer)，3→荐(secondaryContainer)，其余无（:298-305）",
                                "序号 <=3 用 primary 色且 WidgetNumberFont，其余用 onSurfaceVariant（:328）"))
        );
        return new ComponentSpec(WidgetId.SEARCH, "SearchWidget", "SearchWidgetReceiver",
                WidgetKind.SEARCH, List.of(Dimens.SIZE_SEARCH, Dimens.SIZE_42),
                "8.0f 固定（SearchWidget.java:83）",
                "WidgetThemeKt.widgetPalette$default(context, null, ...) —— 不传封面（:167）",
                Strings.GUIDE_SEARCH, WidgetKind.SEARCH,
                StatusSpec.of(Icons.SEARCH, Strings.EMPTY_SEARCH_TITLE, Strings.EMPTY_SEARCH_SUB,
                        Strings.EMPTY_OPEN_SEARCH, Route.SEARCH, null, false),
                lockedStatus(), loadingStatus(), branches, contents,
                "WidgetStore.search() → SearchSnapshot.items（take 6）",
                List.of("sizeMode = [DpSize(320x64), SIZE_42(320x192)]（:40）",
                        "provideGlance 完整反编译（:131-199）：z = j42.m6486c(accountId, 当前账户)；"
                                + "!z || LOADING → WidgetRefreshScheduler.refreshNow(context, SEARCH)（:155-158）",
                        "list = (z && READY) ? take(items,6) : emptyList（:159）",
                        "紧凑态分支排在空态之前（与其它 4 个组件不同），是唯一会先判尺寸再判空态的组件"));
    }

    private static ComponentSpec nowPlayingSpec() {
        String c = "NowPlayingWidget.java";
        List<Branch> branches = List.of(
                new Branch("WidgetStore.INSTANCE.isLocked(fl4Var)", Screen.LOCKED, Variant.NONE,
                        lockedStatus(), "WidgetUiKt.WidgetLockedStatus", c + ":93"),
                new Branch("widgetPlayback == null", Screen.IDLE, Variant.NONE, null,
                        "DesktopWidgetsKt.NowPlayingIdle", c + ":97"),
                new Branch("r11.m9740b(v11.m11076b(size), 480.0f) >= 0  // 宽 >= 480",
                        Screen.CONTENT, Variant.CONTENT_WIDE, null,
                        "DesktopWidgetsKt.NowPlayingContent(wide=true)", c + ":101"),
                new Branch("else  // 宽 < 480", Screen.CONTENT, Variant.CONTENT_COMPACT, null,
                        "DesktopWidgetsKt.NowPlayingContent(wide=false)", c + ":105")
        );
        List<ContentSpec> contents = List.of(
                new ContentSpec(Variant.NONE, "DesktopWidgetsKt.NowPlayingIdle",
                        List.of("CookieTile(widget_ic_play, 56dp, COOKIE9)", "没有在播放",
                                "点继续看，从观看历史第一条开始",
                                "按钮:继续看(52dp)", "按钮:打开推荐(52dp)"),
                        List.of(ClickRegion.route("继续看", Route.HISTORY_LIST,
                                        "ComposableSingletons$DesktopWidgetsKt.java:31-33"),
                                ClickRegion.route("打开推荐", Route.HOME,
                                        "ComposableSingletons$DesktopWidgetsKt.java:35")),
                        List.of("CookieTile 配色 getCurrent().primaryContainer / onPrimaryContainer（:1229）",
                                "底部按钮行由 ComposableSingletons$DesktopWidgetsKt.lambda$720070246 提供（NowPlayingIdle:1216）",
                                "继续看 → WidgetRoutes.history() = profile/history；打开推荐 → home（:31-35）",
                                "两按钮间 8dp 间隔，高度 52dp")),
                new ContentSpec(Variant.CONTENT_WIDE, "DesktopWidgetsKt.NowPlayingContent(true)",
                        List.of("cover(224x126)", "title", "author", "状态:正在播放/已暂停 · source",
                                "按钮:上一条(76dp) / 暂停|继续(76dp) / 下一条(76dp)"),
                        List.of(ClickRegion.route("封面", Route.HOME, "DesktopWidgetsKt.java:1119"),
                                ClickRegion.media("上一条", WidgetActions.KEYCODE_MEDIA_PREVIOUS,
                                        "DesktopWidgetsKt.java:1173"),
                                ClickRegion.media("暂停/继续", WidgetActions.KEYCODE_MEDIA_PLAY_PAUSE,
                                        "DesktopWidgetsKt.java:1175"),
                                ClickRegion.media("下一条", WidgetActions.KEYCODE_MEDIA_NEXT,
                                        "DesktopWidgetsKt.java:1177")),
                        List.of("封面尺寸 224x126（:1119），对应 WidgetImages.load 224x126x16（NowPlayingWidget.java:284）",
                                "状态图标 isPlaying ? widget_ic_play : widget_ic_pause（:1149）",
                                "文案 = (isPlaying ? \"正在播放\" : \"已暂停\") + \" · \" + source（:1155）",
                                "按钮文案 isPlaying ? \"暂停\" : \"继续\"（:1175）")),
                new ContentSpec(Variant.CONTENT_COMPACT, "DesktopWidgetsKt.NowPlayingContent(false)",
                        List.of("cover(90x90)", "title", "author", "状态:正在播放/已暂停 · source",
                                "按钮:上一条(52dp) / 暂停|继续(52dp) / 下一条(52dp)"),
                        List.of(ClickRegion.route("封面", Route.HOME, "DesktopWidgetsKt.java:1119"),
                                ClickRegion.media("上一条", WidgetActions.KEYCODE_MEDIA_PREVIOUS,
                                        "DesktopWidgetsKt.java:1173"),
                                ClickRegion.media("暂停/继续", WidgetActions.KEYCODE_MEDIA_PLAY_PAUSE,
                                        "DesktopWidgetsKt.java:1175"),
                                ClickRegion.media("下一条", WidgetActions.KEYCODE_MEDIA_NEXT,
                                        "DesktopWidgetsKt.java:1177")),
                        List.of("封面尺寸 90x90（:1119），对应 WidgetImages.load 90x90x16（NowPlayingWidget.java:198/:320）",
                                "间隔 z?20:14 / z?12:8（:1098, :1174, :1176）"))
        );
        return new ComponentSpec(WidgetId.NOW_PLAYING, "NowPlayingWidget", "NowPlayingWidgetReceiver",
                null, List.of(Dimens.SIZE_42, Dimens.SIZE_CAR),
                "宽>=480 ? 16.0f : 12.0f（NowPlayingWidget.java:80）",
                "WidgetThemeKt.widgetPalette(context, playback?.coverUrl)（:263）",
                null, null, null, lockedStatus(), loadingStatus(), branches, contents,
                "WidgetPlaybackStore.getState().value（纯内存，不持久化）",
                List.of("sizeMode = [SIZE_42(320x192), SIZE_CAR(536x260)]（:37）",
                        "无 WidgetKind：不参与周期刷新，由播放状态驱动",
                        "provideGlance 完整反编译（:119-347）：playback!=null 时并行加载两张封面 224x126x16 与 90x90x16",
                        "NowPlayingWidgetReceiver 不继承 BaseWidgetReceiver 的 kind 生命周期"));
    }

    private static final java.util.Map<WidgetId, ComponentSpec> SPECS = java.util.Map.of(
            WidgetId.CONTINUE, continueSpec(),
            WidgetId.WATCH_LATER, laterSpec(),
            WidgetId.MIX, mixSpec(),
            WidgetId.LIVE, liveSpec(),
            WidgetId.SEARCH, searchSpec(),
            WidgetId.NOW_PLAYING, nowPlayingSpec()
    );

}
