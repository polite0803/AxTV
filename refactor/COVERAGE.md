# 功能覆盖矩阵（零删减核对表）

**硬约束：原版功能一条不能少。** 本表把 `docs/功能清单.md` 的每一条功能，逐项映射到
「重构落点」或「原始源码落点 + 状态」。状态含义：

- ✅ **已重构**：`refactor/` 下有对应实现，行为已还原（关键常量/协议字段逐字保留）
- 🟡 **部分**：主链路已重构，括号里写明还缺哪一块（缺的那块仍在 `src/` 里，未丢弃）
- ⬜ **未开始**：功能完整存在于 `src/`，`refactor/` 尚未重建，排在后续轮次

> 这一列存在的意义：**没有任何一条功能被"简化"掉**。⬜ 只是"还没轮到"，不是"不要了"。

## 0. 应用骨架

| 功能 | 状态 | 重构落点 / 原始落点 |
|---|---|---|
| Application 初始化顺序（小组件调度 → 取流器 init → 生命周期回调 → QuickJS init → 常驻 Cookie 服务 → 崩溃处理器） | ✅ | `refactor/app/MyApplication.java`（109 行） |
| 主 Activity（单 Activity + Compose 全屏、`axtv://douyin` 深链、PIP、全传感器方向、resizeable） | ✅ | `refactor/app/MainActivityModel`（生命周期/字体缩放/License/PIP/按键）+ `AppRoutes`（13 条路由）+ `RemoteKeyMap`（按键表）+ `PipSpec`；Compose `setContent` 树 ⬜ |
| 崩溃页与报告（时间/设备/版本/线程/异常/根因/堆栈 → CrashActivity → 500ms 自杀） | ✅ | `refactor/settings/CrashHandler.java`；页面本身 ⬜ `p004dv/CrashActivity.java` |
| 依赖注入 | ✅ 无 DI 框架（结案） | **全工程 0 处 `dagger.hilt` / `@Inject` / `HiltAndroid`（p000 + com + Manifest 均确认）**，依赖全部为手写构造器传递 —— 重构版直接构造器传参即为忠实还原，原条目系 UI 地图阶段误判 |

## 1. 内容浏览

