/*
 * 线 H：扫码登录的 Android WebView 宿主。
 *
 * 证据出处（src/ 反编译）：
 *  - src/app/p000/od2.java:273-350（m8562e：JS/DOM 开启、桌面 UA、CookieManager 三方 Cookie、WebViewClient）
 *  - src/app/p000/od2.java:79-122（m8558a：loadUrl + 60000ms 超时）
 *  - src/app/p000/od2.java:150-212（m8560c：evaluateJavascript + JSONTokener 双层解包）
 *  - src/app/p000/od2.java:125-146（m8559b：移除父容器后 destroy）
 *  - src/app/p000/sy3.java:18-34（onPageStarted/onPageFinished 各注入一次 HOOK_SCRIPT，幂等）
 *  - src/app/p000/ez3.java:469-491（m3962k：removeAllCookies/removeSessionCookies + flush，5s 超时）
 *
 * 关键点：钩子必须在 onPageStarted 就注入。原版 sy3 就是这么做的，否则页面在
 * onPageFinished 之前发出的 /passport/web/get_qrcode/ 请求会抓不到，二维码永远为空。
 *
 * QrLoginBridge 在后台线程按阻塞语义调用本类，因此所有 WebView/CookieManager 操作
 * 都投递到主线程，并用 FutureTask/CountDownLatch 把结果同步回调用线程。
 */
package com.github.axtv.dv.ui.login.qr

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import axtv.panel.QrLoginBridge
import org.json.JSONObject
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** 把 Context 链一路解到 Activity（Compose 的 LocalContext 可能是 ContextThemeWrapper）。 */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

class AndroidQrWebViewHost(activity: Activity) : QrLoginBridge.WebViewHost {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val activityRef = WeakReference(activity)
    private var webView: WebView? = null
    private var pageFinished: CountDownLatch? = null
    private var destroyed = false
    @Volatile private var pageStarted = false

    // ───────────────────────── QrLoginBridge.WebViewHost ─────────────────────────

    override fun loadUrl(url: String, timeoutMs: Long) {
        val latch = CountDownLatch(1)
        pageStarted = false
        callOnMain {
            val view = ensureWebView()
            pageFinished = latch
            Log.i(TAG, "loadUrl $url")
            view.loadUrl(url)
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            pageFinished = null
            // 模拟器/弱网下页面可能略超 60s 才 onPageFinished；只要 onPageStarted 已触发
            //（页面活着、钩子已注入），放行继续轮询，不作为失败。
            if (pageStarted) {
                Log.w(TAG, "loadUrl timeout (${timeoutMs}ms) but page already started, continue")
            } else {
                throw IOException("抖音登录页加载超时（${timeoutMs}ms）")
            }
        }
        pageFinished = null
    }

    override fun evaluateJavascript(script: String, timeoutMs: Long): String {
        // 原版 ez3.readCookie 用 document.cookie；HttpOnly 的 sessionid 在 JS 里看不到，
        // 这里改用 CookieManager（包含 HttpOnly），保证登录态判定与落库拿到完整 Cookie。
        if (script.trim() == "document.cookie") {
            // WebView.getUrl() 也必须在主线程调用，整段投递到主线程后再返回。
            val cookie = callOnMain { cookieHeaderNow() }
            Log.i(TAG, "read cookie: ${cookieNames(cookie)}")
            return JSONObject.quote(cookie)
        }
        val result = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        callOnMain {
            val view = ensureWebView()
            view.evaluateJavascript(script) { value ->
                result.set(value)
                latch.countDown()
            }
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw IOException("JS 执行超时（${timeoutMs}ms）")
        }
        return result.get() ?: "null"
    }

