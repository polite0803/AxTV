/*
 * 来源: src/app/com/github/mytv/p004dv/widget/BaseWidgetReceiver.java (43 行)
 *       + 6 个 XxxWidgetReceiver.java（kind 绑定）
 * 原职责: AppWidgetProvider 生命周期：添加首个 widget 时排周期+立即刷新，onUpdate 排周期，
 *         删除最后一个 widget 时取消周期工作；NowPlayingWidgetReceiver 无 kind、不走本逻辑
 * 重构说明: 把生命周期回调里的业务逻辑抽成纯 JDK 方法，Android 侧 Receiver 直接转发调用。
 */
package axtv.widget;

/** AppWidgetProvider 生命周期业务逻辑（原 BaseWidgetReceiver）。 */
public final class WidgetReceiverLifecycle {

    private final WidgetEnvironment env;
    private final WidgetRefreshScheduler scheduler;

    public WidgetReceiverLifecycle(WidgetEnvironment env, WidgetRefreshScheduler scheduler) {
        this.env = env;
        this.scheduler = scheduler;
    }

    /** onEnabled：先 super，再 schedule + refreshNow（BaseWidgetReceiver.java:27-33）。 */
    public void onEnabled(WidgetKind kind) {
        scheduler.schedule(kind);
        scheduler.refreshNow(kind);
    }

    /** onUpdate：schedule 后交给 super.onUpdate（:36-42）。 */
    public void onUpdate(WidgetKind kind) {
        scheduler.schedule(kind);
    }

    /** onDisabled：非「设备保护存储且 SDK>=24」时取消周期工作（BaseWidgetReceiver.java:18-23）。 */
    public void onDisabled(WidgetKind kind) {
        if (!WidgetActions.isDeviceProtected(env.settings)) {
            env.workScheduler.cancel(scheduler.periodicName(kind));
        }
    }
}
