/*
 * 来源: src/app/p000/r13.java:20-38 (f18569c / f18570d / f18571e)、r13.java:59-90 (m9746d)
 *       src/app/com/github/mytv/p004dv/MainActivity.java:143-150 (dispatchKeyEvent)
 * 原职责: 遥控器/键盘按键的归一化映射 —— 媒体键转方向键、焦点方向表、确认键集合、
 *         Compose 焦点处理的完整按键绑定
 * 重构说明: Map 常量逐条还原（含重复项去重后的最终结果）；Android KeyEvent 用 record 表达
 */
package axtv.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 遥控器按键映射表。所有数值都是 Android {@code KeyEvent} / {@code View.FOCUS_*} 的原始常量。 */
public final class RemoteKeyMap {

    // ---- Android KeyEvent 常用键码（用于阅读下表） ----
    public static final int KEY_DPAD_UP = 19;
    public static final int KEY_DPAD_DOWN = 20;
    public static final int KEY_DPAD_LEFT = 21;
    public static final int KEY_DPAD_RIGHT = 22;
    public static final int KEY_DPAD_CENTER = 23;
    public static final int KEY_A = 29;
    public static final int KEY_D = 32;
    public static final int KEY_S = 47;
    public static final int KEY_STAR = 51;
    public static final int KEY_ENTER = 66;
    public static final int KEY_MEDIA_PLAY = 85;
    public static final int KEY_MEDIA_NEXT = 87;
    public static final int KEY_MEDIA_PREVIOUS = 88;
    public static final int KEY_MEDIA_REWIND = 89;
    public static final int KEY_MEDIA_FAST_FORWARD = 90;
    public static final int KEY_MENU = 82;
    public static final int KEY_NUMPAD_ENTER = 160;
    public static final int KEY_SYSTEM_NAVIGATION_UP = 166;
    public static final int KEY_SYSTEM_NAVIGATION_DOWN = 167;

    // ---- View.FOCUS_* ----
    public static final int FOCUS_LEFT = 17;
    public static final int FOCUS_UP = 33;
    public static final int FOCUS_RIGHT = 66;
    public static final int FOCUS_DOWN = 130;

    /**
     * {@code r13.f18569c}：键码 → 焦点移动方向。
     * 原 Kotlin {@code mapOf(...)} 里前 8 项在后面又重复出现一次，Map 去重后是这 10 项。
     * 即：方向键、系统导航键、字母 A/D/S、星号键都能驱动焦点。
     */
    public static final Map<Integer, Integer> FOCUS_DIRECTION;

    /** {@code r13.f18570d}：被视为「确认」的键（原 Set：23, 66, 160）。 */
    public static final Set<Integer> CONFIRM_KEYS = Set.of(23, 66, 160);

    /**
     * {@code r13.f18571e}：MainActivity.dispatchKeyEvent 使用的媒体键 → 方向键重映射，共 7 条。
     * 原第六项写作 {@code WebSocketProtocol.PAYLOAD_SHORT}，是 R8 内联的整数常量 126。
     */
    public static final Map<Integer, Integer> DISPATCH_REMAP;

    static {
        LinkedHashMap<Integer, Integer> focus = new LinkedHashMap<>();
        focus.put(21, FOCUS_LEFT);
        focus.put(22, FOCUS_RIGHT);
        focus.put(19, FOCUS_UP);
        focus.put(166, FOCUS_UP);
        focus.put(20, FOCUS_DOWN);
        focus.put(167, FOCUS_DOWN);
        focus.put(51, FOCUS_UP);
        focus.put(47, FOCUS_DOWN);
        focus.put(29, FOCUS_LEFT);
        focus.put(32, FOCUS_RIGHT);
        FOCUS_DIRECTION = Map.copyOf(focus);

        LinkedHashMap<Integer, Integer> remap = new LinkedHashMap<>();
        remap.put(88, KEY_DPAD_UP);          // MEDIA_PREVIOUS → 上
        remap.put(87, KEY_DPAD_DOWN);        // MEDIA_NEXT → 下
        remap.put(89, KEY_DPAD_LEFT);        // MEDIA_REWIND → 左
        remap.put(90, KEY_DPAD_RIGHT);       // MEDIA_FAST_FORWARD → 右
        remap.put(85, KEY_DPAD_CENTER);      // MEDIA_PLAY → 确认
        remap.put(126, KEY_DPAD_CENTER);     // 原 WebSocketProtocol.PAYLOAD_SHORT
        remap.put(127, KEY_DPAD_CENTER);     // MEDIA_PAUSE → 确认
        DISPATCH_REMAP = Map.copyOf(remap);
    }

