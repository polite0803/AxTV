/*
 * Android 适配层 —— api：SignProvider 实现。
 *
 * 规格出处: refactor/api/NOTES.md、SignProvider.java、sign/NOTES.md §3。
 * a_bogus 由 AbogusSigner 计算；失败时沿用核心层语义返回 ""（核心层已打 Log.e，不静默）。
 *
 * ticket-guard：取证更正——AbstractC0656g4.m4464l 不是 protobuf，是 Base64(JSON)
 * （ts_sign / req_content / req_sign / timestamp / t_trust）+ SHA256withECDSA 签名。
 * 编码逻辑已下沉到 axtv.api.TicketGuardHeaderCodec（零依赖 JCE），本类只做适配：
 *   - 凭据不可用 → 空 Map（原版 m2724a 为假的合法出口）
 *   - serverCert 非空 → TicketGuardUnsupportedException（m4440P 加密分支离线无法复现）
 *   - 公钥派生失败且无 cookie 回退 → TicketGuardException（原版重抛，不静默返回空）
 * 适配层拿不到 CookieStore，bd_ticket_guard_client_data 回退传 null（派生失败时即抛）。
 */
package com.github.axtv.dv.adapter

import axtv.api.SignProvider
import axtv.api.TicketGuardCredentials
import axtv.api.TicketGuardHeaderCodec
import axtv.sign.AbogusSigner

class AndroidSignProvider(
    private val signFn: (query: String, body: String, path: String, userAgent: String?) -> String,
) : SignProvider {

    /** 生产构造：直接委托 AbogusSigner。 */
    constructor(signer: AbogusSigner) : this(signer::sign)

    override fun signABogus(path: String?, query: String, body: String, userAgent: String?): String =
        // 核心层形参顺序是 (query, data, uri, userAgent)，此处按 SignProvider 语义重排。
        signFn(query, body, path ?: "", userAgent)

    override fun ticketGuardHeaders(
        path: String?,
        credentials: TicketGuardCredentials?,
    ): Map<String, String> =
        TicketGuardHeaderCodec.buildHeaders(path ?: "", credentials, null)
}
