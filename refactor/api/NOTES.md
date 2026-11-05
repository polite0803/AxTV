# axtv.api —— 抖音 Web API 客户端 重构说明

来源：`src/app/p000/n11.java`（5659 行，Log TAG `DouyinDirectApi`）+ `fl4` 的 Cookie 部分
+ `kz0` 的请求头/UA 常量 + `ez0`/`gz0`/`h11` 等解析协程。

## 1. 类映射表

| 原类 / 原方法 | 原行数 | 新类 | 新行数 | 说明 |
|---|---|---|---|---|
| `n11.java` 通用请求函数 `m8002r` / `m8004t` / `m8005w` / `m8015G0` / `m8049l` | — | `DouyinApi` | 292 | 请求编排：公共参数 → 签名 → 组头 → 发送 → 落 Cookie → 查错 |
| `n11.m8011D0` 公共参数 | ~60 | `DouyinApi.injectCommonParams` | — | 逐字还原，含 2560/1440/24/8 四个兜底值 |
| `n11.m8013E0` 只注入 msToken | — | `DouyinApi.currentMsToken` | — | 搜索热榜/联想用 |
| `n11.m8015G0` 用户中心签名链 | ~80 | `DouyinApi.applyUserCenterSignChain` | — | a_bogus + uifid + timestamp + x-secsdk-web-signature |
| `n11.f14561n` 默认请求头（14 条） | — | `DouyinEndpoints.DEFAULT_HEADERS` | 195 | 逐字还原 |
| `n11.f14562o` 路径→Referer（40 条）+ `m7993X` | ~50 | `DouyinEndpoints.REFERER` + `refererFor` | — | switch 分支按可确证部分还原 |
| `n11.m7996d` 搜索页 Referer | — | `DouyinEndpoints.searchReferer` | — | 带随机 aid + type |
| `n11.m8003r0` Set-Cookie 解析 | — | `SetCookie` | 37 | 按 `;` 切分后取【第一段】（`o70.m8483U0` = `list.get(0)`），以首个 `=` 拆 name/value |
| `n11.m8000k` bd-tt-error-code 检查 | — | `DouyinApi.call` 内 | — | `bst-tt-error-code` 非 "0" 即抛错 |
| `n11.m7999i` x-secsdk-web-signature | — | `SignProvider.xSecSdkWebSignature`（默认方法） | 71 | MD5(uifid_ts_A96D…7E4E_参数串) |
| `n11` 的 8 类解析协程 | — | `JsonCodec`（接口）+ `ModelJsonCodec` | 18 / 304 | 核心层不绑 JSON 库；默认实现按 @JsonKey 反射绑定 record |
| `fl4` 的 cookie 读写 | 1138 | `CookieStore`（接口） | 73 | 只抽 API 层需要的 9 个方法 |
| `n11` 的 OkHttp 双客户端 | — | `HttpEngine`（接口） | 31 | 30s 三个超时；跟随/不跟随重定向 |
| `kz0.f12695V` 小写 uifid 头 / `m8065v` ticket-guard 头 | — | `ApiRequest.uifidHeader()` / `ticketGuardHeaders()` | 218 | 语义化建模 |
| `n11` 接口方法（44 个端点） | — | `FeedApi` / `VideoApi` / `UserApi` / `SearchApi` / `LiveApi` / `InteractionApi` / `CollectionApi` | 7 个文件 | 拆掉 God class |
| `n11` 的异常语义 | — | `ApiException`（10 个工厂） | 111 | network/http/emptyBody/headerError/apiStatus/loginRequired/rateLimited/missingInteractionCredential |
| `n11` 移动端形态 | — | `DeviceProfile` + `ApiRequest.mobile()` | 76 | `aweme.snssdk.com` + 移动 UA |
| `n11.m8012E` msToken 引导 | — | `MsTokenBootstrap` | 67 | 见下 |
| `n11.m8007A` ttwid 申领 + `m8049l` 前置检查 | — | `AnonymousIdentity` | 122 | 匿名访问身份（设备级 ttwid），见 §7 |

## 2. 端点清单（从 n11 的 map.put 字面量按调用点归组）

