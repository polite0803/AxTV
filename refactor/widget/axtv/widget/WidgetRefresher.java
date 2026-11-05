/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetRefresher.java (1271 行)
 * 原职责: 刷新主逻辑 —— 按 WidgetKind 拉取数据 → 过滤/去重/映射 → 写快照 → updateKind；
 *         含守卫（历史开关 / PIN 锁）、失败落 ERROR 快照、账户切换保护、CancellationException 分支
 * 重构说明: 完整还原 refresh 的分支与异常语义、5 个 refreshXxx、saveError；
 *           数据源用 WidgetDataSource 隔离，kq5 的 4 个取数方法与原行号见各类头注释；
 *           loadMixItem / refreshMix 原方法因 jadx 溢出未反编译，按数据契约 + 结构还原（见 NOTES.md 存疑）。
 */
package axtv.widget;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** 小组件刷新主逻辑。 */
public final class WidgetRefresher {

    private final WidgetEnvironment env;
    private final WidgetStore store;
    private final WidgetUpdater updater;

    public WidgetRefresher(WidgetEnvironment env, WidgetStore store, WidgetUpdater updater) {
        this.env = env;
        this.store = store;
        this.updater = updater;
    }

    /**
     * 刷新一个 kind，返回是否成功（对应 WidgetRefresher.refresh:645 返回 Boolean）。
     *
     * <p>时序：守卫 → 取数 → 写快照(READY) → release 资源 → updateKind → true；
     * 取消异常且账户未变 → saveError → updateKind → false；其它异常 → updateKind → 抛出。
     */
    public boolean refresh(WidgetKind kind) {
        String account = env.settings.currentAccountId();

        // 守卫（:694）：历史功能关闭或账户上锁 → 跳过取数，仅更新 UI 并返回 true（case 1 → Boolean.TRUE）。
        if (!env.settings.historyEnabled() || store.isLocked()) {
            updater.updateKind(kind);
            return true;
        }

        try {
            // 对应 new kq5(new n11(fl4, c1608s), fl4) 取数；c1608s.m10094a() 的资源释放由 finally 表达。
            switch (kind) {
                case HISTORY: refreshHistory(account); break;
                case LATER:   refreshLater(account);   break;
                case MIX:     refreshMix(account);     break;
                case LIVE:    refreshLive(account);    break;
                case SEARCH:  refreshSearch(account);  break;
                default: throw new IllegalStateException("unknown WidgetKind");
            }
            updater.updateKind(kind);
            return true;
        } catch (CancellationException e) {
            // :845-870 / :1041-1066：仅当账户未被切换才落 ERROR 快照，然后 updateKind 并返回 false。
            if (account.equals(env.settings.currentAccountId())) {
                saveError(kind, account);
            }
            updater.updateKind(kind);
            return false;
        } catch (RuntimeException e) {
            // :962-981 / :1265：任何其它异常都要先 updateKind 再重新抛出。
            updater.updateKind(kind);
            throw e;
        } finally {
            // 原 c1608s.m10094a()：Android 侧在此释放 wakelock / 数据库句柄等本次刷新资源。
            env.dataSource.onRefreshFinished();
        }
    }

    // ---------------------------------------------------------------- 各取数路径

    /** 对应 refreshHistory:315-363：kq5.m7253F(0L) → filterPrimaryPlayableAwemes → toWidgetVideo。 */
    private void refreshHistory(String account) {
        List<WidgetDataSource.Video> raw = env.dataSource.fetchHistory(0L);
        List<WidgetVideo> items = new ArrayList<>();
        for (WidgetDataSource.Video v : raw) {
            items.add(WidgetData.toWidgetVideo(v, env.countFormatter));
        }
        // p70.m8943J(list, 10) 只是 ArrayList 初始容量提示，不做 take(10)：保留全部。
        store.saveHistory(new VideoListSnapshot(account, WidgetLoadState.READY, items));
    }

