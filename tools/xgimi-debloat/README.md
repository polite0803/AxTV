# 极米电视/投影 预装清理脚本（可逆，默认只读）

用于清理极米 Play3 等 Android TV 设备上的媒体/商店类预装应用。
不 root、不刷机。**默认推荐 `pm disable-user --user 0`（完全可逆、不删 APK）**；
`pm uninstall --user 0` 仅对系统预装保证可用 `install-existing` 恢复，第三方 /data/app 包需先备份 APK。

## 前置条件

1. 电视打开「开发者选项 → USB 调试」（或网络调试）。
2. 电脑能执行 `adb devices` 并看到设备：
   - USB：直接用数据线连接；
   - 网络：`adb connect <电视IP>:5555`。
3. 电脑有 PowerShell 5.1+（Windows 自带）。

## 使用

在脚本目录执行（把 `<serial>` 换成 `adb devices` 里的序列号）：

```powershell
# 1) 只读：列出所有包并按 CRITICAL / BLOAT / THIRD-PARTY / SYSTEM-REVIEW 分类
powershell -ExecutionPolicy Bypass -File .\xgimi-debloat.ps1 -Serial <serial>

# 2) 先停用（推荐第一步，最可逆；系统应用也可用）
powershell -ExecutionPolicy Bypass -File .\xgimi-debloat.ps1 -Action Disable -Serial <serial> -Packages com.dangbeimarket,com.shafa.market -Yes

# 3) 确认没问题后再卸载当前用户（系统预装可逆；/data/app 第三方包必须加 -AllowApkRemoval，脚本会先备份 APK）
powershell -ExecutionPolicy Bypass -File .\xgimi-debloat.ps1 -Action Uninstall -Serial <serial> -Packages com.dangbeimarket,com.shafa.market -AllowApkRemoval -Yes

# 4) 恢复（读取备份目录的 removed.txt；也可 -Packages 指定）
powershell -ExecutionPolicy Bypass -File .\xgimi-debloat.ps1 -Action Restore -Serial <serial> -Yes

# 5) 重新启用被 disable 的包
powershell -ExecutionPolicy Bypass -File .\xgimi-debloat.ps1 -Action Enable -Serial <serial> -Packages <pkg> -Yes
```

每次执行都会在 `.\xgimi-debloat-backup\<serial>\` 下保存：

- `packages-时间.csv`：完整包清单与分类；
- `<pkg>.path.txt` / `<pkg>.dump.txt`：操作前的 APK 路径与包信息；
- `removed.txt`：本次操作过的包，供 `-Action Restore` 使用。

## 安全策略

脚本会**拒绝**操作以下包（`CRITICAL`）：

- 当前桌面/Launcher（会查 `resolve-activity ... HOME`）；
- 所有 `com.xgimi.*`、`com.mitv.*`；
- `android*`、`com.android*`、`com.google*`；
- 设置、SystemUI、输入法、遥控、OTA/升级、WLAN/蓝牙、音频、媒体/解码、DRM、TV Provider 等关键字。

恢复规则（实测修正）：

- **系统预装**（APK 在 `/system`、`/system_ext`、`/vendor`、`/product`）：`pm uninstall --user 0` 后可用
  `adb shell cmd package install-existing <包名>` 恢复。
- **第三方 /data/app 包**：`pm uninstall --user 0` 有可能连 APK 一起删除，`install-existing` 不保证成功。
  脚本默认**拒绝**这类包的卸载；加 `-AllowApkRemoval` 后会把 APK 先 `adb pull` 到备份目录，
  恢复时先试 `install-existing`，失败再用备份 APK `adb install`。
- 最坏情况：恢复出厂设置恢复全部系统应用。

## 建议顺序

1. 先 `List`，打开 CSV 看 `BLOAT` / `THIRD-PARTY`。
2. 优先用 `Disable` 处理媒体/应用商店类（芒果TV、云视听、奇异果、当贝、沙发管家等）；
   确认不影响桌面/遥控/投影功能后，再考虑 `Uninstall`。
3. 一次只动 1–3 个，重启后确认：
   - 桌面正常；
   - 遥控、自动对焦/梯形校正、设置、OTA 正常；
   - 投屏/语音等功能是否依赖被删应用。
4. 有问题立刻 `Restore` 或 `Enable`。

## 注意

- 这是私有工具，不属于 AxTV 主应用，也不随应用发布。
- 不同固件包名可能不同，以 `List` 的实际输出为准，不要照抄网上的包名清单。
- 清理预装能释放一些内存/存储，但不会消除 X5 在 2GB 设备上的内存瓶颈。
- 已在模拟器验证：列清单、Disable/Enable、第三方包卸载拒绝、APK 备份与恢复全链路。
