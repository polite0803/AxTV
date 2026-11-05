# 签名引擎（a_bogus / sign.js）重构说明

包名 `axtv.sign`，目录 `refactor/sign/`。
源码只读来源：`src/app/p000/*.java` 与 `src/resources/assets/*.js`。

## 1. 目录结构

```
refactor/sign/
├── NOTES.md
├── assets/
│   └── browser-shim.js      258 行  从 C1400n.java:90 的 Java 字符串常量逐字还原的 browser 环境垫片
└── axtv/sign/
    ├── AbogusEngine.java    371 行  a_bogus 会话层：QuickJS 生命周期 / 初始化 / 自检 / 串行执行 / 销毁
    ├── AbogusSigner.java    235 行  对外 API：sign() / signAcSignature() / 结果解析
    ├── SignJsEngine.java    270 行  第二套运行时：sign.js + getMSSDKSignature 直播签名
    ├── JsRuntime.java        33 行  JS 运行时抽象（不依赖 com.whl.quickjs）
    ├── JsEscaper.java        55 行  两种 JS 单引号字面量转义（原 m10092b 与 q30 内联版）
    ├── Assets.java           38 行  assets 抽象
    ├── JsonField.java       226 行  最小 JSON 读取器（替代 org.json 的 opt*）
    └── SignLog.java          64 行  日志出口（替代 android.util.Log）
```

Java 17、纯 JDK；`javac -Xlint:all` 零告警。

## 2. 类映射表

| 原类（`src/app/p000/`） | 原行数 | 原职责 | 新落点 | 新行数 |
|---|---|---|---|---|
| `C1608s.java` | 348 | 签名器主类（TAG `ABogusSigner`）：就绪判定 / 超时 / 销毁 / 结果解析 | `AbogusSigner`（对外语义、`m10093e` 解析）+ `AbogusEngine`（生命周期、`m10094a` 销毁） | 235 + 371 |
| `C1400n.java` case 0 | 243（整类） | 初始化：建 ctx / shim / abogus_dy.js / initABogus / 自检 / sign.js / `__ac` | `AbogusEngine.initialize()` + `loadSignJs()` | — |
| `C0761j.java` | 116 | 真正的 `getABogus(query, data, uri, userAgent)` 调用，脚本拼装 + 转义 | `AbogusSigner.buildSignScript()` / `sign()` | — |
| `C1363m.java` case 0 | 334（整类） | 续体：`withContext(quickjs-abogus)` 后转 `C0761j` | `AbogusEngine.submit()` | — |
| `C1496p.java` case 0 | 145（整类） | 续体：初始化派发 | `AbogusEngine.ensureReady()` | — |
| `C1326l.java` / `C1437o.java` / `C1533q.java` | 41 / 29 / 35 | 协程续体，只为重入 `m10095c` / `m10096d` / `m10097f` | 由 `Future.get(timeout)` 取代，无对应类 | 0 |
| `RunnableC0688h.java` case 0 | 389（整类，49 路 switch） | 销毁任务（`quickjs-abogus` 线程内 destroy） | `AbogusEngine.destroyInline()` | — |
| `RunnableC0688h.java` case 20 | 同上 | 销毁任务（TAG `SignJs`） | `SignJsEngine.destroyInline()` | — |
| `ThreadFactoryC0651g.java` case 0/1 | 35 | 线程名 `quickjs-abogus` / `quickjs-sign`，daemon | 两个引擎构造里的 `ThreadFactory` lambda | — |
| `qb1.java` + `pb1` | 200+ | 单线程 `CoroutineDispatcher` 包装 | `Executors.newSingleThreadExecutor` + `Future.get` | — |
| `no4.java` | 229 | SignJs 引擎：sign.js 加载 / MD5 msStub / `getMSSDKSignature` | `SignJsEngine` | 270 |
| `zg0.java` case 4 | 195（整类，6 路 switch） | sign.js 初始化（TAG `SignJs`） | `SignJsEngine.initialize()` | — |
| `q30.java` case 11 | 373（整类，12 路 switch） | `getMSSDKSignature` 调用 + `-`/`=` 重试循环 | `SignJsEngine.evaluateSignature()` / `signLive()` | — |
| `h40.java`（byte 21） | 195 | no4 初始化派发（反编译失败） | `SignJsEngine.ensureReady()` | — |
| `C1285k.java` case 0 | 664（整类，22 路 switch） | `globalThis.__ac.sign('', …)` 调用 | `AbogusSigner.signAcSignature()` | — |
| `C1570r.java` case 0 | 248 | `__ac.sign` 的协程包装（反编译失败） | 同上 | — |
| `C1608s.m10092b` | — | 4 条替换的 JS 转义 | `JsEscaper.escapeFull()` | — |
| `q30.java:310,314` 内联 | — | 2 条替换的 JS 转义 | `JsEscaper.escapeMinimal()` | — |
| `org.json.JSONObject` 用法（`C1608s:55-68`） | — | 解析 `{"ok":…,"value":…}` | `JsonField` | 226 |