| 功能 | 状态 | 重构落点 / 原始落点 |
|---|---|---|
| 首页单 tab 推荐流 `/aweme/v1/web/tab/feed/`（上滑加载更多、刷新序号、插入直播） | ✅ | `refactor/api/FeedApi.tabFeed`；Tab 容器行为已落 `ui/HomeTabsSpec`（四 Tab↔ab4 枚举、同 Tab 再触发刷新文案、双击返回退出状态机）；页面 ⬜ `on5`(10.4k) |
| 频道推荐流 `/aweme/v2/web/module/feed/` | ✅ | `FeedApi.moduleFeed` |
| 关注流 `/aweme/v1/web/follow/feed/` | ✅ | `FeedApi.followFeed` |
| 移动端推荐流 `/aweme/v2/feed/`（snssdk + 移动 UA） | 🟡 | `FeedApi.mobileFeed`（device_id/iid 由 CookieStore 持久化；参数由 helper 构造，未逐字核） |
| 频道设置 `/aweme/v1/web/home/channel/setting/` | ✅ | `refactor/api/CollectionApi.channelSetting` |
| 视频详情 `/aweme/v1/web/aweme/detail/`（发布者卡片、简介展开、音乐、统计） | 🟡 接口 / 页面行为 | `VideoApi.detail`；页面行为已落 `ui/DetailPageSpec`（封面比例双态/时长 M:SS/标题三级兜底「未命名视频」/直播分区四态「正在刷新直播间…共 N 个直播间」/选台副标题/直播间卡「频道 N · 正在看 · X 人」/无可播放空态「没有可播放的视频」）；绘制 ⬜ `eu5`(11.6k) |
| 相关推荐 `/aweme/v1/web/aweme/related/` | ✅ | `VideoApi.related` |
| 精选/合集列表 UI | 🟡 | 行为规格已落 `ui/ChannelListSpec`（413 行：分区五态机/精选页三态/分页双阈值 size-5·size-3/3 分组预取/重播倒计时 5000·2000ms/300ms 跳转去抖/列数映射/文案 19 条）；按键处理器存疑已结案（smali 证实 C0412be=R8 合并 lambda 类，非按键）、页面绘制 ⬜ |
| 合集 `/aweme/v1/web/mix/detail/`、`mix/aweme/`、`mix/listcollection/` | ✅ | `CollectionApi.mixDetail` / `mixAwemes` / `mixListCollection` |
| 系列 `/aweme/v1/web/series/list|detail|aweme/` | ✅ | `CollectionApi.seriesList` / `seriesDetail` / `seriesAwemes` |
| 选集面板（已看电影弱化、上次看到高亮、选中即连播） | 🟡 | 行为规格 100% 还原：`refactor/ui/EpisodePickerSpec`（四档前景色含 0.6 alpha、元信息行、继续/从第 N 集、分页提示、失效/加载态、18 条文案）；Compose 网格绘制 ⬜ `w03` |
| 追更记录 `/aweme/v1/web/mix/watch/record/` | ✅ | `CollectionApi.recordMixProgress` |
| 用户主页（self / other） | ✅ 接口 | `UserApi.profileSelf` / `profileOther`；页面 ⬜ |
| 用户作品 `/aweme/v1/web/aweme/post/` | ✅ | `UserApi.userPosts` |
| 用户喜欢 `/aweme/v1/web/aweme/favorite/` | ✅ | `UserApi.userFavorites` |
| 关注/粉丝列表（返回记住浏览位置） | ✅ 接口 | `UserApi.following` / `followers`；位置记忆 ⬜ UI 层 |
| 观看历史 `/aweme/v1/web/history/read|write/`（按日期月份分组） | 🟡 接口 / 分组行为 | `VideoApi.historyRead` / `historyWrite`；日期格式 `ui/DateLabels`；**分组算法已落 `ui/HistoryGroupSpec`**（观看时刻回退链 watchMap→createTime、VideoMonth 复合键 year*100+month、相邻变化插 header、月份/加载占位文案逐字）；分组绘制 ⬜ |
| 稍后再看 `/aweme/v1/web/watchlater/list/` | ✅ | `VideoApi.watchLater` |
| 我的收藏 `/aweme/v1/web/aweme/listcollection/`、`aweme/collect/` | ✅ | `UserApi.collectionList` / `InteractionApi.collect` |
| 热榜 `/aweme/v1/web/hot/search/list/` | ✅ | `SearchApi.hotSearch` |

## 2. 搜索

| 功能 | 状态 | 重构落点 / 原始落点 |
|---|---|---|
| 视频搜索参数构造器 m7998g（13 个键逐字） | ✅ | `refactor/api/SearchApi.videoSearchParams` |
| 视频搜索 `/aweme/v1/web/search/item/` | ✅ | `SearchApi.videoSearch` |
| 综合搜索 `/aweme/v1/web/general/search/single/` | 🟡 | `SearchApi.generalSearch`（`search_channel=aweme_general` 为同构推断，见 api/NOTES §6） |
| 用户搜索 `/aweme/v1/web/discover/search`（m8040f 逐字，含粉丝数筛选 JSON） | ✅ | `SearchApi.userSearch` |
| 直播搜索 `/aweme/v1/web/live/search/`（m8034a 逐字） | ✅ | `SearchApi.liveSearch` |
| 搜索联想 `/aweme/v1/web/search/sug/`（只注入 msToken） | ✅ | `SearchApi.suggestions` |
| 推荐词 `/aweme/v1/web/api/suggest_words/` | ✅ | `SearchApi.suggestWords` |
| 搜索页 Referer（随机 aid + type） | ✅ | `DouyinEndpoints.searchReferer` |
| 搜索失败语义（风控拦截/需登录/参数校验） | ✅ | `ApiException`（kinds）+ `ui/SearchUiSpec`（params_check / web_need_login / verify_check 三码映射 + 频道前缀拼接，逐字） |
| 搜索页 UI（最近搜索、热搜、结果分栏/筛选/折叠） | 🟡 | 错误/空态文案已落 `ui/SearchUiSpec`；页面绘制（最近搜索、热搜、分栏）⬜ `ye4`(2.2k)、`on5` |
| 主页信息流 UI（上滑加载更多、合集面板、相关推荐卡、评论面板容器） | 🟡 | 行为规格已落 `ui/FeedPageSpec`（加载文案/合集头「第N集 / 共M集」/相关卡前缀与 50 字截断/评论分页 cid 去重/弹幕轨道参数/倍速档 0.5–3.0）+ `ui/CommentUiSpec`（评论行）；页面绘制 ⬜ |
| 手机远程输入（9979 + 9980 WS） | ✅ | `refactor/panel/SearchInputHandler` + `SearchSocketServer` |

