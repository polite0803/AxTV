# 重构验证记录

> 目的：证明「功能零删减」不只是文档承诺，而是有可执行验证的。
> 所有测试用纯 JDK 17 跑，不需要 Android SDK / Gradle / 模拟器。

## 1. 全量编译

```bash
cd D:/OneManager/AxTV
find refactor -name '*.java' > .workbuddy/src.txt
"C:/Program Files/Microsoft/jdk-17.0.16.8-hotspot/bin/javac.exe" \
  -J-Duser.language=en --release 17 -Xlint:all -encoding UTF-8 \
  -d .workbuddy/build-final @.workbuddy/src.txt
```

判据：`error:` 为 0。各模块单独编译也各自通过
（`sign` / `model` / `panel` / `player` 由各模块负责人验证过 `-Xlint:all` 零告警）。

## 2. 行为烟雾测试

> **（2026-11-05 更新）本节为历史验证记录；这 147 项断言已全部工程化为 JUnit 5 测试，见第 3 节。**
> `.workbuddy/SmokeTest.java` 保留作交叉验证基准，不再作为主测试入口。

脚本：`.workbuddy/SmokeTest.java`（临时验证文件，不属于交付源码）。
用内存假实现驱动 `TvProvider`、`MainActivityModel.Host`、`WidgetStorage` 等接口，
不需要任何 Android 类型。

```bash
JAVAC="C:/Program Files/Microsoft/jdk-17.0.16.8-hotspot/bin/javac.exe"
JAVA="C:/Program Files/Microsoft/jdk-17.0.16.8-hotspot/bin/java.exe"
$JAVAC -J-Duser.language=en --release 17 -encoding UTF-8 \
  -cp .workbuddy/build-final -d .workbuddy/build-final .workbuddy/SmokeTest.java
$JAVA -Dfile.encoding=UTF-8 -cp .workbuddy/build-final SmokeTest
```

**当前结果：147 项全通过。**

