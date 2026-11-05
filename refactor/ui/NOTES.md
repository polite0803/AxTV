# UI 行为规格模块（`axtv.ui`）—— 第一批：设置页 / 画质选择器

> **口径**：用户要求「功能一条都不能少」。Compose 的像素绘制无法用纯 JDK 表达，
> 但「有哪些控件、什么类型、什么顺序、绑定哪个配置键、显示什么文案、改了之后联动什么」
> 全部以可执行数据规格落地，不写占位符、不臆造控件。
>
> 关键实证：反编译产物里 `of2Var.m8567Z(key, type, composable)` 是设置页的控件注册调用，
> `qm4.java` 里 **62 项**、`j16.java` 弹幕样式面板里 **15 项**，共 **77 项**，
> 每项都有源码里的 key + type，顺序也可还原。本模块把它们逐条登记。

## 1. 类映射表

| 原文件 | 行数 | 原职责 | 新类 |
|---|---:|---|---|
| `src/app/p000/qm4.java` | 7.3k | 设置页主 Composable（含画质/编码/账号/关于/主题/播放开关） | `SettingsPageSpec.MAIN_PAGE` |
| `src/app/p000/j16.java` | 2.75k | R8 合并类；其中 1084-1356 行是弹幕样式设置面板 | `SettingsPageSpec.DANMAKU_STYLE_PANEL` |
| `src/app/p000/qm4.java:2143-2199` | — | 直播清晰度 5 档的 label + 描述文案 | `SettingsPageSpec.LiveQualityOption` |
| `src/app/p000/qm4.java:2281-2283` | — | 点播清晰度 ordinal 0/1 的描述文案 | `SettingsPageSpec.VideoQualityOption` |
| `src/app/p000/lq5.java` | 60 | 点播清晰度枚举（jadx 枚举还原失败，但构造参数字符串可读） | `SettingsPageSpec.VideoQualityOption` |
| `src/app/p000/qm4.java:2215-2255` | — | `video_resolution` / `live_video_resolution` 写盘与联动（smali 片段） | `SettingsPageSpec.applyResolutionChange` |

## 2. 主设置页控件表（62 项，顺序 = 源码注册顺序）

「分组」列取上一个 `section_header` 的 key；`—` 表示从页面开始就未遇到 header。

