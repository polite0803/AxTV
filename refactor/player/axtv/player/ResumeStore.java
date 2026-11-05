/*
 * 来源: src/app/p000/i53.java:219-223（zd1.mo5580j(awemeId) 取进度）、zd1.java（播放进度存储）
 * 原职责: 进入某条视频时恢复上次播放位置
 * 重构说明: 抽象为接口，Android 侧用 DataStore/SharedPreferences 实现
 */
package axtv.player;

/**
 * 播放进度存储。
 *
 * <p>原实现：进播放页前 {@code zd1.mo5580j(awemeId)} 拿到上次进度，直接带进
 * {@code ro5.m9956g(..., seekMs, ...)} 与 {@code mo5} 的 {@code seekTo(j)}。
 */
public interface ResumeStore {

    /**
     * 上次播放位置。
     *
     * @return 毫秒；未知返回 0
     */
    long positionOf(String itemId);

    /** 记录位置（暂停/切换/退出时调用）。 */
    void savePosition(String itemId, long positionMs);

    /** 是否已看完（用于跳过 tail 恢复）。 */
    default boolean isFinished(String itemId) {
        return false;
    }
}
