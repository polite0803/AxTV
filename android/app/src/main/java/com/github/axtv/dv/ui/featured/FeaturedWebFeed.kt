/*
 * 精选分区的网页取数兜底。
 *
 * module/feed 对第三方原生请求只回空 protobuf（缺完整 web 身份/官方签名链），
 * 但 App 内的 WebView 打开频道页时，页面自己的请求能拿到真实数据（实测 200 + 1.6MB JSON）。
 * 这里加载频道页、注入 XHR/fetch 钩子，把页面自己拿到的 module/feed 响应收集起来解析，
 * 不参与页面渲染，也不复刻签名。
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
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

object FeaturedWebFeed {

    private const val TAG = "FeaturedWebFeed"
    private const val BRIDGE = "AxFeed"
    private const val MIN_CHROME_MAJOR = 66
    private const val HOOK_INJECT_DELAY_MS = 2_500L
    private const val SCROLL_ROUNDS = 2
    private const val SCROLL_INTERVAL_MS = 3_000L

    private val inFlight = AtomicBoolean(false)

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
            if (!inFlight.compareAndSet(false, true)) return@withContext emptyList()
            val activity = context.findActivity() ?: run {
                inFlight.set(false)
                return@withContext emptyList()
            }
            val payloads = Collections.synchronizedList(mutableListOf<String>())
            val view = try {
                create(activity, payloads)
            } catch (t: Throwable) {
                Log.w(TAG, "webview unavailable", t)
                inFlight.set(false)
                return@withContext emptyList()
            }
            val host = activity.findViewById<ViewGroup>(android.R.id.content)
            runCatching {
                view.alpha = 0f
                view.isFocusable = false
                host?.addView(view, ViewGroup.LayoutParams(1, 1))
            }
            try {
                view.loadUrl("https://www.douyin.com/channel/$tagId")
                delay(HOOK_INJECT_DELAY_MS)
                view.evaluateJavascript(HOOK, null)
                repeat(SCROLL_ROUNDS) {
                    view.evaluateJavascript(
                        "window.scrollTo(0, document.body ? document.body.scrollHeight : 4000)", null,
                    )
                    delay(SCROLL_INTERVAL_MS)
                }
            } finally {
                runCatching { view.stopLoading() }
                runCatching { host?.removeView(view) }
                runCatching { view.destroy() }
                inFlight.set(false)
            }
            withContext(Dispatchers.Default) { parse(payloads.toList()) }
        }

    private fun create(activity: Activity, payloads: MutableList<String>): WebView {
        CookieManager.getInstance().setAcceptCookie(true)
        val cookie = AppRuntime.api?.cookies()?.cookieHeader().orEmpty()
        for (pair in cookie.split(";")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                CookieManager.getInstance().setCookie(
                    "https://www.douyin.com",
                    pair.substring(0, idx).trim() + "=" + pair.substring(idx + 1).trim(),
                )
            }
        }
        val view = WebView(activity)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
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
            val response = runCatching {
                api.decodeBody(json, FeedResponse::class.java, "/aweme/v2/web/module/feed/")
            }.getOrNull() ?: continue
            for (aweme in DataModels.filterPrimaryPlayableAwemes(response.flatAwemes())) {
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