| # | key | type | 分组 |
|---:|---|---|---|
| 1 | `settings_title` | `title` | — |
| 2 | `settings_account` | `account_card` | — |
| 3 | `live_resolution_header` | `section_header` | live_resolution_header |
| 4 | `danmaku_header` | `section_header` | danmaku_header |
| 5 | `video_danmaku` | `switch_row` | danmaku_header |
| 6 | `live_danmaku` | `switch_row` | danmaku_header |
| 7 | `live_danmaku_style` | `animated_enum_row` | danmaku_header |
| 8 | `danmaku_emoji` | `animated_switch_row` | danmaku_header |
| 9 | `danmaku_blocklist` | `switch_row` | danmaku_header |
| 10 | `danmaku_layout` | `danmaku_layout_controls` | danmaku_header |
| 11 | `auth_status_header` | `section_header` | auth_status_header |
| 12 | `auth_status` | `auth_status_card` | auth_status_header |
| 13 | `account_management_header` | `section_header` | account_management_header |
| 14 | `account_management` | `account_management` | account_management_header |
| 15 | `auth_qr_header` | `section_header` | auth_qr_header |
| 16 | `auth_login_mode` | `auth_login_card` | auth_qr_header |
| 17 | `auth_cookie_header` | `section_header` | auth_cookie_header |
| 18 | `auth_cookie` | `auth_text_field` | auth_cookie_header |
| 19 | `auth_security_header` | `section_header` | auth_security_header |
| 20 | `auth_security_status` | `auth_security_card` | auth_security_header |
| 21 | `auth_security_payload` | `auth_text_field` | auth_security_header |
| 22 | `auth_private_key` | `auth_text_field` | auth_security_header |
| 23 | `auth_config_actions` | `auth_config_actions` | auth_security_header |
| 24 | `about_update` | `action_button` | auth_security_header |
| 25 | `about_github` | `action_button` | auth_security_header |
| 26 | `about_telegram` | `action_button` | auth_security_header |
| 27 | `about_qr_links` | `qr_links_card` | auth_security_header |
| 28 | `about_version` | `app_version` | auth_security_header |
| 29 | `form_factor_header` | `section_header` | form_factor_header |
| 30 | `form_factor_note` | `note` | form_factor_header |
| 31 | `app_font_scale` | `enum_row` | form_factor_header |
| 32 | `default_home` | `enum_row` | form_factor_header |
| 33 | `theme_mode` | `enum_row` | form_factor_header |
| 34 | `color_source_header` | `section_header` | color_source_header |
| 35 | `color_style` | `enum_row` | color_source_header |
| 36 | `color_contrast` | `enum_row` | color_source_header |
| 37 | `ambient_motion_header` | `section_header` | ambient_motion_header |
| 38 | `ambient_backdrop` | `switch_row` | ambient_motion_header |
| 39 | `color_transition` | `enum_row` | ambient_motion_header |
| 40 | `video_resolution_header` | `section_header` | video_resolution_header |
| 41 | `video_codec_header` | `section_header` | video_codec_header |
| 42 | `filter_mobile_video` | `switch_row` | video_codec_header |
| 43 | `pip_enabled` | `switch_row` | video_codec_header |
| 44 | `reverse_navigation` | `switch_row` | video_codec_header |
| 45 | `reverse_profile_playlist` | `switch_row` | video_codec_header |
| 46 | `profile_history_tab` | `switch_row` | video_codec_header |
| 47 | `disable_double_back_exit` | `switch_row` | video_codec_header |
| 48 | `show_details_on_start` | `switch_row` | video_codec_header |
| 49 | `player_progress_bar` | `switch_row` | video_codec_header |
| 50 | `clean_pause` | `switch_row` | video_codec_header |
| 51 | `tv_launcher_channel` | `switch_row` | video_codec_header |
| 52 | `player_surface_type` | `enum_row` | video_codec_header |
| 53 | `surface_buffer_align` | `switch_row` | video_codec_header |
| 54 | `audio_balance` | `enum_row` | video_codec_header |
| 55 | `playback_clock` | `enum_row` | video_codec_header |
| 56 | `playback_completion` | `enum_row` | video_codec_header |
| 57 | `video_load_timeout` | `enum_row` | video_codec_header |
| 58 | `separate_video_fetch` | `switch_row` | video_codec_header |
| 59 | `video_start_prefetch` | `switch_row` | video_codec_header |
| 60 | `mix_playlist_panel` | `switch_row` | video_codec_header |
| 61 | `featured_auto_play` | `switch_row` | video_codec_header |
| 62 | `feed_history_capacity` | `enum_row` | video_codec_header |

> 注意 40/41 两个 header 连续出现：画质与编码选项是在 header 之后**动态插入**的枚举行
> （即 §4 的两套档位），不通过 `m8567Z` 注册，因此不在 62 项里。
> 24-28 是关于页卡片，源码中它们排在 `auth_security_header` 之后但没有独立 header，
> 本表按「上一个 header」如实登记。

### 控件类型分布（qm4 62 项）

| 类型 | 数量 | 类型 | 数量 |
|---|---:|---|---:|
| `switch_row` | 19 | `section_header` | 12 |
| `enum_row` | 12 | `auth_text_field` | 3 |
| `action_button` | 3 | 其余 13 种各 1 | 13 |

其余 13 种：`title` / `account_card` / `animated_enum_row` / `animated_switch_row` /
`danmaku_layout_controls` / `auth_status_card` / `account_management` / `auth_login_card` /
`auth_security_card` / `auth_config_actions` / `qr_links_card` / `app_version` / `note`
（唯一一条 `note` 的控件 key 是 `form_factor_note`）。

qm4 共 **18 种** type，j16 另有 **8 种**（`style-header` / `slider` / `option` /
`header` / `quality` / `camera` / `action` / `switch`），合并去重 **26 种**，
与 `SettingsPageSpec.ControlType` 的 26 个枚举值一一对应。

