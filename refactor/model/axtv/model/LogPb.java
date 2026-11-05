/*
 * 来源: src/app/com/github/mytv/p004dv/model/LogPb.java + LogPb$$serializer.java
 * 原职责: 接口的日志埋点标识，请求日志链路时需原样带回
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param imprId 曝光 id（{@code impr_id}）
 */
public record LogPb(@JsonKey("impr_id") String imprId) {

    public LogPb {
        imprId = imprId == null ? "" : imprId;
    }

    public LogPb() {
        this("");
    }
}
