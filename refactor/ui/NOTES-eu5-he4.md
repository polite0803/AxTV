# NOTES — eu5 / he4（视频详情页 + 直播分区页 / 搜索结果 UI）

> 来源：`src/app/p000/eu5.java`（11,652 行）、`src/app/p000/he4.java`（2,192 行）
> 产出：`refactor/ui/axtv/ui/DetailPageSpec.java`（325 行）、`refactor/ui/axtv/ui/LiveBrowseSpec.java`（248 行）
> 验证：javac 17 `--release 17 -Xlint:all` 编译通过，0 警告。

## 一、类映射表

| 原类 (src/app/p000) | 新类 | 行数 |
|---|---|---|
| eu5.java（R8 杂烩，UI 段约 4,900 行 + 噪音约 6,700 行） | DetailPageSpec | 325 |
| he4.java（搜索结果 UI） | LiveBrowseSpec | 248 |

### eu5 分区表（按行号区间）

| 行号区间 | 方法 | 内容 | 落地 |
|---|---|---|---|
| 1–183 | 静态字段 | base64 字母表×3、游标投影列名、protobuf 常量、椭圆曲线常数（f5455E–f5462L）、日式常量 | 噪音，不纳入 |
| 184–680 | m3791A 等 | base64 编解码、字段算术（f5455E 等即曲线参数）、字节校验 | 噪音（crypto），不纳入 |
| 681–777 | m3804M/M/N/…/W、m3815X/m3816Y/m3817Z | Modifier 包装器（焦点/弹簧动画）＋ kotlinx.datetime Instant 解析错误拼串 | 前者并入 Compose 侧；后者噪音 |
| 779–833 | m3818a | 角标 chip：图标(14dp)+文本，背景 `k80.f11881K`、圆角 8dp、内边 7dp | 常量/结构入 NOTES，像素留给 Android |
| 834–989 | m3819a0 | AppCompat `AppLocalesStorageHelper`（语言持久化），androidx 库代码被 R8 合入 | 待迁移（Android 侧复用 androidx 即可） |
| 990–3804 | **m3820b** | 封面卡组合项（"cover-card-bg/title/meta" 三键名）：封面图(默认 16:9) → 角标图 → 大字叠层(str7) → 徽标 chip(icon+str8) → 信息行(标题 2 行省略 + 头像 16dp + 昵称 + meta + "·" + meta) | CoverCard 槽位模型 |
| 3913–5734 | **m3822c** | Aweme → m3820b 装配器（jadx 把 3 个参数缺省分支展开成 8 个重复块）| assembleCoverCard / cardAspect / durationText / cardTitle / coverUrl |
| 5735–5792 | m3823c0 | AppCompat 语言读取 | 待迁移（同上） |
| 5793–5891 | **m3824d** | 无可播放视频空态页：AxTV 图标(80dp/圆角20) + "AxTV"(32sp) + "没有可播放的视频" + "返回上一级" | EMPTY_PAGE_TEXTS + 常量 |
| 5892–5928 等 | m3825d0 等十余个 | 字段算术（crypto） | 噪音 |
| 5929–5968 | m3826e | 通用 chip 容器（icon/文本槽位） | 结构说明 |
| 5988–6037 | m3828f | 三布尔装饰容器（点击/焦点/形状开关） | Compose 侧 |
| 6082–6198 | m3830g | 按窗口档位(br3)自适应宽度的容器 | Compose 侧 |
| 6221–6273 | **m3832h** | LIVE 红点：8dp，alpha 1↔0.35，800ms 往复（键 "liveDot"/"liveDotAlpha"） | LIVE_DOT_* 常量 |
| 6327–10215 | **m3834i** | 直播分区页：jadx 把同一逻辑展开成 4 个重复变体（唯一逻辑约 940 行）。状态/文案/布局见下文状态机 | LiveSectionState + sectionStatusText 等 |
| 10241–10314 | **m3836j** | 分区 chip（"livePartitionContainer/Fg"）：文本=分区名；紧凑内边 32dp / 常规 44dp | PARTITION_CHIP_* |
| 10345–10588 | **m3838k** | 直播间卡片（"liveRoomContainer/Fg/Meta"）：封面(104/112dp×16:9、圆角10) + 主播名(空→"频道 N") + 节目名(空→"暂无节目"；紧凑隐藏) + LIVE 红点 + 人数行 | LiveRoomCard + ownerName/programName/viewerLine |
| 10596–10675 | **m3840l** | 文字返回钮 "返回上一级"（"player-btn-bg/fg"，48dp 圆角） | TEXT_BACK_LEVEL |
| 10683–10747 | **m3842m** | 图标返回钮（contentDescription "返回"） | TEXT_BACK |
| 11110–11118 | m3844n | **jadx 反编译失败**（指令 2626 条被跳过），签名含 hu2（搜索 VM） | 存疑，见清单 |
| 11134–11140 | m3846o | 图片加载状态写入两个 State（url + 成功标志）后发起请求 | Compose 侧 |
| 11156–11431 | m3848p/m3850q | kotlinx.datetime Instant → ISO-8601 / 反解析 | 噪音（kotlin 库） |
| 11491–11525 | m3855s0/m3857t0/m3859u0 | Modifier 工厂 + "超过 64 字符加 …" 截断 | 截断函数并入 Compose 侧 |
| 11578–11609 | m3866y | protobuf 消息比较 | 噪音 |
| 11573–11651 | 其余 | Instant 解析 / base64 | 噪音 |

