/*
 * 来源: src/app/com/github/mytv/p004dv/model/VideoPlayUrlResolver.java
 *       + src/app/com/github/mytv/p004dv/model/VideoPlayUrlResolverKt.java（顶层函数转发壳）
 *       + p000.lq5（目标清晰度枚举）/ p000.cj5（编码偏好枚举）
 * 原职责: 从 Video/Aweme 的多码率、H.264/H.265 主地址中挑出一条最终可播 URL。
 *         这是「能不能播」的唯一决策点：先按编码偏好过滤 → 按清晰度档位匹配 → 再按签名规则挑 URL。
 * 重构说明: 去掉 Android 依赖（MediaCodecList / SharedPreferences），改为两个接口：
 *           {@link Settings}（清晰度与编码偏好的来源）与 {@link DecoderCapabilities}（设备解码上限）。
 *           lq5/cj5 两个混淆枚举还原为 {@link TargetResolution} / {@link CodecPreference}，
 *           保留其原有 ordinal 语义（选档位下标、switch 分支都依赖顺序）。
 */
package axtv.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 播放地址选择器（无状态，可单例复用）。
 *
 * <p>选择流程（与反编译实现一一对应）：
 * <ol>
 *   <li><b>编码过滤</b>：先剔除设备解码能力之外的码率，再按编码偏好（自动 / 只要 H.264 / 只要 H.265）过滤</li>
 *   <li><b>清晰度过滤</b>：自动 → 直接用主地址；最高 → 取码率列表里画质最高的一条；
 *       1080p/720p/540p → 在 gear_name 里找包含对应数字的第一条</li>
 *   <li><b>URL 挑选</b>：在候选地址里优先「已签名」的直链，其次 play 接口地址，最后兜底</li>
 * </ol>
 */
public final class VideoPlayUrlResolver {

    /** 单例，与原 {@code INSTANCE} 对应。 */
    public static final VideoPlayUrlResolver INSTANCE = new VideoPlayUrlResolver();

    /** 已签名 CDN 路径的特征：douyinvod.com/{32 hex}/{8 hex}/ */
    private static final Pattern SIGNED_PATH =
            Pattern.compile("douyinvod\\.com/[0-9a-f]{32}/[0-9a-f]{8}/");

    private static final String CDN_HOST = "douyinvod.com";
    private static final String PLAY_API_PATH = "douyin.com/aweme/v1/play";

    private volatile Settings settings;
    private volatile DecoderCapabilities decoderCapabilities;

    private VideoPlayUrlResolver() {
    }

    // ---------------------------------------------------------------- 枚举

    /**
     * 目标清晰度，对应反编译的 {@code p000.lq5}。
     *
     * <p>ordinal 顺序是语义的一部分：{@link #selectQualityIndex(int)} 依赖它，
     * 所以不能重排。
     */
    public enum TargetResolution {
        /** 自动：不按码率挑，直接用 play_addr（H.265 偏好时优先 play_addr_265）。 */
        AUTO("自动", "auto", "自动"),
        /** 最高画质：取码率列表里画质最高的一条。 */
        HIGHEST("最高画质", "highest", "最高"),
        /** 高清 1080p：gear_name 含 "1080"。 */
        P1080("高清 1080p", "1080p", "1080P"),
        /** 标清 720p：gear_name 含 "720"。 */
        P720("标清 720p", "720p", "720P"),
        /** 流畅 540p：gear_name 含 "540"。 */
        P540("流畅 540p", "480p", "540P");

        private final String label;
        private final String prefValue;
        private final String shortLabel;

        TargetResolution(String label, String prefValue, String shortLabel) {
            this.label = label;
            this.prefValue = prefValue;
            this.shortLabel = shortLabel;
        }

        /** 下拉列表里显示的全名，如「高清 1080p」。 */
        public String label() {
            return label;
        }

        /** 写进 SharedPreferences {@code video_resolution} 的值，如 {@code 1080p}。 */
        public String prefValue() {
            return prefValue;
        }

        /** 播放器浮层上的短名，如「1080P」。 */
        public String shortLabel() {
            return shortLabel;
        }

        /** 按偏好字符串（{@code video_resolution}）反查，找不到返回 null。 */
        public static TargetResolution fromPrefValue(String value) {
            if (value == null) {
                return null;
            }
            for (TargetResolution r : values()) {
                if (r.prefValue.equals(value)) {
                    return r;
                }
            }
            return null;
        }
    }

    /** 编码偏好，对应反编译的 {@code p000.cj5}。ordinal 0/1/2 = 自动/H.264/H.265。 */
    public enum CodecPreference {
        AUTO("auto"),
        H264("h264"),
        H265("h265");

        private final String value;

