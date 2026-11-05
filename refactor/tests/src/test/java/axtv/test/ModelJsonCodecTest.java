/* ModelJsonCodec：JSON → axtv.model record 的 JVM 解码用例。
 *
 * 样本说明：仓库内没有真机抓包样本（docs/ 下无 .json），以下 JSON 均按
 * refactor/model/NOTES.md 的字段表手工构造最小响应，用于验证绑定规则，
 * 不代表线上真实返回体；真实键名待抓包复核（NOTES.md 存疑 S1）。
 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import axtv.api.ModelJsonCodec;
import axtv.model.Aweme;
import axtv.model.Comment;
import axtv.model.CommentListResponse;
import axtv.model.FeedResponse;
import axtv.model.InteractionMutationResponse;
import axtv.model.LiveCorePullData;
import axtv.model.LiveRoom;
import axtv.model.MixDetailResponse;
import axtv.model.UserWorksResponse;
import axtv.model.Video;

import org.junit.jupiter.api.Test;

class ModelJsonCodecTest {

    private final ModelJsonCodec codec = new ModelJsonCodec();

    @Test
    void feedDecodesNestedAwemeAndFlexibleScalars() throws Exception {
        String json = """
                {
                  "status_code": 0,
                  "has_more": "1",
                  "max_cursor": "12345",
                  "aweme_list": [
                    {
                      "aweme_id": "7300000000000000000",
                      "desc": "测试作品",
                      "create_time": "1699999999",
                      "aweme_type": "0",
                      "user_digged": "0",
                      "collect_stat": 1,
                      "video": {
                        "duration": 15000,
                        "width": 1920,
                        "height": 1080,
                        "play_addr": {"url_list": ["https://a/1.mp4", "https://a/2.mp4"]},
                        "bit_rate": [
                          {"gear_name": "normal_1080_0", "bit_rate": 3000000, "is_h265": "1", "FPS": "30"}
                        ]
                      },
                      "author": {"uid": "123", "nickname": "作者", "follower_count": "12345"},
                      "statistics": {
                        "digg_count": "100", "comment_count": 5, "share_count": 0,
                        "collect_count": "0", "play_count": "999"
                      },
                      "play_progress": {"last_modified_time": 0, "play_progress": 0}
                    }
                  ]
                }
                """;

        FeedResponse response = codec.decode(json, FeedResponse.class);

        assertTrue(response.hasMore(), "has_more 字符串 \"1\" 应解成 true");
        assertEquals(12345L, response.maxCursor());
        assertEquals(1, response.awemeList().size());

        Aweme aweme = response.awemeList().get(0);
        assertEquals("7300000000000000000", aweme.awemeId());
        assertEquals(1699999999L, aweme.createTime());
        assertTrue(aweme.isCollected());
        assertFalse(aweme.isDigged());
        assertNull(aweme.mixInfo());
        assertNotNull(aweme.video());
        assertNotNull(aweme.author());
        assertNotNull(aweme.statistics());

        Video video = aweme.video();
        assertEquals(1920, video.width());
        assertEquals(2, video.playAddr().urlList().size());
        assertEquals(30, video.bitRate().get(0).fps(), "FPS 是全大写 key");
        assertEquals(1, video.bitRate().get(0).isH265());
        assertEquals(12345L, aweme.author().followerCount());
        assertEquals(100L, aweme.statistics().diggCount());
        assertEquals(5L, aweme.statistics().commentCount());
        assertEquals(999L, aweme.statistics().playCount());
        assertNull(aweme.playProgress().watchTimes(), "缺省 watch_times 应保持 null 而不是 0");
    }

    @Test
    void alternateKeysAreUsedWhenPrimaryMissing() throws Exception {
        String json = """
                {
                  "aweme_id": "1",
                  "is_digg": "1",
                  "is_favorited": "1",
                  "mix_info": {"series_id": "42", "series_name": "合集"}
                }
                """;

        Aweme aweme = codec.decode(json, Aweme.class);

        assertTrue(aweme.isDigged(), "user_digged 缺失时应回落到 is_digg");
        assertTrue(aweme.isCollected(), "collect_stat 缺失时应回落到 is_favorited");
        assertEquals("42", aweme.mixInfo().mixId(), "mix_id 缺失时应回落到 series_id");
        assertEquals("合集", aweme.mixInfo().mixName());
    }

    @Test
    void missingFieldsFallBackToRecordDefaults() throws Exception {
        String json = """
                {"aweme_id": "1", "desc": null, "video": null}
                """;

        Aweme aweme = codec.decode(json, Aweme.class);

        assertEquals("", aweme.desc());
        assertEquals("1", aweme.awemeId());
        assertNotNull(aweme.video(), "video 为 null 时应由 compact constructor 归一化成空 Video");
        assertTrue(aweme.video().bitRate().isEmpty());
        assertEquals("", aweme.authenticationToken());
    }

    @Test
    void nullableBoxedFieldsKeepNull() throws Exception {
        String json = """
                {"is_digg": "1", "status_code": 0, "status_msg": "ok"}
                """;

        InteractionMutationResponse response = codec.decode(json, InteractionMutationResponse.class);

        assertEquals(Integer.valueOf(1), response.isDigg());
        assertNull(response.isCollect(), "未返回 is_collect 时应保持 null");
        assertEquals("ok", response.statusMsg());
    }

    @Test
    void mapsAndNullableLongDecode() throws Exception {
        String json = """
                {
                  "aweme_list": [{"aweme_id": "7001"}],
                  "has_more": "true",
                  "max_cursor": "100",
                  "aweme_date": {"7001": "1699999999", "7002": 1700000000},
                  "status_code": 0
                }
                """;

        UserWorksResponse response = codec.decode(json, UserWorksResponse.class);

        assertEquals(1, response.awemeList().size());
        assertTrue(response.hasMore());
        assertEquals(100L, response.maxCursor());
        assertNull(response.cursor(), "cursor 未下发时应保持 null");
        assertEquals(2, response.awemeDate().size());
        assertEquals(1699999999L, response.awemeDate().get("7001").longValue());
        assertEquals(1700000000L, response.awemeDate().get("7002").longValue());
    }

    @Test
    void liveStreamMapsAndEmbeddedJsonDecode() throws Exception {
        String json = """
                {
                  "room_id": "1",
                  "id_str": "1",
                  "web_rid": "123",
                  "title": "直播标题",
                  "user_count_str": "1.2万",
                  "status": "2",
                  "stream_url": {
                    "flv_pull_url": {"FULL_HD1": "https://flv/1"},
                    "hls_pull_url_map": {"FULL_HD1": "https://hls/1"},
                    "live_core_sdk_data": {
                      "pull_data": {
                        "options": "[{\\"level\\":1,\\"sdk_key\\":\\"FULL_HD1\\"}]",
                        "stream_data": "{\\"data\\":{\\"FULL_HD1\\":{\\"main\\":{\\"flv\\":\\"u\\"}}}}"
                      }
                    }
                  }
                }
                """;

        LiveRoom room = codec.decode(json, LiveRoom.class);

        assertEquals("123", room.webRid());
        assertEquals("1.2万", room.userCount());
        assertEquals(2, room.status());
        assertEquals("https://flv/1", room.streamUrl().flvPullUrl().get("FULL_HD1"));
        assertEquals("https://hls/1", room.streamUrl().hlsPullUrlMap().get("FULL_HD1"));

        LiveCorePullData pull = room.streamUrl().liveCoreSdkData().pullData();
        assertInstanceOf(List.class, pull.options(), "内嵌 JSON 字符串应解包成树");
        assertInstanceOf(Map.class, pull.streamData());
        Map<?, ?> stream = (Map<?, ?>) pull.streamData();
        assertNotNull(stream.get("data"));
    }

    @Test
    void invalidEmbeddedJsonBecomesNull() throws Exception {
        String json = """
                {"stream_data": "{broken"}
                """;

        LiveCorePullData pull = codec.decode(json, LiveCorePullData.class);

        assertNull(pull.streamData(), "内嵌 JSON 解析失败按原规则视为 null");
    }

    @Test
    void nestedCommentRepliesDecode() throws Exception {
        String json = """
                {
                  "comments": [
                    {
                      "cid": "1",
                      "text": "hi",
                      "user": {"uid": "9"},
                      "digg_count": "3",
                      "reply_comment": [{"cid": "11", "text": "re"}],
                      "is_user_digg": "1"
                    }
                  ],
                  "has_more": 1,
                  "cursor": "10",
                  "total": 2,
                  "status_code": 0
                }
                """;

        CommentListResponse response = codec.decode(json, CommentListResponse.class);

        assertEquals(1, response.comments().size());
        Comment comment = response.comments().get(0);
        assertEquals("1", comment.cid());
        assertEquals(3L, comment.diggCount());
        assertTrue(comment.isDigged());
        assertEquals(1, comment.replyComments().size());
        assertEquals("11", comment.replyComments().get(0).cid());
        assertTrue(response.hasMorePages());
    }

    @Test
    void mixDetailAlternateKeyDecodes() throws Exception {
        String json = """
                {"series_info": {"series_id": "42", "series_name": "合集", "statis": {"total_episode": 3}}}
                """;

        MixDetailResponse response = codec.decode(json, MixDetailResponse.class);

        assertNotNull(response.mixInfo());
        assertEquals("42", response.mixInfo().mixId());
        assertEquals("合集", response.mixInfo().mixName());
        assertEquals(3, response.mixInfo().statis().totalEpisode());
    }

    @Test
    void malformedJsonOrWrongRootThrows() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("{", FeedResponse.class));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("[]", FeedResponse.class));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("", FeedResponse.class));
    }

    @Test
    void wrongFieldShapeThrows() {
        String json = """
                {"aweme_id": "1", "video": "not-an-object"}
                """;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(json, Aweme.class));
    }
}
