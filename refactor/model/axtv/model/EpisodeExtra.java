/*
 * 来源: src/app/com/github/mytv/p004dv/model/EpisodeExtra.java + EpisodeExtra$$serializer.java
 * 原职责: 剧集类直播的附加信息（多机位表 + 默认机位）
 * 重构说明: 数据类 → record
 */
package axtv.model;

import java.util.List;

/**
 * @param cameraInfos            机位列表（{@code camera_infos}）
 * @param defaultCameraIdStr     默认机位 id（{@code default_camera_id_str}）
 * @param cameraInfosTableTitle  机位表标题（{@code camera_infos_table_title}）
 */
public record EpisodeExtra(
        @JsonKey("camera_infos") List<CameraInfo> cameraInfos,
        @JsonKey("default_camera_id_str") String defaultCameraIdStr,
        @JsonKey("camera_infos_table_title") String cameraInfosTableTitle) {

    public EpisodeExtra {
        cameraInfos = cameraInfos == null ? null : List.copyOf(cameraInfos);
        defaultCameraIdStr = defaultCameraIdStr == null ? "" : defaultCameraIdStr;
        cameraInfosTableTitle = cameraInfosTableTitle == null ? "" : cameraInfosTableTitle;
    }

    public EpisodeExtra() {
        this(null, "", "");
    }
}
