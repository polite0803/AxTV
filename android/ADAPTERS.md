# Android 适配层总表（android/ 壳工程工作清单）

> 核心层（`refactor/`，纯 JDK）已 100% 编译通过、125 项行为测试全绿。
> 本表把 14 份 NOTES 里的「待迁移」逐条收拢：**核心接口 → Android 实现 → 依赖 → 行为规格出处**。
> 每一条的行为规格都已在 NOTES 里写死（常量/分支/文案/时序），实现时**照规格抄**，不要重新设计。

## 1. 依赖桥（sign 模块 —— a_bogus 签名）

| 核心接口 | Android 实现 | 依赖 | 状态 / 验证 | 规格出处 |
|---|---|---|---|---|
| `JsRuntime.Factory` | `adapter/QuickJsRuntimeFactoryAdapter.kt`：`QuickJSContext.create()` + `eval`/`close` | 内置 `jniLibs/<abi>/libquickjs-android-wrapper.so` + `com.whl.quickjs` 绑定类（零 Maven 依赖） | 🟡 编译通过；引擎初始化与真实签名待设备验证 | `sign/NOTES.md §6`（含 6 行示例代码） |
| `Assets` | `sign/AndroidAssets.kt`：`context.assets.open(...)` 读 UTF-8 | 无 | 🟡 编译通过；读 assets 待设备 | `sign/NOTES.md §6-1` |
| `SignLog` | `sign/AndroidSignLog.kt`，桥 `android.util.Log`，TAG 沿用 `AbogusEngine`/`SignJsEngine` | 无 | 🟡 编译通过 | `sign/NOTES.md §6-3` |
| `SignProvider` | `adapter/AndroidSignProvider.kt`：委托 `AbogusSigner`；ticket-guard 未实现时明确抛 `UnsupportedOperationException` | 无 | 🟡 实参顺序 JUnit 通过（真跑）；a_bogus 非空待设备 | `api/NOTES.md`；`sign/NOTES.md §3` |
| （线程约定） | `AbogusEngine`/`SignJsEngine` 内单线程 executor（`quickjs-abogus` / `quickjs-sign`） | 无 | ✅ 代码核对 | `sign/NOTES.md §6-4` |
| assets 资源 | `sign.js` / `abogus_dy.js` / `browser-shim.js` 已拷入 `app/src/main/assets/` | 无 | ✅ APK 条目已确认 | `sign/NOTES.md §2/§4` |

## 2. 网络 / 存储（api 模块）

| 核心接口 | Android 实现 | 依赖 | 状态 / 验证 | 规格出处 |
|---|---|---|---|---|
| `HttpEngine` | `api/OkHttpEngine.kt`：连接/读/写各 30s，双 client（跟随/不跟随重定向） | okhttp | 🟡 编译通过；真实请求待设备 | `api/NOTES.md`（HttpEngine 章节常量表） |
| `CookieStore` | `api/SharedPrefsCookieStore.kt`：SharedPreferences(`douyin_auth`) + `SetCookie.parse` 回写 | 无 | 🟡 编译通过；`ticketGuard()` 仍 `empty()` | `api/NOTES.md` |
| `DeviceProfile` | `adapter/AndroidDeviceProfile.kt`（api 用 `Build.*`；settings 用 TV/leanback 判定） | 无 | 🟡 编译通过；TV 判定待设备 | `api/NOTES.md` DeviceProfile 章节 |
| `SignProvider` 注入 | `adapter/AndroidSignProvider.kt` + `adapter/ApiAssembler.kt`（注入核心层 `ModelJsonCodec`） | 无 | 🟡 URL/参数/a_bogus 拼装 JUnit 通过（真跑）；解码 JVM 用例见 `refactor/tests` | `api/NOTES.md` |
| `MsTokenBootstrap` 存储 | 复用 `CookieStore` 的 SP 键（msToken / ttwid 等） | 无 | 🟡 编译通过 | `api/NOTES.md` |

## 3. 播放器（player + player UI）

