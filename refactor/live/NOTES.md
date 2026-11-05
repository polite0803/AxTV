# axtv.live —— 直播 WebSocket / 弹幕 重构说明

来源：`src/app/p000/b11.java`（1113 行，TAG `LiveWS`）、`bq2/co2/cq2/ip2/C1285k/q30`
（TAG `LiveWebSocket`）、`om5.java`（407 行，TAG `Danmaku`）、`wo0/to0/uo0`（弹幕布局）、
`az0`（握手参数）、`no4`（wss 签名）、`sz0`（连接前两次同步 GET）、
`com/github/axtv/dv/model/` 的 `LiveRoom` / `LiveStreamQuality` / `LiveCoreSdkData` / `StreamUrl`。

## 1. 类映射表

| 原类 | 原行数 | 新类 | 新行数 | 说明 |
|---|---|---|---|---|
| `cq2.java` + `bq2.java` | — | `LiveWebSocketClient` | 302 | 连接生命周期：地址拼装 / 帧循环 / 心跳 / 重连 / ack |
| `cq2` 的 wss 地址与参数 | — | `LiveWebSocketClient.buildUrl` | — | 24 个 query 参数逐字还原 |
| `cq2` / `bq2` 心跳与重连 | — | 同文件常量 | — | 心跳 10 000 ms、重连最多 5 次、间隔 5 000 ms、关闭码 1000 |
| `cq2` 的 OkHttp WebSocket 用法 | — | `WebSocketTransport`（接口） + `JdkWebSocketTransport`（JVM 实现） | 56 + 192 | 核心层不绑 OkHttp |
| `b11.java` | 1113 | `LiveHttpClient`（接口） + 抓流/选区逻辑分流到 `LiveStreamResolver` | 39 | 原类同时管 HTTP 与选流 |
| `k04.m6902n/m6907s/m6908t/m6906r/m6909u` | — | `LiveFrameCodec` | 454 | PushFrame / Response / Message / Chat 的 protobuf 手写编解码 |
| `tp2/up2/sp2/rp2/wp2/vp2` | — | `LiveFrame` / `LiveResponse` / `LiveChat` | 115 + 108 + 78 | 帧与消息模型 |
| `az0.java` | — | `LiveCredentials` | 91 | webRid / roomId / userId / cookie |
| `no4.m8230a` | — | `LiveSignatureProvider`（接口） | 20 | 由 `axtv.sign.SignJsEngine` 实现 |
| `qp2.java` | — | `LiveSocketState` | 19 | CONNECTING / CONNECTED / DISCONNECTED / ERROR |
| `wo0/to0/uo0` | — | `DanmakuStyle` | 310 | 弹幕几何：字号/行高/间距/时长/速度基准宽度 |
| `LiveDanmakuSettings`（Kotlin） | — | `LiveDanmakuSettings` | 272 | 直播弹幕偏好 |
| `lq5.java` | — | `LiveQualityPreference` | 55 | 自动/最高/1080/720/540 |
| `DataModelKt` 选流段 | — | `LiveStreamResolver` + `ResolvedStream` | 212 + 55 | SDK 数据优先，其次 flv/hls map 键名回退 |
| `StreamUrl` / `LiveCoreSdkData` | — | `LiveStreamInfo` / `LiveStreamQuality` | 74 + 81 | |
| 各处的 `android.util.Log` | — | `LiveLog`（接口） + `LiveLogFormat` | 61 + 108 | 日志点与文案保留 |
| `fl4` 的直播 Cookie 段 | — | `LiveCookieStore` | 28 | |

## 2. wss 地址与参数（逐字）

```
wss://webcast3-ws-web-lq.douyin.com/webcast/im/push/v2/
  app_name=douyin_web & version_code=180800 & webcast_sdk_version=1.3.0
  update_version_code=1.3.0 & compress=gzip
  cursor=h-1_t-<毫秒>_r-1_d-1_u-1 & host=https://live.douyin.com
  aid=6383 & live_id=1 & did_rule=3 & debug=false & maxCacheMessageNumber=20
  endpoint=live_pc & support_wrds=1 & im_path=/webcast/im/fetch/
  user_unique_id=<userId> & device_platform=web & cookie_enabled=true
  screen_width=1920 & screen_height=1080 & browser_language=zh-CN
  browser_platform=Win32 & browser_name=Mozilla & browser_version=5.0 (Windows)
  identity=audience & room_id=<roomId> & signature=<getMSSDKSignature(msStub, UA)>
```