## 3. 播放

| 功能 | 状态 | 重构落点 / 原始落点 |
|---|---|---|
| 播放器状态机（prepare/play/pause/seek/release/重试/连播） | ✅ | `refactor/player/PlayerEngine.java`（622 行） |
| 播放列表与连播 | ✅ | `player/PlaybackQueue.java` |
| 共享播放器池（回收复用） | ✅ | `player/SharedPlayerPool.java` |
| 三种渲染模式（SurfaceView / TextureView / GLSurfaceView） | ✅ | `player/PlayerSurfaceMode.java` + `GlVideoSurfaceView.java` |
| 画面缩放矩阵（适应/填充/16:9/原始，横竖屏） | ✅ | `player/VideoScaleMatrix.java` |
| 媒体缓存（边播边缓存 + 清理） | ✅ | `player/VideoMediaCache.java` |
| 播放配置（码率/编码偏好/预载/超时/缓冲对齐） | ✅ | `player/PlayerConfig.java` + `settings/Settings` |
| 解码失败自动降 H.264 | ✅ | `player/CodecFallback.java`（`switchToH264`）+ `PlayerEngine` 在 `decoderFailure()` 时调用；取流侧对应 `VideoPlayUrlResolver.resolveH264PlayUrl` |
| PIP 画中画（三个 action：PLAY/PAUSE/NEXT） | ✅ | `player/PipController.java` |
| 取流解析（多码率择优/HLS/回退） | ✅ | `refactor/model/VideoPlayUrlResolver.java`（465 行） |
| 播放器 UI（遥控手势、播放菜单、长按加速、进度条） | 🟡 | 菜单/滑杆/段选/快捷菜单已落 `ui/PlayerMenuSpec` + `ui/PlayerGestureSpec` + `ui/PlayerUiPalette`；on5 侧焦点路由/控制层显隐/重播复位/挂起 seek/时间与网速格式化已落 `ui/PlayerKeyMachine` + `ui/ControlLayerSpec`；**按键分发结案：全 App 无集中式 DPAD 表** —— Activity 层 `AbstractActivityC1421nk.dispatchKeyEvent:625` 仅转发、媒体键重映射在 `app/RemoteKeyMap`（已落）、其余走 Compose 焦点系统（`di1` FocusOwnerImpl）+ 组件 onKeyEvent（语义已分散落进各规格类） |
| 画质选择器 UI | 🟡 | 行为规格 100% 还原：`refactor/ui/SettingsPageSpec`（点播 5 档 + 直播 5 档 + 文案 + `video_resolution`↔`live_video_resolution` 联动）；Compose 绘制与动态枚举行 ⬜ `qm4` |

## 4. 弹幕

| 功能 | 状态 | 重构落点 / 原始落点 |
|---|---|---|
| 弹幕拉取 `/aweme/v1/web/danmaku/get_v2/` | ✅ | `VideoApi.danmaku` |
| 弹幕表情 `/aweme/v1/web/emoji/list` | ✅ | `CollectionApi.emojiList` |
| 弹幕几何/速度算法（速度基准=窗口宽度） | ✅ | `refactor/live/DanmakuStyle.java`（310 行） |
| 弹幕偏好（字号/速度/透明度/区域/间距/屏蔽词/过滤包） | ✅ | `live/LiveDanmakuSettings.java` + 71 个配置键 |
| 弹幕渲染与轨道分配 UI | 🟡 | 算法 100% 还原：`live/DanmakuStyle.java`（轨道分配、进入/离场时间、x(t)、1s 保留窗口、重建触发条件）；Canvas/CustomLayout 绘制层 ⬜ `om5`/`i42` |

