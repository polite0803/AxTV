/*
 * 来源: src/app/p000/n11.java ——
 *       m7998g（视频搜索参数构造器，逐字还原）
 *       m8040f（用户搜索参数构造器）
 *       m8034a（直播搜索参数构造器）
 *       m7996d（搜索页 Referer：/search/<keyword>?aid=<uuid>&type=<type>）
 *       m8013E0（只注入 msToken）、m8011D0（公共参数）
 *       端点：search/item、general/search/single、discover/search、live/search、
 *            search/sug、api/suggest_words、hot/search/list、seo/inner/link
 * 原职责: 搜索全链路
 * 重构说明: 三个构造器的键、默认值、条件分支逐字保留（见 NOTES.md §2），
 *           不做"看起来更合理"的改写。
 */
package axtv.api;

import java.util.LinkedHashMap;
import java.util.Map;

import axtv.model.HotSearchResponse;
import axtv.model.SearchLiveResponse;
import axtv.model.SearchSugResponse;
import axtv.model.SearchUserResponse;
import axtv.model.SearchVideoWorkResponse;
import axtv.model.SeoLinkResponse;
import axtv.model.SuggestWordsResponse;

/**
 * 搜索。
 *
 * <p>返回类型以原实现的解析协程为准（ez0 byte 7/17/18、gz0 byte …、h11）：
 * 视频搜索返回原始 `search/item` 结构 {@link SearchVideoWorkResponse}；派生视图
 * {@link axtv.model.SearchResponse} 不是可反序列化结构，不能直接用于 callFor。
 * 综合搜索（`general/search/single`）在 model 中没有对应 record，保留 {@code String}。
 */
public final class SearchApi {

    private final DouyinApi api;

    public SearchApi(DouyinApi api) {
        this.api = api;
    }

    /**
     * 综合搜索。`/aweme/v1/web/general/search/single/`
     *
     * <p>参数结构同 m7998g，但 `search_channel=aweme_general`
     * （general 分支在反编译产物里只剩 Referer 映射，channel 取值按 m7998g 同构推断，见 NOTES「存疑」）。
     * <p>综合搜索的响应结构在 {@code axtv.model} 中没有对应 record（原实现无解析证据），
     * 因此保留 {@code String}，避免硬塞 {@link axtv.model.SearchResponse} 造成误解析。
     */
    public String generalSearch(String keyword, int offset, int count,
                                String sortType, String publishTime, String filterDuration,
                                String searchId) throws ApiException {
        Map<String, String> params = videoSearchParams("aweme_general", keyword, offset, count,
                sortType, publishTime, filterDuration, searchId);
        return api.call(ApiRequest.get("/aweme/v1/web/general/search/single/")
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .params(params)
                .referer(DouyinEndpoints.searchReferer(keyword, "general"))
                .sign()
                .uifidHeader());
    }

    /**
     * 视频搜索。`/aweme/v1/web/search/item/`
     *
     * <p>对应 n11.m7998g（逐字）：search_channel=aweme_video_web、enable_history=1、
     * search_range=0、search_source=normal_search、query_correct_type=1、is_filter_search=1、
     * from_group_id=""、need_filter_settings=(offset!=0?0:1)、list_type=single，末尾注入公共参数。
     */
    public SearchVideoWorkResponse videoSearch(String keyword, int offset, int count,
                                               String sortType, String publishTime, String filterDuration,
                                               String searchId) throws ApiException {
        Map<String, String> params = videoSearchParams("aweme_video_web", keyword, offset, count,
                sortType, publishTime, filterDuration, searchId);
        return api.callFor(ApiRequest.get(DouyinEndpoints.SEARCH_ITEM)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .params(params)
                // 原 m8067w0 传的是 m7996d(str, "video")，不是 general。
                .referer(DouyinEndpoints.searchReferer(keyword, "video"))
                .sign()
                .uifidHeader(), SearchVideoWorkResponse.class);
    }

