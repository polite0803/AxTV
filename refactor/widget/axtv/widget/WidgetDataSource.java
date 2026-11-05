/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetRefresher.java
 *       refreshHistory:337 (kq5.m7253F(0L) → UserWorksResponse)
 *       refreshLater:389  (kq5.m7254G(0)  → List<Aweme>)
 *       refreshLive:441   (kq5.m7275m()   → Iterable<LiveRoom>)
 *       refreshSearch:532 (kq5.m7273k()   → Iterable<HotWord>)
 *       refreshMix:500 / loadMixItem:305  (反编译溢出未还原，见 NOTES.md 存疑)
 * 原职责: p000.kq5 是抖音数据仓库，按 WidgetKind 拉取对应榜单/作品/直播间/热词/合集选集
 * 重构说明: 核心层不依赖网络/App model，用接口隔离；Raw 嵌套接口承载 App model 里
 *           WidgetData.toWidgetVideo / toWidgetLiveItem 所需的原始字段，Android 侧适配填充。
 */
package axtv.widget;

import java.util.List;

/** 小组件刷新所需的数据源抽象（原 p000.kq5 仓库）。 */
public interface WidgetDataSource {

    /**
     * 拉取用户作品并已应用 DataModelKt.filterPrimaryPlayableAwemes 过滤。
     * 对应 kq5.m7253F(0L, cont) → UserWorksResponse.getAwemeList() → filterPrimaryPlayableAwemes。
     */
    List<Video> fetchHistory(long cursor);

    /** 拉取稍后再看列表；对应 kq5.m7254G(0, cont)。 */
    List<Video> fetchLater(int page);

    /** 拉取关注主播直播列表；对应 kq5.m7275m(cont)。 */
    List<LiveRoom> fetchLive();

    /** 拉取热搜词；对应 kq5.m7273k(cont)。 */
    List<HotWord> fetchHotWords();

    /**
     * 刷新合集追更：遍历现有 items，对每个拉取最新选集/进度，返回新的 MixSnapshot.items。
     * 对应 refreshMix + loadMixItem（原方法反编译溢出，语义见 NOTES.md）。
     */
    List<WidgetMixItem> fetchMixUpdates(String accountId, List<WidgetMixItem> existing);

    /**
     * 一次刷新结束后的资源释放钩子，对应 WidgetRefresher.refresh 里 c1608s.m10094a()。
     * 实现应把网络/IO 的检查型异常包装为运行时异常抛出，以便核心层统一分类处理。
     */
    default void onRefreshFinished() {
    }

    /** 原始作品字段（适配 com.github.axtv.dv.model.Aweme）。 */
    interface Video {
        String awemeId();
        String desc();
        String authorNickname();
        /** 头像候选 URL：avatarThumb.urlList + avatarMedium.urlList，按序取第一个非空。 */
        List<String> authorAvatarUrls();
        /** 封面候选 URL：originCover.urlList + cover.urlList，按序取第一个非空。 */
        List<String> coverUrls();
        /** 视频时长（毫秒），原 getVideo().getDuration()。 */
        long durationMs();
        /** 点赞数，原 getStatistics().getDiggCount()。 */
        long diggCount();
        /** 合集信息，可为 null（原 getMixInfo()）。 */
        MixInfo mixInfo();
    }

    /** 原始合集信息（适配 com.github.axtv.dv.model.MixInfo + MixStatis）。 */
    interface MixInfo {
        String mixId();
        String mixName();
        /** 封面 URL 候选（coverUrl.getUrlList()），可为空。 */
        List<String> coverUrls();
        int currentEpisode();
        int totalEpisode();
        int updatedToEpisode();
    }

    /** 原始直播间（适配 com.github.axtv.dv.model.LiveRoom）。 */
    interface LiveRoom {
        String resolvedRoomId();
        String title();
        String ownerNickname();
        /** avatarThumb.urlList + avatarMedium.urlList。 */
        List<String> ownerAvatarUrls();
        /** roomViewStats.displayShort，可为 null。 */
        String displayShort();
        /** 兜底在线人数 userCount，可为 null/空。 */
        String userCount();
    }

    /** 原始热词（适配 com.github.axtv.dv.model.HotWord）。 */
    interface HotWord {
        String word();
        int label();
    }
}