### he4 分区表

| 方法 | 行号 | 内容 | 落地 |
|---|---|---|---|
| m5083a | 24–133 | "隐藏竖屏"筛选 chip：选中态前景高亮；图标：z=紧凑模式用另一枚图标，否则手绘 `Filled.Smartphone` 路径 | TEXT_HIDE_PORTRAIT；点击切状态 |
| m5084b | 136–279 | 直播结果卡（"liveCardBg/Fg"）：封面(title=直播间标题) + "LIVE" 徽标 + 人数(formatCount+“人”，>0 才显示) + 主播名(空→"主播") + 标题(空→不渲染) | LiveResultCard + liveViewerText/parseCount/anchorName |
| m5085c | 283–355 | 直播结果列表：空 → "未找到相关直播"；否则按布局档位列数(2/3/4/5，TV 紧凑 2)渲染 + loading_more footer | columnCount + EMPTY_LIVE |
| m5086d | 358–403 | 搜索页脚手架：输入行(m5095m) + 结果容器(4dp 间距) | 结构 |
| m5087e | 407–624 | 视频结果瀑布流：去重键/条目 key；列数同上（宽档 spacing 8dp 否则 10dp）；尾部 loading 状态（key "loading_more"/值 "loading"） | dedupeKey/itemKey/aspectRatio |
| m5088f | 631–765 | 结果页头部：折叠动画(searchResultsHeaderCollapse 0↔1) + 返回钮(48×34dp，icon 内容描述 "返回") + "「q」的搜索结果" + 隐藏竖屏 chip(仅视频 Tab 且有结果) | resultTitle/showHidePortraitChip/TEXT_BACK |
| m5089g | 768–793 | 结果标题文本（f13031g，权重 1.0） | resultTitle |
| m5090h | 796–827 | 32dp 间隔占位 | 像素 |
| m5091i | 830–908 | 空态占位：错误态用错误图标，否则手绘 `Filled.SearchOff`；文本由调用方传入 | 结构 |
| m5092j | 917–1483 | 搜索页根：折叠态仅紧凑条；展开态 = 头部(m5088f) + Tab 行(m5094l) + body 状态机（搜索中/错误/关键词空/用户/直播/视频+隐藏竖屏过滤） | resolveBody/errorText/hintText/applyHidePortrait |
| m5093k | 1486–1554 | 频道 chip（"searchTabBg/Fg"）：选中高亮 | SearchTab |
| m5094l | 1557–1603 | 频道行：遍历 re4（视频/用户/直播，8dp 间距） | SearchTab.tabs() |
| m5095m | 1606–1649 | 搜索输入行：高 56dp(紧凑 54dp)，f13034j 字体，放大镜图标 | 结构 |
| m5096n | 1652–1688 | 建议行：图标(16dp) + 文本(f13033i) | 结构 |
| m5097o | 1691–1919 | 建议/历史行：排名(&le;3 高亮，26dp 槽) + 图标 + 文本(f13035k 权重) + 右侧计数 formatCount | rankHighlighted |
| m5098p | 1922–2067 | 用户卡（"userCardBg/Fg"）：头像 56dp 圆角 + 昵称(空→"抖音用户") + "粉丝 N"(>0) + 签名(空→不渲染) | UserCard/userNickname/fansText |
| m5099q | 2070–2181 | 用户结果列表：空 → "未找到相关用户"；否则网格（TV 宽屏走横向列表分支） | columnCount + EMPTY_USER |
| m5100r | 2184–2191 | 视频宽高比，缺省 16:9 | aspectRatio |