        CodecPreference(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static CodecPreference fromValue(String value) {
            if (value == null) {
                return AUTO;
            }
            for (CodecPreference c : values()) {
                if (c.value.equalsIgnoreCase(value)) {
                    return c;
                }
            }
            return AUTO;
        }
    }

    // ---------------------------------------------------------------- 依赖注入

    /**
     * 配置来源（原 {@code p000.fl4} SettingsManager 的两个 StateFlow）。
     *
     * <p>Android 侧实现：{@code targetResolution()} 读设置项的运行时值（可能为 null），
     * {@code rawResolutionPreference()} 直接读 SharedPreferences 的
     * {@code video_resolution}（默认 {@code "auto"}）。
     */
    public interface Settings {

        /** 用户选择的清晰度；null 表示尚未初始化，调用方会退回原始偏好字符串。 */
        TargetResolution targetResolution();

        /** 用户选择的编码偏好；null 视为 {@link CodecPreference#AUTO}。 */
        CodecPreference codecPreference();

        /** SharedPreferences {@code video_resolution} 的原值，缺省 {@code "auto"}。 */
        String rawResolutionPreference();
    }

    /**
     * 设备硬解能力上限（原 {@code queryDecoderMaxDims()} 通过 MediaCodecList 查询）。
     *
     * <p>Android 侧实现：遍历 {@code MediaCodecList(REGULAR_CODECS)}，跳过 encoder，
     * 对 {@code video/hevc} 与 {@code video/avc} 分别取所有支持该 MIME 的解码器
     * {@code VideoCapabilities} 的 width/height upper bound，按「宽×高」取最大的一组。
     * 查不到时返回 0，表示「不限制」。
     */
    public interface DecoderCapabilities {

        /** 指定编码下解码器支持的最大宽度；0 或负数表示未知。 */
        int maxWidth(boolean hevc);

        /** 指定编码下解码器支持的最大高度；0 或负数表示未知。 */
        int maxHeight(boolean hevc);
    }

    /** 初始化（原 {@code init(Context)}）：注入配置与解码能力，可重复调用。 */
    public synchronized void init(Settings settings, DecoderCapabilities decoderCapabilities) {
        if (settings != null) {
            this.settings = settings;
        }
        if (decoderCapabilities != null) {
            this.decoderCapabilities = decoderCapabilities;
        }
    }

    // ---------------------------------------------------------------- 公开入口

    /**
     * 原 {@code VideoPlayUrlResolverKt.resolveFinalPlayUrl(video, target, aweme)}。
     *
     * <p>aweme 非空时会用 aweme 自带的 video 覆盖传入的 video（更贴近当前作品），
     * 但若 aweme.video 的地址或码率列表为空，则逐项回退到入参 video 的值。
     */
    public String resolveFinalPlayUrl(Video video, TargetResolution target, Aweme aweme) {
        if (aweme == null) {
            return resolveFinalPlayUrl(video, target);
        }
        Video awemeVideo = aweme.video();

        PlayAddr playAddr = awemeVideo.playAddr();
        if (!playAddr.hasUrls()) {
            playAddr = video.playAddr();
        }
        PlayAddr playAddr265 = awemeVideo.playAddr265();
        if (!playAddr265.hasUrls()) {
            playAddr265 = video.playAddr265();
        }
        Video source = video != null ? (awemeVideo.bitRate().isEmpty() ? video : awemeVideo) : awemeVideo;
        return selectBestPlayUrl(
                selectCandidateUrls(playAddr, playAddr265, source.bitRate(), resolveTargetResolution(target), false));
    }

    // ---------------------------------------------------------------- Aweme 直达入口

    /**
     * 从 {@link Aweme} 直接取可播 URL（清晰度按设置项解析，等价于 target=null）。
     *
     * <p>地址来源是作品自带的 {@code video.play_addr} / {@code play_addr_265} /
     * {@code bit_rate[].play_addr.url_list}，不额外请求接口。挑出的 URL 可能是：
     * <ul>
     *   <li>{@code douyinvod.com/{32hex}/{8hex}/...} 已签名 CDN 直链（首选）</li>
     *   <li>{@code douyin.com/aweme/v1/play/?video_id=...} 播放接口地址（需 302）</li>
     *   <li>带 {@code signature=} 的 CDN 地址</li>
     *   <li>以上都不命中时的最后一条兜底地址</li>
     * </ul>
     * 无可用地址（作品为空 / 没有 url_list）时返回空串，由播放器决定如何提示。
     */
    public String playableUrl(Aweme aweme) {
        return playableUrl(aweme, null);
    }

