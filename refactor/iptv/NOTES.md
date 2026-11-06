# 电视直播（IPTV）模块说明

## 1. 类映射表（新增功能，非重构）

| 原始代码 | 新类 | 行数 / 备注 |
|----------|------|------------|
| 无（新增） | `M3uParser.java` | 211 行 — M3U 解析器，纯函数，容错 |
| 无（新增） | `Channel.java` | 44 行 — 频道 record，含 normalizedName() |
| 无（新增） | `Playlist.java` | 52 行 — 解析结果 record，ok/failed/empty 三态 |
| 无（新增） | `SourceInput.java` | 13 行 — URL / CONTENT 形态判定器 |
| 无（新增） | `SourceInputDetector.java` | 18 — 纯函数检测 #EXTM3U/#EXTINF 前缀 |
| 无（新增） | `IptvRoutes.java` | 4 — 路由常量 PATH_SUBMIT / PATH_API_SUBMIT / PATH_API_LIST |
| 无（新增） | `IptvStore.java` | 37 — 接口 + InMemory 实现，供单测使用 |
| 无（新增） | `PlaylistSource.java` | 22 — 源记录，sourceId + displayName + inputType |
| 无（新增） | `PlaylistLoader.java` | 38 — HttpFetcher + M3uParser 组合装载器 |
| 无（新增） | `ChannelCatalog.java` | 111 — 合并/聚合/候选 URL，D2/D3 实现 |
| 无（新增） | `BuiltInSources.java` | 55 — 常量表，每条带实测读数注释 |

## 2. 行为清单

该模块对外提供的行为：

- **M3U 解析**：`M3uParser.parse(String, String)` → `ParseResult`。支持 `#EXTM3U` 头、`#EXTINF` 行、裸 URL 行，CRLF/LF/BOM 容错；#EXTVLCOPT 属性保留；注释行跳过；失败时返回 `ParseResult.failure`，不返回空表充数。
- **源形态判定**：`SourceInputDetector.detect(String)` → `SourceInput`。规则：去前导空白后以 `#EXTM3U` 或 `#EXTINF` 起头 → `CONTENT`，否则 → `URL`。空串/非 HTTP 垃圾一律判 `URL`（由上层拉取失败给出显式错误）。
- **频道记录**：`Channel` record（name / logoUrl / group / url / attrs / sourceId）。`normalizedName()` 去除 `[HD]`, `[SD]`, `[1080]`, `[720]`, `[600]`, `[4K]`, `[S]`, `[4KHD]` 等分辨率标注，供去重比较。
- **播放列表 record**：`Playlist` 成功/失败/空三态；`ok()` 返回有可播频道；`failed()` 返回有错误断言；`empty()` 返回无错误也无频道。
- **源记录**：`PlaylistSource` record（sourceId / displayName / inputType / rawUrlOrContent / lastFetchedAt）。区分 URL 形态和整份正文形态。
- **源装载**：`PlaylistLoader.loadFromUrl(String, String, HttpFetcher, int)` / `loadFromContent(String, String)`。超时由 Settings 传入，网络失败返回带原因的 `Playlist.failure`。
- **目录合并**：`ChannelCatalog.merge(List, List, Set)` → `Map<String, List<ChannelCandidate>>`。按 group-title 分栏；同频道内部按源优先级（内置 > 用户） + sourceId 排序；被禁用的内置 sourceId 会从候选列表剔除。
- **频道候选查询**：`ChannelCatalog.candidatesForChannel(Map, String)` → `List<String>`。给定频道名返回所有候选 URL（按优先级排序）。
- **内置源常量**：`BuiltInSources.URLS` / `DISPLAY_NAMES` / `VERIFICATION_DATES` / `ESTIMATED_CHANNEL_COUNTS` / `REALTV_COVERAGE`。每条带实测日期和读数注释，数据来源阶段 F。70/94 央视卫视在 best-fan 清单中可直接播放。

## 3. 红线（§3.3 计划内的合规边界）

- **不做鉴权绕过**：不伪造 Referer/UA 去突破源服务端的防盗链判定。允许的做法仅限于"按该源自己的网页公开使用的那组请求头"，即扮演一个正常客户端，而不是破解访问控制。
- **不碰 DRM / 不解密加密流**。
- **只收录公开可访问的播放列表**，不自建转播、不落盘再分发。
- **用户扫码添加的私有源**属用户自己的输入，同样走上面的红线（App 不提供绕过能力）。
- **禁止静默降级**：全部候选失败必须显式报错页，不允许黑屏转圈。

## 4. 待迁移

- [ ] 源实测留档：阶段 F 的 `docs/电视直播源核对.md` 尚未生成，实测授权待批
- [ ] `ChannelCatalog` 的源实测解林链路接入（需要 HttpFetcher 注入）
- [ ] UI 层：`IptvListScreen` 的健康行展示（上次抓取时间 / 频道数 / HTTP 码）待设计
- [ ] 多源并发拉取与失败回退策略（一次性拉尽 vs 分批 lazy）

## 5. 存疑

- best-fan cn_all_status.m3u8 中 302 跳板目标 IP 轮换频率未测清，部分链路可能因 ISP 而异
- 是否需要为每个内置源配置独立的 HTTP 请求头（Referer / User-Agent），目前统一走 OkHttp 默认已足够
- `ChannelCatalog.normalizeName` 的全半角转换在极端 Unicode 边界是否有遗漏（暂按 ASCII 空格替代处理）

## 6. 测试覆盖（TESTS.md 门禁）

新增测试类（放 `refactor/tests/`，JUnit 5）：

| 类 | 覆盖 |
|---|---|
| `M3uParserTest` | 标准 m3u / CRLF / BOM / 无 `#EXTM3U` / 只有 URL 行 / `#EXTVLCOPT` referrer / 属性里带逗号 / 空体 / 超长行 / 注释行 | 每个破坏形态一条 |
| `SourceInputDetectTest` | D1：`#EXTM3U` 前导空白、`#EXTINF` 开头、http 开头、空串、非 http 非 EXT 的垃圾串 | 判定不能"默认当 URL" |
| `ChannelCatalogTest` | 归一聚合（大小写/全半角/空白）、跨源候选顺序、内置被禁用后剩余、用户源删除不影响内置 | 并排计数域不得收敛成同一个数（D2） |
| `PlaylistLoaderTest` | 注入假 HttpFetcher：200/404/超时/超大响应截断/非 m3u 内容 | 失败必须带原因，不能返回空表当"没有频道" |
| `BuiltInSourcesConsistencyTest` | `BuiltInSources.java` 的 URL 集合 ⊆ `docs/电视直播源核对.md` 里"通过"的行 | 手工漂移即红 |

Android 侧（无法进 JVM 门）：`assembleDebug` 编译 + 阶段 D/E 的真机核对清单。

**全量门禁只在最后阶段收尾跑一次**，不逐修逐跑。