| 端点 | 参数（提取自调用点） | 备注 |
|---|---|---|
| `/aweme/v1/web/tab/feed/` | aweme_pc_rec_raw_data, count, cpu_core_num, creative_id, device_memory, filterGids, free_right, globalwid, live_insert_type, min_window, pc_libra_divert, plug_block, pull_type, refresh_index, screen_height, screen_width, share_aweme | 首页推荐，签名 |
| `/aweme/v2/web/module/feed/` | Seo-Flag, active_id, awemePcRecRawData, count, filterGids, install_time, is_active_tab, module_id, pc_libra_divert, pre_item_ids, pre_log_id, pre_room_ids, presented_ids, pull_type, refer_id, refer_type, refresh_index, support_dash | 频道推荐 |
| `/aweme/v2/feed/` | （helper 构造）**存疑** | 移动端形态 |
| `/aweme/v1/web/aweme/detail/` | aweme_id, origin_type, request_source, support_dash, support_h265 | |
| `/aweme/v1/web/aweme/related/` | Seo-Flag, awemePcRecRawData, aweme_id, count, filterGids, refresh_index, sub_channel_id | |
| `/aweme/v1/web/aweme/post/` | count, cut_version, from_user_page, locate_item_id, locate_query, max_cursor, need_time_list, publish_video_strategy_type, sec_user_id, show_live_replay_strategy, time_list_query, version_code, version_name, whale_cut_token | |
| `/aweme/v1/web/aweme/favorite/` | count, cut_version, max_cursor, min_cursor, publish_video_strategy_type, sec_user_id, whale_cut_token | |
| `/aweme/v1/web/aweme/listcollection/` | （helper 构造）**存疑** | 收藏夹 |
| `/aweme/v1/web/aweme/collect/` | msToken, odin_tt, uifid | POST，需互动凭据 |
| `/aweme/v2/web/aweme/stats/` | （helper 构造）**存疑** | 播放埋点 |
| `/aweme/v1/web/comment/list/` | aweme_id, count, cursor | |
| `/aweme/v1/web/comment/list/reply/` | comment_id, count, cursor, item_id | |
| `/aweme/v1/web/commit/item/digg/` | uifid | POST，需互动凭据 |
| `/aweme/v1/web/commit/dislike/item/` | aweme_id | |
| `/aweme/v1/web/user/profile/self/` | （helper 构造）**存疑** | |
| `/aweme/v1/web/user/profile/other/` | personal_center_strategy, publish_video_strategy_type, sec_user_id, source | |
| `/aweme/v1/web/user/follower/list/` | address_book_access, count, gps_access, keyword, list_type, max_time, min_time, need_filter_settings, offset, query_correct_type, search_* , sec_user_id | 兼做粉丝内搜索 |
| `/aweme/v1/web/user/following/list/` | address_book_access, count, gps_access, is_top, max_time, min_time, offset, sec_user_id, source_type, user_id | |
| `/aweme/v1/web/search/item/` | （helper 构造）**存疑** | |
| `/aweme/v1/web/search/sug/` | keyword, source | 只注入 msToken |
| `/aweme/v1/web/api/suggest_words/` | business_id, count, from_group_id, is_filter_search, keyword, list_type, need_filter_settings, offset, query, query_correct_type, search_channel, search_id, search_source | |
| `/aweme/v1/web/general/search/single/` | （helper 构造，含 awemePcRecRawData）**存疑** | 综合搜索 |
| `/aweme/v1/web/discover/search` | （helper 构造）**存疑** | 用户搜索 |
| `/aweme/v1/web/live/search/` | （helper 构造）**存疑** | 直播搜索 |
| `/aweme/v1/web/hot/search/list/` | （无参数，只注入 msToken） | |
| `/aweme/v1/web/seo/inner/link/` | （helper 构造）**存疑** | |
| `/aweme/v1/web/home/channel/setting/` | channel, cpu_core_num, device_memory, fp, msToken, screen_height, screen_width, uifid, verifyFp, webid | |
| `/aweme/v1/web/mix/detail/` | （helper 构造）**存疑** | |
| `/aweme/v1/web/mix/aweme/` | （helper 构造）**存疑** | |
| `/aweme/v1/web/mix/listcollection/` | a_bogus, count, cursor, timestamp, uifid, x-secsdk-web-signature | 用户中心签名链 |
| `/aweme/v1/web/mix/watch/record/` | episode, item_id, mix_id, scene | POST |
| `/aweme/v1/web/series/list/` | count, cursor, read_new_mix, req_from, sec_user_id | |
| `/aweme/v1/web/series/detail/` | req_from, series_id | |
| `/aweme/v1/web/series/aweme/` | count, cursor, pull_type, series_id, source | |
| `/aweme/v1/web/danmaku/get_v2/` | app_name, authentication_token, duration, end_time, format, group_id, item_id, pc_libra_divert, start_time, support_dash, support_h265, version_code, version_name | |
| `/aweme/v1/web/history/read/` | category, count, directory, max_cursor, status | |
| `/aweme/v1/web/history/write/` | app_name, count, device_platform, enable_history, enter_from, filter_duration, from_group_id, is_filter_search, keyword, language, list_type, live_id, need_filter_settings, offset, publish_time, query_correct_type, search_channel, …（参数很多，helper 构造） | POST |
| `/aweme/v1/web/watchlater/list/` | list_type, offset, operate_type | |
| `/aweme/v1/web/follow/feed/` | aweme_ids, count, cursor, level, pull_type, room_ids, webcast_sdk_version, webcast_version_code | |
| `/aweme/v1/web/emoji/list` | （helper 构造）**存疑** | |
| `/webcast/feed/` | action, action_type, custom_count, enter_source, inner_from_drawer, is_draw, is_ssr, liveid, maxtime, need_map, source_key | live.douyin.com |
| `/webcast/web/feed/follow/` | scene | |
| `/webcast/room/web/enter/` | （helper 构造）**存疑** | |
| `/webcast/room/info_by_scene/` | room_id, scene | |
| `/webcast/web/partition/detail/room/v2/` | count, offset, partition, partition_type | |

