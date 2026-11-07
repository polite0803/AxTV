/*
 * 精选分区的网页取数兜底。
 *
 * module/feed 对第三方原生请求只回空 protobuf（缺完整 web 身份/官方签名链），
 * 但 App 内的 WebView 打开频道页时，页面自己的请求能拿到真实数据（实测 200 + 1.6MB JSON）。
 * 这里加载频道页、注入 XHR/fetch 钩子，把页面自己拿到的 module/feed 响应收集起来解析，
 * 不参与页面渲染，也不复刻签名。
 *
 * 每个分区整页加载都要重付一次 ~26s 的页面 JS 启动（实测 Chrome 108 / Play3），
 * 页内 SPA 切分区只回 20 条无播放地址的骨架（实测），绕不过去。能省的是重复访问：
 * 成功取数的原始 payload 落盘（1 小时 TTL），重启后直接复用；轮询期间响应一到就收工，
 * 42s 只作兜底上限。
 */
package com.github.axtv.dv.ui.featured

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import axtv.model.Aweme
import axtv.model.DataModels
import axtv.model.FeedResponse
import com.github.axtv.dv.adapter.AppRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

object FeaturedWebFeed {

    private const val TAG = "FeaturedWebFeed"
    private const val BRIDGE = "AxFeed"
    private const val MIN_CHROME_MAJOR = 66
    private const val HOOK_INJECT_DELAY_MS = 2_500L
    /** 与探针实测对齐：页面从启动到发出完整 module/feed 约 32s，窗口必须留足。 */
    private const val FETCH_TIMEOUT_MS = 42_000L
    /** 可播视频够网格首屏一页（与 FeaturedRepository.PAGE_SIZE 同量）即提前收工，42s 只作兜底上限。 */
    private const val EARLY_EXIT_ITEMS = 10
    /** 页面偶发返回不含可播视频的 chunk 响应，重载一次通常恢复。 */
    private const val FETCH_ATTEMPTS = 2

    /**
     * 按分区去重：同一分区只允许一个取数会话，不同分区并行。
     * 预取会同时拉 4 个分组，单个布尔锁会让其中 3 个直接返回空。
     */
    private val inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** 页面能否在当前 WebView 上运行；低于 Chrome 80 的引擎跑不了抖音前端。 */
    fun isSupported(context: Context): Boolean = chromeMajor(context) >= MIN_CHROME_MAJOR

    /** 不支持时的显式说明（含实际版本），由分区错误态直接展示。 */
    fun unsupportedReason(context: Context): String? {
        val major = chromeMajor(context)
        return if (major >= MIN_CHROME_MAJOR) null else
            "当前设备 WebView 版本过低（Chrome $major），无法加载分区内容，请升级系统 WebView"
    }

    suspend fun fetch(context: Context, tagId: String): List<Aweme> =
        withContext(Dispatchers.Main) {
            if (tagId.isEmpty() || tagId == "0") return@withContext emptyList()
            if (!isSupported(context)) return@withContext emptyList()
            if (!inFlight.add(tagId)) {
                Log.i(TAG, "skip tag=$tagId (in flight)")
                return@withContext emptyList()
            }
            try {
                val activity = context.findActivity() ?: run {
                    Log.w(TAG, "skip tag=$tagId (no activity)")
                    return@withContext emptyList()
                }
                Log.i(TAG, "start tag=$tagId chrome=${chromeMajor(context)}")
                val payloads = Collections.synchronizedList(mutableListOf<String>())
                val view = try {
                    create(activity, payloads)
                } catch (t: Throwable) {
                    Log.w(TAG, "webview unavailable", t)
                    return@withContext emptyList()
                }
                // 挂到 decorView，而不是 android.R.id.content：后者里是 ComposeView，
                // 往它上层塞 1×1 WebView 会让页面判定自己不可见，服务端只回 20 条
                // 无播放地址的骨架。decorView 是普通容器，同一份取数代码在独立
                // Activity（普通 View 树）里实测能拿到 30 条完整可播数据。
                val host = activity.window.decorView as? ViewGroup
                runCatching {
                    view.alpha = 0f
                    view.isFocusable = false
                    view.isClickable = false
                    host?.addView(view, ViewGroup.LayoutParams(1, 1))
                }
                var items: List<Aweme> = emptyList()
                try {
                    for (attempt in 1..FETCH_ATTEMPTS) {
                        payloads.clear()
                        val startedAt = System.currentTimeMillis()
                        view.loadUrl("https://www.douyin.com/channel/$tagId")
                        delay(HOOK_INJECT_DELAY_MS)
                        view.evaluateJavascript(HOOK, null)
                        // 与探针一致：5s 再注入一次，兜住首屏晚加载的 XHR 包装。
                        delay(2_500L)
                        view.evaluateJavascript(HOOK, null)
                        val deadline = startedAt + FETCH_TIMEOUT_MS
                        var scrolled = false
                        var parsedPayloads = 0
                        var earlyExit = false
                        while (System.currentTimeMillis() < deadline) {
                            // 与探针一致：只在 5.5s 滚一次。连续滚动会把页面推进分块预加载模式，
                            // 服务端只回 20 条无播放地址的骨架（实测）。
                            if (!scrolled && System.currentTimeMillis() - startedAt > 5_500L) {
                                scrolled = true
                                view.evaluateJavascript(
                                    "window.scrollTo(0, document.body ? document.body.scrollHeight : 4000)", null,
                                )
                            }
                            delay(1_000L)
                            // 响应一到就增量解析：够一页就收工，42s 只是兜底上限，不是固定等待。
                            if (payloads.size != parsedPayloads) {
                                parsedPayloads = payloads.size
                                items = withContext(Dispatchers.Default) { parse(payloads.toList()) }
                                Log.i(
                                    TAG,
                                    "progress tag=$tagId payloads=${payloads.size} playable=${items.size} " +
                                        "elapsed=${System.currentTimeMillis() - startedAt}ms",
                                )
                                if (items.size >= EARLY_EXIT_ITEMS) {
                                    earlyExit = true
                                    break
                                }
                            }
                        }
                        Log.i(
                            TAG,
                            "attempt=$attempt/$FETCH_ATTEMPTS tag=$tagId payloads=${payloads.size} " +
                                "scrolled=$scrolled early=$earlyExit elapsed=${System.currentTimeMillis() - startedAt}ms url=${view.url}",
                        )
                        payloads.toList().forEachIndexed { i, p ->
                            Log.i(TAG, "payload[$i] tag=$tagId len=${p.length} head=${p.take(200)}")
                        }
                        if (!earlyExit) {
                            items = withContext(Dispatchers.Default) { parse(payloads.toList()) }
                        }
                        Log.i(TAG, "attempt=$attempt tag=$tagId items=${items.size}")
                        if (items.isNotEmpty()) break
                    }
                } finally {
                    runCatching { view.stopLoading() }
                    runCatching { host?.removeView(view) }
                    runCatching { view.destroy() }
                }
                if (items.isNotEmpty()) store(context, tagId, payloads.toList())
                Log.i(TAG, "done tag=$tagId items=${items.size}")
                items
            } finally {
                inFlight.remove(tagId)
            }
        }

