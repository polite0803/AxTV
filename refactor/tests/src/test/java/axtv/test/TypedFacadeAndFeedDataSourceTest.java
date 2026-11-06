/* 强类型门面 + 首页数据源的 JVM 用例。
 *
 * 用假 HttpEngine 喂固定 JSON，不发真实网络请求；重点覆盖：
 *  - 门面返回类型确实走 JsonCodec（字符串 → model）
 *  - 网络 / HTTP / 空体 / 解码 四类错误分别映射到 ApiException.Kind
 *  - FeedDataSource 对 status_code != 0 抛错且不返回空列表
 *  - UA 在请求头与签名入口的取值关系（取证用，见报告「请求头与 UA」）
 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import axtv.api.ApiException;
import axtv.api.ApiResponse;
import axtv.api.ClientIdentityBranch;
import axtv.api.CookieStore;
import axtv.api.DeviceProfile;
import axtv.api.DouyinApi;
import axtv.api.FeedApi;
import axtv.api.HttpEngine;
import axtv.api.InteractionApi;
import axtv.api.LiveApi;
import axtv.api.ModelJsonCodec;
import axtv.api.SignProvider;
import axtv.api.TicketGuardCredentials;
import axtv.api.UserApi;
import axtv.api.VideoApi;
import axtv.app.FeedDataSource;
import axtv.model.Aweme;
import axtv.model.CommentListResponse;
import axtv.model.FeedResponse;
import axtv.model.LiveFeedResponse;
import axtv.model.LiveFeedResponseHome;
import axtv.model.UserProfileResponse;
import axtv.model.WorkDetailResponse;

import org.junit.jupiter.api.Test;

class TypedFacadeAndFeedDataSourceTest {

    private static final String FEED_JSON = """
            {
              "status_code": 0,
              "has_more": "1",
              "max_cursor": "100",
              "aweme_list": [
                {"aweme_id": "7300000000000000001", "desc": "首页作品"}
              ]
            }
            """;

    // ---------------------------------------------------------------- 门面 typed

    @Test
    void feedTabReturnsTypedFeedResponse() throws Exception {
        Harness h = new Harness(FEED_JSON);
        FeedResponse response = new FeedApi(h.api()).tabFeed(10, 1, 1);

        assertEquals(0, response.statusCode());
        assertEquals(1, response.flatAwemes().size());
        assertEquals("7300000000000000001", response.flatAwemes().get(0).awemeId());
        assertTrue(h.http.lastUrl.contains("/aweme/v1/web/tab/feed/"));
    }

    // ---------------------------------------------------------------- tab/feed 参数取证

    @Test
    void tabFeedSendsOriginalBaseAndSpecificParams() throws Exception {
        Harness h = new Harness(FEED_JSON);
        new FeedApi(h.api()).tabFeed(10, 1, 0);
        String url = decodedQuery(h.http.lastUrl);

        // zy0.m12473a()：26 个桌面基础参数
        for (String base : List.of("device_platform=webapp", "aid=6383", "channel=channel_pc_web",
                "update_version_code=170400", "pc_client_type=1", "pc_libra_divert=Windows",
                "support_h265=1", "support_dash=1", "version_code=170400", "version_name=17.4.0",
                "cookie_enabled=true", "browser_language=zh-CN", "browser_platform=Win32",
                "browser_name=Chrome", "browser_version=147.0.0.0", "browser_online=true",
                "engine_name=Blink", "engine_version=147.0.0.0", "os_name=Windows", "os_version=10",
                "platform=PC", "downlink=10", "effective_type=4g", "round_trip_time=50")) {
            assertTrue(url.contains(base), "tab/feed 必须带原版基础参数: " + base);
        }
        // n11.m8036b0：tab 专用参数
        for (String specific : List.of("filterGids=", "tag_id=", "share_aweme_id=", "live_insert_type=",
                "count=10", "refresh_index=1", "video_type_select=1", "pull_type=0", "min_window=0",
                "free_right=0", "view_count=0", "plug_block=0", "ug_source=", "creative_id=",
                "webcast_sdk_version=170400", "webcast_version_code=170400")) {
            assertTrue(url.contains(specific), "tab/feed 必须带原版专用参数: " + specific);
        }
        // 首页短 JSON
        assertTrue(url.contains("\"is_client\":false"), "首页应带 aweme_pc_rec_raw_data 短 JSON");
        assertFalse(url.contains("videoPrefer"), "refresh_index=1 不应带 videoPrefer");
        // 屏幕/CPU/内存（无 cookie 时用兜底）
        assertTrue(url.contains("screen_width=2560"));
        assertTrue(url.contains("cpu_core_num=24"));
        assertTrue(url.contains("device_memory=8"));
        // 原版 tab/feed 不注入 msToken（走的是 m8011D0 之外的独立路径）
        assertFalse(url.contains("msToken="), "tab/feed 原版不发送 msToken");
    }

    @Test
    void tabFeedLoadMoreCarriesVideoPreferAndViewCount() throws Exception {
        Harness h = new Harness(FEED_JSON);
        new FeedApi(h.api()).tabFeed(10, 2, 2, List.of("7300000000000000001", "7300000000000000002"));
        String url = decodedQuery(h.http.lastUrl);

        assertTrue(url.contains("refresh_index=2"));
        assertTrue(url.contains("view_count=10"), "view_count 应为 (refreshIndex-1)*count");
        assertTrue(url.contains("videoPrefer"), "加载更多应带 videoPrefer 长 JSON");
        assertTrue(url.contains("halfMin"), "videoPrefer 应含 halfMin");
        assertTrue(url.contains("7300000000000000001,7300000000000000002"),
                "halfMin/min 应填入最近 aweme id（逗号拼接）");
    }

    @Test
    void tabFeedSendsGlobalWidOnlyForNumericUserUniqueId() throws Exception {
        Harness h = new Harness(FEED_JSON);
        h.cookies.uniqueId = "1234567890";
        new FeedApi(h.api()).tabFeed(10, 1, 1);
        String url = decodedQuery(h.http.lastUrl);
        assertTrue(url.contains("globalwid=1234567890"));
        assertTrue(url.contains("webid=1234567890"));

        Harness anonymous = new Harness(FEED_JSON);
        new FeedApi(anonymous.api()).tabFeed(10, 1, 1);
        assertFalse(decodedQuery(anonymous.http.lastUrl).contains("globalwid="));
    }

    @Test
    void liveFeedSendsOriginalWebcastParams() throws Exception {
        Harness h = new Harness("{\"status_code\":0,\"data\":[]}");
        new LiveApi(h.api()).feed(10, 0, "");
        String url = decodedQuery(h.http.lastUrl);

        for (String expected : List.of("need_map=1", "liveid=1", "is_draw=1", "inner_from_drawer=0",
                "custom_count=10", "action=load_more", "action_type=loadmore",
                "enter_source=web_homepage_hot_web_live_card", "source_key=web_homepage_hot_web_live_card",
                "is_ssr=true", "maxtime=0", "device_platform=web", "app_name=douyin_web", "live_id=1",
                "enter_from=web_live", "tz_name=Asia/Shanghai", "msToken=")) {
            assertTrue(url.contains(expected), "webcast/feed 必须带原版参数: " + expected);
        }
    }

    @Test
    void liveFeedParsesRealHomeShapeAndFollowUsesLegacyShape() throws Exception {
        // /webcast/feed/ 真身是 {data:[{rid, data:{room...}}]}（webcast 实际返回，非 {data:{data:[]}}）。
        Harness feed = new Harness("""
                {"status_code":0,"data":[{"type":1,"rid":"r1",
                  "data":{"id_str":"7300","web_rid":"7300","title":"t1","status":2,
                          "user_count_str":"1.2万"}}]}
                """);
        LiveFeedResponseHome home = new LiveApi(feed.api()).feed(10, 0, "");
        assertEquals(0, home.statusCode());
        assertTrue(feed.http.lastUrl.contains("/webcast/feed/"));
        assertEquals("t1", home.data().get(0).data().title());
        // 原版 LiveFeedDataHome 的 descriptor 只有 web_rid/data，外层 rid 是未知键被丢弃。
        assertEquals("7300", home.data().get(0).data().webRid());

        // 关注流是旧结构 {data:{data:[{web_rid, room:{...}}]}}，解析器不同。
        Harness follow = new Harness("""
                {"status_code":0,"data":{"data":[{"web_rid":"7300","room":{"title":"t2"}}]}}
                """);
        LiveFeedResponse legacy = new LiveApi(follow.api()).followFeed("scene");
        assertTrue(follow.http.lastUrl.contains("/webcast/web/feed/follow/"));
        assertEquals("t2", legacy.data().data().get(0).room().title());
    }

    @Test
    void videoDetailReturnsTypedWorkDetail() throws Exception {
        Harness h = new Harness("{\"status_code\":0,\"aweme_detail\":{\"aweme_id\":\"42\"}}");
        WorkDetailResponse detail = new VideoApi(h.api()).detail("42");

        assertEquals(0, detail.statusCode());
        assertEquals("42", detail.awemeDetail().awemeId());
        assertTrue(h.http.lastUrl.contains("/aweme/v1/web/aweme/detail/"));
    }

    @Test
    void commentsReturnTypedCommentList() throws Exception {
        Harness h = new Harness("""
                {"status_code":0,"comments":[{"cid":"1","text":"hi"}],"has_more":1,"cursor":"9"}
                """);
        CommentListResponse comments = new InteractionApi(h.api()).comments("42", 0, 10);

        assertEquals(1, comments.comments().size());
        assertEquals("1", comments.comments().get(0).cid());
        assertTrue(comments.hasMorePages());
    }

    // ---------------------------------------------------------------- 错误语义

    @Test
    void decodeFailureIsDecodeKind() {
        Harness h = new Harness("not-json");
        ApiException ex = assertThrows(ApiException.class,
                () -> new FeedApi(h.api()).tabFeed(10, 1, 1));

        assertEquals(ApiException.Kind.DECODE, ex.kind());
        assertTrue(ex.getMessage().contains("/aweme/v1/web/tab/feed/"));
    }

    @Test
    void emptyBodyIsEmptyBodyKind() {
        Harness h = new Harness("");
        ApiException ex = assertThrows(ApiException.class,
                () -> new FeedApi(h.api()).tabFeed(10, 1, 1));

        assertEquals(ApiException.Kind.EMPTY_BODY, ex.kind());
        assertEquals(200, ex.httpStatus());
    }

    @Test
    void ioFailureIsNetworkKind() {
        Harness h = new Harness("{}");
        h.http.failure = new IOException("offline");

        ApiException ex = assertThrows(ApiException.class,
                () -> new FeedApi(h.api()).tabFeed(10, 1, 1));

        assertEquals(ApiException.Kind.NETWORK, ex.kind());
    }

    @Test
    void httpErrorIsHttpStatusKind() {
        Harness h = new Harness("boom");
        h.http.statusCode = 500;

        ApiException ex = assertThrows(ApiException.class,
                () -> new FeedApi(h.api()).tabFeed(10, 1, 1));

        assertEquals(ApiException.Kind.HTTP_STATUS, ex.kind());
        assertEquals(500, ex.httpStatus());
    }

    // ---------------------------------------------------------------- FeedDataSource

    @Test
    void loadFeedReturnsAwemesOnSuccess() throws Exception {
        Harness h = new Harness(FEED_JSON);
        FeedDataSource.FeedPage page = new FeedDataSource(new FeedApi(h.api())).loadFeed(0);
        List<Aweme> awemes = page.awemes();

        assertEquals(1, awemes.size());
        assertEquals("7300000000000000001", awemes.get(0).awemeId());
        assertFalse(awemes.isEmpty());
        assertTrue(page.hasMore(), "has_more=1 时应透传 hasMore");
    }

    @Test
    void loadFeedMapsLoginRequiredStatus() {
        Harness h = new Harness("{\"status_code\":8}");
        ApiException ex = assertThrows(ApiException.class,
                () -> new FeedDataSource(new FeedApi(h.api())).loadFeed(0));

        assertEquals(ApiException.Kind.LOGIN_REQUIRED, ex.kind());
        assertEquals(8, ex.apiStatus());
    }

    @Test
    void loadFeedMapsRateLimitedStatus() {
        Harness h = new Harness("{\"status_code\":2154}");
        ApiException ex = assertThrows(ApiException.class,
                () -> new FeedDataSource(new FeedApi(h.api())).loadFeed(0));

        assertEquals(ApiException.Kind.RATE_LIMITED, ex.kind());
        assertEquals(2154, ex.apiStatus());
    }

    @Test
    void loadFeedMapsOtherBusinessStatus() {
        Harness h = new Harness("{\"status_code\":77}");
        ApiException ex = assertThrows(ApiException.class,
                () -> new FeedDataSource(new FeedApi(h.api())).loadFeed(0));

        assertEquals(ApiException.Kind.API_STATUS, ex.kind());
        assertEquals(77, ex.apiStatus());
    }

    @Test
    void loadFeedPropagatesDecodeFailureInsteadOfEmptyList() {
        Harness h = new Harness("[]");
        ApiException ex = assertThrows(ApiException.class,
                () -> new FeedDataSource(new FeedApi(h.api())).loadFeed(0));

        assertEquals(ApiException.Kind.DECODE, ex.kind());
    }

    @Test
    void loadFeedPropagatesNetworkFailureInsteadOfEmptyList() {
        Harness h = new Harness("{}");
        h.http.failure = new IOException("no route");

        ApiException ex = assertThrows(ApiException.class,
                () -> new FeedDataSource(new FeedApi(h.api())).loadFeed(0));

        assertEquals(ApiException.Kind.NETWORK, ex.kind());
    }

    @Test
    void loadFeedAllowsLegitimateEmptyList() throws Exception {
        Harness h = new Harness("{\"status_code\":0,\"aweme_list\":[]}");
        FeedDataSource.FeedPage page = new FeedDataSource(new FeedApi(h.api())).loadFeed(0);
        List<Aweme> awemes = page.awemes();

        assertTrue(awemes.isEmpty(), "status_code==0 且服务端确实无作品时，空列表是合法结果");
        assertFalse(page.hasMore(), "空页且 has_more 缺失时不应继续翻页");
    }

    @Test
    void loadFeedFirstPageUsesOriginalRefreshPullTypeAndPageSize() throws Exception {
        Harness h = new Harness(FEED_JSON);
        new FeedDataSource(new FeedApi(h.api())).loadFeed(0);
        String url = decodedQuery(h.http.lastUrl);

        assertTrue(url.contains("count=15"), "首页首屏与原版一致，取 15 条");
        assertTrue(url.contains("refresh_index=1"));
        assertTrue(url.contains("pull_type=0"), "首屏 pull_type 必须是 0（原版无 1）");
        assertTrue(url.contains("view_count=0"));
        assertFalse(url.contains("videoPrefer"), "首屏用短 JSON");
    }

    @Test
    void loadFeedLoadMoreUsesNextRefreshIndexAndRecentIds() throws Exception {
        Harness h = new Harness(FEED_JSON);
        new FeedDataSource(new FeedApi(h.api())).loadFeed(1, List.of("111", "222"));
        String url = decodedQuery(h.http.lastUrl);

        assertTrue(url.contains("refresh_index=2"), "第一页之后加载更多必须从 refresh_index=2 开始");
        assertTrue(url.contains("pull_type=2"));
        assertTrue(url.contains("view_count=15"), "view_count 应为 (refreshIndex-1)*count");
        assertTrue(url.contains("videoPrefer"), "加载更多应带 videoPrefer 长 JSON");
        assertTrue(url.contains("111,222"), "最近展示作品 id 必须回传给推荐接口");
    }

    // ---------------------------------------------------------------- 参数编码 / tab 签名形态

    @Test
    void queryIsUrlEncodedForAwemePcRecRawData() throws Exception {
        Harness h = new Harness(FEED_JSON);
        new FeedApi(h.api()).tabFeed(10, 1, 1);
        String raw = h.http.lastUrl;

        // 原版 URL 由 yy0 case 0 生成：花括号/引号/逗号都必须转义，否则服务端判 Sign Invalid
        assertFalse(raw.contains("{\"is_client\""), "URL 里的 JSON 参数必须 URL 编码");
        assertTrue(raw.contains("aweme_pc_rec_raw_data=%7B%22is_client%22"), "应编码为 %7B%22…");
        assertTrue(raw.contains("halfMin") || raw.contains("aweme_pc_rec_raw_data"), "参数名本身不编码");
    }

    @Test
    void tabFeedDoesNotSendABogusButSignsWithUifidOnlyFromCookie() throws Exception {
        Harness h = new Harness(FEED_JSON);
        h.cookies.jar.put("UIFID", "real-uifid");
        new FeedApi(h.api()).tabFeed(10, 1, 1);

        String url = decodedQuery(h.http.lastUrl);
        assertFalse(url.contains("a_bogus="), "tab/feed 原版不算 a_bogus");
        assertNull(h.signer.lastUserAgent, "tab/feed 不触发 a_bogus 签名");
        assertTrue(url.contains("uifid=real-uifid"), "有 UIFID 时应带 uifid");
        assertTrue(url.contains("timestamp="), "签名链必须带 timestamp");
        assertTrue(url.contains("x-secsdk-web-signature="), "有 UIFID 时必须带 secsdk 签名");
    }

    @Test
    void tabFeedSkipsSecsdkSignatureWhenOnlyUifidTempExists() throws Exception {
        Harness h = new Harness(FEED_JSON);
        h.cookies.jar.put("UIFID_TEMP", "temp-uifid");
        new FeedApi(h.api()).tabFeed(10, 1, 1);

        String url = decodedQuery(h.http.lastUrl);
        // UIFID_TEMP 只用于 uifid 请求头/cookie，不参与 secsdk 签名（原 m8036b0:2846-2857）
        assertFalse(url.contains("x-secsdk-web-signature="), "只有 UIFID_TEMP 时原版不带 secsdk 签名");
        assertFalse(url.contains("uifid=temp-uifid"), "UIFID_TEMP 不应写进 query 的 uifid");
        assertTrue(url.contains("timestamp="), "即使不签名也写 timestamp");
    }

    // ---------------------------------------------------------------- UA / 签名取证
    @Test
    void webBranchUaHeaderAndSignatureAreIdentical() throws Exception {
        Harness h = new Harness(FEED_JSON, ClientIdentityBranch.WEB);
        // tab/feed 原版不算 a_bogus，取一个走 A_BOGUS 的 Web 端点来验证 UA 一致性。
        new VideoApi(h.api()).related("42", 10, 1);

        String headerUa = h.http.lastHeaders.get("user-agent");
        assertTrue(headerUa.startsWith("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"), "Web 接口分支应使用 Chrome UA");
        assertEquals(headerUa, h.signer.lastUserAgent,
                "Web 接口分支 a_bogus 的 userAgent 必须与 User-Agent 头逐字节一致");
        assertTrue(h.http.lastHeaders.containsKey("sec-ch-ua"), "Web 接口分支应携带浏览器 client hints");
    }

    @Test
    void webcastBranchUaHeaderAndSignatureAreIdentical() throws Exception {
        Harness h = new Harness("{\"status_code\":0}", ClientIdentityBranch.WEBCAST);
        LiveFeedResponseHome response = new LiveApi(h.api()).feed(10, 0, "");
        assertEquals(0, response.statusCode());

        String headerUa = h.http.lastHeaders.get("user-agent");
        assertTrue(headerUa.contains("QQBrowser/19.7.6764.400"), "Webcast 分支应使用 QQBrowser UA");
        assertEquals(headerUa, h.signer.lastUserAgent,
                "Webcast 分支 a_bogus 的 userAgent 必须与 User-Agent 头逐字节一致");
    }

    @Test
    void appFeedBranchUaHeaderAndSignatureAreIdenticalAndNoDesktopHints() throws Exception {
        Harness h = new Harness("{\"status_code\":0,\"aweme_list\":[]}", ClientIdentityBranch.APP_FEED);
        FeedResponse response = new FeedApi(h.api()).mobileFeed(10, 0);

        assertEquals(0, response.statusCode());
        String headerUa = h.http.lastHeaders.get("user-agent");
        assertTrue(headerUa.startsWith("com.ss.android.ugc.aweme/391000"), "App Feed 分支应使用 App UA");
        assertEquals(headerUa, h.signer.lastUserAgent,
                "App Feed 分支 a_bogus 的 userAgent 必须与 User-Agent 头逐字节一致");

        for (String desktopHeader : List.of("sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform",
                "sec-fetch-dest", "sec-fetch-mode", "sec-fetch-site", "priority", "referer")) {
            assertFalse(h.http.lastHeaders.containsKey(desktopHeader),
                    "App Feed 分支不应发送桌面指纹头: " + desktopHeader);
        }
    }

    @Test
    void webBranchUserCenterSignatureMatchesHeader() throws Exception {
        Harness h = new Harness("{\"status_code\":0,\"user\":{\"uid\":\"1\"}}", ClientIdentityBranch.WEB);
        UserProfileResponse profile = new UserApi(h.api()).profileSelf();
        assertEquals(0, profile.statusCode());

        String headerUa = h.http.lastHeaders.get("user-agent");
        assertEquals(headerUa, h.signer.lastUserAgent,
                "User-Center 签名链也必须与 User-Agent 头同值");
    }

    // ---------------------------------------------------------------- 测试替身

    /** URL 里的 query 已按原版逐项 URL 编码，断言前先解码回可读形态。 */
    private static String decodedQuery(String url) {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q) + '?'
                + java.net.URLDecoder.decode(url.substring(q + 1), StandardCharsets.UTF_8);
    }

    private static final class Harness {
        final FakeHttpEngine http;
        final FakeSigner signer = new FakeSigner();
        final FakeCookieStore cookies = new FakeCookieStore();
        private final DouyinApi api;

        Harness(String body) {
            this(body, ClientIdentityBranch.WEB);
        }

        Harness(String body, ClientIdentityBranch branch) {
            http = new FakeHttpEngine(body);
            api = new DouyinApi(http, signer, cookies,
                    new DeviceProfile("14", "Pixel 7", 34), new ModelJsonCodec(), branch);
        }

        DouyinApi api() {
            return api;
        }
    }

    private static final class FakeHttpEngine implements HttpEngine {
        String lastUrl;
        Map<String, String> lastHeaders = Map.of();
        int statusCode = 200;
        IOException failure;
        private final String body;

        FakeHttpEngine(String body) {
            this.body = body;
        }

        @Override
        public ApiResponse execute(String method, String url, Map<String, String> headers,
                                   byte[] body, String contentType, boolean followRedirects) throws IOException {
            lastUrl = url;
            lastHeaders = new LinkedHashMap<>(headers);
            if (failure != null) {
                throw failure;
            }
            return new ApiResponse(statusCode,
                    this.body.getBytes(StandardCharsets.UTF_8), Map.of());
        }
    }

    private static final class FakeSigner implements SignProvider {
        String lastUserAgent;

        @Override
        public String signABogus(String path, String query, String body, String userAgent) {
            lastUserAgent = userAgent;
            return "test-a-bogus";
        }

        @Override
        public Map<String, String> ticketGuardHeaders(String path, TicketGuardCredentials credentials) {
            return Map.of();
        }
    }

    private static final class FakeCookieStore implements CookieStore {
        private final Map<String, String> jar = new LinkedHashMap<>();
        String uniqueId = "";

        @Override
        public String cookieHeader() {
            return "";
        }

        @Override
        public void setCookieHeader(String cookieHeader) {
            // no-op
        }

        @Override
        public Map<String, String> cookies() {
            return jar;
        }

        @Override
        public String persistentDeviceId(String key) {
            return "1000000000000000";
        }

        @Override
        public String userUniqueId() {
            return uniqueId;
        }

        @Override
        public void setUserUniqueId(String value) {
            uniqueId = value;
        }

        @Override
        public String selfUserId() {
            return "";
        }

        @Override
        public void applySelfUserId(String userId) {
            // no-op
        }

        @Override
        public TicketGuardCredentials ticketGuard() {
            return null;
        }
    }
}