## 3. 帧格式（protobuf 字段号，取自 LiveFrameCodec 常量）

| 结构 | 字段 |
|---|---|
| PushFrame | logId=2, headers=5, payloadEncoding=6, payloadType=7, payload=8 |
| PushHeader | key=1, value=2 |
| Response | messages=1, internalExt=5, needAck=9 |
| Message | method=1, payload=2 |
| ChatMessage | user=2, content=3, eventTime=15, chatBy=20 |
| User | id=1, nickName=3, level=6, idStr=1028 |
| RoomUserSeq | total=7 |

压缩：`payloadEncoding == "gzip"` 时先 gzip 解压再解 Response。

## 4. 一条弹幕的完整链路

```
LiveWebSocketClient.onFrame(bytes)
 ├─ LiveFrameCodec.decodeFrame     → PushFrame（payloadType / logId / headers）
 ├─ payloadType == "hb"            → 心跳帧，丢弃（原实现在此重置心跳计时）
 ├─ LiveFrameCodec.decodePayload   → gzip 解压（若 payloadEncoding=gzip）
 ├─ LiveFrameCodec.decodeResponse  → Response{messages, internalExt, needAck}
 ├─ 逐条 dispatch(method)
 │    ├─ "WebcastChatMessage"         → decodeChat → Listener.onChat
 │    ├─ "WebcastRoomUserSeqMessage"  → decodeRoomUserSeq → Listener.onRoomUserSeq
 │    └─ 其它 method                   → 仅 DEBUG 日志（与原实现一致）
 └─ needAck → send(encodeAckFrame(logId, internalExt))
```

心跳：每 10 000 ms 发 `encodeFrame("hb", 0, new byte[0])`。
重连：`onClosed` / `onFailure` → 计数 +1，> 5 次放弃，否则 5 000 ms 后重开。

## 5. 弹幕渲染参数（DanmakuStyle）

- 速度基准 = **窗口宽度**（2.3.43 的变更点：横竖屏观感一致）
- 轨道分配、行高、垂直/水平间距、显示区域百分比、不透明度、字号、屏蔽词/包过滤由
  `DanmakuStyle` + `LiveDanmakuSettings` 决定；具体公式见两个类的 javadoc（逐字取自 `wo0/to0/uo0`）

## 6. 待迁移（Android 侧）

| 接口 | 实现 |
|---|---|
| `WebSocketTransport` | OkHttp `OkHttpClient.newWebSocket`（`pingInterval=0`，心跳由应用层 hb 帧负责） |
| `LiveHttpClient` | 连接前两次同步 GET（`newCall().execute()`） |
| `LiveLog` | `android.util.Log`（TAG 已内置四个） |
| `LiveSignatureProvider` | `axtv.sign.SignJsEngine.signLive(roomId, userId, ua)` |
| `LiveCookieStore` | `SharedPreferences("douyin_auth")` 的 cookie / user_unique_id |

## 7. 存疑

1. **`b11.java`（1113 行）只做了结构拆分，未逐行重写**：它同时含 HTTP 抓流、选区、以及
   TAG `LiveWS` 的连接管理。连接管理已由 `LiveWebSocketClient` 覆盖；HTTP 部分本次只留接口。
2. **心跳计时重置点**：原实现收到 hb 帧会重置计时器（注释可见），本实现用固定周期调度代替，
   行为等价但失败重试的相位可能与原版不同。
3. **`az0` 的 webRid 与 roomId 的关系**（`web_rid` 是直播间短号，进房接口返回的数字 id 才是
   IM 用的 room_id）在原文里按分支取值，本实现按"调用方传入正确 roomId"建模。
