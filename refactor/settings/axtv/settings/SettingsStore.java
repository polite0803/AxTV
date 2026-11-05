/*
 * 来源: src/app/p000/fl4.java（字段 f6240a = SharedPreferences "douyin_auth"）
 * 原职责: 为 SettingsManager 提供键值持久化（Android SharedPreferences）
 * 重构说明: 抽成接口，核心层不依赖 android.content.SharedPreferences；
 *           写入语义与原文一致：每次 put 立即异步落盘（SharedPreferences.edit().putX().apply()）。
 */
package axtv.settings;

/**
 * 配置存储抽象。
 *
 * <p>Android 侧实现方式（必须与原文完全一致，否则与 App 其它模块读不到同一份数据）：
 * <pre>{@code
 * SharedPreferences sp = context.getSharedPreferences("douyin_auth", Context.MODE_PRIVATE);
 * // getString -> sp.getString(key, def)
 * // putString -> sp.edit().putString(key, value).apply()
 * }</pre>
 * 原文所有写入都用 {@code apply()}（异步写盘、不阻塞、无返回值），因此本接口的 setter 均无返回值。
 *
 * <p>纯 JVM 侧可直接用 {@link MapSettingsStore}（测试/桌面版），语义等价。
 */
public interface SettingsStore {

    String getString(String key, String defValue);

    int getInt(String key, int defValue);

    long getLong(String key, long defValue);

    float getFloat(String key, float defValue);

    boolean getBoolean(String key, boolean defValue);

    /** 对应 {@code SharedPreferences.contains(key)}，用于「旧键回退 / 迁移」判定。 */
    boolean contains(String key);

    void putString(String key, String value);

    void putInt(String key, int value);

    void putLong(String key, long value);

    void putFloat(String key, float value);

    void putBoolean(String key, boolean value);

    void remove(String key);
}
