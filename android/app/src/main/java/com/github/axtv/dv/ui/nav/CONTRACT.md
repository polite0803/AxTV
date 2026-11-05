# 导航接线约定（ui/nav）

本目录是所有「路由 → 页面」的唯一接线点。路由解析在 [NavRoutes.kt](NavRoutes.kt)，真实页面装配在 [NavScreens.kt](NavScreens.kt)，返回栈与分发在 [AxTvApp.kt](../AxTvApp.kt)，EXTRA_ROUTE 消费在 [MainActivity.kt](../../../../../../MainActivity.kt)。

## 1. 路由表（与源码签名一致）

解析入口：`NavRoutes.parse(raw)`，底层复用 `axtv.app.AppRoutes.parse`（route 形如 `path` 或 `path?k=v&k=v`，参数已百分号解码）。未识别 route 落 `home`。

| route | 参数 | 目标 Composable（真实签名） | 返回行为 |
| --- | --- | --- | --- |
| `home` | — | `HomeFeedScreen(rememberedFocusIndex, onFocusIndexChange, onOpenPlayer, onOpenRoute, modifier)` | 栈底，BACK 退桌面 |
| `detail` | `awemeId` | `DetailScreen(awemeId, onBack, onPlay)` | 弹栈 |
| `player` | `index`（可选） | `PlayerFromFeedScreen(startIndex, onBack, modifier)` → `PlayerOverlayScreen` | 弹栈 |
| `play/aweme` | `awemeId` | `PlayerFromAwemeScreen(awemeId, onBack, modifier)` → `VideoApi.detail` + `PlayerOverlayScreen` | 弹栈 |
| `search` | — | `SearchScreen(onBack, onOpenDetail, modifier)` | 弹栈 |
| `search/results` | `query` | `SearchResultsPage(query, onOpenDetail, onBack, modifier)` | 弹栈 |
| `live` | — | `LiveListScreen(onBack, onOpenRoom)` | 弹栈 |
| `live/room` | `id` | `LiveRoomScreen(roomId, onBack)` | 弹栈 |
| `mix/list` | — | `MixListScreen(onBack, onOpenMix)` | 弹栈 |
| `mix/detail` | `id` | `MixDetailScreen(mixId, onBack, onPlay)` | 弹栈 |
| `mix/play` | `id`、`aweme` | `PlayerFromAwemeScreen(awemeId, …)` | 弹栈 |
| `profile` | — | `ProfileScreen(onBack, onOpenDetail)`（仅本人，无 `profile/{uid}`） | 弹栈 |
| `profile/history` | — | `HistoryScreen(onBack, onPlay)` | 弹栈 |
| `profile/favorites` | — | `FavoritesScreen(onBack, onOpenDetail)` | 弹栈 |
| `profile/later` | — | `WatchLaterScreen(onBack, onPlay)` | 弹栈 |
| `settings` | — | `SettingsScreen(modifier)` | 弹栈 |
| `login` | — | `LoginScreen(onBack, onLoggedIn)` | 成功后弹栈 |

返回栈语义：任意非首页页面 BACK 逐级弹栈；首页（栈底）不拦截，交给系统退到 TV 桌面。

## 2. EXTRA_ROUTE 映射

| widget route | 内部去向 | 说明 |
| --- | --- | --- |
| `home` | `home` | 首页 |
| `search` | `search` | 搜索页 |
| `search/results?query=` | 同 | 搜索结果 |
| `live` | `live` | 直播列表 |
| `live/room?id=` | 同 | 直播间 |
| `mix/detail?id=` | 同 | 合集详情 |
| `mix/play?id=&aweme=` | 同 | 单集播放（`aweme` 决定作品） |
| `play/history?id=` | `profile/history` | 别名；`id` 被忽略 |
| `play/later?id=` | `profile/later` | 别名；`id` 被忽略 |
| `profile/history` / `profile/later` | 同 | 历史 / 稍后再看 |
| `login` | 同 | 登录 |
| 未识别 | `home` | `MainActivity` 记 `Log.w` |

`MainActivity` 消费方式：`AppRoutes.takeRoute(extras)` 优先；没有 route 但 `parseAwemeId` 命中时生成 `detail?awemeId=`；`NavRoutes.isKnown` 为假则落 `home` 并 `Log.w`；`onNewIntent` 通过 `RouteRequest(seq)` 再投递一次。

## 3. 数据接线点

- 首页数据：`ui/home/HomeFeedRepository` → `axtv.app.FeedDataSource.loadFeed(cursor)`（cursor 为页序号，0 首屏）。
- 首页队列播放：`PlayerFromFeedScreen` 复用 `HomeFeedRepository.cachedAwemes`；队列为空时补拉首屏。
- 单作品播放：`PlayerFromAwemeScreen` → `VideoApi.detail(awemeId)` → `WorkDetailResponse.awemeDetail()`。
- 播放队列：`ui/nav/awemePlaylistProvider(awemes, startIndex)` → `axtv.model.VideoPlayUrlResolver.playableUrl(aweme)`，并把 `author/coverUrl` 填进 `VideoPlaylistProvider.Entry`。
- 桌面小组件：`PlayerOverlayScreen` 用 `WidgetRuntime.publishPlayback(...)` 上报当前条目 id/标题/作者/封面/播放态。
- 请求头（含接口身份分支）一律由 `AppRuntime.api` 内的 `UserAgentProvider` 决定。

## 4. 与旧契约的差异（以源码为准）

- 旧契约的 `MixDetailPage(mixId, onPlay(mixId, awemeId, startIndex), onBack, modifier)` 与 `MixPlayPage(...)` 并不存在；实际为 `MixDetailScreen(mixId, onBack, onPlay)` 与 `MixListScreen(onBack, onOpenMix)`，且 `CollectionEpisode` 无 `awemeId`/直链，合集只能单集播放。
- 旧契约的 `profile/{uid}` 路由不存在；`ProfileScreen` 只展示本人。
- 旧的 `LiveBrowsePage` / `PlayHistoryPage` 等名称未使用，实际为 `LiveListScreen` / `HistoryScreen` / `WatchLaterScreen` / `FavoritesScreen`。
- `PlaylistItem` / `VideoPlaylistProvider.Entry` 新增可选字段 `author`、`coverUrl`（默认空），不影响既有调用方。
