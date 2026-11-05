# NOTES —— on5（主页信息流 + 播放器页面壳）

来源：`src/app/p000/on5.java`（10,468 行）。
产出：`axtv/ui/PlayerKeyMachine.java`、`axtv/ui/ControlLayerSpec.java`、`axtv/ui/FeedPageSpec.java`
（Java 17 纯 JDK，`-Xlint:all` 零警告通过；不改动既有 axtv/ui 文件，仅引用）。

## 0. 修正前轮（lq2）的假设

lq2 的 NOTES 断言「on5 含 KeyEvent 状态机」。**实测不成立**：on5 全文件（含 import）
没有任何 `KeyEvent`/`KEYCODE`/`onKeyEvent` 引用；宿主 Activity
`AbstractActivityC1421nk.java:625-853` 的 `dispatchKeyEvent/onKeyDown` 也只是透传 super。
on5 实际承载的是**焦点(FocusRequester)路由 + 延迟恢复 + 控制层显隐/复位 + UI 行为规格**。
真正的遥控器按键原始分发在 ro5.java（36,083 行，播放器基础设施，含 ExoPlayer 全局共享
`m9957h`、动画期间"只下载不解码"预载等）——属其他任务范围，见 §7 待迁移。

## 1. 分区表（10.5k 行按行号区间）

