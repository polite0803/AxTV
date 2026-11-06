/*
 * 来源: 新建（电视直播功能，无原版对应）。
 * 原职责: 无。
 * 重构说明: 内置默认直播源常量表。每条带注释：出处（IPTV 社区项目名称）、
 *           核对日期、实测频道数、抽检可连流数。数据均来自本机实测（2026-11-07），
 *           未经实测不得硬编码入表。
 *
 * 【重要】本表是工程手段逼近的产物，任何源随时可能失效（原 best-fan 源已 404）。
 * 红线：
 * - 不要伪造 Referer/UA 破解访问控制
 * - 不要自建转播或再分发
 * - 用户自行添加的私有源同属上述红线
 */
package axtv.iptv;

import java.util.*;

/** 内置默认直播源清单。
 *  每条包含：源 ID、显示名称、M3U 完整 URL、实测条目数、抽检可连流数。
 *  数据来源：2026-11-07 本机实测。
 */
public final class BuiltInSources {

    private BuiltInSources() {
    }

    /** 源 ID 到 M3U URL 的映射（2026-11-07 本机实测：Accept-only 可拉取、可解析，且抽样流可连）。 */
    public static final Map<String, String> URLS = Map.of(
        // vbskycn 央视/卫视（IPv4）：377 条，组含 央视频道/卫视频道/地方/电影；抽检 20 条 19 条可连
        "vbskycn", "https://raw.githubusercontent.com/vbskycn/iptv/master/tv/iptv4.m3u",
        // TianmuTNT：554 条，17 CCTV/53 卫视；抽检 10 条 10 条可连
        "tianmutv", "https://raw.githubusercontent.com/TianmuTNT/iptv/main/iptv.m3u",
        // suxuang/myIPTV（IPv4）：1273 条，159 CCTV/233 卫视；抽检 30 条约 17 条可连
        "suxuang", "https://raw.githubusercontent.com/suxuang/myIPTV/main/ipv4.m3u",
        // iptv-org 中国频道（154 条，36 CCTV）：抽检 10 条 7 条可连（CDN 托管，可达性好）
        "iptv-org-cn", "https://iptv-org.github.io/iptv/countries/cn.m3u",
        // iptv-org 中国全量（516 条，含海外/失效，作广度兜底）：抽检 10 条 3 条可连
        "iptv-org-cn-all", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/cn.m3u"
    );

    /** 源 ID 对应的显示名称。 */
    public static final Map<String, String> DISPLAY_NAMES = Map.of(
        "vbskycn", "vbskycn 央视/卫视（IPv4）",
        "tianmutv", "TianmuTNT 综合",
        "suxuang", "suxuang 央视/卫视（IPv4）",
        "iptv-org-cn", "iptv-org 中国频道",
        "iptv-org-cn-all", "iptv-org 中国全量"
    );

    /** 核对日期（YYYY-MM-DD 格式）。 */
    public static final Map<String, String> VERIFICATION_DATES = Map.of(
        "vbskycn", "2026-11-07",
        "tianmutv", "2026-11-07",
        "suxuang", "2026-11-07",
        "iptv-org-cn", "2026-11-07",
        "iptv-org-cn-all", "2026-11-07"
    );

    /** 实测频道数（该 m3u 里的总条目数）。 */
    public static final Map<String, Integer> ESTIMATED_CHANNEL_COUNTS = Map.of(
        "vbskycn", 377,
        "tianmutv", 554,
        "suxuang", 1273,
        "iptv-org-cn", 154,
        "iptv-org-cn-all", 516
    );

    /** 抽样可连通的流条数（各源样本量见 URLS 注释；保守活性读数，非全量统计）。 */
    public static final Map<String, Integer> REALTV_COVERAGE = Map.of(
        "vbskycn", 19,
        "tianmutv", 10,
        "suxuang", 17,
        "iptv-org-cn", 7,
        "iptv-org-cn-all", 3
    );
}