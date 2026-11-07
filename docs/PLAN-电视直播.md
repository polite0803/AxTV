# 电视直播（IPTV）功能实施计划

覆盖用户提出的三轮需求，一条不漏：

| 轮次 | 原话 | 本计划落点 |
|---|---|---|
| 1 | 增加电视直播功能，能播放央视等直播电视台的节目 | 阶段 A（核心模块）+ 阶段 E（UI/播放） |
| 2 | 用户直播源输入需要支持手机扫码输入（参考现有 cookie 输入实现） | 阶段 C（面板通道）+ 阶段 D（设置页二维码） |
| 3 | 多添加几份较为稳定、频道数量多的电视直播源作为默认列表，同时默认列表支持用户手机扫码添加 | 阶段 A（`BuiltInSources`）+ 阶段 F（实测留档）+ 阶段 C/D（扫码添加） |

**本文是决策档案，不是交付物。** 每阶段开工前回读本节，阶段收尾把读数写回本文的「进度」表。

---

## 1. 边界声明：这不是现有「直播」的扩展

现有 `直播` tab 是**抖音 webcast 直播**，`电视直播` 是**电视台 IPTV 直播流**。两条链除了都叫"直播"之外零交集，
必须说清，否则后面每一处改动都会被误解成在动老代码：

| 维度 | 现有抖音直播（`axtv.live`） | 本功能的电视台直播（新 `axtv.iptv`） |
|---|---|---|
| 数据源 | `live.douyin.com` / webcast 接口下发房间列表 | M3U/M3U8 播放列表（本地内置 + 用户扫码添加） |
| 流协议 | flv / hls，由 `LiveStreamResolver` 从 SDK 数据 + map 键里选流 | HLS（`.m3u8`），Media3 原生支持，无需选流算法 |
| 弹幕 | WebSocket + protobuf 帧（`refactor/live` 23 类 3011 行） | **无**（电视台流不带弹幕） |
| 登录态 | 需要 Cookie / ttwid | **不需要**，匿名可播 |
| 房间模型 | roomId / webRid / 进房接口 | 频道（channel）→ 若干候选 URL |

**结论：不复用 `axtv.live` 任何协议代码**，只复用播放内核（`PlayerEngine` / `Media3Engine`）。
UI 上必须避免与现有 `直播` tab 同名混淆 —— 本功能一律叫 **「电视台」**（路由 `iptv`，页面标题「电视台」）。

---

## 2. 现成可复用的接线点（已核实存在，非推测）