辅助类 `jx4` / `qx4` / `o70` / `p70` / `qi4` / `j42` / `z50` 都是 kotlin-stdlib 的薄封装，逐个函数还原：
`m6866L`=`isBlank`（逐字符 `Character.isWhitespace`）、`m6887g0`=trim、`m6875U`=剥首尾双引号、
`m6886f0`=take(n)、`m9701x`=`String.replace`（全量）、`m9702y`=`startsWith`、`m6859E`=`contains(char)`、`m6486c`=`Objects.equals`。

## 3. 行为清单

### `AbogusSigner`

| 方法 | 语义 | 失败返回值 |
|---|---|---|
| `String sign(query, data, uri, userAgent)` | 计算 a_bogus | `""` |
| `String sign(query, data, uri)` | 同上，`userAgent` 传 JS `undefined` | `""` |
| `String signAcSignature(data)` | 计算 `__ac_signature` | `""` |
| `void close()` | 销毁 ctx 并关闭线程池 | — |

失败语义（逐条对应原实现）：

- 运行时未就绪 → `Log.e("a_bogus runtime is not ready")` → `""`（C1608s:211-212）
- evaluate 抛异常 → `Log.e("getABogus failed")` + 最多 3 层 cause → `""`（C0761j:88-98）
- 超时 → `Log.e("getABogus timed out")` → `""`（C1608s:178-179）
- 返回非 String（含 ctx 为空）→ `""`，不打日志（C0761j:82-87）
- JS 返回 `{"ok":false,"error":…}` → `Log.e("a_bogus generation failed: " + error)` → `""`（C1608s:64-67）
- JS 返回非 JSON 且长度 > 20 → 视为裸签名，原样返回（C1608s:74-77）

### `AbogusEngine`

| 方法 | 语义 |
|---|---|
| `boolean ensureReady()` | 未就绪时执行一次初始化，30 s 超时；结果缓存在 `isReady()` |
| `boolean isReady()` / `isAcrawlerReady()` | 对应 `f19458c` / `f19459d` |
| `Object evaluate(String script, long timeoutMs)` | 在 `quickjs-abogus` 线程上串行执行 |
| `void close()` | 对应 `m10094a`：JS 线程内就地销毁，否则派发并最多等 5 s，最后 shutdown 线程池 |

### `SignJsEngine`

| 方法 | 语义 | 失败 |
|---|---|---|
| `String signLive(roomId, userId, userAgent)` | MD5(live 参数串) → `getMSSDKSignature` | 抛 `IllegalStateException`（`"sign.js runtime is not ready"` / `"Failed to generate Douyin signature"`），与原实现 `C1902zy.m12465i` 一致 |
| `boolean ensureReady()` | 加载 sign.js，15 s 超时 | `false` |
| `void close()` | 新增（原实现无销毁入口） | — |

