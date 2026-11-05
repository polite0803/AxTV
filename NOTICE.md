# NOTICE

## 许可证

**本仓库整体以 GNU General Public License v3.0（SPDX: GPL-3.0）分发**，许可证全文见仓库根目录的 [LICENSE](LICENSE)。

## 上游项目

本仓库（AxTV）中部分代码、资源与设计来自以下开源项目，许可声明如下：

- 上游仓库: [mytv-android/myDV](https://github.com/mytv-android/myDV)
- 上游描述: 保持深呼吸！
- 上游版本: `myDV 2.3.43`（原始包名 `com.github.mytv.dv`）
- 上游许可证: **GNU General Public License v3.0（SPDX: GPL-3.0）**
  - 许可证文本: https://github.com/mytv-android/myDV/blob/main/LICENSE
  - 官方文本: https://www.gnu.org/licenses/gpl-3.0.txt

## 许可要求（GPL-3.0 摘要，非法律文本）

- 分发本仓库中源自上游的代码（含 `refactor/` 与 `android/` 中的相关实现）时，须继续以 GPL-3.0 授权，并保留版权与许可声明。
- 分发二进制（APK）时须同时提供对应的完整源代码，或按 GPL-3.0 第 6 条提供获取方式。
- 不得对源自上游的代码附加额外限制。

许可证以官方文本为准；本节仅为便于阅读的摘要，不替代许可证原文。

## 说明

- `refactor/` 是将上游代码整理为可读、可编译的增量结果；`android/` 是承载该核心层的 Android 壳工程。
- 上游原始证据（`src/`）与本仓库的构建产物、临时文件不入库，见 [.gitignore](.gitignore)。
- 本项目与上游作者无从属或背书关系。