## 二、状态机

### 直播分区页（eu5.m3834i；原 op2）

```
状态: sections: List<Section(go2)>, selectedIndex: Int,
      dataById: Map<sectionId, SectionData(dn2{section, rooms, fullyLoaded})>,
      currentRoom: LiveRoom?, refreshing: Boolean
派生: selectedSection  = sections[selectedIndex]（越界 → null）
      selectedData     = dataById[selectedSection.id]（无 id → null）
      loadingMore      = data != null && rooms 非空 && !fullyLoaded
      focusIndex       = rooms 中 resolvedRoomId == currentRoom.resolvedRoomId 的下标（无 → -1）
渲染（xh0.m11950a 内容区）:
  refreshing                  → 骨架占位（32dp 块）
  data == null || rooms 空    → 空态 "暂无直播"（图标 + rw4.f19366I 样式）
  否则                        → LazyGrid 直播间卡（m3838k）
                                + loadingMore 时尾部 "loading_more" 条目（h30 加载圈）
布局二态:
  侧栏模式（宽屏，fM1769f>0 且非 br3.f2434J）: "选台" 标题 + 副标题（刷新/加载更多时
    用状态文案，否则 "分区名 · 共 N 个"）+ 横向频道行（点击换台）+ 全部直播间网格
  紧凑模式: "分区" 标签 + 分区列表（m3836j chips）+ 头部行（分区名 f13030f + 状态文案）
    + 直播间网格
状态行文案（eu5:6651-6663，优先级从上到下）:
  refreshing   → "正在刷新直播间"
  loadingMore  → "正在加载更多直播间"
  data 空缺/空 → "当前分区暂无直播"
  否则         → "共 N 个直播间"
```

### 搜索 body（he4.m5092j；原 hu2 + ye4 状态层）

```
searching                     → LOADING "搜索中..."
error != null                 → ERROR error ?? "搜索失败"（错误图标样式）
query 空白                    → HINT   "输入关键词开始搜索"
Tab=用户                      → 用户结果列表（空 → "未找到相关用户"）
Tab=直播                      → 直播结果列表（空 → "未找到相关直播"）
Tab=视频: 原始结果空 → HINT；否则 applyHidePortrait（height<=width 保留）：
          过滤后空 → "未找到相关视频"；否则瀑布流
"隐藏竖屏" chip 可见性: Tab=视频 且 视频结果非空（he4:1088-1090）
```

### 视频结果去重键（he4:424-449）

```
aweme_id 空白 → "fallback:" + uid.length + ":" + uid + ":" + createTime + ":" + desc.length + ":" + desc
否则          → "id:" + id.length + ":" + id
条目 key      → "result:" + 去重键 + ":" + 同键第 N 次出现（N 从 0 起）
```

