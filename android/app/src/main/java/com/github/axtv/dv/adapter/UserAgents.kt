/*
 * 写死的 User-Agent 常量。
 *
 * 只收「不依赖设备 WebView 版本」的场景：需要按设备引擎版本伪装的地方
 * （扫码 WebView、精选页 WebFetch）各自从 WebSettings.getDefaultUserAgent 推导，不放这里。
 * 同一份桌面/安卓 UA 原先散在播放器与小组件两处，改一处漏一处的风险由此收口。
 */
package com.github.axtv.dv.adapter

internal object UserAgents {

    /** 安卓 Chrome/120：IPTV 直播源用的中性请求头（不带抖音 Referer，避免反盗链误拒）。 */
    const val ANDROID_CHROME120 =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** 桌面 Chrome/120：后台图片抓取（桌面小组件封面）用，部分 CDN 只认桌面 UA。 */
    const val DESKTOP_CHROME120 =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}