## 3. 弹幕样式面板控件表（15 项，`j16.java:1084-1356`）

| # | key | type | 说明 |
|---:|---|---|---|
| 1 | `style_header` | `style-header` | 面板标题 |
| 2 | `style_opacity` | `slider` | 透明度 → `danmaku_opacity` |
| 3 | `style_area` | `slider` | 显示区域 → `danmaku_area_percent` |
| 4 | `style_vspacing` | `slider` | 行间距 → `danmaku_vertical_spacing` |
| 5 | `style_hspacing` | `slider` | 水平间距 → `danmaku_horizontal_spacing` |
| 6 | `style_font` | `option` | 字号 → `danmaku_font_size` |
| 7 | `style_speed` | `option` | 速度 → `danmaku_speed` |
| 8 | `header` | `header` | 第二组标题 |
| 9 | `quality` | `quality` | 画质入口 |
| 10 | `camera` | `camera` | 取色/预览相机 |
| 11 | `profile` | `action` | 配置管理入口 |
| 12 | `danmaku` | `switch` | 弹幕总开关 → `danmaku_enabled` |
| 13 | `filter_package` | `switch` | 过滤包 → `danmaku_filter_package` |
| 14 | `blocklist` | `switch` | 屏蔽词 → `danmaku_block_enabled` / `danmaku_block_words` |
| 15 | `style` | `action` | 样式配置操作（保存/删除/推送） |

这 6 个 `style_*` 参数与 `axtv.live.DanmakuStyle` 的 6 个输入一一对应
（opacity / displayArea / verticalSpacing / horizontalSpacing / fontSize / speed），
说明**设置页 → 弹幕几何算法**的链路是完整闭合的。

## 4. 画质选项

### 4.1 点播（原 `lq5` 枚举 / `VideoResolution` / `TargetResolution`）

| ordinal | label | prefValue | 短标签 | 描述文案（qm4:2281） |
|---:|---|---|---|---|
| 0 | 自动 | `auto` | 自动 | 按服务端自动匹配 |
| 1 | 最高画质 | `highest` | 最高 | 优先选择可用最高档位 |
| 2 | 高清 1080p | `1080p` | 1080P | — |
| 3 | 标清 720p | `720p` | 720P | — |
| 4 | 流畅 540p | `480p` | 540P | — |

> `prefValue=480p` 但显示「540P」是原实现的命名，逐字保留。
> 取流规则见 `model/VideoPlayUrlResolver.TargetResolution`（按 `gear_name` 含 1080/720/540 匹配）。

### 4.2 直播（qm4:2143-2199 的 5 个 ordinal 分支）

| ordinal | label | 描述文案 |
|---:|---|---|
| 0 | 自动 | 最高的 H.264 挡位，FLV 起播更稳（推荐） |
| 1 | 最高画质 | 无条件原画，HEVC 直播走 HLS，低端设备可能卡顿 |
| 2 | 高挡位 | 按直播间可用挡位偏高选择 |
| 3 | 中挡位 | 按直播间可用挡位居中选择 |
| 4 | 低挡位 | 直播间最低挡位，最流畅 |

### 4.3 联动规则（qm4:2215-2255 的 smali 片段 + `Settings.liveVideoResolution()`）

```
用户选 point → 写 SharedPreferences("video_resolution") = prefValue
             → 遍历枚举按 prefValue 反查，找不到回退 AUTO
             → 更新点播 StateFlow (fl4.f6264m)
             → 若 prefs 不含 "live_video_resolution"：
                  直播 StateFlow (fl4.f6268o) 跟随点播值；
                  点播 == AUTO 时直播改成 HIGHEST（「自动」在直播里不合法）
```

## 5. 71 个配置键 → UI 控件对照（零删减核对）

「精确」= 源码里能直接看到绑定；「推定」= 按 key 命名与分组对齐，需 UI 层复核。

