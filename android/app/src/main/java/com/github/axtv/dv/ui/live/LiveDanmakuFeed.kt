/*
 * 线 C —— 直播弹幕（列表样式）数据源。
 *
 * 规格出处：
 * - refactor/live/NOTES.md（wss 地址/心跳/重连常量）
 * - refactor/live/axtv/live/LiveWebSocketClient.java（订阅入口）
 * - refactor/live/axtv/live/LiveChat.java（ChatMessage / RoomUserSeq 字段）
 * - 设置项 live_danmaku_style 的「列表样式」分支（本阶段直播间只做列表，不接录播滚动浮层）
 *
 * 身份约束：WS 的 UA 与 HTTP 请求共用同一个 UserAgentProvider，按启动快照里的
 * client_identity_branch 取值，绝不在这里硬编码 QQBrowser UA 绕过设置。
 */
package com.github.axtv.dv.ui.live

import axtv.api.ClientIdentityBranch
import axtv.api.UserAgentProvider
import axtv.live.LiveChat
import axtv.live.LiveCredentials
import axtv.live.LiveSignatureProvider
import axtv.live.LiveWebSocketClient
import axtv.model.LiveRoom
import axtv.sign.SignJsEngine
import axtv.ui.DetailPageSpec
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.github.axtv.dv.adapter.AndroidDeviceProfile
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.adapter.QuickJsRuntimeFactoryAdapter
import com.github.axtv.dv.live.AndroidLiveLog
import com.github.axtv.dv.live.OkHttpWebSocket
import com.github.axtv.dv.sign.AndroidAssets
import com.github.axtv.dv.sign.AndroidSignLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 单条弹幕（列表样式展示的昵称 + 内容）。 */
internal data class LiveCommentUi(val nickname: String, val content: String)

/** 弹幕连接状态，直接作为面板标题旁的状态文案。 */
internal enum class DanmakuState(val label: String) {
    CONNECTING("连接中…"),
    CONNECTED("已连接"),
    DISCONNECTED("连接已断开"),
    FAILED("连接失败"),
    DISABLED("弹幕已关闭"),
    UNAVAILABLE("暂不可用"),
}

/**
 * 直播间弹幕订阅。
 *
 * 生命周期：进入直播间调用 [start]，离开调用 [stop]（关闭 WS 与 sign.js 运行时）。
 * 回调统一 post 到主线程，UI 侧可直接写 Compose 状态。
 */
internal class LiveDanmakuFeed(
    context: Context,
    private val room: LiveRoom,
    private val onComment: (LiveCommentUi) -> Unit,
    private val onUserSeq: (Long) -> Unit,
    private val onState: (DanmakuState) -> Unit,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var signer: SignJsEngine? = null

    @Volatile
    private var client: LiveWebSocketClient? = null

    fun start() {
        val settings = AppRuntime.settings
        if (settings == null) {
            postState(DanmakuState.UNAVAILABLE)
            return
        }
        if (!settings.liveDanmakuEnabled()) {
            postState(DanmakuState.DISABLED)
            return
        }
        val webRid = room.effectiveWebRid()
        val roomId = DetailPageSpec.resolvedRoomId(room)
        if (webRid.isBlank() || roomId.isBlank()) {
            postState(DanmakuState.UNAVAILABLE)
            return
        }

        // 与 HTTP 侧一致的 UA：由启动快照分支决定，不私自发 QQBrowser。
        val branch: ClientIdentityBranch = settings.clientIdentityBranch()
        val userAgent = UserAgentProvider(branch, AndroidDeviceProfile.fromBuild()).userAgent()
        val credentials = LiveCredentials(webRid, roomId, settings.selfUserId(), settings.cookie())

        postState(DanmakuState.CONNECTING)
        scope.launch {
            val engine = SignJsEngine(AndroidAssets(appContext), QuickJsRuntimeFactoryAdapter, AndroidSignLog)
            if (!engine.ensureReady()) {
                engine.close()
                postState(DanmakuState.FAILED)
                return@launch
            }
            if (!scope.isActive) {
                engine.close()
                return@launch
            }
            signer = engine
            val ws = LiveWebSocketClient(
                OkHttpWebSocket(),
                SignJsProvider(engine),
                object : LiveWebSocketClient.Listener {
                    override fun onConnected() = postState(DanmakuState.CONNECTED)

                    override fun onChat(message: LiveChat.Message) {
                        val content = message.content().trim()
                        if (content.isEmpty()) return
                        val nickname = message.user().nickName().ifBlank { "观众" }
                        mainHandler.post { onComment(LiveCommentUi(nickname, content)) }
                    }

                    override fun onRoomUserSeq(seq: LiveChat.RoomUserSeq) {
                        val total = seq.totalUser()
                        if (total > 0L) mainHandler.post { onUserSeq(total) }
                    }

                    override fun onDisconnected(reason: String) = postState(DanmakuState.DISCONNECTED)

                    override fun onError(error: Throwable, httpStatus: Int, body: String) =
                        postState(DanmakuState.FAILED)
                },
                AndroidLiveLog,
            )
            client = ws
            if (!scope.isActive) {
                ws.disconnect()
                engine.close()
                return@launch
            }
            ws.connect(credentials, userAgent)
        }
    }

    fun stop() {
        scope.cancel()
        client?.disconnect()
        client = null
        signer?.close()
        signer = null
    }

    private fun postState(state: DanmakuState) {
        mainHandler.post { onState(state) }
    }
}

/**
 * axtv.sign.SignJsEngine 没有实现 axtv.live.LiveSignatureProvider
 * （核心层人为拆开，避免 sign 模块反向依赖 live），这里补一层纯委托。
 */
private class SignJsProvider(private val engine: SignJsEngine) : LiveSignatureProvider {
    override fun signLive(roomId: String, userId: String, userAgent: String): String =
        engine.signLive(roomId, userId, userAgent)
}