    /** 对应 refreshLater:367-415：kq5.m7254G(0) → toWidgetVideo。 */
    private void refreshLater(String account) {
        List<WidgetDataSource.Video> raw = env.dataSource.fetchLater(0);
        List<WidgetVideo> items = new ArrayList<>();
        for (WidgetDataSource.Video v : raw) {
            items.add(WidgetData.toWidgetVideo(v, env.countFormatter));
        }
        store.saveLater(new VideoListSnapshot(account, WidgetLoadState.READY, items));
    }

    /** 对应 refreshLive:419-487：过滤空 roomId → 按 roomId 去重 → toWidgetLiveItem → updatedAt=now。 */
    private void refreshLive(String account) {
        List<WidgetDataSource.LiveRoom> raw = env.dataSource.fetchLive();
        List<WidgetDataSource.LiveRoom> filtered = new ArrayList<>();
        for (WidgetDataSource.LiveRoom r : raw) {
            if (!WidgetData.isBlank(r.resolvedRoomId())) {
                filtered.add(r);
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        List<WidgetLiveItem> items = new ArrayList<>();
        for (WidgetDataSource.LiveRoom r : filtered) {
            if (seen.add(r.resolvedRoomId())) {
                items.add(WidgetData.toWidgetLiveItem(r));
            }
        }
        store.saveLive(new LiveSnapshot(account, WidgetLoadState.READY,
                items, env.clock.getAsLong()));
    }

    /** 对应 refreshSearch:510-565：过滤空 word → new WidgetHotWord(word, label)。 */
    private void refreshSearch(String account) {
        List<WidgetDataSource.HotWord> raw = env.dataSource.fetchHotWords();
        List<WidgetHotWord> items = new ArrayList<>();
        for (WidgetDataSource.HotWord w : raw) {
            if (!WidgetData.isBlank(w.word())) {
                items.add(new WidgetHotWord(WidgetVideo.nz(w.word()), w.label()));
            }
        }
        store.saveSearch(new SearchSnapshot(account, WidgetLoadState.READY, items));
    }

    /**
     * 对应 refreshMix:500-506 / loadMixItem:305-311（jadx 溢出未反编译）。
     * 原逻辑：遍历现有 mix items，对每个经 loadMixItem 拉取最新选集/进度后合并；
     * 此处以 WidgetDataSource.fetchMixUpdates 契约完整表达「增量更新判定 + 取数」。
     */
    private void refreshMix(String account) {
        List<WidgetMixItem> existing = store.mix().items();
        List<WidgetMixItem> updated = env.dataSource.fetchMixUpdates(account, existing);
        store.saveMix(new MixSnapshot(account, WidgetLoadState.READY,
                updated == null ? List.of() : updated));
    }

    // ---------------------------------------------------------------- 失败

    /** 对应 saveError:567-586：按 kind 写入 state=ERROR、items=[] 的快照。 */
    public void saveError(WidgetKind kind, String account) {
        switch (kind) {
            case HISTORY:
                store.saveHistory(new VideoListSnapshot(account, WidgetLoadState.ERROR, List.of()));
                break;
            case LATER:
                store.saveLater(new VideoListSnapshot(account, WidgetLoadState.ERROR, List.of()));
                break;
            case MIX:
                store.saveMix(new MixSnapshot(account, WidgetLoadState.ERROR, List.of()));
                break;
            case LIVE:
                store.saveLive(new LiveSnapshot(account, WidgetLoadState.ERROR, List.of(), 0L));
                break;
            case SEARCH:
                store.saveSearch(new SearchSnapshot(account, WidgetLoadState.ERROR, List.of()));
                break;
            default:
                throw new IllegalStateException("no error snapshot for kind");
        }
    }

    /**
     * 增量/自触发刷新判定：对应 SearchWidget.provideGlance:155-158 —— 账户不匹配或仍 LOADING
     * 时 UI 侧主动 refreshNow。其它 widget 的 provideGlance 结构相同。
     */
    public static boolean needsRefresh(String snapshotAccountId, WidgetLoadState state,
                                       String currentAccountId) {
        return !currentAccountId.equals(snapshotAccountId) || state == WidgetLoadState.LOADING;
    }
}
