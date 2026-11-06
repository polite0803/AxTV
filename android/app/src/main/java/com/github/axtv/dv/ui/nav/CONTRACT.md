# 导航接线约定（ui/nav）

本目录是所有「路由 → 页面」的唯一接线点。路由解析在 [NavRoutes.kt](NavRoutes.kt)，真实页面装配在 [NavScreens.kt](NavScreens.kt)，返回栈与分发在 [AxTvApp.kt](../AxTvApp.kt)，EXTRA_ROUTE 消费在 [MainActivity.kt](../../../../../../MainActivity.kt)。

## 1. 路由表（与源码签名一致）

解析入口：`NavRoutes.parse(raw)`，底层复用 `axtv.app.AppRoutes.parse`（route 形如 `path` 或 `path?k=v&k=v`，参数已百分号解码）。未识别 route 落 `home`。

| route | 参数 | 目标 Composable（真实签名） | 返回行为 |
| --- | --- | --- | --- |
| `home` | `tab`（可选：推荐/关注/移动推荐） | `HomeFeedScreen(rememberedFocusIndex, onFocusIndexChange, onOpenPlayer, onOpenRoute, modifier, initialTab)`；第二排标签决定数据源（推荐=tab/feed、关注=follow/feed、移动推荐=v2/feed、精选=跳此页路由） | 栈底，BACK 退桌面 |
| `detail` | `awemeId`、`searchIndex`（可选） | `DetailScreen(awemeId, onBack, onPlay(awemeId, queueIndex), queueIds)`：搜索来源带队列，详情页内可小窗播放/上一条/下一条，全屏播放按队列连播 | 弹栈 |
| `player` | `index`（可选）、`source`（可选：recommend/follow/mobile） | `PlayerFromFeedScreen(startIndex, onBack, modifier, source)` → `PlayerOverlayScreen` | 弹栈 |
| `play/aweme` | `awemeId` | `PlayerFromAwemeScreen(awemeId, onBack, modifier, onOpenAuthor)` → `VideoApi.detail` + `PlayerOverlayScreen` | 弹栈 |
| `author` | `secUid`、`name`（可选） | `AuthorChannelScreen(secUid, nickname, onBack, onPlay(startAwemeId), onLogin)`：作者资料 + `aweme/post` 作品列表；「播放全部」/点任意作品进 `author/play` | 弹栈 |
| `author/play` | `secUid`、`aweme`（可选） | `PlayerFromAuthorScreen(secUid, awemeId, onBack, onOpenAuthor)`：以该作者作品为队列，从 `aweme` 起播到队尾并按 `max_cursor` 自动续页 | 弹栈 |
| `search` | — | `SearchPage(query, onOpenResults, onBack)`：输入/热搜/联想，提交后进 `search/results` | 弹栈 |
| `search/results` | `query` | `SearchResultsPage(query, onOpenDetail, onOpenVideo, onBack, modifier)` | 弹栈 |
| `search/play` | `index`（可选） | `PlayerFromSearchScreen(startIndex, onBack, modifier)` → 搜索结果队列连播 | 弹栈 |
| `live` | — | `LiveListScreen(onBack, onOpenRoom)` | 弹栈 |
| `live/room` | `id`、`webRid`（可选） | `LiveRoomScreen(roomId, webRid, onBack)`（全屏播放窗口：顶栏不渲染、系统栏沉浸） | 弹栈 |
| `mix/list` | — | `MixListScreen(onBack, onOpenMix)` | 弹栈 |
| `mix/detail` | `id` | `MixDetailScreen(mixId, onBack, onPlay)` | 弹栈 |
| `mix/play` | `id`、`aweme` | `PlayerFromMixScreen(mixId, awemeId, …)`：已加载剧集整队列连播 + 队尾续页 | 弹栈 |
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
| `search/results?query=` | 同 | 搜索结果（从详情返回会回到最近一次搜索，页内改词会替换栈顶路由） |
| `live` | `live` | 直播列表 |
| `live/room?id=` | 同 | 直播间 |
| `mix/detail?id=` | 同 | 合集详情 |
| `mix/play?id=&aweme=` | 同 | 合集内连播（未加载到的集按同一入口继续翻页） |
| `play/history?id=` | `profile/history` | 别名；`id` 被忽略 |
| `play/later?id=` | `profile/later` | 别名；`id` 被忽略 |
| `profile/history` / `profile/later` | 同 | 历史 / 稍后再看 |
| `login` | 同 | 登录 |
| 未识别 | `home` | `MainActivity` 记 `Log.w` |