    override fun clearCookies(timeoutMs: Long) {
        // 主线程调用（如 stop()）时不再阻塞等待回调，避免卡住 UI；后台线程调用时等待落盘完成。
        if (Looper.myLooper() == Looper.getMainLooper()) {
            val manager = CookieManager.getInstance()
            manager.removeAllCookies {
                manager.removeSessionCookies {
                    manager.flush()
                    Log.i(TAG, "cleared cookies (main thread)")
                }
            }
            return
        }
        val latch = CountDownLatch(1)
        callOnMain {
            val manager = CookieManager.getInstance()
            manager.removeAllCookies {
                manager.removeSessionCookies {
                    manager.flush()
                    latch.countDown()
                }
            }
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "clearCookies callback timeout (${timeoutMs}ms)")
        } else {
            Log.i(TAG, "cleared cookies")
        }
    }

    override fun destroy() {
        destroyed = true
        callOnMain {
            val view = webView ?: return@callOnMain
            webView = null
            pageFinished?.countDown()
            runCatching { (view.parent as? ViewGroup)?.removeView(view) }
            runCatching { view.stopLoading() }
            runCatching { view.destroy() }
            Log.i(TAG, "webview destroyed")
        }
    }

    override fun tap(x: Double, y: Double) {
        callOnMain {
            val view = webView ?: return@callOnMain
            val now = android.os.SystemClock.uptimeMillis()
            val down = motionEvent(now, now, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat())
            val up = motionEvent(now, now + 50, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat())
            runCatching {
                view.dispatchTouchEvent(down)
                view.dispatchTouchEvent(up)
            }
            down.recycle()
            up.recycle()
        }
    }

    override fun setInteractive(interactive: Boolean) {
        callOnMain {
            val view = webView ?: return@callOnMain
            view.alpha = if (interactive) 1f else 0f
            view.isFocusable = interactive
            view.isClickable = interactive
            view.importantForAccessibility = if (interactive) {
                View.IMPORTANT_FOR_ACCESSIBILITY_YES
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            if (interactive) {
                view.bringToFront()
                view.requestFocus()
            } else {
                view.clearFocus()
            }
            Log.i(TAG, "webview interactive=$interactive")
        }
    }

    override fun isHostAvailable(): Boolean {
        val activity = activityRef.get() ?: return false
        return !destroyed && !activity.isFinishing && !activity.isDestroyed
    }

    // ───────────────────────── 内部实现 ─────────────────────────

    private fun ensureWebView(): WebView {
        webView?.let { return it }
        val activity = activityRef.get() ?: throw IllegalStateException("Activity 已销毁")
        val view = WebView(activity)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = desktopUserAgent(view)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 与 od2.m8562e 一致：允许第三方 Cookie（passport 域名下发 sessionid）
                CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        view.setBackgroundColor(Color.TRANSPARENT)
        view.alpha = 0f
        view.isFocusable = false
        view.isClickable = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        view.webViewClient = object : WebViewClient() {
            override fun onPageStarted(webView: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                Log.i(TAG, "onPageStarted $url")
                pageStarted = true
                // 原版 sy3.m8562e：页面开始就注入钩子，避免错过 get_qrcode 请求。
                webView.evaluateJavascript(QrLoginBridge.HOOK_SCRIPT, null)
            }

            override fun onPageFinished(webView: WebView, url: String?) {
                Log.i(TAG, "onPageFinished $url")
                webView.evaluateJavascript(QrLoginBridge.HOOK_SCRIPT, null)
                pageFinished?.countDown()
            }

            override fun shouldOverrideUrlLoading(webView: WebView, url: String?): Boolean {
                // 站内跳转放行；非 http(s) 的外链（intent://、snssdk:// 等）拦掉，避免 WebView 报错页。
                return url != null && !url.startsWith("http")
            }
        }
        runCatching {
            val decor = activity.window.decorView as? ViewGroup
            decor?.addView(view, 0, ViewGroup.LayoutParams(-1, -1))
        }
        webView = view
        Log.i(TAG, "webview created, ua=${view.settings.userAgentString}")
        return view
    }

    private fun desktopUserAgent(view: WebView): String {
        val default = runCatching { WebSettings.getDefaultUserAgent(view.context) }.getOrDefault("")
        val chrome = Regex("Chrome/(\\d+)").find(default)?.groupValues?.get(1) ?: "113"
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$chrome.0.0.0 Safari/537.36"
    }

    /**
     * 同时读 creator 与 www 两个域：扫码成功后 sessionid 可能只落在 www.douyin.com
     * （或 sso.douyin.com 之后的重定向域），只读 creator 会永远拿不到登录态。
     * 两个域按 Cookie 名合并（后读到的同名项覆盖先读到的），并分别打键名日志便于取证。
     */
    private fun cookieHeaderNow(): String {
        val manager = CookieManager.getInstance()
        val merged = LinkedHashMap<String, String>()
        for (url in COOKIE_URLS) {
            val cookie = try {
                manager.getCookie(url) ?: ""
            } catch (t: Throwable) {
                Log.w(TAG, "read cookie failed for $url", t)
                ""
            }
            Log.i(TAG, "cookie[$url]: ${cookieNames(cookie)}")
            for (part in cookie.split(";")) {
                val name = part.substringBefore('=').trim()
                if (name.isEmpty()) continue
                merged[name] = part.trim()
            }
        }
        return merged.values.joinToString("; ")
    }

    private fun cookieNames(cookie: String): String =
        cookie.split(";")
            .mapNotNull { it.substringBefore('=').trim().takeIf { name -> name.isNotEmpty() } }
            .joinToString(",")

    private fun motionEvent(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float): MotionEvent {
        val props = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1.0f
            size = 1.0f
        }
        return MotionEvent.obtain(downTime, eventTime, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1.0f, 1.0f, 0, 0, 0, 0)
    }

    private fun <T> callOnMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val task = FutureTask(block)
        mainHandler.post(task)
        return task.get()
    }

    companion object {
        private const val TAG = "AxTV_QR"
        /** 扫码登录态可能落在这两个域上，轮询时都读一遍再合并。 */
        private val COOKIE_URLS = listOf(
            "https://creator.douyin.com",
            "https://www.douyin.com",
        )
    }
}
