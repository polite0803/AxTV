/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetStore.java:55-57 (prefs)
 *       + WidgetThemeKt.java:90 (fl4.f6240a.getInt("global_seed_argb", 0))
 * 原职责: SharedPreferences("axtv_desktop_widgets", MODE_PRIVATE) 的 getString/putString(...).commit()/clear().commit()
 * 重构说明: 核心层不依赖 Android Context/SharedPreferences，用键值存储接口隔离。
 *           Android 侧实现直接代理到 context.getSharedPreferences("axtv_desktop_widgets", 0)。
 */
package axtv.widget;

/** 键值持久化抽象，逐字对应原 SharedPreferences 访问。 */
public interface WidgetStorage {

    /** SharedPreferences 名，逐字还原。 */
    String PREFS_NAME = "axtv_desktop_widgets";

    /** 快照键 —— 逐字还原 WidgetStore 的 5 个 private static final String。 */
    String KEY_HISTORY = "history";
    String KEY_LATER   = "later";
    String KEY_LIVE    = "live";
    String KEY_MIX     = "mix";
    String KEY_SEARCH  = "search";

    /** 主题种子色键（WidgetThemeKt 读取的另一个 SharedPreferences 键）。 */
    String KEY_GLOBAL_SEED_ARGB = "global_seed_argb";

    /** 对应 prefs.getString(key, def)。 */
    String getString(String key, String def);

    /** 对应 edit().putString(key, value).commit()。 */
    void putString(String key, String value);

    /** 对应 fl4.f6240a.getInt(key, def)。 */
    int getInt(String key, int def);

    /** 对应 edit().clear().commit()。 */
    void clear();
}
