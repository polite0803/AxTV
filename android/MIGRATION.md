# AxTV UI 栈迁移 · 第一步（基线 + 三样板）

> **历史记录**：本文是迁移第一步的存档，其中的 compileSdk / minSdk / 版本号已被后续调整覆盖。
> 当前基线以 `android/app/build.gradle.kts` 为准（compileSdk 36 / minSdk 26 / versionName 0.1.4）。

## 1. 结论

- **原版栈**：手机版 Jetpack Compose（`ui/foundation/runtime 1.12.1`、`material3 1.5.0-alpha25`）＋ **自绘 TV 焦点**；APK 内**没有** `androidx.tv` / `androidx.leanback`。结论：**不是 tv-material**，因此迁移是「翻译」而非「还原」——列表焦点定位、焦点盒、按键语义都要在 TV 组件上重新对齐。
- **目标基线**：Kotlin 2.1.0 + Compose BOM 2024.12.01 + `tv-material 1.0.0` + `tv-foundation 1.0.0-alpha11` + compileSdk 35（沿用现有 catalog，未重排工程）。
- **三样板**：首页信息流 / 设置页 / 播放器浮层已落地；`assembleDebug` **构建通过**，产出 `app/build/outputs/apk/debug/app-debug.apk`（16.71 MB）。核心层 `CrashHandler` 的 `Files.writeString` 已按平台兼容改法修复（1 处，见 §5）。

## 2. 原版栈证据

| 结论 | 证据 | 可信度 |
|---|---|---|
| Compose 入口 | `src/app/com/github/mytv/p004dv/MainActivity.java:74`（Activity）、`:256` 调 `jc0.m6600a`；`src/app/p000/jc0.java:27-40` 创建 Compose 根 View 并 `setContentView` | 确认 |
| Compose 版本 | `src/resources/META-INF/androidx.compose.ui_ui.version` = 1.12.1；`...foundation_foundation.version` = 1.12.1；`...material3_material3.version` = 1.5.0-alpha25；activity-compose 1.13.0 | 确认 |
| 无 TV 组件库 | `src/sources/androidx` 无 `tv/`、`leanback/`；`.workbuddy/apktool-smali/smali*/androidx/` 下同样不存在 | 确认 |
| 焦点模型 | `refactor/ui/NOTES.md:415`：无集中 DPAD 表，Activity 仅转发，走 Compose 焦点系统；APK 内仅见 Compose 库侧 FocusOwner/FocusRequester 实现，App 层用 `focusable`/焦点变化自绘 | 确认（转发结论）/ 推测（具体自绘样式） |
| 播放器画面层 | `src/app/p000/bp0.java:116-121`：`SurfaceView` + `androidx.media3.ui.PlayerView`（`bp0.java:3,5` 导入） | 确认 |
| 原版是 Kotlin | `MainActivity.java:75` Kotlin `Companion`；`src/resources/META-INF/kotlinx_coroutines_android.version`；`src/sources/kotlinx/coroutines` | 确认 |

**对「还原 vs 翻译」的含义**：原版所有焦点视觉、焦点盒位置、滚动让位（pivot）都是手写 Compose；坐标/动画无法从 APK 逐像素还原。目标栈用 tv-material/tv-foundation 提供焦点盒与 BringIntoView 行为，只能保证**结构、焦点顺序、按键语义**一致，视觉需按 `PlayerUiPalette` 的角色表重绘。

## 3. 目标基线（`android/`）

- `gradle/libs.versions.toml`：agp 8.7.3、kotlin 2.1.0、composeBom 2024.12.01、tv-material 1.0.0、tv-foundation 1.0.0-alpha11（未改工程结构）。
- `app/build.gradle.kts:69` 新增 `implementation(libs.compose.tv)`；`:24` compileSdk 35；`:28-31` minSdk 23 / versionCode 43 / versionName 2.3.43。
- 包名 `com.github.axtv.dv`、`Theme.AxTV`、`refactor/`、`src/` 均未改动。
- 说明：`tv-foundation 1.0.0`（稳定版）POM 要求 Compose 1.10.3，会把基线从 1.7.6 强行拉高；故沿用 catalog 的 alpha11（基于 1.6.x，BOM 提升到 1.7.6 兼容）。（确认，来源：Google Maven POM 实测）

## 4. 三个样板页

