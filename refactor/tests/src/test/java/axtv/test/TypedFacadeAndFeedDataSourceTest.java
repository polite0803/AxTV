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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
import axtv.app.PlaybackFeedbackReporter;
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

    /**
     * 「换一批」（再点一次已选中的推荐 Tab）：pull_type 仍是 0，但 refresh_index 必须跟着
     * 本地持久化进度往前跳。原版 kq5.f12441c 每次请求自增；这里若退回 1，
     * 服务端每次都按冷启动第一页返回同一批——即「首页永远是这几个视频」。
     */
    @Test
    void refreshKeepsRefreshPullTypeButAdvancesRefreshIndex() throws Exception {
        Harness h = new Harness(FEED_JSON);
        new FeedDataSource(new FeedApi(h.api())).refresh(3, List.of("111", "222"));
        String url = decodedQuery(h.http.lastUrl);

        assertTrue(url.contains("pull_type=0"), "刷新不是加载更多，pull_type 必须是 0");
        assertTrue(url.contains("refresh_index=3"), "刷新必须带上持久化后的序号");
        assertTrue(url.contains("view_count=30"), "view_count 应为 (refreshIndex-1)*count");
        assertTrue(url.contains("videoPrefer"), "序号已前进，必须带 videoPrefer 回传已展示 id");
        assertTrue(url.contains("111,222"), "换一批要把上一批 id 交给服务端排除");
    }

    /** 进度缺失（老配置/首次安装）时刷新必须退回序号 1，不能写出 0 或负数。 */
    @Test
    void refreshClampsRefreshIndexToOne() throws Exception {
        Harness h = new Harness(FEED_JSON);
        new FeedDataSource(new FeedApi(h.api())).refresh(0, List.of());
        String url = decodedQuery(h.http.lastUrl);

        assertTrue(url.contains("refresh_index=1"));
        assertTrue(url.contains("pull_type=0"));
        assertTrue(url.contains("view_count=0"));
        assertFalse(url.contains("videoPrefer"), "序号 1 是冷启动形态，用短 JSON");
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
        FeedResponse response = new FeedApi(h.api()).mobileFeed(1, List.of());

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
    void mobileFeedSendsOriginalNineteenParamsAndNoMaxCursor() throws Exception {
        Harness h = new Harness("{\"status_code\":0,\"aweme_list\":[]}", ClientIdentityBranch.WEB);
        new FeedApi(h.api()).mobileFeed(2, List.of("7300000000000000001", "7300000000000000002"));
        String url = decodedQuery(h.http.lastUrl);

        // n11.m8030V:2257-2299 的 19 键 + presented_ids/filterGids（2322-2326）
        for (String expected : List.of(
                "device_platform=android", "aid=1128", "channel=douyin",
                "version_code=391000", "version_name=39.10.0",
                "device_type=Pixel 7", "os_version=14", "os_api=34", "language=zh",
                "count=10", "refresh_index=2",
                "device_id=1000000000000000", "install_id=1000000000000000",
                "cookie_enabled=true", "downlink=10",
                "cold_start=0", "refer=1", "pull_type=0")) {
            assertTrue(url.contains(expected), "移动推荐必须带原版参数: " + expected);
        }
        assertTrue(url.contains("aweme_rec_raw_data="));
        assertTrue(url.contains("filterGids=7300000000000000001,7300000000000000002"));
        assertTrue(url.contains("presented_ids=7300000000000000001,7300000000000000002"));
        // 非冷启动：videoPrefer 的 like/halfMin/min 三组同值（n11.java:2251-2256）
        assertTrue(url.contains("\"like\":[\"7300000000000000001,7300000000000000002\""),
                "aweme_rec_raw_data 应带 videoPrefer.like 同值串");
        // 原版没有这两个键（旧实现的 max_cursor/iid 是错的）
        assertFalse(url.contains("max_cursor"), "移动推荐原版没有 max_cursor");
        assertFalse(url.contains("iid="), "移动推荐用 install_id，不是 iid");
    }

    @Test
    void mobileFeedColdStartCarriesColdStartFlagsAndShortRawData() throws Exception {
        Harness h = new Harness("{\"status_code\":0,\"aweme_list\":[]}", ClientIdentityBranch.WEB);
        new FeedApi(h.api()).mobileFeed(1, List.of("ignored-on-cold-start"));
        String url = decodedQuery(h.http.lastUrl);

        // kq5.m7280r：refreshIndex==1 → cold_start=1、pull_type=2、id 列表传空（n11.java:2296-2298）
        assertTrue(url.contains("cold_start=1"));
        assertTrue(url.contains("pull_type=2"));
        assertFalse(url.contains("presented_ids"), "冷启动不带 presented_ids");
        assertFalse(url.contains("videoPrefer"), "冷启动用短 JSON（n11.java:2243）");
    }

    @Test
    void mobileFeedUsesAppUaEvenWhenStartupBranchIsWeb() throws Exception {
        Harness h = new Harness("{\"status_code\":0,\"aweme_list\":[]}", ClientIdentityBranch.WEB);
        new FeedApi(h.api()).mobileFeed(1, List.of());
        String headerUa = h.http.lastHeaders.get("user-agent");

        // lz0.java:76：移动形态请求头 UA 恒为 m8001p0()（App UA），与启动身份分支无关
        assertTrue(headerUa.startsWith("com.ss.android.ugc.aweme/391000"),
                ".mobile() 请求必须带 App UA，实际为: " + headerUa);
        assertEquals(headerUa, h.signer.lastUserAgent, "签名 UA 必须与头同值");
        assertEquals("*/*", h.http.lastHeaders.get("accept"));
        assertEquals("zh-CN,zh;q=0.9", h.http.lastHeaders.get("accept-language"));
        assertFalse(h.http.lastHeaders.containsKey("referer"), "lz0.java:76-79 只有 4 个头，无 referer");
        assertFalse(h.http.lastHeaders.containsKey("sec-ch-ua"), "移动形态不得带桌面 client hints");
        assertTrue(h.http.lastUrl.startsWith("https://aweme.snssdk.com/aweme/v2/feed/"));
    }

    @Test
    void followFeedSendsOriginalParams() throws Exception {
        Harness h = new Harness("{\"status_code\":0,\"data\":[]}");
        new FeedApi(h.api()).followFeed(20, 0, "1");
        String url = decodedQuery(h.http.lastUrl);

        // n11.m8022N:1581-1588 专用参数 + 26 个桌面基础参数（zy0.m12473a）
        for (String expected : List.of("cursor=0", "level=1", "count=20", "pull_type=0",
                "aweme_ids=", "room_ids=", "webcast_sdk_version=170400", "webcast_version_code=170400",
                "device_platform=webapp", "aid=6383")) {
            assertTrue(url.contains(expected), "关注流必须带原版参数: " + expected);
        }
        assertTrue(h.http.lastUrl.contains("/aweme/v1/web/follow/feed/"));
    }

    @Test
    void loadFollowPrefersDataAndDerivesNextCursor() throws Exception {
        Harness h = new Harness("""
                {"status_code":0,"cursor":"500","max_cursor":"900",
                 "data":[{"aweme":{"aweme_id":"7300000000000000009","aweme_type":1}}]
                }""");
        FeedDataSource.FollowFeedPage page =
                new FeedDataSource(new FeedApi(h.api())).loadFollow(0);

        // kq5.java:1125-1137：data[].aweme 非空时直接用，不做可播过滤（aweme_type=1 也保留）
        assertEquals(1, page.awemes().size());
        // kq5.java:1149-1151：cursor>0 优先，否则 maxCursor
        assertEquals(500L, page.nextCursor());
    }

    @Test
    void loadFollowFallsBackToPlayableAwemeListAndMaxCursor() throws Exception {
        Harness h = new Harness("""
                {"status_code":0,"cursor":"0","max_cursor":"900","aweme_list":[
                  {"aweme_id":"1","aweme_type":0,"video":{"play_addr":{"url_list":["https://a/1.mp4"]}}},
                  {"aweme_id":"2","aweme_type":1}
                ]}""");
        FeedDataSource.FollowFeedPage page =
                new FeedDataSource(new FeedApi(h.api())).loadFollow(0);

        // data 缺失 → filterPrimaryPlayableAwemes(aweme_list)：只留普通视频且有播放地址
        assertEquals(1, page.awemes().size());
        assertEquals("1", page.awemes().get(0).awemeId());
        assertEquals(900L, page.nextCursor(), "cursor=0 时回退 max_cursor");
    }

    @Test
    void loadFollowUsesOriginalErrorWording() {
        ApiException login = assertThrows(ApiException.class, () ->
                new FeedDataSource(new FeedApi(new Harness("{\"status_code\":8}").api())).loadFollow(0));
        assertEquals("需要登录才能查看关注内容，请在设置中配置完整登录 Cookie", login.getMessage());
        assertEquals(ApiException.Kind.LOGIN_REQUIRED, login.kind());

        ApiException limited = assertThrows(ApiException.class, () ->
                new FeedDataSource(new FeedApi(new Harness("{\"status_code\":2154}").api())).loadFollow(0));
        assertEquals("请求频率过高，请稍后再试", limited.getMessage());

        ApiException other = assertThrows(ApiException.class, () ->
                new FeedDataSource(new FeedApi(new Harness("{\"status_code\":7}").api())).loadFollow(0));
        assertEquals("服务器返回错误 (7)", other.getMessage(), "n11.java:1556-1563 原文案");
    }

    @Test
    void loadMobileKeepsOnlyPrimaryNotEmptyIds() throws Exception {
        Harness h = new Harness("""
                {"status_code":0,"has_more":true,"aweme_list":[
                  {"aweme_id":"7300000000000000001","aweme_type":0},
                  {"aweme_id":"","aweme_type":0},
                  {"aweme_id":"7300000000000000003","aweme_type":2}
                ]}""", ClientIdentityBranch.WEB);
        FeedDataSource.FeedPage page = new FeedDataSource(new FeedApi(h.api()))
                .loadMobile(2, List.of("7300000000000000001"));

        // kq5.m7280r:1503-1510：id 非空 + isPrimaryAweme 双条件
        assertEquals(1, page.awemes().size());
        assertEquals("7300000000000000001", page.awemes().get(0).awemeId());
        assertTrue(page.hasMore());
        assertTrue(decodedQuery(h.http.lastUrl).contains("presented_ids=7300000000000000001"));
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
    // ---------------------------------------------------------------- 播放行为上报（越看越准）

    @Test
    void statsSendsOriginalFormFields() throws Exception {
        Harness h = new Harness("{}");
        new VideoApi(h.api()).stats("7300000000000000001", 0);

        // n11.java:4635 的表单体逐字四项：aweme_type / item_id / play_delta / source。
        // 键名用集合相等而不是 contains：contains("item_id=") 会被 xx_item_id= 蒙过去。
        assertEquals(Set.of("aweme_type", "item_id", "play_delta", "source"),
                formKeys(h.http.lastForm), "stats 表单体键名集合: " + h.http.lastForm);
        Map<String, String> form = formFields(h.http.lastForm);
        assertEquals("0", form.get("aweme_type"), h.http.lastForm);
        assertEquals("7300000000000000001", form.get("item_id"), "stats 的作品 id 键名是 item_id");
        assertEquals("1", form.get("play_delta"), "play_delta 原版是常量 1，不是观看秒数");
        assertEquals("0", form.get("source"), "source 原版是常量 0");
        assertTrue(h.http.lastUrl.contains("/aweme/v2/web/aweme/stats/"));
    }

    @Test
    void historyWriteSendsAuthorIdAndAwemeIdOnly() throws Exception {
        Harness h = new Harness("{}");
        new VideoApi(h.api()).historyWrite("7300000000000000001", "100000000001");

        // n11.java:620：sv2.m10406K(author_id, aweme_id) 只有两项。同样按键名集合判等，
        // 这样自造前缀（xx_author_id）和多余的 enter_from/publish_time/enable_history/app_name
        // 都会红，而 contains 系列断言两个都放行。
        assertEquals(Set.of("author_id", "aweme_id"),
                formKeys(h.http.lastForm), "历史写入只该有 author_id + aweme_id: " + h.http.lastForm);
        Map<String, String> form = formFields(h.http.lastForm);
        assertEquals("100000000001", form.get("author_id"), h.http.lastForm);
        assertEquals("7300000000000000001", form.get("aweme_id"), h.http.lastForm);
        assertTrue(h.http.lastUrl.contains("/aweme/v1/web/history/write/"));
    }

    @Test
    void statsAndHistoryWriteUseAbogusWithoutTicketGuardChain() throws Exception {
        // m8004t(..., 56) → nz0.java:121 只算 a_bogus；nz0.java:173 的 ticket-guard 分支不进入
        // （56 & 32 != 0），所以两处都不该挂 secsdk 用户中心签名链。
        Harness stats = new Harness("{}");
        new VideoApi(stats.api()).stats("7300000000000000001", 0);
        assertTrue(decodedQuery(stats.http.lastUrl).contains("a_bogus="), "stats 必须带 a_bogus");
        assertFeedbackRequestClean(stats);

        Harness history = new Harness("{}");
        new VideoApi(history.api()).historyWrite("7300000000000000001", "100000000001");
        assertTrue(decodedQuery(history.http.lastUrl).contains("a_bogus="), "写历史必须带 a_bogus");
        assertFeedbackRequestClean(history);
    }

    private static void assertFeedbackRequestClean(Harness h) {
        for (Map.Entry<String, String> header : h.http.lastHeaders.entrySet()) {
            assertFalse(header.getKey().toLowerCase(Locale.ROOT).startsWith("bd-ticket-guard"),
                    "上报接口不需要互动凭据，不应带 " + header.getKey());
            assertFalse("x-secsdk-web-signature".equalsIgnoreCase(header.getKey()),
                    "上报接口不挂 secsdk 签名头");
        }
        String url = decodedQuery(h.http.lastUrl);
        assertFalse(url.contains("timestamp="), "用户中心签名链的 timestamp 不该出现在上报请求里");
        assertFalse(url.contains("x-secsdk-web-signature="), "同上，secsdk 签名参数也不该出现");
    }

    @Test
    void reporterSendsBothLegsOncePerAweme() throws Exception {
        Harness h = new Harness("{}");
        PlaybackFeedbackReporter reporter = new PlaybackFeedbackReporter(new VideoApi(h.api()));

        reporter.reportWatched("7300000000000000001", 0, "100000000001");
        assertEquals(2, h.http.requestCount, "一次观看 = 埋点 + 写历史两条请求");
        assertTrue(h.http.lastUrl.contains("/aweme/v1/web/history/write/"), "后发的那条是写历史");

        PlaybackFeedbackReporter.Outcome again = reporter.reportWatched("7300000000000000001", 0, "100000000001");
        assertTrue(again.deduped(), "同一条作品在进程内只报一次（kq5.f12447i 语义）");
        assertEquals(2, h.http.requestCount, "重复上报不得再打网络");

        reporter.reportWatched("7300000000000000002", 68, "100000000002");
        assertEquals(4, h.http.requestCount, "另一条作品要各自上报");
        assertEquals("7300000000000000002", formFields(h.http.lastForm).get("aweme_id"), h.http.lastForm);
    }

    @Test
    void reporterSkipsBlankAwemeId() throws Exception {
        Harness h = new Harness("{}");
        PlaybackFeedbackReporter reporter = new PlaybackFeedbackReporter(new VideoApi(h.api()));

        assertTrue(reporter.reportWatched("   ", 0, "100000000001").deduped(), "空 id 不发请求");
        assertTrue(reporter.reportWatched(null, 0, "").deduped());
        assertEquals(0, h.http.requestCount);
    }

    @Test
    void reporterReportsEachLegFailureAndDoesNotRetry() throws Exception {
        Harness h = new Harness("{}");
        h.http.failure = new IOException("network down");
        PlaybackFeedbackReporter reporter = new PlaybackFeedbackReporter(new VideoApi(h.api()));

        PlaybackFeedbackReporter.Outcome failed = reporter.reportWatched("7300000000000000001", 0, "100000000001");
        assertTrue(failed.hasFailure(), "两条腿的异常都要进返回值，不能伪装成没发生");
        assertTrue(failed.statsError().contains("NETWORK"), failed.statsError());
        assertTrue(failed.historyError().contains("NETWORK"), failed.historyError());
        assertEquals(2, h.http.requestCount, "一条腿失败不牵连另一条：两条都要真发出去");

        // 原版把 id 在发送前就放进 f12447i（kq5.java:390），所以失败也不重试：
        // 网络恢复后同一条仍然不发请求。
        h.http.failure = null;
        assertTrue(reporter.reportWatched("7300000000000000001", 0, "100000000001").deduped());
        assertEquals(2, h.http.requestCount, "失败过的那条不得在恢复后补报");
    }

    private static String decodedQuery(String url) {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q) + '?'
                + java.net.URLDecoder.decode(url.substring(q + 1), StandardCharsets.UTF_8);
    }

    /** 表单体（Texts.joinQuery：k=v&k=v，不编码）的键名集合；判等用，避免 contains 的子串假绿。 */
    private static Set<String> formKeys(String form) {
        return formFields(form).keySet();
    }

    private static Map<String, String> formFields(String form) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String pair : form.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            fields.put(eq < 0 ? pair : pair.substring(0, eq), eq < 0 ? "" : pair.substring(eq + 1));
        }
        return fields;
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
        /** POST 表单体（未编码，DouyinApi 用 Texts.joinQuery 拼装）；GET 请求为空串。 */
        String lastForm = "";
        /** 累计请求次数：上报去重类判据要能断「第二次什么都没发」。 */
        int requestCount;
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
            lastForm = body == null ? "" : new String(body, StandardCharsets.UTF_8);
            requestCount++;
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