## 4. 常量表（值一律原样保留）

| 常量 | 值 | 来源 |
|---|---|---|
| 线程名（a_bogus） | `"quickjs-abogus"` | ThreadFactoryC0651g.java:20、C1608s.java:89 |
| 线程名（sign.js） | `"quickjs-sign"` | ThreadFactoryC0651g.java:25 |
| 初始化超时 | `30_000` ms | C1608s.java:254 |
| 单次调用超时 | `2_500_000` ms | C1608s.java:193/223/332/346（`Renderer.DEFAULT_DURATION_TO_PROGRESS_US`） |
| 销毁等待 | `5` s | C1608s.java:105 |
| sign.js 初始化超时 | `15_000` ms | no4.java:215 |
| abogus 脚本 | `"abogus_dy.js"` | C1400n.java:93 |
| sign 脚本 | `"sign.js"` | C1400n.java:135、zg0.java:146 |
| sign.js 补丁 | `"crawler = _0x5c2014"` → `"crawler = _0x5c2014; globalThis.__ac = crawler"` | C1400n.java:142 |
| 自检 query | `"aid=6383&device_platform=webapp"` | C1400n.java:120 |
| `__ac.init` 参数 | `{aid:99999999,dfp:0}` | C1400n.java:153 |
| 默认 API 路径（JS 侧） | `"/aweme/v1/web/general/search/single/"` | abogus.js:79（JS 内部，未改） |
| 日志截断 | 200 / 150 / 16 / 300 | C1400n:125、C0761j:84、no4:105、C1400n:185 |
| cause 打印层数 | 5（init）/ 3（签名） | C1400n:182、C0761j:91 |
| live 参数串 | 见 `SignJsEngine.LIVE_PARAMS_*` | no4.java:87 |

## 5. 一次 a_bogus 签名的时序（文字版）

```
调用方 n11.m8009B(uri, query, data, userAgent)
  └─ C1608s.m10095c(query, data, uri, userAgent)          [C1608s:131]
       │
       ├─ ① ensureReady()                                 [C1608s:229]
       │    ├─ f19458c == true  → 立刻返回 true
       │    └─ 否则 withTimeout(30 000 ms)
       │         └─ withContext(quickjs-abogus) { C1400n case 0 }   [C1496p:82]
       │              ├─ QuickJSContext.create()
       │              ├─ eval(browser shim, 去掉 \r)              → "browser shim loaded OK"
       │              ├─ eval(abogus_dy.js, 去掉 \r)              → "abogus_dy.js read (N chars)"
       │              ├─ eval(是否有 window.bdms)                  → "bdms object exists: …"
       │              ├─ eval("initABogus()")
       │              │    └─ false → "initABogus() returned false - bdms SDK _v not available"
       │              │              → destroy() → 返回 false
       │              ├─ eval(getABogus / get_ab 是否都是 function)
       │              ├─ eval(getABogus('aid=6383&device_platform=webapp', ''))
       │              │    └─ 日志 "test sign result = <前 200 字符>"
       │              ├─ 不可用 → destroy() → 返回 false
       │              ├─ f19458c = true                           → "runtime initialised successfully"
       │              └─ sign.js：eval(去掉 \r 并打 crawler 补丁的脚本)
       │                   ├─ eval(getMSSDKSignature('', UA))（异常吞掉）
       │                   ├─ 有 globalThis.__ac → eval(__ac.init({aid:99999999,dfp:0}))
       │                   │                       → f19459d = true
       │                   └─ 无 → "byted_acrawler not found in sign.js"
       │
       ├─ ② 未就绪 → Log.e("a_bogus runtime is not ready") → return ""
       │
       ├─ ③ withTimeout(2 500 000 ms)
       │    └─ withContext(quickjs-abogus) { C0761j case 0 }        [C1363m:124]
       │         ├─ 拼脚本（escapeFull：\ ' \n \r 四条替换）
       │         │    uri 空串 → "undefined"；userAgent null → "undefined"
       │         ├─ evaluate(...)                                  [C0761j:81]
       │         └─ 结果为 String → Log.d("getABogus raw result (N chars): <前 150>")
       │
       └─ ④ 解析（m10093e）                                        [C1608s:47]
            ├─ trim → 剥首尾双引号
            ├─ 反转义 \u003d \u002b \u002f \/ \"
            ├─ 空 / "null" / "undefined" → ""
            ├─ JSON ok=true  → value（空则 ""），日志 "Generated a_bogus: length=N"
            ├─ JSON ok=false → 日志 "a_bogus generation failed: <error>" → ""
            └─ 解析异常      → 长度>20 返回原文 / 否则 "Unexpected a_bogus result" → ""

最终把 a_bogus 塞进 query：n11.java:963 / :1041 `map.put("a_bogus", value)`
```

