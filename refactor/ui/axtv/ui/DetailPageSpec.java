/*
 * 来源: src/app/p000/eu5.java (11,653 行，R8 杂烩类；本类只取 UI 行为段)
 *   辅证: eg5.java(日期格式化，已由 DateLabels 覆盖)、he4.java(搜索结果 UI，
 *         已由 LiveBrowseSpec 覆盖)、axtv.model.LiveRoomUtils(观看人数文案)
 * 原职责: 视频详情页/搜索结果封面卡 + 无可播放视频空态页 + 直播分区页
 *         (分区切换/正在刷新直播间/选台面板) + 直播间卡片 + LIVE 红点 + 返回按钮
 * 重构说明: 纯 Java 行为规格；「像素怎么画」留给 Android 侧，
 *         文案逐字保留（含标点与空格），数字常量注明来源行号。
 *
 *eu5 分区归属（详见 refactor/ui/NOTES-eu5-he4.md）：
 *  m3818a 角标chip / m3820b 封面卡(990-3804) / m3822c Aweme卡装配(3913-5734) /
 *  m3824d 无播放空态页 / m3826e chip容器 / m3832h LIVE红点 / m3834i 直播分区页
 *  (6327-10215, 4 个重复变体) / m3836j 分区chip / m3838k 直播间卡片 /
 *  m3840l 文字返回钮 / m3842m 图标返回钮。其余方法为 R8 合并噪音
 *  (base64/字段算术/kotlinx.datetime Instant/AppCompat locales)，见 NOTES。
 */
package axtv.ui;

import axtv.model.Aweme;
import axtv.model.Cover;
import axtv.model.DataModels;
import axtv.model.LiveRoom;
import axtv.model.Video;

import java.util.List;
import java.util.Map;

/** 视频详情页 / 直播分区页行为规格（eu5 的 UI 段）。 */
public final class DetailPageSpec {

    private DetailPageSpec() {
    }

    // ===== 宽高比（m3822c:4044-4049；m5100r 同源缺省值见 LiveBrowseSpec）=====
    /** 竖屏封面比例（eu5:4046）。 */
    public static final float ASPECT_PORTRAIT = 0.5625f;
    /** 横屏封面比例（eu5:4048；m3820b:1243 参数缺省同值）。 */
    public static final float ASPECT_LANDSCAPE = 1.7777778f;

    // ===== 文案（逐字，来源行号见注释）=====
    /** 视频卡标题兜底（eu5:4128 等 8 处重复块）。 */
    public static final String TEXT_UNTITLED_VIDEO = "未命名视频";
    /** 无可播放视频空态页主文案（eu5:5876）。 */
    public static final String TEXT_NO_PLAYABLE = "没有可播放的视频";
    /** 空态页品牌图标与文字（eu5:5870/5872）。 */
    public static final String TEXT_BRAND = "AxTV";
    /** 直播分区页头部标题兜底（eu5:6648）。 */
    public static final String TEXT_LIVE_SECTION_FALLBACK = "直播分区";
    /** 分区页状态行：正在刷新（eu5:6652）。 */
    public static final String TEXT_REFRESHING_ROOMS = "正在刷新直播间";
    /** 分区页状态行：加载更多（eu5:6654）。 */
    public static final String TEXT_LOADING_MORE_ROOMS = "正在加载更多直播间";
    /** 分区页状态行：分区无数据/空列表（eu5:6658/6663）。 */
    public static final String TEXT_SECTION_EMPTY = "当前分区暂无直播";
    /** 分区页状态行：数量前缀（eu5:6660，"共 N 个直播间"，两端各一空格）。 */
    public static final String TEXT_ROOMS_COUNT_PREFIX = "共 ";
    public static final String TEXT_ROOMS_COUNT_SUFFIX = " 个直播间";
    /** 分区内容区空态（eu5:6843）。 */
    public static final String TEXT_GRID_EMPTY = "暂无直播";
    /** 选台面板标题（eu5:6969）。 */
    public static final String TEXT_SELECT_CHANNEL = "选台";
    /** 选台副标题拼接（eu5:6974："分区名 · 共 N 个"，N=当前分区直播间数）。 */
    public static final String TEXT_CHANNEL_COUNT_MID = " · 共 ";
    public static final String TEXT_CHANNEL_COUNT_SUFFIX = " 个";
    /** 紧凑模式侧栏标签（eu5:7249）。 */
    public static final String TEXT_PARTITION = "分区";
    /** 直播间卡片主播昵称兜底（eu5:10520，"频道 N"，N 从 1 起）。 */
    public static final String TEXT_CHANNEL_PREFIX = "频道 ";
    /** 直播间卡片节目名兜底（eu5:10534）。 */
    public static final String TEXT_NO_PROGRAM = "暂无节目";
    /** 观看人数行（eu5:10569）：正在播 → "正在看 · X 人"，否则 "X 人在看"。 */
    public static final String TEXT_WATCHING_PREFIX = "正在看 · ";
    public static final String TEXT_WATCHING_SUFFIX = " 人";
    public static final String TEXT_VIEWERS_SUFFIX = " 人在看";
    /** 详情空态页文字返回钮（eu5:10603/10661）。 */
    public static final String TEXT_BACK_LEVEL = "返回上一级";
    /** 图标返回钮 contentDescription（eu5:10735）。 */
    public static final String TEXT_BACK = "返回";
    /** meta 行分隔符（eu5:1576）。 */
    public static final String TEXT_DOT = "·";
    /** 加载更多 footer 的 item key（eu5:6877，i82.m5448M）。 */
    public static final String KEY_LOADING_MORE = "loading_more";

