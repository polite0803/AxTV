# AxTV Android 壳工程

把 `refactor/`（纯 JDK 核心层，行为规格 100% 落地、326 项测试全绿）接成可安装的 APK 的壳工程。

## 现状

- ✅ Gradle 骨架（`settings.gradle.kts` / `libs.versions.toml` / `app/build.gradle.kts`，版本目录统一管理）
- ✅ `AndroidManifest.xml` 按原版逐条还原（11 权限 / 2 EPG 权限 / 6 小组件 / FileProvider / `axtv://douyin` 深链 / leanback）
- ✅ `res/`（5.2MB，含 `Theme.AxTV`、`tv_banner`、6 个 `widget_*_info.xml`）与 `assets/`（1.5MB，sign JS + panel 模板）已从 `src/resources/` 原样拷入
- ✅ 核心层通过 `sourceSets` 直连 `../refactor/`（不拷贝，只有一份真相）
- ⬜ Android 适配类 —— **全部工作清单见 [`ADAPTERS.md`](ADAPTERS.md)**（接口 → 实现 → 依赖 → NOTES 章节，12 张表）

## 用法

1. Android Studio 打开本目录（需 compileSdk 36 / JDK 17）。
2. 先建最小实现让编译过：`MyApplication` / `MainActivity` / `CrashActivity` / `PipActionReceiver` / 6 个 WidgetReceiver（均为薄壳，装配逻辑按对应 NOTES）。
3. 按 `ADAPTERS.md` 顺序实现适配类（建议顺序：sign → api → settings → player → ui 页面 → panel → widget → tv）。

## 行为规格在哪

| 要实现什么 | 照抄哪里 |
|---|---|
| 初始化顺序 / 路由 / 按键 / PIP | `refactor/app/NOTES.md` |
| 签名引擎装配 | `refactor/sign/NOTES.md §6`（含 6 行示例） |
| 69 个生效配置键 | `refactor/settings/NOTES.md` 全表 |
| 播放菜单文案/色板/手势 | `refactor/ui/NOTES-player-ui.md` |
| 页面行为 | `refactor/ui/NOTES.md §11` + `NOTES-on5.md` + `NOTES-qg5.md` |
| 小组件绘制 | `refactor/widget/NOTES.md §3.1-3.6`（933 行可执行规格） |
| EPG 字段 | `refactor/tv/NOTES.md` |
| 面板路由/安全 | `refactor/panel/NOTES.md` |

原版证据：`src/`（784k 行反编译产物）+ `src/resources/AndroidManifest.xml`，只读不删。
