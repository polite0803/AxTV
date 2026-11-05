/*
 * 「已看」本地清单：Settings.viewedVideoIds() 的环形缓冲读写。
 *
 * 为什么这里裁的是本地清单，而不是设置项字面上的「历史记录容量」：
 * 本应用的历史记录来自服务端接口 VideoApi.historyRead（见 ui/library/UserWorksScreens.kt），
 * 接口没有容量参数，客户端无权裁剪；核心层真正没被消费的只有 viewedVideoIds/setViewedVideoIds。
 * 原版同样把 feed_history_capacity 用在 viewed_video_ids 上：
 *  - 起播记录 + 去重 + 按容量裁剪：src/app/p000/hu2.java:297-313（remove(id) → add(id) → take(capacity)）
 *  - 用裁剪后的清单过滤首页已看条目：src/app/p000/kq5.java:1011-1020
 * 本类逐条对齐这两处行为。
 */
package com.github.axtv.dv.adapter

import axtv.settings.Settings

object ViewedVideoTracker {

    /** 起播记录：去重后追加到队尾，超过容量从队头丢弃（与原版 hu2.m5227C 一致）。 */
    fun record(awemeId: String) {
        val settings = AppRuntime.settings ?: return
        if (awemeId.isBlank()) return
        settings.setViewedVideoIds(trimmed(settings.viewedVideoIds(), awemeId, capacityOf(settings)))
    }

    /** 当前「已看」集合，供首页打标 / 去重。 */
    fun viewedIds(): Set<String> = AppRuntime.settings?.viewedVideoIds()?.toSet() ?: emptySet()

    /**
     * 纯函数版本，便于单测：新 id 移到队尾 + 按容量裁剪。
     * 容量 50 与 1000 的差异因此直接体现在「最早那条已看记录什么时候被忘掉」。
     */
    fun trimmed(existing: List<String>, awemeId: String, capacity: Int): List<String> =
        ArrayList<String>(capacity.coerceAtLeast(1)).apply {
            add(awemeId)
            existing.forEach { if (it != awemeId && size < capacity) add(it) }
        }

    /**
     * FeedHistoryCapacity.value 是包内可见字段（refactor/settings/.../Settings.java:1449-1451），
     * Kotlin 跨包取不到，按枚举名解析出数字，避免为了读一个整数去改核心层。
     */
    private fun capacityOf(settings: Settings): Int =
        settings.feedHistoryCapacity().name.removePrefix("CAPACITY_").toIntOrNull() ?: 100
}
