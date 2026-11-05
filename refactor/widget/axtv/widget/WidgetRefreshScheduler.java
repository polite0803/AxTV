/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetRefreshScheduler.java (195 行)
 * 原职责: WorkManager 周期/立即刷新调度、widget 存在性判定、设备保护存储守卫、周期常量钳制
 * 重构说明: 逐字保留 work 名前缀、input data key、5 个周期（在 WidgetKind）、
 *           WorkManager 最小周期 900000ms / 最小 flex 300000ms 的钳制与日志文本、
 *           kind→receiver 映射、hasWidgets/refreshNow/schedule/scheduleAll 全部分支。
 */
package axtv.widget;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 刷新调度（WorkManager 抽象层）。 */
public final class WidgetRefreshScheduler {

    /** WorkManager input data key，逐字还原 Scheduler.java:41 / Worker.java:68。 */
    public static final String KEY_KIND = "kind";
    /** onceName / periodicName 前缀（:76-82）。 */
    public static final String WORK_PREFIX_ONCE = "widgets_once_";
    public static final String WORK_PREFIX_PERIODIC = "widgets_periodic_";

    /** WorkManager 允许的最小周期与最小 flex（:151-166）。 */
    public static final long MIN_PERIODIC_INTERVAL_MILLIS = 900_000L; // 15 min
    public static final long MIN_PERIODIC_FLEX_MILLIS = 300_000L;     // 5 min

    /** scheduleAll 在设备保护存储下打印的日志（:187）。 */
    public static final String LOG_TAG = "AxTV";
    public static final String LOG_SKIP_DE = "DE storage context: skip widget refresh scheduling";
    /** WorkManager 内部钳制日志文本（:152, :157, :161, :164）。 */
    public static final String LOG_INTERVAL_CLAMPED =
            "Interval duration lesser than minimum allowed value; Changed to 900000";
    public static final String LOG_FLEX_CLAMPED =
            "Flex duration lesser than minimum allowed value; Changed to 300000";
    public static final String LOG_FLEX_GT_INTERVAL =
            "Flex duration greater than interval duration; Changed to ";

    /** enqueueUniqueWork 的冲突策略：同 kind 的立即刷新互相替换（去抖语义，:127 ac1.f334J）。 */
    public static final String EXISTING_WORK_POLICY_ONCE = "REPLACE";

    private static final Logger LOG = Logger.getLogger(LOG_TAG);

    private final WidgetEnvironment env;

    public WidgetRefreshScheduler(WidgetEnvironment env) {
        this.env = env;
    }

    public String onceName(WidgetKind kind) {
        return WORK_PREFIX_ONCE + kind.name();
    }

    public String periodicName(WidgetKind kind) {
        return WORK_PREFIX_PERIODIC + kind.name();
    }

    /** AppWidgetManager.getAppWidgetIds(ComponentName(context, receiver(kind))) 非空（:105-111）。 */
    public boolean hasWidgets(WidgetKind kind) {
        return env.workScheduler.hasWidgets(kind);
    }

    /** kind → Receiver 类名，用于 AppWidgetManager 查询（:84-103）。 */
    public String receiverName(WidgetKind kind) {
        return WidgetUpdater.receiverName(kind);
    }

    /**
     * 立即刷新（:113-129）：非设备保护存储且该 kind 有 widget 时，
     * 以 REPLACE 策略入队一个带网络约束的 OneTimeWorkRequest(WidgetRefreshWorker)。
     */
    public void refreshNow(WidgetKind kind) {
        if (!WidgetActions.isDeviceProtected(env.settings) && hasWidgets(kind)) {
            env.workScheduler.enqueueOnce(onceName(kind), kind,
                    WidgetEnvironment.Constraints.networkOnly());
        }
    }

    /**
     * 周期调度（:131-182）：设备保护存储跳过；无 widget 时取消周期工作；
     * 否则入队带网络约束、经最小周期钳制的 PeriodicWorkRequest。
     */
    public void schedule(WidgetKind kind) {
        if (WidgetActions.isDeviceProtected(env.settings)) {
            return;
        }
        if (!hasWidgets(kind)) {
            env.workScheduler.cancel(periodicName(kind));
            return;
        }
        env.workScheduler.enqueuePeriodic(periodicName(kind), kind,
                periodicSpec(kind), WidgetEnvironment.Constraints.networkOnly());
    }

    /** 启动时对所有 kind 排周期（:184-194）。 */
    public void scheduleAll() {
        if (WidgetActions.isDeviceProtected(env.settings)) {
            LOG.log(Level.INFO, LOG_SKIP_DE);
            return;
        }
        for (WidgetKind kind : WidgetKind.values()) {
            schedule(kind);
        }
    }

    /** 把 kind 的 intervalMinutes 换算并钳制成 WorkManager 可接受的 interval/flex。 */
    public WidgetEnvironment.PeriodicSpec periodicSpec(WidgetKind kind) {
        long millis = TimeUnit.MINUTES.toMillis(kind.getIntervalMinutes());
        long interval = clampIntervalMillis(millis);
        long flex = clampFlexMillis(millis, interval);
        return new WidgetEnvironment.PeriodicSpec(kind.getIntervalMinutes(), interval, flex);
    }

    /** 对应 :151-159：interval 低于 15min 钳到 900000 并打日志。 */
    public long clampIntervalMillis(long millis) {
        if (millis < MIN_PERIODIC_INTERVAL_MILLIS) {
            LOG.log(Level.INFO, LOG_INTERVAL_CLAMPED);
        }
        return Math.max(millis, MIN_PERIODIC_INTERVAL_MILLIS);
    }

    /** 对应 :160-166：flex = coerceIn(max(millis, 900000), 300000, interval) 并打日志。 */
    public long clampFlexMillis(long millis, long intervalMillis) {
        long base = Math.max(millis, MIN_PERIODIC_INTERVAL_MILLIS);
        if (base < MIN_PERIODIC_FLEX_MILLIS) {
            LOG.log(Level.INFO, LOG_FLEX_CLAMPED);
        }
        if (base > intervalMillis) {
            LOG.log(Level.INFO, LOG_FLEX_GT_INTERVAL + intervalMillis);
        }
        return Math.min(Math.max(base, MIN_PERIODIC_FLEX_MILLIS), intervalMillis);
    }
}