    /** 同 {@link #playableUrl(Aweme)}，但显式指定清晰度档位。 */
    public String playableUrl(Aweme aweme, TargetResolution target) {
        if (aweme == null) {
            return "";
        }
        Video video = aweme.video();
        if (video == null) {
            return "";
        }
        return resolveFinalPlayUrl(video, target, aweme);
    }

    /** 原 {@code resolveFinalPlayUrl(Video, lq5)}（无 aweme 版本）。 */
    public String resolveFinalPlayUrl(Video video, TargetResolution target) {
        return selectBestPlayUrl(selectCandidateUrls(
                video.playAddr(), video.playAddr265(), video.bitRate(), resolveTargetResolution(target), false));
    }

    /**
     * 强制 H.264：给硬解只能吃 H.264 的设备/播放器用。
     * 与原 {@code resolveH264PlayUrl} 一致 —— 编码偏好被强制为 H.264，其余流程不变。
     */
    public String resolveH264PlayUrl(Video video, TargetResolution target) {
        return selectBestPlayUrl(selectCandidateUrls(
                video.playAddr(), video.playAddr265(), video.bitRate(), resolveTargetResolution(target), true));
    }

    // ---------------------------------------------------------------- 目标清晰度

    /**
     * 原 {@code resolveTargetResolution(lq5)}。取值优先级：
     * <ol>
     *   <li>入参</li>
     *   <li>设置项运行时值（{@link Settings#targetResolution()}）</li>
     *   <li>SharedPreferences 原始字符串（{@code video_resolution}，默认 {@code "auto"}），
     *       在枚举里按 prefValue 匹配，匹配不到用 {@link TargetResolution#AUTO}</li>
     * </ol>
     */
    public TargetResolution resolveTargetResolution(TargetResolution target) {
        if (target != null) {
            return target;
        }
        Settings s = settings;
        if (s != null) {
            TargetResolution fromState = s.targetResolution();
            if (fromState != null) {
                return fromState;
            }
            String raw = s.rawResolutionPreference();
            TargetResolution parsed = TargetResolution.fromPrefValue(raw == null ? "auto" : raw);
            if (parsed != null) {
                return parsed;
            }
        }
        return TargetResolution.AUTO;
    }

    // ---------------------------------------------------------------- 候选地址

    /**
     * 原 {@code selectCandidateUrls(playAddr, playAddr265, bitRates, target, forceH264)}。
     *
     * @param playAddr       H.264 主地址（兜底）
     * @param playAddr265    H.265 主地址（自动清晰度 + H.265 偏好时优先）
     * @param bitRates       码率列表
     * @param target         目标清晰度
     * @param forceH264      是否强制 H.264（resolveH264PlayUrl 传 true）
     */
    public List<String> selectCandidateUrls(PlayAddr playAddr, PlayAddr playAddr265,
                                            List<BitRate> bitRates, TargetResolution target,
                                            boolean forceH264) {
        CodecPreference codec = forceH264 ? CodecPreference.H264 : codecPreference();
        List<BitRate> list = bitRates == null ? List.of() : bitRates;

        // 1) 设备能解的码率
        List<BitRate> decoderFits = new ArrayList<>();
        for (BitRate br : list) {
            if (fitsDeviceDecoder(br)) {
                decoderFits.add(br);
            }
        }

        // 2) 按编码偏好过滤
        List<BitRate> byCodec;
        switch (codec) {
            case H264 -> {
                byCodec = new ArrayList<>();
                for (BitRate br : decoderFits) {
                    if (br.isH265() != 1) {
                        byCodec.add(br);
                    }
                }
            }
            case H265 -> {
                byCodec = new ArrayList<>();
                for (BitRate br : decoderFits) {
                    if (br.isH265() == 1) {
                        byCodec.add(br);
                    }
                }
            }
            default -> byCodec = decoderFits;
        }
        // 强制 H.264 时，「设备能解」这一档也收窄成 H.264，避免后续兜底拿到 HEVC
        if (forceH264) {
            decoderFits = byCodec;
        }

        switch (target) {
            case AUTO -> {
                // 自动：完全不看 bit_rate，直接用主地址
                if (codec != CodecPreference.H265) {
                    return playAddr.urlList();
                }
                if (!fitsDeviceDecoder(playAddr265.width(), playAddr265.height(), true)) {
                    return playAddr.urlList();
                }
                return playAddr265.urlList().isEmpty() ? playAddr.urlList() : playAddr265.urlList();
            }
            case HIGHEST -> {
                BitRate best = DataModels.highestQualityBitRate(byCodec);
                if (best != null && best.hasUrls()) {
                    return best.playAddr().urlList();
                }
                best = DataModels.highestQualityBitRate(decoderFits);
                if (best != null && best.hasUrls()) {
                    return best.playAddr().urlList();
                }
                return playAddr.urlList();
            }
            default -> {
                String gear = switch (target) {
                    case P1080 -> "1080";
                    case P720 -> "720";
                    case P540 -> "540";
                    default -> ""; // 不可达：AUTO/HIGHEST 已在上面分支处理
                };
                List<String> urls = firstBitRateUrlsByGear(byCodec, gear);
                if (urls != null) {
                    return urls;
                }
                urls = firstBitRateUrlsByGear(decoderFits, gear);
                if (urls != null) {
                    return urls;
                }
                return playAddr.urlList();
            }
        }
    }

