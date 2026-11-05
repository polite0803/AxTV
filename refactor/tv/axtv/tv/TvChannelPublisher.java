/*
 * 来源: src/app/p000/mb5.java:78-136 (m7709b)、139-163 (m7710c)
 *       src/app/p000/lb5.java:113-331 (invokeSuspend case 0，频道同步协程)
 *       src/app/p000/g73.java:721-760 (m4577t/m4578u)、158-190 (m4560G)
 *       src/app/p000/jb5.java:9-13 (m6599a 可用性判定)
 * 原职责: Android TV 首页「推荐 / 稍后再看」两个 Preview Channel 的创建与增量同步
 * 重构说明: Kotlin 协程 + Mutex → 普通同步方法 + synchronized；
 *           ContentResolver 全部走 TvProvider 接口；算法与常量逐条还原
 */
package axtv.tv;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import axtv.model.Aweme;

/** 电视频道发布器。 */
public final class TvChannelPublisher {

    /** 同步结果状态。 */
    public enum Status {
        /** SDK &lt; 26 或系统无 android.media.tv provider（{@code jb5.m6599a} 为 false）。 */
        SKIPPED,
        /** 频道尚未创建；原实现在这种情况下直接返回，不会自动创建频道。 */
        NO_CHANNEL,
        /** 同步过程中出现异常，日志 TAG=TvChannelPublisher。 */
        FAILED,
        OK
    }

    /**
     * @param channelId 命中的频道 id，未命中为 -1
     * @param wanted    本轮想展示的节目数（最多 30，已过滤无 id / 无封面的条目）
     * @param existing  同步前该频道已有的节目数
     * @param inserted  新插入条数
     * @param updated   因字段变化而更新的条数
     * @param deleted   被删除的多余节目数
     */
    public record SyncResult(Status status, long channelId, int wanted, int existing,
                             int inserted, int updated, int deleted, Throwable error) {
    }

    private final TvProvider provider;
    private final TvLog log;
    /** 原 Kotlin {@code Mutex}（mb5.f13971b / lb5 的 o53），保证多个同步协程串行。 */
    private final Object syncMutex = new Object();

