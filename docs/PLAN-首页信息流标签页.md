# PLAN：首页信息流第二排标签（推荐 / 关注 / 精选 / 移动推荐）

用户反馈：「首页信息流下面的第二排按钮（推荐、关注、精选、移动推荐）功能都没有实现」。
先按当前磁盘状态逐个按钮给出真实结论，再给实现计划。

## 0. 现状核对（逐按钮）

| 按钮 | 当前行为 | 结论 |
|---|---|---|
| 推荐 | 已在选中态再点一次 → `loadFirstPage()` → `HomeFeedRepository.refresh()`（refresh_index 持久化自增 + 回传最近已展示 id），标题下显示「正在刷新首页内容…」 | **已实现**（另一会话本轮刚落地，HomeFeedScreen.kt:329-331、HomeFeedRepository.kt:105-138）。与原版 `refreshTextIfSameTab` 语义一致。 |
| 关注 | `onClick` 只执行 `activeTab = tab`（HomeFeedScreen.kt:333），`activeTab` 除选中高亮外没有任何数据消费点；列表始终走 tab/feed | **未实现**（占位） |
| 精选 | `onOpenRoute(NavRoutes.FEATURED)` → FeaturedScreen（真实页面，走 tab/feed + tagId） | **已实现为独立页跳转**；与原版「页内第四个 tab」形态不同（见待裁 Q1） |
| 移动推荐 | 同「关注」，只置 `activeTab` | **未实现**（占位） |

根因（证据见 §1）：
1. UI 层：`activeTab` 是纯高亮状态，四个标签共用同一条 `HomeFeedRepository`（= tab/feed）数据链路，切标签不改数据源。
2. 核心层：`FeedApi.followFeed` / `FeedApi.mobileFeed` 在 App 侧零调用方（`grep -rn followFeed android/app` 只命中 LiveApi 的同名直播方法）。
3. 核心层缺陷 A：`ApiRequest.mobile()` 开关在 `DouyinApi.execute` 里没有任何消费点（grep "mobile" DouyinApi.java = 0 命中）→ 即使调用 mobileFeed，也会带桌面 Chrome UA + 桌面基础参数发往 aweme.snssdk.com，服务端按非法客户端拒绝。
4. 核心层缺陷 B：`FeedApi.mobileFeed` 参数形态与反编译原版不符——发了 `max_cursor`/`iid`，原版发的是 `refresh_index`/`install_id`/`cold_start`/`refer`/`pull_type`/`aweme_rec_raw_data` 等 19 键 + `filterGids`/`presented_ids`，且没有 max_cursor。

## 1. 参数来源对照（逐字核对反编译源）

### 关注流 `/aweme/v1/web/follow/feed/`（原版 n11.m8022N，src/app/p000/n11.java:1519-1617）

- 参数 = zy0.m12473a() 26 个桌面基础参数（= 现 `WEB_BASE_PARAMS`）+ cursor/level/count/pull_type=0/aweme_ids=""/room_ids=""/webcast_sdk_version=170400/webcast_version_code=170400（n11.java:1581-1588）→ **现 FeedApi.followFeed 参数键已对齐**。
- 调用点固定 count=20、level=1（kq5.java:1109 `m8022N(j, 20, 1, …)`）。
- 签名：`m8002r(…, false, …, 252)`；252&32≠0 ⇒ a_bogus 恒为 true（n11.java:495-501）→ 现 `.sign()` 正确。
- 翻页游标：next = `cursor > 0 ? cursor : maxCursor`（kq5.java:1149-1151）；首屏 cursor=0。
- 响应取数：`data[].aweme` 非空优先，否则 `filterPrimaryPlayableAwemes(aweme_list)`（kq5.java:1125-1146）。
- 错误语义：status_code=8 →「需要登录才能查看关注内容，请在设置中配置完整登录 Cookie」；2154 →「请求频率过高，请稍后再试」；其余 →「服务器返回错误 (N)」（n11.java:1556-1563）。
- 缺 sessionid/sessionid_ss 仅 Log.w，不阻断（n11.java:1593-1600）。

### 移动推荐 `/aweme/v2/feed/` @ aweme.snssdk.com（原版 n11.m8030V，n11.java:2126-2345 + lz0.java:44-112）

- 19 个 query 键（n11.java:2257-2299）：device_platform=android、aid=1128、channel=douyin、version_code=391000、version_name=39.10.0、device_type(Build.MODEL，空→"Pixel 10 Pro")、os_version(Build.VERSION.RELEASE，空→"16")、os_api(SDK_INT)、language=zh、**count=10（字面量）**、**refresh_index**（kq5.f12444f，初始 1，每次请求后自增，kq5.java:59/1479/1499）、device_id=m4208o("mobile_feed_device_id")、**install_id**=m4208o("mobile_feed_install_id")、cookie_enabled=true、downlink=10、**cold_start=(refreshIndex==1?1:0)**、refer=1、**pull_type=(coldStart?"2":"0")**、aweme_rec_raw_data（冷启动=短 JSON；非冷启动=把最近 ≤20 个已展示 id 同时填进 videoPrefer 的 like/halfMin/min 三组）。
- 已展示 id 非空时追加 `filterGids` 与 `presented_ids`（同值，逗号连接）（n11.java:2322-2326）。
- **没有 max_cursor / iid**（现 FeedApi.mobileFeed 的这两个参数是错的）。
- a_bogus：lz0.java:52-73 先把参数表里已有的 a_bogus 移除再签名，签出非空才放回 → 每次真实请求都带 a_bogus（现 `.sign()` 语义相符）。
- 请求头只有 4 个（lz0.java:76-79）：User-Agent=m8001p0()（即 `com.ss.android.ugc.aweme/391000 (Linux; U; Android {os}; zh_CN; {model})`，= 现 `DeviceProfile.mobileUserAgent()`）、Accept=\*/\*、Accept-Language=zh-CN,zh;q=0.9、Cookie（非空才带）。**没有 referer、没有桌面 sec-ch-ua 指纹**。
- 响应 protobuf（g13.m4375f）→ 现 `MobileFeedProtoCodec` 已有；空体抛「移动推荐返回空响应 (HTTP N)」。
- id 去重：LinkedHashMap LRU，回传窗口取最近 20（`o70.m8501m1(20, list)`，n11.java:2252），已展示集合封顶 50（kq5 m7280r 侧）。

