/*
 * 来源: src/app/p000/d32.java（交互安全凭据，原 InteractionSecurityConfig）
 * 原职责: 保存互动接口所需的 ticket / tsSign / privateKey / serverCert /
 *         rawPayload，并用 m2724a() 判断是否"可用"
 * 重构说明: 值对象，等价替换 d32；可用性判定与 m2724a 完全一致（三个字段都非空）
 */
package axtv.api;

public final class TicketGuardCredentials {

    private final String rawPayload;
    private final String ticket;
    private final String tsSign;
    private final String privateKey;
    private final String serverCert;

    public TicketGuardCredentials(String rawPayload,
                                  String ticket,
                                  String tsSign,
                                  String privateKey,
                                  String serverCert) {
        this.rawPayload = rawPayload == null ? "" : rawPayload;
        this.ticket = ticket == null ? "" : ticket;
        this.tsSign = tsSign == null ? "" : tsSign;
        this.privateKey = privateKey == null ? "" : privateKey;
        this.serverCert = serverCert == null ? "" : serverCert;
    }

    public static TicketGuardCredentials empty() {
        return new TicketGuardCredentials("", "", "", "", "");
    }

    public String rawPayload() {
        return rawPayload;
    }

    public String ticket() {
        return ticket;
    }

    public String tsSign() {
        return tsSign;
    }

    public String privateKey() {
        return privateKey;
    }

    public String serverCert() {
        return serverCert;
    }

    /** 对应 d32.m2724a()：ticket && tsSign && privateKey 均非空白。 */
    public boolean isUsable() {
        return !Texts.isBlank(ticket) && !Texts.isBlank(tsSign) && !Texts.isBlank(privateKey);
    }
}
