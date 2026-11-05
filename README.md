# AxTV 反编译 + 重构工程（基于 myDV 2.3.43）

本目录是对 `myDV-2.3.43.apk`（抖音 Android TV 客户端，原包名 `com.github.mytv.dv`，重构后 applicationId 为 `com.github.axtv.dv`）的**完整反编译产物**与**重构中的干净工程**。

## 目录结构

```
AxTV/
├── myDV-2.3.43.apk          原始 APK（12.9 MB）
├── README.md                本文件
├── docs/                    分析文档
│   ├── 功能清单.md           全量功能清单 + 类/文件定位
│   ├── 核心流程.md           关键链路（登录 / 签名 / 取流 / 播放 / 直播 / 面板）
│   └── 反编译报告.md         工具、参数、成功率、失败项
├── src/                     ★ 反编译全量源码（只读，勿改）
│   ├── sources/             jadx 输出：8461 个 .java
│   ├── resources/           解码后的资源：AndroidManifest.xml、res/、assets/
│   └── app/                 ★ 应用自有代码抽取（6264 个 .java）
│       ├── p000/            R8 扁平化命名空间（6077 个类）
│       └── com/github/mytv/dv/  保留可读名的应用类（187 个：MainActivity / model / widget）
└── refactor/                ★ 重构中的干净工程（234 文件 / 24,490 行 / 411 class，javac 17 零错误）
    ├── CONVENTIONS.md       重构约定（必读）
    ├── PLAN.md              重构路线与进度
    ├── COVERAGE.md          ★ 功能覆盖矩阵：99 条功能行逐条核对，零删减
    ├── TESTS.md             ★ 147 项行为烟雾测试 + 复现命令
    ├── sign/axtv/sign/      签名引擎（a_bogus / sign.js）
    ├── api/axtv/api/        抖音 Web API 客户端（45 个端点）
    ├── model/axtv/model/    数据模型（87 个 record）+ 取流算法
    ├── player/axtv/player/  播放器（状态机 / 缩放 / 缓存 / PIP / 降档）
    ├── live/axtv/live/      直播 WS + protobuf 帧 + 弹幕几何
    ├── panel/axtv/panel/    手机↔电视 局域网面板（9978/9979/9980）
    ├── settings/axtv/settings/  71 个配置键 / 更新 / 崩溃
    ├── app/axtv/app/        Application + MainActivity + 路由 + 按键 + PIP
    ├── tv/axtv/tv/          电视频道发布（EPG，两个 Preview Channel）
    ├── ui/axtv/ui/          设置页 77 控件 + 画质 10 档 + 选集面板行为规格
    └── widget/axtv/widget/  6 个 Glance 桌面小组件（39 类，行为规格全还原）

android/                    ★ Android 壳工程骨架（gradle + Manifest 还原 + 资源已拷入）
    └── ADAPTERS.md         ★ 适配层总表：接口 → Android 实现 → NOTES 规格出处
```

`src/` 是**证据**（原样保留，可回溯一切行为）；`refactor/` 是**结论**（去混淆、可读、可继续开发）。

## 反编译是怎么做的

```bash
# 工具：jadx 1.5.6 (CLI)  +  JDK 17
java -Xmx6g -cp jadx-1.5.6-all.jar jadx.cli.JadxCLI \
     -d src --deobf -j 8 --show-bad-code --comments-level info myDV-2.3.43.apk
```

- `--deobf`：开去混淆（能得到 `AbstractC1545qb` / `C1402` 这类可追踪的名字，而不是纯 `a.b.c`）
- `--show-bad-code`：即使某方法反编译不完整也导出（保证"不遗留"），坏代码处有 jadx 注释标记
- 结果：10854 个类全部导出为 8461 个 `.java`，204 处方法级失败（见 `docs/反编译报告.md`）

## 三个必须先知道的坑

1. **应用代码不是按包组织的**。R8 full mode 把应用自有类和部分第三方类一起压平进了 `p000` 包，
   类名被混淆成 `n11` / `ro5` / `C1400n`。**不要靠包名判断归属**，要靠：
   - 日志 TAG（`VideoPlayer` / `ABogusSigner` / `DouyinDirectApi` / `LiveWS` / `CookieServer` …）
   - 中文字面量（UI 文案集中在 `on5` / `eu5` / `qm4` / `ro5`）
   - 对 `com.github.mytv.p004dv.R` 与 `model.*` 的引用
2. **`p000` 里混了少量被重命名的第三方类**（已确认：`androidx.navigation` 的 `NavBackStackEntry`
   就是 `p63`）。所以 `src/app/` 是"近似"的应用代码集合，不是严格边界。
3. **原 App 是 Kotlin**，jadx 输出的是反编译出的 Java。Kotlin 语法糖（默认参数、内联、协程、
   密封类）在 Java 里会变形，看到 `p70.m8973g0(...)` 这类调用要意识到它原本是 `CoroutineScope.launch`。

## 建议的阅读顺序

1. `docs/功能清单.md` —— 先知道这 App 有哪些功能
2. `docs/核心流程.md` —— 再知道关键链路怎么走
3. `refactor/*/NOTES.md` —— 每个模块的类映射与设计
4. 需要抠细节时回 `src/app/` 查原类（每份重构代码的文件头都写了来源类路径）

## 重构进度

**功能零删减是硬约束**：原版每条功能都在 `src/` 里有完整实现，`refactor/` 的进度只影响"可读性"。
逐条核对表见 [`refactor/COVERAGE.md`](refactor/COVERAGE.md)（99 条功能行：✅ 81 / 🟡 18 / ⬜ 0（剩余缺口全为绘制层）），
路线与剩余工作见 [`refactor/PLAN.md`](refactor/PLAN.md)，行为验证见 [`refactor/TESTS.md`](refactor/TESTS.md)。

核心域（签名 / API / 模型 / 播放器 / 直播 / 面板 / 设置 / 应用外壳 / EPG）已全部可读重建并编译通过；
本轮新增**设置页 77 个控件与 71 个配置键的一一对照**，把「功能没被删」变成可核对的数据。
剩余主要是 Compose 页面绘制（约 12 万行）与 Glance 组件树绘制，按「行为规格先行、绘制随后」逐页面推进。