| 覆盖域 | 项数 | 关键用例 |
|---|---:|---|
| deep link / 路由 | 7 | `aweme_id` 纯数字校验与 `removeExtra` 副作用、`/video/(\d+)` 正则、extra 优先、`Uri.encode`、路由参数解析、13 条路由 |
| 遥控器按键 | 5 | 媒体键 88→19 且 `scanCode=0`、未命中不改写、7 条重映射、10 条焦点方向、确认键集合 |
| PIP | 6 | ENTER_PIP 判定、1780/1000 比例、clamp 下限、非视频态回退 16:9、息屏暂停、SDK<26 不动作 |
| SDK 列裁剪 | 2 | `applySdkFilter(25)` 移除 `channel_id`/`weight`/`browsable`、SDK33 只删 browsable |
| 节目构造 | 6 | 全字段、无封面丢弃、空 id 丢弃、空标题回退、权重下限 1、最多 30 条 |
| EPG 同步 | 10 | SKIPPED / NO_CHANNEL / 创建频道 + browsable / 频道显示名 / 插入 3 条 / 重复同步零写入 / 字段变化 1 次更新 / 删 2 条 / 空列表清空 / 无 provider 跳过 |
| 同步触发 | 5 | 推荐只发一次、稍后再看每次变化（含空）、开关关闭静默、开关重开补发 |
| 设置页规格 | 11 | 62 + 15 控件、类型可解析、26 种类型、13 分组、点播/直播各 5 档、480p→540P、AUTO 联动 HIGHEST、单独设直播不联动、37 条文案 |
| 选集面板规格 | 7 | 0.6 alpha、四档前景色分支、总集数取最大值、元信息行段顺序、主按钮两分支、分页两分支、失效/加载态文案 |
| 小组件规格 | 10 | 5 种周期 kind（360/30/60 分钟）、非法名返回 null、宽版封面 224×126→448×252、缓存键七段格式、像素下限 24、solid 键格式、位掩码 224、中心裁剪系数、空白 URL 判定 |
| 第三批 UI 规格 | 20 | 相对时间五分支/时间空不显示、匿名兜底、lazyKey 两格式+冲突计数、回复前缀含长度、未选中不展开/选中展开、点赞收藏文案切换+1.2万计数、静态统计行、搜索三特殊码、数字码默认、频道前缀、空态三文案、日期同年省年/跨年含年、MM-dd、年月对、<=0 全 null |
| 播放器 UI 规格 | 12 | 480p→540P、倍速 2×/合法性、播放完后存储键、弹幕行右值(关闭=null)、屏蔽词 N条、行前景禁用降透明、段选圆角 12/4、快捷项处理中优先、倍速提示条逐字、间距一位小数、滑杆 clamp |
| 直播页规格 | 5 | 分辨率标签/零尺寸 null、错误两值固定、连播切下一个/最后一个结束消息、在线人数标签 |
| on5 页面规格 | 11 | 返回键焦点路由/浮层守卫、可播判定、简介二段返回(滚顶→不关/滚顶后→关)、mm:ss、网速 KB/MB 边界、发布时间阈值自适应、合集标题、相关卡集数标、上滑加载更多、倍速档 0.5-3.0 |
| qg5 分区规格 | 8 | 分区五态机(占位/加载/错误/空/内容)、精选页三态、计数标签、列数 2/3/4、分页双阈值 size-5/size-3、3 分组预取定位、重播倒计时两档、分区位置文案 |
| 首页/历史规格 | 14 | 四 Tab 刷新文案/不同 Tab 切换 null、可退出判定(精选需开关)、双击返回 2000ms 窗口、确认浮层条件、再按一次 toast、配置切换标签、退出序列、观看时刻回退链、复合键 year*100+month、按月分组展平(相邻变化插 header)、header 文案、加载占位、月单元格右值 |
| 详情/搜索规格 | 8 | 封面比例双态 0.5625/1.7778、时长 M:SS、标题三级兜底、分区状态四分支、搜索 body 五态、隐藏竖屏保留横屏、去重键两格式+result 键、TV 紧凑列数 |

## 3. JUnit 5 工程化测试（refactor/tests/，2026-11-05）

SmokeTest 的 **147 项断言逐条**拆分为 **22 个 JUnit 5 测试类**，位于
`refactor/tests/src/test/java/axtv/test/`（另有共享夹具 `TestFixtures`：Aweme 工厂 + 内存假 TvProvider）。

构建形态：`refactor/` 是 Gradle 根工程，`:tests` 模块的 **main 源集直接指向 11 个既有源码目录**
（`api/ app/ live/ model/ panel/ player/ settings/ sign/ tv/ ui/ widget/`）——
不移动、不复制任何 java 文件，与 android/ 壳工程、javac 全量编译共用同一份源码。
（不按 11 个目录拆 Gradle 子模块的原因：`api ↔ live ↔ sign` 存在依赖环。）

**运行方式（需 JDK 17，无需 Android SDK / 模拟器）：**

```bash
cd refactor
./gradlew :tests:test        # Windows 用 gradlew.bat；或直接 ./run-tests.sh / run-tests.bat
```

**当前结果：147 tests / 0 failures / 0 errors。**
报告：JUnit XML `tests/build/test-results/test/`，HTML `tests/build/reports/tests/test/`。

