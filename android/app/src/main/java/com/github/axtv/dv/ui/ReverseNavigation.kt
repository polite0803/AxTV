/*
 * 设置项「反向导航」（reverse_navigation）的统一实现。
 *
 * 只做一件事：把这个子树里的 DPAD 上/下按键语义互换 —— 列表越往下排、遥控按"上"反而往下走
 * 的盒子/电视机型上，用户可以用这条设置把方向倒过来。
 *
 * 只在根节点做一次，不改各屏的焦点代码：
 * - 事件在根节点的 onPreviewKeyEvent 里被拦下（预览阶段先根后叶，见 Compose 焦点分发顺序），
 *   换掉 keyCode 后重新派发给当前持焦的 View；
 * - DOWN / UP 都要换：Compose 焦点系统对方向键是在 KeyUp 上做 focusSearch，
 *   只换 DOWN 的话焦点根本不会动；
 * - remapping 重入保护：重新派发会再走一遍本拦截器，第二次直接放行，不会无限递归。
 */
package com.github.axtv.dv.ui

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView

/** 把 DPAD 上/下互换后重新派发；其它按键原样放行。[enabled] 为假时不加任何拦截。 */
@Composable
fun Modifier.reverseDpadUpDown(enabled: Boolean): Modifier {
    if (!enabled) {
        return this
    }
    val root = LocalView.current
    val remapping = remember { booleanArrayOf(false) }
    return onPreviewKeyEvent { event: ComposeKeyEvent -> swapDpad(event, root, remapping) }
}

private fun swapDpad(event: ComposeKeyEvent, root: android.view.View, remapping: BooleanArray): Boolean {
    val native = event.nativeKeyEvent
    val swappedCode = when (native.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> KeyEvent.KEYCODE_DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_UP
        else -> return false
    }
    if (native.action != KeyEvent.ACTION_DOWN && native.action != KeyEvent.ACTION_UP) {
        return false
    }
    if (remapping[0]) {
        // 换码后的那一次派发：交给焦点系统正常处理。
        return false
    }
    remapping[0] = true
    try {
        val swapped = KeyEvent(
            native.downTime,
            native.eventTime,
            native.action,
            swappedCode,
            native.repeatCount,
            native.metaState,
            native.deviceId,
            native.source,
        )
        // Compose 的内容挂在唯一的 AndroidComposeView 上，findFocus() 拿到的就是它自己。
        (root.findFocus() ?: root).dispatchKeyEvent(swapped)
    } finally {
        remapping[0] = false
    }
    return true
}
