# axtv.player —— 视频播放器模块

反编译源：`src/app/p000/ro5.java` 及其辅助类。日志 TAG 统一为 `VideoPlayer`（少数 GL 日志为 `GlVideoSurfaceView`）。

---

## 1. ro5.java 的职责分区

### 1.1 文件形态

`ro5.java` 共 **36083 行**，是 R8 扁平化后最大的单个类。它**不是**一个"播放器类"，而是
**一整屏 Compose 播放页**（Kotlin 文件级函数，编译成 `abstract class ro5` 的静态方法）：

| 行段 | 内容 |
|---|---|
| 1–35 | import + 唯一字段 `f19158a = Build.MANUFACTURER == "TPV"` |
| 37–120 | JADX 生成的 "Code duplicated" 警告注释（360 余条，说明编译器内联展开） |
| **264–35847** | **`m9950a(...)`：唯一的 `@Composable` 主体，31 个参数，含 63 段近乎完全重复的播放器初始化代码** |
| 35848–35887 | `m9951b` 宽高比 / `m9952c` SurfaceHolder 固定尺寸 |
| 35890–35897 | `m9953d` 首帧解锁音频 |
| 35900–35913 | `m9954e` 首帧一次性回调 |
| 35916–35929 | `m9955f` 构建 ExoPlayer（自定义 RenderersFactory + 缓存 MediaSourceFactory） |
| 35931–36035 | `m9956g` 转场期间"只下载不解码"地准备下一条（协程） |
| 36037–36082 | `m9957h` 取用/复用全局共享播放器 |

统计事实：

- `Log.d("VideoPlayer", …)` **133 行**，只有 6 个模板：
  `创建播放器实例`×63、`等待首帧，暂时静音: url=`×63、`动画期间准备视频(只下载不解码):`×3、
  `创建全局共享播放器`×2、`复用全局共享播放器, 当前引用数:`×1、`首帧音频解锁(`×1。
- 没有 `switch/case`（Kotlin `when` 已被内联成 63 段顺序分支），因此**同一套播放逻辑被复制了 63 份**，
  每份约 500 行，仅闭包捕获的状态槽不同。

> 结论：ro5 的"63 段重复"是反编译噪音，不是 63 种不同行为。**抽取去重后，播放相关逻辑不足 400 行**
> （已全部落到本模块）；剩余 3.5 万行是 UI 布局（进度条、弹幕层、按钮、转场动画），属于 Compose 层。

### 1.2 单段分支内的职责分区（以 740–1440 行为样本）

| 行号 | 职责 | 新代码归属 |
|---|---|---|
| 767–779 | `remember { fp3() }` / `remember { i16() }`：PlayerView 持有者 + "脏"标记 | `SurfaceHost`（Android 侧） |
| 817–838 | 无外部 player 时 `m9955f` 新建；有则直接复用 | `SharedPlayerPool` |
| 839–967 | 18 个 `remember` 状态槽（当前 url、attempt、hasRetried、muteUntilFirstFrame、aspect、fixedSize…） | `PlayerEngine` 字段 |
| 987–1051 | `DisposableEffect`：创建 `ko5` 监听器，按"是否已解锁首帧"决定 `setVolume(0/1)` | `PlayerEngine.open` + `onRenderedFirstFrame` |
| 1052–1084 | `LaunchedEffect`（`um5`）：条目占位状态复位（`VideoItem` 日志，属信息流层） | 不迁移（见 §6） |
| 1127–1148 | `LaunchedEffect(url, attempt)`（`mo5`）：去重派发 / 预缓冲复用 / 换源 | `PlayerEngine.open` |
| 1149–1158 | `LaunchedEffect`（`qk4`→`no5`）：isPlaying 三态驱动播放/暂停 | `PlayerEngine.drivePlay` |
| 1159–1185 | `SideEffect`：`setPlaybackSpeed(max(f, 0.1))` | `PlayerEngine.setSpeed` |
| 1186–1194 | `DisposableEffect`：共享播放器引用计数 +1 / 退出暂停 | `SharedPlayerPool.addRef/releaseRef` |
| 1195–1221 | `DisposableEffect`（`jo5`）：解绑 surface 但保留最后一帧 | `PlayerEngine.detachSurfaceKeepLastFrame` |
| 1243–1325 | 按 `dp3` 三种模式 inflate `player_view_surface/texture/gl`，GL 模式挂 GLSurfaceView | `PlayerSurfaceMode` + `GlVideoSurfaceView` |
| 1326–1337 | `ug5`：`setPlayer` / `setKeepContentOnPlayerReset` | `SurfaceHost`（Android 侧） |
| 1338–1437 | `ho5`：宽高比、固定缓冲区尺寸、缩放矩阵下发 | `VideoScaleMatrix` + `SurfaceHost` |
| 1437 | `mw2.m7925a`：把 view 挂进 Compose 树 | 不迁移（UI） |