| 配置键 | UI 控件 | 判定 |
|---|---|---|
| `accounts_list` / `current_account_id` | `account_management`, `settings_account` | 推定 |
| `self_user_id` / `user_unique_id` | `settings_account`, `auth_status` | 推定 |
| `ambient_backdrop` | `ambient_backdrop` | 精确 |
| `app_font_scale` | `app_font_scale` | 精确 |
| `audio_balance_level` | `audio_balance` | 推定 |
| `auto_play_next_video` | `playback_completion` | 推定 |
| `clean_pause` | `clean_pause` | 精确 |
| `color_contrast` | `color_contrast` | 精确 |
| `color_source` | `color_source_header` / `color_style` | 推定 |
| `color_style` | `color_style` | 精确 |
| `color_transition` | `color_transition` | 精确 |
| `cookie_string` | `auth_cookie` | 精确 |
| `danmaku_area` / `danmaku_area_percent` | `style_area`, `danmaku_layout` | 推定 |
| `danmaku_block_enabled` / `danmaku_block_words` | `blocklist`, `danmaku_blocklist` | 推定 |
| `danmaku_emoji_enabled` | `danmaku_emoji` | 精确 |
| `danmaku_enabled` | `danmaku` | 精确 |
| `danmaku_filter_package` | `filter_package` | 精确 |
| `danmaku_font_size` | `style_font` | 精确 |
| `danmaku_horizontal_spacing` | `style_hspacing` | 精确 |
| `danmaku_opacity` | `style_opacity` | 精确 |
| `danmaku_speed` | `style_speed` | 精确 |
| `danmaku_vertical_spacing` | `style_vspacing` | 精确 |
| `default_home_screen` | `default_home` | 推定 |
| `disable_double_back_exit` | `disable_double_back_exit` | 精确 |
| `douyin_auth` / `remote_config` | SharedPreferences 文件名（非 UI 项） | — |
| `dynamic_color` / `global_seed_argb` | `color_source_header` | 推定 |
| `featured_auto_play` | `featured_auto_play` | 精确 |
| `feed_history_capacity` | `feed_history_capacity` | 精确 |
| `filter_mobile_video` | `filter_mobile_video` | 精确 |
| `interaction_private_key` | `auth_private_key` | 精确 |
| `interaction_security_payload` | `auth_security_payload` | 精确 |
| `interaction_server_cert` / `interaction_ticket` / `interaction_ts_sign` | `auth_security_status` | 推定 |
| `last_announcement_id` | `about_update`, `about_version` | 推定 |
| `license_agreed` / `license_agreed_version` / `license_required_version` | `about_update`（License 弹窗） | 推定 |
| `live_danmaku_enabled` | `live_danmaku` | 精确 |
| `live_danmaku_style` | `live_danmaku_style` | 精确 |
| `live_video_resolution` | `live_resolution_header` 下的动态枚举行 | 精确 |
| `mix_playlist_panel_enabled` | `mix_playlist_panel` | 推定 |
| `mobile_feed_device_id` / `mobile_feed_install_id` | 无 UI（内部持久化） | — |
| `open_own_profile_on_history_tab` | `profile_history_tab` | 推定 |
| `pip_enabled` | `pip_enabled` | 精确 |
| `playback_clock_display_mode` | `playback_clock` | 推定 |
| `playback_completion_action` | `playback_completion` | 推定 |
| `playback_speed` | 播放器菜单（不在设置页） | — |
| `player_progress_bar` | `player_progress_bar` | 精确 |
| `player_surface_type` | `player_surface_type` | 精确 |
| `reverse_navigation` | `reverse_navigation` | 精确 |
| `reverse_playlist_in_profile` | `reverse_profile_playlist` | 推定 |
| `separate_video_fetch` | `separate_video_fetch` | 精确 |
| `show_details_on_start` | `show_details_on_start` | 精确 |
| `surface_buffer_align` | `surface_buffer_align` | 精确 |
| `theme_mode` | `theme_mode` | 精确 |
| `tv_launcher_channel_enabled` | `tv_launcher_channel` | 精确 |
| `ui_form_factor` | `form_factor_header` / `form_factor_note` | 推定 |
| `video_codec_preference` | `video_codec_header` 下的动态枚举行 | 精确 |
| `video_danmaku_enabled` | `video_danmaku` | 精确 |
| `video_load_timeout_seconds` | `video_load_timeout` | 推定 |
| `video_resolution` | `video_resolution_header` 下的动态枚举行 | 精确 |
| `video_start_prefetch` | `video_start_prefetch` | 精确 |
| `viewed_video_ids` | 无 UI（内部持久化） | — |