## 三、文案全表（逐字，含标点与空格）

| 文案 | 来源 |
|---|---|
| `未命名视频` | eu5:4128（m3822c 8 处重复块同串） |
| `没有可播放的视频` | eu5:5876 |
| `AxTV` | eu5:5870/5872 |
| `直播分区` | eu5:6648（分区名缺失兜底） |
| `正在刷新直播间` | eu5:6652 |
| `正在加载更多直播间` | eu5:6654 |
| `当前分区暂无直播` | eu5:6658/6663 |
| `共 N 个直播间` | eu5:6660（`"共 "` + N + `" 个直播间"`） |
| `暂无直播` | eu5:6843 |
| `选台` | eu5:6969 |
| `分区名 · 共 N 个` | eu5:6974（`" · 共 "` + N + `" 个"`） |
| `分区` | eu5:7249 |
| `频道 N` | eu5:10520（`"频道 "` + (下标+1)） |
| `暂无节目` | eu5:10534 |
| `正在看 · X 人` | eu5:10569（`"正在看 · "` + X + `" 人"`） |
| `X 人在看` | eu5:10569（X + `" 人在看"`） |
| `返回上一级` | eu5:10603/10661 |
| `返回` | eu5:10735（contentDescription）；he4:731 同 |
| `·` | eu5:1576（meta 分隔符） |
| `loading_more` | eu5:6877 / he4:599（footer item key） |
| `隐藏竖屏` | he4:115 |
| `LIVE` | he4:239 |
| `主播` | he4:255 |
| `抖音用户` | he4:2023 |
| `粉丝 N` | he4:2034（`"粉丝 "` + formatCount） |
| `「关键词」的搜索结果` | he4:774（`"「"` + q + `"」的搜索结果"`） |
| `搜索中...` | he4:1138 |
| `搜索失败` | he4:1147 |
| `输入关键词开始搜索` | he4:1154/1205 |
| `未找到相关视频` | he4:1233（= SearchUiSpec.EMPTY_VIDEO） |
| `未找到相关直播` | he4:293（= SearchUiSpec.EMPTY_LIVE） |
| `未找到相关用户` | he4:2086（= SearchUiSpec.EMPTY_USER） |
| `N人` | he4:244（formatCount + `"人"`，无空格） |
| `M:SS`（时长） | eu5:4118（`"%d:%02d"`） |
| `M月d日` / `yyyy年M月d日` | DateLabels.medium（eu5:4156 引用） |
| `MM-dd` | DateLabels.shortLabel（eu5:4161 引用） |
| `视频` / `用户` / `直播` | re4:19-21（Tab 文案） |

## 四、常量表

| 常量 | 值 | 来源 |
|---|---|---|
| ASPECT_PORTRAIT | 0.5625f | eu5:4046 |
| ASPECT_LANDSCAPE | 1.7777778f | eu5:4048、he4:2188、eu5:1243(参数缺省) |
| LIVE_DOT 8dp / alpha 1↔0.35 / 800ms | — | eu5:6239-6247 |
| 分区 chip 内边 32dp(紧凑)/44dp | — | eu5:10273 |
| 直播卡封面宽 104dp(紧凑)/112dp | — | eu5:10470 |
| 空态页 logo 80dp / 圆角 20dp / 标题 32sp | — | eu5:5870/5872 |
| 返回钮 48×34dp / 图标钮 48dp | — | he4:706/711 |
| 结果列数 2/3/4/5（TV 紧凑固定 2） | — | he4:306-318、458-475、2135-2142 |
| 头像 56dp（用户卡）/ 16dp（封面卡作者行） | — | he4:1989、eu5:1532 |
| 排名徽标 26dp 槽，≤3 高亮 | — | he4:1795-1799 |

## 五、待迁移（Android/Compose 侧）

