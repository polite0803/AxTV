/*
 * 来源: src/app/p000/eg5.java (1737 行，R8 杂烩类；本类只取日期段 690-720)
 * 原职责: 秒级时间戳 → 中文日期标签（详情页 eu5:4156、选集面板 w03:1895 的更新时间显示）
 *         m3650X(690-703): 同年「M月d日」、跨年「yyyy年M月d日」；j<=0 → null
 *         m3651Y(706-716): 「MM-dd」（Locale.US 两位数字）；j<=0 → null
 *         m3652Z(718-727): (year, month) 对（月选择数据，原 tn5）；j<=0 → null
 * 重构说明: 三个纯函数。时区用设备本地默认（原实现 Calendar.getInstance() 无参）。
 */
package axtv.ui;

import java.util.Calendar;

/** 秒级时间戳的中文日期标签（纯函数）。 */
public final class DateLabels {

    private DateLabels() {
    }

    /**
     * 「M月d日」/「yyyy年M月d日」（eg5:690-703）：与当前同年省略年份。
     *
     * @param epochSeconds 秒级时间戳；&le; 0 返回 {@code null}
     */
    public static String medium(long epochSeconds) {
        if (epochSeconds <= 0) {
            return null;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(epochSeconds * 1000L);
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH) + 1;
        int day = c.get(Calendar.DAY_OF_MONTH);
        if (year == Calendar.getInstance().get(Calendar.YEAR)) {
            return month + "月" + day + "日";
        }
        return year + "年" + month + "月" + day + "日";
    }

    /** 「MM-dd」（eg5:706-716，{@code String.format(Locale.US, "%02d-%02d", month, day)}）。 */
    public static String shortLabel(long epochSeconds) {
        if (epochSeconds <= 0) {
            return null;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(epochSeconds * 1000L);
        return String.format(java.util.Locale.US, "%02d-%02d",
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** 年月对（eg5:718-727，原 {@code tn5(year, month)}；month 已 +1，1 起）。 */
    public static YearMonth yearMonth(long epochSeconds) {
        if (epochSeconds <= 0) {
            return null;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(epochSeconds * 1000L);
        return new YearMonth(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    /** 年月对（原 {@code tn5}）。 */
    public record YearMonth(int year, int month) {
    }
}