### live 签名时序（第二套）

```
cq2.java:180  this.f3320b.m8230a(roomId, userId, UA)
  └─ no4.m8230a                                              [no4:54]
       ├─ ensureReady()：withTimeout(15 000) → withContext(quickjs-sign) → zg0 case 4
       │    ├─ QuickJSContext.create()（注意：不注入 browser shim）
       │    ├─ eval(sign.js, 去掉 \r)
       │    ├─ eval(typeof getMSSDKSignature === 'function')
       │    │    ├─ true  → f15164d = true → "SignJs runtime initialized successfully with QuickJS"
       │    │    └─ false → "getMSSDKSignature is missing in sign.js" → destroy → false
       ├─ msStub = MD5(固定参数串).hex（小写）
       │    串：live_id=1,aid=6383,version_code=180800,webcast_sdk_version=1.3.0,
       │        room_id=<roomId>,sub_room_id=,sub_channel_id=,did_rule=3,
       │        user_unique_id=<userId>,device_platform=web,device_type=,ac=,identity=audience
       ├─ Log.d("Generating live signature: roomId=…, userId=…, msStub=<前16>...")
       └─ withContext(quickjs-sign) { q30 case 11 }
            ├─ eval("getMSSDKSignature('<msStub>', '<UA>')")    ← 只有 \ 和 ' 两条转义
            └─ 结果含 '-' 或 '=' → 原地重试（无上限、无 sleep）；否则打 length/retries 返回
```

## 6. 待迁移（依赖 Android / 第三方，本轮未落地）

### 6.1 `Assets`

```java
Assets assets = name -> context.getAssets().open(name);
```

### 6.2 `JsRuntime` 的 QuickJS 适配器

使用工程内已有的 `com.whl.quickjs.wrapper.QuickJSContext`（不要引入新依赖）：

```java
JsRuntime.Factory factory = () -> {
    QuickJSContext ctx = QuickJSContext.create();
    return new JsRuntime() {
        @Override public Object eval(String script) { return ctx.evaluate(script); }
        @Override public void close() { ctx.destroy(); }
    };
};
```

注意原实现的 `destroy()` 在已销毁的 ctx 上会抛异常（C1608s:93-97 有 catch），适配器里应做幂等保护。

### 6.3 日志

```java
SignLog log = (level, tag, msg, err) -> {
    switch (level) {
        case DEBUG -> Log.d(tag, msg);
        case INFO  -> Log.i(tag, msg);
        case WARN  -> Log.w(tag, msg);
        case ERROR -> { if (err == null) Log.e(tag, msg); else Log.e(tag, msg, err); }
    }
};
```

### 6.4 线程 / 协程

`sign()` / `signLive()` / `ensureReady()` 都是阻塞调用（内部是 `Future.get`），Android 侧必须
`withContext(Dispatchers.IO)` 或放到后台线程；**禁止在主线程调用**。原实现由 Dispatchers 的
`withContext(qb1)` 天然保证，迁移后这份保证转移给调用方。