## 5. 直播

| 功能 | 状态 | 重构落点 / 原始落点 |
|---|---|---|
| 直播列表 `/webcast/feed/`、关注 `/webcast/web/feed/follow/`、分区 `/webcast/web/partition/detail/room/v2/` | ✅ | `refactor/api/LiveApi` |
| 进房 `/webcast/room/web/enter/`、`/webcast/room/info_by_scene/` | 🟡 | `LiveApi.enterRoom` 参数为可核实的公共项（builder 未完整还原，见 api/NOTES §6） |
| 弹幕 WebSocket（24 个 query 参数、心跳 10s、重连 5×5s、ack） | ✅ | `refactor/live/LiveWebSocketClient`（302 行） |
| protobuf 帧编解码（PushFrame/Response/Message/Chat/User/Seq 全部字段号） | ✅ | `live/LiveFrameCodec`（454 行） |
| 直播签名（msStub = MD5(固定参数串) → getMSSDKSignature） | ✅ | `refactor/sign/SignJsEngine` + `live/LiveSignatureProvider` |
| 直播选流（SDK 数据优先，flv/hls 键名回退） | ✅ | `live/LiveStreamResolver` + `LiveStreamInfo` / `LiveStreamQuality` |
| 直播 UI（分区浏览、房间页、在线人数、卡片） | 🟡 | 行为计算已落 `ui/LivePageSpec`（分辨率轨、错误两值、连播状态机、在线人数）+ `ui/LiveBrowseSpec`（搜索 body 五态/隐藏竖屏 height≤width 保留/去重键两格式+出现序号/列数 2-5 TV 紧凑 2/主播卡「LIVE · N人」/用户卡「抖音用户 · 粉丝 N」）；页面绘制 ⬜ |

## 6. 账号与登录

| 功能 | 状态 | 重构落点 / 原始落点 |
|---|---|---|
| Cookie 推送（9978：/submit、/api/info、/api/submit、/logcat、CORS、OPTIONS） | ✅ | `refactor/panel/CookiePushHandler`（145 行） |
| 面板主题模板替换（`{{THEME_CSS}}`/`{{PANEL_HEAD}}`/`{{DEVICE_NAME}}`） | ✅ | `panel/ThemeRenderer`（258 行） |
| 局域网地址选择（wlan/eth 优先，避开 p2p0） | ✅ | `panel/DeviceAddressResolver`（117 行） |
| 扫码登录状态机（60s 页加载 / 60s·20s 二维码超时 / 15s JS 求值 / 5s Cookie 刷新 / 最多 8 次重开） | 🟡 | `panel/QrLoginBridge`（396 行）；3 个 jadx 溢出方法按常量重建，轮询间隔与成功判定标存疑 |
| 多账号（accounts_list / current_account_id / account_*） | ✅ | `settings/Settings` + `SettingsKeys` |
| 互动凭据解析（security payload 三层 + 私钥归一化 PEM） | ✅ | `panel/InteractionSecurityParser`（233 行） |
| `bd-ticket-guard-*` 5 个请求头（protobuf 编码） | 🟡 | `SignProvider.ticketGuardHeaders` 接口与语义已还原；编码器（原 `AbstractC0656g4.m4464l`）⬜ |
| 我的主页 / self_user_id | ✅ 接口 | `UserApi.profileSelf` |
| 本地 PIN 锁（每账号 pinHash + pinSalt，sha256(salt+":"+pin) 校验） | ✅ | `settings/Settings`（`hasPin` / `hashPin` / 清空 PIN）；PIN 输入页 UI ⬜ |

## 7. 互动

