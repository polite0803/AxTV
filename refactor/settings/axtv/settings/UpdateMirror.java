/*
 * 来源: src/app/p000/lf5.java（f13115e 镜像表、f13116f/13117g/13118h/13119i 缓存、m7411a 入口）
 *       + src/app/p000/ff5.java / nu1.java（两处「取镜像前缀列表」的调用点，5 分钟 TTL）
 * 原职责: GitHub release 下载的 ghproxy 镜像回退
 * 重构说明: 抽成独立类；顺序、域名、TTL、空串代表「直连」全部原样保留
 */
package axtv.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * GitHub 加速镜像。
 *
 * <p>原文把镜像前缀写死在 {@code lf5.f13115e}，共 10 项，最后一项是空串 —— 表示直接连
 * {@code github.com}（无代理）。检查更新与下载 APK 时都按此顺序逐个尝试，第一个成功即停。
 *
 * <p>原文有两条独立缓存（检查用 / 下载用），各自 TTL 5 分钟（{@code System.currentTimeMillis() + 300000}）。
 * 缓存的是「前缀列表」，不是结果；本类用两个槽位复刻这一行为。
 */
public final class UpdateMirror {

    /** 缓存有效期：5 分钟（原 {@code 300000L}）。 */
    public static final long CACHE_TTL_MILLIS = 300_000L;

    /** 镜像前缀，顺序即回退顺序；最后一项空串 = 直连。 */
    public static final List<String> PREFIXES = List.of(
            "https://gh-proxy.org/",
            "https://gh-proxy.com/",
            "https://ghproxy.net/",
            "https://gh.cc/",
            "https://mirror.ghproxy.com/",
            "https://github.moeyy.xyz/",
            "https://ghproxy.cn/",
            "https://gh.api.99988866.xyz/",
            "https://gh-proxy.ygxz.in/",
            "");

    private List<String> checkCache;
    private long checkCacheExpiry;
    private List<String> downloadCache;
    private long downloadCacheExpiry;

    /** 检查更新用的前缀列表（原 {@code lf5.f13117g} + {@code f13116f}）。 */
    public synchronized List<String> forCheck() {
        long now = System.currentTimeMillis();
        if (checkCache == null || now >= checkCacheExpiry) {
            checkCache = List.copyOf(PREFIXES);
            checkCacheExpiry = now + CACHE_TTL_MILLIS;
        }
        return checkCache;
    }

    /** 下载 APK 用的前缀列表（原 {@code lf5.f13119i} + {@code f13118h}，与检查缓存互不影响）。 */
    public synchronized List<String> forDownload() {
        long now = System.currentTimeMillis();
        if (downloadCache == null || now >= downloadCacheExpiry) {
            downloadCache = List.copyOf(PREFIXES);
            downloadCacheExpiry = now + CACHE_TTL_MILLIS;
        }
        return downloadCache;
    }

    /** 按原文拼接方式生成候选地址：前缀为空串时原样返回目标地址。 */
    public static List<String> candidates(List<String> prefixes, String targetUrl) {
        List<String> urls = new ArrayList<>(prefixes.size());
        for (String prefix : prefixes) {
            urls.add(prefix.isEmpty() ? targetUrl : prefix + targetUrl);
        }
        return urls;
    }

    /** 缓存失效（测试/手动刷新用；原文没有显式失效入口，靠 TTL 到期）。 */
    public synchronized void invalidate() {
        checkCache = null;
        downloadCache = null;
    }
}
