# 应用外壳模块（`axtv.app`）

> 本模块覆盖 **单 Activity 外壳**：Application 初始化顺序、MainActivity 生命周期、
> 遥控器按键映射、deep link 路由、画中画。
> Android 能力全部收进 `MainActivityModel.Host` / `MyApplication.Platform` 接口，
> 核心决策逻辑为纯 Java 17，可独立编译与假驱动测试。

## 1. 类映射表

| 原文件 | 行数 | 原职责 | 新类 | 行数 |
|---|---:|---|---|---:|
| `src/app/com/github/mytv/p004dv/MyApplication.java` | 130 | Application 初始化顺序 + 崩溃兜底 | `MyApplication` | 已有 |
| `src/app/com/github/mytv/p004dv/MainActivity.java` | 451 | 生命周期、深链、字体缩放、屏幕常亮、License、PIP、按键 | `MainActivityModel` | 244 |
| `src/app/com/github/mytv/p004dv/MainActivity.java:78,102-126` | — | 作品 id 提取（extra / 正则） | `AppRoutes` | 200 |
| `src/app/com/github/mytv/p004dv/widget/WidgetRoutes.java` | 82 | 13 条路由生成 + Intent 构造 | `AppRoutes` | 同上 |
| `src/app/p000/r13.java:20-38` | — | 三张遥控器按键表 | `RemoteKeyMap` | 176 |
| `src/app/p000/r13.java:59-90` (`m9746d`) | — | Compose 焦点处理键位绑定 | `RemoteKeyMap.COMPOSE_KEY_BINDINGS` | 同上 |
| `src/app/com/github/mytv/p004dv/MainActivity.java:347-429` | — | PIP 判定与参数 | `PipSpec` | 143 |
| `src/app/p000/pt2.java` | — | SCREEN_ON/OFF 广播接收器 | `Host.registerScreenOnOffReceiver` | — |
| `src/app/p000/kx2.java` | — | 方向/传感器管理器（`m7322b` / `m7321a`） | `Host.setOrientationManagerEnabled` / `releaseOrientationManager` | — |
| `src/app/p000/t34.java` | — | 远程配置 / License 全局 StateFlow | `Host.setLicenseAccepted` | — |
| `src/app/p000/hu2.java` (`m5244z`) | — | 播放协调器（暂停 / 状态读取） | `Host.pausePlayback` / `isPlaying` / `hasPlaybackState` | — |

## 2. Deep link 路由全表（13 条，逐字来自 `WidgetRoutes`）

| 方法 | 路由 | 参数编码 |
|---|---|---|
| `home()` | `home` | — |
| `search()` | `search` | — |
| `login()` | `login` | — |
| `live()` | `live` | — |
| `history()` | `profile/history` | — |
| `later()` | `profile/later` | — |
| `liveRoom(id)` | `live/room?id=<id>` | `Uri.encode` |
| `mixDetail(id)` | `mix/detail?id=<id>` | `Uri.encode` |
| `playHistory(id)` | `play/history?id=<id>` | `Uri.encode` |
| `playLater(id)` | `play/later?id=<id>` | `Uri.encode` |
| `playMix(mixId, awemeId)` | `mix/play?id=<mixId>&aweme=<awemeId>` | 两次 `Uri.encode` |
| `searchResult(query)` | `search/results?query=<query>` | `Uri.encode` |

承载 extra：`com.github.axtv.dv.widget.ROUTE`；Intent flags `872415232`
（`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP` 等的组合，原值保留）。

**接收侧（MainActivity）**：

1. `aweme_id` extra：trim 后**逐字符判定 `Character.isDigit`**，全数字才接受，随即 `removeExtra`；
2. 否则用正则 `/video/(\d+)` 匹配 `Intent.getData().getPath()`，取第 1 组；
3. `ROUTE` extra 同样取出后 `removeExtra`（`onCreate` 与 `onNewIntent` 都做），避免配置变更重复导航；
4. `AppRoutes.parse(route)` 把路由拆成 `Target(page, args)` 供 NavHost 使用。

## 3. 遥控器按键表

### 3.1 `MainActivity.dispatchKeyEvent` 的媒体键重映射（`r13.f18571e`，7 条）

| 原键码 | 含义 | 映射后 |
|---:|---|---:|
| 88 | MEDIA_PREVIOUS | 19 DPAD_UP |
| 87 | MEDIA_NEXT | 20 DPAD_DOWN |
| 89 | MEDIA_REWIND | 21 DPAD_LEFT |
| 90 | MEDIA_FAST_FORWARD | 22 DPAD_RIGHT |
| 85 | MEDIA_PLAY | 23 DPAD_CENTER |
| 126 | 原写作 `WebSocketProtocol.PAYLOAD_SHORT`（R8 内联整数） | 23 DPAD_CENTER |
| 127 | MEDIA_PAUSE | 23 DPAD_CENTER |

