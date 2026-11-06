# 搜索页（ui/search）接线说明

> 线 B 交付：只新增 `ui/search/**`，未改 `MainActivity.kt`、`ui/AxTvApp.kt`、`ui/nav/**`、
> `refactor/**`、`build.gradle.kts`、`libs.versions.toml`、`AndroidManifest.xml`。

## 页面签名（固定，供导航线接线）

本包同时提供 3 个入口，导航线按自己的路由模型二选一即可：

```kotlin
// 1) 线 B 固定交付签名：单页完成「热搜/联想 → 结果 tab」全流程
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
)

// 2) 对齐 ui/nav/CONTRACT.md 的双路由写法：输入页只负责输入/热搜/联想
@Composable
fun SearchPage(
    query: String? = null,
    onOpenResults: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
)

// 3) 对齐 ui/nav/CONTRACT.md 的双路由写法：结果页按 query 自动进入结果态
@Composable
fun SearchResultsPage(
    query: String,
    onOpenDetail: (awemeId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
)
```

接线示例（路由由导航线决定，本包不引用 nav、不发路由字符串）：

```kotlin
// 单页模式
SearchScreen(
    onBack = { /* 回 home */ },
    onOpenDetail = { awemeId -> /* 打开详情/播放页 */ },
)

// 双路由模式
SearchPage(query = null, onOpenResults = { q -> /* 打开 search/results?query=q */ }, onBack = { /* 回 home */ })
SearchResultsPage(query = q, onOpenDetail = { id -> /* 打开详情/播放页 */ }, onBack = { /* 回搜索页 */ })
```

- `onOpenDetail` 只接收 **awemeId**：视频卡片、直播条目点击时传入；用户条目**不可点**（见取证结论）。
- 本轮补齐「用户条目点击」「搜索历史」两个 stub，**对外签名无变化**，导航线无需改接线。
- `onBack`：仅在编辑态按 BACK 时调用；结果态按 BACK 先回到编辑态，不会直接退出应用。
- 双路由模式下 `SearchPage` 不加载结果：点热词/联想词/搜索只回填并调用 `onOpenResults(keyword)`。
- BACK 分层：`SearchScreen` 结果态 → 编辑态；`SearchResultsPage` 直接 `onBack()`（回搜索页）。

## 文件清单

| 文件 | 职责 |
| --- | --- |
| `SearchScreen.kt` | 页面骨架、输入栏、历史/热搜/联想、三 tab 结果、焦点编排、BACK 分层 |
| `SearchState.kt` | 状态机与数据层：SearchApi 调用、分页、错误文案、模型映射、格式化 |
| `SearchHistory.kt` | 本地搜索历史：SharedPreferences `search_history` / 键 `history`（与原版同名） |
| `README.md` | 本说明 |

## 已实现交互

1. **输入**：`BasicTextField` + 系统输入法（不引第三方键盘）；输入框有自绘焦点框；
   IME 搜索键 / 物理 Enter / 右侧「搜索」按钮均可提交；「清空」「返回」可 D-pad 操作。
2. **搜索前**：输入为空时先展示「历史搜索」（本地，若有），再展示 `hotSearch` 热搜列表
   （名次 + 词 + 热度），失败/空态均有面板与「重试」。
3. **搜索历史**：提交搜索后写入本地（新词置顶、去重、最多 10 条，仅登录态记录）；
   `LazyRow` 横排展示，点词立即搜索，「清空历史」清空并把焦点交还输入框。
4. **输入联想**：350ms 防抖后调 `suggestions`；为空或失败时回落 `suggestWords`；
   失败提示可点击重试，不阻塞输入。
5. **结果**：视频（3 列网格：封面 + 标题 + 作者 + 时长角标）、用户、直播三个 tab；
   每个分栏独立缓存与分页；空结果使用 `SearchUiSpec` 逐字空态文案，不白屏。
6. **打开详情**：视频/直播条目点击回调 `onOpenDetail(awemeId)`，本包不实现详情页。
7. **焦点**：`LazyColumn`/`LazyRow`；三个分栏各自保存 `LazyListState` 与焦点下标，
   首屏数据落地/切回已加载分栏时通过 `SearchState.focusSignal` 复位焦点；
   输入框上 `DPAD_UP/DOWN` 直接跳出，避免被文本光标吞掉。
8. **BACK**：结果态 → 编辑态（保留关键词与结果缓存）；编辑态 → `onBack()`。

## 取证结论（本轮补齐两个 stub）

### 1) 用户条目点击：不支持「看他人主页」

- `ui/nav/CONTRACT.md:20` 只有 `profile`（当前去向 `StubScreen`），没有任何 `profile/{uid}` 之类的他人主页路由。
- `ui/nav/CONTRACT.md:38-39` 的搜索契约只给 `onOpenDetail(awemeId)`，没有用户维度回调。
- `ui/profile/ProfileScreen.kt:83` 的固定签名是 `ProfileScreen(onBack, onOpenDetail)`；
  `:115` 调 `UserApi.profileSelf()`、`:141` 的 `secUid` 来自自己的资料响应——只能看自己。

