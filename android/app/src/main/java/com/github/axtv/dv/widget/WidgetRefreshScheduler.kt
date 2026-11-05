/*
 * Android 适配层 —— widget 模块：刷新调度。
 * 规格出处: refactor/widget/NOTES.md §6（WorkManager 周期/去抖/网络约束/重试 3 次）
 * 与原版差异: 原版通过核心层 WidgetRefreshScheduler + WidgetEnvironment 注入；此处直接调用 WorkManager，
 *            周期与 WorkManager 下限（15min）一致，flex 固定 5min（原版 flex 公式见 NOTES §6.3）。
 * NowPlaying 无 kind、不参与周期刷新（播放状态驱动，由 WidgetRenderers.refresh 直接更新）。
 */
package com.github.axtv.dv.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import axtv.widget.WidgetKind
import axtv.widget.WidgetRenderSpec
import java.util.concurrent.TimeUnit
import kotlin.math.max

/** 与核心层 WidgetRefreshScheduler.java:41 / Worker:68 一致的 inputData key（文件内共用）。 */
private const val KEY_KIND = "kind"

internal object WidgetRefreshScheduler {

    private const val WORK_PREFIX_ONCE = "widgets_once_"
    private const val WORK_PREFIX_PERIODIC = "widgets_periodic_"
    private const val MIN_PERIODIC_INTERVAL_MINUTES = 15L
    private const val MIN_PERIODIC_FLEX_MINUTES = 5L
    private const val MAX_RUN_ATTEMPTS = 3

    fun schedule(context: Context, widgetId: WidgetRenderSpec.WidgetId) {
        val kind = kindOf(widgetId) ?: return
        val interval = max(kind.intervalMinutes, MIN_PERIODIC_INTERVAL_MINUTES)
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            interval,
            TimeUnit.MINUTES,
            MIN_PERIODIC_FLEX_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraint())
            .setInputData(workDataOf(KEY_KIND to kind.name))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_PREFIX_PERIODIC + kind.name,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context, widgetId: WidgetRenderSpec.WidgetId) {
        val kind = kindOf(widgetId) ?: return
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(WORK_PREFIX_PERIODIC + kind.name)
    }

    /** 立即刷新一次（onEnabled / 失败态重试），与周期任务同名去抖 REPLACE。 */
    fun refreshNow(context: Context, widgetId: WidgetRenderSpec.WidgetId) {
        val kind = kindOf(widgetId) ?: return
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setConstraints(networkConstraint())
            .setInputData(workDataOf(KEY_KIND to kind.name))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WORK_PREFIX_ONCE + kind.name,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun networkConstraint(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    /** Note §6.1：HISTORY → 继续看、LATER → 稍后再看、MIX → 合集追更、LIVE/SEARCH 同名。 */
    private fun kindOf(widgetId: WidgetRenderSpec.WidgetId): WidgetKind? =
        when (widgetId) {
            WidgetRenderSpec.WidgetId.CONTINUE -> WidgetKind.HISTORY
            WidgetRenderSpec.WidgetId.WATCH_LATER -> WidgetKind.LATER
            WidgetRenderSpec.WidgetId.MIX -> WidgetKind.MIX
            WidgetRenderSpec.WidgetId.LIVE -> WidgetKind.LIVE
            WidgetRenderSpec.WidgetId.SEARCH -> WidgetKind.SEARCH
            WidgetRenderSpec.WidgetId.NOW_PLAYING -> null
        }

    /** Worker 输入 kind → widgetId 的反向映射。 */
    fun widgetIdOfKind(kindName: String): WidgetRenderSpec.WidgetId? =
        when (WidgetKind.fromName(kindName)) {
            WidgetKind.HISTORY -> WidgetRenderSpec.WidgetId.CONTINUE
            WidgetKind.LATER -> WidgetRenderSpec.WidgetId.WATCH_LATER
            WidgetKind.MIX -> WidgetRenderSpec.WidgetId.MIX
            WidgetKind.LIVE -> WidgetRenderSpec.WidgetId.LIVE
            WidgetKind.SEARCH -> WidgetRenderSpec.WidgetId.SEARCH
            null -> null
        }

    fun maxRunAttempts(): Int = MAX_RUN_ATTEMPTS
}

/** 刷新 Worker：后台线程拉取真实数据后重绘对应小组件。 */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val kindName = inputData.getString(KEY_KIND) ?: return Result.success()
        val widgetId = WidgetRefreshScheduler.widgetIdOfKind(kindName) ?: return Result.success()
        return try {
            WidgetDataProviderHolder.installRealData(applicationContext)
            WidgetDataProviderHolder.provider.refresh(widgetId)
            WidgetRenderers.refresh(applicationContext, widgetId)
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount >= WidgetRefreshScheduler.maxRunAttempts()) {
                Result.success()
            } else {
                Result.retry()
            }
        }
    }
}
