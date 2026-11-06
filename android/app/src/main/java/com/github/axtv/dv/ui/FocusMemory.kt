/*
 * 进程内焦点记忆。
 *
 * TV 页面从播放器/详情返回时是重新组合（remember 状态不在），如果没有记忆，
 * 各列表会退回到「第一项/返回按钮」，遥控器要重新走很远。这里按「页面上下文」
 * 记下最后聚焦条目的 id 或下标，页面重建后优先恢复。
 *
 * 只存字符串，不持有 Composable/Requester；调用方负责按 id 找回条目。
 */
package com.github.axtv.dv.ui

internal object FocusMemory {

    private val lastValues = HashMap<String, String>()

    @Synchronized
    fun remember(scope: String, value: String) {
        if (scope.isNotBlank() && value.isNotBlank()) {
            lastValues[scope] = value
        }
    }

    @Synchronized
    fun last(scope: String): String? = lastValues[scope]
}
