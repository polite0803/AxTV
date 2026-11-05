/*
 * 来源: src/app/p000/eu2.java:60-155 (Flow collect 的 emit(Boolean)/emit(List) 分支)
 *       src/app/p000/y30.java:290-310 (case 7 / case 8，推荐频道一次性发布标记)
 * 原职责: 决定「什么时候、用哪份列表、同步哪个频道」——电视频道发布的触发条件
 * 重构说明: Kotlin StateFlow/Flow + 协程 → 普通回调；三个状态位与判定顺序逐条还原
 */
package axtv.tv;

import java.util.List;
import axtv.model.Aweme;

/**
 * 电视频道同步触发器。
 *
 * <p>原实现观察三个条件：
 * <ul>
 *   <li>{@code fw4 ... f7643v0}：用户是否开启「发布到电视频道」</li>
 *   <li>{@code hu2.f8493d.f6216C}：数据是否已初始化完成（避免启动时用空列表清空频道）</li>
 *   <li>{@code n24.f14605I}：推荐频道是否已发布过（只发一次，避免反复覆盖）</li>
 * </ul>
 */
public final class TvSyncTrigger {

    /** 一次触发的结果。 */
    public record Trigger(String channelKey, List<Aweme> videos) {
    }

    /** 用户开关：是否允许发布到电视首页。 */
    private boolean channelEnabled;
    /** 数据初始化完成标记。 */
    private boolean ready;
    /** 推荐频道只发布一次（原 {@code n24.f14605I}）。 */
    private boolean recommendPublished;
    /** 最近一次「稍后再看」列表，供开关变化/ready 变化时补发。 */
    private List<Aweme> watchLater = List.of();

    public void setChannelEnabled(boolean enabled) {
        this.channelEnabled = enabled;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public boolean isChannelEnabled() {
        return channelEnabled;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isRecommendPublished() {
        return recommendPublished;
    }

    /** 调用方完成一次成功同步后回写，用于复位一次性标记（失败时可重发）。 */
    public void markRecommendPublished(boolean value) {
        this.recommendPublished = value;
    }

    /**
     * 推荐信息流到达（{@code y30 case 8} / {@code eu2} 的推荐分支）。
     * 条件：列表非空 &amp;&amp; 尚未发布 &amp;&amp; 开关开启 &amp;&amp; 已 ready。
     * 命中后立刻置位，后续重复回调不再触发。
     */
    public Trigger onRecommendFeed(List<Aweme> videos) {
        if (videos == null || videos.isEmpty()) {
            return null;
        }
        if (recommendPublished || !channelEnabled || !ready) {
            return null;
        }
        recommendPublished = true;
        return new Trigger(ChannelSpec.RECOMMEND, videos);
    }

    /**
     * 「稍后再看」列表变化（{@code eu2.emit(List)} / {@code y30 case 7}）。
     * 条件：开关开启 &amp;&amp; 已 ready。列表可以为空 —— 空列表的作用是清空频道节目。
     */
    public Trigger onWatchLaterChanged(List<Aweme> videos) {
        this.watchLater = videos == null ? List.of() : List.copyOf(videos);
        if (!channelEnabled || !ready) {
            return null;
        }
        return new Trigger(ChannelSpec.WATCH_LATER, watchLater);
    }

    /**
     * 用户开关变为开启（{@code eu2.m3788a} 的 Boolean 分支）。
     * 原实现用当前缓存的稍后再看列表立即同步一次，不需要等下一次列表变化。
     */
    public Trigger onChannelEnabledChanged(boolean enabled) {
        boolean was = this.channelEnabled;
        this.channelEnabled = enabled;
        if (!enabled || was || !ready) {
            return null;
        }
        return new Trigger(ChannelSpec.WATCH_LATER, watchLater);
    }

    /** 初始化完成后补发一次稍后再看（原 eu2 的 case 2/3 状态机）。 */
    public Trigger onReadyChanged(boolean isReady) {
        boolean was = this.ready;
        this.ready = isReady;
        if (!isReady || was || !channelEnabled) {
            return null;
        }
        return new Trigger(ChannelSpec.WATCH_LATER, watchLater);
    }
}
