/*
 * 线 H：登录页（扫码登录 + Cookie 手动导入 + 登录态验证 + 登出）。
 *
 * 规格出处：
 *  - refactor/panel/NOTES.md §C（原版扫码登录 = 隐藏 WebView 注入钩子）
 *  - refactor/panel/axtv/panel/QrLoginBridge.java（状态机、注入脚本、轮询节奏）
 *  - src/app/p000/ez3.java:509-655（m3963l：创建 WebView → 20s 首发二维码 → 等扫码）
 *  - src/app/p000/ez3.java:181-186 / 233-238 / 397-402（m3955b/m3957d/m3960i 均 jadx 溢出）
 *  - src/app/p000/od2.java:273-350（m8562e：WebView 配置与 UA）
 *  - src/app/p000/sy3.java:18-34（onPageStarted/onPageFinished 注入钩子）
 *  - refactor/panel/axtv/panel/AuthStore.java:55-83（登录态判定：sessionid / sessionid_ss 非空）
 *  - android/.../api/SharedPrefsCookieStore.kt（SP 文件 douyin_auth，键 cookie_header）
 *  - refactor/api/axtv/api/UserApi.java:31-37（profileSelf 复核登录态）
 *  - refactor/api/axtv/api/SetCookie.java:19-36（按 ";" 切分、首个 '=' 拆 name/value）
 *
 * 扫码登录成功后，Cookie 先写入 SharedPrefsCookieStore，再用 profileSelf() 复核；
 * 复核通过才算登录成功，失败会恢复原 Cookie 并如实提示，不用手动 Cookie 冒充扫码结果。
 */
package com.github.axtv.dv.ui.login

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import axtv.api.ApiException
import axtv.api.UserApi
import axtv.panel.QrLoginBridge
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.panel.PanelRuntime
import com.github.axtv.dv.api.SharedPrefsCookieStore
import com.github.axtv.dv.ui.featured.FeaturedRepository
import com.github.axtv.dv.ui.home.HomeFeedRepository
import com.github.axtv.dv.ui.home.FollowFeedRepository
import com.github.axtv.dv.ui.home.MobileFeedRepository
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.ui.login.qr.AndroidQrWebViewHost
import com.github.axtv.dv.ui.login.qr.findActivity
import com.github.axtv.dv.ui.settings.QrLink
import com.github.axtv.dv.ui.settings.QrLinksDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap
import java.util.Locale

/** 登录态验证结果。 */
private sealed interface LoginState {
    data object Idle : LoginState
    data object Verifying : LoginState
    data class Success(val nickname: String, val uid: String) : LoginState
    data class Failure(val message: String) : LoginState
}

/** 扫码阶段（对应 QrLoginBridge.Status）。 */
private enum class QrPhase { Idle, Starting, Waiting, Verifying, Success, Failed }

/** 短信验证码最长几位：页面输入框普遍 4-8 位，多出来的按键忽略即可。 */
private const val VERIFY_CODE_MAX_LENGTH = 8

/** 扫码区 UI 状态：二维码 PNG 在 Android 侧解码成 Bitmap 后展示。 */
private data class QrUiState(
    val phase: QrPhase = QrPhase.Idle,
    val message: String = "",
    val bitmap: Bitmap? = null,
    /** 界面这一帧二维码的更新时间（毫秒），用于展示「最近刷新 X 秒前」。 */
    val freshAtMs: Long = 0L,
    /** 验证页上渲染了验证码输入框 → 弹出应用侧数字键盘代填。 */
    val codeInput: Boolean = false,
)

/** 扫码链路日志标签（logcat 过滤：adb logcat -s AxTV_QR）。 */
private const val TAG_QR = "AxTV_QR"

/** Cookie 属性名（粘贴完整 Set-Cookie 行时需要剔除）。 */
internal val COOKIE_ATTRIBUTES = setOf(
    "path", "domain", "expires", "max-age", "secure", "httponly",
    "samesite", "priority", "partitioned", "comment", "version",
)