---

## 2. 类映射表

| 原类（src/app/p000/…） | 行数 | 新类（axtv.player） | 说明 |
|---|---|---|---|
| `ro5.java` | 36083 | `PlayerEngine` `VideoScaleMatrix` `PlayerSurfaceMode` `SharedPlayerPool` `PlayerConfig` | 播放逻辑抽净；UI 不迁移 |
| `ko5.java` | 380 | `PlayerEngine`（Listener 实现）+ `PlayerEventListener` | 36 个 `Player.Listener` 方法里只有 5 个非空 |
| `mo5.java` | 204 | `PlayerEngine.open` | 去重派发、预缓冲复用、缓存分支 |
| `no5.java` | 67 | `PlayerEngine.drivePlay` | isPlaying/surfaceBound/shared 三态 |
| `oo5.java` | 56 | `SharedPlayerPool.releaseIndependent` / `releaseRef` | 引用计数释放 |
| `je2.java` | 58 | `SharedPlayerPool.releaseAll` | 只取 default 分支（"回收共享播放器"） |
| `jo5.java` | 147 | `PlayerEngine.detachSurfaceKeepLastFrame` | 解绑保留最后一帧 |
| `lp1.java` | 103 | `GlVideoSurfaceView`（契约） | GL surface 绑定/解绑/缓冲区尺寸 |
| `xn5.java` | 229 | `GlVideoSurfaceView.Spec` + 文档 | GL 管线参数与着色器 |
| `mp1.java` | 83 | `GlVideoSurfaceView`（契约） | GLSurfaceView 宿主 |
| `dp3.java` | 51 | `PlayerSurfaceMode` | 三种渲染模式 |
| `pq5.java` | 96 | `VideoScaleMatrix` | pinch 缩放 + 偏移钳制 |
| `fk3.java` | 93 | `VideoScaleMatrix.Transform` | Compose `MutableFloatState` 的包装 |
| `sn5.java` | 110 | `VideoMediaCache` | 96 MiB LRU + 2 MiB 预载 |
| `t92.java` | 14 | `PlayerEngine`（去重逻辑） | `WeakHashMap<player, (attempt,url)>` |
| `wi4.java`（仅 f23543–f23547） | — | `SharedPlayerPool` | 该类的其余内容是 Compose 焦点搜索，与本模块无关 |
| `MainActivity.java`（PIP 段） | 370–425 | `PipController` | 参数与动作 |
| `PipActionReceiver.java` | 48 | `PipController.commandOf` | 动作分发 |
| `AbstractC1317kr.java` | 39 | `PipController`（文档） | API 26 兼容壳 |

新增但原代码没有显式类型的抽象：`MediaEngine`（ExoPlayer）、`SurfaceHost`（三种视图）、
`PlaybackQueue`（信息流游标）、`ResumeStore`（播放进度）、`CodecFallback`（H.264 降档）、
`PlayerEventListener`（事件面）、`PlaybackError`、`VideoSize`、`PlayerState`。

---

## 3. 缩放矩阵公式（精确还原）

### 3.1 显示比例

```
aspectRatio = (videoWidth * pixelWidthHeightRatio) / videoHeight        ro5.java:35853
```