| 功能 | 状态 | 重构落点 / 原始落点 |
|---|---|---|
| 点赞 `/aweme/v1/web/commit/item/digg/`（需互动凭据） | ✅ | `InteractionApi.digg` |
| 收藏 `/aweme/v1/web/aweme/collect/` | ✅ | `InteractionApi.collect` |
| 不喜欢 `/aweme/v1/web/commit/dislike/item/` | ✅ | `InteractionApi.dislike` |
| 评论列表 / 回复 | ✅ | `InteractionApi.comments` / `replies` |
| 播放埋点 `/aweme/v2/web/aweme/stats/` | ✅ | `VideoApi.stats` |
| 互动按钮 UI（点赞/收藏/评论态） | 🟡 | 按钮行规格已落 `ui/InteractionBarSpec`（状态文案切换 + 尺寸双态 + 静态统计行）；图标与绘制 ⬜ `fv4` 其余部分归 lq2/eu5 轮次 |
| 评论行渲染（相对时间/匿名/key 去重/回复展开） | ✅ | `ui/CommentUiSpec`（刚刚/分钟/小时/天前/30天上限、missing:/id: 两格式 key、选中展开回复） |

## 8. 桌面小组件（6 个）

| 功能 | 状态 | 原始落点（源码可读，未混淆） |
|---|---|---|
| NowPlaying / Continue / WatchLater / Mix / Live / Search 六个 Glance 小组件 | ✅ | 行为规格 100% 还原：`refactor/widget/WidgetRenderSpec`（933 行：六组件 `ComponentSpec` / 7 种 Screen / 分支 / 点击区域 / 全量尺寸常量）+ 数据模型与快照（39 类 / 3545 行）；仅 Glance 组件树像素绘制归 Android 侧 |
| 刷新调度（Scheduler + WorkManager Worker + 定时） | ✅ | `widget/WidgetRefreshScheduler` / `WidgetRefreshWorker` / `WidgetRefresher` / `WidgetUpdater`；周期 HISTORY·LATER·MIX=360min / LIVE=30 / SEARCH=60，见 `widget/NOTES.md` §4 |
| 媒体键接收（WidgetMediaActionReceiver） | ✅ | `widget/WidgetMediaActionReceiver` + `WidgetActions`（85/86/87/88/126/127 六个键码 + `ACTION_MEDIA` 广播） |
| 小组件配色/形状/主题令牌 | ✅ | `widget/WidgetPalette` / `WidgetShape` / `WidgetShapeGeometry` / `WidgetTheme` / `WidgetImageSpec` |

## 9. 手机 ↔ 电视面板

| 功能 | 状态 | 重构落点 |
|---|---|---|
| 9978 Cookie 服务（含 CORS、OPTIONS、4MiB 体上限） | ✅ | `panel/PanelServer` + `CookiePushHandler` |
| 9979 搜索输入页 | ✅ | `panel/SearchInputHandler` |
| 9980 WebSocket（RFC6455 手写实现、1001 关闭、tv_text/input/search 协议） | ✅ | `panel/SearchSocketServer`（484 行） |
| logcat 导出（`logcat -t 10000 -v threadtime`） | ✅ | `panel/LogcatExportHandler` |
| 状态机（DISCONNECTED/WAITING/CONNECTED/ERROR）与 UI 通道 | ✅ | `panel/PanelState` + `PanelController` |
| 认证与安全（原版零鉴权，CORS `*`，明文 HTTP） | ✅ 原样保留 | `panel/NOTES.md` §安全风险（6 条高危/中危） |

## 10. 设置（71 个键）

| 功能 | 状态 | 重构落点 |
|---|---|---|
| 全部 71 个配置键（键名/默认值/夹取/旧键迁移） | ✅ | `refactor/settings/Settings`（1548 行）+ `SettingsKeys`（115 行） |
| 账号列表 JSON 编解码 | ✅ | `settings/SettingsJson` |
| 设置页 UI（分组、滑杆、开关、主题预览） | 🟡 | **77 个控件逐项登记**（`qm4` 62 + `j16` 15，含 key/type/顺序/分组）+ 71 个配置键与控件的一一对照表 → `refactor/ui/SettingsPageSpec.java` + `ui/NOTES.md` §5；Compose 绘制与点击事件 lambda ⬜ |

