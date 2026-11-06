/*
 * 首页数据源（新 UI 栈的最小接入口）。
 * 说明：首页渲染由 UI 线负责，本类只负责「拿数据 + 把错误语义暴露给调用方」，
 *       不缓存、不重试、不吞异常，避免把网络/解码/业务失败伪装成「空列表」。
 */
package axtv.app;

import java.util.List;

import axtv.api.ApiException;
import axtv.api.FeedApi;
import axtv.model.Aweme;
import axtv.model.FeedResponse;

/**
 * 首页推荐流数据源。
 *
 * <p>内部调用强类型门面 {@link FeedApi#tabFeed(int, int, int)}，一次请求返回一页作品。
 *
 * <p><b>关于 cursor</b>：`/aweme/v1/web/tab/feed/` 原实现没有 cursor 参数，
 * 翻页靠 {@code refresh_index} + {@code pull_type}。因此这里的 {@code cursor}
 * 被定义为「已加载页数」：
 * <ul>
 *   <li>{@code 0} → 首屏（refresh_index=1，pull_type=0）</li>
 *   <li>{@code >0} → 加载更多（refresh_index=cursor+1，pull_type=2）</li>
 * </ul>
 * 原版首页首页刷新用 pull_type=0、加载更多用 pull_type=2（n11.m7995c0 默认值 +
 * kq5.m7281s 调用点），没有 pull_type=1 这个取值。
 * 需要按 max_cursor 翻页的调用方应改用 {@link axtv.api.FeedApi#followFeed(int, long, String)}
 * 等真正带 cursor 的端点，而不是把 max_cursor 传进来。
 */
public final class FeedDataSource {

    /** 原版首页首屏条数（a40 / C1887zj 对空 tag 传 15）。 */
    public static final int DEFAULT_PAGE_SIZE = 15;

    /** 加载更多要回传的「最近已展示作品」条数（原版取 10 个）。 */
    private static final int RECENT_ID_LIMIT = 10;

    private final FeedApi feedApi;

    public FeedDataSource(FeedApi feedApi) {
        if (feedApi == null) {
            throw new IllegalArgumentException("feedApi == null");
        }
        this.feedApi = feedApi;
    }

    /**
     * 拉取一页首页作品（不携带最近作品 id，只适合首屏）。
     *
     * @param cursor 已加载页数，见类注释
     * @throws ApiException 任一错误分支
     */
    public FeedPage loadFeed(long cursor) throws ApiException {
        return loadFeed(cursor, List.of());
    }

    /**
     * 拉取一页首页作品，并把最近已展示的作品 id 回传给推荐接口。
     *
     * <p>原版加载更多时把这些 id 拼进 {@code aweme_pc_rec_raw_data.videoPrefer}；
     * 不传会让服务端无法判断推荐上下文，容易返回重复页或直接空页，表现为「翻不动」。
     *
     * @param cursor         已加载页数，见类注释
     * @param recentAwemeIds 最近已展示作品 id（按展示顺序，取最后 10 个）
     * @throws ApiException 任一错误分支
     */
    public FeedPage loadFeed(long cursor, List<String> recentAwemeIds) throws ApiException {
        boolean firstPage = cursor <= 0;
        int refreshIndex = firstPage ? 1 : (int) Math.min((long) Integer.MAX_VALUE - 1, cursor + 1);
        int pullType = firstPage ? 0 : 2;
        List<String> recent = recentAwemeIds == null
                ? List.of()
                : recentAwemeIds.stream()
                        .filter(id -> id != null && !id.isBlank())
                        .toList();
        if (recent.size() > RECENT_ID_LIMIT) {
            recent = recent.subList(recent.size() - RECENT_ID_LIMIT, recent.size());
        }
        FeedResponse response = feedApi.tabFeed(DEFAULT_PAGE_SIZE, refreshIndex, pullType, recent);
        requireSuccess(response.statusCode());
        List<Aweme> awemes = response.flatAwemes();
        // has_more 可能以布尔/数字/字符串多种形态返回，模型层（ModelJsonCodec）已按
        // FlexibleBoolean 规则归一化；缺失（false）时退回「本页非空就还能翻」，
        // 避免服务端不下发该字段时直接停在第一页。
        boolean resolvedHasMore = response.hasMore();
        return new FeedPage(awemes, resolvedHasMore || !awemes.isEmpty());
    }

    /** 一页信息流：作品 + 是否还有下一页。 */
    public record FeedPage(List<Aweme> awemes, boolean hasMore) {
        public FeedPage {
            awemes = awemes == null ? List.of() : List.copyOf(awemes);
        }
    }

    private static void requireSuccess(int statusCode) throws ApiException {
        if (statusCode == 0) {
            return;
        }
        if (statusCode == 8) {
            throw ApiException.loginRequired();
        }
        if (statusCode == 2154) {
            throw ApiException.rateLimited();
        }
        throw ApiException.apiStatus(statusCode, "信息流返回 status_code=" + statusCode);
    }
}
