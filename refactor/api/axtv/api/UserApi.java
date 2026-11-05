/*
 * 来源: src/app/p000/n11.java —— user/profile/self、user/profile/other、aweme/post、
 *       aweme/favorite、user/following/list、user/follower/list、aweme/listcollection
 * 原职责: 我的主页、他人主页、作品/喜欢/收藏列表、关注与粉丝列表
 * 重构说明: 参数键取自 n11 调用点字面量
 */
package axtv.api;

/** 用户主页与作品列表。 */
public final class UserApi {

    private final DouyinApi api;

    public UserApi(DouyinApi api) {
        this.api = api;
    }

    /** 我的主页。`/aweme/v1/web/user/profile/self/` */
    public String profileSelf() throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.USER_PROFILE_SELF)
                .commonParams()
                .sign()
                .uifidHeader()
                .userCenterSignChain());
    }

    /** 他人主页。`/aweme/v1/web/user/profile/other/` */
    public String profileOther(String secUserId, String source) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.USER_PROFILE_OTHER)
                .param("sec_user_id", secUserId)
                .param("source", source)
                .param("publish_video_strategy_type", "2")
                .param("personal_center_strategy", "1")
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 用户作品。`/aweme/v1/web/aweme/post/` */
    public String userPosts(String secUserId, long maxCursor, int count) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.AWEME_POST)
                .param("sec_user_id", secUserId)
                .param("max_cursor", String.valueOf(maxCursor))
                .param("count", String.valueOf(count))
                .param("from_user_page", "1")
                .param("need_time_list", "1")
                .param("time_list_query", "0")
                .param("publish_video_strategy_type", "2")
                .param("show_live_replay_strategy", "1")
                .param("whale_cut_token", "")
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 用户"喜欢"列表。`/aweme/v1/web/aweme/favorite/` */
    public String userFavorites(String secUserId, long maxCursor, long minCursor, int count) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.AWEME_FAVORITE)
                .param("sec_user_id", secUserId)
                .param("max_cursor", String.valueOf(maxCursor))
                .param("min_cursor", String.valueOf(minCursor))
                .param("count", String.valueOf(count))
                .param("publish_video_strategy_type", "2")
                .param("cut_version", "1")
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 关注列表。`/aweme/v1/web/user/following/list/` */
    public String following(String secUserId, int offset, int count) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.USER_FOLLOWING)
                .param("sec_user_id", secUserId)
                .param("offset", String.valueOf(offset))
                .param("count", String.valueOf(count))
                .param("source_type", "1")
                .param("is_top", "1")
                .commonParams()
                .sign()
                .uifidHeader()
                .userCenterSignChain());
    }

    /** 粉丝列表。`/aweme/v1/web/user/follower/list/` */
    public String followers(String secUserId, int offset, int count) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.USER_FOLLOWER)
                .param("sec_user_id", secUserId)
                .param("offset", String.valueOf(offset))
                .param("count", String.valueOf(count))
                .param("list_type", "0")
                .commonParams()
                .sign()
                .uifidHeader()
                .userCenterSignChain());
    }

    /**
     * 收藏夹列表。`/aweme/v1/web/aweme/listcollection/`
     *
     * <p>原实现参数由 helper 构造，未逐字核实（见 NOTES.md「存疑」）。
     */
    public String collectionList(long cursor, int count) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.AWEME_LISTCOLLECTION)
                .param("cursor", String.valueOf(cursor))
                .param("count", String.valueOf(count))
                .commonParams()
                .sign()
                .userCenterSignChain());
    }
}
