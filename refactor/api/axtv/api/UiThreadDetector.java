/*
 * 来源: src/app/p000/n11.java m8012E 中的 Looper 判断
 *       if (j42.m6486c(Looper.myLooper(), Looper.getMainLooper())) return 随机 msToken;
 * 原职责: 主线程上禁止发起同步网络请求，直接退化为随机 msToken
 * 重构说明: 核心层不依赖 Android Looper，改成可注入的判定接口；
 *           Android 侧实现：Looper.myLooper() == Looper.getMainLooper()
 */
package axtv.api;

public interface UiThreadDetector {

    boolean isUiThread();

    /** 默认（桌面 JVM / 单元测试）永远不是 UI 线程。 */
    UiThreadDetector NEVER = () -> false;
}