| 行号区间 | 方法 | 职责 | 落地 |
|---|---|---|---|
| 49-59 | f16123a/f16124b/f16125c/f16126d | 静态配置：弹幕轨道生成器×2、倍速档 0.5..3.0、弹幕轨道参数 cb1×5 | FeedPageSpec（速度/轨道）；生成器细节存疑 §6 |
| 62-375 | m8675A | **合集选择面板**（modal sheet）：header/loading/empty/网格/related_more；当前集序号按 awemeId 匹配默认 0；集数=头序号+网格下标；"上滑加载更多" | FeedPageSpec |
| 378-387 | m8676A0 | t43 状态读取（R8 透传） | — |
| 388-556 | m8677B | **启动占位大图卡**：VideoBigThumb；url 含 "+" 置空；"MM:SS / MM:SS" 进度文本 | FeedPageSpec |
| 557-573 | m8678B0 | UI 组合透传 | — |
| 574-3204 | m8679C | **播放页壳主函数**（2630 行，R8 分支复制严重）：快捷菜单装配、主菜单 sheet、弹幕设置 sheet、画质/倍速段选、返回焦点路由 m8681D 调用点 | 本表其余各条 |
| 3205-3242 | m8680C0/m8681D | m8681D=**返回焦点路由**（on5:3231）：浮层可见→焦点给浮层关闭目标，否则还播放面；try/catch 吞异常 | PlayerKeyMachine.routeBackFocus |
| 3243-3252 | m8682D0 | **显示控制层**：浮层可见则返回；progressRatio=pos/dur；visible=true；timestamp=now；anchored=false；100ms 恢复焦点 | ControlLayerSpec.showControls |
| 3255-3259 | m8683E | 状态读取 | — |
| 3260-3294 | m8684E0 | **重播复位**：visible=false、position=0、trackQuality=null、buffered=0、pendingSeek=null、endOverlay=false、seekTo(0)+play | ControlLayerSpec.replayReset |
| 3297-3354 | m8685F | 单行文本+点击包装（R8 透传） | — |
| 3357-3360 | m8686F0 | 布局尺寸换算（bw0 模式） | — |
| 3363-3776 | m8687G | **视频信息头**：分辨率 i×i2、"头像" AsyncImage、@nickname、"·" 分隔、"MM:SS / MM:SS"、Filled.Share；点击→完整简介浮层 | FeedPageSpec（文案） |
| 3782-4725 | m8689H | **反编译失败**（18000 指令单元，jadx 抛 UnsupportedOperationException，on5:4724） | §6 存疑 |
| 4728-4735 | m8690H0 | 日期 "yyyy-MM-dd HH:mm"，秒/毫秒自适配 | ControlLayerSpec.formatPublishTime |
| 4738-4743 | m8691I | 占位结束日志 TAG=VideoItem："占位结束: awemeId=.. reason=.." | PlayerKeyMachine.placeholderEndLog |
| 4746-4763 | m8692I0/m8694J0 | 浮点→文本、"×" 倍速后缀 | ControlLayerSpec |
| 4751-4769 | m8693J/m8695K | **关浮层+恢复焦点**：J=false→回调→80ms；K=false→50ms | PlayerKeyMachine.dismissOverlayRestoreDelay |
| 4772-4831 | m8696K0 | 分辨率串提取：x→×、`\d{3,4}×\d{3,4}`、[·/|,] 切分取首段 | ControlLayerSpec.extractResolutionText |
| 4834-4845 | m8697L | 关浮层+条件(80ms)恢复焦点 | 同上 |
| 4846-4862 | m8698L0 | remembered 长状态（hk3=0 起始） | — |
| 4865-4874 | m8699M | 关浮层+直接 requestFocus（无延迟，try/catch） | PlayerKeyMachine |
| 4883-4901 | m8701N | **可播判定**：主 urlList 非空或任一码率 urlList 非空 | PlayerKeyMachine.hasPlayableUrl |
| 4904-4932 | m8702N0/m8703O/m8704O0/m8705P | 布局尺寸/状态读取（纯 UI） | — |
| 4935-4982 | m8706Q..m8715Z | hk3(gk3/cw4) 状态读写透传（hk3=mutableLongState） | ControlLayerSpec 字段 |
| 5023-5610 | m8716a | **快捷动作磁贴**：图标 22dp+标题+可选副标题；选中 bg=f11898c/fg=f11899d，否则 fg=f11914s | FeedPageSpec（文案引用 PlayerMenuSpec） |
| 5618-5719 | m8718b | **评论行**：nickname(副色)/text(主色, maxLines=2)/digg(formatCount) | CommentUiSpec（cb0 规格）衔接 |
| 5725-5799 | m8720c/m8721c0 | **进度条行**：rm1.invoke() 取当前位置 clamp(0,dur)、比例、mm:ss 两侧文本、Slider(onValueChange) | ControlLayerSpec.formatClock + NOTES §3 |
| 5802-5853 | m8722d/m8723d0 | 图标磁贴按钮 48dp（alpha 0.38 未激活；仅 z 可点） | — |
| 5856-5925 | m8724e/m8725e0 | 胶囊文本按钮 36dp（选中 bg=f11898c/fg=f11899d） | — |
| 5928-6053 | m8726f | **完整简介浮层**：bg α0.96、标题"视频简介"(22sp)、"@nickname"、分隔线 α0.55、合集头、正文 18sp、滚动→顶部/关闭（C1850yj case15） | PlayerKeyMachine.descriptionOverlayBackAction |
| 6061-6185 | m8728g | 播放页变体组合 | — |
| 6191-6219 | m8730h | **弹幕渲染入口**：p70.m8968e(on, lanes=f16123a/f16124b) | FeedPageSpec 常量 |
| 6227-6348 | m8732i | **互动提示行**：图标 14dp（α 1.0/0.45）+标题+副标题+点击 | FeedPageSpec |
| 6356-6396 | m8734j | **计数磁贴**：图标 14dp+文本 12sp | FeedPageSpec |
| 6399-6425 | m8735j0 | **实际分辨率**：Tracks 选中视频轨 w×h → "WxH"（t43） | ControlLayerSpec.trackResolutionText |
| 6428-6503 | m8736k | **点赞/收藏反馈浮层**：文案→图标映射（取消点赞/点赞/取消收藏/收藏） | FeedPageSpec.feedbackIcon |
| 6511-6600 | m8738l | **快捷磁贴渲染**（wy2=PlayerGestureSpec.QuickAction 原型）：64dp 方块、24dp 图标、标签；selected→高亮 | PlayerGestureSpec 引用 |
| 6608-6717 | m8740m | **合集信息头**："第N集 / 共M集"、"合集 · name"、"未命名合集"、合集图标 12dp | FeedPageSpec |
| 6725-6791 | m8742n | **网速行**："· 网速" + KB/s(MB/s)，阈值 1022976，"#.#" 格式 | ControlLayerSpec.formatNetSpeed |
| 6799-6848 | m8744o | "正在播放"呼吸动画：1600ms/2 相位 "nowPlayingPhase" | ControlLayerSpec |
| 6856-6911 | m8746p | **暂停大图标**：44dp "暂停" 居中 | ControlLayerSpec.TEXT_PAUSE |
| 6919-7043 | m8748q | **占位封面**：α 动画 "placeholderAlpha" 160ms；隐藏渐出后仍保渲染 | ControlLayerSpec |
| 7065-7207 | m8750r | **结束浮层**：so3 分支图标（jadx switch 反转，§6）；280dp 槽 | ControlLayerSpec/§6 |
| 7215-7251 | m8752s | 封面图渲染（rj3 优先） | — |
| 7259-7459 | m8754t | 头像行（α 动画 + 点击） | — |
| 7467-7763 | m8756u | 相关视频容器（UI 壳） | — |
| 7769-7997 | m8758v | 播放器容器（UI 壳，参数 60+） | — |
| 8011-8106 | m8760w | **互动按钮列**：点赞/评论/收藏/更多，间距 18 | FeedPageSpec 文案 |
| 8107-8114 | m8761w0 | **评论加载更多触发**：面板可见/游标无效则跳过；否则协程 tg0(default) | FeedPageSpec（评论分页） |
| 8115-8198 | m8762x | 互动磁贴：图标 28dp+计数、标签 11sp；按压/聚焦 α 提亮 | — |
| 8199-8352 | m8763x0 | 控制条组合（UI 壳） | — |
| 8353-10088 | m8764y | **播放页主函数**（1735 行）：含"正在外屏放映"(8646)、"结束放映"(8660) 投屏态 | FeedPageSpec 文案 |
| 10089-10438 | m8766z | **相关推荐卡**：前缀(正在播放/立即播放/相关推荐)+" · "+"第 N 集"；标题截 50+"..."/"无标题视频"；"@nickname"；meta(时间 · 时长)；digg/评论 formatCount；cover→dynamicCover→"" | FeedPageSpec |
| 10441-10467 | m8767z0 | **视频切换状态重置**：计数+1×3、mode 切换、控制层/拖动/seek/标志位全清、可播才写 currentVideo、DETAIL 无可播→重试计数+1 | PlayerKeyMachine.applyVideoSwitch |

