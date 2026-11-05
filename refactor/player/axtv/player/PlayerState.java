/*
 * 来源: src/app/p000/ko5.java（Player.Listener）、ro5.java（Compose 状态）
 * 原职责: 把 media3 的整数播放状态映射成界面可用状态
 * 重构说明: 用 enum 取代 {@code playbackState == 1/2/3/4} 的裸数字比较
 */
package axtv.player;

/**
 * 播放器状态机。
 *
 * <p>数值与 media3 {@code Player.STATE_*} 对齐（{@code ko5.java:148} 判断
 * {@code i == 4} 即 STATE_ENDED），另加 {@link #IDLE} 之前的 {@link #UNPREPARED}
 * 与终态 {@link #RELEASED} 以区分"还没设过媒体"和"已销毁"。
 */
public enum PlayerState {

    /** 尚未加载任何媒体。 */
    UNPREPARED(0),

    /** 已下发 setMediaItem + prepare，等内核回调（media3 无对应值）。 */
    PREPARING(0),

    /** 已设置媒体但还没 prepare（media3 STATE_IDLE）。 */
    IDLE(1),

    /** 缓冲中（media3 STATE_BUFFERING）。 */
    BUFFERING(2),

    /** 可以播放（media3 STATE_READY）。 */
    READY(3),

    /** 播放完毕（media3 STATE_ENDED），触发自动下一集。 */
    ENDED(4),

    /** 播放失败且重试/降档均无效。 */
    ERROR(5),

    /** 已 release，任何调用都是空操作。 */
    RELEASED(6);

    private final int media3Code;

    PlayerState(int media3Code) {
        this.media3Code = media3Code;
    }

    /** 对应的 media3 {@code Player.STATE_*} 值；UNPREPARED/ERROR/RELEASED 返回 0。 */
    public int media3Code() {
        return media3Code;
    }

    /** 由 media3 状态码还原（{@code ko5.java:146} 的入参）。 */
    public static PlayerState fromMedia3(int code) {
        return switch (code) {
            case 1 -> IDLE;
            case 2 -> BUFFERING;
            case 3 -> READY;
            case 4 -> ENDED;
            default -> UNPREPARED;
        };
    }

    /** 是否处于"有画面/可交互"的稳定态。 */
    public boolean isStable() {
        return this == READY || this == ENDED;
    }
}
