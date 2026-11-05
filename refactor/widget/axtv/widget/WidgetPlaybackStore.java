/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetPlaybackStore.java (40 行)
 * 原职责: 内存中的 MutableStateFlow&lt;WidgetPlayback?&gt;(null)，publish / clear / getState
 * 重构说明: 用 AtomicReference + 监听器列表还原「可观察的当前播放态」，不持久化。
 *           Android 侧可直接代理到 StateFlow；核心层保持纯 JDK。
 */
package axtv.widget;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** 正在播放状态 store（内存，初值 null）。 */
public final class WidgetPlaybackStore {

    /** 状态变更监听（对应 StateFlow.collect）。 */
    public interface Listener {
        void onChanged(WidgetPlayback value);
    }

    private final AtomicReference<WidgetPlayback> state = new AtomicReference<>(null);
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    /** 对应 getState().getValue()。 */
    public WidgetPlayback getState() {
        return state.get();
    }

    /** 对应 publish(WidgetPlayback)。 */
    public void publish(WidgetPlayback playback) {
        if (playback == null) {
            throw new NullPointerException("playback");
        }
        state.set(playback);
        for (Listener l : listeners) {
            l.onChanged(playback);
        }
    }

    /** 对应 clear()：置回 null。 */
    public void clear() {
        state.set(null);
        for (Listener l : listeners) {
            l.onChanged(null);
        }
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }
}
