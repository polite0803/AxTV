# AxTV 重构工程 —— 统一约定

本目录是 AxTV 2.3.43 反编译源码（`../src`）的**重构目标工程**。
反编译产物是 Java（原 App 用 Kotlin 写），类名已被 R8 全量混淆为 `p000.*`。

## 铁律

1. **`../src` 是只读的**。不得修改、移动、删除其中任何文件。所有新代码只写到本目录。
2. **不遗留功能**。原类里每一个对外行为（公开方法、常量、协议字段、边界处理、日志点）
   都要在新代码里有对应实现。确实属于 Android UI/Compose 无法在纯 Java 核心层表达的，
   写进该模块 `NOTES.md` 的「待迁移」清单，不要静默丢弃。
3. **简化**。去掉反编译产物里的噪音：`f95xxx` 字段名、`m10092b` 方法名、
   冗余临时变量、jadx 拆出来的 `$$serializer` 脚手架、无意义的 `C0029C` 之类改名常量。
   用有意义的名字重建结构，但**不改变行为**。

## 目标技术形态

- 语言：**Java 17**，核心层只用 JDK（`java.net.http`、`java.util`、`java.util.concurrent`）。
- 不依赖 Android SDK、OkHttp、kotlinx.serialization 于核心层。
  原代码用到的这些能力，改为**接口 + 说明**（如 `CookieStore`、`HttpEngine`、`Assets`），
  在 `NOTES.md` 写清 Android 侧应有实现。
- JSON：核心层用 `JsonCodec` 接口抽象，不绑具体库（原工程用 kotlinx.serialization）。
- 包名根：`axtv`。每个模块一个子包。

## 每个类的文件头

```java
/*
 * 来源: src/app/p000/C1400n.java (反编译自 com.github.axtv.dv 的 R8 混淆类)
 * 原职责: QuickJS 引擎初始化 / abogus.js + abogus_dy.js 加载 / a_bogus 计算
 * 重构说明: 拆成 AbogusEngine(引擎生命周期) + AbogusSigner(签名调用)
 */
```

## 每个模块必须产出

- `*.java` 重构代码
- `NOTES.md`：
  - **类映射表**：`原类 (src/app/p000/xxx.java)` → `新类` → 行数
  - **行为清单**：该模块对外提供了什么
  - **待迁移**：依赖 Android/第三方、本轮未落地的部分
  - **存疑**：反编译不完整（jadx 报错）或语义不明处

## 已确认的事实（可直接引用，无需再验证）

- 包名 `com.github.axtv.dv`，版本 0.0.3 (versionCode 3)，minSdk 26 / targetSdk 36，Android TV 应用
- 应用自有代码 ≈ `src/app/p000/*`（6077 个类，R8 扁平化）+ `src/app/com/github/mytv/dv/*`（187 个类，含 model/widget）
- 第三方库保持原包名：`androidx.*` `okhttp3.*` `coil3.*` `kotlinx.*` `com.whl.quickjs.*` `com.materialkolor.*`
  —— 这些**不在重构范围内**
- API 域名：`www.douyin.com`、`live.douyin.com`、`aweme.snssdk.com`、`ttwid.bytedance.com`
- 签名：`assets/abogus.js` + `assets/bdms.js` + `assets/abogus_dy.js` + `assets/sign.js` 经 QuickJS 执行
- 日志 TAG 可用于定位模块：VideoPlayer / ABogusSigner / DouyinDirectApi / LiveWS / SearchInputServer / CookieServer / SignJs / UpdateManager / SettingsManager / QrLoginManager / Danmaku / TvChannelPublisher