1. **全部 Compose 绘制**：本模块只落行为规格（字段/条件/动作/文案/常量）；像素、焦点、动画由 Android 侧按规格实现。
2. **m3819a0/m3823c0**：AppCompat `AppLocalesStorageHelper` 语言持久化（androidx 库代码被 R8 合入 eu5）——Android 侧直接用 androidx.appcompat 的实现即可，不重写。
3. **m3804M/m3811T/m3813V/m3814W/m3855s0/m3857t0**：Modifier 工厂（焦点边框、弹簧动画、点击缩放）——Compose 侧重建。
4. **手绘矢量图标**：`Filled.SearchOff`（he4:846-889）、`Filled.Smartphone`（he4:91-111）、返回箭头（he4:731）——Compose 侧用等价 material icon。
5. **m3846o**：封面加载状态双 State 写入 + 请求发起（coil）。

## 六、存疑清单

1. ~~**eu5.m3844n（11110-11118）jadx 反编译失败**~~ → **已结案（smali 补读）**：
   dex 原名 `n`（按签名特征 `(Lhu2;Ljava/util/List;IJIIZ...` 定位，留档
   `.workbuddy/eu5-n.smali.txt`，3289 行）。实测 **0 个字符串常量**；invoke 全部为
   Compose 运行时样板（go1.Q/o0/g/i）+ 全局播放器状态读取 `wi4.g` ×30 +
   State 读取 `cw4.getValue` ×19 + `ad4.w`(remember) ×10。定性：**搜索结果播放页的
   装配/渲染函数**——无独立业务逻辑，行为已由 DetailPageSpec / FeedPageSpec /
   PlayerGestureSpec 等子组件规格覆盖。**无行为丢失**。
   （方法名映射注意：jadx 与 smali 方法顺序在此区间不一致——R8 噪音方法混排，
   需按签名特征定位而非序号。）
2. **m3820b 大字叠层（eu5:1413-1451）语义**：叠层文本槽位（str20=str7）在唯一调用点 eu5:4164 收到的是 `DateLabels.shortLabel`（"MM-dd"），即作者模式下封面大字显示 MM-dd 日期。此映射按字节码逐参核对过，但视觉上不常见，建议真机截图佐证。
3. **m3834i 选台副标题条件（eu5:6971 `z29||z35`）**：变体一为 `(z29||z35)?statusText:"名 · 共 N 个"`，重复变体二退化为恒用 statusText（jadx 重复块噪音）。按变体一落规格；z29/z35 分别对应 refreshing/loadingMore 的外层捕获，但与 z19/z21 的关系因变量重命名不完全可追溯——已按"showStatus 布尔"落规格。
4. **he4:240 `qx4.m9695B(userCount)`**：字符串→Long 的解析函数实现未读（R8 库方法），按 `Long.parseLong` 失败回 0 落规格（parseCount）。
5. **m3828f 的 tw0 三布尔、m3830g 的 br3 布局档位**：容器级开关，语义（可点击/可聚焦/形状）按参数序推测，未逐一验证。
6. **按键路由**：eu5/he4 内**无任何 KEYCODE 分支**——与 COVERAGE.md 结论一致（本 App 无集中 KeyEvent 分发，按键走 Compose 焦点系统），如实记录。
7. **快捷面板装配**：eu5/he4 中**不存在** PlayerGestureSpec.QuickAction 装配点（grep 简介/弹幕/外屏/屏蔽/倍速/选集/合集 均无命中）；该装配在 on5，已由 FeedPageSpec 覆盖，未重复落地。
8. **R8 噪音归属**（不纳入重构）：base64 编解码（f5470b/f5471c/f5472d 字母表 + m3868z）、椭圆曲线字段算术（m3795D–m3868z 的大部分 int[]/long[] 运算，f5455E–f5462L 为曲线常数）、kotlinx.datetime Instant（m3815X–m3817Z、m3848p/m3850q、f5479k 位权表、m3859u0 截断）、protobuf 比较（m3866y）、AppCompat 语言存储（m3819a0/m3823c0）、游标投影列名（f5486r）。