**结论：71 个键全部有归属** —— 要么有显式控件，要么在动态枚举行/播放器菜单，
要么是内部状态（4 项）或 SharedPreferences 文件名（2 项）。没有「配置存在但 UI 找不到入口」的孤立项。

## 6. 中文文案（37 条，逐字保留）

```
← → 选择，OK 进入 / 主播头像 / 删除 / 字体大小 / 弹幕速度 / 点按选择要使用的配置 /
直播设置 / 谁在看？ / GitHub 主页 / Telegram 群组 / 三米外观看时可以调大一档 /
中挡位 / 仅画面取色时生效 / 优先选择可用最高档位 / 低挡位 / 保存配置 / 加载视频 /
左右键调节 / 已保存 / 已设置 PIN / 当前配置 / 按服务端自动匹配 /
按直播间可用挡位偏高选择 / 按直播间可用挡位居中选择 / 推送到配置 / 文字大小 /
无条件原画，HEVC 直播走 HLS，低端设备可能卡顿 / 最高画质 /
最高的 H.264 挡位，FLV 起播更稳（推荐） / 添加新配置 / 清除 /
直播间最低挡位，最流畅 / 自动 / 色彩过渡动画 / 返回分类 /
页面颜色与电视一致 / 高挡位
```

## 7. 待迁移（Android 侧）

1. `m8567Z(key, type, composable)` → 按 `ControlType` 分派到对应 Composable；
   本模块只提供顺序、类型、分组与绑定，不接管绘制。
2. `switch_row` / `enum_row` / `slider` / `option` 的点击 lambda、焦点、遥控器左右键调节
   （提示文案「← → 选择，OK 进入」「左右键调节」）。
3. `auth_text_field` 的三个输入框与 Cookie/私钥归一化（见 `panel/CookiePushHandler`）。
4. 动态插入的画质/编码枚举行（§4 两套档位 + `video_codec_preference` 的 auto/h264/h265）。
5. 主题取色（`camera` 控件）、`color_style` / `dynamic_color` / `global_seed_argb` 的联动。
6. `danmaku_layout_controls` → 调 `axtv.live.DanmakuStyle` 实时预览。
7. `about_update` → `settings/UpdateManager`；`about_qr_links` → 二维码面板。
8. 账号管理 → `settings/Settings` 的多账号 API；`auth_login_card` → `panel/QrLoginBridge`。

## 8. 存疑

- **S1**：`qm4` 和 `j16` 都是 R8 合并类，`qm4` 里同一段 Composable 出现两遍
  （2143 段与 6169 段，Compose 编译器生成的重载），本表按 key 去重、保留首次出现顺序。
- **S2**：`qm4:2215-2255` 是 jadx 未能反编译、直接嵌入的 smali 片段；联动规则按该 smali
  与 `settings/Settings.liveVideoResolution()` 的 Java 实现交叉确认，可信度高。
- **S3**：点播与直播画质枚举的 ordinal 0/1 同名（自动 / 最高画质），源码里变量名都是
  `lq5Var2`（泛型擦除 + R8），本模块按两套枚举处理，需 UI 层复核是否为同一枚举的不同展示分支。
- **S4**：`video_resolution_header` / `video_codec_header` / `live_resolution_header` 之后
  没有 `m8567Z` 注册，档位是运行时按枚举动态生成；本模块的档位表来自 `lq5.java` 常量与
  `qm4` 的 ordinal 分支，未找到逐档位的 `onClick` 绑定代码（jadx 溢出），按命名对齐。
- **S5**：`j16` 的 `camera` / `quality` / `profile` / `style` 四个 action 控件具体跳转目标
  未反编译出来，只登记了 key 与 type。
- **S6**：控件 key → 配置键的「推定」项（§5）是按命名与分组对齐，未逐条看到 `putString` 调用；
  建议 UI 层接手时用真机切换设置 + 抓 SharedPreferences 复核。
