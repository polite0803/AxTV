/*
 * 来源: 新建（电视台连通性标注，无原版对应）。
 * 原职责: 无。
 * 重构说明: 单个候选播放地址的活性记录。三态 UNKNOWN/OK/DEAD 严格分开（PLAN R1）：
 *           没探测过、探测失败但次数不够，都仍是 UNKNOWN，绝不当作 DEAD 隐藏，
 *           否则首装时整张列表会被判死而变空。判死只看连续失败次数（P4=3）。
 *           质量档只在 OK 时有意义；OK 之后出现失败先降为 POOR 而不是立刻判死。
 */
package axtv.iptv;

import axtv.panel.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** 一个候选 URL 的活性与质量。不可变；每次探测/播放反馈产出新记录。 */
public record LivenessRecord(
    String url,
    State state,
    QualityPolicy.Quality quality,
    int consecutiveFailures,
    long lastProbeAtEpochMs,
    long elapsedMs,
    String lastError
) {

    public enum State {
        /** 从未探测成功过，也没攒够判死次数。不可隐藏。 */
        UNKNOWN,
        /** 最近一次探测/播放成功。 */
        OK,
        /** 连续失败达到 {@link QualityPolicy#DEAD_FAILURE_THRESHOLD}。可隐藏。 */
        DEAD
    }

    /** UI 用的单一档位：把「未探测」与「已判死」也各占一格，不与质量三档混排（PLAN R2）。 */
    public enum Tier {
        UNKNOWN, GOOD, FAIR, POOR, DEAD
    }

    public LivenessRecord {
        url = url == null ? "" : url;
        state = state == null ? State.UNKNOWN : state;
        quality = quality == null ? QualityPolicy.Quality.POOR : quality;
        consecutiveFailures = Math.max(0, consecutiveFailures);
        lastError = lastError == null ? "" : lastError;
    }

    /** 从未探测过的地址。 */
    public static LivenessRecord unknown(String url) {
        return new LivenessRecord(url, State.UNKNOWN, QualityPolicy.Quality.POOR, 0, 0L, 0L, "");
    }

    /** 探测/播放成功：清零失败计数，按耗时定档。 */
    public LivenessRecord withSuccess(long elapsedMs, long atEpochMs, int timeoutSeconds) {
        return new LivenessRecord(
            this.url, State.OK, QualityPolicy.classify(elapsedMs, timeoutSeconds),
            0, atEpochMs, Math.max(0L, elapsedMs), ""
        );
    }

    /**
     * 探测/播放失败：累加连续失败。
     * 未到判死阈值时保留原状态——原本 OK 的降为 POOR（仍可见、仍会优先尝试），
     * 原本 UNKNOWN 的仍是 UNKNOWN（R1：不隐藏未确证死亡的地址）。
     */
    public LivenessRecord withFailure(long atEpochMs, String error) {
        int failures = this.consecutiveFailures + 1;
        State next = failures >= QualityPolicy.DEAD_FAILURE_THRESHOLD ? State.DEAD : this.state;
        QualityPolicy.Quality nextQuality = next == State.OK ? QualityPolicy.Quality.POOR : this.quality;
        return new LivenessRecord(
            this.url, next, nextQuality, failures, atEpochMs, this.elapsedMs,
            error == null ? "" : error
        );
    }

    /** 当前档位。UNKNOWN 与 DEAD 各自独立，不被质量档覆盖。 */
    public Tier tier() {
        return switch (this.state) {
            case UNKNOWN -> Tier.UNKNOWN;
            case DEAD -> Tier.DEAD;
            case OK -> switch (this.quality) {
                case GOOD -> Tier.GOOD;
                case FAIR -> Tier.FAIR;
                case POOR -> Tier.POOR;
            };
        };
    }

    /** 只有确证死亡才允许隐藏。 */
    public boolean isHidden() {
        return this.state == State.DEAD;
    }

    /** 序列化为扁平 JSON 对象（键序固定，保证 write→parse→write 逐字节一致）。 */
    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("url", this.url);
        map.put("state", this.state.name());
        map.put("quality", this.quality.name());
        map.put("failures", (long) this.consecutiveFailures);
        map.put("probedAt", this.lastProbeAtEpochMs);
        map.put("elapsedMs", this.elapsedMs);
        map.put("error", this.lastError);
        return Json.write(map);
    }

    /** 解析。JSON 损坏退回未探测状态（不抛异常，避免整页加载失败）。 */
    public static LivenessRecord fromJson(String text) {
        if (text == null || text.isBlank()) {
            return unknown("");
        }
        Map<String, Object> raw;
        try {
            raw = Json.parseObject(text);
        } catch (IllegalArgumentException e) {
            return unknown("");
        }
        return fromMap(raw);
    }

    public static LivenessRecord fromMap(Map<String, Object> map) {
        String url = Json.optString(map, "url");
        return new LivenessRecord(
            url,
            parseState(Json.optString(map, "state")),
            parseQuality(Json.optString(map, "quality")),
            (int) Json.optLong(map, "failures", 0L),
            Json.optLong(map, "probedAt", 0L),
            Json.optLong(map, "elapsedMs", 0L),
            Json.optString(map, "error")
        );
    }

    private static State parseState(String name) {
        for (State s : State.values()) {
            if (s.name().equals(name)) {
                return s;
            }
        }
        return State.UNKNOWN;
    }

    private static QualityPolicy.Quality parseQuality(String name) {
        for (QualityPolicy.Quality q : QualityPolicy.Quality.values()) {
            if (q.name().equals(name)) {
                return q;
            }
        }
        return QualityPolicy.Quality.POOR;
    }
}