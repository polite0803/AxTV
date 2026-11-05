/*
 * 来源: src/app/p000/no4.java 的 m8230a(roomId, userId, userAgent)（TAG "SignJs"）
 * 原职责: 用 sign.js 的 getMSSDKSignature 生成 wss 的 signature 参数
 * 重构说明: 核心层不依赖 QuickJS，抽象为接口；实现见 axtv.sign.SignJsEngine（sign 模块已还原）
 */
package axtv.live;

/** 直播 wss 地址签名器。 */
public interface LiveSignatureProvider {

    /**
     * 生成 {@code signature} 参数值。
     *
     * @param roomId    IM 房间 id（az0.b）
     * @param userId    user_unique_id（az0.c）
     * @param userAgent 与连接使用的 User-Agent 一致
     * @return 非空签名串；失败返回空串（原实现在空串时仍会拼进 URL，服务端拒绝）
     */
    String signLive(String roomId, String userId, String userAgent) throws Exception;
}