`signAcSignature` 与 `sign` 共用同一个 `quickjs-abogus` 线程，可并发调用（会串行化）。

### 6.5 单例持有

原 `MyApplication` 持有 `C1608s(context)` 与 `no4(application)`；迁移后同样在 Application 里各建一个实例，
进程退出时调用 `close()`（原实现没有调用点，`m10094a` 只在初始化失败时被触发）。

## 7. 实现风险（原样保留的行为，不建议直接照搬）

1. **初始化失败会永久杀死引擎。** `C1400n:113/128/188` 在失败时调用 `m10094a()`，而 `C1608s:119`
   无条件 `executor.close()` → 线程池 shutdown。之后每次 `ensureReady()` 都直接失败，
   只能重建 `AbogusSigner` 实例。重构保留了 close 语义，但把 `RejectedExecutionException`
   包成 `IllegalStateException` 并让 `ensureReady()` 返回 false，不再向上抛。
   **建议**：初始化失败只销毁 ctx，不要关线程池；或提供 `reset()`。

2. **销毁成功路径会留下悬空引用（已修正）。** `RunnableC0688h:64-79` 的 `break` 位于 try 内，
   导致 `f19460e = null; f19458c = false; f19459d = false`（:76-78）**只在 destroy 抛异常时才执行**。
   正常销毁后字段仍指向已释放的原生指针，再 evaluate 会踩野指针。重构改为销毁后统一清空。

3. **超时常量单位错了，等于没有超时。** `Renderer.DEFAULT_DURATION_TO_PROGRESS_US` 是微秒常量
   （2 500 000 = 2.5 s），但被当毫秒传给 `withTimeout` → 41.7 分钟。`"getABogus timed out"` 与
   `"signAcSignature timed out"` 两条日志实际不可达。数值按要求原样保留（`CALL_TIMEOUT_MS`）。

4. **没有缓存。** 任务书提到的「缓存」在原实现里不存在：`C1608s` 只有三个状态位（ready/acrawler/ctx），
   没有 a_bogus 结果缓存，没有脚本预编译缓存。每次签名都重新拼一个脚本字符串再 evaluate
   （拼接 + 解析约 1–3 ms，evaluate 才是大头）。`n11` 里的 `f14572j/f14573k` 缓存的是
   device_id / install_id，与本模块无关。

5. **`withTimeout` 中断不了 native 调用。** QuickJS 的 `evaluate` 是阻塞 JNI 调用，
   Kotlin 的 `withTimeout` 只是放弃等待，任务继续占着那唯一一条线程。若某个脚本死循环，
   **之后所有签名请求都会排队卡住**。重构用 `Future.get(timeout)` + `cancel(true)`，本质相同。

6. **live 签名重试是无上限、无退避的忙循环**（`q30:320-335`）。判据是「结果不含 `-` 也不含 `=`」，
   而 base64 填充天然以 `=` 结尾 —— 一旦 `getMSSDKSignature` 返回带 `=` 的值，
   这段代码会在 JS 线程上**永久空转**，且没有任何日志。重构按要求保留原判据，
   但强烈建议加 `MAX_RETRIES`（如 8）与 `Thread.onSpinWait()`/短 sleep。

7. **`escapeMinimal` 不转义换行。** live 签名只做 `\` 和 `'` 两条替换（q30:310/314），
   若 `userAgent` 含 `\n`/`\r` 会直接破坏生成的 JS 语句。a_bogus 链路用的
   `escapeFull` 才有换行转义。

8. **两套运行时完全隔离，sign.js 被加载两次。** abogus 上下文（带 shim + `crawler` 补丁 + `__ac`）
   和 sign 上下文（裸跑，只用 `getMSSDKSignature`）各自持有一份 485 KB 的 sign.js AST，
   另外 abogus 还有 538 KB 的 abogus_dy.js。两个线程、两份堆，内存占用翻倍。

