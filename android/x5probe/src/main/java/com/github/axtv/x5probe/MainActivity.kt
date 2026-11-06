/*
 * 私有测试探针（仅自用，不随主应用发布）：
 * 验证腾讯 X5 离线内核能否在电视上安装，并验证 X5 WebView 打开抖音频道页时
 * 页面自己的 module/feed 请求是否返回真实数据。
 */
package com.github.axtv.x5probe

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tencent.smtt.sdk.QbSdk
import com.tencent.smtt.sdk.TbsListener
import com.tencent.smtt.sdk.WebChromeClient
import com.tencent.smtt.sdk.WebView
import com.tencent.smtt.sdk.WebViewClient
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.NetworkInterface
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var statusScroll: ScrollView
    private lateinit var webHost: FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var systemWebView: android.webkit.WebView? = null
    private lateinit var logFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        logFile = File(getExternalFilesDir(null) ?: filesDir, "x5probe.log")
        appendFile("==== session start " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) + " ====")
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            appendFile("CRASH ${e.javaClass.name}: ${e.message}" + System.lineSeparator() + Log.getStackTraceString(e))
            defaultHandler?.uncaughtException(t, e)
        }
        startLogServer()
        log("日志文件: ${logFile.absolutePath}")
        log("设备: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}")
        log("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        log("系统 WebView UA: ${android.webkit.WebSettings.getDefaultUserAgent(this)}")
        initTbs()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { webView?.destroy() }
        runCatching { systemWebView?.destroy() }
        super.onDestroy()
    }

    private fun buildUi(): ViewGroup {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(24, 24, 24, 24)
        }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        statusScroll = ScrollView(this).apply { addView(status) }
        root.addView(
            statusScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        webHost = FrameLayout(this)
        root.addView(webHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)))
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 0)
        }
        buttons.addView(Button(this).apply {
            text = "重新检测/安装内核"
            setOnClickListener { initTbs() }
        })
        buttons.addView(Button(this).apply {
            text = "重新打开频道页"
            setOnClickListener { startWebTest(false) }
        })
        buttons.addView(Button(this).apply {
            text = "纯加载（不注入）"
            setOnClickListener { startWebTest(true, false) }
        })
        buttons.addView(Button(this).apply {
            text = "纯加载（移动 UA）"
            setOnClickListener { startWebTest(true, true) }
        })
        buttons.addView(Button(this).apply {
            text = "X5 移动 UA 完整"
            setOnClickListener { startWebTest(false, true) }
        })
        buttons.addView(Button(this).apply {
            text = "重启应用"
            setOnClickListener { restart() }
        })
        root.addView(buttons)
        val buttons2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        buttons2.addView(Button(this).apply {
            text = "X5 仅创建 WebView"
            setOnClickListener { testX5ConstructOnly() }
        })
        buttons2.addView(Button(this).apply {
            text = "系统 WebView 打开频道页"
            setOnClickListener { startSystemWebTest(false) }
        })
        buttons2.addView(Button(this).apply {
            text = "系统 WebView（移动 UA）"
            setOnClickListener { startSystemWebTest(true) }
        })
        root.addView(buttons2)
        val buttons3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        buttons3.addView(Button(this).apply {
            text = "X5 轻量纯加载"
            setOnClickListener { startWebTest(true, false, true) }
        })
        buttons3.addView(Button(this).apply {
            text = "X5 轻量完整"
            setOnClickListener { startWebTest(false, false, true) }
        })
        root.addView(buttons3)
        return root
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun log(message: String) {
        Log.i(TAG, message)
        appendFile(message)
        runOnUiThread {
            status.append(message + System.lineSeparator())
            statusScroll.post { statusScroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun startLogServer() {
        val nl = System.lineSeparator()
        Thread({
            try {
                ServerSocket(8766).use { server ->
                    val urls = localIps().joinToString(" ") { "http://" + it + ":8766/log" }
                    log("日志下载: " + urls)
                    while (true) {
                        server.accept().use { client ->
                            runCatching {
                                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                                val requestLine = reader.readLine().orEmpty()
                                val path = requestLine.split(" ").getOrNull(1).orEmpty()
                                val body = when {
                                    path.startsWith("/log") -> logFile.readText()
                                    path == "/" || path.isEmpty() -> "X5 probe log" + nl + "GET /log" + nl
                                    else -> "not found"
                                }
                                val bytes = body.toByteArray(Charsets.UTF_8)
                                val header = "HTTP/1.1 200 OK" + nl +
                                    "Content-Type: text/plain; charset=utf-8" + nl +
                                    "Content-Length: " + bytes.size + nl +
                                    "Connection: close" + nl + nl
                                val out = client.getOutputStream()
                                out.write(header.toByteArray(Charsets.UTF_8))
                                out.write(bytes)
                                out.flush()
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "log server stopped: " + t.javaClass.simpleName + ": " + t.message)
            }
        }, "x5probe-log-server").apply { isDaemon = true; start() }
    }


    private fun localIps(): List<String> {
        val out = mutableListOf<String>()
        runCatching {
            for (nif in java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        out.add(addr.hostAddress.orEmpty())
                    }
                }
            }
        }
        return out
    }

    private fun appendFile(message: String) {
        runCatching {
            logFile.appendText("[" + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()) + "] " + message + System.lineSeparator())
        }
    }

    private fun initTbs() {
        log("---- initX5Environment ----")
        QbSdk.setDownloadWithoutWifi(true)
        runCatching { QbSdk.setCoreMinVersion(QbSdk.CORE_VER_ENABLE_202207) }
        QbSdk.setTbsListener(object : TbsListener {
            override fun onDownloadFinish(code: Int) = log("tbs downloadFinish=$code")
            override fun onInstallFinish(code: Int) {
                log("tbs installFinish=$code" + if (code == 200) "（成功，请重启应用）" else "")
                if (code == 200) {
                    log("内核已安装，点“重启应用”后再测试频道页")
                    val cached = File(cacheDir, "tbs_core_release.tbs.apk")
                    if (cached.exists()) {
                        log("删除缓存内核: ${cached.length()} bytes")
                        cached.delete()
                    }
                }
            }
            override fun onDownloadProgress(progress: Int) {
                if (progress % 20 == 0) log("tbs downloadProgress=$progress%")
            }
        })
        QbSdk.initX5Environment(this, object : QbSdk.PreInitCallback {
            override fun onCoreInitFinished() = log("coreInitFinished")
            override fun onViewInitFinished(isX5: Boolean) {
                val version = QbSdk.getTbsVersion(this@MainActivity)
                log("viewInitFinished isX5=$isX5 tbsVersion=$version canLoadX5=${QbSdk.canLoadX5(this@MainActivity)}")
                if (isX5) {
                    log("X5 内核已加载；按“重新打开频道页”开始取数测试")
                } else if (version > 0) {
                    log("tbsVersion>0 但 isX5=false，暂不创建 WebView")
                } else {
                    installOffline()
                }
            }
        })
    }

    /** 线上内核不可用时，安装打包在 assets 里的离线内核。 */
    private fun installOffline() {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val asset = when {
            abi.startsWith("arm64") -> "x5/arm64_v8a/tbs_core_release.tbs.apk"
            abi.startsWith("armeabi") -> "x5/armeabi_v7a/tbs_core_release.tbs.apk"
            else -> {
                log("没有匹配 ABI 的内核: $abi")
                return
            }
        }
        val out = File(cacheDir, "tbs_core_release.tbs.apk")
        try {
            if (!out.exists() || out.length() == 0L) {
                assets.open(asset).use { input -> out.outputStream().use { input.copyTo(it) } }
            }
            log("离线内核已解压: ${out.length()} bytes ($asset)")
            QbSdk.installLocalTbsCore(applicationContext, CORE_VERSION, out.absolutePath)
            log("installLocalTbsCore 已调用 (core=$CORE_VERSION)")
        } catch (t: Throwable) {
            log("本地安装异常: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun startWebTest(pureLoad: Boolean = false, mobileUa: Boolean = false, light: Boolean = false) {
        log("startWebTest pure=$pureLoad")
        if (!QbSdk.canLoadX5(this) && QbSdk.getTbsVersion(this) == 0) {
            log("X5 内核尚未可用，先完成安装/重启")
            return
        }
        runCatching { webView?.destroy() }
        runCatching { systemWebView?.destroy() }
        webHost.removeAllViews()
        log("before X5 WebView()")
        val view = try {
            WebView(this)
        } catch (t: Throwable) {
            log("X5 WebView 创建失败: ${t.javaClass.simpleName}: ${t.message}")
            return
        }
        log("after X5 WebView()")
        log("before X5 settings")
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        if (light) {
            log("before light settings")
            view.settings.apply {
                loadsImagesAutomatically = false
                blockNetworkImage = true
                mediaPlaybackRequiresUserGesture = true
            }
            log("before software layer")
            runCatching { view.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null) }
            log("after software layer")
        }
        log("after X5 settings")
        val mi = android.app.ActivityManager.MemoryInfo()
        (getSystemService(android.app.ActivityManager::class.java)).getMemoryInfo(mi)
        log("内存: avail=${mi.availMem / 1048576}MB total=${mi.totalMem / 1048576}MB low=${mi.lowMemory}")
        log("模式: " + (if (pureLoad) "纯加载" else "完整") + (if (light) " + 轻量（禁图/禁媒体/软渲染）" else ""))
        val defaultUa = view.settings.userAgentString
        val chrome = Regex("Chrome/(\\d+)").find(defaultUa)?.groupValues?.get(1) ?: "77"
        val desktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$chrome.0.0.0 Safari/537.36"
        view.settings.userAgentString = desktopUa
        log("X5 默认 UA: $defaultUa")
        log("使用 UA: $desktopUa")
        if (!pureLoad) view.addJavascriptInterface(Bridge(), "AxFeed")
        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: com.tencent.smtt.export.external.interfaces.ConsoleMessage?): Boolean {
                if (msg != null && msg.message().startsWith("X5PROBE")) log(msg.message())
                return true
            }

            override fun onJsPrompt(
                v: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: com.tencent.smtt.export.external.interfaces.JsPromptResult?,
            ): Boolean {
                if (message != null && message.startsWith("AXFEED_SUM:")) {
                    log(message.removePrefix("AXFEED_SUM:"))
                    result?.confirm("ok")
                    return true
                }
                return super.onJsPrompt(v, url, message, defaultValue, result)
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView?, url: String?) {
                log("pageFinished: $url")
                v?.evaluateJavascript(HOOK, null)
            }
        }
webHost.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        webView = view
        log("加载频道页 ...")
        view.loadUrl(CHANNEL_URL)
        log("loadUrl 已调用")
        if (!pureLoad) {
            handler.postDelayed({
                view.evaluateJavascript(HOOK, null)
                log("hook injected (t+3s)")
            }, 3_000)
            for (i in 1..3) {
                handler.postDelayed({
                    view.evaluateJavascript(
                        "window.scrollTo(0, document.body ? document.body.scrollHeight : 4000); 'ok'",
                        null,
                    )
                    log("scroll $i")
                }, 3_000L * (i + 3))
            }
            handler.postDelayed({
                view.evaluateJavascript(PERF_DUMP) { result -> log("PERF $result") }
            }, 18_000)
        }
        handler.postDelayed({
            log("测试窗口结束：url=${view.url} tbsVersion=${QbSdk.getTbsVersion(this)}")
        }, 22_000)
    }

    private fun testX5ConstructOnly() {
        log("construct-only: before X5 WebView()")
        val v = try {
            WebView(this)
        } catch (t: Throwable) {
            log("construct-only: error ${t.javaClass.simpleName}: ${t.message}")
            return
        }
        log("construct-only: after X5 WebView()")
        val ua = runCatching { v.settings.userAgentString }.getOrNull()
        log("construct-only: ua=" + ua)
        runCatching { v.destroy() }
        log("construct-only: destroyed")
    }

    private fun startSystemWebTest(mobileUa: Boolean = false) {
        log("systemWeb: start")
        runCatching { systemWebView?.destroy() }
        webHost.removeAllViews()
        val v = try {
            android.webkit.WebView(this)
        } catch (t: Throwable) {
            log("systemWeb: construct error ${t.javaClass.simpleName}: ${t.message}")
            return
        }
        v.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            loadsImagesAutomatically = false
            blockNetworkImage = true
            if (android.os.Build.VERSION.SDK_INT >= 17) {
                mediaPlaybackRequiresUserGesture = true
            }
        }
        val defaultUa = v.settings.userAgentString
        if (mobileUa) {
            log("systemWeb: UA=移动 UA（不覆盖）")
        } else {
            val chrome = Regex("Chrome/([0-9]+)").find(defaultUa)?.groupValues?.get(1) ?: "66"
            v.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/" + chrome + ".0.0.0 Safari/537.36"
            log("systemWeb: UA=" + v.settings.userAgentString)
        }
        v.addJavascriptInterface(Bridge(), "AxFeed")
        v.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                if (msg != null && msg.message().startsWith("X5PROBE")) log(msg.message())
                return true
            }

            override fun onJsPrompt(
                view: android.webkit.WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: android.webkit.JsPromptResult?,
            ): Boolean {
                if (message != null && message.startsWith("AXFEED_SUM:")) {
                    log(message.removePrefix("AXFEED_SUM:"))
                    result?.confirm("ok")
                    return true
                }
                return super.onJsPrompt(view, url, message, defaultValue, result)
            }
        }
        v.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                log("systemWeb: pageFinished $url")
                view?.evaluateJavascript(HOOK, null)
            }

            override fun shouldInterceptRequest(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?,
            ): android.webkit.WebResourceResponse? {
                val u = request?.url?.toString().orEmpty()
                if (u.contains("aweme") || u.contains("/feed") || u.contains("channel")) {
                    log("systemWeb REQ " + u.take(180))
                }
                return null
            }
        }
        webHost.addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        systemWebView = v
        log("systemWeb: loadUrl")
        v.loadUrl(CHANNEL_URL)
        handler.postDelayed({ v.evaluateJavascript(HOOK, null); log("systemWeb: hook injected") }, 3_000)
        for (i in 1..3) {
            handler.postDelayed({
                v.evaluateJavascript("window.scrollTo(0, document.body ? document.body.scrollHeight : 4000); 'ok'", null)
                log("systemWeb: scroll " + i)
            }, 3_000L * (i + 3))
        }
        handler.postDelayed({ v.evaluateJavascript(PERF_DUMP) { r -> log("systemWeb PERF " + r) } }, 18_000)
    }

    private fun restart() {
        val intent = android.content.Intent(this, MainActivity::class.java)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        handler.postDelayed({ Runtime.getRuntime().exit(0) }, 1_000)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onFeed(json: String) {
            runOnUiThread {
                try {
                    val obj = JSONObject(json)
                    val list = obj.optJSONArray("aweme_list")
                    val count = list?.length() ?: 0
                    val first = list?.optJSONObject(0)?.optString("desc").orEmpty().take(48)
                    log("FEED status=${obj.optInt("status_code")} n=$count more=${obj.optBoolean("has_more")} len=${json.length} first=$first")
                } catch (t: Throwable) {
                    log("FEED 解析失败: ${t.message} len=${json.length}")
                }
            }
        }
    }

    private companion object {
        const val TAG = "X5PROBE"
        const val CORE_VERSION = 46285
        const val CHANNEL_URL = "https://www.douyin.com/channel/300205"

        val PERF_DUMP = "(function(){try{var es=performance.getEntriesByType('resource');var out=[];for(var i=0;i<es.length;i++){var n=es[i].name;if(/aweme|feed|channel|api/i.test(n)){out.push(n.split('?')[0]+' t='+es[i].transferSize);}}return JSON.stringify({total:es.length,hits:out.slice(0,30)});}catch(e){return 'PERR '+e;}})()"

        val HOOK = """
(function(){
  if (window.__axProbeHooked) return; window.__axProbeHooked = true;
  function report(t, u){
    var path = String(u || '').split('?')[0];
    try { if (window.AxFeed && t) window.AxFeed.onFeed(t); } catch(e) {}
    try {
      var sum = '';
      try {
        var j = JSON.parse(t);
        var a = j.aweme_list || [];
        var first = a.length ? String(a[0].desc || '').slice(0, 20) : '';
        sum = ' url=' + path + ' code=' + j.status_code + ' n=' + a.length + ' more=' + j.has_more + ' len=' + t.length + ' first=' + first;
      } catch(e2) { sum = ' url=' + path + ' parse=no len=' + t.length + ' head=' + String(t).slice(0, 60); }
      window.prompt('AXFEED_SUM:' + sum, '');
    } catch(e3) {}
  }
  var X = XMLHttpRequest.prototype, oo = X.open, os = X.send;
  X.open = function(m, u){ this.__u = String(u); return oo.apply(this, arguments); };
  X.send = function(b){
    try { this.addEventListener('load', function(){
      try { var u = this.__u || ''; if (u.indexOf('/module/feed/') >= 0) {
        var t = this.response;
        if (t && typeof t === 'object' && !(t instanceof ArrayBuffer)) { try { t = JSON.stringify(t); } catch(e) { t = ''; } }
        else if (typeof t !== 'string') { try { t = new TextDecoder('utf-8').decode(t); } catch(e) { t = ''; } }
        report(t || '', u);
      } } catch(e) {}
    }); } catch(e) {}
    return os.apply(this, arguments);
  };
  var f = window.fetch;
  if (f) {
    window.fetch = function(i, init){
      var u = typeof i === 'string' ? i : ((i && i.url) || '');
      var p = f.apply(this, arguments);
      if (u.indexOf('/module/feed/') >= 0) {
        p.then(function(r){ r.clone().text().then(function(t){ report(t || '', u); }); });
      }
      return p;
    };
  }
})();
"""
    }
}