`MainActivity` 消费方式：`AppRoutes.takeRoute(extras)` 优先；没有 route 但 `parseAwemeId` 命中时生成 `detail?awemeId=`；`NavRoutes.isKnown` 为假则落 `home` 并 `Log.w`；`onNewIntent` 通过 `RouteRequest(seq)` 再投递一次。

## 3. 数据接线点

- 首页数据：`ui/home/HomeFeedRepository` → `axtv.app.FeedDataSource.loadFeed(cursor, recentIds)`（cursor 为已加载页数，加载更多从 `refresh_index=2` 开始并回传最近作品 id）。
  首屏与「换一批」（再点一次已选中的推荐 Tab）走 `FeedDataSource.refresh(refreshIndex, recentIds)`，`refreshIndex` 读写设置键 `feed_refresh_index`，跨重启继续自增。
- 首页队列播放：`player?source=recommend|follow|mobile` 决定队列来源（默认 recommend）。
  `PlayerFromFeedScreen` 按 source 取对应仓储快照（`HomeFeedRepository` / `FollowFeedRepository` / `MobileFeedRepository`），
  队列为空时按各自首屏语义补拉一页（recommend 走 `refresh()`，follow 走 `refresh()`=清去重后 cursor=0，
  mobile 走 `load(1)` 冷启动）；到队尾按各自分页继续追加。
- 精选队列播放：`FeaturedRepository.cache(channel)` 记录分区上下文；`PlayerFromFeaturedScreen` 到队尾时按该分区
  `loadMore(channel, lastVisible)` 继续翻页。
- 搜索队列播放：`SearchPlaybackRepository` 缓存视频 tab 的 aweme（首屏与界面翻页都会更新）；搜索结果进详情页时
  把队列 id 顺序带给 `DetailScreen`（小窗播放/上一条/下一条），全屏播放进 `search/play` 按搜索顺序连播并在队尾继续翻页。
- 作者频道播放：`AuthorChannelScreen` 用 `UserApi.profileOther(secUid, "channel_pc_web")` + `UserApi.userPosts(secUid, maxCursor, 18)`
  拉资料与作品，并把结果写进 `ui/author/AuthorRepository`；`PlayerFromAuthorScreen` 用该快照组成队列
  （从选中作品播到队尾，不回头），队尾按 `max_cursor` 继续翻页直到 `has_more=false`。
  入口：详情页作者行、播放器底部控制栏「作者」按钮（`PlaylistItem.authorSecUid` 为空时不显示）。
- 合集队列播放：`CollectionEpisode.aweme` 保留接口原始作品，`PlayerFromMixScreen` 用已加载剧集组成整队列
  从选中集顺序连播（不回头），队尾按 `CollectionRepository.loadMoreEpisodes` 继续翻页；分集面板选中某集时
  以该集重建队列起播。失效合集的详情页提供「从本机列表移除」（`RemovedCollections`，本机过滤）。
- 单作品播放：`PlayerFromAwemeScreen` → `VideoApi.detail(awemeId)` → `WorkDetailResponse.awemeDetail()`。
- 播放队列：`ui/nav/awemePlaylistProvider(awemes, startIndex)` → `axtv.model.VideoPlayUrlResolver.playableUrl(aweme)`，并把 `author/coverUrl` 填进 `VideoPlaylistProvider.Entry`。
- 桌面小组件：`PlayerOverlayScreen` 用 `WidgetRuntime.publishPlayback(...)` 上报当前条目 id/标题/作者/封面/播放态。
- 请求头（含接口身份分支）一律由 `AppRuntime.api` 内的 `UserAgentProvider` 决定。

## 4. 与旧契约的差异（以源码为准）

- 旧契约的 `MixDetailPage(mixId, onPlay(mixId, awemeId, startIndex), onBack, modifier)` 与 `MixPlayPage(...)` 并不存在；实际为 `MixDetailScreen(mixId, onBack, onPlay)` 与 `MixListScreen(onBack, onOpenMix)`，且 `CollectionEpisode` 无 `awemeId`/直链，合集只能单集播放。
- 旧契约的 `profile/{uid}` 路由不存在；`ProfileScreen` 只展示本人。他人主页走 `author?secUid=`（作者频道），
  资料与作品分别对应 `UserApi.profileOther` / `UserApi.userPosts`。
- 旧的 `LiveBrowsePage` / `PlayHistoryPage` 等名称未使用，实际为 `LiveListScreen` / `HistoryScreen` / `WatchLaterScreen` / `FavoritesScreen`。
- `PlaylistItem` / `VideoPlaylistProvider.Entry` 新增可选字段 `author`、`coverUrl`（默认空），不影响既有调用方。
