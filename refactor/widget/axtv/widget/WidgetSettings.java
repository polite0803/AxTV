/*
 * 来源: src/app/com/github/mytv/p004dv/widget/ 多处对 p000.fl4 (设置/账户管理器) 的调用
 *       WidgetStore.java:44 (canSave), :109-122 (isLocked), WidgetRefresher.java:694 (m4214v)
 *       WidgetActionsKt.java:24-26 (isDeviceProtected), WidgetThemeKt.java:86-95 (m4198e/m4199f/m4197d/global_seed_argb/uiMode)
 *       WidgetUiKt.java:3654-3659 (SDK_INT / system_app_widget_background_radius)
 * 原职责: 提供当前账户、功能开关、PIN 锁状态、设备保护存储判定、主题偏好、屏幕密度等环境量
 * 重构说明: 核心层不依赖 Android Context，用接口隔离；Android 侧实现代理到 fl4 / Configuration / Resources。
 */
package axtv.widget;

/** 设置与设备环境抽象（原 p000.fl4 + Context 配置）。 */
public interface WidgetSettings {

    /** 当前登录账户 id（fl4.m4202i()）；空串表示未登录。 */
    String currentAccountId();

    /** 是否允许记录/展示观看历史与列表（fl4.m4214v()）。 */
    boolean historyEnabled();

    /** 当前账户配置是否被 PIN 上锁（WidgetStore.isLocked(fl4)）。 */
    boolean isLocked();

    /** 是否处于设备保护存储（直接启动）上下文（WidgetActionsKt.isDeviceProtected）。 */
    boolean isDeviceProtected();

    /** 深色模式：uiMode & 48 == 32（WidgetThemeKt.java:95）。 */
    boolean isNightMode();

    /** 屏幕密度 displayMetrics.density（WidgetImages 计算像素尺寸用）。 */
    float density();

    /** Android SDK 版本（Build.VERSION.SDK_INT）。 */
    int sdkInt();

    /** Android 12+ 系统小组件背景圆角（dp）；小于 0 表示不适用。 */
    float systemWidgetBackgroundRadiusDp();

    /**
     * 主题色来源（fl4.m4198e()）。返回 "CONTENT" 时表示按封面图动态取色；
     * 其它值由 Android 侧 ColorSchemeFactory 解释。
     */
    String themeSource();

    /** 调色板风格（fl4.m4199f() → p000.s90 枚举名）。 */
    String paletteStyle();

    /** 对比度档位（fl4.m4197d() → p000.z70 枚举名）。 */
    String contrast();

    /** 全局种子色 ARGB（SharedPreferences "global_seed_argb"，0 = 未设置）。 */
    int globalSeedArgb();
}
