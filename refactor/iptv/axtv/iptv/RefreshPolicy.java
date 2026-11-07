/*
 * 来源: 新建（电视台持久化与刷新时机，无原版对应）。
 * 原职责: 无。
 * 重构说明: 双 TTL 判据（PLAN P3）。目录与活性是两份独立输入：清单几天才变一次，
 *           而地址活性一天内就可能变，所以不共用一个过期时间，也不因刷目录而清活性。
 *           时间戳非正（从未取过）与时钟回拨（now 早于 recorded）都判为过期，
 *           宁可多取一次也不让一份来历不明的旧快照长期顶替真实数据。
 */
package axtv.iptv;

/** 刷新时机判据（纯函数，可单测边界）。 */
public final class RefreshPolicy {

    /** 目录快照有效期：7 天。 */
    public static final long CATALOG_TTL_MS = 7L * 24 * 60 * 60 * 1000;

    /** 活性记录有效期：24 小时。 */
    public static final long LIVENESS_TTL_MS = 24L * 60 * 60 * 1000;

    private RefreshPolicy() {
    }

    /** 目录是否需要重新拉取。 */
    public static boolean catalogStale(long fetchedAtEpochMs, long nowEpochMs) {
        return stale(fetchedAtEpochMs, nowEpochMs, CATALOG_TTL_MS);
    }

    /** 某个地址的活性是否需要重新探测（含判死后到期复检，让恢复的台能回来）。 */
    public static boolean livenessStale(long probedAtEpochMs, long nowEpochMs) {
        return stale(probedAtEpochMs, nowEpochMs, LIVENESS_TTL_MS);
    }

    private static boolean stale(long recordedAt, long now, long ttlMs) {
        if (recordedAt <= 0L) {
            return true;
        }
        if (now < recordedAt) {
            return true;
        }
        return now - recordedAt >= ttlMs;
    }
}