## 2. 按键 → 动作全表（on5 可证部分）

| 输入/触发 | 行为 | 来源 |
|---|---|---|
| 返回键（浮层可见） | requestFocus(浮层关闭目标) | m8681D on5:3231-3240 |
| 返回键（无浮层） | requestFocus(播放面焦点) | 同上 |
| 关闭弹幕设置/简介等浮层 | 置 false→回调(false)→延迟 80ms/50ms requestFocus | m8693J/m8695K/m8697L |
| 关闭浮层（m8699M 路径） | 直接 requestFocus（无延迟） | on5:4865-4874 |
| 显示控制层（任意交互） | 见 §3；后 100ms requestFocus | m8682D0 + oo0 byte20 |
| 简介浮层返回 | scrollTop&gt;0→平滑滚顶；否则关闭 | C1850yj case15:297-306 |
| 重播确认 | seekTo(0)+play+全状态复位 | m8684E0 |
| 结束浮层图标 | so3：AUTO_NEXT/PAUSE/REPLAY 三态图标 | m8750r（图标反转存疑 §6） |
| 挂起 seek | 延迟 500ms → seekTo+状态落地 | tg0 case4:469-499 |

**KeyEvent 原始分发不在 on5**（§0）；DPAD/媒体键→上述动作的绑定在 ro5/其他类，未覆盖。

## 3. 控制层状态机

```
[任意交互] --m8682D0--> visible=true, ratio=pos/dur(dur>0), ts=now, anchored=false
                         ├- 100ms 后 requestFocus（oo0 byte20）
                         └- 若浮层 A/B 任一可见 → 不改变状态直接返回
[视频结束] --m8684E0--> endOverlay 显示
[结束浮层确认] → visible=false, pos=0, quality=null, buffered=0,
                 pendingSeek=null, endOverlay=false, seekTo(0), play
[上滑换视频] --m8767z0--> 全部状态复位（PlayerKeyMachine.applyVideoSwitch）
[挂起 seek]  --tg0 case4--> 500ms 后 seekTo(pos), pos=pos, buffered=0
```

焦点恢复延迟表（oo0.java）：byte17=50ms、byte16/18=80ms、byte20(默认)=100ms、byte19=150ms。

## 4. 文案全表（逐字，含标点空格）

