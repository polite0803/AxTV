/*
 * 来源: src/app/p000/i53.java:214-224（取当前/下一个并预载）、jj5.java:74 / wf5.java:73（预载下一个）、
 *       ko5.java:148-150（STATE_ENDED 触发下一集）、o70.m8484V0（按索引取列表项）
 * 原职责: 播放列表游标 + 播完自动下一条 + 下一条预载
 * 重构说明: 抽成独立的纯逻辑队列，不涉及播放器
 */
package axtv.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * 播放队列（信息流/合集连播）。
 *
 * <p>原实现里"队列"就是信息流列表 + 一个游标（{@code o70.m8484V0(position, list)}），
 * 播完由 {@code ko5.onPlaybackStateChanged(STATE_ENDED)} 回调父层推进。
 * 这里把这套语义收拢：
 * <ul>
 *   <li>游标前进/后退；</li>
 *   <li>播完自动切下一条（可关）；</li>
 *   <li>提前拿 {@link #peekNext()} 给缓存层预载头部 2 MiB；</li>
 *   <li>每条带 id 用于记录播放进度（{@link ResumeStore}）。</li>
 * </ul>
 */
public final class PlaybackQueue {

    private static final Logger LOG = Logger.getLogger("VideoPlayer");

    /**
     * 一条待播项。
     *
     * @param id         业务 id（抖音 awemeId）
     * @param title      标题，仅日志/埋点用
     * @param durationMs 时长，未知传 0
     */
    public record Item(String id, String title, long durationMs) {
    }

    /** 把 item 解析成可播放直链（Android 侧实现：码率/编码偏好 + 签名）。 */
    public interface UrlResolver {
        /**
         * @return 直链，失败返回 null
         */
        String resolve(Item item);
    }

    /** 游标变化回调。 */
    public interface Listener {
        void onCurrentChanged(Item item, int index);
    }

    private final UrlResolver resolver;
    private final List<Item> items = new ArrayList<>();
    private int index = -1;
    private boolean autoNextEnabled = true;
    private Listener listener;

    public PlaybackQueue(UrlResolver resolver) {
        this.resolver = resolver;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** 整体替换列表并定位到 {@code startIndex}（信息流刷新/进入合集时调用）。 */
    public synchronized void replaceAll(List<Item> newItems, int startIndex) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        index = items.isEmpty() ? -1 : Math.max(0, Math.min(startIndex, items.size() - 1));
        notifyChanged();
    }

    /**
     * 在队尾追加条目（信息流在播放器内继续翻页），按 id 去重且不移动游标。
     *
     * @return 实际追加的条数
     */
    public synchronized int append(List<Item> newItems) {
        if (newItems == null || newItems.isEmpty()) {
            return 0;
        }
        java.util.HashSet<String> known = new java.util.HashSet<>();
        for (Item item : items) {
            known.add(item.id());
        }
        int added = 0;
        for (Item item : newItems) {
            if (item != null && known.add(item.id())) {
                items.add(item);
                added++;
            }
        }
        return added;
    }

    public synchronized List<Item> items() {
        return Collections.unmodifiableList(items);
    }

    public synchronized int index() {
        return index;
    }

    /** 当前项，空队列返回 null。 */
    public synchronized Item current() {
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    /** 当前项对应直链。 */
    public String currentUrl() {
        Item item = current();
        if (item == null) {
            return null;
        }
        return resolveUrl(item);
    }

    /**
     * 解析某条的直链。
     *
     * @return 直链；失败返回 null（调用方负责上报"解析播放地址失败"）
     */
    public String resolveUrl(Item item) {
        if (item == null) {
            return null;
        }
        String url = resolver.resolve(item);
        if (url == null || url.isEmpty()) {
            LOG.warning("解析播放地址失败: id=" + item.id());
            return null;
        }
        return url;
    }

    /** 下一项（不移动游标），用于预载。 */
    public synchronized Item peekNext() {
        int next = index + 1;
        return next >= 0 && next < items.size() ? items.get(next) : null;
    }

    public synchronized boolean hasNext() {
        return index + 1 < items.size();
    }

    public synchronized boolean hasPrevious() {
        return index > 0;
    }

    /**
     * 游标前进一条。
     *
     * @return 是否成功；已在末尾返回 false
     */
    public synchronized boolean moveNext() {
        if (!hasNext()) {
            return false;
        }
        index++;
        notifyChanged();
        return true;
    }

    /** 游标后退一条。 */
    public synchronized boolean movePrevious() {
        if (!hasPrevious()) {
            return false;
        }
        index--;
        notifyChanged();
        return true;
    }

    /** 前进并返回新当前项（不检查是否到底）。 */
    public synchronized Item next() {
        return moveNext() ? current() : null;
    }

    /** 后退并返回新当前项。 */
    public synchronized Item previous() {
        return movePrevious() ? current() : null;
    }

    /** 显式跳转到某条（点合集某集）。 */
    public synchronized boolean moveTo(int target) {
        if (target < 0 || target >= items.size() || target == index) {
            return false;
        }
        index = target;
        notifyChanged();
        return true;
    }

    /**
     * 播放结束时的推进决策。
     *
     * @return 需要播放的下一条，null 表示播完停下
     */
    public synchronized Item onPlaybackEnded() {
        if (!autoNextEnabled) {
            return null;
        }
        if (!moveNext()) {
            return null;
        }
        return current();
    }

    public synchronized boolean isAutoNextEnabled() {
        return autoNextEnabled;
    }

    public synchronized void setAutoNextEnabled(boolean enabled) {
        this.autoNextEnabled = enabled;
    }

    private void notifyChanged() {
        Listener l = listener;
        if (l != null) {
            l.onCurrentChanged(current(), index);
        }
    }
}
