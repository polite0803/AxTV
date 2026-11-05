/*
 * 来源: src/app/p000/mb5.java:24-38 (f13969c)、mb5.java:41-75、mb5.java:78-136
 *       src/app/p000/eu2.java:82/100/117/147、y30.java:303 (频道 key 字面量)
 *       src/app/p000/nb5.java:12、ob5.java:11、jb5.java:9-13、g12.java:m4356k
 * 原职责: Android TV "下一集/推荐频道"（Preview Channel）两个自有频道的标识与全部发布常量
 * 重构说明: 散落在 6 个混淆类里的字面量集中到一处，值逐字保留
 */
package axtv.tv;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 电视频道发布的全部常量。 */
public final class ChannelSpec {

    /** 两个自有频道 id，原值来自 {@code mb5.f13969c} 与 eu2/y30 的调用点。 */
    public static final String RECOMMEND = "axtv_recommend";
    public static final String WATCH_LATER = "axtv_watch_later";

    /** id → 中文显示名（{@code display_name}），原 {@code sv2.m10400E(str, f13969c)}。 */
    public static final Map<String, String> DISPLAY_NAMES;

    static {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put(RECOMMEND, "推荐");
        m.put(WATCH_LATER, "稍后再看");
        DISPLAY_NAMES = Map.copyOf(m);
    }

    /** TvProvider 的 authority 与两个 Content URI（nb5 / ob5）。 */
    public static final String TV_PROVIDER_AUTHORITY = "android.media.tv";
    public static final String CHANNEL_URI = "content://android.media.tv/channel";
    public static final String PREVIEW_PROGRAM_URI = "content://android.media.tv/preview_program";

    /** {@code jb5.m6599a} / {@code g73.m4569k} 等全部操作的最低 SDK。 */
    public static final int MIN_SDK = 26;

    /** 频道的 app 深链（{@code app_link_intent_uri}）与视频深链前缀（{@code intent_uri}）。 */
    public static final String APP_LINK_INTENT_URI = "axtv://douyin";
    public static final String VIDEO_DEEP_LINK_PREFIX = "axtv://douyin/video/";

    /** {@code g12.m4356k} 强制写入的频道类型。 */
    public static final String CHANNEL_TYPE = "TYPE_PREVIEW";

    /** 频道 logo：cacheDir 下文件名、位图边长、PNG 压缩质量（mb5.m7709b:101-123）。 */
    public static final String LOGO_FILE = "tv_channel_logo.png";
    public static final int LOGO_SIZE_PX = 96;
    public static final int LOGO_QUALITY = 100;

    /** 节目条数与权重：{@code take(30)}、{@code weight = max(1, 30 - index)}。 */
    public static final int MAX_PROGRAMS = 30;
    public static final int WEIGHT_BASE = 30;
    public static final int MIN_WEIGHT = 1;

    /** 标题：{@code take(60)}，空文案回退（mb5.m7708a:57-61）。 */
    public static final int TITLE_MAX_CHARS = 60;
    public static final String NO_TITLE = "（无标题）";

    /** {@code type} 固定 4；竖屏 {@code poster_art_aspect_ratio}=4，横屏=0（mb5.m7708a:56,65）。 */
    public static final int PROGRAM_TYPE = 4;
    public static final int ASPECT_RATIO_LANDSCAPE = 0;
    public static final int ASPECT_RATIO_PORTRAIT = 4;

    /** 查询频道时的 projection（{@code eu5.f5486r}，12 列，顺序原样）。 */
    public static final List<String> CHANNEL_PROJECTION = List.of(
            "_id", "package_name", "type", "display_name", "description",
            "app_link_intent_uri", "internal_provider_id", "internal_provider_data",
            "internal_provider_flag1", "internal_provider_flag2",
            "internal_provider_flag3", "internal_provider_flag4");

    /** 日志 TAG（原 {@code TvChannelPublisher} / {@code PreviewChannelHelper} / {@code PreviewChannel}）。 */
    public static final String TAG_PUBLISHER = "TvChannelPublisher";
    public static final String TAG_HELPER = "PreviewChannelHelper";
    public static final String TAG_CHANNEL = "PreviewChannel";

    private ChannelSpec() {
    }

    public static String displayName(String channelKey) {
        return DISPLAY_NAMES.getOrDefault(channelKey, channelKey);
    }

    public static boolean isKnownChannel(String channelKey) {
        return DISPLAY_NAMES.containsKey(channelKey);
    }

    public static String videoDeepLink(String awemeId) {
        return VIDEO_DEEP_LINK_PREFIX + awemeId;
    }
}
