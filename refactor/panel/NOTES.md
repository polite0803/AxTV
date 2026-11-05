# 手机 ↔ 电视 局域网面板服务（Cookie 推送 / 搜索输入 / 扫码登录）

AxTV 2.3.43 的「电视端起 HTTP/WebSocket 服务，手机浏览器访问」特色功能。核心层纯 JDK 17，
不依赖 Android SDK / OkHttp / Java-WebSocket / kotlinx。原模块分散在 15 个 R8 混淆类里，
其中 3 个类还承担着与 Compose 页面、WebView 的耦合，本模块把它们拆成「协议/服务」与「平台接口」两层。

## 一句话结论

- 两条 HTTP 链路：**9978**（Cookie 推送，随应用常驻）、**9979**（搜索输入页面，开关触发）；一条 WebSocket：**9980**（搜索输入通道）。
- 全部为明文 HTTP、**零鉴权**、CORS 全开、不校验来源 IP/Origin，且能在同一局域网内被任意网页 CSRF 推送登录态 —— 见「安全风险」。
- 3 个 jadx 未能反编译的方法（扫码轮询核心）按可见常量与状态重建，标为「存疑」。

## 类映射表

| 原类（`src/app/p000/`） | 行数 | 新类（`axtv.panel`） | 行数 | 说明 |
|---|---|---|---|---|
| `qj0.java` | 179 | `CookiePushHandler` | 145 | Cookie 服务路由与业务；`/submit` `/api/info` `/api/submit` `/logcat` OPTIONS |
| `pj0.java` | 69 | `DeviceAddressResolver` | 117 | 设备名（BRAND+MODEL）、首选局域网 IPv4（wlan/eth 优先） |
| `ct5.java` | 111 | `ThemeRenderer` | 258 | 模板读取/缓存、`{{KEY}}` 替换、HTML 转义、主题 CSS、no-store |
| `k63.java` | 149 | `PanelServer` | 229 | NanoHTTPD 骨架 → `com.sun.net.httpserver`：端口、并发、启停 |
| `e63.java` / `h63.java` | 296 / 305 | `PanelRoutes`（`Request` / `Response`） | 191 | 请求/响应模型 + 路由表 |
| `vc4.java` | 22 | `SearchInputHandler` | 50 | 9979 只服务 `/` 与 `/search` |
| `wc4.java` | 647 | `SearchSocketServer` | 484 | 9980 WebSocket：握手、文本帧、1001 关闭、消息分发 |
| `zc4.java` | 98 | `PanelState` + `PanelController` | 226 + 183 | 服务器状态机、连接集合、UI 通道、整体启停 |
| `dg0.java` | 37 | `PanelState.Status` | — | DISCONNECTED / WAITING / CONNECTED / ERROR |
| `C1710ur.java` | 56 | `PanelState.AuthSubmitPayload` | — | cookie / securityPayload / privateKey |
| `gu2.java` | 899 | 无独立类 | — | 23 个协程 lambda 的联合体；只取与本模块相关的 3 段：case 8（WebView 求值 15s）、case 9/10（清 Cookie）、case 13（网络变化回调） |
| `iu1.java` | 69 | `PanelController.startSearchServer()` | — | 开关打开时起 9979 + 9980，失败置 ERROR |
| `te4.java` | 57 | `PanelController.publishTvText()` | — | 电视文字变化 → 广播 `tv_text` |
| `f32.java` + `e32` | 199 | `InteractionSecurityParser` + `InteractionSecurityConfig` | 233 + 42 | security payload 三层解析、私钥归一化 |
| `fl4.java`（裁剪） | 1138 | `AuthStore` | 131 | `douyin_auth` 5 个键的读写 + 登录态判定（`el4`） |
| `ez3.java` | 665 | `QrLoginBridge` | 396 | 扫码登录状态机；3 个方法反编译失败，见「存疑」 |
| `od2.java` / `sy3.java` / `ty3.java` | 396 / 53 / 157 | `QrLoginBridge.WebViewHost` | — | WebView 会话、注入脚本、坐标点击 → 接口 |
| — | — | `Json` | 339 | 取代 `org.json`（原代码全部用 JSONObject/JSONArray） |

## 完整路由表

### 9978 —— Cookie 推送（`CookiePushHandler`）

