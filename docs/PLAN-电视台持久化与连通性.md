# PLAN —— 电视台：持久化 / 连通筛选 / 分组修正 / 质量标注 / 节流更新

需求（用户原话五项）：
1. 持久化电视频道数据，不要每次启动或切过来都下载更新；
2. 依据连通状况筛选，连不上就隐藏；
3. 允许手动修正电视频道的分组；
4. 标注连接状况为连接质量高低；
5. 适当时机下载更新，但避免每次都下，节约资源。

---

## 1. 普查结论（先证既有实现，不造重复机制）

| 现状 | 证据 | 对本次的影响 |
|---|---|---|
| 目录只有**进程内**缓存 | [IptvRepository.kt:74](file:///\\172.29.147.201\projects\AxTV\android\app\src\main\java\com\github\axtv\dv\ui\iptv\IptvRepository.kt#L74) `@Volatile private var cached` | **需求 1 的根因**：进程死即丢，每次冷启动重拉 5 份内置 + 全部用户源。会话内切页已不重拉（`cachedCatalog()`），所以「切过来就下载」只在进程被杀后成立 |
| 用户源**已**持久化 | [AndroidIptvStore.kt](file:///\\172.29.147.201\projects\AxTV\android\app\src\main\java\com\github\axtv\dv\panel\AndroidIptvStore.kt) 落 Settings `iptv_sources_list` | 不要重做；缺的是**解析后目录**的持久化，不是源清单 |
| **没有任何流级活性数据** | `IptvSourceHealth` 只记「m3u 清单能否拉到」（channels/bytes/error） | **需求 2、4 无数据源**，必须新建。清单 200 ≠ 能播 |
| 分组纯取自 m3u | `IptvRepository.kt:137` `ch.group().ifBlank { "未分组" }` | 需求 3 需新增覆盖层，且必须**跨刷新存活** |
| 死路：内置源禁用未接线 | `Settings.iptvDisabledBuiltinIds()`（Settings.java:625）+ `ChannelCatalog.merge` 的 `disabledBuiltInIds` 参数（ChannelCatalog.java:70）**全仓零消费点**；`IptvRepository.kt:86` 用的是 `id.startsWith("disabled")`，而真实 ID 是 `vbskycn`/`tianmutv`，永不命中 | D2 裁定「内置可逐个禁用」实际没落地。本次顺带接通（同一段代码要重写） |
| 重复实现：合并/分组绕过了核心层 | 核心层有 `ChannelCatalog.merge()`(:73) 与 `candidatesForChannel()`(:133)，但 `IptvRepository` 只用了 `normalizeName()`(:120)，自己在 113–152 行重写了聚合与分组 | 本次**撤掉 Kotlin 侧重写，改调核心层 merge**；否则禁用/质量/覆盖三处都要在两套逻辑里各修一遍 |
| 探测能力已具备 | `HttpFetcher.getStream(url, userAgent)` 返回 `StreamResponse(statusCode, InputStream, contentLength)` | 可做**有界读**（读首 N 字节即关），不必整条下载 |
| 无既有 liveness/probe 实现 | 全仓 grep 仅 `QrLoginBridge` 命中（无关） | 新建不构成重复 |

**并发协调**：`IptvListScreen.kt` 正被另一会话改（`rememberSaveable` + `FocusMemory` 焦点恢复，已见 diff）。
因此阶段顺序定为**核心层 → 仓库/存储层 → UI**；UI 阶段若那批仍未落地，则只改不提交，等其落地后统一切。

---

## 2. 红线（违反即判构造性错误）

- **R1 三态不得合并**：`未探测` / `探测失败` / `探测通过` 是三种缺席，各占一句。
  **需求 2 的「隐藏」只作用于确证不可用，绝不隐藏未探测** —— 否则首装/清缓存后列表全空，把「还没测」伪装成「都不能播」。
- **R2 两套计数域不并排成可比**：源级健康（清单可拉，分母=源数）与频道级活性（流可连，分母=已探测频道数）语义不同，
  不得并成一张表、不得收敛成同一个数。顶部源健康行保留原样，另起一行写「已探测 N / 可用 M / 隐藏 K」。
- **R3 探测剂量推导，不取上限**：不得一次并发探全部候选（5 源合计 2874 条）。并发上限、单批条数、单条读取字节数都要有推导与两侧钉死，并留运行读数。
- **R4 探测口径 = 生产播放口径**：用与 `iptvFactory()` 相同的中性 UA、不带抖音 Referer；探的是**流地址/首个分片**，不是 m3u 清单本身。
- **R5 不伪造 Referer/UA 破解防盗链、不自建转播**（沿用 PLAN-电视直播 §7）。
- **R6 核心层纯 Java 17**，Android 能力经接口注入，模块必带 `NOTES.md`（refactor/CONVENTIONS.md 铁律）。

---

## 3. 架构与落点

### 3.1 核心层 `refactor/iptv/`（纯 Java 17，可 JVM 单测）

| 新增/改动 | 职责 |
|---|---|
| `CatalogSnapshot.java`（新） | record：`schemaVersion` / `fetchedAtEpochMs` / `List<ChannelEntry>` / `List<SourceHealthEntry>`。纯数据 + JSON 编解码（不碰 Android） |
| `LivenessRecord.java`（新） | record：`channelId` / `state(UNKNOWN,OK,DEAD)` / `quality(GOOD,FAIR,POOR)` / `ttfbMs` / `consecutiveFailures` / `lastProbeAt` / `lastOkAt` / `probeCount` |
| `LivenessProbe.java`（新） | 注入 `HttpFetcher`；`probe(candidateUrl)` → 有界读首片，返回状态码 + TTFB + 读取字节数；HLS 先取清单再取首片，TS 直连做有界读 |
| `QualityPolicy.java`（新） | **判据集中一处**：TTFB → 质量档；连续失败 → DEAD。阈值由 `iptvSourceLoadTimeoutSeconds` 推导（见 §4），不自造魔数 |
| `RefreshPolicy.java`（新） | 双 TTL 判据：`shouldRefreshCatalog(now, fetchedAt)` / `shouldReprobe(now, lastProbeAt)`；纯函数，可单测边界 |
| `GroupOverrides.java`（新） | `channelId → group` 映射的归一与合并；覆盖优先于 m3u 的 group-title |
| `ChannelCatalog.merge`（改消费方式） | 由 Kotlin 侧改为真正调用，传入 `disabledBuiltInIds`，**同时接通死路** |
| `NOTES.md`（改） | 补新建类映射、行为清单、红线 R1–R4、待迁移 |

### 3.2 Android 适配层

| 落点 | 内容 | 依据 / 代价 / 回退 |
|---|---|---|
| `AndroidIptvCatalogStore.kt`（新） | 目录快照落 **文件** `filesDir/iptv/catalog.json`，临时文件 + 原子 rename | 依据：目录可达数百 KB～MB 级，SharedPreferences 全量驻内存且每次 settings 访问都读，不适合大 blob。代价：自管 IO 与 `schemaVersion`。回退：删文件即回到联网加载 |
| `AndroidIptvLivenessStore.kt`（新） | 活性记录落 **独立文件** `filesDir/iptv/liveness.json` | 依据：与目录**分开**，刷新目录不得清空活性历史（两者 TTL 不同）。回退：删文件 → 全部回到 UNKNOWN（按 R1 不隐藏） |
| Settings 新键 `iptv_group_overrides` | 用户手动分组，JSON map，落 SharedPreferences | 依据：这是**用户意图**，量小（几十条）、必须跨目录刷新与跨版本存活，与快照生命周期不同。访问器按既有 `iptvSourcesList` 同款写法 |
| Settings 新键 `iptv_hide_dead`（默认 true） | 需求 2 的开关 | 让用户能看见被隐藏的东西，避免静默消失 |
| `IptvRepository`（改） | 启动先读盘 → 立即出目录；撤掉自写聚合改调 `ChannelCatalog.merge`；接 `iptvDisabledBuiltinIds`；接 `GroupOverrides`；接活性过滤与质量标注 | 这是本次主改文件 |
| `IptvProbeScheduler.kt`（新） | 协程调度：并发上限 4、单批 ≤ 20、只在电视台页可见时跑；结果写回 liveness store | R3 的剂量落点 |

### 3.3 UI（`IptvListScreen` / `IptvPlayerScreen`）

- 卡片右上角**质量徽标**：优 / 良 / 差 / 不可用 / 未探测（五态，颜色与文案各不同，未探测用中性灰，绝不渲染成「差」）。
- 顶部第二行：`已探测 N · 可用 M · 已隐藏 K`（与源健康行分开，R2）。K>0 时给「显示已隐藏」入口，可临时取消隐藏以便手动救回。
- 卡片**长按 OK / 菜单键** → 「修改分组」对话框：列出既有分组 + 新建分组；写入 `iptv_group_overrides`，立即重组列表。
- 顶部按钮区新增「检测连通性」：触发有界批量探测（当前分组优先），带进度与可取消。
- 「刷新」语义明确化：强制重拉清单 + 重探当前分组（不再是静默全量）。

---

## 4. 质量分档与隐藏判据（参数有推导，可证伪）

设 `T = Settings.iptvSourceLoadTimeoutSeconds()`（现缺省 10s，已有键，不新造）。

| 档 | 判据 | T=10s 时 | 推导依据 |
|---|---|---|---|
| 优 GOOD | 探测成功且 `TTFB ≤ T/8` | ≤ 1.25s | 直播「秒开」体验上界；取 T 的 1/8 使阈值随用户超时档联动 |
| 良 FAIR | 成功且 `T/8 < TTFB ≤ T/3` | ≤ 3.33s | 可等待但不流畅；1/3 为超时预算的三分之一 |
| 差 POOR | 成功但 `TTFB > T/3`，或近 5 次探测中有失败 | > 3.33s | 能播但体验差，不隐藏只标注 |
| 不可用 DEAD | **连续 3 次**探测失败（或连续 3 次真实播放失败） | — | 单次失败可能是瞬时抖动，3 次才判死；与「重试/下一频道」的既有回退语义一致 |
| 未探测 UNKNOWN | 无任何记录 | — | **永不隐藏**（R1） |

- 阈值写成 `QualityPolicy` 常量并注释推导；**首轮实测后按真实 TTFB 分布标定**，标定读数写进 NOTES.md（不先拍死）。
- **主信号 = 真实播放结果**（零额外网络）：播放成功 → OK + 记 TTFB；播放失败 → 失败计数 +1，达 3 → DEAD。
  这条最省资源，直接服务需求 5。主动探测只作为补充（手动按钮 / 可见分组的机会式后台探测）。

---

## 5. 刷新时机（双 TTL，需求 1 + 5）

| 触发 | 行为 |
|---|---|
| 冷启动 / 切到电视台 | **只读盘**，立即渲染，零网络。盘上没有才联网（首装唯一一次） |
| 盘上目录 age > **7 天** | 进入页面后**后台**增量重拉清单，不阻塞渲染；拉完原子替换快照 |
| 活性记录 age > **24 小时** | 标记为待重探（仍显示旧档 + 「数据可能过期」提示），按 §4 主信号优先用真实播放结果刷新 |
| 用户点「刷新」 | 强制重拉清单 + 重探当前分组 |
| 手机推送新源 | **只拉该源**并增量合并，不动其它源、不清活性历史 |
| 播放某频道 | 用播放结果更新该频道活性（零额外请求） |

两个 TTL 分开是因为**输入独立**：社区清单本身变化慢（频道增删以周计），而流地址失效快（以小时/天计）。
合成一个 TTL 会导致「为了追流失效而天天重拉整份清单」，正是需求 5 要避免的浪费。

---

## 6. 阶段计划（按序，上一阶段门不过不开下一阶段）

| 阶段 | 内容 | 验收门 |
|---|---|---|
| **G1 核心层数据模型与判据** | `CatalogSnapshot` / `LivenessRecord` / `QualityPolicy` / `RefreshPolicy` / `GroupOverrides` + JSON 编解码 | `javac --release 17` 零错误；新增单测全绿，**必含坏样本**：缺字段/旧 schemaVersion/空数组/损坏 JSON/阈值两侧边界（T/8 与 T/3 各取 ±1ms）/连续失败 2 次不判死 3 次判死 |
| **G2 探测器** | `LivenessProbe`（HLS 取首片 / TS 有界读）+ 剂量常量 | 单测：2xx/404/超时/非播放列表内容/中断流；断言**读取字节数有上限**、断言并发与单批常量被真正消费 |
| **G3 存储与仓库接线** | 两个 Store + 2 个 Settings 键 + `IptvRepository` 改造（撤重复聚合、接通禁用死路、接覆盖/活性/质量） | 单测：快照原子写后读回逐字相等；刷新目录**不清**活性；覆盖分组跨刷新存活；禁用某内置源后其频道消失（**这条现在必红，修完须转绿**） |
| **G4 调度与播放反馈** | `IptvProbeScheduler` + 播放成功/失败回写活性 | 单测：并发上限不被突破、页面不可见时不调度、播放失败 3 次→DEAD、UNKNOWN 不被过滤 |
| **G5 UI** | 质量徽标五态 / 计数行 / 隐藏开关 / 分组对话框 / 检测按钮 | 编译通过 + 真机核对清单（见 §7）。**与并发会话重叠则只改不提交** |
| **G6 收口** | NOTES.md、文档、全量门禁一次跑 | `./gradlew :tests:test` + `:app:assembleRelease`（**须离开网络盘构建**，aapt2 在 UNC 路径会超时） |

---

## 7. 真机核对清单（G5 验收，一轮做完不来回）

1. 首装（清数据）进电视台 → 联网一次 → 杀进程重进 → **零网络请求**且列表立即出现（抓包或 logcat 确认）。
2. 断网进电视台 → 显示盘上目录 + 明确「离线，数据来自 X 天前」，**不是**空态、**不是**全部标不可用。
3. 播一个已知连不上的台 3 次 → 徽标转「不可用」→ 列表隐藏它 → 顶部 K 计数 +1 → 「显示已隐藏」能找回。
4. 未探测频道在隐藏开关开启时**仍然可见**（R1 的核心验收）。
5. 手动把某频道改到新建分组 → 杀进程重进 → 分组仍在；再点「刷新」重拉清单 → 分组**仍在**。
6. 禁用某内置源 → 该源频道消失、其它源不受影响（死路接通的验收）。
7. 手机推一条新源 → 只有该源被拉取，其它源无请求，活性历史未被清空。
8. 质量徽标五态各出现一次，颜色/文案互不相同；「未探测」不被误读为「差」。

---

## 8. 待拍板项（推荐值已给出，不改就按推荐做）

| # | 选项 | 推荐 | 依据 / 代价 |
|---|---|---|---|
| **P1** | 目录落盘位置 | **文件** `filesDir/iptv/catalog.json` | SharedPreferences 不适合数百 KB～MB blob 且全量驻内存。代价：自管原子写与 schema 版本 |
| **P2** | 活性主信号 | **真实播放结果优先**，主动探测仅作补充 | 最省资源（需求 5）；代价：冷门频道长期停留在 UNKNOWN，需手动「检测连通性」补 |
| **P3** | 双 TTL 取值 | 清单 **7 天** / 活性 **24 小时** | 输入独立（§5）。代价：两个常量要各自标定；可后续做成设置项 |
| **P4** | 判死阈值 | 连续 **3** 次失败 | 单次失败可能瞬时抖动；与既有「重试/下一频道」回退语义一致 |
| **P5** | 质量阈值 | 由 `T=iptvSourceLoadTimeoutSeconds` 推导（T/8、T/3），首轮实测后标定 | 不造魔数、随用户超时档联动。代价：T 很小时档位会挤在一起，需下限保护 |
| **P6** | 是否顺带撤掉 Kotlin 侧重复聚合并接通禁用死路 | **是** | 同一段代码本次必须重写；不撤则禁用/质量/覆盖要在两套逻辑各修一遍。代价：G3 范围变大，但消掉一处长期重复 |
| **P7** | 隐藏开关默认值 | **默认开**，但顶部显式给出 K 与「显示已隐藏」入口 | 满足需求 2 又不静默消失（R1/R2） |

---

## 9. 明确不做

- ❌ 不做订阅自动轮询/定时后台服务（只在进页面且 TTL 过期时后台增量，避免常驻耗电）
- ❌ 不引入第三方预编译二进制或外部可用性 API
- ❌ 不把「未探测」计入任何可用率分母
- ❌ 不在本次改抖音直播间取数链（并发会话正在改 LiveRepository，避免撞车）