@Composable
fun LoginScreen(onBack: () -> Unit, onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cookieStore = remember(context) { SharedPrefsCookieStore(context) }
    var cookieInput by remember { mutableStateOf("") }
    var savedCookie by remember { mutableStateOf(cookieStore.cookieHeader()) }
    var state by remember { mutableStateOf<LoginState>(LoginState.Idle) }
    var notice by remember { mutableStateOf("") }
    var qrState by remember { mutableStateOf(QrUiState()) }
    var qrBridge by remember { mutableStateOf<QrLoginBridge?>(null) }
    var qrHost by remember { mutableStateOf<QrLoginBridge.WebViewHost?>(null) }
    /** 本机 WebView 是否可用；不可用时桥走纯 HTTP 通道，界面上不该再出现「显示登录页」。 */
    var webViewUsable by remember { mutableStateOf(true) }
    /** 上一帧二维码内容的哈希：页面换码后桥会推新帧，据此刷新「最近刷新」时间。 */
    var lastQrFrameHash by remember { mutableStateOf(0) }
    /** 每秒跳一次的时钟，仅用于展示「最近刷新 X 秒前」。 */
    var qrClockMs by remember { mutableStateOf(0L) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val fieldRequester = remember { FocusRequester() }
    val qrRequester = remember { FocusRequester() }
    // 二维码显示边长固定 480px（1080p 电视上仍好扫），保证整页 1080p 一屏放下、不滚动。
    val qrSide = with(LocalDensity.current) { 480.toDp() }
    /** 风控把扫码转成短信验证码时，应用侧数字键盘收集的那串数字。 */
    var verifyCode by remember { mutableStateOf("") }
    /** 一屏能看完的登录诊断（内核 / 页面 / 二维码 / 轮询），用户截图即可定位问题。 */
    var diagnosticText by remember { mutableStateOf("") }
    val keypadVisible = qrState.phase == QrPhase.Verifying && qrState.codeInput

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        // 初始焦点给「生成二维码」：电视端遥控器可直接 OK 触发；输入框方向键会吞掉焦点，
        // 若把初始焦点放在输入框，用户无法用方向键走到扫码按钮。
        runCatching { qrRequester.requestFocus() }
    }

    // 验证窗口出现时清空上次的输入；收起时把焦点交还给扫码按钮 —— 浮出的登录页 WebView
    // 收回去以后 Compose 侧是焦点空洞，不抢一次的话遥控器按键没人接。
    LaunchedEffect(keypadVisible) {
        if (keypadVisible) {
            verifyCode = ""
        } else {
            withFrameNanos { }
            runCatching { qrRequester.requestFocus() }
        }
    }

    // 诊断信息每秒刷新一次：扫码登录依赖页面内 SDK，出问题时这是最有用的现场证据。
    LaunchedEffect(Unit) {
        while (true) {
            diagnosticText = qrBridge?.diagnostic().orEmpty()
            delay(1000)
        }
    }

    // 同上：浮出的登录页收起后阶段回到 Waiting，也要把焦点拉回扫码区。
    LaunchedEffect(qrState.phase) {
        if (qrState.phase != QrPhase.Verifying) {
            withFrameNanos { }
            runCatching { qrRequester.requestFocus() }
        }
    }

    // 等待扫码时每秒推进一次时钟，让「最近刷新 X 秒前」跟着走。
    LaunchedEffect(qrState.phase) {
        if (qrState.phase == QrPhase.Waiting) {
            while (true) {
                qrClockMs = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    // 离开页面（BACK / 切 Tab）时中断扫码线程并销毁隐藏 WebView，避免后台常驻。
    DisposableEffect(Unit) {
        onDispose {
            val bridge = qrBridge
            qrBridge = null
            qrHost = null
            if (bridge != null) {
                bridge.setListener(null)
                // stop() 里的清 Cookie/销毁都在后台线程做，不在主线程等 5s 回调。
                Thread { bridge.stop() }.start()
            }
        }
    }

    fun stopQrSession() {
        val bridge = qrBridge
        qrBridge = null
        qrHost = null
        if (bridge != null) {
            // 先摘掉监听，避免 stop() 发布的 IDLE 覆盖成功文案。
            bridge.setListener(null)
            Thread { bridge.stop() }.start()
        }
        qrState = QrUiState()
        lastQrFrameHash = 0
    }

    /**
     * 把 Cookie 落库并用 profileSelf() 复核；只有拿到非空 user 才回调 onLoggedIn()。
     *
     * @param fromQr 扫码路径：失败时保留二维码便于重试，文案明确「扫码完成但复核未通过」。
     */
    fun applyCookie(normalized: String, successNotice: String, fromQr: Boolean) {
        val api = AppRuntime.api
        if (api == null) {
            state = LoginState.Failure("接口组件尚未就绪，请重启应用后重试")
            return
        }
        notice = ""
        state = LoginState.Verifying
        val lastQrBitmap = qrState.bitmap
        scope.launch {
            val outcome: Result<LoginState> = withContext(Dispatchers.IO) {
                val previous = cookieStore.cookieHeader()
                // 先落库再验证：DouyinApi 持有的 CookieStore 每次请求都从同一份 SP 读取。
                cookieStore.setCookieHeader(normalized)
                try {
                    val response = UserApi(api).profileSelf()
                    val user = response.user()
                    if (user == null) {
                        cookieStore.setCookieHeader(previous)
                        Result.failure(
                            IllegalStateException(
                                "接口未返回用户资料：Cookie 无效、不完整或已过期（需要 sessionid / sessionid_ss）",
                            ),
                        )
                    } else {
                        val nickname = user.nickname()
                        val uid = user.uid()
                        if (uid.isNotBlank()) cookieStore.applySelfUserId(uid)
                        // 同步核心层 Settings（axtv_settings.cookie_string），
                        // 供 Settings.isLoggedIn()/设置页 Cookie 行读取同一登录态。
                        AppRuntime.settings?.setCookie(normalized)
                        Result.success(LoginState.Success(nickname.ifBlank { uid.ifBlank { "抖音用户" } }, uid))
                    }
                } catch (e: ApiException) {
                    cookieStore.setCookieHeader(previous)
                    Result.failure(IllegalStateException(describe(e), e))
                } catch (t: Throwable) {
                    cookieStore.setCookieHeader(previous)
                    Result.failure(t)
                }
            }
            outcome.onSuccess { result ->
                savedCookie = normalized
                state = result
                notice = successNotice
                if (fromQr) {
                    val nickname = (result as? LoginState.Success)?.nickname ?: "抖音用户"
                    stopQrSession()
                    qrState = QrUiState(QrPhase.Success, "扫码登录成功：$nickname", lastQrBitmap)
                }
                // 必须在返回上一页之前清缓存：首页读的是进程内快照，
                // 晚一步就会把登录前那一屏当成新推荐展示出来。
                HomeFeedRepository.invalidate()
                FollowFeedRepository.invalidate()
                MobileFeedRepository.invalidate()
                FeaturedRepository.invalidate()
                onLoggedIn()
            }.onFailure { error ->
                val message = error.message?.takeIf { it.isNotBlank() } ?: "验证失败，请稍后重试"
                state = LoginState.Failure(message)
                if (fromQr) {
                    qrState = qrState.copy(
                        phase = QrPhase.Failed,
                        message = "扫码已完成，但登录态复核未通过：$message（可点「重新生成二维码」再试）",
                    )
                }
            }
        }
    }

    fun verify() {
        val normalized = normalizeCookieInput(cookieInput)
        if (normalized.isEmpty()) {
            state = LoginState.Failure("未能从输入内容解析出可用的 k=v Cookie 项，请粘贴完整的 Cookie 请求头")
            return
        }
        applyCookie(normalized, "验证通过，Cookie 已写入 douyin_auth 并同步到设置存储", fromQr = false)
    }

    fun startQrLogin() {
        // 重新开始前先结束上一轮（旧线程自行清理 Cookie / WebView）。
        val previous = qrBridge
        qrBridge = null
        if (previous != null) {
            previous.setListener(null)
            Thread { previous.stop() }.start()
        }
        val activity = context.findActivity()
        if (activity == null) {
            qrState = QrUiState(QrPhase.Failed, "当前界面不可用（未取到 Activity），请停留在应用内再点扫码登录")
            return
        }
        val host = AndroidQrWebViewHost(activity)
        val bridge = QrLoginBridge(host)
        webViewUsable = host.isUsable()
        // 浮出的登录页里按 BACK：收起页面（WebView 自己会把它当成网页历史返回）。
        host.onBackFromPage = { bridge.requestVerificationDismiss() }
        bridge.setListener { st ->
            mainHandler.post {
                val bytes = st.qrImage()
                Log.i(
                    TAG_QR,
                    "status=${st.status()} message=${st.message()} qrBytes=${bytes?.size ?: 0}",
                )
                val bitmap = bytes?.let { data ->
                    runCatching { BitmapFactory.decodeByteArray(data, 0, data.size) }.getOrNull()
                }
                // 只有内容变化才算「新的一帧」；桥每秒都会回推同一帧，不能每次都刷新时间。
                val frameHash = bytes?.contentHashCode() ?: 0
                val isNewFrame = bytes != null && frameHash != lastQrFrameHash
                if (isNewFrame) lastQrFrameHash = frameHash
                val phase = when (st.status()) {
                    QrLoginBridge.Status.STARTING -> QrPhase.Starting
                    QrLoginBridge.Status.WAITING_SCAN, QrLoginBridge.Status.SCANNED -> QrPhase.Waiting
                    QrLoginBridge.Status.VERIFY -> QrPhase.Verifying
                    QrLoginBridge.Status.SUCCESS -> QrPhase.Success
                    QrLoginBridge.Status.EXPIRED, QrLoginBridge.Status.FAILED -> QrPhase.Failed
                    else -> QrPhase.Idle
                }
                qrState = QrUiState(
                    phase = phase,
                    message = st.message(),
                    bitmap = bitmap ?: qrState.bitmap.takeIf {
                        phase == QrPhase.Waiting || phase == QrPhase.Failed || phase == QrPhase.Verifying
                    },
                    freshAtMs = if (isNewFrame) System.currentTimeMillis() else qrState.freshAtMs,
                    codeInput = st.codeInput(),
                )
                if (st.status() == QrLoginBridge.Status.SUCCESS) {
                    applyCookie(st.cookieHeader(), "扫码登录成功，Cookie 已写入 douyin_auth", fromQr = true)
                }
            }
        }
        qrHost = host
        qrBridge = bridge
        lastQrFrameHash = 0
        qrState = QrUiState(QrPhase.Starting, "正在打开抖音登录页…")
        bridge.start()
    }

    fun logout() {
        stopQrSession()
        state = LoginState.Idle
        cookieInput = ""
        // 要求：登出清空 douyin_auth（cookie_header / user_unique_id / self_user_id 一并清掉）。
        // 只删登录相关键：douyin_auth 里还存着 device_id / iid 等设备级匿名身份，
        // 整体 clear() 会让下次启动换一套设备标识，容易触发风控。
        val authPrefs = context.getSharedPreferences("douyin_auth", android.content.Context.MODE_PRIVATE)
        val authEditor = authPrefs.edit()
        for (key in authPrefs.all.keys) {
            if (key == "cookie_header" || key == "cookie_string" ||
                key == "user_unique_id" || key == "self_user_id" ||
                key.startsWith("interaction_")
            ) {
                authEditor.remove(key)
            }
        }
        authEditor.apply()
        // WebView 的 CookieManager 是独立 cookie 罐：只清 SP 不清它，登出后再打开登录页/精选页
        // 仍带着旧会话（扫码桥只在下一次扫码开始时才清）。回调传 null 即可，落盘用 flush 兜住。
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        savedCookie = ""
        // accounts_list 里也存着各账号的 cookie / privateKey / ticket；登出必须一并清掉，
        // 否则“已登出”的设备仍可被导出登录态。互动凭据同理。
        AppRuntime.settings?.let { settings ->
            settings.setCookie("")
            settings.clearInteraction()
            settings.setAccounts(emptyList())
            settings.setCurrentAccountId("")
        }
        notice = "已退出登录，本机登录凭据已清空"
        HomeFeedRepository.invalidate()
        FeaturedRepository.invalidate()
    }

    // 手机推送：电视内核不可用时，用手机登录抖音网页版再把 Cookie 推进来（不依赖 WebView 版本）。
    val phonePushUrl = PanelRuntime.controller?.cookieSubmitUrl()
    var showPhonePush by remember { mutableStateOf(false) }

    // 整页套一层 Box：验证码键盘要能盖住二维码区。电视整屏只有约 540dp 高，
    // 把键盘内联排在二维码下面会直接溢出屏幕。
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ───────────────── 二维码区块置顶：480px 边长 + 32dp 白边，一屏内固定不滚动 ─────────────────
            // 32dp = 64px ≈ 4.4 个模块（480px/33 模块 ≈ 14.5px），满足二维码静默区 ≥4 模块的要求。
            Box(
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(32.dp),
            ) {
                val bitmap = qrState.bitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "抖音登录二维码",
                        modifier = Modifier.size(qrSide),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(qrSide)
                            .background(AxTvColors.Surface, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "点下方「生成二维码」开始",
                            color = AxTvColors.TextSecondary,
                            style = TextStyle(fontSize = 16.sp),
                        )
                    }
                }
            }

            // ───────────────── 状态行：扫码状态 + 帧龄，单行显示 ─────────────────
            Text(
                text = buildString {
                    append("扫码状态：")
                    append(qrState.message.ifBlank { "未开始" })
                    if (qrState.bitmap != null) {
                        append(" · 最近刷新 ")
                        append(qrAgeSeconds(qrState.freshAtMs, qrClockMs))
                        append(" 秒前")
                    }
                    (state as? LoginState.Success)?.let { append(" · 已登录：${it.nickname}") }
                    (state as? LoginState.Failure)?.let { append(" · 验证失败：${it.message}") }
                    if (notice.isNotBlank()) append(" · $notice")
                },
                color = when {
                    state is LoginState.Failure || qrState.phase == QrPhase.Failed -> AxTvColors.AccentWarm
                    state is LoginState.Success || qrState.phase == QrPhase.Success -> AxTvColors.Accent
                    else -> AxTvColors.TextSecondary
                },
                style = TextStyle(fontSize = 15.sp),
                maxLines = 1,
            )

            if (diagnosticText.isNotBlank() && qrState.phase != QrPhase.Idle) {
                Text(
                    text = "诊断：$diagnosticText",
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 11.sp),
                    maxLines = 2,
                )
            }

            // ───────────────── 扫码操作按钮 ─────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoginButton(
                    label = when (qrState.phase) {
                        QrPhase.Starting -> "正在生成二维码…"
                        QrPhase.Idle -> "生成二维码"
                        else -> "重新生成二维码"
                    },
                    enabled = qrState.phase != QrPhase.Starting,
                    primary = true,
                    modifier = Modifier.focusRequester(qrRequester),
                    onClick = ::startQrLogin,
                )
                if (qrState.phase != QrPhase.Idle) {
                    LoginButton(label = "停止扫码", onClick = ::stopQrSession)
                }
                // 兜底入口：自动探测（DOM/URL/轮询字段）任何一种漏判时，用户仍能把登录页拿出来
                // 自己在页面上完成验证；不做这个入口，真机就会卡在「已扫码」无处可去。
                if (!keypadVisible && webViewUsable &&
                    (qrState.phase == QrPhase.Waiting || qrState.phase == QrPhase.Verifying)
                ) {
                    LoginButton(
                        label = "显示登录页",
                        onClick = { qrBridge?.requestManualVerification() },
                    )
                }
                LoginButton(
                    label = "手机推送登录",
                    onClick = {
                        if (phonePushUrl != null) {
                            showPhonePush = true
                        } else {
                            notice = "局域网面板尚未启动，请重启应用后再试"
                        }
                    },
                )
                LoginButton(
                    label = "退出登录",
                    enabled = state !is LoginState.Verifying && savedCookie.isNotBlank(),
                    onClick = ::logout,
                )
            }

            // ───────────────── 手动 token / Cookie 输入区：输入框占满 + 导入按钮，压成一行 ─────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var fieldFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .background(AxTvColors.Surface, RoundedCornerShape(10.dp))
                        .focusRing(fieldFocused, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (cookieInput.isEmpty()) {
                        Text(
                            text = "粘贴 Cookie 请求头（须含 sessionid / sessionid_ss）",
                            color = AxTvColors.TextSecondary,
                            style = TextStyle(fontSize = 13.sp),
                        )
                    }
                    BasicTextField(
                        value = cookieInput,
                        onValueChange = { cookieInput = it },
                        singleLine = true,
                        textStyle = TextStyle(color = AxTvColors.TextPrimary, fontSize = 13.sp),
                        cursorBrush = SolidColor(AxTvColors.Accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldRequester)
                            .onFocusChanged { fieldFocused = it.isFocused || it.hasFocus }
                            // 输入框会吞掉方向键，UP 时主动把焦点交还给扫码区，避免遥控器被困在输入框。
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyUp && event.key == Key.DirectionUp) {
                                    runCatching { qrRequester.requestFocus() }
                                    true
                                } else {
                                    false
                                }
                            },
                    )
                }
                LoginButton(
                    label = when (state) {
                        LoginState.Verifying -> "验证中…"
                        else -> "导入"
                    },
                    enabled = state !is LoginState.Verifying && cookieInput.isNotBlank(),
                    primary = true,
                    onClick = ::verify,
                )
            }

            // ───────────────── 一行说明 ─────────────────
            Text(
                text = "说明：优先扫上方二维码；手动 Cookie 仅在扫码不可用时使用。扫码结果会复核登录态，失败如实提示。BACK 返回上一级。",
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
                maxLines = 1,
            )
        }

        // 风控要求短信验证码：电视没有输入法，网页里的 input 也敲不进数字，只能应用侧收码。
        if (showPhonePush && phonePushUrl != null) {
            QrLinksDialog(
                links = listOf(
                    QrLink(
                        title = "手机推送登录信息",
                        url = phonePushUrl,
                        hint = "手机扫码后登录抖音网页版，按页面提示把 Cookie 推到电视",
                        openable = false,
                    ),
                ),
                onOpen = {},
                onDismiss = { showPhonePush = false },
            )
        }

        if (keypadVisible) {
            VerifyCodeKeypad(
                code = verifyCode,
                hint = qrState.message,
                onDigit = { digit -> if (verifyCode.length < VERIFY_CODE_MAX_LENGTH) verifyCode += digit },
                onDelete = { if (verifyCode.isNotEmpty()) verifyCode = verifyCode.dropLast(1) },
                onClear = { verifyCode = "" },
                onSubmit = { qrBridge?.requestCodeSubmission(verifyCode) },
                onOpenPage = { qrBridge?.requestManualVerification() },
            )
        }
    }
}