| 核心接口 | Android 实现 | 依赖 | 规格出处 |
|---|---|---|---|
| `MediaEngine` | `Media3Engine.kt`：ExoPlayer 装配（HLS/进度/速度/PIP），错误映射到 `PlaybackError` | media3（hls/okhttp-ds/session） | `player/NOTES.md` ro5 分区表 |
| `SurfaceHost` | Compose `AndroidView(SurfaceView/TextureView)` + `GlVideoSurfaceView`（GL SDR 修正） | media3-ui | `player/NOTES.md`；`ui/NOTES-player-ui.md` |
| 缓存目录/大小 | `VideoCache.kt`：`SimpleCache`（目录 `filesDir` 子目录、上限按 NOTES 常量） | media3-datasource | `player/NOTES.md` 缓存常量表 |
| `PlaybackQueue` 装配 | 视频页根组合（原 `on5.H`，已定性为纯装配枢纽，见 NOTES-on5 §6-1） | — | `ui/NOTES-on5.md` |
| 控制层/菜单绘制 | 按 `PlayerMenuSpec`/`PlayerGestureSpec`/`PlayerUiPalette` 逐条绘制（色板 41 令牌、文案逐字） | compose-tv-material | `ui/NOTES-player-ui.md` |
| PIP | `PipParamsBuilder.kt`：`PipSpec`（1780/1000 比例、3 action → `PipActionReceiver`） | 无 | `app/NOTES.md` PIP 表 |

## 4. 直播（live 模块）

| 核心接口 | Android 实现 | 依赖 | 规格出处 |
|---|---|---|---|
| `WebSocketTransport` | `OkHttpWebSocket.kt`：OkHttp WebSocket，重连退避按 `LiveWebSocketClient` 常量 | okhttp | `live/NOTES.md` 帧/心跳/重连表 |
| `LiveLog` / `LiveLogFormat` | 桥 `android.util.Log`（TAG `LiveWS`/`LiveWebSocket`） | 无 | `live/NOTES.md` |
| `LiveHttpClient` | OkHttp 包装（room/enter 等拉流接口） | okhttp | `live/NOTES.md` |
| 弹幕绘制 | 按 `DanmakuStyle` 几何 + `live/NOTES.md` 渲染层说明绘制 | compose | `live/NOTES.md` |

## 5. 手机面板（panel 模块）

| 核心接口 | Android 实现 | 依赖 | 规格出处 |
|---|---|---|---|
| `AssetSource` | `AndroidAssets.kt` 复用（panel-head.html/search.html/submit.html 模板） | 无 | `panel/NOTES.md` Android 清单 |
| `ThemeColors` | `PanelTheme.kt`：动态取色（`Palette`，31 色表在 NOTES） | palette | `panel/NOTES.md` |
| `AuthStore` | `SharedPrefsAuthStore.kt`（`douyin_auth` SP + StateFlow） | 无 | `panel/NOTES.md` 安全/落盘章节 |
| `WebViewHost` | `QrLoginWebView.kt`：UA 伪装 + 坐标点击 + XHR/fetch 钩子脚本（NOTES 已附原脚本） | webview | `panel/NOTES.md` QrLoginBridge 章节 |
| 二维码 Bitmap 解码 | `QrDecoder.kt`：`ImageDecoder`/ML Kit 任选，接口 `QrLoginBridge.Decoder` | 任选 | `panel/NOTES.md` |
| `ConnectivityManager` 监听 | `PanelNetwork.kt`：Wi-Fi/以太网地址变化 → `DeviceAddressResolver` 重算 | 无 | `panel/NOTES.md` |
| 端口 9978/9979/9980 | `PanelServer` 直接跑（纯 JDK `com.sun.net.httpserver` 可用） | 无 | `panel/NOTES.md` 路由表 |

## 6. 设置 / 更新 / 崩溃（settings 模块）

| 核心接口 | Android 实现 | 依赖 | 状态 / 验证 | 规格出处 |
|---|---|---|---|---|
| `SettingsStore` | `settings/SharedPrefsSettingsStore.kt`（含类型不匹配回落默认值） | 无 | 🟡 71 键/默认值 JUnit 通过（真跑）；SP 落盘待设备 | `settings/NOTES.md` 配置键全表 |
| `SettingsJson` | 核心层 `SimpleSettingsJson`（纯 JDK，无需 Android 适配） | 无 | ✅ 编译通过 | `settings/NOTES.md` |
| 设置页持久化 | `adapter/SettingsBinder.kt` + `adapter/AppRuntime.kt`（`MyApplication.onCreate` 装配） | 无 | 🟡 映射 JUnit 通过；UI 重启回读待设备 | 本轮模拟器实测缺陷 |
| `AppInstaller` | 未实现 | — | ⬜ 本轮范围外 | `settings/NOTES.md` 更新时序 |
| `AppInfo.signerSha256` | 未实现 | — | ⬜ 本轮范围外 | `settings/UpdateManager.java` |
| `CrashHandler` 落盘/导出 | 未装配（模拟器未见 `crash-*.txt`） | — | ⬜ 非本轮三张表，仅归因 | `settings/NOTES.md`；`app/NOTES.md` |

