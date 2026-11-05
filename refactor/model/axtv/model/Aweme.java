/*
 * 来源: src/app/com/github/mytv/p004dv/model/Aweme.java + Aweme$$serializer.java
 * 原职责: 一个作品（视频）的完整节点，是列表/详情/搜索/收藏等所有接口的核心单元
 * 重构说明: 数据类 → record；userDigged/collectStat 带备用 key（不同接口版本命名不同），
 *           用 @JsonKey.alternates 表达；awemeType == 0 表示「一次作品」，见 DataModels.isPrimaryAweme
 */
package axtv.model;

/**
 * @param awemeId           作品 id（{@code aweme_id}）
 * @param desc              文案/标题（{@code desc}）
 * @param video             播放信息（{@code video}）
 * @param author            作者（{@code author}）
 * @param statistics        互动数据（{@code statistics}）
 * @param createTime        发布时间，秒级 Unix 时间戳（{@code create_time}）
 * @param awemeType         作品类型，0 = 普通视频（{@code aweme_type}）
 * @param playProgress      服务端播放进度（{@code play_progress}）
 * @param userDigged        当前用户是否已点赞，1 = 是（{@code user_digged}，
 *                          备用 key：{@code is_digg} / {@code digg_status}；接口可能返回字符串）
 * @param collectStat       当前用户是否已收藏，1 = 是（{@code collect_stat}，
 *                          备用 key：{@code collect_status} / {@code is_collect} / {@code is_collected} /
 *                          {@code is_favorited}；接口可能返回字符串）
 * @param authenticationToken 播放鉴权 token（{@code authentication_token}）
 * @param mixInfo           所属合集（{@code mix_info}）；<b>可空</b>，非合集作品为 null
 */
public record Aweme(
        @JsonKey("aweme_id") String awemeId,
        @JsonKey("desc") String desc,
        @JsonKey("video") Video video,
        @JsonKey("author") Author author,
        @JsonKey("statistics") Statistics statistics,
        @JsonKey("create_time") long createTime,
        @JsonKey("aweme_type") int awemeType,
        @JsonKey("play_progress") PlayProgress playProgress,
        @JsonKey(value = "user_digged", alternates = {"is_digg", "digg_status"}) long userDigged,
        @JsonKey(value = "collect_stat", alternates = {"collect_status", "is_collect", "is_collected", "is_favorited"})
        long collectStat,
        @JsonKey("authentication_token") String authenticationToken,
        @JsonKey("mix_info") MixInfo mixInfo) {

    public Aweme {
        awemeId = awemeId == null ? "" : awemeId;
        desc = desc == null ? "" : desc;
        video = video == null ? new Video() : video;
        author = author == null ? new Author() : author;
        statistics = statistics == null ? new Statistics() : statistics;
        playProgress = playProgress == null ? new PlayProgress() : playProgress;
        authenticationToken = authenticationToken == null ? "" : authenticationToken;
    }

    /** 反编译默认值：空串 / 空 Video / 空 Author / 空 Statistics / 0 / 空 PlayProgress / null mixInfo。 */
    public Aweme() {
        this("", "", new Video(), new Author(), new Statistics(), 0L, 0,
                new PlayProgress(), 0L, 0L, "", null);
    }

    /** 原 {@code userDigged == 1} / {@code collectStat == 1} 的语义包装（0/1，不是布尔）。 */
    public boolean isDigged() {
        return userDigged == 1L;
    }

    public boolean isCollected() {
        return collectStat == 1L;
    }

    /** 是否属于某个合集（{@code mix_info} 存在且 id 非空）。 */
    public boolean hasMix() {
        return mixInfo != null && !mixInfo.mixId().isEmpty();
    }
}