- **S7（选集面板）**：`w03.java:70-75` 的三个布尔标志变量名被 R8 抹掉，
  只能确认「四档前景色」的分支结构（PRIMARY / NORMAL / ALTERNATE_DIM / DIMMED_60），
  不能确证哪个布尔对应「上次看到 / 已看过 / 聚焦」。`EpisodePickerSpec.foreground`
  因此按分支结构命名，不臆造业务含义；真机对照 HELP_TEXT 即可补齐语义。
- **S8（选集面板）**：`w03` 的选集网格单元格数量、列数、分页步长未逐字核
  （分页文案里能确认 `已加载第 a–b 集 / 共 N 集` 的格式），网格布局参数属绘制层。

## 9. 验证

`SettingsPageSpec` / `EpisodePickerSpec` 为纯数据 + 纯函数，`javac --release 17 -Xlint:all` 零错误。
可用断言核对：`MAIN_PAGE.size()==62`、`DANMAKU_STYLE_PANEL.size()==15`、
`ControlType` 覆盖 26 种 raw 值、`applyResolutionChange("auto", false)` 返回 `auto/highest`、
`metaLine` 的段顺序、`loadHint` 的两种分支。实际烟雾测试见 `refactor/TESTS.md`。

---

## 10. 选集面板行为规格（`EpisodePickerSpec`）

来源 `src/app/p000/w03.java`（2513 行）。COVERAGE 第 35 行「选集面板」的行为 100% 落地，
只差 Compose 网格绘制。

### 10.1 状态与视觉

| 项 | 规则 | 源码行 |
|---|---|---|
| 已看过弱化 | 前景色取 `x70.m11833c(f11914s, 0.6f)`，即 **60% alpha** | 75 |
| 上次看到高亮 | 帮助文案明确承诺「上次看到的一集高亮」，四档前景色见 `Foreground` 枚举 | 357 / 70-75 |
| 单元格尺寸 | 高 44dp、圆角 12dp | 78 |
| 失效态 | 标题「合集内容已失效」+ 提示「请返回重试」 | 1379 / 1559 / 1663 |
| 加载态 | 无 status 文本且正在加载 → 「正在加载该集…」，否则空串 | 483 / 783 |
| 空标题 | 「未命名合集」 | 2085 |
| 帮助文案 | 「已看过的集弱化显示，上次看到的一集高亮。选中任意一集，从这一集连播。」 | 357 / 657 |
| 点击语义 | 选中任意一集 → 从该集起连播（返回起始序号交给 `PlaybackQueue`） | HELP_TEXT |

### 10.2 文案格式全表

| 场景 | 格式 | 源码行 |
|---|---|---|
| 面板标题 | `选集 · 共 {N} 集` | 352 / 2160 |
| 元信息行 | 各段用 ` · ` 连接，顺序：`共 {max(total,updatedTo,current)} 集` → `已看到第 {N} 集` → `更新至 {X}` → `{formatCount(playVv)} 次播放` → 附加文案 | 2020-2036 |
| 主按钮（有进度） | `继续第 {N} 集` | 2143 |
| 主按钮（无进度） | `从第 {N} 集播放` | 2152 |
| 分页（已加载区间非空） | `已加载第 {from}–{to} 集 / 共 {total} 集` | 2468 |
| 分页（未加载） | `共 {total} 集` | 2468 |
| 其它 | `取消` / `合集` / `N 集` / `N 集 / 共 N 集` | 中文串表 |

### 10.3 中文文案（18 条，逐字）

```
 次播放 /  集 / 共  /  集 /  集播放 / 从第  / 共  / 取消 / 合集 /
合集内容已失效 / 已加载第  / 已看到第  /
已看过的集弱化显示，上次看到的一集高亮。选中任意一集，从这一集连播。 /
更新至  / 未命名合集 / 正在加载该集… / 继续第  / 请返回重试 / 选集 · 共 
```

> 「展示总集数」取 `max(totalEpisode, updatedToEpisode, currentEpisode)` 三个字段的最大值，
> 不是单取 `totalEpisode` —— 连载中的合集以 `currentEpisode` 为准。