> 标「存疑」的 15 个端点：参数在 helper 里拼装（部分含压缩后的 `awemePcRecRawData` 大 JSON），
> 提取脚本只按"put 字面量"归类，没能逐字追到端点。门面方法里保留了可核实的公共项，
> 补齐时请以真机抓包为准。

## 3. 公共参数构造（逐字还原 m8011D0）

| 参数 | 取值 |
|---|---|
| `msToken` | `cookie["msToken"]`，空则现生成（`Texts.randomMsToken`，见 `MsTokenBootstrap`） |
| `screen_width` | `cookie["dy_swidth"]`，兜底 `"2560"` |
| `screen_height` | `cookie["dy_sheight"]`，兜底 `"1440"` |
| `cpu_core_num` | `cookie["device_web_cpu_core"]`，兜底 `"24"` |
| `device_memory` | `cookie["device_web_memory_size"]`，兜底 `"8"` |
| `verifyFp` / `fp` | `cookie["s_v_web_id"]`，**两个键同值，仅存在时写** |
| `uifid` | `cookie["UIFID"]`，仅存在时写 |
| `webid` | `user_unique_id`，**仅当全为数字**时写 |

## 4. 用户中心签名链（m8015G0）

```
a_bogus   = signer.signABogus(path, query, body, live ? QQBrowserUA : undefined)
uifid     = query["uifid"] ?? UIFID ?? UIFID_TEMP
timestamp = 秒级
缺失 uifid → 只写 timestamp + WARN "withUserCenterSignChain: UIFID/UIFID_TEMP 均缺失，<path> 将不带 x-secsdk-web-signature"
否则       → 写 uifid / timestamp / x-secsdk-web-signature
```

`x-secsdk-web-signature = MD5( uifid + "_" + timestamp + "_A96D855A08C0A9707F8BEF0D9A527E4E_" + 完整参数串 )`

## 5. 匿名访问身份（`n11.m8007A` + `m8049l` → `AnonymousIdentity`）

抖音 Web 的公开接口（首页推荐 `/aweme/v1/web/tab/feed/`、频道、搜索热榜…）**不要求登录**，
但要求请求携带设备级 `ttwid` cookie。全新安装/未登录时 jar 里没有它，服务端直接回空响应
（`ApiException.Kind.EMPTY_BODY`），界面就表现为「未登录 → 所有页面都没有数据」。