- 前置条件 `w > 0 && h > 0`，否则直接返回（`ro5.java:35850`）；
- 与上次值相等则跳过 `setAspectRatio`（`ro5.java:35859`，用 `!=` 比较 float，不是 epsilon）；
- `PlayerView.resizeMode` 恒为 `0`（`RESIZE_MODE_FIT`，`ro5.java:1284`）。

**横竖屏处理**：应用不支持播放页旋转（全工程仅 `C1285k.java:532` 在设置变更时调用
`setRequestedOrientation`）。竖屏素材在横屏容器里的表现完全由上面的 aspect + FIT 决定：
左右留黑边、居中。没有额外的旋转/裁切逻辑，`VideoScaleMatrix` 也不需要处理旋转。

### 3.2 偏移钳制（`pq5.m9170b`）

```
若 scale <= 1 或尺寸非法: offset = (0, 0)
maxX = max(0, (contentW * scale - viewportW) / 2)
maxY = max(0, (contentH * scale - viewportH) / 2)
offset = (clamp(offset.x, -maxX, maxX), clamp(offset.y, -maxY, maxY))
```

确保任何缩放级别下画面都不脱离视口。

### 3.3 缩放一步（`pq5.m9172a`）

```
入参: offset, scale, factor, centroid, viewport, content, pan
守卫: factor > 0 且有限；viewport/content 均 > 0

s' = clamp(scale * factor, 1.0, 3.0)                     // pq5.java:54
k  = s' / scale
O' = offset * k
   + (centroid - viewport.center) * (1 - k)
   + pan
O' = clampOffset(O', content, viewport, s')              // pq5.java:59
```

这是标准"以手势点为锚"的缩放：`centroid` 处的像素在缩放前后保持不动。

### 3.4 双击/单击归位（`j16.m6389V`）

```
target = scale < 1.1 ? 1.0 : scale
offset = clampOffset(offset, content, viewport, target)
```

即"没放大就归位（含居中），已放大则只重新钳制"。真正的双击倍率由调用方传入
`m9172a` 的 `factor`，本模块不臆造 2.0。

### 3.5 下发到视图（`ro5.java:1412-1422`）

```
TextureView:
    matrix.setScale(s, s, w/2, h/2)
    matrix.postTranslate(tx, ty)
    textureView.setTransform(matrix)

SurfaceView (SDK >= 24):
    setScaleX(s); setScaleY(s)
    setTranslationX(tx); setTranslationY(ty)
```

矩阵展开（`VideoScaleMatrix.toMatrix`）：

```
| s  0  (1-s)*pivotX + tx |
| 0  s  (1-s)*pivotY + ty |
| 0  0          1         |
```

### 3.6 SurfaceView 固定缓冲区（`ro5.java:35881-35885`）

```
fixedSize ? holder.setFixedSize((w + 63) & ~63, h) : holder.setSizeFromLayout()
```

64 像素对齐；去重键 `(w, h, fixedSize)`，由 `zz4` 全局开关控制（默认 `false`）。

---

## 4. 参数表

### 4.1 重试与降档（`ko5.java:154-198`）

| 参数 | 值 | 位置 |
|---|---|---|
| 每条目最大重试次数 | 1 | `ko5.java:184` `hasRetried` |
| 重试动作 | 直接 `exoPlayer.prepare()`（不重建 mediaItem） | `ko5.java:190` |
| 重试后仍失败 | `"重试也失败"` → 走降档 | `ko5.java:196` |
| 降档触发条件 | cause 链含 `MediaCodecVideoDecoderException` | `ko5.java:163-175` |
| 降档动作 | 回调换 H.264 直链，静默不报错 | `ko5.java:117-126` |
| 最终上报文案 | `PlaybackException.getMessage()`，null 时 `"播放失败"` | `ko5.java:130-131` |

### 4.2 首帧静音

| 参数 | 值 | 位置 |
|---|---|---|
| 起播音量 | `0.0f`（仅当 `muteUntilFirstFrame && !firstFrameUnlocked`） | `ro5.java:1013` |
| 首帧后音量 | `1.0f` | `ro5.java:35895` |
| 日志 | `等待首帧，暂时静音: url=` / `首帧音频解锁(rendered): url=` | `ro5.java:1014` / `35896` |