### 10.4 待迁移（Android 侧）

1. Compose 网格（`LazyVerticalGrid` 之类）与单元格点击/焦点处理。
2. `MixInfo.getStatis()` → `EpisodePickerSpec.MixMeta` 的适配。
3. `DataModels.formatCount(playVv)` 的调用（本模块接收已格式化字符串）。
4. `onEpisodeSelected(index)` → `PlaybackQueue` 从该序号起连播；追更记录写回 `CollectionApi.recordMixProgress`。
5. 分页加载触发点（`/aweme/v1/web/mix/aweme/` 的 `cursor` / `count`）。

---

## 11. 第三批行为规格（评论行 / 互动按钮 / 搜索状态）

四个纯函数规格类（含 DateLabels），均为 `javac 17 -Xlint:all` 零错误，烟雾测试覆盖（TESTS.md「第三批 UI 规格」15 项）。

### 11.1 `CommentUiSpec`（来源 `cb0.java`，823 行 —— 评论项渲染）

| 行为 | 还原点 |
|---|---|
| 相对时间五分支 | `cb0:147-160`：&lt;60s「刚刚」、&lt;1h「N分钟前」、&lt;1d「N小时前」、&lt;**30d**（2_592_000_000ms）「N天前」、超限走调用方 `SimpleDateFormat`；`create_time<=0` 返回空串不显示 |
| 匿名兜底 | `cb0:170`：昵称 blank → 「匿名用户」 |
| 作者行 | `cb0:186-195`：`昵称` + 可选 ` · 时间`（时间空不拼接，分隔符 `" · "`） |
| LazyList key | `m2151g`(797-823)：cid blank → `missing:<uidLen>:<uid>:<createTime>:<textLen>:<text>`，否则 `id:<cidLen>:<cid>`；再 `parentKey + ":" + base`，同 key 第 N 次出现追加 `:N`（冲突计数表） |
| key 前缀 | 顶层 `"comment"`（cb0:396）；回复 `"reply:<顶层key长度>:<顶层key>"`（cb0:408，长度编进前缀是原防碰撞手段） |
| 回复展开 | `m2148d`(391-412)：顶层全入列；仅选中 key 且 cid 不在两个排除 Set 才展开内嵌回复（`replyComments==null` → 空列表） |
| 点赞数 | `diggCount > 0` 才显示，`formatCount` |

### 11.2 `InteractionBarSpec`（来源 `fv4.java` 互动按钮段）

| 行为 | 还原点 |
|---|---|
| 状态文案 | `fv4:218/229`：点赞/已点赞、收藏/已收藏，计数**恒显示**（0 也显示，`formatCount`） |
| 激活高亮 | 激活 → 主题令牌 `k80.f11899d`，普通 → `k80.f11912q`（色值归 Android 侧） |
| 尺寸双态 | `m4264a:70-82`：高 56/36dp、图标 16/10dp、圆角 12/8dp、水平 padding 20/12dp（large/compact） |
| 静态统计行 | `fv4:591/592/610/611`（m4275l）：点赞/评论/收藏/分享四行，无状态切换 |

**边界**：fv4 其余（合集信息卡 `m4268e`、作者信息行、即将播放卡 `m4271h`、播放菜单标签）
与 lq2/eu5 耦合，归对应轮次，见下表。

### 11.3 `SearchUiSpec`（来源 `ye4.java` 264-286 + 空态文案）

| 行为 | 还原点 |
|---|---|
| 业务码三特殊分支 | `m12087y`：`params_check`→「搜索参数校验失败」、`web_need_login`→「搜索需要登录态，请更新 Cookie」、`verify_check`→「搜索被风控校验拦截，请稍后重试」，其余 → 「搜索错误: &lt;code&gt;」 |
| 非业务异常 | `&lt;前缀&gt; + "搜索失败: " + message`（m9803C 裸拼接，综合 Tab 前缀 `""`） |
| 频道前缀 | 用户/直播 Tab 传 `"用户"`/`"直播"`（787/879） |
| 频道 catch 文案 | `717/722/932/937`：「用户搜索异常: 」/「直播搜索异常: 」+ message |
| 空结果 | 670「未找到相关视频」（仅首次加载且空）/ 788+1108+1658 用户 / 880+1211+1751 直播 |
| rc4 抛出点 | `kq5:557`（status_code≠0 → 数字码）、`kq5:575-585`（空结果且 `searchNilInfo.searchNilType` 非空 → 语义码）、`n11:4894/4937/5155/5190` |

