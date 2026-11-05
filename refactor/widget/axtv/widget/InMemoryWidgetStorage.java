/*
 * 来源: 新增（WidgetStorage 的纯 JDK 参考实现）
 * 原职责: 无（核心层测试/桌面预览用）
 * 重构说明: 用 ConcurrentHashMap 逐字模拟 SharedPreferences 的 get/put/clear/commit 语义，
 *           令本模块在纯 JDK 下可独立运行与验证；Android 侧仍应注入真实 SharedPreferences 实现。
 */
package axtv.widget;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** WidgetStorage 的线程安全内存实现（供核心层测试）。 */
public final class InMemoryWidgetStorage implements WidgetStorage {

    private final Map<String, String> strings = new ConcurrentHashMap<>();
    private final Map<String, Integer> ints = new ConcurrentHashMap<>();

    @Override
    public String getString(String key, String def) {
        String v = strings.get(key);
        return v != null ? v : def;
    }

    @Override
    public void putString(String key, String value) {
        if (value == null) {
            strings.remove(key);
        } else {
            strings.put(key, value);
        }
    }

    @Override
    public int getInt(String key, int def) {
        Integer v = ints.get(key);
        return v != null ? v : def;
    }

    /** 设置整型键（对应 SharedPreferences.Editor.putInt）。 */
    public void putInt(String key, int value) {
        ints.put(key, value);
    }

    @Override
    public void clear() {
        strings.clear();
        ints.clear();
    }
}