### 4.3 转场预缓冲（`ro5.m9956g`）

| 参数 | 值 | 位置 |
|---|---|---|
| 轮询片长 | 16 ms（`delay(16)`，且计数器每次 +16） | `ro5.java:36014` / `36001` |
| 最长等待 | 500 ms | `ro5.java:36001` |
| 等待条件 | `engine.isPlaying()` 为真才继续等 | `ro5.java:35975` |
| 动作 | `setPlayWhenReady(false) → pause → setMediaItem → prepare → seekTo` | `ro5.java:35971-35982` |
| 去重 | url 相同且 `playbackState != IDLE` 直接返回 | `ro5.java:35968` |

### 4.4 缓存（`sn5.java`）

| 参数 | 值 | 位置 |
|---|---|---|
| 目录 | `<cacheDir>/video-media` | `sn5.java:85` |
| 容量 | 100663296 B = 96 MiB | `sn5.java:85` |
| 淘汰 | `LeastRecentlyUsedCacheEvictor` | `sn5.java:85` |
| 数据库 | `StandaloneDatabaseProvider` | `sn5.java:85` |
| 预载长度 | 2097152 B = 2 MiB | `sn5.java:108` |
| 预载旁路条件 | url 空 / `isCached(url, 0, 2MiB)` / 同一 url 正在预载 | `sn5.java:106-113` |
| 并发 | 单线程 executor，新请求取消旧请求 | `sn5.java:60` / `118-121` |
| CacheDataSource flags | 2 = `FLAG_IGNORE_CACHE_ON_ERROR` | `sn5.java:76` |
| 请求头 | UA / Referer=`https://www.douyin.com/` / Accept / Accept-Language / sec-ch-ua ×3 | `sn5.java:47` |

### 4.5 缩放与手势

| 参数 | 值 | 位置 |
|---|---|---|
| 缩放范围 | 1.0 – 3.0 | `pq5.java:54` |
| 判定已放大 | `> 1.001f` | `pq5.java:70` |
| 手势互斥窗口 | 400 ms（缩放后 400ms 内忽略单击） | `pq5.java:38` + `C1789ww.java:99` |
| 点击归位阈值 | 1.1 | `j16.java:680` |
| 缓冲区宽度对齐 | 64 px | `ro5.java:35882` |

### 4.6 其他

| 参数 | 值 | 位置 |
|---|---|---|
| 构建时 | `repeatMode=OFF`，`playWhenReady=false` | `ro5.java:35926-35927` |
| 播放器创建日志 | `创建播放器实例` | `ro5.java:827` |
| 视频渲染器 joining | 50 ms | `sy5.java:20` |
| 播放速度下限 | 0.1 | `ro5.java:1174` |
| TPV 机型强制 output-surface workaround | `MANUFACTURER == "TPV"` | `ro5.java:35` + `sy5.java:26` |

### 4.7 画中画

| 参数 | 值 | 位置 |
|---|---|---|
| 最低 SDK | 26 | `MainActivity.java:383` |
| 自动进入 | SDK ≥ 31 | `MainActivity.java:417` |
| 比例范围 | 0.47 – 2.39 | `MainActivity.java:403` |
| 比例精度 | `(int)(ratio*1000) / 1000` | 同上 |
| 未知比例 | 16:9 | 同上 |
| 动作 | PIP_PAUSE(req 0) / PIP_PLAY(req 1) / PIP_NEXT(req 2) | `MainActivity.java:404-414` |
| 动作语义 | PAUSE 与 PLAY **都**是"播放暂停计数器 +1"；NEXT 置标志 | `PipActionReceiver.java:29-45` |

---

## 5. 行为清单（新代码对外提供）

`PlayerEngine`

