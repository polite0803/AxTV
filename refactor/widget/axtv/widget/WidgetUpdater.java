/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetUpdater.java (308 行)
 * 原职责: App 内即时推送（播放/搜索/列表数据）落库并触发 Glance 更新；按 kind 分派到具体 widget
 * 重构说明: 完整保留 pushLive / pushSearch / pushVideos / recordVideo / updateAll / updateKind，
 *           HISTORY/LATER 的前置校验（"Failed requirement."）、save* + updateKind 时序，
 *           以及 kind → GlanceAppWidget 的映射表。Glance 更新通过 WidgetEnvironment.UpdateSink 注入。
 */
package axtv.widget;

import java.util.ArrayList;
import java.util.List;

/** 落库 + 触发 Glance 更新。 */
public final class WidgetUpdater {

    private final WidgetEnvironment env;
    private final WidgetStore store;

    public WidgetUpdater(WidgetEnvironment env, WidgetStore store) {
        this.env = env;
        this.store = store;
    }

    /** kind → 原 GlanceAppWidget 类名（WidgetUpdater.updateKind:285-304 / Scheduler.receiver:84-103）。 */
    public static String glanceWidgetName(WidgetKind kind) {
        switch (kind) {
            case HISTORY: return "ContinueWidget";
            case LATER:   return "WatchLaterWidget";
            case MIX:     return "MixWidget";
            case LIVE:    return "LiveWidget";
            case SEARCH:  return "SearchWidget";
            default: throw new IllegalStateException("no widget for kind");
        }
    }

    /** kind → 原 AppWidgetProvider（Receiver）类名（WidgetRefreshScheduler.receiver:84-103）。 */
    public static String receiverName(WidgetKind kind) {
        switch (kind) {
            case HISTORY: return "ContinueWidgetReceiver";
            case LATER:   return "WatchLaterWidgetReceiver";
            case MIX:     return "MixWidgetReceiver";
            case LIVE:    return "LiveWidgetReceiver";
            case SEARCH:  return "SearchWidgetReceiver";
            default: throw new IllegalStateException("no receiver for kind");
        }
    }

    /** 对应 k21.m7006u0(new XxxWidget(), context)（WidgetUpdater.java:281-307）。 */
    public void updateKind(WidgetKind kind) {
        env.updateSink.updateKind(kind);
    }

    /**
     * 对应 updateAll（WidgetUpdater.java:218-279）：遍历所有 kind 更新，最后额外更新 NowPlayingWidget。
     * NowPlayingWidget 不在 WidgetKind 中，由 UpdateSink.updateAll 统一处理。
     */
    public void updateAll() {
        env.updateSink.updateAll();
    }

    /** 直播数据即时推送：saveLive(READY, now) + updateKind(LIVE)（:107-120）。 */
    public void pushLive(List<WidgetDataSource.LiveRoom> rooms) {
        List<WidgetLiveItem> items = new ArrayList<>();
        for (WidgetDataSource.LiveRoom r : rooms) {
            items.add(WidgetData.toWidgetLiveItem(r));
        }
        store.saveLive(new LiveSnapshot(env.settings.currentAccountId(),
                WidgetLoadState.READY, items, env.clock.getAsLong()));
        updateKind(WidgetKind.LIVE);
    }

    /** 热词即时推送：saveSearch(READY) + updateKind(SEARCH)（:122-134）。 */
    public void pushSearch(List<WidgetDataSource.HotWord> words) {
        List<WidgetHotWord> items = new ArrayList<>();
        for (WidgetDataSource.HotWord w : words) {
            items.add(new WidgetHotWord(WidgetVideo.nz(w.word()), w.label()));
        }
        store.saveSearch(new SearchSnapshot(env.settings.currentAccountId(),
                WidgetLoadState.READY, items));
        updateKind(WidgetKind.SEARCH);
    }

    /**
     * 视频列表即时推送：仅 HISTORY / LATER 合法，否则原代码抛 "Failed requirement."（:136-140）。
     */
    public void pushVideos(WidgetKind kind, List<WidgetDataSource.Video> videos) {
        if (kind != WidgetKind.HISTORY && kind != WidgetKind.LATER) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        List<WidgetVideo> items = new ArrayList<>();
        for (WidgetDataSource.Video v : videos) {
            items.add(WidgetData.toWidgetVideo(v, env.countFormatter));
        }
        VideoListSnapshot snap = new VideoListSnapshot(
                env.settings.currentAccountId(), WidgetLoadState.READY, items);
        if (kind == WidgetKind.HISTORY) {
            store.saveHistory(snap);
        } else {
            store.saveLater(snap);
        }
        updateKind(kind);
    }

    /** 观看记录：store.recordVideo + updateKind(HISTORY) + updateKind(MIX)（:166-208）。 */
    public void recordVideo(WidgetDataSource.Video aweme) {
        store.recordVideo(aweme);
        updateKind(WidgetKind.HISTORY);
        updateKind(WidgetKind.MIX);
    }
}