    // ===== 封面卡装配（m3822c → m3820b）=====

    /**
     * 封面卡的显示槽位模型（m3820b 的参数语义，行号对应 eu5:1320-1663 的可见条件）。
     *
     * <p>两种互斥的卡片形态（m3822c:4123-4162）：
     * <ul>
     *   <li>标题模式（showTitle=true）：显示 desc 标题 + 时长 meta + 点赞徽标，
     *       不显示作者行与日期；</li>
     *   <li>作者模式（showTitle=false, showAuthor/showDate）：显示作者头像+昵称行 +
     *       日期 meta + MM-dd 大字叠层（overlayText），不显示标题/时长/点赞。</li>
     * </ul>
     *
     * @param coverUrl       封面 URL（video.cover 首 URL，空则 dynamicCover 首 URL，eu5:4104-4107）
     * @param aspect         宽高比（见 {@link #cardAspect}）
     * @param title          卡片主标题（标题模式：desc→昵称→"未命名视频"；作者模式为 null）
     * @param authorName     作者昵称（作者模式，空昵称 → null，eu5:4137-4141）
     * @param authorAvatar   作者头像 URL（作者模式，eu5:4146）
     * @param metaDuration   时长 meta（标题模式："M:SS"；eu5:4112-4119）
     * @param metaDate       日期 meta（作者模式且显示日期：DateLabels.medium，eu5:4156）
     * @param overlayText    封面大字叠层（作者模式且显示日期：DateLabels.shortLabel "MM-dd"，eu5:4161）
     * @param badgeText      点赞徽标文本（标题模式：formatCount(diggCount)，eu5:4164）
     * @param cornerLabel    角标 chip 文本（m3818a 的 ml0.f14162a，可 null）
     */
    public record CoverCard(
            String coverUrl,
            float aspect,
            String title,
            String authorName,
            String authorAvatar,
            String metaDuration,
            String metaDate,
            String overlayText,
            String badgeText,
            String cornerLabel) {
    }

    /**
     * 卡片宽高比（m3822c:4044-4049）：无显式值时按视频方向取 9:16 / 16:9。
     *
     * @param width  视频宽；&le;0 视为未知
     * @param height 视频高；&le;0 视为未知
     */
    public static float cardAspect(int width, int height) {
        if (height > width) {
            return ASPECT_PORTRAIT;
        }
        return ASPECT_LANDSCAPE;
    }

