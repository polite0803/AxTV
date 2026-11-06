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
import android.net.Uri
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
import java.util.Locale
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
    /** 主帧加载失败（ERR_NAME_NOT_RESOLVED / ERR_CONNECTION_* 等），用来把真实原因带出 WebView。 */
    @Volatile private var mainFrameError: String? = null
    /**
     * 浮出的登录页里按 BACK：交给宿主收起页面。
     * WebView 自己会把 BACK 当成「网页历史返回」，验证页没有历史就根本退不出来。
     */
    var onBackFromPage: (() -> Unit)? = null

    // ───────────────────────── QrLoginBridge.WebViewHost ─────────────────────────

    override fun loadUrl(url: String, timeoutMs: Long) {
        val latch = CountDownLatch(1)
        pageStarted = false
        mainFrameError = null
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
                val reason = mainFrameError
                throw IOException(
                    if (reason != null) {
                        "抖音登录页加载失败（$reason）"
                    } else {
                        "抖音登录页加载超时（${timeoutMs}ms）"
                    },
                )
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

    /**
     * 把隐藏 WebView 变成可操作的验证页（或收回去）。
     *
     * 浮出时必须同时开 focusableInTouchMode：Android 只在 touch mode 关闭时才让方向键在网页
     * 元素之间移焦点，电视上根本没有触摸，不显式退出 touch mode 就永远聚焦不进去。
     * 收回时不能只把 alpha 置 0：bringToFront 已经把它挪到 decor 末尾，不送回索引 0 的话，
     * 一层 alpha=0 的透明 WebView 压在最上面，会把 Compose 的点击全吃掉。
     */
    override fun setInteractive(interactive: Boolean) {
        callOnMain {
            val view = webView ?: return@callOnMain
            view.alpha = if (interactive) 1f else 0f
            view.isFocusable = interactive
            view.isFocusableInTouchMode = interactive
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
                (view.parent as? ViewGroup)?.let { parent ->
                    // 已在索引 0 时不要 remove/add：重复挂载会重置页面 JS 计时器与验证态。
                    if (parent.indexOfChild(view) != 0) {
                        runCatching {
                            parent.removeView(view)
                            parent.addView(view, 0)
                        }.onFailure { Log.w(TAG, "restore webview stacking failed", it) }
                    }
                }
            }
            Log.i(TAG, "webview interactive=$interactive")
        }
    }

    override fun isHostAvailable(): Boolean {
        val activity = activityRef.get() ?: return false
        return !destroyed && !activity.isFinishing && !activity.isDestroyed
    }

    /** 浏览器内核标识（如 "Chrome/143.0.7499.24"），登录页诊断用。 */
    override fun engineInfo(): String {
        val version = runCatching { WebView.getCurrentWebViewPackage()?.versionName }
            .getOrNull()
        return if (version.isNullOrBlank()) "unknown" else "Chrome/$version"
    }

    /**
     * 本机是否有可用的 WebView。
     *
     * 定制 ROM / 电视盒子可能把 WebView 裁掉：`new WebView(context)` 会直接抛
     * MissingWebViewPackageException / AndroidRuntimeException / NoClassDefFoundError。
     * 用「真的建一个」来判断，比查包名可靠；失败时桥自动切到纯 HTTP 通道。
     */
    override fun isUsable(): Boolean = try {
        callOnMain { runCatching { ensureWebView() }.isSuccess }
    } catch (t: Throwable) {
        Log.w(TAG, "webview unavailable, fall back to http", t)
        false
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
            // 与 od2.m8562e 一致用 COMPATIBILITY：登录/验证页会夹带 http 子资源，
            // 收紧成 NEVER_ALLOW 会让老电视上的验证组件加载不出来（表现为验证窗口不出现）。
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
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
        view.setOnKeyListener { target, keyCode, event ->
            // 只在页面可交互（浮出）时接管 BACK；隐藏态下 BACK 仍然交给 Compose 导航。
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK &&
                event.action == android.view.KeyEvent.ACTION_UP && target.isFocusable
            ) {
                onBackFromPage?.invoke()
                true
            } else {
                false
            }
        }
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

            /**
             * 只记主帧错误：子资源（图片/埋点）失败不该让登录页判死，电视盒子常见的
             * ERR_NAME_NOT_RESOLVED / ERR_CONNECTION_TIMED_OUT 都从这里暴露出来。
             */
            override fun onReceivedError(
                webView: WebView,
                request: android.webkit.WebResourceRequest,
                error: android.webkit.WebResourceError,
            ) {
                if (!request.isForMainFrame) return
                val detail = "err=${error.errorCode} ${error.description}"
                mainFrameError = detail
                Log.w(TAG, "main frame error: $detail url=${request.url}")
            }

            /**
             * 只拦非 http(s) 的自定义 scheme（intent://、snssdk:// 等会跳出应用）。
             *
             * 这里刻意不做域名白名单：二次验证/中间跳转会落在 passport、sso、verify、snssdk
             * 等不同域名上，按域名拦会把验证页直接挡掉——真机表现就是扫码后一直停在
             * 「已扫码」，页面上什么也不出现。主帧 URL 由 onPageStarted 记录，追查不受影响。
             */
            override fun shouldOverrideUrlLoading(webView: WebView, url: String?): Boolean {
                if (url == null) return true
                val scheme = runCatching { Uri.parse(url).scheme }.getOrNull()
                val allowed = scheme.equals("http", ignoreCase = true) ||
                    scheme.equals("https", ignoreCase = true)
                if (!allowed) Log.i(TAG, "block non-http navigation: $url")
                return !allowed
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

    /**
     * 登录页使用桌面 Chrome UA。
     *
     * 与反编译原版 od2.m8562e 完全一致：从系统默认 UA 里取 Chrome 主版本，再拼成桌面 UA。
     * 原版在极米这类 Chrome/66 的老内核上能正常出码，说明抖音登录页对 66 是兼容的；
     * 这里不要“贴心”地改成 147，否则站点会下发老内核跑不动的新页面。
     */
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
            // 扫码确认后 sessionid 可能先落在裸域或 sso 域，再随重定向种到 www：
            // 只读前两个域时，真机会出现「手机已确认、电视永远拿不到登录态」。
            "https://douyin.com",
            "https://sso.douyin.com",
        )
    }
}
