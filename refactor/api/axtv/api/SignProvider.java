/*
 * 来源: src/app/p000/n11.java m8009B/m7989C（a_bogus 计算入口）
 *       + m8037c（bd-ticket-guard 请求头）+ m7999i（x-secsdk-web-signature）
 *       + AbstractC0656g4（ticket-guard client data 编解码，属 axtv.sign 模块）
 * 原职责: 生成请求签名参数与安全请求头
 * 重构说明: 抽成接口，实现放 axtv.sign（依赖 assets/abogus.js + QuickJS）。
 *           核心层只负责"何时签、签什么"的调用编排，不含算法。
 */
package axtv.api;

import java.util.Map;

public interface SignProvider {

    /**
     * 计算 a_bogus。
     *
     * 原实现链路：m7989C → m8009B → C1608s.m10095c → QuickJS 调用
     *   getABogus(queryString, bodyString, path, userAgent)
     *
     * @param path       接口路径，如 "/aweme/v1/web/aweme/detail/"
     * @param query      已拼接的 query 串（"k=v&k2=v2"），不含前导 '?'
     * @param body       POST 表单串（"k=v&..."）或 ""；GET 传 ""
     * @param userAgent  仅 live.douyin.com 域名传 QQBrowser UA，其余传 null（JS 收到 undefined）
     * @return 签名串；失败时返回 ""（原实现失败返回 "" 并打日志，不中断请求）
     */
    String signABogus(String path, String query, String body, String userAgent);

    /**
     * 构造 bd-ticket-guard-* 请求头（对应 m8037c）。
     *
     * 原语义：
     *   1. credentials 不可用 → 返回空 Map（不加任何头）
     *   2. 解析 privateKey（AbstractC0656g4.m4425A）；失败则回退到
     *      cookie["bd_ticket_guard_client_data"] 解出的 ree-public-key，
     *      两者都拿不到则抛出原始异常
     *   3. 返回 5 个头：
     *        bd-ticket-guard-client-data      = AbstractC0656g4.m4464l(path, credentials)
     *        bd-ticket-guard-ree-public-key   = 上面解析出的公钥
     *        bd-ticket-guard-version          = "2"
     *        bd-ticket-guard-web-sign-type    = "1"
     *        bd-ticket-guard-web-version      = "2"
     *
     * @param path        请求路径
     * @param credentials 当前账号的交互安全凭据
     */
    Map<String, String> ticketGuardHeaders(String path, TicketGuardCredentials credentials);

    /**
     * 计算 x-secsdk-web-signature（对应 m7999i）。
     *
     * 原算法（纯 MD5，无外部依赖，默认实现已给出，实现类可覆盖）：
     *   md5( uifid + "_" + timestamp + "_A96D855A08C0A9707F8BEF0D9A527E4E_" + join(params, "&") )
     * 其中 join(params) 是"加入 uifid、timestamp 之后"的完整参数串（原实现对参数表做
     * 浅拷贝后补写 uifid / timestamp，再按插入顺序拼接；已存在的 key 位置不变）。
     *
     * @param params    当前参数表（可已包含 uifid / timestamp）
     * @param uifid     UIFID
     * @param timestamp 秒级时间戳字符串
     */
    default String xSecSdkWebSignature(Map<String, String> params, String uifid, String timestamp) {
        java.util.LinkedHashMap<String, String> copy = new java.util.LinkedHashMap<>(params);
        copy.put("uifid", uifid);
        copy.put("timestamp", timestamp);
        StringBuilder sb = new StringBuilder();
        sb.append(uifid).append('_').append(timestamp)
                .append("_A96D855A08C0A9707F8BEF0D9A527E4E_")
                .append(Texts.joinQuery(copy));
        return Texts.md5Hex(sb.toString());
    }
}
