/*
 * 来源: src/app/p000/un2.java (2436 行，直播卡片 + 直播播放页)
 * 原职责: 本类覆盖可执行的行为计算 ——
 *         m10914e(2372-2403): 从 ExoPlayer Tracks 取 type==2(视频) 选中轨的 width×height 标签
 *         m10916g(2409-2435): 直播连播切换状态机（下一个 / 结束+消息+回调）
 *         1146: 错误覆盖层文案固定两值（实际错误消息只用于判断是否为超时）
 *         187/321: 「在线人数」标签 + displayViewerCount（model.LiveRoomUtils 已还原）
 *         m10911b 主渲染与 m10910a 卡片为 Compose 绘制层，归 Android 侧。
 * 重构说明: ExoPlayer Tracks 依赖用 (width, height) 入参隔离，轨选择规则写进 javadoc。
 */
package axtv.ui;

import axtv.model.LiveRoomUtils;
import axtv.model.LiveRoom;

/** 直播播放页 / 直播卡片的行为规格（纯函数）。 */
public final class LivePageSpec {

    /** 在线人数标签（un2:187/321 逐字）。 */
    public static final String LABEL_VIEWERS = "在线人数";
    /** 超时文案（un2:1146）。 */
    public static final String MSG_TIMEOUT = "加载超时";
    /** 失败文案（un2:1146，非超时的一切错误都显示这个）。 */
    public static final String MSG_FAILED = "加载失败";

    private LivePageSpec() {
    }

    /** 在线人数展示值（直接复用 model 层的取整规则）。 */
    public static String viewerCount(LiveRoom room) {
        return LiveRoomUtils.displayViewerCount(room);
    }

    /**
     * 错误覆盖层文案（un2:1146 原样）：
     * {@code actualError.equals("加载超时") ? "加载超时" : "加载失败"} ——
     * 上层只区分「是不是超时」，其它错误细节不展示。
     * 显示条件（un2:1143）：连播中 && 非用户主动暂停 && 错误消息非 null。
     */
    public static String errorOverlay(String actualError) {
        return MSG_TIMEOUT.equals(actualError) ? MSG_TIMEOUT : MSG_FAILED;
    }

    /**
     * 分辨率标签（m10914e，un2:2372-2403）：原实现遍历 ExoPlayer Tracks 的
     * {@code type == 2}（视频轨）分组，取**选中的** trackFormat；
     * {@code width <= 0 || height <= 0} 时置 null（不显示）。
     * 本方法接收已提取的宽高，格式 {@code width×height}（乘号 U+00D7）。
     */
    public static String resolutionLabel(int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        return width + "×" + height;
    }

    /** 连播切换结果（m10916g 的两个分支）。 */
    public record NextStep(boolean advance, int nextIndex, String endMessage) {
    }

    /**
     * 直播连播切换决策（m10916g，un2:2409-2435）：
     * <ul>
     *   <li>{@code index + 1} 在列表范围内 → 切到下一个（所有状态位重置：
     *       loading=true、错误清空、分辨率隐藏、暂停态复位）；</li>
     *   <li>超出范围（已是最后一个）→ 置 {@code endMessage} 并触发回调
     *       （原实现回调 {@code tm1Var.invoke(ho2)}，由调用方决定回列表/退出）。</li>
     * </ul>
     * 两分支共同点：loading 置 true、错误与暂停态清空。
     *
     * @param size          直播列表长度
     * @param index         当前索引（原实现取 {@code gk3.m4652h() + 1} 作为候选）
     * @param endMessage    播完提示文案（由调用方传入，原样展示）
     */
    public static NextStep nextLive(int size, int index, String endMessage) {
        int candidate = index + 1;
        if (candidate >= 0 && candidate < size) {
            return new NextStep(true, candidate, null);
        }
        return new NextStep(false, index, endMessage);
    }
}