- `open(url, seekMs, forceReload)` — 去重派发、预缓冲复用、换源、静音、seek、可选自动播放
- `openItem(item)` / `openCurrentItem()` — 从队列取 item + 进度恢复 + 预载下一条
- `play()` / `pause()` / `togglePlayPause()` — 无 surface 时只记意愿，绑定后自动起播
- `seekTo` / `setSpeed`（下限 0.1）
- `attachSurface` / `detachSurfaceKeepLastFrame`
- `prepareDuringTransition(url, seekMs)` — 转场"只下载不解码"
- `playNext` / `playPrevious`
- `release()` — 共享池退引用 / 独立播放器直接 release
- 内部状态机：`UNPREPARED → PREPARING → BUFFERING → READY → ENDED`，异常分支 `ERROR`，终态 `RELEASED`
- 错误处理：重试 1 次 → 解码器降档 → 上报
- 播放结束：`queue.onPlaybackEnded()` 自动连播

`VideoScaleMatrix` — 纯函数：`clampOffset` / `zoom` / `realignAfterTap` / `reset` / `toTransform` / `toMatrix` / `alignedSurfaceWidth`

`VideoMediaCache` — `requestPreload`（空/已缓存/重复跳过，串行 + 取消）/ `cancelPreload` / `isCached` / `dataSourceFactory` / `requestHeaders`

`PipController` — `buildParams` / `aspectRatio` / `standardActions` / `commandOf` / `enter`（含异常兜底）

`PlaybackQueue` — `replaceAll` / `next` / `previous` / `moveTo` / `peekNext` / `onPlaybackEnded` / `resolveUrl`

`SharedPlayerPool` — `obtain`（同 client 复用、异 client 重建）/ `addRef` / `releaseRef` / `releaseIndependent` / `releaseAll`

`GlVideoSurfaceView` — 契约 + `Spec`（EGL 8/8/8/8、`RENDERMODE_WHEN_DIRTY`、external OES、LINEAR、CLAMP_TO_EDGE、顶点/纹理坐标、着色器源码）

---

## 6. 待迁移（Android / Compose 层，本模块不实现）

| 项 | 原位置 | 说明 |
|---|---|---|
| 播放页全部 UI | `ro5.m9950a`（264–35847） | 进度条、按钮、弹幕层、转场动画、焦点导航，3.5 万行 |
| `PlayerView` 三套布局 | `res/layout/player_view_{surface,texture,gl}.xml` | 需按 `PlayerSurfaceMode.layoutName()` inflate |
| PlayerView 固定属性 | `ro5.java:1278-1287` | `useController=false`、`controllerAutoShow=false`、`controllerHideOnTouch=true`、`focusable=false`、`focusableInTouchMode=false`、`descendantFocusability=FOCUS_AFTER_DESCENDANTS(393216)`、`resizeMode=0`、`keepScreenOn=true`、黑底、`MATCH_PARENT` |
| `MediaEngine` 实现 | `ro5.m9955f` | `ExoPlayer.Builder(...).setRenderersFactory(po5).setMediaSourceFactory(DefaultMediaSourceFactory(sn5.cache))` |
| 自定义 RenderersFactory | `po5.java` / `sy5.java` | `buildAudioSink` 注入 `v05`（AudioProcessorChain）；`buildVideoRenderers` 用 `sy5`（`codecNeedsSetOutputSurfaceWorkaround` 对 TPV 恒真，joining 50ms） |
| 音量均衡音频处理器 | `rs2.java` / `os2.java` / `qs2.java` | 见 §7.1 参数表，纯 DSP，可后续单独移植 |
| GL 视图实现 | `mp1.java` / `xn5.java` | 按 `GlVideoSurfaceView` 契约实现；`queueEvent` 切线程 |
| Surface 绑定 | `lp1.java` | `setVideoSurface` 前比对 `(player, surface)`；解绑时 `setKeepContentOnPlayerReset(true)` + `setPlayer(null)` |
| 转场动画驱动 | `i53.java` / `a53` | 32ms 延迟后解析下一条 + 读进度 + `m9956g` |
| 信息流占位复位 | `um5.java` case 0 | 日志 TAG `VideoItem`，属信息流模块 |
| PIP 系统调用 | `MainActivity` / `PipActionReceiver` | `PictureInPictureParams.Builder`、`RemoteAction`、`PendingIntent.getBroadcast`、图标 `pip_pause/play/next` |
| 弹幕 / 字幕 | `ko5.onCues`（空实现） | 原版就是空的，不需迁移 |
| 播放进度上报 | `zd1` / `on5` / `ta1` | 属数据层，`ResumeStore` 只定义读写接口 |
| 直播播放路径 | `C1789ww` 等 | 与点播共用 `PlayerEngine`，但 URL 解析与 PIP 比例策略不同（直播传 0 → 16:9） |