## 7. EPG（tv 模块）

| 核心接口 | Android 实现 | 依赖 | 规格出处 |
|---|---|---|---|
| `TvProvider` | `TvProviderAndroid.kt`：`TvContractCompat.Channels/PreviewPrograms` ContentResolver | `androidx.tvprovider` | `tv/NOTES.md` 字段全表（SDK 列裁剪在 `TvProviderColumns`） |

## 8. 桌面小组件（widget 模块）

| 核心接口 | Android 实现 | 依赖 | 规格出处 |
|---|---|---|---|
| 6 个 Glance 组件 | 按 `WidgetRenderSpec`（933 行规格）逐组件绘制：尺寸/圆角/分支/点击动作 | glance-appwidget + material3 | `widget/NOTES.md §3.1-3.6` |
| `WidgetStorage` | `DataStoreWidgetStorage.kt` 或 SharedPreferences（键全表在 NOTES §7） | 无/work | `widget/NOTES.md §7` |
| `WidgetEnvironment.ImageLoader` | `CoilImageLoader.kt`：按 `WidgetImageSpec`（缓存键七段、像素下限 24、LruCache 48）裁剪 | coil | `widget/NOTES.md`；`WidgetImageSpec.java` |
| 调度 | `WidgetRefreshScheduler` → WorkManager PeriodicWork（约束/间隔按 NOTES §6 常量） | work-runtime | `widget/NOTES.md §6` |
| `Uri.encode` | 直接 `android.net.Uri.encode`（核心层 `WidgetRoutes` 的唯一外部依赖） | 无 | `widget/NOTES.md §8` |

## 9. UI 绘制层（ui 模块 —— 行为规格已 100% 就绪）

| 页面 | 规格来源 | 备注 |
|---|---|---|
| 视频详情页 | `ui/NOTES-on5.md` + `FeedPageSpec` + `InteractionBarSpec` + `DateLabels` | 原 `on5.H` 装配根已结案 |
| 播放菜单/手势 | `PlayerMenuSpec` + `PlayerGestureSpec` + `PlayerUiPalette` | 文案/色板/尺寸逐条可抄 |
| 设置页（77 控件） | `SettingsPageSpec`（控件表 + 71 键对照） | 类型 26 种控件渲染器 |
| 选集面板 | `EpisodePickerSpec` | 四档前景色/文案格式 |
| 精选/分区列表 | `ChannelListSpec` + `NOTES-qg5.md` | 分区五态机 |
| 评论面板 | `CommentUiSpec` | 相对时间/key 去重/回复展开 |
| 搜索页 | `SearchUiSpec` | 错误三码 + 空态文案 |
| 直播页 | `LivePageSpec` + `live/NOTES.md` | 分辨率/错误两值/连播 |
| 首页 Tab 容器 | `i42`(8.1k) 尚未单独立规格 —— 上滑加载/双击返回行为部分在 `FeedPageSpec` | **唯一还值得补一轮规格的页面** |

## 10. 资源拷贝清单（src/resources → app/src/main）

| 源 | 目标 | 说明 |
|---|---|---|
| `res/`（values/、mipmap/、drawable/、xml/widget_*_info、xml/file_paths） | `app/src/main/res/` | 原样拷贝；`Theme.AxTV`/`tv_banner` 均在 |
| `assets/*.js`（sign 系） | `app/src/main/assets/` | `sign/NOTES.md §2` 文件清单 |
| `assets/web/*.html`（panel 模板） | `app/src/main/assets/web/` | `panel/NOTES.md` |

## 11. 接入方式

当前推荐 **B**：把 `refactor/*/axtv/**` 与 `refactor/ui/axtv/ui/**` 复制到
`app/src/main/java/axtv/**`（纯 JDK 类零改动直接编入）。等壳工程跑通后，若想模块化，
再迁到 `includeBuild`（方案 A）。

## 12. 验证状态

- 核心层：`javac 17` 零错误 + 125 项行为测试（`refactor/TESTS.md`）。
- 壳工程：`:app:assembleDebug` 通过（AGP 8.7.3 / compileSdk 35 / minSdk 23），APK 22.8 MB。
- 本轮 JUnit（JVM，真跑 8 项全绿）：`ApiUrlAssemblyTest`(2)、`SignProviderForwardingTest`(2)、`SettingsKeyMappingTest`(4)。
- 待设备/模拟器验证：QuickJS 初始化与真实 a_bogus、SharedPreferences 落盘与重启回读、TV 判定、Looper 主线程判定。