    /** 磁盘缓存有效期：站内播放地址有期限，1 小时内复用仍可播。 */
    private const val CACHE_TTL_MS = 60L * 60 * 1000
    private const val CACHE_DIR = "featured"

    /**
     * 分区取数的磁盘缓存：一次成功取数的原始 payload 落到 cacheDir，重启后直接复用。
     *
     * 整页取数每次都要重付 ~26s 的页面 JS 启动（实测 Chrome 108 / Play3），
     * 页面自身的 SPA 切分区只回 20 条无播放地址的骨架，绕不过去；能省的是重复访问。
     */
    suspend fun cached(context: Context, tagId: String): List<Aweme> = withContext(Dispatchers.IO) {
        val file = cacheFile(context, tagId)
        if (!file.isFile) return@withContext emptyList()
        if (System.currentTimeMillis() - file.lastModified() > CACHE_TTL_MS) {
            file.delete()
            return@withContext emptyList()
        }
        val payloads = runCatching {
            val arr = org.json.JSONArray(file.readText())
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrNull().orEmpty()
        if (payloads.isEmpty()) return@withContext emptyList()
        withContext(Dispatchers.Default) { parse(payloads) }
    }

    private fun store(context: Context, tagId: String, payloads: List<String>) {
        runCatching {
            val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
            val arr = org.json.JSONArray().apply { payloads.forEach { put(it) } }
            File(dir, "$tagId.json").writeText(arr.toString())
            // 顺手清掉过期缓存，避免长期占存储。
            val now = System.currentTimeMillis()
            dir.listFiles()?.forEach { f ->
                if (now - f.lastModified() > CACHE_TTL_MS) f.delete()
            }
        }
    }

    private fun cacheFile(context: Context, tagId: String): File =
        File(File(context.cacheDir, CACHE_DIR), "$tagId.json")

    private fun create(activity: Activity, payloads: MutableList<String>): WebView {
        CookieManager.getInstance().setAcceptCookie(true)
        val view = WebView(activity)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            // 本 WebView 带 JS 桥，文件/content 访问显式关死（API 26-29 默认值为 true）。
            allowFileAccess = false
            allowContentAccess = false
            userAgentString = desktopUserAgent(activity)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        }
        view.addJavascriptInterface(Bridge(payloads), BRIDGE)
        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                if (msg != null && msg.message().startsWith("AxFeed")) Log.d(TAG, msg.message())
                return true
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // 首屏请求可能早于注入，完成后再兜一次；翻页由滚动触发。
                view?.evaluateJavascript(HOOK, null)
            }