| 文案 | 用途 | 来源行 |
|---|---|---|
| 上滑加载更多 | 合集面板分页尾 | 333 |
| 主页 / 评论 / 不喜欢 / 弹幕开 / 弹幕关 / 屏蔽词 / 简介 / 结束放映 / 外屏放映 | 快捷菜单项 | 1294-1357 |
| 处理中（快捷项，无省略号）/ 处理中...（PlayerMenuSpec.LABEL_PROCESSING） | 处理态 | 1305/1573 |
| 已点赞 / 点赞 / 已收藏 / 收藏 | 快捷菜单态 | 1310/1312/1326/1328 |
| 画质 | 主菜单段 | 1445 |
| 完整简介 / 查看发布者主页 / 不喜欢 | 菜单行 | 1479/1490/1501 |
| 弹幕 / 弹幕设置 / 弹幕屏蔽词 / 条 | 弹幕区 | 1657/1667/1696 |
| 结束外屏放映 / 外屏放映 | 投屏态 | 1760 |
| 视频简介 | 简介浮层标题 | 6032 |
| 取消点赞 / 点赞 / 取消收藏 / 收藏 | 反馈浮层 | 6469-6476 |
| 第N集 / 共M集（无空格，" / " 连接） | 合集头进度 | 6634-6640 |
| 未命名合集 / 合集 ·  | 合集标题 | 6691/6694 |
| 网速（前缀 "·"） | 网速行 | 6758-6759 |
| 暂停 | 暂停大图标 | 6901 |
| 点赞 / 评论 / 取消收藏 / 收藏 / 更多 | 互动按钮列 | 8035-8050 |
| 正在外屏放映 / 结束放映 | 投屏浮层 | 8646/8660 |
| 正在播放 / 立即播放 / 相关推荐 | 相关卡前缀 | 10200/10202/10204 |
| 第 N 集（带空格） | 相关卡集数 | 10175 |
| 无标题视频 | 相关卡兜底标题 | 10361 |
| 头像 | AsyncImage contentDescription | 3545 |
| 占位结束: awemeId=.. reason=.. | Log TAG=VideoItem | 4741 |
| Failed to load more: / Failed to load replies: status=.., cid=.. | Log TAG=Comments | tg0:547/554/613 |

重复文案区（1294-1760 / 2145-2208 / 2530-2633）为 R8 分支复制，语义同一装配。

## 5. 常量表

| 常量 | 值 | 来源 |
|---|---|---|
| 倍速档位 | 0.5/0.75/1.0/1.25/1.5/2.0/3.0 | on5:56 |
| 弹幕轨道参数 (offset,数量,α) | (3,4,0)(7,12,.35)(11,20,.7)(15,12,.15)(19,4,.55) | on5:59 |
| 轨道生成区间 | 180..6，步进 2（两组生成器） | on5:50/53 |
| mm:ss 格式 | "%d:%02d"，输入 ms | on5:3779 |
| 日期格式 | "yyyy-MM-dd HH:mm"，&lt;1e6 先×1000 | on5:4732/4729 |
| 网速 KB 阈值 | 1022976 B（999×1024）；KB=÷1024 整除；MB="#.#" | on5:6760-6763 |
| 分辨率正则 | `\d{3,4}×\d{3,4}`；分隔 `[·/|,]` | on5:4780/4799 |
| 焦点恢复延迟 | 50/80/100/150ms | oo0:549/572/595/618/641 |
| 挂起 seek 延迟 | 500ms | tg0:479 |
| 关闭后延迟返回 | 80ms（tg0 case1, tg0:240） | tg0 |
| 呼吸动画 | 1600ms、2 相位、"nowPlayingPhase" | on5:6808 |
| 占位淡入 | 160ms、"placeholderAlpha"、α 0↔1 | on5:6940 |
| 简介浮层 | bg α0.96、滚动区宽 0.94/0.78、max 1320、min 220..360、padding 28/22 | on5:5971-5988 |
| 相关卡标题截断 | 50 字符 + "..." | on5:10155 |
| 控制条计时 | 显示时 System.currentTimeMillis() 落 hk3 | on5:3249 |
| 网格间距 | 合集面板 8dp（on5:200）、相关卡 12dp（on5:10225） | — |
| 互动列间距 | 18dp | on5:8023 |

## 6. 存疑

