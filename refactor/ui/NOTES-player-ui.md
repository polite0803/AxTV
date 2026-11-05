# NOTES —— 播放器 UI 层（lq2 + 播放菜单装配）

来源：`src/app/p000/lq2.java`（22,457 行，全工程最大 UI 类）及其直接调用方（菜单装配层）。
产出：`axtv/ui/PlayerUiPalette.java`、`axtv/ui/PlayerMenuSpec.java`、`axtv/ui/PlayerGestureSpec.java`（Java 17 纯 JDK，`-Xlint:all` 零警告通过）。

## 1. lq2.java 职责分区表（按行号区间）

| 行号区间 | 方法 | 职责 | 落地 |
|---|---|---|---|
| 1-44 | 类头 | 静态图标缓存 f13455I-K | — |
| 45-570 | m7477A…m7502Z | **非播放器 UI 噪音**：int[]/long[] 大数运算（SMIME/ASN.1 系）、Map 比较、MotionEvent source 判断（m7490N）、lerp（m7495S）、资源 id 查找（m7540t Context,String）等，R8 并入的散件 | 见 §6 待迁移 |
| 134-248 | m7486J/K/L | 菜单图标矢量工厂：`Filled.LocalFireDepartment`（火焰）、`Filled.LockOpen`（解锁）、`Filled.Replay`（重播），24dp | PlayerMenuSpec.ICON_* |
| 571-645 | m7503a | **菜单面板头**：背景卡（TERTIARY_CONTAINER，圆角）+ 图标（tint ON_TERTIARY_CONTAINER，pad 16）+ 标题「播放菜单」；外层滚动列表项距 10dp；尾 padding 参数（dd0 调用传 8） | PlayerMenuSpec §面板头 |
| 648-672 | m7504a0 | 音频编码字节打包（非 UI） | 待迁移 |
| 675-792 | m7505b | **图标按钮**：图标 + 文案（返回/弹幕设置…），点击回调，尺寸 44dp、间距 12dp、图标 pad 20，bg/fg 命名 `menu_icon_bg`/`menu_icon_fg` | PlayerMenuSpec.IconButton |
| 796-820 | m7506b0 | ASN.1 解析（非 UI） | 待迁移 |
| 823-1012 | m7507c | **选项组**：标题 + 横向滚动 chip 行；chip 高 44、选中 bg=PRIMARY_CONTAINER/fg=ON_PRIMARY_CONTAINER、未选 fg=ON_SURFACE_VARIANT、文本 14sp；点击→onSelect(item) | PlayerMenuSpec（选项组常量） |
| 1015-1130 | m7508c0 | 非 UI（导航辅助） | 待迁移 |
| 1131-16355 | m7509d | **菜单行（核心组件）**：图标?+标题+右值+副标题+尾部插槽；选中/禁用/按下/聚焦四态配色；圆角 12、padding(16,6,16,6)、标题 maxLines 1、右值 maxLines 2、副标题 α0.78、禁用 fg α0.7、最小宽 44。块内 ~35 份重复体为 R8/分支复制，语义同一组件 | PlayerMenuSpec.MenuRow/RowState |
| 16356-16766 | m7510d0 | ViewStructure/autofill 框架胶水（非 UI） | 待迁移 |
| 16767-16806 | m7511e | **分组小标题**：色 ON_SURFACE_VARIANT、排版 f13038n、padding(8, top, 0, 6) | PlayerMenuSpec.SectionLabel |
| 16819-16856 | m7512e0 | 非 UI（字体度量） | 待迁移 |
| 16857-16971 | m7513f | **段选 chip**：选中/按下→圆角 12，否则 4；选中 bg/fg 同上；可加 focusable | PlayerMenuSpec.SegmentRow |
| 16972-17031 | m7514f0 | 非 UI | 待迁移 |
| 17032-19756 | m7515g | **段选行**：List→标签（labelFn(item, selected)）、单选、首尾圆角特判、可选 focusRequester；横向滚动、间距 8 | PlayerMenuSpec.SegmentRow |
| 19757-19848 | m7516g0 | 大数运算（非 UI） | 待迁移 |
| 19849-20104 | m7517h | **滑杆行**：标题（4,0,4,6）+ 滑杆（padding 16,12）+ 值文本 14sp + 固定提示「左右键微调，长按加速」12sp；bg f11877G/描边 f11878H（`video_menu_slider_bg`）；press/release + click/longClick 四处理器（步长 f2/f3） | PlayerGestureSpec.SliderRow |
| 20105-20204 | m7518h0 | 大数运算（非 UI） | 待迁移 |
| 20205-20209 | m7519i | **步进辅助**：`invoke(clamp(v+delta, min, max))` | SliderRow.adjust |
| 20227-20358 | m7521j/m7523k | ripple/indication 框架胶水（非 UI） | 待迁移 |
| 20359-20463 | m7525l | 布局容器包装（框架层） | 待迁移 |
| 20469-20524 | m7527m | **Switch**（checked/enabled）：选中轨道 PRIMARY（`row_switch_checked_border`）、未选 OUTLINE（`row_switch_unchecked_border`）、thumb SURFACE | PlayerMenuSpec.SwitchRow |
| 20527-20609 | m7528m0 等 | 大数运算（非 UI） | 待迁移 |
| 20610-22122 | m7529n | **开关行**：m7509d 包装 + 尾部 Switch（onToggle(!checked)，lq2:22055） | SwitchRow |
| 22123-22457 | m7530n0…m7546z | 大数/Map/资源/绘制辅助散件（非 UI） | 待迁移 |