## 2. 实现阶段

### 阶段 A：核心层修复（refactor/）

- A1 `FeedApi.mobileFeed` 重写签名与参数：`mobileFeed(int refreshIndex, boolean coldStart, List<String> presentedIds)`；按 §1 组 19 键 + filterGids/presented_ids + aweme_rec_raw_data；device_id/install_id 走 `CookieStore.persistentDeviceId` 键名不变；count 固定 10。
- A2 `DouyinApi.execute` 消费 `request.isMobile()`：mobile 请求改用 APP_FEED 头模板（UA=mobileUserAgent、Accept、Accept-Language，无 referer、无桌面指纹）；参数注入跳过 `injectCommonParams` 的桌面 26 键路径（mobile 端参数表由 FeedApi 自带）。
- A3 `FeedDataSource` 增加两个加载方法：
  - `loadFollow(long cursor)` → followFeed(20, cursor, "1")，按 §1 取数与 next-cursor 规则，status 8/2154 用原版文案抛错（ApiException.loginRequired/rateLimited 的 message 若与原版文案不一致则对齐文案）。
  - `loadMobile(int refreshIndex, List<String> presentedIds)` → mobileFeed，暴露 hasMore/cursor。
- A4 单测（refactor/tests）：mobileFeed 参数表逐键断言（含 cold_start/pull_type 联动、filterGids=presented_ids、无 max_cursor）；followFeed next-cursor 推导；isMobile 头模板断言。

### 阶段 B：UI 层按标签接数据（android/…/ui/home/）

- B1 新增 `FollowFeedRepository` / `MobileFeedRepository`（object，形态对齐 HomeFeedRepository：load/refresh/cached* + invalidate），关注 cursor 翻页、移动 refresh_index 自增 + 已展示 id LRU(50)。登录/登出时与 HomeFeedRepository 一同 invalidate（找到现有 invalidate 调用点接入）。
- B2 `HomeFeedScreen` 的 `loadFirstPage/loadNextPage/LaunchedEffect` 按 `activeTab` 分发到对应仓储；chip 语义对齐 HomeTabsSpec：不同标签点击=切换并重载该标签首屏；同标签再点=刷新（推荐已有；关注/移动同规则）。标题行显示当前标签名。错误态直接展示 ApiException 文案（关注 status=8 即原版登录提示）。
- B3 精选保持现状路由（若 Q1 批「页内化」则另开阶段）。

### 阶段 C：播放队列与路由

- C1 关注/移动标签内点条目进播放器：PLAYER 路由扩 `source` 参数（recommend|follow|mobile），`PlayerFromFeedScreen` 按 source 选队列快照与续页 loadMore（复用现有模式）。
- C2 AxTvApp/defaultHomeRoute 与 SettingsBinder 中「移动推荐未实现」的注释/映射更新为实际行为；不做无关改动。

### 阶段 D：门禁与实机清单（只在最后跑一次全量）

- D1 `assembleDebug` + refactor/tests + android 单测，一次性跑完。
- D2 实机核对清单：①关注（需登录 Cookie）出真实关注流、到末尾自动翻页、未登录时显示原版提示；②移动推荐冷启动/翻页/去重；③推荐刷新回归；④三标签进播放器连播与队尾续页。

## 3. 待拍板

- **Q1 精选形态**：原版是页内第四 tab（与推荐同屏共用列表壳）；现状是跳独立 FeaturedScreen（已可用）。
  推荐：**保持路由跳转**——功能已在，页内化会把 FeaturedScreen 的分区逻辑搬进 HomeFeedScreen，改动面大且回归风险高。若批「页内化」，另开阶段并退役 FEATURED 路由。
- **Q2 移动推荐数据价值**：`/aweme/v2/feed/` 匿名（无 sessionid）时服务端常返回空/风控。原版如此，照做。
  推荐：**照原版实现**，空结果时界面展示错误/空态文案，不做任何伪造兜底。
- **Q3 status=8 文案**：以反编译原文为准（「需要登录才能查看关注内容，请在设置中配置完整登录 Cookie」）。
  推荐：**逐字采用原版文案**。

## 4. 边界声明

- 不动另一会话刚落地的推荐刷新链路（refresh/persisted index），只在其旁边扩展按标签分发。
- 不改 LiveApi.followFeed（直播关注，同名不同链）。
- android/local.properties 不读不提交。
