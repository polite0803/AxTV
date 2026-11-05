# axtv.settings —— 配置 / 更新 / 崩溃 重构说明

来源：`src/app/p000/fl4.java`（1138 行，TAG `SettingsManager`）、`ll4.java`（303 行，UI 侧读写）、
`lf5.java`/`ff5.java`（TAG `UpdateManager`）、`MyApplication.java`（崩溃兜底）。

## 1. 类映射表

| 原类 | 原行数 | 新类 | 新行数 | 说明 |
|---|---|---|---|---|
| `fl4.java` | 1138 | `Settings` | 1548 | 全部配置项的读写 + 取值夹取 + 旧键迁移 + 账号列表 + 互动凭据 |
| `fl4` 键名字面量（散落） | — | `SettingsKeys` | 115 | **71 个键**集中成常量（见 §2） |
| `fl4.m4215x` / `m4216y`（org.json） | — | `SettingsJson` | 274 | 账号数组 / 弹幕屏蔽词的 JSON 编解码 |
| `fl4.f6240a`（SharedPreferences） | — | `SettingsStore`（接口） + `MapSettingsStore`（测试用） | 48 + 119 | 核心层不依赖 Android |
| `lf5.java` | — | `UpdateManager` | 526 | 检查更新 → 下载 → 校验 → 安装 |
| `lf5.f13115e` 镜像表 | — | `UpdateMirror` | 78 | 9 个 ghproxy 镜像的回退顺序 |
| `ff5.java` | — | `ReleaseJson` | 213 | GitHub Releases 响应解析（tag_name / assets[].digest …） |
| `MyApplication` 的异常处理器 | — | `CrashHandler` | 120 | 报告格式与 500ms 自杀延迟原样保留 |

## 2. 配置键全表（71 个，键名字面量与原 APK 一致）

### 存储容器
| key | 常量 |
|---|---|
| `douyin_auth` | `PREFS_SETTINGS`（SharedPreferences 名） |
| `remote_config` | `PREFS_REMOTE_CONFIG` |

### 账号 / 登录 / 互动凭据
`cookie_string` `accounts_list` `current_account_id` `self_user_id` `user_unique_id`
`interaction_security_payload` `interaction_ticket` `interaction_ts_sign`
`interaction_private_key` `interaction_server_cert`
`mobile_feed_device_id` `mobile_feed_install_id`
`license_agreed` `license_agreed_version` `license_required_version` `last_announcement_id`

### 播放
`video_resolution` `live_video_resolution` `video_codec_preference` `separate_video_fetch`
`video_start_prefetch` `video_load_timeout_seconds` `player_surface_type` `surface_buffer_align`
`playback_clock_display_mode` `playback_completion_action` `auto_play_next_video`
`playback_speed` `player_progress_bar` `pip_enabled` `clean_pause` `featured_auto_play`
`audio_balance_level`

### 浏览 / 列表
`viewed_video_ids` `feed_history_capacity` `open_own_profile_on_history_tab`
`reverse_playlist_in_profile` `mix_playlist_panel_enabled` `show_details_on_start`
`filter_mobile_video` `default_home_screen` `reverse_navigation` `disable_double_back_exit`

### 弹幕
`danmaku_enabled` `video_danmaku_enabled` `live_danmaku_enabled` `danmaku_emoji_enabled`
`live_danmaku_style` `danmaku_opacity` `danmaku_area_percent` `danmaku_area`
`danmaku_vertical_spacing` `danmaku_horizontal_spacing` `danmaku_font_size` `danmaku_speed`
`danmaku_block_enabled` `danmaku_block_words` `danmaku_filter_package`

### 外观
`ui_form_factor` `app_font_scale` `theme_mode` `color_source` `dynamic_color` `color_style`
`color_contrast` `color_transition` `global_seed_argb` `ambient_backdrop`

### 其它
`tv_launcher_channel_enabled`

> 与 `docs/功能清单.md` 早期版本列的 48 个相比，这里是 71 个 —— 以本表为准。

## 3. 更新流程（UpdateManager）

```
check() → GET https://api.github.com/repos/polite0803/AxTV/releases/latest
        → ReleaseJson 解析 tag_name / body / assets[]（name / browser_download_url / digest）
        → 版本比较（2.3.43 → 43）
        → 下载：UpdateMirror 依次回退 9 个镜像
             ghproxy.net → ghproxy.cn → gh-proxy.com → gh-proxy.org → gh-proxy.ygxz.in
             → mirror.ghproxy.com → gh.api.99988866.xyz → gh.cc → github.moeyy.xyz
        → 校验：SHA-256（digest）
        → 安装：FileProvider + ACTION_INSTALL_PACKAGE
```

## 4. 崩溃兜底（CrashHandler）

报告字段（顺序原样）：`━━ AxTV 崩溃报告 ━━` / 时间 `yyyy-MM-dd HH:mm:ss` / 设备 `BRAND MODEL` /
`应用版本 2.3.43 (43)` / `Android RELEASE (API n)` / 线程名 / `━━ 异常信息 ━━` / 异常类 + message /
根因（若有）/ `━━ 堆栈跟踪 ━━` / 完整栈 → `CrashActivity` 的 `crash_info` extra → `sleep(500)` →
`Process.killProcess(myPid())`。

## 5. 待迁移（Android 侧）

| 接口 | 实现 |
|---|---|
| `SettingsStore` | `SharedPreferences("douyin_auth")` 包装（`getString/putString`，同步 `apply()`） |
| `UpdateManager` 的安装步骤 | `FileProvider.getUriForFile` + `Intent.ACTION_INSTALL_PACKAGE` + `REQUEST_INSTALL_PACKAGES` |
| `UpdateManager` 的下载 | OkHttp/HttpURLConnection；原实现写到 `cacheDir` 后校验 digest |
| `CrashHandler` 的启动 | `Thread.setDefaultUncaughtExceptionHandler`（在 `Application.onCreate` 里安装） |

## 6. 存疑

1. `fl4` 有 1138 行，其中一部分是 UI 侧便捷访问器（`ll4` 转发）；本模块按"键 → 强类型访问器"
   重建，个别只在 UI 里出现的派生值（如按屏幕密度换算的默认字号）留在 `Settings` 的注释里，未硬编码。
2. `feed_history_capacity` 等键的取值上界在原文里是散落的 `coerceIn`，已按可见的上/下界夹取；
   若真机行为不同以实测为准。
3. `license_*` 与 `last_announcement_id` 关联的公告/协议弹窗流程属 UI 层，本轮只还原存储语义。
