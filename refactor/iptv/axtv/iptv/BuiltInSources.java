/*
 * 来源: 新建（电视直播功能，无原版对应）。
 * 原职责: 无。
 * 重构说明: 内置默认直播源常量表。每条带注释：出处（IPTV 社区项目名称）、
 *           核对日期、实测频道数、央视/卫视覆盖情况。数据均来自阶段 F 实测留档
 *           （docs/电视直播源核对.md），未经实测不得硬编码入表。
 *
 * 【重要】本表是工程手段逼近的产物，任何源随时可能失效。红线：
 * - 不要伪造 Referer/UA 破解访问控制
 * - 不要自建转播或再分发
 * - 用户自行添加的私有源同属上述红线
 */
package axtv.iptv;

import java.util.*;

/** 内置默认直播源清单。
 *  每条包含：源 ID、显示名称、M3U 完整 URL、预估频道数、备注。
 *  数据来源：阶段 F 工程实测，详见 docs/电视直播源核对.md。
 *  2026-11-05 统计：70/94 央视/卫视频道在本清单 m3u8 中可直接播放。
 */
public final class BuiltInSources {

    private BuiltInSources() {
    }

    /** 源 ID 到 M3U URL 的映射。 */
    public static final Map<String, String> URLS = Map.of(
        // best-fan 社区索引（2026-11-05 实测：70/94 央视卫视可用）
        "best-fan", "https://best-fan.github.io/iptv/cn_all_status.m3u8",
        // boydqz gitee（已被屏蔽，保留供对照，实际已失效）
        // "boydqz", "https://boydqz.gitee.io/iptv/best.m3u8"
        // iptv-org 国内线路（2026-11-05 实测：0/14 可用）
        // "iptv-org", "https://iptv-org.github.io/iptv-channel-m3u8/master.m3u8"
        // 备用来源：标记为禁用，防止意外启用
        "disabled-best-fan", "https://best-fan.github.io/iptv/cn_all_status.m3u8"
    );

    /** 源 ID 对应的显示名称。 */
    public static final Map<String, String> DISPLAY_NAMES = Map.of(
        "best-fan", "best-fan 社区索引",
        "boydqz", "boydqz Gitee（已屏蔽）",
        "iptv-org", "iptv-org 国内线路",
        "disabled-best-fan", "best-fan（禁用）"
    );

    /** 核对日期（YYYY-MM-DD 格式）。 */
    public static final Map<String, String> VERIFICATION_DATES = Map.of(
        "best-fan", "2026-11-05"
        // "boydqz", "2026-10-20"
        // "iptv-org", "2026-11-05"
    );

    /** 实测频道数（该 m3u8 里的总条目数）。 */
    public static final Map<String, Integer> ESTIMATED_CHANNEL_COUNTS = Map.of(
        "best-fan", 214
        // "boydqz", 0
        // "iptv-org", 0
    );

    /** 實測可用的央视/衛視預覆數（真 m3u8 可播放的频道数）。 */
    public static final Map<String, Integer> REALTV_COVERAGE = Map.of(
        "best-fan", 70
        // "boydqz", 0
        // "iptv-org", 0
    );
}