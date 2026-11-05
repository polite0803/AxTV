# 电视频道发布（EPG / Preview Channel）模块

> 包 `axtv.tv`，纯 Java 17 + JDK。Android 的 `TvContract` / `ContentResolver` / `Bitmap`
> 全部由 `TvProvider` 接口隔离，核心同步算法可独立编译与测试。
> **源码只读**：本模块所有内容来自 `../../src/app/p000/`，未修改原始反编译产物。

## 1. 类映射表

| 原文件（src/app/p000/） | 行数 | 原职责 | 新类 | 行数 |
|---|---:|---|---|---:|
| `mb5.java` | 164 | 频道 id→显示名映射、Aweme→PreviewProgram、频道创建、节目查询 | `ChannelSpec` / `ProgramBuilder` / `TvChannelPublisher` | 90 / 118 / 236 |
| `lb5.java` | 515 | 频道同步协程（`invokeSuspend` case 0，4 参数构造器） | `TvChannelPublisher.sync` | 同上 |
| `lb5.java`（8 参数构造器 / case 1） | — | **不属于本模块**：`on5.java:570` 调用它做视频详情相关逻辑，与 EPG 无关 | — | 见「存疑 S5」 |
| `g73.java` | 861 | R8 合并类；其中 `m4569k`/`m4577t`/`m4578u`/`m4560G`/`m4561a` 是 PreviewChannelHelper | `TvProvider`（接口）+ `TvChannelPublisher` 的插入/更新算法 | 78 / 同上 |
| `at3.java` | 502+ | PreviewProgram 的 `ContentValues` 读回（`m1471a`）与按 SDK 裁剪（`m1472b`） | `PreviewProgram` / `TvProviderColumns` | 108 / 100 |
| `g12.java` (`m4356k`, byte=25) | — | Channel.Builder：校验 display_name / app_link_intent_uri，写 `type=TYPE_PREVIEW`，挂 logo URI | `TvChannelPublisher.ensureChannel` | 同上 |
| `jb5.java` | 13 | `m6599a`：SDK>=26 且 `resolveContentProvider("android.media.tv",0)!=null` | `TvChannelPublisher.isAvailable` | 同上 |
| `nb5.java:12` / `ob5.java:11` | — | 两个 Content URI 常量 | `ChannelSpec` | 同上 |
| `AbstractC1803x9.java:301` | — | `TvContract.requestChannelBrowsable` | `TvProvider.requestChannelBrowsable` | — |
| `eu5.java:104` (`f5486r`) | — | 频道查询 12 列 projection | `ChannelSpec.CHANNEL_PROJECTION` | 同上 |
| `eu2.java` | 164 | Flow collect：开关变化 / 稍后再看列表变化 → 同步 | `TvSyncTrigger` | 108 |
| `y30.java:290-310` | — | case 7（稍后再看）/ case 8（推荐首次非空 + 一次性标记） | `TvSyncTrigger` | 同上 |
| `jx4.java:432/446`、`o70.java:430` | — | `take(n)` / `trim()` / `Iterable.take(n)` | `ProgramBuilder` 内的私有工具 | 同上 |

## 2. 两个频道

| channel key | `display_name` | 数据来源 | 触发 |
|---|---|---|---|
| `axtv_recommend` | 推荐 | 首页推荐流 | 列表首次非空且只发一次 |
| `axtv_watch_later` | 稍后再看 | 稍后再看列表 | 列表每次变化（含变空） |

两者共用 `app_link_intent_uri = "axtv://douyin"`，`type = "TYPE_PREVIEW"`，
logo 为 `cacheDir/tv_channel_logo.png`（96×96 ARGB_8888，用应用图标绘制，PNG 质量 100；
**仅当文件不存在或长度为 0 时重新生成**）。

## 3. 字段全表

### 3.1 Channel（`ensureChannel` 写入）