| 页面 | 文件 | 规格出处 | 原版来源 / 说明 |
|---|---|---|---|
| 首页信息流 | `app/src/main/java/com/github/axtv/dv/ui/home/HomeFeedScreen.kt` | `refactor/ui/NOTES.md` §信息流、`FeedPageSpec.java`、`HomeTabsSpec.java` | `i42` 容器 / `on5` 视频页；LazyRow + LazyColumn，焦点索引提升到 `AxTvApp` 跨页记忆并恢复 |
| 设置页 | `.../ui/settings/SettingsScreen.kt` | `refactor/ui/NOTES.md` §设置页（`qm4.java:7.3k`，62 项）、`SettingsPageSpec.java` | 取 19 个代表项，覆盖 TITLE/SECTION_HEADER/SWITCH/ENUM/ACTION/VERSION/NOTE/CARD；↑↓ 行焦点链、←→ 枚举、OK 切换 |
| 播放器浮层 | `.../ui/player/PlayerOverlayScreen.kt` | `refactor/ui/NOTES-player-ui.md`、`PlayerMenuSpec.java`、`PlayerUiPalette.java` | `lq2` 菜单装配；画面 `AndroidView(SurfaceView)` 占位（原版 `bp0.java:116-121` PlayerView），浮层 Compose；画质/倍速/弹幕/屏蔽词/简介/返回 |

公共层：`ui/AxTvTheme.kt`（tv-material `MaterialTheme` + 自绘焦点框）、`ui/AxTvApp.kt`（三页导航壳，`MainActivity` 只改为 `setContent { AxTvApp() }`）。

## 5. 构建结果

- 命令：`.\gradlew.bat :app:assembleDebug --console=plain`（`D:\OneManager\AxTV\android`），最终耗时 **53.8s**（BUILD SUCCESSFUL）。
- 环境：JAVA_HOME=JDK 17；SDK `C:\Users\polit\AppData\Local\Android\Sdk`（platform 35 存在）；Gradle wrapper 8.14；依赖从 Google Maven 正常拉取（tv-foundation alpha11 首次下载）。
- APK：`android/app/build/outputs/apk/debug/app-debug.apk`，**16.71 MB**（17,518,770 字节）。
- 修复：`refactor/settings/axtv/settings/CrashHandler.java:94` 的 `Files.writeString`（Java 11 API，Android 35 android.jar 无）改为 `FileOutputStream + report.getBytes(UTF_8)`，截断/编码语义一致；仅改此 1 处，未动其它核心文件。
- 同类隐患已在后续提交中处理：`UpdateManager` / `CrashHandler` 改用 `java.io`，`LiveFrameCodec` 手写累积替代 `readAllBytes`；minSdk 已抬到 26。


## 6. 后续推进顺序（按 99 条覆盖矩阵）

1. **播放器页**（控件最多、与原版差异最大）：`PlayerKeyMachine` 按键状态机 → 控制层显隐/焦点 → 菜单/手势/进度条 → Danmaku 浮层。
2. **首页/详情**：Tab 容器与分页 → 信息流卡片 → 合集/评论面板。
3. **设置页全量**：把 62 项按 `SettingsPageSpec` 批量展开到同一渲染器，再接 `SettingsStore`。
4. **直播页**：`LivePageSpec` + 焦点让位与弹幕样式。
5. **性能与低端盒**：Compose 1.11.4 + `androidx.compose.foundation.lazy` 在 1GB 盒子上先测首帧/滑动掉帧，再决定是否换 `BringIntoViewSpec` 或降级 TextureView。
6. **TV 集成**：`TvProvider`/Widget 与 Compose 页面并行，不阻塞 UI 主线。

## 7. 已知风险

- **低端盒性能**：电视盒子 GPU/JIT 弱，LazyColumn 的焦点自动滚动 + 自绘边框可能掉帧；基线阶段未接图片加载（Coil）与弹幕，真实负载后需复测。（推测）
- **焦点**：原版手写焦点盒/让位 vs tv-material 默认行为，边距与滚动位置会有可见差异；tv-foundation 1.0.0 已删除 `TvLazy*`，本轮全部迁到 `androidx.compose.foundation.lazy`（LEANBACK 设备默认 `PivotBringIntoViewSpec` 的 30% pivot 与 TvLazy `PivotOffsets` 默认值一致）。
- **Surface 合成**：`AndroidView(SurfaceView)` 与 Compose 浮层的 z-order 在部分盒子/TextureView 模式下会闪黑或浮层被盖；原版已提供 `PlayerSurfaceMode` 兜底，接入 Media3 时必须按该模式验证。（推测）
- **版本代差**：原版 Compose 1.12.1 / material3 1.5.0-alpha25 vs 基线 1.7.6 / 1.3.1，API 行为（尤其焦点/滚动）不能假设一致。（确认）
