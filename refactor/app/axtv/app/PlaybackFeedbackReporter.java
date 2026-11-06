/*
 * 来源: src/app/p000/kq5.java:366-397（m7256I 播放埋点：空 id 跳过 + 进程内 item_id 去重 f12447i）
 *       src/app/p000/kq5.java:693-721（m7264Q 写观看历史）
 *       src/app/p000/n11.java:4635 / n11.java:620（两条端点的表单体字段）
 * 原职责: 把「用户看过哪条作品」告诉服务端，让 tab/feed 的推荐按观看行为调整后续批次
 * 重构说明: 取证结论——原版把这两个方法实现了但没有任何业务调用点
 *           （kq5.m7256I / m7264Q 的调用方只有 jadx 生成的续体类 bq5.java:26 / jq5.java:26），
 *           真正在播放链路上报的只有合集进度 mix/watch/record（fx3.java:54 → kq5.m7257J）。
 *           所以「接通越看越准」是本项目相对原版的主动偏离；偏离的只有「有没有人调用」，
 *           请求形态与去重/跳过语义仍逐字对齐上面几处。
 */
package axtv.app;

import java.util.LinkedHashSet;
import java.util.Set;

import axtv.api.ApiException;
import axtv.api.VideoApi;

/**
 * 播放行为上报（播放埋点 + 观看历史）。
 *
 * <p>两条腿各自按作品 id 在进程内去重，语义抄 {@code kq5.f12447i}：
 * <b>发送前</b>就入集合，因此失败也不重试（同一次进程里重复点同一条不会重复打网络）。
 * 换进程（重启）后集合清空，同一条会再报一次——原版同样是进程级作用域。
 *
 * <p>与 {@link FeedDataSource} 的「不吞异常」不同：本类的异常是<b>返回值的一部分</b>
 * （{@link Outcome}），因为上报属后台旁路，失败既不该打断播放，也不该伪装成没发生。
 */
public final class PlaybackFeedbackReporter {

    /**
     * 一次上报的结果。
     *
     * @param deduped      该作品本次未发送（已在本进程报过，或 id 为空）
     * @param statsError   播放埋点失败原因；成功或未尝试时为 null
     * @param historyError 观看历史失败原因；成功或未尝试时为 null
     */
    public record Outcome(boolean deduped, String statsError, String historyError) {

        public boolean hasFailure() {
            return statsError != null || historyError != null;
        }
    }

    private static final Outcome DEDUPED = new Outcome(true, null, null);

    private final VideoApi videoApi;

    /** 已报过播放埋点的作品 id（原版 kq5.f12447i）。 */
    private final Set<String> statsReported = new LinkedHashSet<>();

    /** 已写过观看历史的作品 id；与埋点各自独立，一条腿失败不牵连另一条的判定。 */
    private final Set<String> historyReported = new LinkedHashSet<>();

    public PlaybackFeedbackReporter(VideoApi videoApi) {
        if (videoApi == null) {
            throw new IllegalArgumentException("videoApi == null");
        }
        this.videoApi = videoApi;
    }

    /**
     * 上报「看过了这一条」。
     *
     * @param awemeId   作品 id；空（含纯空白）直接跳过，对齐 {@code jx4.m6866L} 判定
     * @param awemeType 作品类型（{@code aweme.aweme_type}），埋点体里的 aweme_type
     * @param authorId  发布者 id（{@code aweme.author.uid}），写历史用的 author_id
     */
    public synchronized Outcome reportWatched(String awemeId, int awemeType, String authorId) {
        if (awemeId == null || awemeId.isBlank()) {
            return DEDUPED;
        }
        // add 的返回值即「这条腿本次是不是新报的」；两条腿各自判定，互不牵连。
        boolean statsNew = statsReported.add(awemeId);
        boolean historyNew = historyReported.add(awemeId);
        if (!statsNew && !historyNew) {
            return DEDUPED;
        }
        String statsError = statsNew ? attempt(() -> videoApi.stats(awemeId, awemeType)) : null;
        String historyError = historyNew ? attempt(() -> videoApi.historyWrite(awemeId, authorId)) : null;
        return new Outcome(false, statsError, historyError);
    }

    private static String attempt(Call call) {
        try {
            call.run();
            return null;
        } catch (ApiException e) {
            return e.kind() + ": " + e.getMessage();
        }
    }

    /** VideoApi 的两个上报方法都返回 String（原实现不解析响应体），这里只关心有没有抛异常。 */
    private interface Call {
        void run() throws ApiException;
    }
}