| 列 | 值 | 来源 |
|---|---|---|
| `display_name` | `推荐` / `稍后再看` | `mb5.f13969c` |
| `internal_provider_id` | `axtv_recommend` / `axtv_watch_later` | 调用点字面量 |
| `app_link_intent_uri` | `axtv://douyin` | `mb5:99` |
| `type` | `TYPE_PREVIEW` | `g12.m4356k` |
| logo | `file://.../tv_channel_logo.png` | `mb5:101-125` |

`g12.m4356k` 还会在 `display_name` 为空时抛 `"Need channel name. Use method setDisplayName(String) to set it."`、
`app_link_intent_uri` 为空时抛 `"Need app link intent uri for channel..."`；本模块两个值都是非空常量，不会触发。

### 3.2 PreviewProgram（`ProgramBuilder.fromAweme` 写入，顺序即原 put 顺序）

| 列 | 值 / 规则 | 来源 |
|---|---|---|
| `channel_id` | 命中的频道 `_id` | `mb5:55` |
| `type` | `4`（固定） | `mb5:56` |
| `title` | `desc.take(60)`；空文案 → `（无标题）` | `mb5:57-61` |
| `short_description` | `"@" + author.nickname.trim()`；昵称为空 → `""` | `mb5:51,62` |
| `poster_art_uri` | `cover.urlList[0]` → `originCover` → `dynamicCover` 三级回退 | `mb5:47` |
| `poster_art_aspect_ratio` | 竖屏（`height > width`）`4`，否则 `0` | `mb5:50,65` |
| `duration_millis` | `video.duration`（毫秒） | `mb5:66` |
| `intent_uri` | `axtv://douyin/video/<awemeId>` | `mb5:67` |
| `internal_provider_id` | `awemeId`（同步去重键） | `mb5:69` |
| `weight` | `max(1, 30 - index)` | `mb5:70-71` |

**丢弃规则**：`awemeId` 为 null/空白，或三个封面都没有 URL → 该条不生成节目（`mb5:47-49` 返回 null）。

### 3.3 按 SDK 裁剪（`TvProviderColumns.applySdkFilter`，原 `at3.m1472b`）

| 条件 | 移除的列 |
|---|---|
| SDK < 24 | `season_title` |
| SDK < 26 | `review_rating_style`, `review_rating` |
| SDK < 26 | `internal_provider_id`, `preview_video_uri`, `last_playback_position_millis`, `duration_millis`, `intent_uri`, `transient`, `type`, `poster_art_aspect_ratio`, `poster_thumbnail_aspect_ratio`, `logo_uri`, `availability`, `starting_price`, `offer_price`, `release_date`, `item_count`, `live`, `interaction_count`, `author`, `cid`, `logo_content_description`, `genre`, `start_time_utc_millis`, `end_time_utc_millis`, `preview_audio_uri`, `tv_series_item_type` |
| 所有版本 | `browsable`（只读列） |
| SDK < 26 | `channel_id`, `weight` |

> 注意最后一条：低版本上写入的节目不带频道归属。原实现如此，本模块保留。

## 4. 同步时序（原 `lb5.invokeSuspend` case 0）

```
Flow 事件 → TvSyncTrigger 判定 → sync(channelKey, videos)
  1. isAvailable()：SDK<26 或无 android.media.tv provider → SKIPPED，什么都不做
  2. 取得 Mutex（原 o53 Mutex，多个同步协程串行）
  3. queryChannels() 里找 internal_provider_id == channelKey
     └─ 找不到 → NO_CHANNEL，直接返回（同步不会自动创建频道，创建由 ensureChannel 负责）
  4. take(30) → 逐条 ProgramBuilder.fromAweme → 跳过无效条目 → wanted 列表 + wantedIds 集合
  5. queryAllPrograms() → 按 channel_id 过滤 → existing
  6. 删除 existing 中 internal_provider_id 不在 wantedIds 的条目（含 null），仅 SDK>=26
     └─ 任一步异常 → Log.w("TvChannelPublisher", "同步频道 <key> 失败", e) → FAILED
  7. 建 existingIds: internal_provider_id → _id（无 id 的跳过）
  8. 遍历 wanted：
     ├─ 无既有 id → insertProgram(applySdkFilter(values, sdk))     [g73.m4578u]
     └─ 有既有 id → queryProgram(id) 逐字段 deepEquals，有差异才 update  [g73.m4560G]
  9. Log.i("TvChannelPublisher", "synced <key>: channel=<id> wanted=<n> existing=<n>")
 10. 释放 Mutex
```