所有响应都带：`Access-Control-Allow-Origin: *`、`Access-Control-Allow-Methods: GET, POST, OPTIONS`、
`Access-Control-Allow-Headers: Content-Type`（原 `qj0` 在 `serve()` 末尾统一追加）。

| 方法 | 路径 | 入参 | 出参 | 备注 |
|---|---|---|---|---|
| OPTIONS | 任意 | — | `200`，空体 | 额外 `Access-Control-Max-Age: 86400`；`text/plain` |
| GET | `/submit` | — | `200` `text/html; charset=utf-8`，`Cache-Control: no-store` | `submit.html`，仅替换 `DEVICE_NAME` |
| GET | `/api/info` | — | `200` `application/json` `{"success":true,"device_name":"…","app_name":"AxTV"}` | 字段顺序固定 |
| POST | `/api/submit` | JSON `{cookie, security_payload, private_key}` | `200` `{"success":true,"message":"推送成功！登录信息已更新"}`<br>`400` `{"success":false,"message":"请至少填写 Cookie 或互动安全凭据"}`<br>`500` `{"success":false,"message":"提交失败: …"}` | 三个字段 trim 后全空即 400；异常（含 body 非 JSON）500 |
| GET | `/logcat` | — | `200` `text/plain; charset=utf-8` + `Content-Disposition: attachment; filename="logcat.txt"`<br>`500` `读取 logcat 失败: …` | `logcat -t 10000 -v threadtime` |
| * | 其它 | — | `404` `text/plain` `404` | 带 CORS |

### 9979 —— 搜索输入页面（`SearchInputHandler`）

只有两个路径；**不校验方法**（原 `vc4` 只比对路径），也**没有 CORS 头**，与 9978 不同。

| 方法 | 路径 | 入参 | 出参 |
|---|---|---|---|
| 任意 | `/` | — | `200` `text/html; charset=utf-8`，`Cache-Control: no-store`（`search.html`，无业务变量） |
| 任意 | `/search` | — | 同上 |
| 任意 | 其它 | — | `404` `text/plain` `404` |

### 9980 —— 搜索输入通道（`SearchSocketServer`，RFC 6455）

| 方向 | 帧 | 载荷 | 说明 |
|---|---|---|---|
| 握手 | HTTP Upgrade | `Sec-WebSocket-Key` / `Version: 13` | 回 `101` + `Sec-WebSocket-Accept`；非 GET 或缺 Key 回 `400` |
| 电视 → 手机 | text | `{"type":"tv_text","text":"…"}` | 握手成功后**只给该连接**补发一次；之后由 `publishTvText` 广播 |
| 手机 → 电视 | text | `{"type":"input","text":"…"}` | 边打边同步（中文输入法组字期间前端不发） |
| 手机 → 电视 | text | `{"type":"search","text":"…"}` | 回车 / 点「在电视上搜索」 |
| 双向 | close | code `1001` | 电视关闭服务时逐个发 1001，reason 为空串 |

其它 type 一律忽略；JSON 解析失败只记日志 `消息解析失败: <原文>`。

## 模板占位符

`assets/web/*.html` 里出现、服务端替换的变量（一一对应，无遗漏）：

| 占位符 | 出现位置 | 取值 | 是否 HTML 转义 |
|---|---|---|---|
| `{{THEME_CSS}}` | submit.html:8、search.html:8 | `:root{color-scheme:light;--primary:#RRGGBB;…}@media (prefers-color-scheme:dark){…}` | 否（可信 HTML） |
| `{{PANEL_HEAD}}` | submit.html:9、search.html:9 | `assets/web/panel-head.html` 原文 | 否（可信 HTML） |
| `{{DEVICE_NAME}}` | submit.html:119、123、184 | `Build.BRAND + " " + Build.MODEL`（品牌已含在型号里时只留型号） | 是 |
| `{{KEY}}` | submit.html:7、search.html:7 | 无 | 注释里的说明文字，**原样保留**（替换器对未知键返回原串） |

CSS 变量 31 个，顺序见 `ThemeRenderer.CSS_VARIABLES`；取值为 `#` + 低 24 位大写 6 位十六进制。

## 手机侧操作 → 电视侧反应

### A. 推送 Cookie

