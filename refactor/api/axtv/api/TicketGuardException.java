/*
 * 来源: 新增（原实现中 m4425A 派生失败 + cookie 回退为空时直接重抛原始异常，
 *       n11.java m8037c L2929-2931）
 * 原职责: ticket-guard 头构造失败的统一异常出口
 * 重构说明: 受检异常不可用（SignProvider 接口未声明），用 RuntimeException；
 *           语义等价于原版"重抛原始异常"——凭据可用却产不出头时必须显式失败，
 *           不允许静默返回空 Map 冒充成功。
 */
package axtv.api;

public class TicketGuardException extends RuntimeException {

    public TicketGuardException(String message) {
        super(message);
    }

    public TicketGuardException(String message, Throwable cause) {
        super(message, cause);
    }
}
