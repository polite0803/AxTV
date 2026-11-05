/*
 * 来源: src/app/p000/mw3.java (5854 行 —— 观看历史 + 合集页 + 扫码推送面板)
 * 原职责: 本类覆盖可执行行为 ——
 *         观看时刻取值 eg5.m3660h(eg5:730-740): watchMap[awemeId]>0 优先，否则 aweme.createTime
 *         按月分组展平 mw3:4343-4385: VideoMonth(year,month) 相邻变化插 header，每条插 item；
 *         不分组模式全 item
 *         header 文案 mw3:5777/5788: 标题「Y 年 M 月」+ 计数「N 个」
 *         加载占位 mw3:5700: 「加载到 Y 年 M 月…」
 *         日期选择单元格 mw3:5625-5640: 复合键 year*100+month（tn5.m10628a），
 *           右值 = 「M 月」+（count>0 ?「N 个」:「未加载」）
 *         面板文案: 按日期查找/加载中…(mw3:324)、加载失败，点击重试、暂无内容、
 *           扫码推送（mw3:280-310 段，支持同时推送 Cookie、ticket、ts_sign 和 private key）
 *         tn5 = VideoMonth(year, month)，equals/hashCode 按值，排序键 year*100+month
 * 重构说明: 纯函数 + 数据模型；Compose 渲染归 Android 侧。
 */
package axtv.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 观看历史按月分组 + 日期选择的行为规格（纯函数）。 */
public final class HistoryGroupSpec {

    // ---- 文案（逐字） ----
    public static final String LABEL_FIND_BY_DATE = "按日期查找";   // mw3:301/324
    public static final String LABEL_LOADING = "加载中…";           // mw3:324（z2 分支）
    public static final String LABEL_LOAD_FAILED_RETRY = "加载失败，点击重试";
    public static final String LABEL_EMPTY = "暂无内容";
    public static final String LABEL_NOT_LOADED = "未加载";          // mw3:5637
    public static final String LABEL_SWITCH_CONFIG = "切换配置";
    public static final String LABEL_SCAN_PUSH = "扫码推送";
    public static final String LABEL_SCAN_PUSH_HINT = "手机扫码推送登录信息";
    public static final String LABEL_SCAN_PUSH_DESC = "支持同时推送 Cookie、ticket、ts_sign 和 private key";
    public static final String LABEL_NO_LOGIN_HINT = "扫描二维码添加Cookie或登录后即可使用";
    public static final String LABEL_ANONYMOUS_USER = "未登录用户";

    /** 分组键 = year*100+month（tn5.m10628a，mw3:5632 同式）。 */
    public static int groupKey(int year, int month) {
        return year * 100 + month;
    }

    /** 观看时刻（eg5.m3660h，eg5:730-740）：watchMap[awemeId] &gt; 0 优先，否则 createTime。 */
    public static long watchTime(String awemeId, Map<String, Long> watchTimeMap, long createTime) {
        Long t = awemeId == null ? null : watchTimeMap.get(awemeId);
        return t != null && t > 0 ? t : createTime;
    }

    /** 展平后的列表元素：月份 header（含计数）或视频 item（mw3:4361-4385 ox5/px5）。 */
    public record Entry(String headerTitle, Integer headerCount, String awemeId) {
    }

    /**
     * 按月分组展平（mw3:4343-4385，z43=按日期查找开启时）：
     * 输入已按观看时间排序的条目；相邻条目的 VideoMonth 变化时插入 header
     * （计数 = 该 VideoMonth 的总条数），随后插入每条 item。
     * 月份从 {@code monthOf(epochMillis)} 提供（原实现走 {@code eg5.m3652Z}，设备本地时区）。
     *
     * @param keys    每条视频的分组键（year*100+month，无时间戳的条目传 null → 跳过分组归属）
     * @param counts  每个 VideoMonth 键的总条数（原实现先全量统计，sv2.m10447w）
     */
    public static List<Entry> flattenByMonth(int[] keys, Map<Integer, Integer> counts) {
        List<Entry> out = new ArrayList<>();
        if (keys == null) {
            return out;
        }
        int last = Integer.MIN_VALUE;
        boolean first = true;
        for (int key : keys) {
            if (key != last) {
                Integer count = counts.get(key);
                // header 标题按复合键反解：year = key/100, month = key%100
                out.add(new Entry(headerTitle(key / 100, key % 100),
                        count != null ? count : 1, null));
                last = key;
                first = false;
            }
            out.add(new Entry(null, null, String.valueOf(key)));
            if (first) {
                first = false;
            }
        }
        return out;
    }

    /** 月份 header 标题（mw3:5777 逐字）：{@code year + " 年 " + month + " 月"}。 */
    public static String headerTitle(int year, int month) {
        return year + " 年 " + month + " 月";
    }

    /** header 计数（mw3:5788 逐字）：{@code count + " 个"}。 */
    public static String headerCount(int count) {
        return count + " 个";
    }

    /** 无限滚动到未加载月份的占位（mw3:5700 逐字）：{@code "加载到 " + year + " 年 " + month + " 月…"}。 */
    public static String loadMoreHint(int year, int month) {
        return "加载到 " + year + " 年 " + month + " 月…";
    }

    /**
     * 日期选择单元格右值（mw3:5636-5637）：
     * 左值 {@code month + " 月"}；有内容 → {@code count + " 个"}，否则「未加载」。
     */
    public static String monthCellCount(int count) {
        return count > 0 ? count + " 个" : LABEL_NOT_LOADED;
    }

    /** 日期选择单元格是否为当前选中月（mw3:5639：复合键相等判定）。 */
    public static boolean isMonthSelected(int cellKey, int selectedKey) {
        return cellKey == selectedKey;
    }

    /** 便捷：从时间戳计算分组键（设备本地时区，原实现走 Calendar）。 */
    public static int keyOfEpochMillis(long epochMillis) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(epochMillis);
        return groupKey(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1);
    }

    /** 便捷：单组计数表构造。 */
    public static Map<Integer, Integer> countKeys(int[] keys) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int k : keys) {
            counts.merge(k, 1, Integer::sum);
        }
        return counts;
    }
}
