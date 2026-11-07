/*
 * 来源: 新建（电视台连通性标注，无原版对应）。
 * 原职责: 无。
 * 重构说明: 质量分档的唯一判据。阈值不取拍脑袋常量，而是从既有的
 *           Settings.IPTV_SOURCE_LOAD_TIMEOUT_SECONDS（记作 T）推导：
 *           GOOD ≤ T/8、FAIR ≤ T/3、POOR > T/3。这样用户把超时调大调小，
 *           分档口径跟着变，不会出现「超时 60s 却仍按 10s 判 GOOD」的错配。
 */
package axtv.iptv;

/** 连接质量分档判据（纯函数，可单测边界）。 */
public final class QualityPolicy {

    /** 连续失败达到此次数才判死（PLAN P4）。2 次不判死，避免一次网络抖动就隐藏频道。 */
    public static final int DEAD_FAILURE_THRESHOLD = 3;

    /** GOOD 上界为 T/8。 */
    public static final int GOOD_DIVISOR = 8;

    /** FAIR 上界为 T/3。 */
    public static final int FAIR_DIVISOR = 3;

    private QualityPolicy() {
    }

    /** 三档质量。仅在 {@link LivenessRecord.State#OK} 时有意义。 */
    public enum Quality {
        GOOD, FAIR, POOR
    }

    /** 超时秒数下限：Settings 允许配 0/负数，推导阈值时必须夹到 1，否则除零或负阈值。 */
    public static int effectiveTimeoutSeconds(int timeoutSeconds) {
        return Math.max(1, timeoutSeconds);
    }

    /** GOOD 的上界（毫秒，含）。 */
    public static long goodMaxMs(int timeoutSeconds) {
        return effectiveTimeoutSeconds(timeoutSeconds) * 1000L / GOOD_DIVISOR;
    }

    /** FAIR 的上界（毫秒，含）；超过即 POOR。 */
    public static long fairMaxMs(int timeoutSeconds) {
        return effectiveTimeoutSeconds(timeoutSeconds) * 1000L / FAIR_DIVISOR;
    }

    /** 按首字节/首分片耗时给质量档。耗时为负视作 0（时钟回拨不应产出 POOR）。 */
    public static Quality classify(long elapsedMs, int timeoutSeconds) {
        long ms = Math.max(0L, elapsedMs);
        if (ms <= goodMaxMs(timeoutSeconds)) {
            return Quality.GOOD;
        }
        if (ms <= fairMaxMs(timeoutSeconds)) {
            return Quality.FAIR;
        }
        return Quality.POOR;
    }
}