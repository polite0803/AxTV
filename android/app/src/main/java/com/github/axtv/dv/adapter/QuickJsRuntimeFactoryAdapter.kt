/*
 * Android 适配层 —— sign：QuickJS 运行时工厂。
 *
 * 执行层方案：APK 自带的 libquickjs-android-wrapper.so + 工程内 com.whl.quickjs 绑定类
 * （零新增 Maven 依赖）。JNI 符号与绑定类的 native 声明逐字匹配。
 *
 * 线程约束：QuickJSContext 的 checkSameThread() 要求所有 evaluate/destroy 都落在
 * 创建它的线程。AbogusEngine / SignJsEngine 用单线程 executor 串行化全部调用，
 * 因此本工厂的 create() 只在对应 JS 线程内被调用，eval/close 也只会从该线程进入。
 */
package com.github.axtv.dv.adapter

import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.QuickJSContext
import axtv.sign.JsRuntime

object QuickJsRuntimeFactoryAdapter : JsRuntime.Factory {

    @Volatile
    private var libraryLoaded = false

    private fun ensureLibraryLoaded() {
        if (libraryLoaded) return
        synchronized(this) {
            if (!libraryLoaded) {
                QuickJSLoader.init()
                libraryLoaded = true
            }
        }
    }

    override fun create(): JsRuntime {
        ensureLibraryLoaded()
        val context = QuickJSContext.create()
        return object : JsRuntime {
            override fun eval(script: String): Any? = context.evaluate(script, "unknown.js")

            override fun close() {
                // QuickJSContext.destroy() 不可重复调用；这里兜住重复 close 与销毁期异常。
                runCatching { context.destroy() }
            }
        }
    }
}