| 测试类 | 项数 | 覆盖域 |
|---|---:|---|
| AppRoutesTest | 7 | deep link / 路由 |
| RemoteKeyMapTest | 5 | 遥控器按键 |
| PipSpecTest | 6 | 画中画 |
| TvProviderColumnsTest | 2 | SDK 列裁剪 |
| ProgramBuilderTest | 6 | 节目构造 |
| TvChannelPublisherTest | 10 | EPG 同步 |
| TvSyncTriggerTest | 5 | 同步触发 |
| SettingsPageSpecTest | 11 | 设置页规格 |
| EpisodePickerSpecTest | 7 | 选集面板规格 |
| WidgetSpecTest | 10 | 小组件规格 |
| CommentUiSpecTest | 9 | 评论区 |
| InteractionBarSpecTest | 2 | 互动栏 |
| SearchUiSpecTest | 4 | 搜索页 |
| DateLabelsTest | 5 | 日期标签 |
| PlayerMenuGestureTest | 12 | 播放器 UI 规格 |
| LivePageSpecTest | 5 | 直播页规格 |
| PlayerKeyControlFeedTest | 11 | on5 页面规格 |
| ChannelListSpecTest | 8 | qg5 分区规格 |
| HomeTabsSpecTest | 7 | 首页 Tab |
| HistoryGroupSpecTest | 7 | 历史按月分组 |
| DetailPageSpecTest | 4 | 详情页规格 |
| LiveBrowseSpecTest | 4 | 直播浏览/搜索 |
| **合计** | **147** | 与第 2 节 SmokeTest 逐项对应 |

CI：`.github/workflows/refactor-tests.yml` —— push/PR 触及 `refactor/**` 时自动跑
`./gradlew :tests:test --no-daemon`，并把测试报告上传为 artifact（推送到 GitHub 后生效）。
迁移时踩坑一处：「空列表清空频道」用例须先同步到只剩 1 条节目再清空（deleted==1），
与原 SmokeTest 场景一致，直接从 3 条清空会得到 deleted==3。

## 4. 各模块负责人的独立验证

| 模块 | 验证方式 | 结果 |
|---|---|---|
| `sign/` | 假 `JsRuntime` 跑脚本拼装、`m10093e` 四种分支、live 参数串、MD5 | 与 `C0761j:81` / `no4:87` 逐字一致 |
| `model/` | 临时 main 跑 6 组选流用例 | 全部符合，含「强制 H264 跳过 HEVC 1080 选 720」 |
| `panel/` | 31 项本地自测 | 全通过（9978 全部路由 / 9979 / 9980 帧 / 私钥归一化三分支 / 模板替换） |
| `player/` | `javac -Xlint:all` | 零告警 |
| `settings/` | `javac -Xlint:all` | 零告警 |
| `live/` | `javac -Xlint:all` | 零告警；帧字段号表见 `live/NOTES.md` |
| `api/` | 全量编译 | 45 个端点、7 个业务门面 |

## 5. 测试抓到并修掉的真实缺陷

| 缺陷 | 发现方式 | 修复 |
|---|---|---|
| `SettingsPageSpec.MAIN_PAGE` 在 `CONFIRMED_SETTINGS_KEYS` 之前静态初始化，导致 `ExceptionInInitializerError` | 烟雾测试第 42 项 | 把映射表的 static 块移到两个 List 声明之前 |
| `CONFIRMED_SETTINGS_KEYS` 里 4 个值写成了控件 key 而非真实配置键（`video_danmaku` vs `video_danmaku_enabled` 等） | 对照 `SettingsKeys.java` 的 71 个键复核 | 改为真实键名，并补全到 26 条精确映射 |
| `SmokeTest` 里「SDK<26 跳过」用例没先把假 provider 的 sdk 设成 25 | 首次运行 40/41 | 修正测试夹具（非产品代码问题） |

## 6. 尚未被测试覆盖的部分（如实登记）

- Compose 绘制层、Glance 组件树、Canvas 弹幕绘制 —— 需要 Android 设备/模拟器。
- protobuf 帧的字节级往返 —— `live/LiveFrameCodec` 有字段号表，但缺真机抓包样本做 golden test。
- 27 个响应壳类的 JSON key —— `$$serializer` 缺失，需抓包复核（见 `model/NOTES.md` S1）。
- `bd-ticket-guard-*` 的 protobuf 编码器 —— 接口已定义，编码器未实现（COVERAGE 第 111 行 🟡）。
- 扫码登录三个 jadx 溢出方法 —— 按可见常量重建，轮询间隔与成功判定待真机验证。