### 6.1 音量均衡（`rs2`）参数（供后续移植）

`os2.m8820c` 的档位表（`startupGainDb, maxGainDb, minGainDb, silenceGateDb, calibrationSignalSec, programIntegrationSec, gainAttackSec, gainReleaseSec`）：

| 档位 | startup | max | min | silenceGate | calibration | integration | attack | release |
|---|---|---|---|---|---|---|---|---|
| off | 0 | 0 | 0 | -120 | 0 | 0 | 0.05 | 1.2 |
| low | -0.5 | 1.0 | -8 | -55 | 2.5 | 10 | 3.0 | 8.0 |
| medium | -1.5 | 2.0 | -12 | -58 | 2.0 | 8 | 2.0 | 8.0 |
| high | -3.0 | 2.5 | -16 | -60 | 1.5 | 6 | 1.5 | 5.0 |

目标响度 `-20 dB`（`rs2.java:212` 的 `(-20.0d) - (log10(power) * 10) - 0.691`），
增益限幅 `0.98 / peak`，混合权重 `[1, 1, 1, 0, 1.41…]`（`i3 > 2` 时第 4 声道置 0、第 5+ 声道 1.41）。

---

## 7. 存疑

1. **`pq5.m9172a` 的 6 个参数里有两个尺寸**（`j3`、`j4`、`j5`，其中三个都做了 `>0` 校验）。
   `m9170b(offset, size1, size2, scale)` 中 `size1` 是内容、`size2` 是视口；但 `m9172a` 里
   中心点用 `j4`、钳制用 `(j3, j5)`，无法从单个调用点唯一确定 `j3/j4/j5` 的三者关系。
   本模块按"内容尺寸 + 视口尺寸"两参数实现，并以视口中心作为锚点基准，行为在常见分辨率下一致。
2. **`m9956g` 的时间双重累加**：`j2 = j4 + 16`，同时 `delay(16)`，即"等待时长"按逻辑步进而非
   真实时钟。反编译的循环被 JADX 标为 `Unsupported multi-entry loop pattern`，可能存在还原偏差。
3. **`ko5` 的 `f12383N`（"attempt"）**：类型是 `Object`，既被当作 map 的 key 比较，又被拼进日志，
   推测是"本次加载的序号令牌"，用于丢弃过期回调。本模块用 `retryCount` + url 比较近似。
4. **`dp3` 枚举**：JADX 报 `Enum visitor error`，`values()` 引用不存在的 `f4211N` 字段，
   已按三个常量重建；`f4213I`/`f4214J` 两个字段值完全相同（都是枚举名）。
5. **`f19158a`（TPV 判定）** 同时被两处使用：`sy5.codecNeedsSetOutputSurfaceWorkaround`
   （正确用途）与 `ko5.m7237a` 里的 `boolean z2 = ro5.f19158a;`——后者读出来只赋给局部变量
   从不使用，是 R8 遗留的无用读。
6. **`m9952c` 用原始 `w/h` 而非乘过 pixelRatio 的值**做 `setFixedSize`，与 `m9951b`
   的宽高比口径不一致。是否是有意（缓冲区按编码分辨率）无法确认，本模块保留原口径。
7. **`mo5.java:112`** 的 `new uj3(obj2, str)`（二元组）被塞进 `WeakHashMap<ExoPlayer, uj3>`，
   而比较时 `j42.m6486c(t92.get(player), uj3Var)`；`uj3` 的 `equals` 未在产物中确认，
   去重语义按"player → (attempt, url)"理解。
