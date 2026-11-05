/*
 * 来源: src/app/p000/fl4.java（m4190H 写 Cookie、m4189G 写互动凭据、m4200g/m4204k/m4206m 读回、
 *       m4213u / m4214v 状态判定）+ src/app/p000/el4.java（sessionid 判定）
 * 原职责: 登录凭据的持久化（SharedPreferences "douyin_auth"）与可用性判定
 * 重构说明: 抽成接口，核心层不碰 SharedPreferences；键名与语义原样保留。
 *           Android 侧实现见 NOTES.md「待迁移」。
 */
package axtv.panel;

/**
 * 登录凭据存储。
 *
 * <p>Android 侧实现：{@code context.getSharedPreferences("douyin_auth", Context.MODE_PRIVATE)}，
 * 键名必须与下表一致，否则与 App 其它模块（播放器/接口层）读不到同一份 Cookie：
 *
 * <table border="1">
 *   <caption>SharedPreferences: douyin_auth</caption>
 *   <tr><th>键</th><th>含义</th></tr>
 *   <tr><td>cookie_string</td><td>整条 Cookie 字符串</td></tr>
 *   <tr><td>interaction_security_payload</td><td>security payload 原文</td></tr>
 *   <tr><td>interaction_ticket</td><td>ticket</td></tr>
 *   <tr><td>interaction_ts_sign</td><td>ts_sign</td></tr>
 *   <tr><td>interaction_private_key</td><td>私钥（PEM）</td></tr>
 *   <tr><td>interaction_server_cert</td><td>server_cert</td></tr>
 * </table>
 *
 * <p>原实现写入后立刻 apply（异步落盘），并同时刷新 App 内的 StateFlow；
 * 这一侧由实现的 {@code setXxx} 负责通知 UI，核心层只管调用。
 */
public interface AuthStore {

    /** {@code cookie_string}，缺省空串。 */
    String cookie();

    /** 写 {@code cookie_string}。 */
    void setCookie(String cookie);

    /** {@code interaction_security_payload}，缺省空串。 */
    String interactionSecurityPayload();

    /** {@code interaction_private_key}，缺省空串。 */
    String interactionPrivateKey();

    /** 从 4 个 interaction_* 键还原配置（原 {@code fl4.m4205l()}）。 */
    InteractionSecurityConfig interactionSecurity();

    /**
     * 写互动凭据：payload 原文 + 拆出的 4 个字段（原 {@code fl4.m4189G(String, d32)}）。
     *
     * @param rawPayload 手机端提交的原文（可能为空，字段仍会被写入）
     */
    void setInteractionSecurity(String rawPayload, InteractionSecurityConfig config);

    /**
     * 是否已登录：Cookie 里存在非空 {@code sessionid} 或 {@code sessionid_ss}（原 {@code el4.m3730a}）。
     */
    default boolean isLoggedIn() {
        return hasSessionId(cookie());
    }

    /** 互动凭据是否可用（原 {@code fl4.m4213u()}）。 */
    default boolean hasInteractionSecurity() {
        return interactionSecurity().isComplete();
    }

    /** 原 {@code el4.m3730a}：按 {@code ;} 切分，{@code name=value} 且 name 为 sessionid/sessionid_ss、value 非空。 */
    static boolean hasSessionId(String cookie) {
        if (cookie == null || cookie.isEmpty()) {
            return false;
        }
        for (String part : cookie.split(";")) {
            int equals = part.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String name = part.substring(0, equals).trim();
            String value = part.substring(equals + 1).trim();
            if (("sessionid".equals(name) || "sessionid_ss".equals(name)) && !value.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 内存实现：仅用于核心层自测/桌面跑通流程。
     * 生产环境请用 SharedPreferences 版本，行为差异见 NOTES.md。
     */
    final class InMemory implements AuthStore {
        private volatile String cookie = "";
        private volatile String payload = "";
        private volatile String ticket = "";
        private volatile String tsSign = "";
        private volatile String privateKey = "";
        private volatile String serverCert = "";

        @Override
        public String cookie() {
            return this.cookie;
        }

        @Override
        public void setCookie(String cookie) {
            this.cookie = cookie == null ? "" : cookie;
        }

        @Override
        public String interactionSecurityPayload() {
            return this.payload;
        }

        @Override
        public String interactionPrivateKey() {
            return this.privateKey;
        }

        @Override
        public InteractionSecurityConfig interactionSecurity() {
            return new InteractionSecurityConfig(this.payload, this.ticket, this.tsSign, this.privateKey, this.serverCert);
        }

        @Override
        public void setInteractionSecurity(String rawPayload, InteractionSecurityConfig config) {
            this.payload = rawPayload == null ? "" : rawPayload;
            this.ticket = config.ticket();
            this.tsSign = config.tsSign();
            this.privateKey = config.privateKey();
            this.serverCert = config.serverCert();
        }
    }
}
