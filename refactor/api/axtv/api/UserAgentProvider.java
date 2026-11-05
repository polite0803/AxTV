/*
 * 来源: src/app/p000/n11.java:85（web 头表）、kz0.java:226（live UA）、n11.java:454-492（App UA）
 * 原职责: 三套客户端身份散落在常量与调用点里，UA 与请求头/签名容易脱节
 * 重构说明: 收敛为唯一取值点——请求头与 a_bogus 签名都只从这里取同一个值
 */
package axtv.api;

import java.util.Map;

/**
 * 客户端身份的唯一取值点。
 *
 * <p>一次 {@link DouyinApi} 实例对应一个启动期内快照的分支；
 * {@link #userAgent()} 的结果同时用于请求头与 {@link SignProvider#signABogus} 的 userAgent，
 * 不允许调用方自行拼接 UA。
 */
public final class UserAgentProvider {

    /** www.douyin.com 用的 UA（Chrome 147，原 n11.f14561n）。 */
    static final String WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/147.0.0.0 Safari/537.36";

    /** live.douyin.com 用的 UA（QQ 浏览器，原 kz0.java:226）。 */
    static final String WEBCAST_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.97 "
                    + "Safari/537.36 Core/1.116.567.400 QQBrowser/19.7.6764.400";

    private final ClientIdentityBranch branch;
    private final String userAgent;

    public UserAgentProvider(ClientIdentityBranch branch, DeviceProfile device) {
        this.branch = branch == null ? ClientIdentityBranch.WEB : branch;
        this.userAgent = resolve(this.branch, device);
    }

    public ClientIdentityBranch branch() {
        return branch;
    }

    /** 本次请求使用的 UA；请求头与签名实参必须都取此值。 */
    public String userAgent() {
        return userAgent;
    }

    /** 该分支的完整请求头（已含 user-agent）；业务头（referer/cookie/uifid 等）由调用方追加。 */
    public Map<String, String> headers() {
        return DouyinEndpoints.headersFor(branch, userAgent);
    }

    private static String resolve(ClientIdentityBranch branch, DeviceProfile device) {
        switch (branch) {
            case WEBCAST:
                return WEBCAST_USER_AGENT;
            case APP_FEED:
                return device.mobileUserAgent();
            default:
                return WEB_USER_AGENT;
        }
    }
}