1. ~~**m8689H（on5:4719-4725）反编译失败**~~ → **已结案（smali 补读，apktool baksmali 2.x）**：
   dex 原名 `H`（方法序号对齐验证：jadx 第 17 个方法 = smali 第 16 个 `.method H(Aweme;ZZZZZ...)`）。
   实测 414 寄存器 / 9,100 条指令 / 1,684 个 invoke，其中 ~75% 为 Compose 运行时样板
   （go1.g/Q/o0/i/t/d0/e/h/k），其余为状态读取（`on5.Y/Z/a0/d0/e0/s0(Lt43;)` ×130+）、
   `on5.s0` 子面板 ×11、`Aweme.getAwemeId/getVideo` ×25。**全方法仅 2 个字符串常量
   （`""` 与 `":"`）**。定性：**视频页装配根**——把 100+ 参数分发进子组件，无独立业务
   逻辑；行为已由各子组件规格（PlayerKeyMachine/ControlLayerSpec/FeedPageSpec/
   InteractionBarSpec 等）覆盖。smali 留档：`.workbuddy/on5-H.smali.txt`。
2. **m8750r 结束浮层 so3→图标映射被 jadx 反转**（DONT_INVERT 注释，on5:7107-7118）。
   按语义应为 AUTO_NEXT→oh4.m8583h、PAUSE→jl2.m6673I(暂停图标,见 on5:6901)、
   REPLAY→lq2.m7488L(重播图标,见 NOTES-player-ui §1)；反编译控制流无法自洽证实。
3. **f16123a/f16124b 弹幕轨道生成器**：`ma1.m7698e(range(180,6),2).m10518a(hf5(23))`
   的精确语义（区间方向/映射函数）未能从混淆调用还原，仅登记常量。
4. **m8682D0 进度比例的分子分母**（on5:3247 两个 hk3 谁为 duration）按
   tg0 case4 的复位次序推断：第 4 参=pos、第 5 参=duration；代码按
   `duration>0 ? pos/dur : 0` 落地。若 Android 侧实机比例 &gt;1 需对调。
5. **m8675A:10163-10174 的集数取值分支**（mixInfo.statis.currentEpisode vs 入参 num）
   jadx 输出两个 `numValueOf = null` 重复赋值，语义受损；按"currentEpisode&gt;0 优先，
   否则用入参 num"落地。
6. **KeyEvent→动作绑定表缺失**：DPAD 上下换视频、确认暂停、长按倍速等最终绑定
   位于 ro5.java（36,083 行）/其他类，不在本模块范围（§7）。

## 7. 待迁移（Android 侧）

| 项 | 原引用 | 说明 |
|---|---|---|
| FocusRequester 语义 | ki1.m7177a | 焦点请求/取消（FocusRequester.Default/Cancel 哨兵，ki1:24-28） |
| ExoPlayer | androidx.media3 | seekTo/setPlayWhenReady/play/pause；全局共享播放器在 ro5.m9957h |
| Tracks→分辨率 | m8735j0 | 选中视频轨 Format.width/height |
| 计数格式化 | DataModelKt.formatCount | digg/comment 计数缩写（1.2万 等） |
| 网络速度源 | m8742n 的 hk3(long) | 字节计数器由播放器/OkHttp 侧喂入 |
| 遥控器按键分发 | ro5.java（36,083 行） | DPAD/媒体键→播放动作、长按连发、动画期间预载（"动画期间准备视频(只下载不解码)"，ro5:35978-36064） |
| Compose 绘制 | go1/r13/mw2 等 | 全部像素/布局/动画执行 |
| 评论请求 | kq5.m7267c/m7268d | CommentListResponse{statusCode,comments,cursor,hasMore} |
| 图标语义映射 | sp0/k21/wi4/kb4/hc4/j42.m6460B/vt0/e21.m3297t/oh4.m8583h/jl2.m6673I/lq2.m7488L/li4.m7443g 等 | 各磁贴/反馈图标矢量 |

## 8. 类映射表

| 原类 | 新类 | 行数 |
|---|---|---|
| src/app/p000/on5.java（+ki1/oo0/tg0/C1850yj 辅证） | axtv/ui/PlayerKeyMachine.java | 149 |
| src/app/p000/on5.java（+tg0 case4 辅证） | axtv/ui/ControlLayerSpec.java | 216 |
| src/app/p000/on5.java（+tg0 case5/default 辅证） | axtv/ui/FeedPageSpec.java | 263 |