    public TvChannelPublisher(TvProvider provider, TvLog log) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.log = log == null ? TvLog.noop() : log;
    }

    /** {@code jb5.m6599a}: SDK &gt;= 26 且系统存在 TvProvider 才发布。 */
    public boolean isAvailable() {
        return provider.sdkInt() >= ChannelSpec.MIN_SDK && provider.hasTvProvider();
    }

    /**
     * 精确还原 {@code mb5.m7709b(String, boolean)}：按 internal_provider_id 找频道，
     * 找不到就创建（含 logo 与 browsable 请求）。
     *
     * @param browsable 是否请求让频道可浏览；原实现中该值为 true 且 SDK &lt; 26 时直接返回 -1，
     *                  <b>不会</b>插入频道
     */
    public long ensureChannel(String channelKey, boolean browsable) throws IOException {
        int sdk = provider.sdkInt();
        Long found = findChannelId(channelKey);
        if (found != null) {
            return found;
        }

        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put(TvProviderColumns.DISPLAY_NAME, ChannelSpec.displayName(channelKey));
        values.put(TvProviderColumns.INTERNAL_PROVIDER_ID, channelKey);
        values.put(TvProviderColumns.APP_LINK_INTENT_URI, ChannelSpec.APP_LINK_INTENT_URI);
        // g12.m4356k 无条件写入；display_name / app_link_intent_uri 为空时原实现会抛
        // "Need channel name..." / "Need app link intent uri..."，这里由非空常量保证
        values.put(TvProviderColumns.TYPE, ChannelSpec.CHANNEL_TYPE);

        if (!provider.channelLogoReady()) {
            provider.createChannelLogo();
        }
        String logoUri = provider.channelLogoUri();

        if (!browsable) {
            return insertChannelWithLogo(values, logoUri, sdk);
        }
        if (sdk < ChannelSpec.MIN_SDK) {
            return -1L;
        }
        long id = insertChannelWithLogo(values, logoUri, sdk);
        provider.requestChannelBrowsable(id);
        return id;
    }

    /** {@code g73.m4577t}: 插入频道 + 写 logo，失败回滚删除频道。 */
    private long insertChannelWithLogo(Map<String, Object> values, String logoUri, int sdk)
            throws IOException {
        if (sdk < ChannelSpec.MIN_SDK) {
            return -1L;
        }
        try {
            long id = provider.insertChannel(values);
            if (id < 0) {
                throw new NullPointerException("Channel insertion failed");
            }
            if (!provider.addChannelLogo(id, logoUri)) {
                provider.deleteChannel(id);
                throw new IOException("Failed to add logo, so channel (ID=" + id + ") was not created");
            }
            return id;
        } catch (SecurityException e) {
            log.e(ChannelSpec.TAG_HELPER,
                    "Your app's ability to insert data into the TvProvider may have been revoked.", e);
            return -1L;
        }
    }

    /** {@code mb5.m7710c}: 查全部节目后按 channel_id 过滤（缺列按 -1 处理）。 */
    public List<PreviewProgram> queryPrograms(long channelId) {
        ArrayList<PreviewProgram> out = new ArrayList<>();
        List<Map<String, Object>> rows = provider.queryAllPrograms();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            Long cid = longOrNull(row.get(TvProviderColumns.CHANNEL_ID));
            if ((cid == null ? -1L : cid) == channelId) {
                out.add(PreviewProgram.fromValues(row));
            }
        }
        return out;
    }

    /**
     * 精确还原 {@code lb5.invokeSuspend(case 0)} 的频道同步。
     *
     * <p>流程：可用性检查 → 取得 Mutex → 找频道（找不到就什么也不做）→ take(30) 构造节目
     * → 删除多余节目 → 已存在的按 id 做字段 diff 后更新、不存在则插入 → 记 synced 日志。
     */
    public SyncResult sync(String channelKey, List<Aweme> videos) {
        if (!isAvailable()) {
            return new SyncResult(Status.SKIPPED, -1, 0, 0, 0, 0, 0, null);
        }
        synchronized (syncMutex) {
            try {
                Long channelId = findChannelId(channelKey);
                if (channelId == null) {
                    // 原 lb5:216-218 / 334-336：ys3Var == null 时直接返回 Unit
                    return new SyncResult(Status.NO_CHANNEL, -1, 0, 0, 0, 0, 0, null);
                }
                long cid = channelId;

                List<PreviewProgram> wanted = ProgramBuilder.buildWanted(cid, videos);
                Set<String> wantedIds = new HashSet<>();
                for (PreviewProgram p : wanted) {
                    wantedIds.add(p.internalProviderId());
                }

                List<PreviewProgram> existing = queryPrograms(cid);
                int deleted = 0;
                for (PreviewProgram old : existing) {
                    String oldId = old.internalProviderId();
                    if (oldId == null || !wantedIds.contains(oldId)) {
                        if (provider.sdkInt() >= ChannelSpec.MIN_SDK && old.rowId() != null) {
                            provider.deleteProgram(old.rowId());
                            deleted++;
                        }
                    }
                }

                // 原 lb5:286-311：以 internal_provider_id → _id 建映射（无 id 的条目跳过）
                Map<String, Long> existingIds = new HashMap<>();
                for (PreviewProgram old : existing) {
                    if (old.internalProviderId() != null && old.rowId() != null) {
                        existingIds.put(old.internalProviderId(), old.rowId());
                    }
                }

                int inserted = 0;
                int updated = 0;
                for (PreviewProgram p : wanted) {
                    LinkedHashMap<String, Object> values =
                            TvProviderColumns.applySdkFilter(p.toValues(), provider.sdkInt());
                    Long rowId = existingIds.get(p.internalProviderId());
                    if (rowId == null) {
                        provider.insertProgram(values);   // 原 g73.m4578u
                        inserted++;
                    } else if (updateIfChanged(rowId, values)) {
                        updated++;
                    }
                }

                log.i(ChannelSpec.TAG_PUBLISHER,
                        "synced " + channelKey + ": channel=" + cid
                                + " wanted=" + wanted.size() + " existing=" + existing.size());
                return new SyncResult(Status.OK, cid, wanted.size(), existing.size(),
                        inserted, updated, deleted, null);
            } catch (Exception e) {
                log.w(ChannelSpec.TAG_PUBLISHER, "同步频道 " + channelKey + " 失败", e);
                return new SyncResult(Status.FAILED, -1, 0, 0, 0, 0, 0, e);
            }
        }
    }

    /** {@code g73.m4560G}: 逐字段比较，只有存在不同值才 update。 */
    private boolean updateIfChanged(long rowId, Map<String, Object> newValues) {
        Map<String, Object> old = provider.queryProgram(rowId);
        if (old == null) {
            return false;
        }
        for (Map.Entry<String, Object> e : newValues.entrySet()) {
            if (!Objects.deepEquals(e.getValue(), old.get(e.getKey()))) {
                provider.updateProgram(rowId, newValues);
                return true;
            }
        }
        return false;
    }

    private Long findChannelId(String channelKey) {
        List<Map<String, Object>> channels = provider.queryChannels();
        if (channels == null) {
            return null;
        }
        for (Map<String, Object> row : channels) {
            Object id = row.get(TvProviderColumns.INTERNAL_PROVIDER_ID);
            if (channelKey.equals(id == null ? null : id.toString())) {
                Long rowId = longOrNull(row.get(TvProviderColumns.ID));
                return rowId == null ? -1L : rowId;   // 原 ys3.m12163b() 缺列返回 -1
            }
        }
        return null;
    }

    private static Long longOrNull(Object o) {
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
}
