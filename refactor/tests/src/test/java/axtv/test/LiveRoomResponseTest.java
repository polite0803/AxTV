/*
 * 直播间取房间的响应结构回归用例。
 *
 * 背景：真实接口（2026-11-06 实测 /webcast/room/info_by_scene/）返回的是
 *   {"status_code":0,"data":{ ...房间字段... }}
 * 即 data **直接就是房间对象**，不是 data.room。此前按推定结构解析，导致
 * 每个直播间都提示「未取到直播间信息」。这里把真实结构钉死，防止再退回去。
 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import axtv.api.ModelJsonCodec;
import axtv.model.LiveRoomResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LiveRoomResponseTest {

    private final ModelJsonCodec codec = new ModelJsonCodec();

    @Test
    @DisplayName("info_by_scene：data 直接是房间对象（含数字 id 与推流地址）")
    void sceneResponsePutsRoomDirectlyUnderData() throws Exception {
        String json = """
                {
                  "status_code": 0,
                  "data": {
                    "id_str": "7704827838759684906",
                    "status": 2,
                    "title": "示例直播间",
                    "user_count": 179,
                    "web_rid": "339638092575",
                    "stream_url": {
                      "flv_pull_url": { "FULL_HD1": "https://example.com/a.flv" }
                    }
                  }
                }
                """;
        LiveRoomResponse response = codec.decode(json, LiveRoomResponse.class);
        assertEquals(0, response.statusCode());
        assertNotNull(response.room(), "data 直接就是房间对象，不能再按 data.room 解析");
        assertEquals("7704827838759684906", response.room().idStr());
        assertEquals("339638092575", response.room().webRid());
        assertEquals(2, response.room().status());
        assertFalse(response.room().streamUrl().flvPullUrl().isEmpty());
    }
}
