/*
 * 来源: src/app/p000/zc4.java（f26226c 状态流、f26235l 电视当前文字、f26234k 连接集合）
 *       + src/app/p000/dg0.java（服务器状态枚举）
 *       + src/app/p000/C1710ur.java（AuthSubmitPayload）
 *       + src/app/p000/wc4.java m11491o（手机 → 电视消息解析）与 m11492p（电视 → 手机 tv_text）
 *       + src/app/p000/te4.java（电视搜索框文字广播）
 * 原职责: 面板服务与 UI 层之间的双向通道：服务器状态、手机输入、手机搜索、电视回显文字、Cookie 推送结果
 * 重构说明: 把分散在 zc4 / wc4 / te4 里的状态与回调集中成一个可观察对象，
 *           消息类型字符串（input / search / tv_text）与 JSON 字段名（type / text）原样保留。
 */
package axtv.panel;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 面板服务与 UI 层的输入/输出通道。
 *
 * <p>三组信号：
 * <ul>
 *   <li><b>服务器状态</b>：DISCONNECTED / WAITING / CONNECTED / ERROR（原 {@code dg0}），
 *       对应电视设置页上「搜索输入服务」的开关与指示灯；</li>
 *   <li><b>手机 → 电视</b>：{@code {"type":"input","text":"…"}}（边打边同步）、
 *       {@code {"type":"search","text":"…"}}（回车/点「在电视上搜索」）；</li>
 *   <li><b>电视 → 手机</b>：{@code {"type":"tv_text","text":"…"}}（电视搜索框的当前文字，连接时补发一次）；</li>
 *   <li><b>Cookie 推送</b>：手机提交成功后通知 UI 立即切换登录态。</li>
 * </ul>
 *
 * 本类不依赖 Android，也没有协程/Flow；监听器在调用线程上同步回调，UI 侧自行切主线程。
 */
public final class PanelState {

    /** 搜索输入服务器状态（原 {@code dg0}，枚举顺序一致）。 */
    public enum Status {
        /** 未启动 */
        DISCONNECTED,
        /** 已监听、无手机连接 */
        WAITING,
        /** 至少一台手机连上 */
        CONNECTED,
        /** 监听失败或 WebSocket 错误 */
        ERROR
    }

    /** 手机推送的登录信息（原 {@code C1710ur} / AuthSubmitPayload）。 */
    public record AuthSubmitPayload(String cookie, String securityPayload, String privateKey) {
        public AuthSubmitPayload {
            cookie = cookie == null ? "" : cookie;
            securityPayload = securityPayload == null ? "" : securityPayload;
            privateKey = privateKey == null ? "" : privateKey;
        }

        public boolean hasCookie() {
            return !this.cookie.isBlank();
        }

        public boolean hasSecurity() {
            return !this.securityPayload.isBlank() || !this.privateKey.isBlank();
        }
    }

    /** 手机 → 电视消息的 type 取值（原 wc4.m11491o 里的字符串常量）。 */
    public static final String TYPE_INPUT = "input";
    public static final String TYPE_SEARCH = "search";
    public static final String TYPE_TV_TEXT = "tv_text";

    /** JSON 字段名。 */
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_TEXT = "text";

    /** UI 监听器。所有方法都有空实现，按需覆盖。 */
    public interface Listener {
        default void onStatusChanged(Status status) {
        }

        default void onPhoneInput(String text) {
        }

        default void onPhoneSearch(String text) {
        }

        default void onAuthPushed(AuthSubmitPayload payload) {
        }
    }

    private final Set<String> clients = ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Logger log;

    private volatile Status status = Status.DISCONNECTED;
    private volatile String tvText = "";

    public PanelState(String logTag) {
        this.log = Logger.getLogger(logTag);
    }

    public Status status() {
        return this.status;
    }

    public String tvText() {
        return this.tvText;
    }

    public int clientCount() {
        return this.clients.size();
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            this.listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    /** 服务器开始监听（原：zc4 收到 dg0.WAITING）。 */
    public void markServerStarted() {
        setStatus(Status.WAITING);
    }

    /** 服务器启动失败（原：dg0.ERROR + 日志「服务器启动失败」）。 */
    public void markServerStartFailed() {
        setStatus(Status.ERROR);
    }

    /** 服务器停止：清空连接、回到 DISCONNECTED。 */
    public void markStopped() {
        this.clients.clear();
        setStatus(Status.DISCONNECTED);
    }

    /**
     * 一台手机完成 WebSocket 握手。
     *
     * @return true 表示该连接是新增的（调用方随后应给它补发一次 tv_text）
     */
    public boolean clientConnected(String clientId) {
        boolean added = this.clients.add(clientId);
        if (added) {
            setStatus(Status.CONNECTED);
        }
        return added;
    }

    /** 一台手机断开；最后一个断开时回到 DISCONNECTED。 */
    public void clientDisconnected(String clientId) {
        this.clients.remove(clientId);
        if (this.clients.isEmpty() && this.status == Status.CONNECTED) {
            setStatus(Status.DISCONNECTED);
        }
    }

    /** WebSocket 层错误（原：dg0.ERROR，日志「WebSocket 错误」由调用方记录）。 */
    public void socketError() {
        setStatus(Status.ERROR);
    }

    /** 电视搜索框文字变化：先存下来，供新连接补发。 */
    public void setTvText(String text) {
        this.tvText = text == null ? "" : text;
    }

    /** 电视 → 手机的一条 tv_text 消息体。 */
    public String tvTextMessage() {
        return Json.object(FIELD_TYPE, TYPE_TV_TEXT, FIELD_TEXT, this.tvText);
    }

    /**
     * 解析手机上行消息。只认 {@code input} / {@code search}，其余忽略。
     *
     * @return 解析结果；无法解析或 type 不识时返回 null
     */
    public ParsedMessage parsePhoneMessage(String rawText) {
        try {
            Map<String, Object> object = Json.parseObject(rawText);
            String type = Json.optString(object, FIELD_TYPE);
            if (TYPE_INPUT.equals(type)) {
                return new ParsedMessage(TYPE_INPUT, Json.optString(object, FIELD_TEXT));
            }
            if (TYPE_SEARCH.equals(type)) {
                return new ParsedMessage(TYPE_SEARCH, Json.optString(object, FIELD_TEXT));
            }
            return null;
        } catch (RuntimeException e) {
            this.log.log(Level.SEVERE, "消息解析失败: " + rawText, e);
            return null;
        }
    }

    /** 把解析后的消息派发给 UI。 */
    public void dispatch(ParsedMessage message) {
        if (message == null) {
            return;
        }
        if (TYPE_INPUT.equals(message.type())) {
            this.listeners.forEach(listener -> listener.onPhoneInput(message.text()));
        } else if (TYPE_SEARCH.equals(message.type())) {
            this.listeners.forEach(listener -> listener.onPhoneSearch(message.text()));
        }
    }

    /** Cookie 推送成功：通知 UI 立即使用新的登录信息。 */
    public void authPushed(AuthSubmitPayload payload) {
        this.listeners.forEach(listener -> listener.onAuthPushed(payload));
    }

    private void setStatus(Status status) {
        if (this.status == status) {
            return;
        }
        this.status = status;
        this.listeners.forEach(listener -> listener.onStatusChanged(status));
    }

    /** 上行消息（type + text）。 */
    public record ParsedMessage(String type, String text) {
        public ParsedMessage {
            text = text == null ? "" : text;
        }
    }
}
