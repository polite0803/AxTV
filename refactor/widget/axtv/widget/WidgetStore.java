/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetStore.java (383 行)
 * 原职责: SharedPreferences("axtv_desktop_widgets") 里 5 个快照的读写、账户校验、观看记录落库、
 *         每个账户配置 PIN 锁判定
 * 重构说明: 逐字保留 PREFS 名、5 个 key、canSave 规则、读取失败兜底默认快照、commit 语义、
 *           recordVideo 的去重/截断（history 20 条 / mix 3 条）与合集进度合并全部逻辑。
 *           存储与 JSON 分别用 WidgetStorage / JsonCodec 接口隔离。
 */
package axtv.widget;

import java.util.ArrayList;
import java.util.List;

/** 小组件快照持久化 + 观看记录落库。 */
public final class WidgetStore {

    private final WidgetEnvironment env;

    public WidgetStore(WidgetEnvironment env) {
        this.env = env;
    }

    // ---------------------------------------------------------------- 读

    /** 读取；key 不存在或 JSON 解析失败一律返回默认空快照（WidgetStore.java:86-107 等）。 */
    private <T> T read(String key, Class<T> type, T def) {
        String raw = env.storage.getString(key, null);
        if (raw == null) {
            return def;
        }
        try {
            T value = env.json.decode(raw, type);
            return value != null ? value : def;
        } catch (Exception e) {
            return def;
        }
    }

    public VideoListSnapshot history() {
        return read(WidgetStorage.KEY_HISTORY, VideoListSnapshot.class, VideoListSnapshot.empty());
    }

    public VideoListSnapshot later() {
        return read(WidgetStorage.KEY_LATER, VideoListSnapshot.class, VideoListSnapshot.empty());
    }

    public LiveSnapshot live() {
        return read(WidgetStorage.KEY_LIVE, LiveSnapshot.class, LiveSnapshot.empty());
    }

    public MixSnapshot mix() {
        return read(WidgetStorage.KEY_MIX, MixSnapshot.class, MixSnapshot.empty());
    }

    public SearchSnapshot search() {
        return read(WidgetStorage.KEY_SEARCH, SearchSnapshot.class, SearchSnapshot.empty());
    }

    // ---------------------------------------------------------------- 写

    /** canSave：accountId 非空且等于当前登录账户（WidgetStore.java:43-45）。 */
    public boolean canSave(String accountId) {
        return !WidgetData.isBlank(accountId)
                && accountId.equals(env.settings.currentAccountId());
    }

    private <T> void write(String key, T value, String accountId) {
        if (!canSave(accountId)) {
            return;
        }
        env.storage.putString(key, env.json.encode(value));
    }

    public void saveHistory(VideoListSnapshot v) {
        write(WidgetStorage.KEY_HISTORY, v, v.accountId());
    }

    public void saveLater(VideoListSnapshot v) {
        write(WidgetStorage.KEY_LATER, v, v.accountId());
    }

    public void saveLive(LiveSnapshot v) {
        write(WidgetStorage.KEY_LIVE, v, v.accountId());
    }

    public void saveMix(MixSnapshot v) {
        write(WidgetStorage.KEY_MIX, v, v.accountId());
    }

    public void saveSearch(SearchSnapshot v) {
        write(WidgetStorage.KEY_SEARCH, v, v.accountId());
    }

    /** clear().commit()（WidgetStore.java:81-84）。 */
    public void clear() {
        env.storage.clear();
    }

    /** 当前账户是否被 PIN 上锁（原 WidgetStore.isLocked(fl4)，UI 状态分支第一优先级）。 */
    public boolean isLocked() {
        return env.settings.isLocked();
    }

    // ---------------------------------------------------------------- recordVideo

    /**
     * 记录一次观看：写历史（去重置顶、截断 20 条），并在有合集信息时更新合集进度
     * （去重置顶、截断 3 条）。对应 WidgetStore.java:193-304。
     */
    public synchronized void recordVideo(WidgetDataSource.Video aweme) {
        String account = env.settings.currentAccountId();
        if (WidgetData.isBlank(account) || !env.settings.historyEnabled() || isLocked()) {
            return;
        }

        WidgetVideo wv = WidgetData.toWidgetVideo(aweme, env.countFormatter);

        // ---- 历史 ----
        VideoListSnapshot hist = history();
        if (!account.equals(hist.accountId())) {
            hist = new VideoListSnapshot(account, WidgetLoadState.LOADING, List.of());
        }
        List<WidgetVideo> mergedHistory = prependDedup(
                List.of(wv), hist.items(), WidgetVideo::id, wv.id(), 20);
        saveHistory(new VideoListSnapshot(hist.accountId(), WidgetLoadState.READY, mergedHistory));

        // ---- 合集追更 ----
        WidgetDataSource.MixInfo mi = aweme.mixInfo();
        if (mi == null || WidgetData.isBlank(mi.mixId())) {
            return;
        }
        MixSnapshot mix = mix();
        if (!account.equals(mix.accountId())) {
            mix = new MixSnapshot(account, WidgetLoadState.LOADING, List.of());
        }
        WidgetMixItem existing = null;
        for (WidgetMixItem it : mix.items()) {
            if (it.id().equals(mi.mixId())) {
                existing = it;
                break;
            }
        }

        int currentEpisode = Math.max(mi.currentEpisode(), existing != null ? existing.episode() : 0);
        // 原 j42.m6472N(totalEpisode, updatedToEpisode, currentEpisode, existingTotal)
        // = 对全部实参取 max（p000/j42.java:292-297 已确认）。
        int total = Math.max(
                Math.max(Math.max(mi.totalEpisode(), mi.updatedToEpisode()), currentEpisode),
                existing != null ? existing.total() : 0);

        String name = mi.mixName();
        if (WidgetData.isBlank(name)) {
            name = existing != null ? existing.name() : "";
        }
        String author = aweme.authorNickname();
        if (WidgetData.isBlank(author)) {
            author = existing != null ? existing.author() : "";
        }
        String cover = WidgetData.firstNonBlank(mi.coverUrls());
        if (cover == null) {
            cover = wv.coverUrl();
            if (WidgetData.isBlank(cover)) {
                cover = existing != null ? existing.coverUrl() : "";
            }
        }
        List<WidgetEpisode> next = existing != null ? existing.nextEpisodes() : List.of();
        List<WidgetEpisode> kept = new ArrayList<>();
        for (WidgetEpisode e : next) {
            if (e.number() > currentEpisode) {
                kept.add(e);
            }
        }
        WidgetMixItem updated = new WidgetMixItem(
                mi.mixId(), name, author, cover, currentEpisode, total,
                WidgetVideo.nz(aweme.awemeId()), kept);

        List<WidgetMixItem> mergedMix = prependDedup(
                List.of(updated), mix.items(), WidgetMixItem::id, mi.mixId(), 3);
        saveMix(new MixSnapshot(mix.accountId(), WidgetLoadState.READY, mergedMix));
    }

    /** 把 newHeads 置顶，剔除 old 中与 newHeads 同 id 的项，最后截断到 limit 条。 */
    private static <T> List<T> prependDedup(List<T> heads, List<T> old,
                                            java.util.function.Function<T, String> idOf,
                                            String dedupId, int limit) {
        List<T> out = new ArrayList<>(heads);
        for (T item : old) {
            if (!idOf.apply(item).equals(dedupId)) {
                out.add(item);
            }
        }
        return out.size() > limit ? List.copyOf(out.subList(0, limit)) : List.copyOf(out);
    }
}
