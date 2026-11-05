/*
 * 来源: src/app/p000/d32.java（InteractionSecurityConfig）
 * 原职责: 互动安全凭据（security payload 原文 + 拆出的 ticket / ts_sign / privateKey / serverCert）
 * 重构说明: record 化，去掉 f3616b 之类字段名。
 */
package axtv.panel;

/**
 * 互动安全凭据。
 *
 * <p>对应原 {@code d32}：字段名与手机端提交的 {@code security_payload} / {@code private_key}
 * 语义一致，落盘到 SharedPreferences 的 5 个键（见 {@link AuthStore}）。
 *
 * @param rawPayload 手机端原始 security_payload 文本
 * @param ticket     ticket / bd-ticket
 * @param tsSign     ts_sign
 * @param privateKey 私钥（PEM 或已归一化的 Base64）
 * @param serverCert server_cert
 */
public record InteractionSecurityConfig(String rawPayload, String ticket, String tsSign, String privateKey, String serverCert) {

    public InteractionSecurityConfig {
        rawPayload = rawPayload == null ? "" : rawPayload;
        ticket = ticket == null ? "" : ticket;
        tsSign = tsSign == null ? "" : tsSign;
        privateKey = privateKey == null ? "" : privateKey;
        serverCert = serverCert == null ? "" : serverCert;
    }

    /** 空配置（对应原 {@code e32} 四个字段全空时的产物）。 */
    public static InteractionSecurityConfig empty() {
        return new InteractionSecurityConfig("", "", "", "", "");
    }

    /**
     * 是否可用于互动（点赞/收藏）。
     * 原 {@code d32.m2724a()}：ticket、tsSign、privateKey 三者都非空才算可用。
     */
    public boolean isComplete() {
        return !this.ticket.isBlank() && !this.tsSign.isBlank() && !this.privateKey.isBlank();
    }
}