### 11.4 `DateLabels`（来源 eg5.java 日期段 690-720）

| 行为 | 还原点 |
|---|---|
| medium | `m3650X`：同年「M月d日」、跨年「yyyy年M月d日」；≤0 → null（调用点 eu5:4156 / w03:1895） |
| shortLabel | `m3651Y`：`String.format(Locale.US, "%02d-%02d", month, day)` |
| yearMonth | `m3652Z`：(year, month+1) 对（原 tn5）；≤0 → null |
| 时区 | 设备本地默认（原 Calendar.getInstance() 无参） |

**nu1 结论**：APK 下载校验（进度两分支/SHA-256/镜像 300s 过期/空串镜像跳过）已全部被 `settings/UpdateManager` + `UpdateMirror` 覆盖，无需新文件。

### 11.5 UI 类覆盖边界（防重复劳动）

| 原类 | 已落 `refactor/ui/` | 留给后续轮次 |
|---|---|---|
| `qm4`(7.3k) | 77 控件 + 画质 10 档（SettingsPageSpec） | 页面绘制 |
| `j16`(2.75k) | 弹幕样式 15 控件（SettingsPageSpec） | 页面绘制 |
| `w03`(2.5k) | 选集面板（EpisodePickerSpec） | 页面绘制 |
| `cb0`(823) | 评论行（CommentUiSpec）✅ 全量 | — |
| `fv4`(1.5k) | 互动按钮行（InteractionBarSpec） | 合集卡/作者行/即将播放卡 → lq2/eu5 轮 |
| `ye4`(2.2k) | 错误/空态（SearchUiSpec） | 页面绘制（最近搜索/热搜/分栏） |
| `lq2`(22.4k) | 菜单/滑杆/段选/快捷菜单（PlayerMenuSpec + PlayerGestureSpec + PlayerUiPalette，见 NOTES-player-ui.md） | 按键状态机/控制层/进度条 → **on5**（子任务实证 lq2 无 KeyEvent 分发） |
| `un2`(2.4k) | 直播页行为（LivePageSpec：分辨率轨/错误两值/连播状态机）✅ 全量 | 分区浏览 he4、页面绘制 |
| `on5`(10.5k) | 焦点路由/控制层显隐/重播复位/评论分页/合集面板（PlayerKeyMachine + ControlLayerSpec + FeedPageSpec，见 NOTES-on5.md） | m8689H jadx 溢出、页面绘制；按键分发结案：无集中 DPAD 表（Activity 层仅转发，走 Compose 焦点系统） |
| `qg5`(7.6k) | 精选/分区列表（ChannelListSpec + NOTES-qg5.md：分区五态机/分页双阈值/预取/重播倒计时） | 页面绘制；按键存疑已结案（smali：C0412be=R8 lambda 合并类） |
| `i42`(8.1k) | Tab 容器 + 退出状态机（HomeTabsSpec：四 Tab↔ab4/刷新文案/双击返回 2000ms） | Compose 修饰符工厂段（约半数）、页面绘制 |
| `mw3`(5.9k) | 观看历史按月分组（HistoryGroupSpec：回退链/复合键/展平/文案）+ 扫码推送文案 | 合集页其余行为、页面绘制 |
| `eu5`(11.6k) | 详情页+直播分区（DetailPageSpec + NOTES-eu5-he4.md：封面卡/分区四态/选台/空态） | m3844n jadx 溢出待 smali、绘制；约 6700 行 R8 噪音已标注 |
| `he4`(2.2k) | 直播分区浏览（LiveBrowseSpec：搜索五态/去重键/列数）✅ 全量 19 方法 | 页面绘制 |
