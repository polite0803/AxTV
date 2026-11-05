/*
 * 来源: src/app/p000/n11.java —— commit/item/digg、aweme/collect、commit/dislike/item、
 *       comment/list、comment/list/reply
 * 原职责: 点赞/收藏/不喜欢/评论列表
 * 重构说明: 点赞与收藏在原实现里需要"互动安全凭据"（security payload + private_key），
 *           凭据缺失时抛 ApiException.missingInteractionCredential()；这里保留该判定点
 */
package axtv.api;

/** 互动：点赞、收藏、不喜欢、评论。 */
public final class InteractionApi {

    private final DouyinApi api;

    public InteractionApi(DouyinApi api) {
        this.api = api;
    }

    /**
     * 点赞 / 取消点赞。`/aweme/v1/web/commit/item/digg/`（POST 表单）
     *
     * @param action 1=点赞 0=取消（原实现两个分支）
     */
    public String digg(String awemeId, int action, String itemType) throws ApiException {
        requireInteractionCredential();
        return api.call(ApiRequest.postForm(DouyinEndpoints.COMMIT_DIGG)
                .body("aweme_id", awemeId)
                .body("item_type", itemType == null ? "0" : itemType)
                .body("type", String.valueOf(action))
                .commonParams()
                .sign()
                .uifidHeader()
                .ticketGuardHeaders()
                .logTicketGuardVerdicts());
    }

    /**
     * 收藏 / 取消收藏。`/aweme/v1/web/aweme/collect/`（POST 表单）
     *
     * @param action 1=收藏 0=取消
     */
    public String collect(String awemeId, int action) throws ApiException {
        requireInteractionCredential();
        return api.call(ApiRequest.postForm(DouyinEndpoints.AWEME_COLLECT)
                .body("aweme_id", awemeId)
                .body("action", String.valueOf(action))
                .commonParams()
                .sign()
                .uifidHeader()
                .ticketGuardHeaders()
                .logTicketGuardVerdicts());
    }

    /** 不喜欢。`/aweme/v1/web/commit/dislike/item/` */
    public String dislike(String awemeId) throws ApiException {
        return api.call(ApiRequest.postForm(DouyinEndpoints.COMMIT_DISLIKE)
                .body("aweme_id", awemeId)
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 一级评论。`/aweme/v1/web/comment/list/` */
    public String comments(String awemeId, long cursor, int count) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.COMMENT_LIST)
                .param("aweme_id", awemeId)
                .param("cursor", String.valueOf(cursor))
                .param("count", String.valueOf(count))
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 二级评论（回复）。`/aweme/v1/web/comment/list/reply/` */
    public String replies(String itemId, String commentId, long cursor, int count) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.COMMENT_REPLY)
                .param("item_id", itemId)
                .param("comment_id", commentId)
                .param("cursor", String.valueOf(cursor))
                .param("count", String.valueOf(count))
                .commonParams()
                .sign()
                .uifidHeader());
    }

    private void requireInteractionCredential() throws ApiException {
        TicketGuardCredentials credentials = api.cookies().ticketGuard();
        if (credentials == null || !credentials.isUsable()) {
            throw ApiException.missingInteractionCredential();
        }
    }
}