```
电视启动
  └─ PanelController.startCookieServer() → 9978 监听
     日志: "Cookie 服务器启动: http://192.168.x.x:9978/submit"
手机打开 /submit
  └─ GET /submit → 渲染 submit.html（DEVICE_NAME=电视名）
手机粘贴 Cookie（本地只做计数 + 看有没有 sessionid，不发请求）
手机点「推送到电视」
  └─ POST /api/submit
       ├ 三项全空 → 400 "请至少填写…"（手机红字提示）
       └ 有值 → CookiePushHandler
            ├ cookie 非空 → AuthStore.setCookie → prefs.cookie_string
            ├ security/private 非空 → InteractionSecurityParser.parse → prefs.interaction_*(4 键 + payload)
            ├ PanelState.authPushed(AuthSubmitPayload) → UI 立即换登录态
            ├ 日志: "收到扫码提交: cookie=true, security=false"（取落盘后的值）
            └ 200 {"success":true,…} → 手机展示成功页（纯前端渲染）
可选: GET /api/info 取设备名；GET /logcat 下载日志
```

### B. 搜索输入

```
电视设置里打开「搜索输入」开关
  └─ PanelController.startSearchServer(): 9979 HTTP → 9980 WS → 状态 WAITING
     日志: "搜索输入服务器启动，HTTP 端口: 9979"
手机打开 http://ip:9979 → 页面 → 连 ws://ip:9980
  └─ 握手成功 → 状态 CONNECTED → 电视补发 {"type":"tv_text","text":"<电视搜索框现有文字>"}
手机每输入一个字 → {"type":"input"} → PanelState.onPhoneInput → 电视搜索框实时更新
手机回车/点按钮 → {"type":"search"} → PanelState.onPhoneSearch → 电视发起搜索
电视遥控器改字 → publishTvText() → 广播 {"type":"tv_text"} → 手机「电视上显示：…」同步
手机断线 → 状态 DISCONNECTED（手机 2 秒后自动重连，期间 UI 显示「已断开 · 重连中」）
关闭开关 → 两个端口关闭 → 已连手机收到 1001 → 手机重试连接
```

### C. 扫码登录（与面板服务共用同一份登录态）

```
点「扫码登录」
  └─ 状态 STARTING「正在打开抖音登录页」
      ├ 无前台 Activity → FAILED「请回到应用界面内再发起登录」
      └ 清 Cookie（removeAllCookies + removeSessionCookies + flush，5s 超时）
         → loadUrl("https://creator.douyin.com/")（60s 超时）
         → onPageFinished 注入钩子（记录 /passport/web/get_qrcode/ 与 /check_qrconnect/ 响应）
         → 轮询 window.__dyQr：解析 {"data":{"qrcode":"<base64>"}} → 二维码位图
            → 状态 WAITING_SCAN「等待扫码」
用户扫码 → 读 document.cookie，出现 sessionid/sessionid_ss → SUCCESS（携带 cookieHeader）
二维码过期 → 最多重开 8 次，日志 "qr expired, restarting login (#n)"，文案「二维码已过期，正在重新生成」
关闭/页面退出 → 清 Cookie、销毁隐藏 WebView → IDLE
```

## 数据落盘（原 `fl4`，SharedPreferences `douyin_auth`）

| 键 | 写入点 | 读取点 |
|---|---|---|
| `cookie_string` | `/api/submit` 且 cookie 非空 | 所有抖音接口、`isLoggedIn()` |
| `interaction_security_payload` | `/api/submit` 且 security/private 任一非空 | 互动签名 |
| `interaction_ticket` / `interaction_ts_sign` / `interaction_private_key` / `interaction_server_cert` | 同上（由解析结果写入） | 互动签名 |

- 均为**明文**存储（无加密、无 Keystore）。
- 写入是 `apply()` 异步落盘，因此「推送成功后立刻崩溃」理论上有极小概率丢写（原实现同）。
- 判定：`isLoggedIn` = Cookie 中存在非空 `sessionid` 或 `sessionid_ss`；`hasInteractionSecurity` = ticket、ts_sign、privateKey 三者都非空。

## 与原实现的差异（有意为之，需评审）

