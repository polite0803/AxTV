# 重构路线

## 硬约束（先读这段）

1. **功能一条不能少。** 原版 2.3.43 的每个功能都必须在 `src/`（原样保留）与 `refactor/`（可读重建）之间有明确归属。
   逐条核对见 **[COVERAGE.md](COVERAGE.md)** —— 99 条功能行的状态、落点、缺口都列在表里。
2. **"简化"只针对代码形态**：去混淆名、拆 God class、常量保留原值并注明来源。**不删功能、不改行为、不裁剪边界条件。**
3. 每轮结束都要更新 `COVERAGE.md`：把 ⬜ 改成 ✅，或把缺口写进该模块 `NOTES.md` 的「待迁移」。

## 规模基线

| 指标 | 数值 |
|---|---|
| 反编译产物 `src/app/` | **784,115 行**（`p000` 6077 类 + `com/github/axtv/dv` 187 类） |
| 其中 Compose UI | 141 个类（`lq2` 22.4k / `eu5` 11.7k / `on5` 10.5k / `i42` 8.1k / `qg5` 7.6k / `qm4` 7.3k / `mw3` 5.9k …） |
| 网络相关 | 102 个类 |
| 应用自有模型 | 88 个类 |
| 重构产出 `refactor/` | **261 个文件 / 29,442 行 / 447 个 class（2026-11-05 统计，不含 `refactor/tests/`；含测试共 286 文件 / 31,656 行；本轮未复跑 javac）** |

逐类索引：`docs/class-index.csv`（6077 行，含行数 / 日志 TAG / 中文字面量 / 依赖模型 / API 路径）

## 已完成模块（第一层：核心域，纯 Java 17）

| 模块 | 文件 | 行数 | 覆盖内容 | 说明 |
|---|---|---|---|---|
| `sign/` | 8 | 1,292 | a_bogus / sign.js 双 QuickJS 运行时、转义、结果解析 | `NOTES.md` 含 12 条实现风险 |
| `api/` | 23 | 2,331 | 45 个端点、公共参数、用户中心签名链、ticket-guard 头语义、7 个业务门面 | `NOTES.md` 含端点表 |
| `model/` | 85 | 3,577 | 87 个模型 record、柔性解码、取流算法、计数格式化 | `NOTES.md` 含字段总览表 |
| `live/` | 23 | 3,011 | wss 连接、protobuf 帧、心跳重连 ack、弹幕几何、直播选流 | `NOTES.md` 含帧字段号表 |
| `panel/` | 15 | 3,096 | 9978/9979/9980 三个服务、模板渲染、扫码状态机、互动凭据解析 | `NOTES.md` 含路由表 + 安全风险 |
| `player/` | 17 | 2,323 | 播放状态机、缩放矩阵、缓存、连播、PIP、H.264 降档、共享播放器池 | `NOTES.md` 含 ro5 分区 |
| `settings/` | 9 | 3,041 | 71 个配置键、更新与 9 个镜像回退、崩溃报告 | `NOTES.md` 含配置键全表 |
| `app/` | 1 | 109 | Application 初始化顺序 | — |

## 第二轮新增模块（行为规格 + 应用外壳 / EPG）

| 模块 | 文件 | 覆盖内容 | 说明 |
|---|---:|---|---|
| `tv/` | 8 | 两个 Preview Channel 的创建/增量同步、Aweme→PreviewProgram、SDK 列裁剪、触发条件 | `tv/NOTES.md` 含字段全表与同步时序 |
| `app/` 扩展 | 4 | MainActivity 生命周期、13 条 deep link、遥控器按键表、PIP 参数 | `app/NOTES.md` 含按键表与路由表 |
| `ui/` 第一批 | 2 | **77 个设置页控件**（key/type/顺序/分组）、点播+直播共 10 档画质、71 键对照、选集面板完整行为规格（四档前景色/文案格式/状态分支） | `ui/NOTES.md` 含 77 行控件表 + 选集规格 |
| `widget/` | 39 | 3,545 | 六个 Glance 小组件行为规格（`WidgetRenderSpec` 933 行：7 态 Screen / 分支 / 点击区域 / 尺寸常量）、刷新链 Refresher→Scheduler→Worker→Updater、存储键全表、action + deep link + 媒体键码表、主题色板、图片规格 | `widget/NOTES.md` 518 行含 49 行类映射表 |

### 口径：UI 层怎么做到「功能不删」

Compose 的像素绘制无法用纯 JDK 表达，所以采用**行为规格先行**：
把「有哪些控件 / 什么类型 / 什么顺序 / 绑定哪个配置键 / 显示什么文案 / 改了之后联动什么」
全部落成可执行的数据模型 + 逐行规格表，只有「像素怎么画」留给 Android 侧。
`qm4.m8567Z(key, type, composable)` 与 `j16.m8567Z(...)` 的注册调用是这套规格的实证来源。

## 剩余（第三层：绘制层）

| 模块 | 规模 | 内容 | 依据 |
|---|---|---|---|
| `ui/` 绘制层 | 141 类 / 约 12 万行 | Compose 页面：主页信息流、视频详情、评论、搜索、合集/选集、播放器 UI、直播页 | `docs/UI地图.md` |
| `widget/` 绘制层 | 6 个组件树 | Glance Composable 的实际布局与 Bitmap 解码 | `src/app/com/github/mytv/p004dv/widget/` |
| 零散页面 | 4 类 | CrashActivity / PipActionReceiver / PIN 输入页 / Hilt 注入替代 | `COVERAGE.md` |

## 每轮的做法

1. 从 `docs/class-index.csv` 定位目标类（按 `log_tags` / `zh_sample` / `api_paths` 过滤）
2. 读 `src/app/p000/<class>.java`，把**全部**对外行为列成清单（公开方法、常量、协议字段、错误语义、边界分支）
3. 在 `refactor/<module>/axtv/<module>/` 下按职责重建；文件头注明来源类与原始行数
4. 常量原样保留并注明来源行号；Android / 第三方能力抽成接口，并在 `NOTES.md` 写清 Android 侧实现
5. 更新该模块 `NOTES.md`（类映射表 / 行为清单 / 待迁移 / 存疑）
6. 更新 `COVERAGE.md` 与本文进度表
7. 全量编译验证：`javac --release 17` 零错误

## 已知的"不能照抄"的地方

- **Kotlin → Java 变形**：`p70.m8973g0(...)` 其实是 `CoroutineScope.launch`；`C0029C` / `RendererCapabilities` 是 jadx 的改名/合并产物，要按上下文还原真实类型
- **`p000` 里混了被重命名的第三方类**：`AbstractC1545qb`(7507 行) 是 `androidx.navigation`、`p63` 是 `NavBackStackEntry`、`p70`/`ez2`/`i42` 混着 Media3 与 Compose 内部实现 —— 这些**不重写**，只引用
- **`Flexible*Serializer`**：抖音接口会把数字/布尔返回成字符串，解码必须容错（已在 `model/FlexibleJson` 还原）
- **`SetCookie` 取 `;` 切分后的【第一段】**（`o70.m8483U0` = `list.get(0)`）：`ttwid=1%7C...; Path=/; HttpOnly` → `ttwid=1%7C...`。早期重构误读成"取最后一段"，会让服务端下发的 ttwid/msToken/UIFID 永远回写不进 cookie jar，已修正