| 能力 | 复用点 | 证据 |
|---|---|---|
| 拉远端 M3U 文本 | `axtv.settings.HttpFetcher.getText(url, accept, timeoutSeconds)` —— 已有 Android OkHttp 实现，**不新造 HTTP 抽象** | [refactor/settings/axtv/settings/HttpFetcher.java:74](file:///\\172.29.147.201\projects\AxTV\refactor\settings\axtv\settings\HttpFetcher.java#L74)、[android/.../settings/OkHttpHttpFetcher.kt](file:///\\172.29.147.201\projects\AxTV\android\app\src\main\java\com\github\axtv\dv\settings\OkHttpHttpFetcher.kt) |
| HLS 播放 | `Media3Resources.mediaSourceFactory(ctx, useCache)` → `DefaultMediaSourceFactory` 原生支持 HLS | [android/.../player/Media3Engine.kt:95](file:///\\172.29.147.201\projects\AxTV\android\app\src\main\java\com\github\axtv\dv\player\Media3Engine.kt#L95)、换源入口 `setMediaItem(uri, useCache)` [:232](file:///\\172.29.147.201\projects\AxTV\android\app\src\main\java\com\github\axtv\dv\player\Media3Engine.kt#L232) |
| 扫码推送页模板 | `assets/web/submit.html`（读 `?token=`，POST `/api/submit`） | [android/app/src/main/assets/web/submit.html](file:///\\172.29.147.201\projects\AxTV\android\app\src\main\assets\web\submit.html) |
| 面板路由与令牌 | `CookiePushHandler.PORT=9978` / `PATH_SUBMIT="/submit"` / `routes().requireAuth(auth)` | [refactor/panel/axtv/panel/CookiePushHandler.java:39](file:///\\172.29.147.201\projects\AxTV\refactor\panel\axtv\panel\CookiePushHandler.java#L39)、[:75](file:///\\172.29.147.201\projects\AxTV\refactor\panel\axtv\panel\CookiePushHandler.java#L75) |
| 推送到手机可点的带令牌 URL | `PanelController.cookieSubmitUrl()` = `auth.appendTo(DeviceAddressResolver.url(local, PORT, PATH))` | [refactor/panel/axtv/panel/PanelController.java:85](file:///\\172.29.147.201\projects\AxTV\refactor\panel\axtv\panel\PanelController.java#L85) |
| 电视端二维码 | `QrLinksDialog` + `rememberQrBitmap` + `encodeQr(text, 320)`（zxing **本地**编码，不依赖在线二维码服务） | [android/.../ui/settings/SettingsActionDialogs.kt:91](file:///\\172.29.147.201\projects\AxTV\android\app\src\main\java\com\github\axtv\dv\ui\settings\SettingsActionDialogs.kt#L91)、[:186](file:///\\172.29.147.201\projects\AxTV\android\app\src\main\java\com\github\axtv\dv\ui\settings\SettingsActionDialogs.kt#L186) |
| 凭据存储接口形态 | `AuthStore`（核心层接口 + `InMemory`，Android 侧 SharedPreferences 实现）—— 新源存储照这个形态做 | [refactor/panel/axtv/panel/AuthStore.java](file:///\\172.29.147.201\projects\AxTV\refactor\panel\axtv\panel\AuthStore.java) |
| 遥控器友好的列表页骨架 | `LiveListScreen(onBack, onOpenRoom)` 的 tab + LazyGrid + 焦点记忆 + 失败重试焦点兜底 | [android/.../ui/live/LiveListScreen.kt:70](file:///\\172.29.147.201\projects\AxTV\android\app\src\main\java\com\github\axtv\dv\ui\live\LiveListScreen.kt#L70) |

**结论：播放、扫码、HTTP 三块都是接线，不是新建。真正的新代码只有 M3U 解析、源目录合并、内置源清单。**

---

## 3. 关键难点与红线（必须先看清再动手）

### 3.1 没有"稳定"的直播源 —— 这是需求里唯一无法用代码兑现的部分

- 央视等官方 HLS 地址带**时效签名**（`?time=`/`?sign=` 一类）+ **Referer 白名单**，轮换周期从数小时到数周，任何硬编码 URL 都会失效。
- 第三方聚合站（"电视家""蜜糖"这类）成批关停，失效是**整份列表级**的，不是单频道级。
- 所以"较为稳定"只能用工程手段逼近，不能靠选源选到永远不死的：
  1. **多源冗余**：内置 ≥4 份清单，同一频道在多个源里都出现；
  2. **同频道多候选 URL**：合并目录时按规范化频道名聚合，播放失败自动落同名下一条；
  3. **源级健康可见**：每个源显示"上次抓取时间 / 拉到的频道数 / HTTP 码"，用户能自己看出哪份死了；
  4. **禁止静默降级**：全部候选失败必须显式报错页，不允许黑屏转圈（对齐既有约定 [结构性缺口不得在 UI 造成歧义]）。

### 3.2 内置清单必须由实测产生，不允许凭记忆写地址

我**不会**凭印象往 `BuiltInSources.java` 里填 URL —— 那样交付的是一份"看着有、实际全 404"的死表。
阶段 F 是硬前置：**每个内置源都要有真实读数（HTTP 码 + 频道计数 + 抓取时间戳 + 响应前若干字节）留档，
没有实测证据的地址不进内置清单。** 若现网核对需要登录后才能拿到某些清单，我会当场说明缺哪一步、由你配合什么。

### 3.3 合规红线（写进 NOTES.md，不接受"顺手加一下"）

- **不做鉴权绕过**：不伪造 Referer/UA 去突破源服务端的防盗链判定。允许的做法仅限于"按该源自己的网页公开使用的那组请求头"，
  即扮演一个正常客户端，而不是破解访问控制。
- **不碰 DRM / 不解密加密流**。
- **只收录公开可访问的播放列表**，不自建转播、不落盘再分发。
- 用户扫码添加的私有源属用户自己的输入，同样走上面的红线（App 不提供绕过能力）。

---

## 4. 待拍板项（推荐值已给出；你不改就按推荐做）

| # | 选项 | 推荐 | 依据 / 代价 |
|---|---|---|---|
| **D1** | 扫码推送的源形态：A 只收 URL；B 只收 M3U 正文；C 两者都收，按内容判定 | **C** | 手机浏览器里"粘贴短链"和"粘贴整份 m3u 正文"都很常见。判定规则确定：去掉前导空白后以 `#EXTM3U` 或 `#EXTINF` 开头 → 正文，否则 → URL。代价：仅多一个分支 + 一条单测 |
| **D2** | 内置源与用户源的关系 | **分组共存**：内置只读（可逐个禁用），用户源独立分组，不混排、不合并计数 | 混排会让"这个频道是谁的"不可追溯；分组后删除用户源不会污染内置状态 |
| **D3** | 频道去重口径 | 按**频道显示名**去空白/全半角/大小写归一后聚合为同一频道，保留各源候选 URL | 代价：`CCTV1 综合` 与 `CCTV-1` 仍会被判成两个；不做人工别名表（别名表列阶段 F 之后按需补） |
| **D4** | 入口位置 | **一级 tab 不加**；在现有「直播」页顶部加一个「电视台」入口，跳独立路由 `iptv` | 加一级 tab 会挤到 7 个顶部项、遥控器横排已到上限。**注意：不能把「电视台」塞进 `LiveTab` 枚举** —— [LiveRepository.kt:46](file:///\\172.29.147.201\projects\AxTV\android\app\src\main\java\com\github\axtv\dv\ui\live\LiveRepository.kt#L46) 的 `LiveTab(label, partitionId)` 是抖音 webcast 分区表（热门/游戏/娱乐），`LiveRepository.rooms(tab)` 只会打 webcast 接口，IPTV 进去必然拿不到数据。所以只做**页面级入口 + 独立屏**，数据链完全分开。代价：电视台藏一层，需要设置页也能直达（阶段 D 的动作行补一句跳转） |
| **D5** | 播放是否走磁盘缓存 | **不走**（`useCache = false`） | 直播 HLS 分片不可复用，缓存只占空间并拖慢首帧；`Media3Engine.setMediaItem(uri, useCache)` 已有这个开关 |
| **D6** | 是否做订阅自动刷新（进页面时重新拉远端源） | **先不做**，只给「手动刷新」按钮 | 自动刷新把可用性绑在外部站点上，且每次开 App 多几个阻塞请求。留到内置源实测完再决定 |

---

## 5. 阶段计划

每个阶段独立可验收，按序推进；上一阶段门禁不过不开下一阶段。核心层遵守
[refactor/CONVENTIONS.md](file:///\\172.29.147.201\projects\AxTV\refactor\CONVENTIONS.md) 铁律（纯 Java 17、接口化 Android 能力、必带 `NOTES.md`）。

### 阶段 A —— 核心模块 `refactor/iptv/`（新，包名 `axtv.iptv`）

| 文件 | 职责 |
|---|---|
| `M3uParser.java` | `#EXTM3U` / `#EXTINF:时长,属性…` / `tvg-name` `tvg-id` `tvg-logo` `group-title` / 裸 URL 行；CRLF、BOM、`#EXTVLCOPT:http-referrer=`、空行、无 `#EXTM3U` 头、注释行、URL 与属性混排都要容错 |
| `Channel.java` | `record Channel(String name, String logoUrl, String group, String url, Map<String,String> attrs, String sourceId)` |
| `Playlist.java` | `record Playlist(String sourceId, List<Channel> channels, String rawError)` |
| `SourceInput.java` | D1 的判定器：`ofUrl(String)` / `ofContent(String)` / `detect(String)` |
| `PlaylistSource.java` | 一个源 = 标识 + 显示名 + 拉取方式（内置 URL / 内置正文 / 用户 URL / 用户正文） |
| `PlaylistLoader.java` | 依赖注入 `HttpFetcher`，`load(source)` → `Playlist`；有大小上限与超时（不自造数值，取自 settings 的 `videoLoadTimeoutSeconds` 同一档或新键） |
| `ChannelCatalog.java` | 内置 + 用户源合并、D3 的名称归一聚合、按 group 分栏、给出「该频道候选 URL 有序列表」 |
| `BuiltInSources.java` | 内置默认清单（常量表）。**每条带注释：出处、核对日期、实测频道数**，数据来自阶段 F |
| `NOTES.md` | 类映射表（本模块**无 `src/` 来源**，属新增功能，需在表里显式标「新建，非重构」）+ 行为清单 + 待迁移 + 存疑 + §3.3 红线 |

验收：`javac --release 17` 零错误；`./gradlew :tests:test` 新增用例全绿（用例清单见 §6）。

### 阶段 B —— 配置键与访问器

| 落点 | 内容 |
|---|---|
| [refactor/settings/axtv/settings/SettingsKeys.java](file:///\\172.29.147.201\projects\AxTV\refactor\settings\axtv\settings\SettingsKeys.java) | 新增分组 `// ─── 电视台直播 ───`：`IPTV_SOURCES_LIST = "iptv_sources_list"`（用户源，JSON 数组）、`IPTV_DISABLED_BUILTIN_IDS = "iptv_disabled_builtin_ids"`、`IPTV_LAST_CHANNEL = "iptv_last_channel"`、`IPTV_SOURCE_LOAD_TIMEOUT_SECONDS = "iptv_source_load_timeout_seconds"` |
| `refactor/settings/axtv/settings/Settings.java` | 对应类型化访问器（照 `liveVideoResolution()` 的"缺省回退"写法）；列表键走 `JsonCodec`，不自造分隔符格式 |
| `refactor/settings/axtv/settings/SettingsTypes.java` | 若 `SourceInput` 形态需要枚举承载（URL/CONTENT），在 iptv 模块里定义即可，**不塞进 SettingsTypes**（避免配置层携带业务模型） |
| `refactor/settings/NOTES.md` | 配置键全表补这 4 行（该表是既有门的数据源，漏一行就是不一致） |

验收：新键读写往返一致；无旧键迁移（本功能全新，无历史数据）。

### 阶段 C —— 手机扫码推送通道（复用 9978）

| 落点 | 内容 |
|---|---|
| `android/app/src/main/assets/web/iptv.html` | 照 `submit.html` 骨架：大 textarea（可粘贴 URL **或**整份 m3u 正文，D1）+ 名称输入 + 推送按钮 + 成功页；沿用 `{{THEME_CSS}}` / `{{PANEL_HEAD}}` 占位符；JS 从 `location.search` 取 `token` 并随 POST 带上 |
| `refactor/panel/axtv/panel/IptvStore.java` | 核心层接口（对称 `AuthStore`）：`List<SourceJson> sources()` / `addSource(String json)` / `removeSource(String id)`；**带 `InMemory` 实现供 JVM 单测** |
| `refactor/panel/axtv/panel/IptvSourceHandler.java` | `PORT = CookiePushHandler.PORT`（9978，**不开新端口**，少一个防火墙/常驻服务面）；路由：`GET /iptv` → 页面，`POST /api/iptv` → 校验并落库，`GET /api/iptv` → 当前用户源列表（供手机回显）；整表照 `CookiePushHandler.routes()` 的写法 `.requireAuth(this.auth)` |
| `refactor/panel/axtv/panel/PanelController.java` | 新 `iptvSubmitUrl()`，逐字照 [:85](file:///\\172.29.147.201\projects\AxTV\refactor\panel\axtv\panel\PanelController.java#L85) 的 `cookieSubmitUrl()` 形态；`startCookieServer()` 复用（同端口同一 server，路由表合并即可，不新建 `PanelServer`） |
| `android/.../panel/PanelRuntime.kt` | 装配 `AndroidIptvStore`（SharedPreferences `douyin_auth`，与 `AndroidAuthStore` 同文件同 `apply()` 语义） |

**安全约束（不可协商）**：新路由必须在 `routes()` 返回前经过 `.requireAuth(auth)`，与 Cookie 推送共用同一 `PanelAuth` 令牌，
**不得为直播源另造免鉴权通道**。OPTIONS 预检照旧放行。直播源不是机密凭据，因此不额外加密存储，但传输仍只在局域网内、仍带令牌。

验收：单测覆盖「无 token → 401」「错误 token → 401」「OPTIONS 无 token → 200」「正文/URL 两种形态各落一条源」。

### 阶段 D —— 电视端二维码入口

| 落点 | 内容 |
|---|---|
| `android/.../ui/settings/SettingsActionDialogs.kt` | 新增 `LiveSourceQrDialog`，照 `InteractionCredentialQrDialog` [:159](file:///\\172.29.147.201\projects\AxTV\android\app\src\main\java\com\github\axtv\dv\ui\settings\SettingsActionDialogs.kt#L159) 逐字同构：`PanelRuntime.controller?.iptvSubmitUrl()` → `QrLinksDialog(links, …)`；URL 为空（面板未启动）时**显式提示"面板服务未运行"**，不显示空白二维码 |
| `CredentialActionsDialog` / 设置页动作行 | 补一行「添加电视台直播源」；同时在「电视台」列表页的空态给同一个入口（D4 的可达性补偿） |

验收：真机上电视出码 → 手机扫码 → 页面加载 → 粘贴一条 m3u URL → 推送 → 电视端列表出现该源。这一段需要一次真实设备动作，我会在开工前给一份**单轮核对清单**（不是一步一验）。

### 阶段 E —— UI 与播放接线

| 落点 | 内容 |
|---|---|
| [android/.../ui/nav/NavRoutes.kt](file:///\\172.29.147.201\projects\AxTV\android\app\src\main\java\com\github\axtv\dv\ui\nav\NavRoutes.kt) | 新常量 `IPTV = "iptv"`、`IPTV_PLAY = "iptv/play"`；`NavDestination.Iptv` / `IptvPlay(channelId)`；`parse()` / `routeOf()` / `KNOWN_PAGES` 三处同批补齐（**漏 `routeOf` 会导致返回栈持久化丢路由**）；widget 别名不需要 |
| `android/.../ui/live/LiveListScreen.kt` | 只在顶部动作行加一个「电视台」入口按钮，`onNavigate(NavRoutes.IPTV)`。**不改 `LiveTab`、不碰 `LiveRepository`**（理由见 D4：那条链是 webcast 分区表，IPTV 进去必空） |
| `android/.../ui/iptv/IptvListScreen.kt` | 源分组 + 频道网格（复用 `FocusMemory` 焦点恢复、失败重试焦点兜底，同 `LiveListScreen` [:83](file:///\\172.29.147.201\projects\AxTV\android\app\src\main\java\com\github\axtv\dv\ui\live\LiveListScreen.kt#L83)）；每个源显示健康行（上次抓取时间/频道数/HTTP 码）；空态与"源全部拉取失败"是**两句不同的话**，不合并 |
| `android/.../ui/iptv/IptvPlayerScreen.kt` | 播 `Media3Engine.setMediaItem(url, useCache = false)`（D5）+ `PlayerEngine` 的失败重试；同频道候选 URL 逐个降级；全部失败给显式错误页 + 「换源重试」 |
| `android/.../ui/AxTvApp.kt` | `NavDestination.Iptv/IptvPlay` 分支接线；`IptvPlay` 进 immersive 集合（对齐 `LiveRoom` 的处理，[:221](file:///\\172.29.147.201\projects\AxTV\android\app\src\main\java\com\github\axtv\dv\ui\AxTvApp.kt#L221)） |
| `refactor/tv/` | **不动**。该模块是 EPG Preview Channel，与本功能无关，命名相近容易被误改，在 NOTES.md 里显式声明 |

验收：真机开播一个央视频道出画面；拔掉一条候选 URL 后自动落下一条；源全死时页面显示原因而非黑屏。

### 阶段 F —— 内置源实测与留档（阶段 A 的 `BuiltInSources` 数据前置）

1. 候选清单来源：**公开维护的 IPTV 索引项目**（按国家/地区分组的社区 m3u 合集）＋ 你提供的私有源地址。我会先枚举候选、
   逐个 `HttpFetcher.getText` 匿名 GET，**只读、不 POST、不带任何凭据**。
2. 每个候选记录：规范化域名、HTTP 码、字节数、`#EXTINF` 计数、解析成功的频道数、央视/卫视覆盖数、抓取时间戳。
3. 按「频道数 ≥ 阈值 且 央视+卫视覆盖齐全 且 连续两次抓取都成功」筛出 **≥4 份**内置源；不达标的写进留档但不进内置表。
4. 产出 `docs/电视直播源核对.md`（原始读数，可复核），`BuiltInSources.java` 只从这份留档取数。
5. **联网核对需要你显式授权**（外部只读请求）。我会在阶段 F 开工时一次性申请，并说明要发几个请求、打到哪些域名。

验收：留档里每个内置源都有读数；`BuiltInSources.java` 的地址集合与留档**逐条一致**（这条写成门，防止两边漂移）。

---

## 6. 门禁（对齐 [refactor/TESTS.md](file:///\\172.29.147.201\projects\AxTV\refactor\TESTS.md)，现状 180 用例 / 24 类）

新增测试类（放 `refactor/tests/`，JUnit 5）：

| 类 | 覆盖 | 必须有负控（破坏样本只让对应那条红） |
|---|---|---|
| `M3uParserTest` | 标准 m3u / CRLF / BOM / 无 `#EXTM3U` / 只有 URL 行 / `#EXTVLCOPT` referrer / 属性里带逗号 / 空体 / 超长行 / 注释行 | 每个破坏形态一条 |
| `SourceInputDetectTest` | D1：`#EXTM3U` 前导空白、`#EXTINF` 开头、`http` 开头、空串、非 http 非 EXT 的垃圾串 | 判定不能"默认当 URL" |
| `ChannelCatalogTest` | 归一聚合（大小写/全半角/空白）、跨源候选顺序、内置被禁用后剩余、用户源删除不影响内置 | 并排计数域不得收敛成同一个数（D2） |
| `PlaylistLoaderTest` | 注入假 `HttpFetcher`：200/404/超时/超大响应截断/非 m3u 内容 | 失败必须带原因，不能返回空表当"没有频道" |
| `IptvSourceHandlerTest` | 无 token→401、错 token→401、OPTIONS→200、POST 正文/URL 各成功、超限体 | 用 `AuthStore.InMemory` + `IptvStore.InMemory`，纯 JVM 跑 |
| `BuiltInSourcesConsistencyTest` | `BuiltInSources.java` 的 URL 集合 ⊆ `docs/电视直播源核对.md` 里"通过"的行 | 手工漂移即红 |

Android 侧（无法进 JVM 门）：`assembleDebug` 编译 + 阶段 D/E 的真机核对清单。

**全量门禁只在最后阶段收尾跑一次**，不逐修逐跑。

---

## 7. 明确不做（避免顺手扩范围）

- ❌ EPG 节目单 / 预告 —— 原版 `refactor/tv/` 是抖音 Preview Channel，不是电视台 EPG，需要另立需求
- ❌ 时移、回看、录制
- ❌ 弹幕（电视台流无此协议）
- ❌ 源鉴权绕过 / DRM 处理 / 加密流解密
- ❌ 自建转播或把流二次分发
- ❌ 多播放器并行、画中画里的电视台（PIP 现只服务点播，扩它要单独评估）
- ❌ 在线二维码服务（zxing 本地编码已够，不引入外部依赖）

---

## 8. 进度

| 阶段 | 状态 | 读数 |
|---|---|---|
| A 核心模块 | 完成 | M3uParser/Channel/Playlist/Sources/Store + NOTES.md；BuiltInSources 5 源（实测条目 377/554/1273/154/516，抽检可连 19/10/17/7/3） |
| B 配置键 | 已完成 | SettingsKeys/Settings 已添加 IPTV_SOURCES_LIST / IPTV_DISABLED_BUILTIN_IDS / IPTV_LAST_CHANNEL / IPTV_SOURCE_LOAD_TIMEOUT_SECONDS 及访问器 |
| C 扫码通道 | 已完成 | IptvStore / IptvSourceHandler / IptvRoutes 在 refactor/iptv 下实现；PanelController 已 wiring |
| D 二维码入口 | 已完成 | LiveSourceQrDialog 已追加至 SettingsActionDialogs.kt；OPTIONS 放行与 requireAuth 共用 |
| E UI/播放 | 已完成（真机验证待做） | IptvListScreen / IptvPlayerScreen 已建并接入 AxTvApp；电视台播放页已入 immersive 全屏判定；随 0.1.3 发布。剩余仅真机验证 |
| F 源实测留档 | 完成 | 实测数据 2026-11-07 写入 BuiltInSources（出处 / 核对日期 / 条目数 / 抽检可连数四项齐）；归档文档已补至 [docs/电视直播源核对.md](file:///\\172.29.147.201\projects\AxTV\docs\电视直播源核对.md)，读数为 5 源 / 条目 377·554·154·516·1273 / 抽检可连 19·10·7·3·17 |

**执行顺序建议：F → A → B → C → D → E。**
F 前置是因为 `BuiltInSources` 的数据只能来自实测，先写代码会造出一张假表；
其余按依赖方向不变。