| # | 原实现 | 本实现 | 原因 |
|---|---|---|---|
| 1 | 每连接一线程（NanoHTTPD AsyncRunner，无上限） | 固定 8 线程池 | 避免手机端并发打满线程；单机面板够用 |
| 2 | `byte[contentLength]` 直接分配，无上限 | 上限 4 MiB，超限 500 | 原实现可用 `Content-Length: 2^31-1` 触发 OOM |
| 3 | 只按 `Content-Length` 读体，chunked 请求读不到 | `HttpExchange` 自动解 chunked | 属兼容性增强，手机端 `fetch` 一定带长度，行为不变 |
| 4 | Java-WebSocket 库（Selector + 解码器池 + 压缩协商） | 最小 RFC 6455（文本帧、ping/pong、close、分片） | 只用到文本帧，去掉第三方依赖 |
| 5 | `logcat` 输出一次性读入内存 | 相同（未改动） | 10000 行约 1–2 MB，可接受 |
| 6 | 服务器日志走 `android.util.Log` | `System.Logger`（同名 TAG） | 核心层不依赖 Android；Android 侧可桥接回 Log |

除上述 6 条，路由、状态码、字段名、消息类型、超时、重试上限、日志文案均按原样保留。

## 待迁移（Android 侧需实现）

| 接口 | 应有实现 | 要点 |
|---|---|---|
| `ThemeRenderer.AssetSource` | `context.getAssets().open("web/" + name)` | 路径已带 `web/` 前缀，实现不要再拼 |
| `ThemeRenderer.ThemeColors` | MaterialKolor `c74.m2048a(…)` 的动态配色，浅/深各 31 色 | 种子来自 `global_seed_argb`（0 表示跟随系统）；用 `argb & 0xFFFFFF` → `#RRGGBB`（`ThemeRenderer.cssColor`） |
| `AuthStore` | `SharedPreferences("douyin_auth")` + 原有 StateFlow 刷新 | 键名见上表；`setCookie` 后要同时刷新 `cookie_string` 流与登录态流，否则播放器不切换 |
| `LogcatExportHandler.LogcatSource` | 直接 `ProcessLogcat`（已提供）；或改为 `logcat -d` | Android 上 `Runtime.exec("logcat")` 依赖 `/system/bin`，非系统应用只能看到自身日志 |
| `DeviceAddressResolver.DeviceIdentity` | `Build.BRAND` / `Build.MODEL` | 型号可能已含品牌，`deviceName` 会去重 |
| `QrLoginBridge.WebViewHost` | 复刻 `od2` + `sy3` + `ty3` | ①无头 WebView 加到 decorView 索引 0、全屏、`setFocusable(false)`；②UA 伪装 `Mozilla/5.0 (Windows NT 10.0; Win64; x64) … Chrome/<内核版本>.0.0.0 Safari/537.36`（内核版本从默认 UA 里 `Chrome/(\d+)` 取，取不到用 113）；③`setDomStorageEnabled(true)`、`setAcceptThirdPartyCookies(true)`；④`onPageFinished` 里注入 `HOOK_SCRIPT`；⑤`evaluateJavascript` 结果要再解一层 JSON 字符串；⑥`tap` 用 `MotionEvent` 按 `viewWidth / docWidth` 换算坐标后 `dispatchTouchEvent`，间隔 80 ms 再发 UP |
| 二维码位图 | `BitmapFactory.decodeByteArray(qrImage, 0, len)` | 核心层只给 PNG 字节 |
| 服务器生命周期 | 随 ViewModel/Service 启停；Cookie 服务建议放前台 Service | 原实现 Cookie 服务线程 `daemon=false`，进程存活期间常驻 |
| 网络变化监听 | `ConnectivityManager.registerNetworkCallback`（原 `gu2` case 13） | 地址变化后要刷新电视上显示的二维码/URL，并重启搜索服务 |
| 权限 | `INTERNET`；导出 logcat 需系统签名或 `READ_LOGS` | 明文 HTTP 需 `usesCleartextTraffic=true`（原工程已如此） |

## 存疑（反编译不完整 / 语义不明）

1. **扫码登录核心三步未反编译**：`ez3.m3955b(od2, long)`（轮询二维码，356 指令）、`m3957d`（1144 指令）、`m3960i`（2900 指令）均为 `JadxOverflowException` / `Method dump skipped`。
   本实现据可见证据重建：`60000`/`20000` 两次超时、`15000` 的 JS 求值超时、`5000` 的 Cookie 刷新超时、最多 8 次过期重开、日志 `qr expired, restarting login (#n)`、状态串 `正在打开抖音登录页` / `等待扫码` / `二维码已过期，正在重新生成` / `抖音二维码生成超时，请重试` / `请回到应用界面内再发起登录`。
   **未确认**：轮询间隔（本实现 1 s）、登录成功判定（本实现用 `document.cookie` 含 `sessionid`）、`SCANNED`（已扫码未确认）如何进入、`nickname` 从哪取（`check_qrconnect` 的 `data.nickname`？本实现留空）、`codeInput` 何时为 true。