    /** 当前设置里的编码偏好；未注入 Settings 时按 AUTO。 */
    private CodecPreference codecPreference() {
        Settings s = settings;
        if (s == null) {
            return CodecPreference.AUTO;
        }
        CodecPreference c = s.codecPreference();
        return c == null ? CodecPreference.AUTO : c;
    }

    /**
     * 原 {@code selectCandidateUrls$lambda$6}：取**第一条** gear_name 含指定数字的码率。
     *
     * <p>注意与直觉不同的地方：只检查第一条命中的码率，若它的 url_list 为空就直接返回 null，
     * 不会继续往后找。这是原实现的行为，必须保留（否则会选到与原 App 不同的档位）。
     */
    private static List<String> firstBitRateUrlsByGear(List<BitRate> list, String gear) {
        for (BitRate br : list) {
            String name = br.gearName() == null ? "" : br.gearName();
            if (name.toLowerCase(Locale.ROOT).contains(gear.toLowerCase(Locale.ROOT))) {
                return br.playAddr().urlList().isEmpty() ? null : br.playAddr().urlList();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 最终 URL

    /**
     * 原 {@code selectBestPlayUrl$app(List<String>)}：四级优先级。
     *
     * <ol>
     *   <li>已签名直链：含 {@code douyinvod.com} 且路径匹配 {@code /32hex/8hex/}</li>
     *   <li>播放接口：含 {@code douyin.com/aweme/v1/play}（需要二次 302，最稳定但多一跳）</li>
     *   <li>带 {@code signature=} 且含 {@code douyinvod.com}</li>
     *   <li>兜底：列表最后一条（原 {@code lastOrNull}）</li>
     * </ol>
     * 全部不命中返回空串。
     */
    public String selectBestPlayUrl(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return "";
        }
        String signed = null;
        for (String url : urls) {
            if (url != null && isPathSigned(url)) {
                signed = url;
                break;
            }
        }
        if (signed != null) {
            return signed;
        }
        String playApi = null;
        for (String url : urls) {
            if (url != null && url.contains(PLAY_API_PATH)) {
                playApi = url;
                break;
            }
        }
        if (playApi != null) {
            return playApi;
        }
        String withSignature = null;
        for (String url : urls) {
            if (url != null && url.contains("signature=") && url.contains(CDN_HOST)) {
                withSignature = url;
                break;
            }
        }
        if (withSignature != null) {
            return withSignature;
        }
        return urls.get(urls.size() - 1);
    }

    /**
     * 原 {@code isPathSigned}：判断是否「已经带路径签名」的 CDN 直链。
     * 判据 = 含 {@code douyinvod.com} 且路径形如 {@code /{32 位 hex}/{8 位 hex}/}。
     */
    public boolean isPathSigned(String url) {
        if (url == null || !url.contains(CDN_HOST)) {
            return false;
        }
        return SIGNED_PATH.matcher(url).find();
    }

    // ---------------------------------------------------------------- 解码能力

    /** 原 {@code fitsDeviceDecoder(BitRate)}：按该码率 PlayAddr 上的宽高与编码判断。 */
    public boolean fitsDeviceDecoder(BitRate bitRate) {
        PlayAddr addr = bitRate.playAddr();
        return fitsDeviceDecoder(addr.width(), addr.height(), bitRate.isHevc());
    }

    /**
     * 原 {@code fitsDeviceDecoder$app(w, h, hevc)}。
     *
     * <p>宽高未知（<= 0）或查不到解码能力上限时一律放行 —— 宁可选一个可能播不了的档位，
     * 也不要在查询失败时把列表筛空。
     */
    public boolean fitsDeviceDecoder(int width, int height, boolean hevc) {
        if (width <= 0 || height <= 0) {
            return true;
        }
        DecoderCapabilities caps = decoderCapabilities;
        if (caps == null) {
            return true;
        }
        int maxW = caps.maxWidth(hevc);
        int maxH = caps.maxHeight(hevc);
        if (maxW <= 0 || maxH <= 0) {
            return true;
        }
        return width <= maxW && height <= maxH;
    }
}
