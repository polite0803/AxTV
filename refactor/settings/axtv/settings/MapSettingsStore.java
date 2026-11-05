/*
 * 来源: 无对应原类（原文只有 Android SharedPreferences 一种实现）
 * 原职责: 无
 * 重构说明: 纯 JDK 的 SettingsStore 实现，供单测 / 桌面端使用；语义对齐 SharedPreferences
 */
package axtv.settings;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存版配置存储。
 *
 * <p>行为对齐 {@code SharedPreferences}：
 * <ul>
 *   <li>类型不匹配时抛 {@link ClassCastException}（与 Android 一致，便于暴露键名/类型错用）；</li>
 *   <li>缺省值在键不存在时返回，不做类型校验；</li>
 *   <li>写入立即生效（Android 是 apply 到内存 + 异步落盘，读取侧同样是立即生效）。</li>
 * </ul>
 */
public final class MapSettingsStore implements SettingsStore {

    private final ConcurrentMap<String, Object> values = new ConcurrentHashMap<>();

    @Override
    public String getString(String key, String defValue) {
        Object value = values.get(key);
        if (value == null) {
            return defValue;
        }
        if (!(value instanceof String s)) {
            throw new ClassCastException(key + " is not a String");
        }
        return s;
    }

    @Override
    public int getInt(String key, int defValue) {
        Object value = values.get(key);
        if (value == null) {
            return defValue;
        }
        if (!(value instanceof Integer i)) {
            throw new ClassCastException(key + " is not an int");
        }
        return i;
    }

    @Override
    public long getLong(String key, long defValue) {
        Object value = values.get(key);
        if (value == null) {
            return defValue;
        }
        if (!(value instanceof Long l)) {
            throw new ClassCastException(key + " is not a long");
        }
        return l;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object value = values.get(key);
        if (value == null) {
            return defValue;
        }
        if (!(value instanceof Float f)) {
            throw new ClassCastException(key + " is not a float");
        }
        return f;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Object value = values.get(key);
        if (value == null) {
            return defValue;
        }
        if (!(value instanceof Boolean b)) {
            throw new ClassCastException(key + " is not a boolean");
        }
        return b;
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public void putString(String key, String value) {
        values.put(key, value);
    }

    @Override
    public void putInt(String key, int value) {
        values.put(key, value);
    }

    @Override
    public void putLong(String key, long value) {
        values.put(key, value);
    }

    @Override
    public void putFloat(String key, float value) {
        values.put(key, value);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        values.put(key, value);
    }

    @Override
    public void remove(String key) {
        values.remove(key);
    }
}
