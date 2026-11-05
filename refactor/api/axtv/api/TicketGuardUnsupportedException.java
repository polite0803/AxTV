/*
 * 来源: 新增（对应 AbstractC0656g4.m4440P 加密分支：serverCert 非空时的
 *       ECDH + HKDF/HMAC 加密封装路径）
 * 原职责: 标记"离线无法复现的 ticket-guard 分支"
 * 重构说明: serverCert 加密分支依赖服务端下发的证书做密钥交换，反编译只见到
 *           KeyAgreement/Mac 调用残骸（AbstractC0656g4 头部 import），完整算法
 *           无法离线取证复现。凭据带 serverCert 时抛本异常，明确失败而不是
 *           伪造普通签名头（服务端会因缺加密层拒签，普通签名在加密会话里无效）。
 */
package axtv.api;

public class TicketGuardUnsupportedException extends TicketGuardException {

    public TicketGuardUnsupportedException(String message) {
        super(message);
    }
}