`ensureChannel` 的插入子流程（原 `g73.m4577t`）：

```
insertChannel(values)
  ├─ SDK<26 → -1
  ├─ 返回 id < 0 → 抛 "Channel insertion failed"
  ├─ addChannelLogo(id, logoUri) == false → deleteChannel(id)
  │    → 抛 IOException("Failed to add logo, so channel (ID=..) was not created")
  ├─ SecurityException → Log.e("PreviewChannelHelper",
  │    "Your app's ability to insert data into the TvProvider may have been revoked.") → -1
  └─ browsable=true 且 SDK>=26 → requestChannelBrowsable(id)
     （browsable=true 且 SDK<26 时直接返回 -1，不插入）
```

`addChannelLogo`（原 `g73.m4561a`）按 logo URI 的 scheme 分路：
`android.resource` / `file` / `content` 走 `ContentResolver.openInputStream`，其余走 HTTP；
失败记 `PreviewChannel` / `PreviewChannelHelper` 日志并返回 false。

## 5. 触发条件全表（原 `eu2` / `y30`）

| 事件 | 条件 | 动作 |
|---|---|---|
| 推荐流到达 | 列表非空 && 开关开启 && ready && 未发布过 | `sync("axtv_recommend", list)`，置一次性标记 |
| 稍后再看列表变化 | 开关开启 && ready | `sync("axtv_watch_later", list)`（空列表也同步＝清空频道） |
| 用户开关变为开 | 之前为关 && ready | 用缓存的稍后再看列表立即同步 |
| 初始化完成 | 之前未 ready && 开关开启 | 用缓存的稍后再看列表补发一次 |
| `y30 case 7` | 开关开启 | 同时 `WidgetUpdater.pushVideos(WidgetKind.LATER, list)`（属 widget 模块） |

## 6. 常量表

| 常量 | 值 | 来源 |
|---|---|---|
| 最低 SDK | 26 | `jb5:11` / `g73` 各处 |
| 最多节目数 | 30 | `lb5:221` `o70.m8500l1(list, 30)` |
| 权重基数 / 下限 | 30 / 1 | `mb5:70-71` |
| 标题长度 | 60 | `mb5:61` |
| logo 边长 / 质量 | 96 / 100 | `mb5:103,112` |
| 节目 type | 4 | `mb5:56` |
| 竖屏 / 横屏 aspect ratio | 4 / 0 | `mb5:65` |
| 视频深链 | `axtv://douyin/video/` | `mb5:67` |
| 频道 app 深链 | `axtv://douyin` | `mb5:99` |
| TvProvider authority | `android.media.tv` | `jb5:11` |

## 7. 待迁移（Android 侧实现 `TvProvider`）