结论：**不补路由**（也不允许改 `ui/nav/**` / `ui/profile/**`）。用户行改为：
不可点击（`Modifier.focusable()`，Enter 无动作）+ 整行 `alpha=0.62` + 右侧灰字「主页暂不可进入」，
不再保留「看着能点却没反应」的卡片。

### 2) 搜索历史：原版有本地实现

- `src/app/p000/tc4.java:33`：`getSharedPreferences("search_history", 0)`。
- `src/app/p000/tc4.java:36`：`getString("history", null)`，值为 JSON 字符串数组。
- `src/app/p000/ye4.java:954`：写入前判登录态（`fl4.m4214v()`，即 cookie 含 sessionid）。
- `src/app/p000/ye4.java:973`：过滤与本次关键词相同的旧条目（去重）。
- `src/app/p000/ye4.java:981`：`take(10)`——只保留最近 10 条。
- `src/app/p000/ye4.java:985`：`putString("history", new JSONArray(list).toString())`（新词在前）。
- `src/app/p000/qe4.java:1270`：分区标题「历史搜索」；`:1233`：「清空历史」按钮；`:122`：「热搜榜」。

结论：按证据落地为本地存储，键名逐字一致（`search_history` / `history`），
`refactor/settings` 内无对应键，因此实现放在 `ui/search/SearchHistory.kt`（只读写该文件，不碰 `refactor/**`）。

> 布局顺序：空输入时「历史搜索」在「热搜」之上（依据 `qe4.java:1270` 与 `:122` 的分区顺序）。
> 原版单条删除（`hu2.java` case 16）本轮未复刻，只做「清空历史」。

## Stub / 未实现（明确边界）

- **用户条目不可点**：契约无他人主页路由（见上取证），UI 已明确标注「主页暂不可进入」。
- **直播条目**只用 `SearchLiveLives.author` 展示主播与「直播中」，未解析 `rawdata`
  （直播间标题/封面需要额外 JSON 解析，不在本线范围）。
- **历史单条删除**未复刻（原版有，本轮只提供「清空历史」）。
- 视频卡片的封面未做占位图/加载失败兜底图，仅用底色避免白块。

## 数据与参数（只读消费，不改核心层）

- API 门面：`axtv.api.SearchApi(AppRuntime.api)`；未新建 OkHttp 客户端。
- 使用：`hotSearch`、`suggestions`、`suggestWords`、`videoSearch`、`userSearch`、`liveSearch`。
  未使用 `generalSearch`（仍是 String 且无 model）。
- 分页参数对照反编译调用点：
  - 视频 `offset=0/count=20` 首屏，之后 `count=10`；`sort_type="0"`、`publish_time="0"`、
    `filter_duration=""`；`search_id` 取上一页 `log_pb.impr_id`。
  - 用户每页 `count=10`，`filter_search=false`、`fan_filter_json=""`。
  - 直播首屏 `count=15`，之后 `count=10`。
- 业务 `status_code != 0` 按 `SearchUiSpec.errorLabel` 展示；网络异常按 `ApiException.Kind` 分支给可读文案。
- 服务端现要求登录后才能搜索：未登录请求 `search/item` / `live/search` / `general/search/single`
  返回 `status_code=2483`（`status_msg`「请先登录，再继续搜索吧」），联想与热搜仍可匿名使用。
  未登录时 `SearchState` 在发起结果请求前直接展示「搜索需要登录」提示；登录态失效时 2483 也映射到同一文案。

## 未验证项与可信度

| 结论 | 可信度 |
| --- | --- |
| 本次未执行 Gradle 构建，Kotlin 编译结果未验证 | 中（代码按现有 `ui/home` 的 TV Compose 用法编写，但未编译） |
| 系统输入法在 Android TV 上获焦即弹出的行为未真机验证 | 低（依赖 Compose CoreTextField 默认 IME 行为） |
| 分页 offset/count 与 search_id 参数来自反编译调用点（kq5/n11） | 高（逐行核对过实参） |
| 用户/直播 tab 的 `has_more`、`cursor` 字段语义按现有 record 推定使用 | 中（以 offset 递增为主，cursor 未参与翻页） |
| 焦点复位采用 `withFrameNanos` + FocusRequester，沿用 home 页同款写法 | 中（同款写法已在 home 页使用，本页未实测） |
| 三个入口签名与 `ui/nav/CONTRACT.md` 的 `SearchPage` / `SearchResultsPage` 对齐 | 高（逐字核对契约文档） |
| 历史键名/格式/上限与登录门槛对齐原版（`tc4.java` / `ye4.java`） | 高（逐行核对反编译产物） |
| 空输入时「历史搜索」位于「热搜」之上的纵向顺序 | 中（依据 qe4 两个分区标题，未还原完整布局树） |
| 用户行「focusable 但不可点击」的 D-pad 手感 | 中（未真机验证 Enter 无动作且焦点不丢） |
| 未改动任何范围外文件（`git status` 可复核） | 高 |