9. **`m10093e` 的解析异常兜底可能把错误文本当签名返回。** 只要原始文本长度 > 20，
   解析失败时会被原样当成 a_bogus 返回（C1608s:74-77），同时只打一条 DEBUG 日志。
   真实失败会被静默吞掉，直到服务端返回错误码。

10. **销毁只等 5 秒。** `m10094a` 在非 JS 线程销毁时 `.get(5, SECONDS)`，超时后照样
    `executor.close()`，正在跑的 evaluate 可能在已 destroy 的 ctx 上继续执行。

11. **`abogus.js` / `bdms.js` 两个资源从未被加载。** 实际只 open 了 `abogus_dy.js` 与 `sign.js`
    （`C1400n:93`、`:135`、`zg0:146`）。`abogus.js`（4 KB 的 XHR 劫持包装）已被
    `abogus_dy.js` 内的 `getABogus` / `initABogus` 取代，属遗留资源。
    所以重构后的 `Assets` 只需能读这两个文件 + `browser-shim.js`。

12. **`__ac_signature` 是死代码。** `C1608s.m10097f` 在全部 6077 个类中无调用方
    （唯一引用是它自己的续体 `C1533q`）。保留实现，但不要以为现网在用。

## 8. 存疑（反编译不完整 / 语义不明）

1. **`C1400n:159` 的 `break`。** sign.js 加载成功后会 `break` 出 switch，跳过 `:175` 的
   `r10 = 1; return true`，字面上让初始化返回 null(=false)。但同一函数在 `:132` 已经打了
   "runtime initialised successfully"，且业务上签名确实可用，判断为 jadx 的块重构产物。
   重构取「初始化成功后返回 true」，并且 `ensureReady()` 的返回值改为读 `isReady()` 标志，
   对两条路径都安全。

2. **`C1570r.invokeSuspend`（case 0）反编译失败**（`JadxOverflowException`，C1570r:210-222）。
   按 `C1285k` case 0 的同构实现（`globalThis.__ac.sign('', …)`）还原，错误文案取
   `C1285k:241/244` 的那两条。

3. **`h40.invokeSuspend`（byte 21）反编译失败**（同上）。按 `zg0` case 4 + 单线程 dispatcher 还原；
   15000 ms 超时取自 `no4:215`。

4. **`C1608s.m10095c` 与 `m10097f` 被 jadx 标记 `Code decompiled incorrectly`**
   （C1608s:126-131、:209-224、:281-283、:334-347），存在 `return obj2;` 之类的死代码。
   参数顺序不是从这两处读出来的，而是从 `C1363m` / `C0761j` 的构造参数链反推，
   再用调用方 `n11.m8009B → m10095c(str2, str3, str, str4)` 交叉验证：

   `m10095c(query, data, uri, userAgent)` → `C1363m(this, query, data, uri, userAgent)`
   → `new C0761j(this, uri, userAgent, query, data)` → `getABogus(query, data, uri, userAgent)`。

5. **`getABogus` 的第 4 参含义。** `abogus_dy.js` 里签名是
   `function getABogus(query, data, uri, userAgent)`，`abogus.js`（未加载）里是 3 参。
   代码实际传 4 个，第 4 个来自 `n11` 的 UA 字符串（见 `kz0.java:123` 的 QQBrowser UA 字面量），
   故按 `userAgent` 命名。

6. **`Renderer.DEFAULT_DURATION_TO_PROGRESS_US` 的具体数值**取自 media3 的公开常量（2 500 000），
   本仓库未包含该类源码，无法在产物内二次确认；但 `cl0.java:164`、`ec0.java:15` 把它当毫秒用，
   佐证了「单位误用」这个判断。

7. **`no4.m8230a` 的前两个参数名**按 `cq2.java:180` 的调用点推断为 `roomId` / `userId`
   （参数串里被拼进 `room_id=` 与 `user_unique_id=`）。
