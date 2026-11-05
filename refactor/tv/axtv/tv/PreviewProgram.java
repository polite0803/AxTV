/*
 * 来源: src/app/p000/mb5.java:41-75 (m7708a)、src/app/p000/at3.java:29+ (m1471a) / 461+ (m1472b)
 * 原职责: 一条 PreviewProgram 的全部业务字段（原 ContentValues）
 * 重构说明: ContentValues → record + toValues()/fromValues()；写入顺序与列名逐字保留
 */
package axtv.tv;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一条"下一个观看"节目。
 *
 * @param rowId                数据库 {@code _id}；新建时为 null（原插入结果由 {@code ContentUris.parseId} 得到）
 * @param channelId            所属频道 id
 * @param type                 固定 4（{@link ChannelSpec#PROGRAM_TYPE}）
 * @param title                作品文案，截断 60 字符，空则「（无标题）」
 * @param shortDescription     "@作者昵称"，昵称为空时为空串
 * @param posterArtUri         三级封面回退后的第一张图
 * @param posterArtAspectRatio 竖屏 4 / 横屏 0
 * @param durationMillis       视频时长（毫秒）
 * @param intentUri            {@code axtv://douyin/video/<awemeId>}
 * @param internalProviderId   作品 id，也是同步去重键
 * @param weight               {@code max(1, 30 - index)}，越靠前越大
 */
public record PreviewProgram(
        Long rowId,
        long channelId,
        int type,
        String title,
        String shortDescription,
        String posterArtUri,
        int posterArtAspectRatio,
        int durationMillis,
        String intentUri,
        String internalProviderId,
        int weight) {

    /** 按 {@code mb5.m7708a} 的 put 顺序生成列值表。 */
    public LinkedHashMap<String, Object> toValues() {
        LinkedHashMap<String, Object> v = new LinkedHashMap<>();
        if (rowId != null) {
            v.put(TvProviderColumns.ID, rowId);
        }
        v.put(TvProviderColumns.CHANNEL_ID, channelId);
        v.put(TvProviderColumns.TYPE, type);
        v.put(TvProviderColumns.TITLE, title);
        v.put(TvProviderColumns.SHORT_DESCRIPTION, shortDescription);
        v.put(TvProviderColumns.POSTER_ART_URI, posterArtUri);
        v.put(TvProviderColumns.POSTER_ART_ASPECT_RATIO, posterArtAspectRatio);
        v.put(TvProviderColumns.DURATION_MILLIS, durationMillis);
        v.put(TvProviderColumns.INTENT_URI, intentUri);
        v.put(TvProviderColumns.INTERNAL_PROVIDER_ID, internalProviderId);
        v.put(TvProviderColumns.WEIGHT, weight);
        return v;
    }

    /** 从 ContentResolver 查询结果（{@code at3.m1471a}）还原。缺列取类型默认值。 */
    public static PreviewProgram fromValues(Map<String, Object> v) {
        return new PreviewProgram(
                asLong(v.get(TvProviderColumns.ID)),
                asLong(v.get(TvProviderColumns.CHANNEL_ID)) == null
                        ? -1L : asLong(v.get(TvProviderColumns.CHANNEL_ID)),
                asInt(v.get(TvProviderColumns.TYPE)),
                asString(v.get(TvProviderColumns.TITLE)),
                asString(v.get(TvProviderColumns.SHORT_DESCRIPTION)),
                asString(v.get(TvProviderColumns.POSTER_ART_URI)),
                asInt(v.get(TvProviderColumns.POSTER_ART_ASPECT_RATIO)),
                asInt(v.get(TvProviderColumns.DURATION_MILLIS)),
                asString(v.get(TvProviderColumns.INTENT_URI)),
                asString(v.get(TvProviderColumns.INTERNAL_PROVIDER_ID)),
                asInt(v.get(TvProviderColumns.WEIGHT)));
    }

    private static Long asLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o instanceof String s && !s.isEmpty()) {
            try {
                return Long.valueOf(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static int asInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s && !s.isEmpty()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