```
ensure(engine, cookies):
  jar["ttwid"] 非空        → 直接返回（不再请求）
  否则 POST ttwid.bytedance.com/ttwid/union/register/
      headers: UA=Chrome/147, Accept=application/json, Content-Type=application/json,
               Origin=https://www.douyin.com, Referer=https://www.douyin.com/
      body:    {"region":"cn","aid":1768,"needFid":false,"service":"www.ixigua.com",
                "migrate_info":{"ticket":"","source":"node"},
                "cbUrlProtocol":"https","union":true}
      取值:    Set-Cookie 里的 ttwid（第一段） → 响应体 "ttwid":"…" 兜底
      写入:    cookies.putAll({ttwid: …})
  失败 → 仅 WARN，返回 null（不抛异常、不阻断业务请求）
```

`DouyinApi.ensureAnonymousIdentity()` 在 `execute()` 最前面调用（由构造参数
`anonymousIdentityBootstrap` 控制，App 装配时为 `true`），并用 `identityAttempted`
复刻 `n11.f14568f` 的「每个进程只尝试一次」语义，失败后不会每个请求都多打一次。

## 6. 待迁移（Android 侧需实现）

| 接口 | Android 侧实现要点 |
|---|---|
| `HttpEngine` | OkHttp 适配（两个 client：普通 30s、Cookie 引导不跟随重定向），或 JDK `HttpClient` |
| `CookieStore` | SharedPreferences：`cookie_string`、`self_user_id`、`user_unique_id`、`mobile_feed_device_id`、`mobile_feed_install_id`；`ticketGuard()` 读 5 个 `interaction_*` 键 |
| `SignProvider` | 由 `axtv.sign` 的 `AbogusSigner` + `SignJsEngine` 实现；`ticketGuardHeaders` 已由 `axtv.api.TicketGuardHeaderCodec` 实现（**取证更正：`m4464l` 是 Base64(JSON)+SHA256withECDSA，不是 protobuf**）；`serverCert` 的 ECDH 分支离线不可复现，抛 `TicketGuardUnsupportedException` |
| `JsonCodec` | 核心层已提供 `ModelJsonCodec`：`axtv.panel.Json` 解析 JSON 树 + `@JsonKey`/`FlexibleJson` 反射绑定 `axtv.model` record，零第三方依赖；Android 直接注入即可 |
| `DeviceProfile` | `Build.VERSION.RELEASE` / `Build.MODEL` / `SDK_INT` |

## 7. 存疑

1. **15 个端点的参数未逐字核实**（见 §2 表格里的「存疑」）。这些端点的参数在 helper
   （`m7989C` / `m8009B` 的入参拼装、`awemePcRecRawData` 压缩 JSON）里，静态提取没跟到端点。
2. **`m8002r` 的布尔参数语义**：原函数签名里有 `z`（是否签名）与另一个布尔（是否跟随重定向），
   已按 `ApiRequest.sign()` / `HttpEngine.followRedirects` 建模；但"Cookie 引导不跟随重定向"
   只在 `MsTokenBootstrap` 路径上确认，其余端点默认 `true`。
3. **`bd-ticket-guard-*` 5 个头的值**：取证更正——`AbstractC0656g4.m4464l` 产出 `Base64(JSON)`（字段
   `ts_sign` / `req_content` / `req_sign` / `timestamp` / `t_trust`），签名是
   `SHA256withECDSA("ticket=<t>&path=<p>&timestamp=<s>")`，**不是 protobuf**。已由
   `TicketGuardHeaderCodec` 零依赖实现；仅 `serverCert` 的 ECDH+HKDF 加密分支未实现（离线无法取证）。
4. **`SetCookie` 取 `;` 切分后的第一段**（`o70.m8483U0` = `list.get(0)`）：
   `Set-Cookie: ttwid=1%7C...; Path=/; Domain=...; HttpOnly; SameSite=None` → 取 `ttwid=1%7C...`。
   早期重构误读为"只取最后一段"，会把 `SameSite=None` 当 cookie 写回（并让服务端下发的
   `ttwid`/`msToken`/`UIFID` 永远进不了 jar），已按原算法修正为第一段。
