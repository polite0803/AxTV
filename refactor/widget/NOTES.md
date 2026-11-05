# 桌面小组件模块（`axtv.widget`）重构说明

> 源码：`src/app/com/github/mytv/p004dv/widget/`（**49 个 java / 13,104 行**，未被 R8 混淆，类名可读）。
> 产出：`refactor/widget/axtv/widget/` 下 **39 个 Java / 3,536 行**。
> 编译：`javac.exe --release 17 -encoding UTF-8 -d D:/OneManager/AxTV/.workbuddy/build-widget`（零错误，81 个 class）。
> `src/` 严格只读，本轮未修改/移动/删除任何源文件。

## 0. 功能口径（如何做到「一条不丢」）

原版是 Kotlin + Glance/Compose，**Compose 组件树无法用纯 JDK 绘制**。按「行为规格」而非「像素」落地：
把「显示哪些字段 / 什么条件下切换状态 / 点哪里做什么 / 多大尺寸 / 什么形状 / 刷新周期与触发」
全部转成**可执行的纯 Java 模型**（`WidgetRenderSpec`，933 行）+ 接口隔离；
只有「像素怎么画」交给 Android 侧（见 §8 待迁移，仅 4 类真正依赖 Android）。

- `WidgetRenderSpec.resolve(WidgetId, ViewInput)` 是可执行的分支判定器，已用 jshell 自检六组件全部状态。
- 每个 `ComponentSpec` 内含 `List<Branch>`（**每个 `if` 分支一行**）、`List<ContentSpec>`（显示字段 + 点击区域）、
  `List<ClickRegion>`（点击→动作）。`WidgetRenderSpec.Strings` 收录**全部 42 条中文文案**，`Icons` 收录**全部 21 个 drawable**。
- `ClickRegion.materialize(args...)` 把规格物化成真实 `WidgetAction`（可再 `describe()` 成 Intent/Broadcast 描述）。

---

## 1. 类映射表（49 个原文件，逐行；每个文件都有独立一行）

| # | 原文件（行数） | 新类 / 落点 | 新类行数 |
|---:|---|---|---:|
| 1 | `WidgetUiKt.java` (4353) | `WidgetRenderSpec`（Strings/Icons/StatusSpec/Branch/公共组件规格） | 933 |
| 2 | `DesktopWidgetsKt.java` (1457) | `WidgetRenderSpec`（Dimens/六组件 ContentSpec/尺寸阈值） | 同上 |
| 3 | `NowPlayingWidget.java` (353) | `WidgetRenderSpec.spec(NOW_PLAYING)` + `NowPlayingContent/Idle` 规格 | 同上 |
| 4 | `NowPlayingWidgetReceiver.java` (16) | `WidgetReceiverLifecycle`（无 kind，不走周期调度） | 38 |
| 5 | `ContinueWidget.java` (151) | `WidgetRenderSpec.spec(CONTINUE)` | 同上 |
| 6 | `ContinueWidgetReceiver.java` (21) | `WidgetReceiverLifecycle` / `WidgetUpdater.receiverName(HISTORY)` | — |
| 7 | `WatchLaterWidget.java` (152) | `WidgetRenderSpec.spec(WATCH_LATER)` | 同上 |
| 8 | `WatchLaterWidgetReceiver.java` (21) | `WidgetReceiverLifecycle` / `receiverName(LATER)` | — |
| 9 | `MixWidget.java` (157) | `WidgetRenderSpec.spec(MIX)`（含双调色板切换） | 同上 |
| 10 | `MixWidgetReceiver.java` (21) | `WidgetReceiverLifecycle` / `receiverName(MIX)` | — |
| 11 | `LiveWidget.java` (147) | `WidgetRenderSpec.spec(LIVE)` | 同上 |
| 12 | `LiveWidgetReceiver.java` (21) | `WidgetReceiverLifecycle` / `receiverName(LIVE)` | — |
| 13 | `SearchWidget.java` (205) | `WidgetRenderSpec.spec(SEARCH)`（provideGlance 完整反编译） | 同上 |
| 14 | `SearchWidgetReceiver.java` (21) | `WidgetReceiverLifecycle` / `receiverName(SEARCH)` | — |
| 15 | `BaseWidgetReceiver.java` (43) | `WidgetReceiverLifecycle`（onEnabled/onUpdate/onDisabled） | 38 |
| 16 | `ComposableSingletons$DesktopWidgetsKt.java` (45) | `WidgetRenderSpec.Strings.IDLE_CONTINUE/IDLE_OPEN_RECOMMEND` + Idle 两个 ClickRegion（**脚手架已合并，业务语义保留**） | 同上 |
| 17 | `WidgetRefresher.java` (1271) | `WidgetRefresher` + `WidgetDataSource` | 174 + 93 |
| 18 | `WidgetUpdater.java` (308) | `WidgetUpdater` | 110 |
| 19 | `WidgetRefreshScheduler.java` (195) | `WidgetRefreshScheduler` | 130 |
| 20 | `WidgetRefreshWorker.java` (110) | `WidgetRefreshWorker` | 49 |
| 21 | `WidgetStore.java` (383) | `WidgetStore` + `WidgetStorage` | 194 + 37 |
| 22 | `WidgetDataKt.java` (78) | `WidgetData` | 87 |
| 23 | `WidgetPlaybackStore.java` (40) | `WidgetPlaybackStore`（内存 StateFlow 语义） | 55 |
| 24 | `WidgetPlayback.java` (188) | `WidgetPlayback` | 32 |
| 25 | `VideoListSnapshot.java` (169) | `VideoListSnapshot` | 39 |
| 26 | `LiveSnapshot.java` (193) | `LiveSnapshot` | 40 |
| 27 | `MixSnapshot.java` (169) | `MixSnapshot` | 39 |
| 28 | `SearchSnapshot.java` (169) | `SearchSnapshot` | 39 |
| 29 | `WidgetVideo.java` (254) | `WidgetVideo` | 44 |
| 30 | `WidgetVideo$$serializer.java` (125) | `WidgetVideo` 的 `@JsonKey`（**descriptor 脚手架已合并**） | — |
| 31 | `WidgetMixItem.java` (296) | `WidgetMixItem` | 38 |
| 32 | `WidgetMixItem$$serializer.java` (140) | `WidgetMixItem` 的 `@JsonKey`（脚手架已合并） | — |
| 33 | `WidgetLiveItem.java` (201) | `WidgetLiveItem` | 32 |
| 34 | `WidgetLiveItem$$serializer.java` (106) | `WidgetLiveItem` 的 `@JsonKey`（脚手架已合并） | — |
| 35 | `WidgetEpisode.java` (117) | `WidgetEpisode` | 20 |
| 36 | `WidgetEpisode$$serializer.java` (90) | `WidgetEpisode` 的 `@JsonKey`（脚手架已合并） | — |
| 37 | `WidgetHotWord.java` (116) | `WidgetHotWord` | 25 |
| 38 | `WidgetHotWord$$serializer.java` (91) | `WidgetHotWord` 的 `@JsonKey`（脚手架已合并） | — |
| 39 | `WidgetImages.java` (454) | `WidgetImages` + `WidgetImageSpec` + `WidgetShapeGeometry` | 135+142+129 |
| 40 | `WidgetImages$load$bitmap$1.java` (60) | `WidgetImages.renderPlan`（**协程 lambda 脚手架已合并**，render 调用语义保留） | — |
| 41 | `WidgetPalette.java` (91) | `WidgetPalette`（48 令牌 + 27 providers + 6 额外） | 208 |
| 42 | `WidgetShape.java` (19) | `WidgetShape` + `WidgetShapeGeometry`（极坐标公式） | 15+129 |
| 43 | `WidgetThemeKt.java` (168) | `WidgetTheme` | 77 |
| 44 | `WidgetRoutes.java` (82) | `WidgetRoutes` | 89 |
| 45 | `WidgetActionsKt.java` (48) | `WidgetActions` + `WidgetAction` | 46+51 |
| 46 | `RefreshWidgetAction.java` (101) | `RefreshWidgetAction` | 28 |
| 47 | `WidgetMediaActionReceiver.java` (41) | `WidgetMediaActionReceiver` | 38 |
| 48 | `WidgetKind.java` (29) | `WidgetKind` | 39 |
| 49 | `WidgetLoadState.java` (18) | `WidgetLoadState` | 13 |