    /**
     * 时长徽标文本（m3822c:4112-4119）：{@code duration>0 → "%d:%02d"（分:秒）}，否则 null。
     *
     * @param durationMillis 视频时长（毫秒）
     */
    public static String durationText(long durationMillis) {
        if (durationMillis <= 0) {
            return null;
        }
        long seconds = durationMillis / 1000;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * 视频卡标题（m3822c:4124-4129）：desc → 作者昵称 → "未命名视频"，逐级兜底。
     */
    public static String cardTitle(String desc, String authorNickname) {
        String title = desc;
        if (title == null || title.isBlank()) {
            title = authorNickname;
        }
        if (title == null || title.isBlank()) {
            return TEXT_UNTITLED_VIDEO;
        }
        return title;
    }

    /**
     * 封面 URL（m3822c:4104-4107）：静态封面首选，退回动态封面。
     */
    public static String coverUrl(Video video) {
        String url = firstUrl(video.cover());
        if (url == null) {
            url = firstUrl(video.dynamicCover());
        }
        return url == null ? "" : url;
    }

    private static String firstUrl(Cover cover) {
        List<String> urls = cover.urlList();
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        String first = urls.get(0);
        return first == null || first.isEmpty() ? null : first;
    }

    /**
     * Aweme → 封面卡槽位装配（m3822c 主体，eu5:3913-5734 去重后的唯一逻辑）。
     *
     * @param titleMode  显示标题/时长/点赞徽标（原 z4）
     * @param showAuthor 显示作者头像+昵称行（原 z5；仅 !titleMode 时生效，eu5:4134）
     * @param showDate   显示日期 meta 与 MM-dd 叠层（原 z10；仅 !titleMode 时生效，eu5:4153）
     */
    public static CoverCard assembleCoverCard(
            Aweme aweme, boolean titleMode, boolean showAuthor, boolean showDate) {
        boolean authorMode = !titleMode;
        boolean withAuthor = authorMode && showAuthor;
        boolean withDate = authorMode && showDate;
        return new CoverCard(
                coverUrl(aweme.video()),
                cardAspect(aweme.video().width(), aweme.video().height()),
                titleMode ? cardTitle(aweme.desc(), aweme.author().nickname()) : null,
                withAuthor ? blankToNull(aweme.author().nickname()) : null,
                withAuthor ? firstUrl(aweme.author().avatarThumb()) : null,
                titleMode ? durationText(aweme.video().duration()) : null,
                withDate ? DateLabels.medium(aweme.createTime()) : null,
                withDate ? DateLabels.shortLabel(aweme.createTime()) : null,
                titleMode ? DataModels.formatCount(aweme.statistics().diggCount()) : null,
                null);
    }

    private static String firstUrl(axtv.model.Avatar avatar) {
        List<String> urls = avatar.urlList();
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        String first = urls.get(0);
        return first == null || first.isEmpty() ? null : first;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    // ===== 直播分区页（m3834i，eu5:6327-10215）=====

    /** 分区（原 go2：f7328a=id、f7329b=名称）。 */
    public record Section(String id, String name) {
    }

    /** 分区数据（原 dn2：f4116a=分区、f4117b=直播间列表、f4118c=已全部加载）。 */
    public record SectionData(Section section, List<LiveRoom> rooms, boolean fullyLoaded) {

        public SectionData {
            rooms = rooms == null ? List.of() : List.copyOf(rooms);
        }

        public boolean isEmpty() {
            return rooms.isEmpty();
        }
    }

    /**
     * 分区页状态（原 op2：f16202e/f16204g/f16207j/f16209l/f16213p，eu5:6392-6396）。
     *
     * @param sections      分区列表（有序）
     * @param selectedIndex 选中分区下标
     * @param dataById      分区 id → 分区数据
     * @param currentRoom   正在播放的直播间（可为 null）
     * @param refreshing    是否正在刷新（状态行显示"正在刷新直播间"）
     */
    public record LiveSectionState(
            List<Section> sections,
            int selectedIndex,
            Map<String, SectionData> dataById,
            LiveRoom currentRoom,
            boolean refreshing) {

        /** 当前选中的分区；下标越界返回 null（eu5:6416 o70.m8484V0 语义）。 */
        public Section selectedSection() {
            if (selectedIndex < 0 || selectedIndex >= sections.size()) {
                return null;
            }
            return sections.get(selectedIndex);
        }

        /** 选中分区的数据；分区无 id 或无数据返回 null（eu5:6418-6419）。 */
        public SectionData selectedData() {
            Section section = selectedSection();
            if (section == null || section.id() == null) {
                return null;
            }
            return dataById.get(section.id());
        }

        /**
         * 是否正在加载更多（原 z5/z24，eu5:6449-6457）：分区已选中、列表非空且未全部加载。
         */
        public boolean loadingMore() {
            SectionData data = selectedData();
            return data != null && !data.isEmpty() && !data.fullyLoaded();
        }
    }

    /**
     * 房间解析 id（原 LiveRoom.getResolvedRoomId，src LiveRoom:319-331）：
     * roomId → idStr → enterRoomId → webRid，取第一个非空白者。
     * （axtv.model.LiveRoom record 暂无此派生属性，故在此提供。）
     */
    public static String resolvedRoomId(LiveRoom room) {
        String id = room.roomId();
        if (id == null || id.isBlank()) {
            id = room.idStr();
            if (id == null || id.isBlank()) {
                id = room.enterRoomId();
                if (id == null || id.isBlank()) {
                    return room.webRid();
                }
            }
        }
        return id;
    }

    /**
     * 焦点下标（eu5:6423-6442）：当前直播间在分区列表中的位置（按 resolvedRoomId 相等比较）；
     * 不在列表中返回 -1。
     */
    public static int focusIndex(SectionData data, LiveRoom currentRoom) {
        if (data == null || currentRoom == null) {
            return -1;
        }
        String currentId = resolvedRoomId(currentRoom);
        List<LiveRoom> rooms = data.rooms();
        for (int i = 0; i < rooms.size(); i++) {
            if (resolvedRoomId(rooms.get(i)).equals(currentId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 分区页头部标题（eu5:6648）：分区名；分区/名称缺失 → "直播分区"。
     */
    public static String sectionTitle(SectionData data) {
        if (data == null || data.section() == null || data.section().name() == null) {
            return TEXT_LIVE_SECTION_FALLBACK;
        }
        return data.section().name();
    }

    /**
     * 分区页状态行文案（eu5:6651-6663，逐分支）：
     * 正在刷新 → "正在刷新直播间"；加载更多 → "正在加载更多直播间"；
     * 无分区数据或空列表 → "当前分区暂无直播"；否则 → "共 N 个直播间"。
     */
    public static String sectionStatusText(boolean refreshing, boolean loadingMore, SectionData data) {
        if (refreshing) {
            return TEXT_REFRESHING_ROOMS;
        }
        if (loadingMore) {
            return TEXT_LOADING_MORE_ROOMS;
        }
        if (data == null || data.isEmpty()) {
            return TEXT_SECTION_EMPTY;
        }
        return TEXT_ROOMS_COUNT_PREFIX + data.rooms().size() + TEXT_ROOMS_COUNT_SUFFIX;
    }

    /**
     * 选台面板副标题（eu5:6971-6975）：状态优先（刷新/加载更多时），
     * 否则 "分区名 · 共 N 个"（N 为当前分区直播间数；分区数据缺失按 0）。
     */
    public static String selectChannelSubtitle(
            boolean showStatus, String statusText, SectionData data) {
        if (showStatus) {
            return statusText;
        }
        String name = sectionTitle(data);
        int count = data == null ? 0 : data.rooms().size();
        return name + TEXT_CHANNEL_COUNT_MID + count + TEXT_CHANNEL_COUNT_SUFFIX;
    }

    // ===== 直播间卡片（m3838k，eu5:10345-10588）=====

    /**
     * 直播间卡片的显示槽位（m3838k）。
     *
     * @param coverUrl    封面 URL（room.cover 首 URL，空串兜底，eu5:10409-10412）
     * @param ownerName   主播昵称（空 → "频道 N"，N=列表下标+1，eu5:10518-10521）
     * @param program     节目名（紧凑模式 z2=true 时为 null 不显示；空 → "暂无节目"，eu5:10525-10535）
     * @param viewerLine  人数行（见 {@link #viewerLine}）
     * @param compact     紧凑模式（封面宽 104dp，否则 112dp，eu5:10470；隐藏节目行）
     * @param showLiveDot 显示 LIVE 红点（原 z，eu5:10560）
     */
    public record LiveRoomCard(
            String coverUrl, String ownerName, String program,
            String viewerLine, boolean compact, boolean showLiveDot) {
    }

    /**
     * 直播间卡片的观看人数行（eu5:10569，观看人数文案出自 LiveRoomUtils.displayViewerCount）：
     * 当前正在播 → "正在看 · X 人"；否则 → "X 人在看"。
     */
    public static String viewerLine(boolean watching, String displayViewerCount) {
        if (watching) {
            return TEXT_WATCHING_PREFIX + displayViewerCount + TEXT_WATCHING_SUFFIX;
        }
        return displayViewerCount + TEXT_VIEWERS_SUFFIX;
    }

    /**
     * 直播间卡片主播名（eu5:10518-10521）：昵称空白 → "频道 N"（N=列表下标+1，从 1 起）。
     */
    public static String ownerName(String nickname, int index) {
        return nickname == null || nickname.isBlank()
                ? TEXT_CHANNEL_PREFIX + (index + 1)
                : nickname;
    }

    /**
     * 直播间卡片节目名（eu5:10525-10535）：紧凑模式（z2）不显示（返回 null）；
     * 否则标题空白 → "暂无节目"。
     */
    public static String programName(String title, boolean compact) {
        if (compact) {
            return null;
        }
        return title == null || title.isBlank() ? TEXT_NO_PROGRAM : title;
    }

    /** LIVE 红点参数（m3832h，eu5:6221-6271）：8dp 圆点，直播中 alpha 在 1↔0.35 间以 800ms 周期往复（动画键 "liveDot"/"liveDotAlpha"，eu5:6239）。 */
    public static final float LIVE_DOT_SIZE_DP = 8.0f;
    public static final float LIVE_DOT_ALPHA_MAX = 1.0f;
    public static final float LIVE_DOT_ALPHA_MIN = 0.35f;
    public static final int LIVE_DOT_PERIOD_MS = 800;

    // ===== 按钮 / chip =====

    /**
     * 分区 chip（m3836j，eu5:10241-10312）：文本为分区名；选中(z)用高亮底色/前景，
     * 未选中普通底色；紧凑(z2)时内边距 32dp，否则 44dp（eu5:10273）。
     * 点击 → rm1Var（单选）；长按/二级 → rm1Var2。
     */
    public static final int PARTITION_CHIP_PADDING_COMPACT_DP = 32;
    public static final int PARTITION_CHIP_PADDING_DP = 44;

    /** 直播间卡片封面宽度（eu5:10470）：紧凑 104dp，普通 112dp。 */
    public static final int ROOM_COVER_WIDTH_COMPACT_DP = 104;
    public static final int ROOM_COVER_WIDTH_DP = 112;

    /**
     * 空态页元素顺序（m3824d，eu5:5860-5880）：
     * AxTV 启动图（80dp 圆角 20dp）→ 16dp → "AxTV"（32sp）→ 24dp →
     * "没有可播放的视频" → 40dp → "返回上一级" 文字按钮。
     */
    public static final int EMPTY_LOGO_SIZE_DP = 80;
    public static final int EMPTY_LOGO_CORNER_DP = 20;
    public static final int EMPTY_TITLE_SP = 32;
    public static final List<String> EMPTY_PAGE_TEXTS =
            List.of(TEXT_BRAND, TEXT_NO_PLAYABLE, TEXT_BACK_LEVEL);
}