    /**
     * 用户搜索。`/aweme/v1/web/discover/search`
     *
     * <p>对应 n11.m8040f：search_channel=aweme_user_web、search_source=switch_tab、
     * is_filter_search 由入参决定；粉丝数筛选条件以 JSON 写进 search_filter_value。
     */
    public SearchUserResponse userSearch(String keyword, int offset, int count, String searchId,
                                         boolean filterSearch, String fanFilterJson) throws ApiException {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("search_channel", "aweme_user_web");
        if (fanFilterJson != null && !fanFilterJson.isEmpty()) {
            params.put("search_filter_value", fanFilterJson);
        }
        params.put("keyword", keyword);
        params.put("search_source", "switch_tab");
        params.put("query_correct_type", "1");
        params.put("is_filter_search", filterSearch ? "1" : "0");
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));
        params.put("need_filter_settings", offset != 0 ? "0" : "1");
        if (!Texts.isBlank(searchId)) {
            params.put("search_id", searchId);
        }
        params.put("list_type", "single");
        api.injectCommonParams(params);
        return api.callFor(ApiRequest.get(DouyinEndpoints.SEARCH_DISCOVER)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .params(params)
                .referer(DouyinEndpoints.searchReferer(keyword, "user"))
                .sign()
                .uifidHeader(), SearchUserResponse.class);
    }

    /**
     * 直播搜索。`/aweme/v1/web/live/search/`
     *
     * <p>对应 n11.m8034a：search_channel=aweme_live、search_source=normal_search、is_filter_search=0。
     */
    public SearchLiveResponse liveSearch(String keyword, int offset, int count, String searchId) throws ApiException {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("search_channel", "aweme_live");
        params.put("keyword", keyword);
        params.put("search_source", "normal_search");
        params.put("query_correct_type", "1");
        params.put("is_filter_search", "0");
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));
        params.put("need_filter_settings", offset != 0 ? "0" : "1");
        if (!Texts.isBlank(searchId)) {
            params.put("search_id", searchId);
        }
        params.put("list_type", "single");
        api.injectCommonParams(params);
        return api.callFor(ApiRequest.get(DouyinEndpoints.SEARCH_LIVE)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .params(params)
                .referer(DouyinEndpoints.searchReferer(keyword, "live"))
                .sign()
                .uifidHeader(), SearchLiveResponse.class);
    }

    /**
     * 搜索联想。`/aweme/v1/web/search/sug/`
     *
     * <p>原实现只放两个参数 + m8013E0（只注入 msToken），source 固定 `aweme_video_web`。
     */
    public SearchSugResponse suggestions(String keyword) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.SEARCH_SUG)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .param("keyword", keyword)
                .param("source", "aweme_video_web")
                .referer(DouyinEndpoints.searchReferer(keyword, "general"))
                .msTokenOnly(), SearchSugResponse.class);
    }

    /** 搜索框下方推荐词。`/aweme/v1/web/api/suggest_words/` */
    public SuggestWordsResponse suggestWords(String keyword, int count) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.SUGGEST_WORDS)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .param("keyword", keyword)
                .param("count", String.valueOf(count))
                .param("business_id", "1")
                .param("list_type", "1")
                .param("search_channel", "aweme_general")
                .param("query_correct_type", "1")
                .referer(DouyinEndpoints.searchReferer(keyword, "general"))
                .msTokenOnly(), SuggestWordsResponse.class);
    }

    /** 热榜。`/aweme/v1/web/hot/search/list/`（无业务参数，只注入 msToken） */
    public HotSearchResponse hotSearch() throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.HOT_SEARCH)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .referer(DouyinEndpoints.SEARCH_REFERER)
                .msTokenOnly(), HotSearchResponse.class);
    }

    /** SEO 内链。`/aweme/v1/web/seo/inner/link/` */
    public SeoLinkResponse seoInnerLink(String awemeId) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.SEO_INNER_LINK)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .param("aweme_id", awemeId)
                .referer(DouyinEndpoints.SEARCH_REFERER)
                .commonParams()
                .sign(), SeoLinkResponse.class);
    }

    // ------------------------------------------------------------------ m7998g 逐字还原

    private Map<String, String> videoSearchParams(String channel, String keyword, int offset, int count,
                                                 String sortType, String publishTime,
                                                 String filterDuration, String searchId) {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("search_channel", channel);
        params.put("enable_history", "1");
        params.put("sort_type", sortType);
        params.put("publish_time", publishTime);
        params.put("filter_duration", filterDuration);
        params.put("search_range", "0");
        params.put("keyword", keyword);
        params.put("search_source", "normal_search");
        params.put("query_correct_type", "1");
        params.put("is_filter_search", "1");
        params.put("from_group_id", "");
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));
        params.put("need_filter_settings", offset != 0 ? "0" : "1");
        if (!Texts.isBlank(searchId)) {
            params.put("search_id", searchId);
        }
        params.put("list_type", "single");
        api.injectCommonParams(params);
        return params;
    }
}
