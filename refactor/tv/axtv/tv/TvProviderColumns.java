/*
 * 来源: src/app/p000/at3.java:461-502 (m1472b)、at3.java:29+ (m1471a)
 *       src/app/p000/mb5.java:55-71、g12.java:m4356k、eu5.java:104 (f5486r)
 * 原职责: TvContract.PreviewPrograms 的列名与按系统版本裁剪不支持列的规则
 * 重构说明: ContentValues → LinkedHashMap<String,Object>；裁剪列表与顺序逐字还原
 */
package axtv.tv;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** PreviewProgram / Channel 的列名常量与 SDK 兼容裁剪。 */
public final class TvProviderColumns {

    public static final String ID = "_id";
    public static final String PACKAGE_NAME = "package_name";
    public static final String CHANNEL_ID = "channel_id";
    public static final String WEIGHT = "weight";
    public static final String TYPE = "type";
    public static final String TITLE = "title";
    public static final String SHORT_DESCRIPTION = "short_description";
    public static final String POSTER_ART_URI = "poster_art_uri";
    public static final String POSTER_ART_ASPECT_RATIO = "poster_art_aspect_ratio";
    public static final String DURATION_MILLIS = "duration_millis";
    public static final String INTENT_URI = "intent_uri";
    public static final String INTERNAL_PROVIDER_ID = "internal_provider_id";
    public static final String DISPLAY_NAME = "display_name";
    public static final String DESCRIPTION = "description";
    public static final String APP_LINK_INTENT_URI = "app_link_intent_uri";
    public static final String INTERNAL_PROVIDER_DATA = "internal_provider_data";
    public static final String INTERNAL_PROVIDER_FLAG1 = "internal_provider_flag1";
    public static final String INTERNAL_PROVIDER_FLAG2 = "internal_provider_flag2";
    public static final String INTERNAL_PROVIDER_FLAG3 = "internal_provider_flag3";
    public static final String INTERNAL_PROVIDER_FLAG4 = "internal_provider_flag4";

    /** {@code at3.m1472b}: SDK &lt; 24 移除。 */
    public static final List<String> REMOVE_BELOW_24 = List.of("season_title");

    /** {@code at3.m1472b}: SDK &lt; 26 第一段移除。 */
    public static final List<String> REMOVE_BELOW_26_RATING = List.of(
            "review_rating_style", "review_rating");

    /** {@code at3.m1472b}: SDK &lt; 26 第二段移除（全部原列，顺序原样）。 */
    public static final List<String> REMOVE_BELOW_26 = List.of(
            "internal_provider_id", "preview_video_uri", "last_playback_position_millis",
            "duration_millis", "intent_uri", "transient", "type",
            "poster_art_aspect_ratio", "poster_thumbnail_aspect_ratio", "logo_uri",
            "availability", "starting_price", "offer_price", "release_date",
            "item_count", "live", "interaction_count", "author",
            // 原文 DownloadService.KEY_CONTENT_ID；media3 CmcdConfiguration.KEY_CONTENT_ID = "cid"
            "cid",
            "logo_content_description", "genre", "start_time_utc_millis",
            "end_time_utc_millis", "preview_audio_uri", "tv_series_item_type");

    /** {@code at3.m1472b}: 任何 SDK 都移除（该列只可读不可写）。 */
    public static final String ALWAYS_REMOVE = "browsable";

    /** {@code at3.m1472b}: SDK &lt; 26 第三段移除。 */
    public static final List<String> REMOVE_BELOW_26_CHANNEL = List.of(
            CHANNEL_ID, WEIGHT);

    private TvProviderColumns() {
    }

    /**
     * 精确还原 {@code at3.m1472b()}：复制一份 values，按 SDK 版本依次移除不支持的列。
     * 注意原实现是无条件移除 {@code browsable}，且 {@code channel_id}/{@code weight}
     * 在 SDK &lt; 26 时会被移除 —— 即低版本上写入的节目不会归属频道。
     */
    public static LinkedHashMap<String, Object> applySdkFilter(Map<String, Object> source, int sdkInt) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>(source);
        if (sdkInt < 24) {
            remove(values, REMOVE_BELOW_24);
        }
        if (sdkInt < 26) {
            remove(values, REMOVE_BELOW_26_RATING);
            remove(values, REMOVE_BELOW_26);
        }
        values.remove(ALWAYS_REMOVE);
        if (sdkInt < 26) {
            remove(values, REMOVE_BELOW_26_CHANNEL);
        }
        return values;
    }

    private static void remove(Map<String, Object> values, List<String> keys) {
        for (String key : keys) {
            values.remove(key);
        }
    }
}