命中时构造新 KeyEvent，**保留** downTime / eventTime / action / repeatCount / metaState / deviceId / flags，
**scanCode 固定 0**；未命中原样交给 `super.dispatchKeyEvent`。

### 3.2 焦点方向表（`r13.f18569c`，重复项去重后 10 条）

| 键码 | View.FOCUS_* | 值 |
|---:|---|---:|
| 21 DPAD_LEFT / 29 KEY_A | FOCUS_LEFT | 17 |
| 22 DPAD_RIGHT / 32 KEY_D | FOCUS_RIGHT | 66 |
| 19 DPAD_UP / 166 SYS_NAV_UP / 51 KEY_STAR | FOCUS_UP | 33 |
| 20 DPAD_DOWN / 167 SYS_NAV_DOWN / 47 KEY_S | FOCUS_DOWN | 130 |

确认键集合（`f18570d`）：`{23, 66, 160}`。

### 3.3 Compose 焦点键位绑定（`r13.m9746d`）

| 键 | 动作槽位 |
|---|---|
| 21 / 22 / 19 / 166 / 20 / 167 | focusLeft / focusRight / focusUp / focusUp / focusDown / focusDown |
| 23 / 66 / 160 | confirm |
| 82 / 176 / 259 / 36 | menu |
| 40 | secondaryConfirm |
| 51 / 47 / 29 / 32 | disabled（禁止默认处理） |
| 7–16 | digit0–digit9（数字键快捷跳转） |

第二张表把 `21,22,19,166,20,167,66,160,23` 置为 null，即**禁用 Compose 默认焦点处理**，改由上面的 lambda 接管。

## 4. PIP 参数表

| 项 | 值 | 来源 |
|---|---|---|
| 默认宽高比 | 16:9 | `MainActivity:403` |
| 自定义宽高比范围 | `[0.47, 2.39]`，乘 1000 取整作分子、分母 1000 | `ut0.m10994q` |
| 忽略自定义比例的条件 | 播放状态 == `ab4.f312M`（非视频态）或比例 &lt;= 0 | `:400-403` |
| 最低 SDK | 26 | `:386` |
| 自动进入 PIP | SDK >= 31 `setAutoEnterEnabled(true)` | `:415-417` |
| action 1 | `com.github.axtv.dv.PIP_PAUSE`，reqCode 0，`R.drawable.pip_pause` | `:405-407` |
| action 2 | `com.github.axtv.dv.PIP_PLAY`，reqCode 1，`R.drawable.pip_play` | `:408-410` |
| action 3 | `com.github.axtv.dv.PIP_NEXT`，reqCode 2，`R.drawable.pip_next` | `:411-413` |
| Intent | 均 `setPackage(getPackageName())`，flag 为 media3 `C.BUFFER_FLAG_NOT_DEPENDED_ON` | 同上 |

**`onUserLeaveHint` 判定顺序**：coordinator 为空 → 什么都不做；屏幕未交互 → 暂停；
未播放或无播放状态 → 暂停；SDK<26 → 什么都不做；否则进 PIP，`enterPictureInPictureMode` 抛异常则回落到暂停。

## 5. 生命周期时序

```
attachBaseContext
  └─ douyin_auth/app_font_scale（默认 1.0，clamp 到 [1.0,1.6]）
     └─ != 1.0 → Configuration.fontScale *= scale → createConfigurationContext

onCreate(savedInstanceState, extras, dataPath)
  1. discardSavedState = (savedInstanceState != null && !coldLaunchSeen)
     coldLaunchSeen = true（进程级静态标记）
  2. 若 discardSavedState → 传 null 给 super.onCreate（丢弃恢复状态）
  3. setSoftInputMode(48)
  4. parseAwemeId(extras, dataPath) → StateFlow(f3271k0)
  5. takeRoute(extras)             → StateFlow(f3272l0)
  6. addFlags(128)                 // FLAG_KEEP_SCREEN_ON
  7. 注册 ACTION_SCREEN_OFF / ACTION_SCREEN_ON 广播
  8. remote_config：license_agreed / license_agreed_version(默认 agreed?1:0)
                  / license_required_version(默认 1)
     → accepted = agreed && agreedVersion >= requiredVersion → 全局 StateFlow
  9. new kx2(方向管理器)，启动两个启动协程（mt2 / ft2）
 10. m2368v(fullscreen)：false → hide(1)，true → show()  【按 SDK 分 4 个实现类：>=35/>=30/>=26/其它】
 11. try { setContent } catch { Log.e("MainActivity","Fatal error in setContent",e) }

onStart   → 方向管理器 enable(true)
onStop    → 方向管理器 enable(false)
onResume  → 若「待恢复播放」且当前未播放 → 清标记并置 f8486N=false（不自动续播）
onDestroy → 方向管理器 release + 注销广播
onNewIntent → setIntent + 重新解析 awemeId / ROUTE
onPictureInPictureModeChanged(z) → coordinator.pipMode = z
onUserLeaveHint → 见 §4
dispatchKeyEvent → 见 §3.1
```

