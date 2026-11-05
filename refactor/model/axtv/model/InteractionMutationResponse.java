/*
 * 来源: src/app/com/github/mytv/p004dv/model/InteractionMutationResponse.java
 * 原职责: 点赞/收藏接口的返回体（返回操作后的状态）
 * 重构说明: 数据类 → record；isDigg / isCollect 是**可空 Integer**：
 *           该接口可能只回其中一项，null 表示「本次没返回」而不是「未点赞」，不要折叠成 0。
 *           $$serializer 未导出，key 按惯例推定。
 */
package axtv.model;

/**
 * @param isDigg     操作后是否已点赞，0/1（推定 {@code is_digg}）；<b>可空</b>
 * @param isCollect  操作后是否已收藏，0/1（推定 {@code is_collect}）；<b>可空</b>
 * @param statusCode 状态码（推定 {@code status_code}）
 * @param statusMsg  状态文案（推定 {@code status_msg}）
 */
public record InteractionMutationResponse(
        @JsonKey("is_digg") Integer isDigg,
        @JsonKey("is_collect") Integer isCollect,
        @JsonKey("status_code") int statusCode,
        @JsonKey("status_msg") String statusMsg) {

    public InteractionMutationResponse {
        statusMsg = statusMsg == null ? "" : statusMsg;
    }

    public InteractionMutationResponse() {
        this(null, null, 0, "");
    }
}
