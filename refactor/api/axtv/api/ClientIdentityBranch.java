/*
 * 用户设置：客户端身份分支。
 * 选中哪个分支，本次启动内所有请求的 user-agent、a_bogus 的 userAgent
 * 以及配套 client hints 全部由该分支决定（启动时快照，改设置后下次启动生效）。
 */
package axtv.api;

public enum ClientIdentityBranch {

    /** Web 接口族：Chrome 147 全套浏览器头。 */
    WEB("Web 接口", 0),
    /** Webcast：QQBrowser 那套身份，client hints 按该浏览器版本保留。 */
    WEBCAST("Webcast", 1),
    /** App Feed：抖音 App UA，不带任何桌面指纹头。 */
    APP_FEED("App Feed", 2);

    public final String displayName;
    public final int value;

    ClientIdentityBranch(String displayName, int value) {
        this.displayName = displayName;
        this.value = value;
    }

    /** 越界 / 脏值回落到默认分支 WEB。 */
    public static ClientIdentityBranch fromValue(int value) {
        for (ClientIdentityBranch branch : values()) {
            if (branch.value == value) {
                return branch;
            }
        }
        return WEB;
    }
}