1. `sdkInt()` → `Build.VERSION.SDK_INT`；`hasTvProvider()` → `getPackageManager().resolveContentProvider("android.media.tv", 0)`。
2. `queryChannels()` → `ContentResolver.query(Uri.parse(CHANNEL_URI), CHANNEL_PROJECTION, null, null, null)`（原 `g73.m4569k`）。
3. `queryAllPrograms()` / `queryProgram(id)` → `content://android.media.tv/preview_program`，列读回按 `at3.m1471a` 的逐列 `getColumnIndex` + 类型判断（本模块 `PreviewProgram.fromValues` 已兼容）。
4. `insertChannel` / `insertProgram` → `ContentUris.parseId(insert(...))`；捕获 `SecurityException` 返回 -1。
5. `deleteChannel` → `TvContract.buildChannelUri(id)`；`deleteProgram` → `ContentUris.withAppendedId(PREVIEW_PROGRAM_URI, id)`。
6. `addChannelLogo` → 复刻 `g73.m4561a`：三种本地 scheme 用 `openInputStream` + `BitmapFactory.decodeStream`，http(s) 用 `HttpURLConnection`，再写回 `TvContract.buildChannelLogoUri(id)`；失败日志 TAG 见 `ChannelSpec`。
7. `createChannelLogo` → `Bitmap.createBitmap(96,96,ARGB_8888)` + 应用图标 `setBounds(0,0,96,96)` 绘制 + `compress(PNG,100,...)` 写 cacheDir。
8. `requestChannelBrowsable` → `TvContract.requestChannelBrowsable(context, id)`。
9. `TvLog` → `android.util.Log.i/w/e`。
10. 触发侧：把 `StateFlow` 的变化接到 `TvSyncTrigger` 的四个 `onXxx` 回调，返回值交给 `TvChannelPublisher.sync`；原实现在 `ur0`（IO dispatcher）上启动协程，本模块为同步方法，调用方须放到 IO 线程。
11. `WidgetUpdater.pushVideos(WidgetKind.LATER, ...)` 的联动属 `axtv.widget` 模块。

## 8. 存疑

- **S1（重要）**：`lb5.java:259` 的删除判据被 jadx 还原成
  `if (asString2 == null && setM8509u1.contains(asString2))`，该条件恒为假（`null` 不在 wanted id 集合里），
  且 `while (r4.hasNext())` / `p70.m8929A0(); throw null;` 是 jadx 对 `forEachIndexed` 的失败还原
  （文件头有大量 `JADX WARN: Code duplicated`）。本模块按业务意图修正为
  **「`internal_provider_id` 为空或不在 wanted 集合 → 删除」**，否则频道永远不会清理旧节目，
  与 `synced ... wanted=N existing=M` 的语义不符。需真机抓 TvProvider 数据复核。
- **S2**：`at3.m1472b` 里 `DownloadService.KEY_CONTENT_ID` 对应的字符串未在反编译产物中落盘；
  本模块按 media3 `CmcdConfiguration.KEY_CONTENT_ID = "cid"` 推定为 `cid`。
- **S3**：`type=4` 与 `poster_art_aspect_ratio=4` 是原实现硬编码整数，未查到 TvContract 常量名；
  按值原样保留，未擅自替换成语义常量。
- **S4**：`g73.m4561a` 中 http(s) logo 下载的连接超时/读取超时、Bitmap 采样参数未逐字核（该方法 98 行且含大量 R8 合并分支），
  `TvProvider.addChannelLogo` 接口里只写明了三种本地 scheme 与失败日志；Android 侧实现时应对照 `g73.java:200-297` 复核。
- **S5**：`lb5` 的 8 参数构造器（case 1 / `on5.java:570`）与本模块无关，是另一个 Kotlin lambda 被 R8 合并进同一个类；
  它属于视频详情页链路，不在 EPG 覆盖范围。
- **S6**：`eu2.m3788a` 的状态机 case 0/1/2/3 里有一个布尔 `z`（`du2.f4321I`）参与条件，
  反编译不完整，无法确认它对应「开关」还是「首次」。`TvSyncTrigger` 用 `channelEnabled` + `ready` 两个状态位表达了可确定的分支。

## 9. 自测

`javac --release 17 -Xlint:all -encoding UTF-8` 零错误零告警（与其余模块一起全量编译）。
`TvProvider` 可用内存 Map 实现做假驱动，覆盖：SKIPPED / NO_CHANNEL / 新增 / 字段变化更新 / 删除多余 / 空列表清空 /
SDK<26 列裁剪 / 三级封面回退 / weight 下限。
