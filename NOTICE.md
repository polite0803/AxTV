# NOTICE

## 许可证

**本仓库整体以 GNU General Public License v3.0（SPDX: GPL-3.0）分发**，许可证全文见仓库根目录的 [LICENSE](LICENSE)。

## 第三方组件

本仓库/APK 包含以下第三方组件，各自按其原始许可证分发：

| 组件 | 版本/形式 | 许可证 | 说明 |
|---|---|---|---|
| QuickJS | `jniLibs/<abi>/libquickjs-android-wrapper.so` | MIT（Fabrice Bellard） | JS 运行时；Android 绑定类 `com.whl.quickjs` 为原 APK 内置实现 |
| ZXing core | 3.5.3（Maven） | Apache-2.0 | 本地渲染二维码 |
| AndroidX / Jetpack Compose / Media3 / WorkManager / Coil | 见 `android/gradle/libs.versions.toml` | Apache-2.0 | Android 运行时与 UI |
| OkHttp | 4.12.0 | Apache-2.0 | HTTP 客户端 |

QuickJS 的 `.so` 以预编译形式随仓库分发；其上游源码与构建方式见 <https://bellard.org/quickjs/>。
分发 APK 时请一并保留上述许可证与版权声明。

## 许可要求（GPL-3.0 摘要，非法律文本）

- 分发本仓库中源自上游的代码（含 `refactor/` 与 `android/` 中的相关实现）时，须继续以 GPL-3.0 授权，并保留版权与许可声明。
- 分发二进制（APK）时须同时提供对应的完整源代码，或按 GPL-3.0 第 6 条提供获取方式。
- 不得对源代码附加额外限制。

许可证以官方文本为准；本节仅为便于阅读的摘要，不替代许可证原文。

## 说明

- 本项目与抖音无从属或背书关系。