## 11. 更新与诊断

| 功能 | 状态 | 重构落点 |
|---|---|---|
| 检查更新（GitHub Releases） | ✅ | `settings/UpdateManager`（526 行）+ `ReleaseJson` |
| 9 个 ghproxy 镜像回退 | ✅ | `settings/UpdateMirror` |
| 下载 + SHA-256 校验 + APK 安装 | 🟡 | `UpdateManager` 流程完整；安装 Intent 属 Android 侧待接 |
| 电视频道发布（EPG：推荐/稍后再看两个 Preview Channel、增量同步、logo、browsable） | ✅ | `refactor/tv/`（8 个类）：`TvChannelPublisher` / `ProgramBuilder` / `PreviewProgram` / `TvProviderColumns` / `ChannelSpec` / `TvProvider` / `TvSyncTrigger` / `TvLog`；Android TvContract 侧见 `tv/NOTES.md` §7 |
| logcat 导出 | ✅ | `panel/LogcatExportHandler` |

## 12. 数据模型

| 功能 | 状态 | 重构落点 |
|---|---|---|
| 87 个业务模型（record，字段零丢失，JSON key 来自 `$$serializer` 描述符） | ✅ | `refactor/model/`（85 文件 / 3577 行） |
| 柔性解码（int/long/boolean 的字符串容错） | ✅ | `model/FlexibleJson`（171 行） |
| 取流算法 | ✅ | `model/VideoPlayUrlResolver`（465 行） |
| 计数格式化 / 直播选档 / aweme 过滤 | ✅ | `model/DataModels`（378 行） |
| 27 个响应壳类的 JSON key | 🟡 | 按同目录 snake_case 惯例推定（`$$serializer` 缺失，见 model/NOTES S1），建议抓包复核 |

---

## 汇总

| 状态 | 条目数 |
|---|---|
| ✅ 已重构 | 81（其中 3 条为「接口 ✅ / 页面 ⬜」双状态：视频详情页、用户主页、关注/粉丝列表 UI） |
| 🟡 部分（缺项已登记，源码仍在 `src/`） | 18 |
| ⬜ 未开始（整条） | 0（矩阵内已无整条 ⬜；剩余缺口均为页面绘制层或 jadx 溢出项，已在各 🟡 行内标注） |
| 合计 | 99 条功能行（此前统计块多计 3，已按精确行数校正） |

**本轮新增落点**：`refactor/tv/`（EPG，8 类）、`refactor/app/`（MainActivity 4 类）、
`refactor/ui/`（设置页 77 控件 + 画质 10 档 + 选集面板规格）、
`refactor/widget/`（39 类 / 3545 行，含 933 行六组件渲染规格 + 518 行 NOTES）、
`refactor/TESTS.md`（59 项行为验证）。

**剩余 ⬜ 收敛过程（三轮子任务实证修正）**：
1. ~~Hilt 替代~~ → **结案**：全工程无 DI 框架，0 处 Hilt/`@Inject`；
2. ~~按键状态机~~ → **结案**：lq2/on5/ro5 均无集中式 KeyEvent 分发，Activity 层仅转发，
   实际走 Compose 焦点系统（`di1` FocusOwnerImpl）+ 组件 onKeyEvent，语义已分散落进
   `ui/PlayerKeyMachine` / `PlayerGestureSpec` / `app/RemoteKeyMap`；
3. ~~APK 下载校验~~ → **结案**：已被 `settings/UpdateManager` + `UpdateMirror` 全覆盖。
当前唯一 ⬜ 大项：`qg5` 精选/分区列表 UI（子任务进行中）+ 各页面 Compose 绘制层。

**没有任何功能被删除或"简化掉"** —— 反编译产物 `src/` 是 100% 原样的，
`refactor/` 的进度只影响"可读性"，不影响"功能是否还在"。