            /** 只拦非 http(s) 的自定义 scheme（intent://、snssdk:// 等会跳出应用）。
             *  与 AndroidQrWebViewHost 同款：刻意不做域名白名单，抖音跳转域名不可枚举。 */
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return true
                val scheme = runCatching { android.net.Uri.parse(url).scheme }.getOrNull()
                val allowed = scheme.equals("http", ignoreCase = true) ||
                    scheme.equals("https", ignoreCase = true)
                if (!allowed) Log.i(TAG, "block non-http navigation: $url")
                return !allowed
            }
        }
        return view
    }

    private class Bridge(private val payloads: MutableList<String>) {
        @JavascriptInterface
        fun onFeed(json: String) {
            if (json.length > 100) payloads.add(json)
        }
    }

    private fun parse(payloads: List<String>): List<Aweme> {
        val api = AppRuntime.api ?: return emptyList()
        val out = LinkedHashMap<String, Aweme>()
        for (json in payloads) {
            if (!json.trimStart().startsWith("{")) continue
            // 诊断：module/feed 的响应是多模块结构，直接看顶层字段与 data 元素形状，
            // 才能判断是「字段名对不上」还是「这本来就不是视频列表模块」。
            runCatching {
                val obj = org.json.JSONObject(json)
                val keys = obj.keys().asSequence().toList()
                val dataArr = obj.optJSONArray("data")
                val awemeArr = obj.optJSONArray("aweme_list")
                val data0 = dataArr?.optJSONObject(0)?.keys()?.asSequence()?.toList()
                Log.i(
                    TAG,
                    "shape keys=$keys dataLen=${dataArr?.length()} awemeLen=${awemeArr?.length()} data0=$data0",
                )
            }.onFailure { Log.w(TAG, "shape read failed: ${it.message}") }
            val response = try {
                api.decodeBody(json, FeedResponse::class.java, "/aweme/v2/web/module/feed/")
            } catch (t: Throwable) {
                Log.w(TAG, "decode failed: ${t.message} head=${json.take(120)}")
                continue
            }
            val flat = response.flatAwemes()
            flat.firstOrNull()?.let { a ->
                val v = a.video()
                Log.i(
                    TAG,
                    "firstAweme id=${a.awemeId()} type=${a.awemeType()} primary=${DataModels.isPrimaryAweme(a)} " +
                        "identity=${DataModels.hasDisplayableIdentity(a)} playable=${v.isPlayable()} " +
                        "bitRates=${v.bitRate().size} bitUrls=${v.bitRate().count { it.hasUrls() }} " +
                        "playUrls=${v.playAddr().urlList().size} dlUrls=${v.downloadAddr().urlList().size} " +
                        "desc=${a.desc().take(20)}",
                )
            }
            Log.i(TAG, "decoded flat=${flat.size} status=${response.statusCode()}")
            for (aweme in flat) {
                // 网页 module/feed 的 aweme 常只带 video.bit_rate，主 play_addr 为空；
                // filterDisplayableFeedAwemes 只认 play_addr，会把整页都过滤掉（实测 20→0）。
                // 播放链路本来就优先用 bit_rate（VideoPlayUrlResolver），这里用 isPlayable 判。
                if (!DataModels.isPrimaryAweme(aweme)) continue
                if (!aweme.video().isPlayable()) continue
                if (!DataModels.hasDisplayableIdentity(aweme)) continue
                val id = aweme.awemeId()
                if (id.isNotEmpty()) out.putIfAbsent(id, aweme)
            }
        }
        return out.values.toList()
    }

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    private fun chromeMajor(context: Context): Int {
        val ua = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("")
        return Regex("Chrome/(\\d+)").find(ua)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun desktopUserAgent(context: Context): String {
        val default = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("")
        val chrome = Regex("Chrome/(\\d+)").find(default)?.groupValues?.get(1) ?: "113"
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$chrome.0.0.0 Safari/537.36"
    }

    private const val HOOK = """
(function(){
  if (window.__axFeedHooked) return; window.__axFeedHooked = true;
  function report(text){
    try { if (window.$BRIDGE && text && text.length > 100) window.$BRIDGE.onFeed(text); } catch(e) {}
  }
  var X = XMLHttpRequest.prototype, oo = X.open, os = X.send;
  X.open = function(m, u){ this.__u = String(u); return oo.apply(this, arguments); };
  X.send = function(b){
    try { this.addEventListener('load', function(){
      try { var u = this.__u || ''; if (u.indexOf('/module/feed/') >= 0) {
        var t = this.response;
        if (t && typeof t === 'object' && !(t instanceof ArrayBuffer)) { try { t = JSON.stringify(t); } catch(e) { t = ''; } }
        else if (typeof t !== 'string') { try { t = new TextDecoder('utf-8').decode(t); } catch(e) { t = ''; } }
        report(t || '');
      } } catch(e) {}
    }); } catch(e) {}
    return os.apply(this, arguments);
  };
  var f = window.fetch;
  window.fetch = function(i, init){
    var u = typeof i === 'string' ? i : ((i && i.url) || '');
    var p = f.apply(this, arguments);
    if (u.indexOf('/module/feed/') >= 0) {
      p.then(function(r){ r.clone().text().then(function(t){ report(t || ''); }); });
    }
    return p;
  };
})();
"""
}
