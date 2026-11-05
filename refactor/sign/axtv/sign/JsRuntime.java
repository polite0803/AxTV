/*
 * 来源: com.whl.quickjs.wrapper.QuickJSContext 的使用点
 *       C1400n.java:87(create) / :88(赋值) / :90,:104,:105,:109,:116,:120,:144,:146,:150
 *       C0761j.java:77-81 / C1285k.java:228-235 / zg0.java:144-153 / q30.java:302-330
 *       C1608s.java:91-93(destroy) / RunnableC0688h.java:68-71,:313-314
 * 原职责: QuickJS 引擎上下文：evaluate(script) 返回 Object，destroy() 释放
 * 重构说明: 核心层只依赖本接口，不依赖 com.whl.quickjs；Android 侧适配器见 NOTES.md「待迁移」
 */
package axtv.sign;

/**
 * 一个 JS 运行时上下文。非线程安全——所有调用都必须落在创建它的那个线程上
 * （原实现用单线程 dispatcher 保证，见 {@link AbogusEngine} / {@link SignJsEngine}）。
 */
public interface JsRuntime {

    /**
     * 执行脚本并返回结果。
     *
     * <p>返回值类型与 QuickJS 的映射一致：JS string -> {@link String}，boolean -> {@link Boolean}，
     * number -> {@link Number}，null/undefined -> {@code null}，对象 -> 实现自定义类型。
     * 脚本抛异常时应抛出 RuntimeException（原实现由 QuickJSContext 抛 Exception）。
     */
    Object eval(String script);

    /** 释放原生上下文。重复调用应是安全的空操作。 */
    void close();

    /** 创建新上下文。每次初始化只调用一次。 */
    interface Factory {
        JsRuntime create();
    }
}