**新增（无对应原文件，纯 JDK 抽象/聚合）**：`JsonKey`(23)、`JsonCodec`(26)、`InMemoryWidgetStorage`(49)、
`WidgetStorage`(37，与上表 #21 共享)、`WidgetDataSource`(93，与 #17 共享)、`WidgetSettings`(52)、
`WidgetEnvironment`(137)、`WidgetImageSpec`(142，与 #39 共享)、`WidgetShapeGeometry`(129，与 #39/#42 共享)、
`WidgetRenderSpec`(933，与 #1/#2 共享)。

> 5 个 `$$serializer` 的 JSON key 由 descriptor 的 `ip3.m5695k("...")` 抠出，逐字写进各 record 的 `@JsonKey`；
> 4 个 Snapshot 无独立 `$$serializer`（descriptor 在 `write$Self$app` 里按 index 暴露），见 §10 存疑。

---

## 2. 数据模型字段与 JSON key 全表

| 模型 | 字段（`@JsonKey`） | 默认值 / 备注 |
|---|---|---|
| `WidgetVideo` | `id`,`title`,`author`,`avatarUrl`,`coverUrl`,`duration`,`likes` | 7×String 全默认 `""`；`nz()` 归一 |
| `WidgetMixItem` | `id`,`name`,`author`,`coverUrl`,`episode`(int),`total`(int),`resumeAwemeId`,`nextEpisodes`(List) | `""`/`0`/`[]` |
| `WidgetLiveItem` | `roomId`,`title`,`author`,`avatarUrl`,`viewers` | 5×String 默认 `""` |
| `WidgetEpisode` | `number`(int),`awemeId` | 两字段均**必填**（deserializer 缺字段抛异常） |
| `WidgetHotWord` | `word`,`label`(int) | `""`/`0`；label 1/2/3 → 徽章 新/热/荐 |
| `VideoListSnapshot` | `accountId`,`state`,`items`(`List<WidgetVideo>`) | `""`/`LOADING`/`[]` |
| `LiveSnapshot` | `accountId`,`state`,`items`,`updatedAt`(long) | 多一个时间戳 |
| `MixSnapshot` | `accountId`,`state`,`items`(`List<WidgetMixItem>`) | — |
| `SearchSnapshot` | `accountId`,`state`,`items`(`List<WidgetHotWord>`) | — |
| `WidgetPlayback` | `id`,`title`,`author`,`coverUrl`,`source`,`isPlaying`,`isLive`,`durationMs` | **纯内存，不持久化、无 @Serializable**；`isLive=false`,`durationMs=0` 由重载构造保留 |
| `WidgetLoadState` | 枚举 `LOADING`/`READY`/`ERROR` | kotlinx 按名字序列化 |
| `WidgetKind` | 枚举 `HISTORY(360)`/`LATER(360)`/`MIX(360)`/`LIVE(30)`/`SEARCH(60)` | 括号内为周期分钟；`NOW_PLAYING` **无 kind** |

各模型 `SERIAL_NAME` 常量保留反编译 descriptor 的全限定名（如 `com.github.axtv.dv.widget.WidgetVideo`）。

---

## 3. 六个组件逐组件规格表

尺寸语义已确认：`vt0.m11298a(f,f2)` 打包为 `f=宽(高32位)`、`f2=高(低32位)`；
`v11.m11075a` 取**高**、`m11076b` 取**宽**（`v11.java:11-18`）。因此下表阈值判定的是高还是宽均已核准。

### 3.1 继续观看 `ContinueWidget`（kind=HISTORY）

| 项 | 值 |
|---|---|
| sizeMode | `SIZE_22`(156×192) / `SIZE_42`(320×192) — `ContinueWidget.java:28` |
| WidgetSurface 圆角 | `8.0f` 固定（:82） |
| 调色板 | `widgetPalette(context, 首条封面)` |
| 数据源 | `WidgetStore.history()` → `VideoListSnapshot.items` |

状态分支（`ContinueWidget.java:95-123`，`WidgetRenderSpec.spec(CONTINUE).branches()` 逐条对应）：

| 优先级 | 条件 | 结果 | 文案/动作 |
|---|---|---|---|
| 1 | `WidgetStore.isLocked(fl4)` | `WidgetLockedStatus` | 锁图标 /「配置已上锁」/「设了 PIN 的配置不在桌面显示内容」/「打开应用」→ `home` |
| 2 | `!fl4.m4214v()` | `WidgetGuideStatus` | 「登录后显示」/「继续看要用你的观看历史」/「去登录」→ `login` |
| 3 | `!z \|\| state==LOADING` | `WidgetLoadingStatus` | 历史图标 /「正在加载」/「数据加载后会自动更新」 |
| 4 | `state==ERROR` | `WidgetErrorStatus(HISTORY)` | 错误图标 /「没能刷新」/「网络不可用，稍后自动重试」/「重试」→ `refreshAction(HISTORY)` |
| 5 | `widgetVideo==null` | `WidgetStatus` | inbox 图标 /「还没有观看历史」/「看过的视频会出现在这里」/「去推荐看看」→ `home` |
| 6 | `宽 < 300` (`m11076b,300`) | `ContinueSmall` | 见下 |
| 7 | else | `ContinueLarge` | 见下 |

| 内容态 | 显示字段 | 点击区域 → 动作 |
|---|---|---|
| Small | cover(100dp) +「最近看过」+ title + author/avatar(16dp) + duration | 整个封面 → `play/history?id={video.id}` |
| Large | cover(99dp) +「最近看过」+ title + author/avatar(20dp) + duration +「更早看过」+ 更早封面行(56dp) | 首条封面 → `play/history?id=首条`；每条更早封面 → `play/history?id={id}` |
| 条件可见 | 「最近看过」标签非空才显（`WidgetUiKt:1237`）；duration 非空才显(`:2976`)；`更早看过` 分区在 list 为空时隐藏(`DesktopWidgetsKt:154`) | — |

### 3.2 稍后再看 `WatchLaterWidget`（kind=LATER）

| 项 | 值 |
|---|---|
| sizeMode | `SIZE_42`(320×192) / `SIZE_43`(320×292) — `:28` |
| 圆角 | `0.0f` 固定（:82） |
| 数据源 | `WidgetStore.later()` → items |

| 优先级 | 条件 | 结果 | 文案/动作 |
|---|---|---|---|
| 1 | isLocked | `WidgetLockedStatus` | 同 3.1 |
| 2 | `!m4214v()` | Guide | 「稍后再看要用你的列表」/「去登录」→ `login` |
| 3 | `!z \|\| LOADING` | Loading | 「正在加载」 |
| 4 | `ERROR` | `WidgetErrorStatus(LATER)` | 「重试」→ `refreshAction(LATER)` |
| 5 | `list.isEmpty()` | `WidgetStatus` | 「稍后再看是空的」/「加入稍后再看的视频会出现在这里」/「去推荐看看」→ `home` |
| 6 | `高 < 250` (`m11075a,250`) | `LaterCompact` | 见下 |
| 7 | else | `LaterExpanded` | 见下 |

| 内容态 | 显示字段 | 点击 → 动作 |
|---|---|---|
| Compact | Header「稍后再看」+ schedule 图标 + `<n> 条`；封面行（likes 角标） | 每个封面 → `play/later?id={id}` |
| Expanded | Header + `<n> 条`；每条 cover(140dp)+likes + title + author/avatar(16dp) | 每个封面 → `play/later?id={id}` |

### 3.3 合集追更 `MixWidget`（kind=MIX）

| 项 | 值 |
|---|---|
| sizeMode | `SIZE_42` / `SIZE_43`（:31） |
| 圆角 | `0.0f`（:83） |
| 调色板 | **`z2 ? widgetPalette : widgetPalette2` 两套，按 `高<250` 切换**（:71-72，唯一使用双调色板的组件） |
| 数据源 | `WidgetStore.mix()` → `MixSnapshot.items`（含 `nextEpisodes`） |

| 优先级 | 条件 | 结果 | 文案/动作 |
|---|---|---|---|
| 1 | isLocked | Locked | 同 3.1 |
| 2 | `!m4214v()` | Guide | 「合集追更要用你的观看历史」→ `login` |
| 3 | `!z \|\| LOADING` | Loading | 「正在加载」 |
| 4 | `ERROR` | `WidgetErrorStatus(MIX)` | 「重试」→ `refreshAction(MIX)` |
| 5 | `list.isEmpty()` | `WidgetStatus` | 「没有合集追更」/「看过合集里的视频后会出现在这里」/「去推荐看看」→ `home` |
| 6 | `高 < 250` | `MixCompact`（取首条 + 首个 bitmap） | 见下 |
| 7 | else | `MixExpanded`（取前 3 条） | 见下 |

| 内容态 | 显示字段 | 点击 → 动作 |
|---|---|---|
| Compact | Header(视频库图标+「合集追更」)；cover(64dp)；name；`author · 共 N 集`；progress(`E / T` 或 `第 E 集`);「继续第 E 集」按钮；后续选集按钮(最多3,`<n> 集`) | 封面 → `mix/detail?id={id}`；「继续第 E 集」→ `mix/play?id={id}&aweme={resumeAwemeId}`；选集按钮 → `mix/play?id={id}&aweme={episode.awemeId}` |
| Expanded | Header「合集追更」；每条 cover(52dp)+name+progress(`第 E / T 集`)+播放图标按钮(cd=`播放`+name) | 整行 → `mix/detail?id=`；封面 → `mix/detail?id=`；播放按钮 → `mix/play?id=&aweme=resume` |
| 条件可见 | author 空隐藏；`total>0` 才显「共 N 集」、`E / T`；`nextEpisodes` 只保留 `number>currentEpisode`（`WidgetStore:285`）后取前 3 | — |

### 3.4 关注直播 `LiveWidget`（kind=LIVE）

| 项 | 值 |
|---|---|
| sizeMode | `DpSize(320×92)` / `SIZE_42`（:28） |
| 圆角 | `高<130 ? 10.0f : 12.0f`（:78） |
| 数据源 | `WidgetStore.live()` → items + `updatedAt` |

| 优先级 | 条件 | 结果 | 文案/动作 |
|---|---|---|---|
| 1 | isLocked | Locked | 同 3.1 |
| 2 | `!m4214v()` | Guide | 「登录后显示关注的主播」→ `login` |
| 3 | `!z \|\| LOADING` | Loading | 「正在加载」 |
| 4 | `ERROR` | `WidgetErrorStatus(LIVE)` | 「重试」→ `refreshAction(LIVE)` |
| 5 | `list.isEmpty()` | `WidgetStatus` | live 图标 /「关注的主播没在直播」/「开播后会在这里显示」/「打开直播」→ `live` |
| 6 | `高 < 130` | `LiveCompact` | 见下 |
| 7 | else | `LiveExpanded` | 见下 |

| 内容态 | 显示字段 | 点击 → 动作 |
|---|---|---|
| Compact | 徽章(live 图标+`<n> 人在播`, 72dp tinted)；每个主播 avatar(48dp)+author | 徽章 → `live`；每个主播行 → `live/room?id={roomId}` |
| Expanded | Header「关注的直播」+ live 图标 + `<HH:mm> 更新`（`widgetTime(updatedAt)`，空则隐藏）；每条 avatar(36dp)+author+title+`viewers`+「在看」 | 每行 → `live/room?id={roomId}` |
| 配色 | Header 第4参 `z=true` → CookieTile 用 primaryContainer（`:699`/`WidgetUiKt:1612`） | — |

### 3.5 搜索热搜 `SearchWidget`（kind=SEARCH，provideGlance 完整反编译）

| 项 | 值 |
|---|---|
| sizeMode | `DpSize(320×64)` / `SIZE_42`（:40） |
| 圆角 | `8.0f`（:83） |
| 调色板 | `widgetPalette$default(context,null,...)` **不传封面**（:167） |
| 数据源 | `WidgetStore.search()` → items，`(z && READY) ? take(items,6) : []`（:159） |

`provideGlance` 前置逻辑（`SearchWidget.java:153-167`）：`z = j42.m6486c(accountId, 当前账户)`；
`!z || state==LOADING` → **`WidgetRefreshScheduler.refreshNow(context, SEARCH)` 自触发刷新**；
其余 5 组件 provideGlance 结构相同（jadx 溢出未反编译）。

| 优先级 | 条件 | 结果 | 文案/动作 |
|---|---|---|---|
| 1 | isLocked | Locked | 同 3.1 |
| 2 | `!m4214v()` | Guide | 「搜索和热搜需要登录」→ `login` |
| 3 | `!z \|\| LOADING` | Loading | 「正在加载」 |
| 4 | `ERROR` | `WidgetErrorStatus(SEARCH)` | 「重试」→ `refreshAction(SEARCH)` |
| 5 | **`高 < 130`（先于空态！）** | `SearchCompact(首个热词)` | 见下 |
| 6 | `list.isEmpty()` | `WidgetStatus` | search 图标 /「热搜暂时为空」/「仍然可以打开搜索页输入」/「打开搜索」→ `search` |
| 7 | else | `SearchExpanded` | 见下 |

> **分支顺序差异**：紧凑分支(#5)在空态(#6)之前，是唯一先判尺寸再判空态的组件；
> 窄尺寸 + 空列表 → `SearchCompact(null)`，热词回退「搜索视频、用户、直播」。已用 `resolve` 自检。

| 内容态 | 显示字段 | 点击 → 动作 |
|---|---|---|
| Compact | search 图标(24dp) +「大家都在搜」+ (首热词 或「搜索视频、用户、直播」) + whatshot 按钮「打开热搜」 | 文字区 → `search`；热门按钮 → `search` |
| Expanded | 搜索栏(52dp: search 图标+「搜索视频、用户、直播」)；两列热词 `take(3)` 与 `drop(3).take(3)`；每行 序号+热词+徽章(新/热/荐) | 搜索栏 → `search`；每个热词行 → `search/results?query={word}` |
| 徽章映射 | `label=1→新`(tertiaryContainer) / `2→热`(primaryContainer) / `3→荐`(secondaryContainer) / 其余无 | 序号 `<=3` 用 primary + WidgetNumberFont，其余 onSurfaceVariant |

### 3.6 正在播放 `NowPlayingWidget`（**无 kind**，播放状态驱动）

| 项 | 值 |
|---|---|
| sizeMode | `SIZE_42`(320×192) / `SIZE_CAR`(536×260)（:37） |
| 圆角 | `宽>=480 ? 16.0f : 12.0f`（:80） |
| 调色板 | `widgetPalette(context, playback?.coverUrl)`（:263，CONTENT 来源按封面取色） |
| 数据源 | `WidgetPlaybackStore.getState().value`（**纯内存，不持久化**）；`playback!=null` 时并行加载封面 `224×126×16` 与 `90×90×16` |

| 优先级 | 条件 | 结果 | 文案/动作 |
|---|---|---|---|
| 1 | isLocked | `WidgetLockedStatus` | 同 3.1 |
| 2 | `widgetPlayback==null` | `NowPlayingIdle` | CookieTile(play 图标,56dp,COOKIE9) /「没有在播放」/「点继续看，从观看历史第一条开始」/「继续看」→ `profile/history` /「打开推荐」→ `home` |
| 3 | `宽 >= 480` | `NowPlayingContent(wide=true)` | 见下 |
| 4 | else | `NowPlayingContent(wide=false)` | 见下 |

> Idle 两个按钮来自 `ComposableSingletons$DesktopWidgetsKt.java:31-35`（`lambda$720070246`），
> 已并入 `WidgetRenderSpec` 的 Idle `ClickRegion`，**未丢弃**。

| 内容态 | 显示字段 | 点击 → 动作 |
|---|---|---|
| Wide | cover(224×126) + title + author + 状态(`正在播放/已暂停 · source`) + 三按钮(76dp) | 封面 → `home`；上一条 → `media(88)`；暂停/继续 → `media(85)`；下一条 → `media(87)` |
| Compact | cover(90×90) + title + author + 状态 + 三按钮(52dp) | 同上 |
| 状态文案 | `isPlaying ? "正在播放" : "已暂停"` + ` · ` + `source`；按钮 `isPlaying ? "暂停" : "继续"` | 图标 `isPlaying ? widget_ic_play : widget_ic_pause`（原码如此，:1149） |

**7 种 Screen**：`LOCKED` / `GUIDE` / `LOADING` / `ERROR` / `EMPTY` / `CONTENT` / `IDLE`。
`WidgetStatus` 还有二级布局分支：`高<140` 竖排(Column) else 横排(Row)（`WidgetUiKt.java:3175`）；
错误态用 `errorTint=true` → COOKIE4 + errorContainer 配色，其余 COOKIE9 + primaryContainer。

---

## 4. 尺寸 / 圆角 / 形状 / 颜色常量表

### 4.1 尺寸与阈值（`WidgetRenderSpec.Dimens`）

| 常量 | 值(dp) | 来源 |
|---|---|---|
| `SIZE_22` / `SIZE_42` / `SIZE_43` / `SIZE_CAR` | 156×192 / 320×192 / 320×292 / 536×260 | `DesktopWidgetsKt.java:84-87` |
| `SIZE_LIVE` / `SIZE_SEARCH` | 320×92 / 320×64 | `LiveWidget:28` / `SearchWidget:40` |
| 布局阈值 | 继续看 宽300 / 稍后 高250 / 合集 高250 / 直播 高130 / 搜索 高130 / 正在播放 宽480 / Status 高140 | 各 Widget `if` + `WidgetUiKt:3175` |
| 圆角 | 8 / 0 / 10 / 12 / 16；`WidgetSurface` 默认 12；系统上限 28(SDK≥31 读 `system_app_widget_background_radius`) | `WidgetUiKt:3649-3659` |
| 封面 | 52/56/64/90/99/100/126/140/224 等 | 各 `WidgetCover`/`load` 调用 |

### 4.2 形状（`WidgetShape` + `WidgetShapeGeometry`）

| 形状 | 极坐标半径 r(θ) | 来源 |
|---|---|---|
| `ROUNDED` | 圆角矩形 `addRoundRect(corner)` | `WidgetImages:171-175` |
| `COOKIE4` | `0.9 + 0.1·cos(4θ)` | `:247` |
| `COOKIE9` | `0.94 + 0.06·cos(9θ)` | `:260` |
| `CLOVER4` | `0.62 + 0.38·|cos(2θ)|^0.55` | `:251` |

120 点采样 → 7 点滑动平均 ×2 → 按 max 归一化 → 极坐标建路径；`insetPath` 以中心缩放。
`tintedBackground` 圆角→drawable：4/8/12/20/28 → `widget_shape_4/8/12/20/28`，其余 `widget_shape_pill`（`WidgetUiKt:4327-4342`）。

### 4.3 颜色令牌（`WidgetPalette`）

- **48 个 Material3 ColorScheme 令牌**（`TOKENS`，顺序 = `p000.k80` 构造参数，`k80.toString():261-288` 核准）：
  primary…tertiaryFixedVariant。
- **27 个 Glance ColorProviders**（`Providers.FIELD_TOKENS/FIELD_NAMES`，= `p000.j80`/`l51` 构造参数，`j80.java:89-115` 核准），
  含派生 `widgetBackground`（`jl2.m6711k(secondaryContainer)`，HCT 亮度调整，用 `LongUnaryOperator` 注入，默认恒等）。
- **6 个额外 surface/fixed 供应器**：surfaceContainerLow/High/Highest、surfaceContainer、primaryFixed、onPrimaryFixed。
- `getCurrent() = isDark ? dark : light`；`isDark = (uiMode & 48)==32`（`WidgetThemeKt.java:95`）。
- 主题来源：`fl4.m4198e()`(t80) / `m4199f()`(s90) / `m4197d()`(z70) / `global_seed_argb`；
  `CONTENT` 且封面非空 → `ri0.m9896c` 取封面种子色 → `va4.m11129b`（LruCache），否则 `c74.m2048a`（`WidgetThemeKt:157-167`）。

---

## 5. 完整动作表 / deep link 表 / 媒体键表

### 5.1 三类 Glance action（`WidgetAction` / `WidgetActions` / `WidgetRoutes`）

| Type | 构造 | 落地行为 |
|---|---|---|
| `OPEN_ROUTE` | `widgetAction(route)` | `startActivity(Intent(MainActivity, EXTRA_ROUTE=route))`，`addFlags(872415232)` = `0x34000000` = NEW_TASK\|SINGLE_TOP\|CLEAR_TOP |
| `MEDIA_KEY` | `mediaAction(context, keyCode)` | 发 `ACTION_MEDIA` 广播，带 `EXTRA_KEY_CODE=keyCode` |
| `REFRESH` | `refreshAction(kind)` | `ActionParameters("widget_kind"→kind.name())` → `RefreshWidgetAction` 回调 |

`WidgetAction.describe()` 输出可核对的 Intent/Broadcast/Callback 描述。

### 5.2 deep link 全表（`WidgetRoutes`，参数走 `Uri.encode`，等价百分号编码已实现）

| 方法 | route 字符串 |
|---|---|
| `home()` | `home` |
| `history()` | `profile/history` |
| `later()` | `profile/later` |
| `live()` | `live` |
| `search()` | `search` |
| `login()` | `login` |
| `liveRoom(id)` | `live/room?id={enc}` |
| `mixDetail(id)` | `mix/detail?id={enc}` |
| `playHistory(id)` | `play/history?id={enc}` |
| `playLater(id)` | `play/later?id={enc}` |
| `playMix(id,aweme)` | `mix/play?id={enc}&aweme={enc}` |
| `searchResult(q)` | `search/results?query={enc}` |

### 5.3 媒体键映射（`DesktopWidgetsKt:1173-1177` + `WidgetActions` 常量）

| 按钮 | keyCode | Android KeyEvent |
|---|---:|---|
| 上一条 | 88 | `KEYCODE_MEDIA_PREVIOUS` |
| 暂停/继续 | 85 | `KEYCODE_MEDIA_PLAY_PAUSE` |
| 下一条 | 87 | `KEYCODE_MEDIA_NEXT` |

其余保留常量：86 STOP / 126 PLAY / 127 PAUSE（本模块未直接调用，但原 `WidgetActions` 定义完整）。
`WidgetMediaActionReceiver.onReceive`：`getIntExtra("key_code", -1)`，`>=0` 才 `decorView.post{dispatchKeyEvent}`。

---

## 6. 刷新时序与周期/超时/节流常量表

### 6.1 时序

```
触发源（见 6.2）
   │
   ▼
WidgetRefreshScheduler.refreshNow / schedule(kind)
   ├─ 守卫：!isDeviceProtected(SDK>=24 && DE storage)
   ├─ hasWidgets(kind) = AppWidgetManager.getAppWidgetIds(ComponentName(context, receiver(kind))).length>0
   ├─ 立即：enqueueUniqueWork("widgets_once_"+kind, REPLACE, OneTimeWorkRequest)  ← 去抖
   └─ 周期：enqueueUniquePeriodicWork("widgets_periodic_"+kind, PeriodicWorkRequest)
        输入 data：KEY_KIND="kind" → kind.name()；约束 ch0(仅联网 x93.f24443J，充电/空闲/电量/存储皆 false)
        周期钳制：见 6.3
   ▼
WidgetRefreshWorker.doWork(kindName, runAttemptCount)
   ├─ kind 解析失败 / 无 widget → SUCCESS
   ├─ refresher.refresh(kind) 成功 → SUCCESS
   └─ 失败 → runAttemptCount>=3 ? SUCCESS : RETRY
   ▼
WidgetRefresher.refresh(kind)                         【WidgetRefresher.java:645】
   ├─ 守卫 :694 !m4214v() || isLocked → updateKind(kind) + return TRUE（不取数）
   ├─ 取数（见 6.4）→ 过滤/去重/映射 → saveXxx(快照 READY)
   ├─ CancellationException :845/:1041 → 账户未变才 saveError(ERROR 空快照) → updateKind → FALSE
   ├─ 其它 Throwable :962 → release + updateKind → 抛出
   └─ finally：c1608s.m10094a() 释放资源（→ env.dataSource.onRefreshFinished()）
   ▼
WidgetStore.saveXxx（canSave 校验账户）→ WidgetStorage.putString(key, JsonCodec.encode)
   ▼
WidgetUpdater.updateKind(kind) → WidgetEnvironment.UpdateSink.updateKind（Android 侧 Glance 更新）
```

另有 App 内即时推送链（不经过 WorkManager）：`WidgetUpdater.pushLive/pushSearch/pushVideos/recordVideo`
→ `saveXxx(READY 快照)` → `updateKind`；`recordVideo` 额外 `updateKind(MIX)`，`updateAll` 额外更新 `NowPlayingWidget`。

### 6.2 触发源

| 触发 | 位置 |
|---|---|
| 添加首个 widget | `BaseWidgetReceiver.onEnabled` → `schedule(kind)` + `refreshNow(kind)`（:27-33） |
| `onUpdate` | `BaseWidgetReceiver.onUpdate` → `schedule(kind)`（:36-42） |
| 删除最后一个 widget | `onDisabled` → 非 DE 时 `cancel("widgets_periodic_"+kind)`（:16-24） |
| 周期到点 | WorkManager `PeriodicWorkRequest` |
| UI 自触发 | `SearchWidget:155-158`（及同构的其余 5 组件）：`!accountMatches || LOADING` → `refreshNow` |
| 错误态「重试」按钮 | `refreshAction(kind)` → `RefreshWidgetAction.onAction` → `refresher.refresh(kind)` |
| 播放/搜索/列表数据变化 | `WidgetUpdater.push* / recordVideo`（App 内直接推送） |
| 启动时全量排周期 | `scheduleAll()`；DE 上下文打印 `DE storage context: skip widget refresh scheduling` 并跳过 |

### 6.3 周期/超时/节流常量（全部带来源行号）

| 常量 | 值 | 来源 |
|---|---|---|
| `HISTORY/LATER/MIX` 周期 | 360 分钟 | `WidgetKind.java:9-11` |
| `LIVE` 周期 | 30 分钟 | `WidgetKind.java:12` |
| `SEARCH` 周期 | 60 分钟 | `WidgetKind.java:13` |
| `MIN_PERIODIC_INTERVAL_MILLIS` | 900000 (15min) | `WidgetRefreshScheduler.java:151-159`（源码逐字确认，非推测） |
| `MIN_PERIODIC_FLEX_MILLIS` | 300000 (5min) | `WidgetRefreshScheduler.java:160-166` |
| flex 计算 | `coerceIn(max(millis,900000), 300000, interval)` | `:166` `ut0.m10996s` |
| `MAX_RUN_ATTEMPTS` | 3 | `WidgetRefreshWorker.java:108` |
| 去抖策略 | `ExistingWorkPolicy.REPLACE`（`ac1.f334J`），同名 once 工作互相替换 | `Scheduler:127` |
| 工作名前缀 | `widgets_once_` / `widgets_periodic_` | `Scheduler:76-82` |
| 网络约束 | `x93.f24443J` = `NetworkType.CONNECTED`；充电/空闲/电量/存储 = false | `Scheduler:123`/`:143` `ch0` |
| `isDeviceProtected` | `SDK_INT>=24 && context.isDeviceProtectedStorage()` | `WidgetActionsKt:24-26` |
| 系统圆角 SDK 门槛 | 31 | `WidgetUiKt:3654` |
| 图片 LruCache 容量 | 48 | `WidgetImages.java:37` |
| 图片最小像素 | 24 | `WidgetImages.java:361-365` |
| 图片默认圆角 / 形状 | 12dp / ROUNDED | `WidgetImages:118-121` |
| 形状采样 / 平滑 | 120 点 / 7 点×2 | `WidgetImages:176-203` |
| 历史截断 / 合集截断 | 20 条 / 3 条 | `WidgetStore:219`/`:299` |
| 热词取数 | 前 6 条 | `SearchWidget:159` |
| `updatedAt` | `System.currentTimeMillis()` | `WidgetRefresher:485` / `Updater:117` |

> **超时**：源码 widget 模块内**没有显式网络超时常量**（无 `withTimeout`/OkHttp timeout 字面量）；
> 取数超时由 `p000.kq5` 数据仓库与 Coil/网络层控制，属仓外依赖，见 §10 存疑 D2。

### 6.4 取数源（`WidgetRefresher` → `p000.kq5` 仓库，接口化为 `WidgetDataSource`）

| 刷新路径 | 原取数调用 | 处理 |
|---|---|---|
| `refreshHistory` | `kq5.m7253F(0L)` → `UserWorksResponse.awemeList` → `filterPrimaryPlayableAwemes` | `toWidgetVideo` → `saveHistory(READY)` |
| `refreshLater` | `kq5.m7254G(0)` → `List<Aweme>` | `toWidgetVideo` → `saveLater(READY)` |
| `refreshLive` | `kq5.m7275m()` → `Iterable<LiveRoom>` | 过滤空 roomId → 按 roomId 去重 → `toWidgetLiveItem` → `saveLive(READY, now)` |
| `refreshSearch` | `kq5.m7273k()` → `Iterable<HotWord>` | 过滤空 word → `WidgetHotWord(word,label)` → `saveSearch(READY)` |
| `refreshMix` / `loadMixItem` | `:500` / `:305`（**jadx 溢出未反编译**） | 以 `WidgetDataSource.fetchMixUpdates(account, existing)` 契约表达「遍历现有项→拉最新选集/进度→合并」 |
| `saveError` | `:567-586` | 按 kind 写 `state=ERROR, items=[]` 空快照 |

`filterPrimaryPlayableAwemes` 过滤、`toWidgetVideo`/`toWidgetLiveItem` 的兜底文案
（「无标题视频」/「正在直播」/「主播」/「0」）、时长 `"%d:%02d"`、点赞数格式化（注入 `CountFormatter`）
均在 `WidgetData` 还原。`canSave(accountId)`：非空且等于当前账户（`WidgetStore:43-45`）。

---

## 7. 存储键全表

| 键 / 常量 | 值 | 归属 |
|---|---|---|
| SharedPreferences 名 | `axtv_desktop_widgets` | `WidgetStore.java:55-57` |
| `KEY_HISTORY` | `history` | `WidgetStorage` |
| `KEY_LATER` | `later` | 同上 |
| `KEY_LIVE` | `live` | 同上 |
| `KEY_MIX` | `mix` | 同上 |
| `KEY_SEARCH` | `search` | 同上 |
| `KEY_GLOBAL_SEED_ARGB` | `global_seed_argb`（另一个 prefs `fl4.f6240a.getInt(key,0)`，0=未设置） | `WidgetThemeKt:90` |
| `EXTRA_ROUTE` | `com.github.axtv.dv.widget.ROUTE` | `WidgetRoutes.java:13` |
| `EXTRA_KEY_CODE` | `key_code`（默认 -1） | `WidgetMediaActionReceiver:14` |
| `ACTION_MEDIA` | `com.github.axtv.dv.widget.MEDIA` | `:13` |
| `KEY_KIND`（WorkManager inputData） | **`kind`** | `WidgetRefreshScheduler.java:41` / `Worker:68` |
| `WIDGET_KIND_PARAMETER`（Glance ActionParameters） | **`widget_kind`** | `WidgetActionsKt.java:21` |
| `WORK_PREFIX_ONCE` / `WORK_PREFIX_PERIODIC` | `widgets_once_` / `widgets_periodic_` | `Scheduler:76-82` |

> **注意**：`kind`（WorkManager 数据）与 `widget_kind`（Glance action 参数）是两个不同的 key，勿混用。
> **没有 `playback` 持久化键**——`WidgetPlaybackStore` 是内存 `MutableStateFlow<WidgetPlayback?>(null)`，
> 不落 SharedPreferences（`WidgetPlaybackStore.java:1-40`）；`global_seed_argb` 属于 `fl4` 的全局 prefs，不是 `axtv_desktop_widgets`。

存储访问 `getString`/`putString(...).commit()`/`clear().commit()` 抽象为 `WidgetStorage`，
参考实现 `InMemoryWidgetStorage`；Android 侧注入 `context.getSharedPreferences("axtv_desktop_widgets", 0)`。
JSON 用 `JsonCodec` 接口隔离（原 kotlinx.serialization `Json{}`），读取失败/不存在一律兜底默认空快照。

---

## 8. 待迁移（仅真正依赖 Android / 第三方，且已用「接口 + 行为规格」落地）

| # | 依赖 | 核心层替代 | Android 侧应做 |
|---|---|---|---|
| 1 | **Glance 组件树绘制** | `WidgetRenderSpec`（Branch/ContentSpec/ClickRegion/Dimens/Strings/Icons）+ `WidgetAction` | 六个 `GlanceAppWidget.provideGlance` 按 `spec(id)` 与 `resolve()` 渲染；`CompositionLocal` 注入 `WidgetPalette.Providers` |
| 2 | **Bitmap 解码 / Canvas 裁剪** | `WidgetImages` + `WidgetImageSpec` + `WidgetShapeGeometry` + `WidgetEnvironment.ImageLoader` | Coil `ImageLoader.execute` + `BitmapShader`/`Path`；`renderPlan` 分层绘制；`SeedExtractor` = `ri0.m9896c` |
| 3 | **AppWidgetManager** | `WidgetEnvironment.WorkScheduler.hasWidgets` / `UpdateSink` / `WidgetReceiverLifecycle` | `getAppWidgetIds(ComponentName)` 存在性判定；`GlanceAppWidgetManager.updateXxx`；`onEnabled/onUpdate/onDisabled` 转发 |
| 4 | **WorkManager** | `WidgetRefreshScheduler` / `WidgetRefreshWorker` / `Constraints` / `PeriodicSpec` | `OneTimeWorkRequest` / `PeriodicWorkRequest` / `CoroutineWorker`；`Result.success()/retry()` 映射；`ch0` 约束 |
| 5 | 系统能力 | `WidgetSettings`（账户/开关/PIN/DE/夜间/密度/SDK/主题偏好）、`CountFormatter`、`ColorSchemeFactory`、`LongSupplier clock` | 代理 `p000.fl4` / `Configuration` / `Resources` / `DataModelKt.formatCount` / `c74`+`va4` |
| 6 | `Uri.encode` | `WidgetRoutes.encode`（纯 JDK 等价百分号编码） | 可直接改用 `android.net.Uri.encode` |
| 7 | 媒体键投递 | `WidgetEnvironment.MediaSink` | `WidgetMediaActionReceiver` → `decorView.post{dispatchKeyEvent(KeyEvent(...))}` |

---

## 9. 验证

- `javac.exe --release 17 -encoding UTF-8` 全量编译本模块 **零错误 / 81 class**。
- jshell / 临时 `Verify` 自检通过：`WidgetRoutes.encode`（空格→`%20`、`&`→`%26`、中文 UTF-8、`/`→`%2F`）、
  六组件 `resolve` 全状态分支（LOCKED/GUIDE/EMPTY/SMALL/LARGE/IDLE/CONTENT_WIDE，
  含 SEARCH 紧凑态先于空态的特殊顺序）、圆角计算、`widgetTime(0)=""`、
  `ClickRegion.materialize` → `startActivity`/`broadcast` 描述、6 份 ComponentSpec 齐全。
- 验证用的临时 `Verify.java` 已删除，未进入产出目录。

---

## 10. 存疑清单

- **D1**：`WidgetRefresher.loadMixItem`(:305) / `refreshMix`(:500) 因 `JadxOverflowException` 未反编译，
  `refreshMix` 内部「遍历现有 mix items → 逐条拉最新选集/进度 → 合并」按数据契约 +
  `WidgetDataSource.fetchMixUpdates` 接口还原，具体 API 名/字段需真机或反编译工具复核。
- **D2**：widget 模块内无显式网络超时常量；`filterPrimaryPlayableAwemes` 过滤规则、
  `DataModelKt.formatCount` 的中文计数格式（万/亿）在仓外 `p000`，Android 侧注入等价实现。
- **D3**：4 个 Snapshot 的 `$$serializer` 未随包提供（descriptor 在 `write$Self$app` 按 index 暴露），
  `SERIAL_NAME` 为按包路径推定；Android 侧须确保 kotlinx.serialization 的 `Json{}` 配置位
  （`ignoreUnknownKeys` / `encodeDefaults` / `explicitNulls`）与原实现一致。
- **D4**：`WidgetPalette` 的 `widgetBackground` = `jl2.m6711k(secondaryContainer)` 是
  material-color-utilities 的 HCT 亮度变换，核心层用可注入 `LongUnaryOperator` 表达（默认恒等），
  Android 侧应注入 `jl2.m6711k` 等价实现以完全还原配色。
- **D5**：`NowPlayingWidget` 状态图标 `isPlaying ? widget_ic_play : widget_ic_pause`（`DesktopWidgetsKt:1149`）
  与常规命名直觉相反，按原码逐字保留，未做「修正」。
- 已在本轮源码精读中**确认、不再存疑**的点：`v11.m11075a=高 / m11076b=宽`（`v11.java:11-18`）；
  `j42.m6472N` = 对全部实参取 max（`j42.java:292-297`）；`j42.m6486c` = null-safe equals（`j42.java:477`）；
  `WebSocketProtocol.PAYLOAD_SHORT=126`（`okhttp3/internal/p009ws/WebSocketProtocol.java:36`，
  故 NowPlaying 宽版封面为 224×126×16）；`MIN_PERIODIC` 900000/300000（`Scheduler:151-166` 逐字）；
  `BaseWidgetReceiver` 三个生命周期回调逐字核对；`NOW_PLAYING` 无 kind、不参与周期调度（已确认，非缺陷）。
