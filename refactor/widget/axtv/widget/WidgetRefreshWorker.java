/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetRefreshWorker.java (110 行)
 * 原职责: CoroutineWorker.doWork —— 解析 kind、widget 存在性判定、调 WidgetRefresher.refresh、
 *         失败时按 runAttemptCount 决定 retry / success
 * 重构说明: 完整保留 doWork 分支与重试上限 3；返回纯 JDK 的 Result 枚举，
 *           Android 侧映射为 androidx.work.ListenableWorker.Result.success()/retry()。
 */
package axtv.widget;

/** WidgetRefreshWorker 的纯逻辑（原 doWork）。 */
public final class WidgetRefreshWorker {

    /** 最大重试次数：runAttemptCount &gt;= 3 放弃（WidgetRefreshWorker.java:108）。 */
    public static final int MAX_RUN_ATTEMPTS = 3;

    /** 对应 androidx.work.ListenableWorker.Result。 */
    public enum Result {
        SUCCESS,
        RETRY
    }

    private final WidgetRefresher refresher;
    private final WidgetRefreshScheduler scheduler;

    public WidgetRefreshWorker(WidgetRefresher refresher, WidgetRefreshScheduler scheduler) {
        this.refresher = refresher;
        this.scheduler = scheduler;
    }

    /**
     * @param kindName        来自 inputData.getString("kind")，解析失败为 null
     * @param runAttemptCount 对应 getRunAttemptCount()
     */
    public Result doWork(String kindName, int runAttemptCount) {
        WidgetKind kind = WidgetKind.fromName(kindName);
        if (kind == null) {
            return Result.SUCCESS;                       // :98 无效 kind → success
        }
        if (!scheduler.hasWidgets(kind)) {
            return Result.SUCCESS;                       // :83-84 无 widget → success
        }
        boolean ok = refresher.refresh(kind);            // :91
        if (ok) {
            return Result.SUCCESS;                       // :105-106
        }
        // :108 失败：第 3 次尝试后放弃（success），否则 retry
        return runAttemptCount >= MAX_RUN_ATTEMPTS ? Result.SUCCESS : Result.RETRY;
    }
}
