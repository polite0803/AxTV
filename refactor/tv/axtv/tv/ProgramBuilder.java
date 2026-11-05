/*
 * 来源: src/app/p000/mb5.java:41-75 (m7708a)
 *       src/app/p000/jx4.java:432 (m6886f0 = take(n))、446 (m6887g0 = trim)
 *       src/app/p000/o70.java:430 (m8500l1 = take(n))、o70.m8483U0 (firstOrNull)
 * 原职责: 把一个 Aweme 转成 TvContract.PreviewPrograms 的一行
 * 重构说明: 保留全部边界处理（无 id / 无封面 → 丢弃该条），三级封面回退顺序不变
 */
package axtv.tv;

import java.util.List;
import axtv.model.Aweme;
import axtv.model.Author;
import axtv.model.Cover;
import axtv.model.Video;

/** Aweme → PreviewProgram 的构造规则。 */
public final class ProgramBuilder {

    private ProgramBuilder() {
    }

    /**
     * 精确还原 {@code mb5.m7708a(long channelId, int index, Aweme)}。
     *
     * @return 该作品无法生成节目时返回 {@code null}（id 为空，或三个封面都没有 URL）
     */
    public static PreviewProgram fromAweme(long channelId, int index, Aweme aweme) {
        if (aweme == null) {
            return null;
        }
        String awemeId = aweme.awemeId();
        if (isBlank(awemeId)) {
            return null;
        }
        Video video = aweme.video();
        String poster = firstUrl(video == null ? null : video.cover());
        if (poster == null) {
            poster = firstUrl(video == null ? null : video.originCover());
        }
        if (poster == null) {
            poster = firstUrl(video == null ? null : video.dynamicCover());
        }
        if (poster == null) {
            return null;
        }

        boolean portrait = video != null && video.height() > video.width();
        Author author = aweme.author();
        String nickname = author == null || author.nickname() == null ? "" : trim(author.nickname());
        String shortDescription = nickname.isEmpty() ? "" : "@" + nickname;

        String desc = aweme.desc();
        String title = take(isBlank(desc) ? ChannelSpec.NO_TITLE : desc, ChannelSpec.TITLE_MAX_CHARS);

        int weight = ChannelSpec.WEIGHT_BASE - index;
        if (weight < ChannelSpec.MIN_WEIGHT) {
            weight = ChannelSpec.MIN_WEIGHT;
        }

        return new PreviewProgram(
                null,
                channelId,
                ChannelSpec.PROGRAM_TYPE,
                title,
                shortDescription,
                poster,
                portrait ? ChannelSpec.ASPECT_RATIO_PORTRAIT : ChannelSpec.ASPECT_RATIO_LANDSCAPE,
                video == null ? 0 : video.duration(),
                ChannelSpec.videoDeepLink(awemeId),
                awemeId,
                weight);
    }

    /** 取 {@code take(30)} 后逐个构造，跳过无法生成的条目（原 lb5:221-241）。 */
    public static List<PreviewProgram> buildWanted(long channelId, List<Aweme> videos) {
        if (videos == null || videos.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<PreviewProgram> out = new java.util.ArrayList<>();
        int limit = Math.min(videos.size(), ChannelSpec.MAX_PROGRAMS);
        for (int i = 0; i < limit; i++) {
            PreviewProgram p = fromAweme(channelId, i, videos.get(i));
            if (p != null) {
                out.add(p);
            }
        }
        return List.copyOf(out);
    }

    private static String firstUrl(Cover cover) {
        if (cover == null || cover.urlList() == null || cover.urlList().isEmpty()) {
            return null;
        }
        String first = cover.urlList().get(0);
        return isBlank(first) ? null : first;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Kotlin {@code String.trim()} 语义：去掉首尾 Unicode 空白。 */
    private static String trim(CharSequence s) {
        int start = 0;
        int end = s.length();
        while (start < end && Character.isWhitespace(s.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.subSequence(start, end).toString();
    }

    /** {@code jx4.m6886f0}: 取前 n 个字符（按 UTF-16 code unit）。 */
    private static String take(String s, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Requested character count " + n + " is less than zero.");
        }
        return s.substring(0, Math.min(n, s.length()));
    }
}