## 2. 按键 → 动作全表（lq2 可证部分）

| 输入 | 组件 | 动作 | 来源 |
|---|---|---|---|
| 左/右键（单击） | 滑杆行 | value ± clickStep，clamp(min,max) | m7519i lq2:20205；vk5:54（0.1） |
| 左/右键（长按） | 滑杆行 | 迭代 ± holdStep（加速） | lq2:19970/19982（f3）；vk5:76（0.2） |
| 确认键/点击 | 菜单行 | onClick（禁用时 rm1=null，lq2:1483） | lq2:1475 |
| 确认键/点击 | 开关行 | checked 取反 | lq2:22055 uj0(tm1, z) |
| 确认键/点击 | 段选 chip | 选中该值 | lq2:932/17252 |
| 确认键/点击 | 图标按钮 | onClick（返回/弹幕设置等） | lq2:732 |
| 任意交互 | 菜单行 | pressed→标题字重切换、按下态配色 | lq2:1538-1542 |

**重要边界**：遥控器 KeyEvent 的分发与「控制层自动隐藏」「长按加速计时器」「锁屏手势」「进度条 mm:ss」**不在 lq2**（全文件无 KeyEvent/onKeyEvent/计时逻辑）。它们位于播放器页面类（`on5.java`，10,468 行，含快捷菜单装配 1294-1357；另有 a91/ku3/ld4/vk5/xn2/ao2/vv0 装配件）。lq2 只承载提示文案「左右键微调，长按加速」（lq2:20025）与步进纯函数。**建议为 on5 另立工作流**，本模块未覆盖其 KeyEvent 状态机。

## 3. 菜单项全表（装配层，含取值）

| 菜单项 | 组件 | 取值/联动 | 来源 |
|---|---|---|---|
| 面板标题 | 面板头 | 「播放菜单」 | dd0:69→lq2:624 |
| 画质 | 段选行 | 自动/最高/1080P/720P/540P（code: auto/highest/1080p/720p/480p） | lq5:21-28, ku3:377, a91:623 |
| 倍速 | 段选行 | 0.5/0.75/1.0/1.25/1.5/2.0/3.0，显示 "N×" | on5:56, ld4:84, vk5:105 |
| 播放完后 | 菜单行（Replay 图标） | auto_next/pause/replay（显示=存储键） | a91:587, so3:19-21 |
| 弹幕 | 开关行 | 右值：列表样式/滚动样式；关→不渲染右值 | xn2:50, on5:1657 |
| 弹幕设置 | 图标按钮 | 打开弹幕设置页 | on5:1667 |
| 弹幕屏蔽词 | 开关行 | 右值 "N 条" | on5:1696, a91:170 |
| 过滤福袋评论 | 开关行 | boolean（直播弹幕设置） | xn2:97 |
| 显示区域 | 滑杆行 | steps 98，clickStep 1.0 / holdStep 5.0 | xn2:117, vv0:56 |
| 垂直间距 / 水平间距 | 滑杆行 | 0.5–2.0，steps 14，click 0.1 / hold 0.2，值 "%.1fx" | vk5:54/76, xn2:137/157 |
| 完整简介 | 菜单行 | 打开简介浮层 | on5:1479 |
| 查看发布者主页 | 菜单行 | 跳主页 | on5:1490 |
| 不喜欢 | 菜单行 | 反馈 | on5:1501 |
| 外屏放映 / 结束外屏放映 | 菜单行 | 投屏态双文案 | on5:1760 |
| 返回 | 图标按钮 | 关闭面板/页 | ao2:71/218 |
| 快捷胶囊（播放页） | 快捷项 | 主页/评论/不喜欢/点赞(↔取消点赞)/收藏(↔取消收藏)/弹幕开↔弹幕关/屏蔽词/简介/结束放映↔外屏放映，处理中... | on5:1294-1357（2145-2208 重复装配） |

## 4. 常量表（含来源行号）

