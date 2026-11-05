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
 * 被定义为「页序号」：
 * <ul>
 *   <li>{@code 0} → 首屏（refresh_index=1，pull_type=1）</li>
 *   <li>{@code >0} → 加载更多（refresh_index=cursor，pull_type=2）</li>
 * </ul>
 * 需要按 max_cursor 翻页的调用方应改用 {@link axtv.api.FeedApi#followFeed(int, long, String)}
 * 等真正带 cursor 的端点，而不是把 max_cursor 传进来。
 */
public final class FeedDataSource {

    /** 与原首页首屏一致的默认条数。 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private final FeedApi feedApi;

    public FeedDataSource(FeedApi feedApi) {
        if (feedApi == null) {
            throw new IllegalArgumentException("feedApi == null");
        }
        this.feedApi = feedApi;
    }

    /**
     * 拉取一页首页作品。
     *
     * <p>错误处理（与 {@link ApiException.Kind} 一一对应）：
     * <ul>
     *   <li>{@link ApiException.Kind#NETWORK}：底层 IO/网络失败</li>
     *   <li>{@link ApiException.Kind#HTTP_STATUS}：HTTP 非 2xx，{@code httpStatus()} 为状态码</li>
     *   <li>{@link ApiException.Kind#EMPTY_BODY}：2xx 但响应体为空（常见于签名/Cookie 失效）</li>
     *   <li>{@link ApiException.Kind#DECODE}：JSON 无法解成 {@link FeedResponse}</li>
     *   <li>{@link ApiException.Kind#LOGIN_REQUIRED} / {@link ApiException.Kind#RATE_LIMITED} /
     *       {@link ApiException.Kind#API_STATUS}：HTTP 2xx 但接口 {@code status_code != 0}</li>
     * </ul>
     * 上述任一情况都不会返回空列表。只有 {@code status_code == 0} 且服务端确实没有下发作品时，
     * 才返回合法的空列表。
     *
     * @param cursor 页序号，见类注释
     * @throws ApiException 任一错误分支
     */
    public List<Aweme> loadFeed(long cursor) throws ApiException {
        int refreshIndex = cursor <= 0 ? 1 : (int) Math.min(Integer.MAX_VALUE, cursor);
        int pullType = cursor <= 0 ? 1 : 2;
        FeedResponse response = feedApi.tabFeed(DEFAULT_PAGE_SIZE, refreshIndex, pullType);
        requireSuccess(response.statusCode());
        return response.flatAwemes();
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
