/*
 * 来源: src/app/p000/n11.java —— user/profile/self、user/profile/other、aweme/post、
 *       aweme/favorite、user/following/list、user/follower/list、aweme/listcollection
 * 原职责: 我的主页、他人主页、作品/喜欢/收藏列表、关注与粉丝列表
 * 重构说明: 参数键取自 n11 调用点字面量
 */
package axtv.api;

import axtv.model.UserListResponse;
import axtv.model.UserProfileResponse;
import axtv.model.UserWorksResponse;

/**
 * 用户主页与作品列表。
 *
 * <p>返回类型以原实现的解析协程为准（ez0 byte 19/20/21/22/24/26）：
 * 主页返回 {@link UserProfileResponse}，作品/喜欢/收藏夹返回 {@link UserWorksResponse}，
 * 关注/粉丝返回 {@link UserListResponse}。
 * 注意：`aweme/listcollection` 在原实现里解成 {@code UserWorksResponse}（作品列表），
 * 不是 {@code CollectListResponse}（收藏夹元信息）。
 */
public final class UserApi {

    private final DouyinApi api;

    public UserApi(DouyinApi api) {
        this.api = api;
    }

    /** 我的主页。`/aweme/v1/web/user/profile/self/` */
    public UserProfileResponse profileSelf() throws ApiException {
        // 原版 m8047j0：m8002r(..., 252) —— a_bogus + 公共参数，不带 uifid 头/ secsdk 签名。
        return api.callFor(ApiRequest.get(DouyinEndpoints.USER_PROFILE_SELF)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .commonParams()
                .sign(), UserProfileResponse.class);
    }

    /** 他人主页。`/aweme/v1/web/user/profile/other/` */
    public UserProfileResponse profileOther(String secUserId, String source) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.USER_PROFILE_OTHER)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .param("sec_user_id", secUserId)
                .param("source", source)
                .param("publish_video_strategy_type", "2")
                .param("personal_center_strategy", "1")
                .commonParams()
                .sign()
                .uifidHeader(), UserProfileResponse.class);
    }

    /** 用户作品。`/aweme/v1/web/aweme/post/` */
    public UserWorksResponse userPosts(String secUserId, long maxCursor, int count) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.AWEME_POST)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .param("sec_user_id", secUserId)
                .param("max_cursor", String.valueOf(maxCursor))
                .param("count", String.valueOf(count))
                .param("from_user_page", "1")
                .param("need_time_list", "1")
                .param("time_list_query", "0")
                .param("publish_video_strategy_type", "2")
                .param("show_live_replay_strategy", "1")
                .param("whale_cut_token", "")
                // aweme/post 与 profile/self 不同：服务端要求用户中心签名链
                // （a_bogus + uifid + timestamp + x-secsdk-web-signature）。
                .commonParams()
                .userCenterSignChain(), UserWorksResponse.class);
    }

    /** 用户"喜欢"列表。`/aweme/v1/web/aweme/favorite/` */
    public UserWorksResponse userFavorites(String secUserId, long maxCursor, long minCursor, int count) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.AWEME_FAVORITE)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .param("sec_user_id", secUserId)
                .param("max_cursor", String.valueOf(maxCursor))
                .param("min_cursor", String.valueOf(minCursor))
                .param("count", String.valueOf(count))
                .param("publish_video_strategy_type", "2")
                .param("cut_version", "1")
                .param("whale_cut_token", "")
                // 收藏列表同样先走用户中心签名链；失败分支再退回无签名（见仓库层重试）。
                .commonParams()
                .userCenterSignChain(), UserWorksResponse.class);
    }

    /** 关注列表。`/aweme/v1/web/user/following/list/` */
    public UserListResponse following(String secUserId, int offset, int count) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.USER_FOLLOWING)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .param("sec_user_id", secUserId)
                .param("offset", String.valueOf(offset))
                .param("count", String.valueOf(count))
                .param("source_type", "1")
                .param("is_top", "1")
                .commonParams()
                .sign()
                .uifidHeader()
                .userCenterSignChain(), UserListResponse.class);
    }

    /** 粉丝列表。`/aweme/v1/web/user/follower/list/` */
    public UserListResponse followers(String secUserId, int offset, int count) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.USER_FOLLOWER)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .param("sec_user_id", secUserId)
                .param("offset", String.valueOf(offset))
                .param("count", String.valueOf(count))
                .param("list_type", "0")
                .commonParams()
                .sign()
                .uifidHeader()
                .userCenterSignChain(), UserListResponse.class);
    }

    /**
     * 收藏夹列表。`/aweme/v1/web/aweme/listcollection/`
     *
     * <p>原实现参数由 helper 构造，未逐字核实（见 NOTES.md「存疑」）。
     */
    public UserWorksResponse collectionList(long cursor, int count) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.AWEME_LISTCOLLECTION)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .param("cursor", String.valueOf(cursor))
                .param("count", String.valueOf(count))
                .commonParams()
                .sign()
                .userCenterSignChain(), UserWorksResponse.class);
    }
}