| 常量 | 值 | 来源 |
|---|---|---|
| 菜单行圆角 | 12dp | lq2:1458 |
| 菜单行 padding | (16, 6, 16, 6) | lq2:1404/1856 |
| 菜单行最小宽 | 44dp | lq2:1846 |
| 标题/右值 maxLines | 1 / 2 | lq2:1409/1414 |
| 副标题透明度 | 0.78 | lq2:1457/1613 |
| 禁用前景透明度 | 0.7 | lq2:1443/1888 |
| 段选 chip 高 | 44dp | lq2:916 |
| 段选间距 / 段选文本 | 8dp / 14sp | lq2:868 / 962 |
| 段选圆角（选中↔未选） | 12dp ↔ 4dp | lq2:16906 |
| 图标按钮尺寸/间距/图标 pad | 44 / 12 / 20dp | lq2:706/732/750 |
| 滑杆区 padding | (16, 12) | lq2:19989 |
| 滑杆标题 padding | (4, 0, 4, 6) | lq2:19930 |
| 提示文本字号 | 12sp | lq2:20025 |
| 弹幕间距范围/步数/步长 | 0.5–2.0 / 14 / 0.1 & 0.2 | vk5:47/54/76 |
| 显示区域 steps | 98 | xn2:117 |
| 倍速列表 | [0.5,0.75,1.0,1.25,1.5,2.0,3.0] | on5:56 |
| 面板头滚动项距 | 10dp | lq2:579 |
| 配色角色 | k80=M3 ColorScheme（48 token） | k80:160-209/261-275 |

## 5. 文案全表（逐字）

「播放菜单」(lq2:624) · 「左右键微调，长按加速」(lq2:20025) · 「 倍速 · 按住 ← 中」(dd0:29，拼接于速度值后) · 「返回」(ao2:71) · 「弹幕样式」(ao2:72) · 「弹幕设置」(ao2:219/on5:1667) · 「弹幕」(xn2:50) · 「列表样式」/「滚动样式」(xn2:50) · 「过滤福袋评论」(xn2:97) · 「显示区域」(xn2:117) · 「垂直间距」/「水平间距」(vk5:54/76) · 「倍速」(vk5:105) · 「画质」(ku3:363) · 「播放完后」(a91:587) · 「弹幕屏蔽词」(a91:170) · 「完整简介」(on5:1479) · 「查看发布者主页」(on5:1490) · 「不喜欢」(on5:1501) · 「外屏放映」/「结束外屏放映」(on5:1760) · 「点赞」/「取消点赞」/「处理中...」(on5:1573-1575) · 「收藏」/「取消收藏」(on5:1603) · 「弹幕开」/「弹幕关」(on5:1335/1337) · 「主页」/「评论」/「屏蔽词」/「简介」/「结束放映」(on5:1294-1357) · 「条」(on5:1696) · 「×」(on5:4762) · 画质显示值「自动」「最高」「1080P」「720P」「540P」(lq5:21-28) · 图标名「Filled.LocalFireDepartment」「Filled.LockOpen」「Filled.Replay」(lq2:139/182/227)

## 6. 待迁移（Android/Compose 侧）

- lq2 内非 UI 散件（§1 标注"待迁移"的 ~9 个区间）：大数运算、ASN.1、autofill、ripple 胶水等，属其他模块职责，本轮未落地（lq2 是 R8 打平类，这些不是播放器 UI）。
- 像素绘制：全部组件的 Compose 绘制（chip/滑杆轨道/Switch 视觉）按规格由 Android 侧重建。
- on5.java（播放器页面，10,468 行）：KeyEvent 分发、控制层显隐、锁屏、进度条——不在本轮范围，需独立工作流。

## 7. 存疑

1. **「 倍速 · 按住 ← 中」**（dd0:29）：逐字保留；句尾「中」疑为 jadx 截断或「中键」缺字，语义待真机验证。
2. **滑杆 press/release 与 click/longClick 的方向接线**：处理器 lambda（yx2）未展开，±方向与 f2/f3 的确切绑定按「click=f2 单步、long=f3 加速」解读（与提示文案吻合），待对照字节码确认。
3. **显示区域滑杆的 min/max 与值文本格式**（xn2:117/vv0:56 的 strM9212t 后缀）未在本次抽样中定位到，规格层留参数。
4. **f11877G/f11878H 的 M3 扩展名**：k80 toString 只到 outline/scrim，扩展 22 个 token（f11874D-f11892V）未逐一命名，规格层用原字段名引用。
5. m7509d/m7529n/m7515g 内大量重复块为 R8 复制产物；已按"同一组件"归并，若后续发现某块参数不同需回查。
6. m7503a 面板头只含图标+标题，菜单主体的 Column 装配在播放器页面类（dd0 lambda 链上游），主体布局参数未在本模块落地。
