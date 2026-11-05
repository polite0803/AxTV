/*
 * 来源: src/app/p000/qp2.java
 * 原职责: WebSocket 连接状态：CONNECTING / CONNECTED / DISCONNECTED / ERROR
 * 重构说明: 还原为标准 enum（原 class 是 R8 抹掉 enum 修饰的残骸），顺序不变，
 *           因为 ip2 case 5 用 {@code state == CONNECTED} 推导 isConnected
 */
package axtv.live;

/** 直播弹幕连接状态。 */
public enum LiveSocketState {
    /** 已发起连接（含参数准备阶段）。 */
    CONNECTING,
    /** 握手成功，心跳已启动。 */
    CONNECTED,
    /** 正常关闭（本地调用 close 或对端 close）。 */
    DISCONNECTED,
    /** 失败：连接异常且重试耗尽，或备份地址也失败。 */
    ERROR
}
