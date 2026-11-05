/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveCoreSdkData.java + LiveCoreSdkData$$serializer.java
 * 原职责: 直播 SDK 拉流数据的根节点（只有一层 pull_data）
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param pullData 拉流数据（{@code pull_data}）；<b>可空</b>
 */
public record LiveCoreSdkData(@JsonKey("pull_data") LiveCorePullData pullData) {

    public LiveCoreSdkData() {
        this(null);
    }
}