2. **`/api/submit` 未校验 Content-Type**：原实现不看请求头，任何 body 都当 JSON 解析；本实现保持。
3. **Cookie 服务是否也随「退出登录」重启**：`hu2` 只在构造与 `onStop` 里动它，未见重启点；本实现同样只由 `PanelController` 显式启停。
4. **`qj0` 的 `f18029h` 流去向**：`C1710ur` 被 emit 到一个 5 容量的 channel 后由谁消费，反编译中未见明确订阅点（推测是登录态刷新）；本实现用 `PanelState.authPushed` 交给 UI 层，语义等价。
5. **`gu2` 之外还有 `hu2.m5237s(gj0)` 反编译失败**（636 指令），与面板服务无关（信息流），未纳入。
6. **`/api/info` 的用途**：页面里没有直接调用，推测给未来的多端发现/调试用；原样保留。

## 安全风险清单（按严重度）

| 级别 | 问题 | 细节 | 建议 |
|---|---|---|---|
| **高** | 零鉴权 + CORS `*` | `POST /api/submit` 无 token、无来源校验；`Access-Control-Allow-Origin: *` 且允许 `Content-Type`，任意网页（含手机浏览器里开着的第三方站点）都能 `fetch('http://192.168.x.x:9978/api/submit')` 把**攻击者的** Cookie 推给电视，造成账号被顶替/会话劫持 | 加一次性配对码（页面展示、POST 校验）、限制 `Origin`、或改为短时 token |
| **高** | 明文传输敏感凭据 | Cookie（含 `sessionid`）与**私钥**走 HTTP 明文，同网段可被 ARP/DNS 投毒嗅探 | 至少 TLS 自签 + 首次信任，或改走局域网加密通道 |
| **高** | 明文落盘 | `douyin_auth` 里 `cookie_string`、`interaction_private_key` 明文；`adb backup`/root 可直接读 | 用 `EncryptedSharedPreferences` 或 Keystore 包裹 |
| **中** | WebSocket 不校验 `Origin`、无鉴权 | 任意网页可连 `ws://ip:9980` 读走 `tv_text`（可能含用户正在输入的关键词），也能把电视搜索框改成任意内容 | 校验 Origin=同源页面 + 握手带一次性 token |
| **中** | 无来源 IP 限制 | 同一局域网（含访客 Wi-Fi、热点）任何人可访问 9978/9979/9980 | 绑定到指定网卡/白名单网段 |
| **中** | `/logcat` 泄露 | 导出最近 1 万行日志，可能含 Cookie、接口 URL、设备信息 | 加鉴权或仅在 debug 构建开放 |
| **中** | 无速率/并发限制 | 可无限次 POST 触发 prefs 写入与 UI 通知；WS 可无限重连 | 令牌桶 + 连接数上限 |
| **中** | 请求体无上限（原实现） | `byte[contentLength]` 直接分配 → OOM | 本实现已加 4 MiB 上限 |
| **低** | `device_name` 直接进 HTML | 已做 HTML 转义（`&quot; &lt; &gt; &amp; &#39;`），无 XSS | 保持 |
| **低** | 二维码 base64 未校验大小 | 超大 `qrcode` 字段会解出巨大 byte[] | 解码前限制长度（建议 ≤ 256 KB） |
| **低** | 端口固定且暴露在 0.0.0.0 | 与「避免 p2p0」的地址选择逻辑不一致（服务监听所有网卡） | 如需收紧，绑到选中网卡的地址 |

## 验证

- `javac -encoding UTF-8` 全量通过（Java 17 语法，本机 javac 27 编译）。
- 临时自测脚本（未入库）覆盖 31 项：两页渲染与占位符（含 `{{KEY}}` 保留、no-store、CORS 有无）、`/api/info`、
  `/api/submit` 的空/成功/落盘/回传、私钥归一化 3 个分支、OPTIONS 预检、`/logcat` 附件头、404、
  WS 握手 Accept 值、连接即补发 `tv_text`、`input`/`search` 派发、状态 CONNECTED→DISCONNECTED、停止后端口释放。全部通过。
