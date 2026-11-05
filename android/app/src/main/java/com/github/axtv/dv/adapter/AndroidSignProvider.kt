/*
 * Android 适配层 —— api：SignProvider 实现。
 *
 * 规格出处: refactor/api/NOTES.md、SignProvider.java、sign/NOTES.md §3。
 * a_bogus 由 AbogusSigner 计算；失败时沿用核心层语义返回 ""（核心层已打 Log.e，不静默）。
 *
 * ticket-guard：原实现依赖 AbstractC0656g4 的 protobuf 编解码，重构未包含该算法。
 * 本轮不伪造请求头，凭据可用时明确抛 UnsupportedOperationException（避免静默降级造成假阳性）。
 */
package com.github.axtv.dv.adapter

import axtv.api.SignProvider
import axtv.api.TicketGuardCredentials
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
    ): Map<String, String> {
        if (credentials == null || !credentials.isUsable()) {
            return emptyMap()
        }
        throw UnsupportedOperationException(
            "ticket-guard 头未实现：protobuf 编码（AbstractC0656g4.m4464l）尚未重构，本轮不伪造请求头",
        )
    }
}
