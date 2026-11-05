/*
 * 来源: src/app/p000/pj0.java
 * 原职责: 电视在局域网里的身份信息 —— 设备名（Build.BRAND + Build.MODEL）与首选 IP 地址
 * 重构说明: Android 的 Build 换成显式传入的 brand/model；IP 选择逻辑逐行保留：
 *           优先 wlan* / eth* 接口（避开 Wi-Fi Direct 的 p2p0），其次任意非环回 IPv4，最后 0.0.0.0。
 */
package axtv.panel;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 设备名与局域网地址解析。
 *
 * <p>版本日志里明确过：{@code p2p0}（Wi-Fi Direct）上拿到的地址手机连不上，
 * 所以选地址时优先 {@code wlan*} / {@code eth*}，其它接口只作兜底。
 */
public final class DeviceAddressResolver {

    /** 取不到地址时的返回值（原实现一致，手机端会看到 {@code http://0.0.0.0:9978/submit}）。 */
    public static final String NO_ADDRESS = "0.0.0.0";

    private static final Logger LOG = Logger.getLogger("CookieServer");

    /** 设备身份。Android 侧：{@code Build.BRAND} / {@code Build.MODEL}。 */
    public interface DeviceIdentity {
        String brand();

        String model();
    }

    private DeviceAddressResolver() {
    }

    /**
     * 设备展示名（原 {@code pj0.m9096a()}）：
     * 品牌名已被型号包含时只显示型号（如 "Xiaomi TV" 里的 "Xiaomi"），否则拼成 "品牌 型号"；
     * 型号为空时用 "Unknown Device"。
     */
    public static String deviceName(DeviceIdentity identity) {
        String model = identity == null ? null : identity.model();
        if (model == null || model.isBlank()) {
            model = "Unknown Device";
        }
        String brand = identity == null || identity.brand() == null ? "" : identity.brand();
        if (containsIgnoreCase(model, brand)) {
            return model;
        }
        return (brand + " " + model).trim();
    }

    /**
     * 首选局域网 IPv4（原 {@code pj0.m9097b()}）。
     *
     * <p>遍历顺序与原实现一致：遇到 up 且非环回、名字含 wlan/eth 的接口就直接返回其第一个合格地址；
     * 否则记住第一个合格地址，等遍历完再返回。
     */
    public static String localAddress() {
        try {
            List<NetworkInterface> interfaces = listInterfaces();
            String fallback = null;
            for (NetworkInterface networkInterface : interfaces) {
                try {
                    if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                        continue;
                    }
                    String name = networkInterface.getName();
                    boolean preferred = name != null
                            && (containsIgnoreCase(name, "wlan") || containsIgnoreCase(name, "eth"));
                    for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                        if (!(address instanceof Inet4Address ipv4)) {
                            continue;
                        }
                        if (ipv4.isLoopbackAddress() || ipv4.isLinkLocalAddress()) {
                            continue;
                        }
                        String hostAddress = ipv4.getHostAddress();
                        if (hostAddress == null) {
                            continue;
                        }
                        if (preferred) {
                            return hostAddress;
                        }
                        if (fallback == null) {
                            fallback = hostAddress;
                        }
                    }
                } catch (Exception ignored) {
                    // 单个接口异常不影响其它接口（原实现的 try 只包住最外层，此处更宽容）
                }
            }
            return fallback != null ? fallback : NO_ADDRESS;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "获取 IP 失败", e);
            return NO_ADDRESS;
        }
    }

    /** 会话地址：{@code http://<ip>:<port><path>}。 */
    public static String url(String host, int port, String path) {
        return PanelServer.baseUrl(host == null || host.isEmpty() ? NO_ADDRESS : host, port)
                + (path == null ? "" : path);
    }

    private static List<NetworkInterface> listInterfaces() throws Exception {
        var enumeration = NetworkInterface.getNetworkInterfaces();
        return enumeration == null ? List.of() : Collections.list(enumeration);
    }

    private static boolean containsIgnoreCase(String text, String needle) {
        return text != null && needle != null && text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
