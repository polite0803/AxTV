/*
 * 来源: src/app/com/github/mytv/p004dv/model/CameraInfo.java + CameraInfo$$serializer.java
 * 原职责: 直播的多机位信息（赛事/演唱会直播才有）
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param cameraIdStr 机位 id（{@code camera_id_str}）
 * @param title       机位名（{@code title}）
 * @param cameraType  机位类型（{@code camera_type}，接口可能返回字符串）
 * @param streamInfo  该机位的拉流地址（{@code stream_info}）；<b>可空</b>
 */
public record CameraInfo(
        @JsonKey("camera_id_str") String cameraIdStr,
        @JsonKey("title") String title,
        @JsonKey("camera_type") int cameraType,
        @JsonKey("stream_info") StreamUrl streamInfo) {

    public CameraInfo {
        cameraIdStr = cameraIdStr == null ? "" : cameraIdStr;
        title = title == null ? "" : title;
    }

    public CameraInfo() {
        this("", "", 0, null);
    }
}
