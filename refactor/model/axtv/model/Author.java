/*
 * 来源: src/app/com/github/mytv/p004dv/model/Author.java + Author$$serializer.java
 * 原职责: 作者/用户公开资料（昵称、三档头像、签名、各项计数）
 * 重构说明: 数据类 → record；六个计数全部走 FlexibleLong（抖音把 follower_count 等返回成字符串）
 */
package axtv.model;

/**
 * @param uid             用户数字 id（{@code uid}）
 * @param nickname        昵称（{@code nickname}）
 * @param avatarThumb     小头像（{@code avatar_thumb}）
 * @param avatarMedium    中头像（{@code avatar_medium}）
 * @param avatarLarger    大头像（{@code avatar_larger}）
 * @param secUid          加密 id（{@code sec_uid}），接口签名/用户主页参数都用它
 * @param signature       个性签名（{@code signature}）
 * @param followerCount   粉丝数（{@code follower_count}，接口可能返回字符串）
 * @param followingCount  关注数（{@code following_count}，可能返回字符串）
 * @param totalFavorited  获赞总数（{@code total_favorited}，可能返回字符串）
 * @param awemeCount      作品数（{@code aweme_count}，可能返回字符串）
 * @param favoritingCount 收藏数（{@code favoriting_count}，可能返回字符串）
 */
public record Author(
        @JsonKey("uid") String uid,
        @JsonKey("nickname") String nickname,
        @JsonKey("avatar_thumb") Avatar avatarThumb,
        @JsonKey("avatar_medium") Avatar avatarMedium,
        @JsonKey("avatar_larger") Avatar avatarLarger,
        @JsonKey("sec_uid") String secUid,
        @JsonKey("signature") String signature,
        @JsonKey("follower_count") long followerCount,
        @JsonKey("following_count") long followingCount,
        @JsonKey("total_favorited") long totalFavorited,
        @JsonKey("aweme_count") long awemeCount,
        @JsonKey("favoriting_count") long favoritingCount) {

    public Author {
        uid = uid == null ? "" : uid;
        nickname = nickname == null ? "" : nickname;
        avatarThumb = avatarThumb == null ? new Avatar() : avatarThumb;
        avatarMedium = avatarMedium == null ? new Avatar() : avatarMedium;
        avatarLarger = avatarLarger == null ? new Avatar() : avatarLarger;
        secUid = secUid == null ? "" : secUid;
        signature = signature == null ? "" : signature;
    }

    /** 反编译默认值：字符串 ""、头像为空 Avatar、计数 0。 */
    public Author() {
        this("", "", new Avatar(), new Avatar(), new Avatar(), "", "", 0L, 0L, 0L, 0L, 0L);
    }

    /** 头像优先级：大 → 中 → 缩略（界面用大头像，失败逐级退回）。 */
    public Avatar bestAvatar() {
        if (!avatarLarger.urlList().isEmpty()) {
            return avatarLarger;
        }
        if (!avatarMedium.urlList().isEmpty()) {
            return avatarMedium;
        }
        return avatarThumb;
    }
}