    /**
     * {@code r13.m9746d} 第一张键位表：键码 → 动作槽位。
     * 原实现每个槽位是一个 lambda；这里用槽位名表达，Android 侧把 lambda 接回去即可。
     */
    public static final Map<Integer, String> COMPOSE_KEY_BINDINGS;

    static {
        LinkedHashMap<Integer, String> b = new LinkedHashMap<>();
        b.put(21, "focusLeft");
        b.put(22, "focusRight");
        b.put(19, "focusUp");
        b.put(166, "focusUp");
        b.put(20, "focusDown");
        b.put(167, "focusDown");
        b.put(23, "confirm");
        b.put(66, "confirm");
        b.put(160, "confirm");
        b.put(82, "menu");
        b.put(176, "menu");
        b.put(259, "menu");
        b.put(36, "menu");
        b.put(40, "secondaryConfirm");
        b.put(51, "disabled");
        b.put(47, "disabled");
        b.put(29, "disabled");
        b.put(32, "disabled");
        // 数字键 7..16 → 原 C1891zn(..., byte 4..10, 1..3)，即 1..9 + 0 的快捷跳转
        b.put(7, "digit0");
        b.put(8, "digit1");
        b.put(9, "digit2");
        b.put(10, "digit3");
        b.put(11, "digit4");
        b.put(12, "digit5");
        b.put(13, "digit6");
        b.put(14, "digit7");
        b.put(15, "digit8");
        b.put(16, "digit9");
        COMPOSE_KEY_BINDINGS = Map.copyOf(b);
    }

    /** {@code r13.m9746d} 第二张表：这些键禁用默认焦点处理（原值全为 null）。 */
    public static final List<Integer> COMPOSE_DISABLED_FOCUS_KEYS =
            List.of(21, 22, 19, 166, 20, 167, 66, 160, 23);

    /** 一个最小 KeyEvent 表示（字段顺序与 MainActivity:149 的构造参数一致）。 */
    public record KeyEvent(long downTime, long eventTime, int action, int keyCode,
                           int repeatCount, int metaState, int deviceId, int scanCode, int flags) {
    }

    private RemoteKeyMap() {
    }

    /**
     * 精确还原 {@code MainActivity.dispatchKeyEvent}：
     * 命中 {@link #DISPATCH_REMAP} 时构造新 KeyEvent —— 保留 downTime/eventTime/action/
     * repeatCount/metaState/deviceId/flags，<b>scanCode 固定为 0</b>；
     * 未命中返回 {@code null}，表示原样交给 super。
     */
    public static KeyEvent remapForDispatch(KeyEvent e) {
        Integer mapped = DISPATCH_REMAP.get(e.keyCode());
        if (mapped == null) {
            return null;
        }
        return new KeyEvent(e.downTime(), e.eventTime(), e.action(), mapped,
                e.repeatCount(), e.metaState(), e.deviceId(), 0, e.flags());
    }

    /** 键码 → View 焦点方向；无映射返回 null。 */
    public static Integer focusDirection(int keyCode) {
        return FOCUS_DIRECTION.get(keyCode);
    }

    public static boolean isConfirm(int keyCode) {
        return CONFIRM_KEYS.contains(keyCode);
    }
}