## 6. 待迁移（Android 侧 `Host` 实现）

1. `prefs(name)` → `getSharedPreferences(name, 0)`；`applyFontScale` → `createConfigurationContext`。
2. `setSoftInputMode(48)` / `addFlags(128)` → `getWindow()`。
3. 广播：`IntentFilter(ACTION_SCREEN_OFF / ACTION_SCREEN_ON)` + `pt2` 接收器（API 版本用 `ContextCompat.registerReceiver` 的等价标志 4）。
4. `setLicenseAccepted` → `t34.f20452e` 的 `StateFlow`；`t34.f20456i` 保存 prefs 引用。
5. 方向管理器 `kx2`：按 `f3269i0`（全屏标记）切换 `m7322b(true/false)`，`m7321a()` 释放。
6. 启动协程 `mt2` / `ft2`：原在 `rt0.m9996B(this)`（lifecycleScope）上以 `Dispatchers` 启动，本模块为同步方法，调用方放 IO 线程。
7. `applySystemBars` → 按 SDK 选 `cw5`(>=35) / `bw5`(>=30) / `aw5`(>=26) / `zv5`，复刻 `m2368v`。
8. `setContent` → Compose `setContent { ... }`（NavHost 属 UI 层，尚未重构）。
9. PIP：`PictureInPictureParams.Builder` + `RemoteAction` + `PendingIntent.getBroadcast`；`Icon.createWithResource`。
10. `dispatchKeyEvent` → 用 `RemoteKeyMap.remapForDispatch` 的结果构造 `android.view.KeyEvent`。
11. `AppRoutes.encode` 对应 `android.net.Uri.encode`；调用方需确认保留字符集一致（见存疑 S2）。

## 7. 存疑

- **S1**：`MainActivity:159` 的 `Object[] objArr = (bundle == null || f3268p0) ? false : true` 是 jadx
  类型推断失败（`JADX WARN: Multi-variable type inference failed`）。按布尔语义还原为
  `bundle != null && !f3268p0`，即「首次启动却带 savedInstanceState 时丢弃恢复状态」。
- **S2**：`Uri.encode` 的保留字符集本模块按 RFC 3986 unreserved + Android 常见的 `!~*'()` 实现；
  未找到反编译产物里的 `Uri.encode` 实现（属 framework），建议用中文/空格串真机比对。
- **S3**：`r13.f18571e` 第 6 项原写作 `WebSocketProtocol.PAYLOAD_SHORT`（OkHttp 常量 126），
  明显是 R8 把整数常量错误标注成了同值字段；按键码 126 的 Android 语义未能确定，按原值保留。
- **S4**：`166/167` 推定为 `KEYCODE_SYSTEM_NAVIGATION_UP/DOWN`；`176/259/36`（menu 槽位）
  对应具体遥控器按键未能确认，按原值保留在绑定表里。
- **S5**：`C0029C.BUFFER_FLAG_NOT_DEPENDED_ON` 是 media3 常量，被 R8 内联为 PendingIntent flags；
  实际数值未在产物中落盘，`PipSpec` 只记录了 action 名与 requestCode。
- **S6**：`SOFT_INPUT_MODE=48`、`WINDOW_FLAGS=128` 按原值保留；48 的组合语义
  （`SOFT_INPUT_STATE_*` + `SOFT_INPUT_ADJUST_*`）未反查 framework 常量表。
- **S7**：MainActivity 的 `f3270j0`（hu2 播放协调器）由 Hilt 注入，本模块用 `coordinatorPresent` 表达；
  依赖注入（COVERAGE 第 19 行）整体改为构造器注入，未复刻 Hilt 生成代码。

## 8. 验证

与 `refactor/` 其余模块一起 `javac --release 17 -Xlint:all -encoding UTF-8` 全量编译通过。
`MainActivityModel.Host` 可用内存假实现覆盖：冷启动丢弃状态、字体缩放、两条 deep link 入口、
License 三键组合、PIP 四种判定分支、媒体键 7 条重映射。
