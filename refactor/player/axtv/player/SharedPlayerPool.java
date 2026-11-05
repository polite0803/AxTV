/*
 * 来源: src/app/p000/wi4.java:23543-23547（全局共享播放器静态槽）、
 *       ro5.java:35936-36082（m9957h 取用 + m9955f 构建）、C1771we.java（acquire）、
 *       oo5.java（release/引用计数）、je2.java（回收日志）
 * 原职责: 跨页面复用同一个 ExoPlayer，靠引用计数决定何时暂停
 * 重构说明: 收拢为带租约的对象池；独立播放器（PIP）走独立生命周期
 */
package axtv.player;

import java.util.logging.Logger;

/**
 * 共享播放器池。
 *
 * <p>为什么共享：播放页与信息流、转场动画、PIP 会同时需要播放能力，反复
 * {@code build/release} ExoPlayer 会造成 200~400ms 的黑屏；共享后切页只是 {@code setMediaItem}。
 *
 * <p>规则（逐条对应原实现）：
 * <ol>
 *   <li>首次取用：{@code log "创建全局共享播放器"}，记录 client 身份哈希；</li>
 *   <li>同一 client 再取用：直接复用，{@code setVolume(1f)}，{@code log "复用全局共享播放器, 当前引用数: N"}；</li>
 *   <li>client 变了（换了 OkHttpClient 配置）：释放旧的、重建，同样打"创建全局共享播放器"；</li>
 *   <li>每次挂载 +1，卸载 -1；计数归零且当前页面已不在播放时，{@code setPlayWhenReady(false)+pause}；</li>
 *   <li>独立播放器（{@code independent=true}）不进池，销毁时直接 release 并打"释放独立播放器"。</li>
 * </ol>
 */
public final class SharedPlayerPool {

    private static final Logger LOG = Logger.getLogger("VideoPlayer");

    /** 创建播放内核。Android 侧实现即 {@code ro5.m9955f}。 */
    public interface EngineFactory {
        MediaEngine create(Object clientKey);
    }

    private final EngineFactory factory;

    private MediaEngine shared;
    private Object sharedClientKey;
    private int refCount;

    public SharedPlayerPool(EngineFactory factory) {
        this.factory = factory;
    }

    /**
     * 取用共享播放器（{@code ro5.m9957h}）。
     *
     * <p>注意：本方法不增加引用计数；计数由界面挂载/卸载负责，见 {@link #addRef()}。
     */
    public synchronized MediaEngine obtain(Object clientKey) {
        if (shared == null) {
            LOG.fine("创建全局共享播放器");
            shared = factory.create(clientKey);
            sharedClientKey = clientKey;
            return shared;
        }
        if (sharedClientKey == clientKey || (sharedClientKey != null && sharedClientKey.equals(clientKey))) {
            LOG.fine("复用全局共享播放器, 当前引用数: " + refCount);
            shared.setVolume(PlayerConfig.VOLUME_UNMUTED);
            return shared;
        }
        shared.release();
        LOG.fine("创建全局共享播放器");
        shared = factory.create(clientKey);
        sharedClientKey = clientKey;
        return shared;
    }

    /** 挂载 +1（{@code C1771we} 的 DisposableEffect）。 */
    public synchronized void addRef() {
        refCount++;
    }

    /** 新建一个不共享的播放器（PIP / 小窗，{@code ro5.java:827} 的独立分支）。 */
    public MediaEngine newIndependent() {
        return factory.create(null);
    }

    /**
     * 卸载 -1（{@code oo5.dispose}）。
     *
     * @param pauseWhenIdle 计数归零时是否暂停播放
     */
    public synchronized void releaseRef(boolean pauseWhenIdle) {
        if (refCount > 0) {
            refCount--;
        }
        if (pauseWhenIdle && refCount <= 0 && shared != null) {
            shared.setPlayWhenReady(false);
            shared.pause();
        }
    }

    /** 独立播放器销毁（{@code oo5} 的 {@code z==true} 分支）。 */
    public static void releaseIndependent(MediaEngine engine) {
        if (engine == null) {
            return;
        }
        engine.setPlayWhenReady(false);
        engine.pause();
        LOG.fine("释放独立播放器");
        engine.release();
    }

    /** 回收共享播放器（{@code je2} 的默认分支，进程/页面彻底退出时）。 */
    public synchronized void releaseAll() {
        if (shared != null) {
            LOG.fine("回收共享播放器");
            shared.release();
            shared = null;
            sharedClientKey = null;
        }
        refCount = 0;
    }

    public synchronized int refCount() {
        return refCount;
    }

    public synchronized MediaEngine shared() {
        return shared;
    }
}
