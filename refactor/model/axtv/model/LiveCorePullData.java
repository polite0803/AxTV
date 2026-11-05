/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveCorePullData.java + LiveCorePullData$$serializer.java
 * 原职责: 直播 SDK 拉流数据本体：options（档位描述）+ stream_data（各档位地址）
 * 重构说明: 原字段类型是 kotlinx JsonElement（接口把这段下发成「JSON 字符串」或「JSON 对象」两种形态），
 *           核心层不绑定 JSON 库，用 Object 承载「已解析的 JSON 树」，由调用方 JsonCodec 负责解包。
 *           解包规则（原 DataModelKt.unwrapEmbeddedJsonStringOrNull）：若值是字符串且 trim 后以 { 或 [ 开头，
 *           则按 JSON 再解析一次；解析失败视为 null。
 */
package axtv.model;

/**
 * @param options    档位描述节点（{@code options}），可能嵌了一层 JSON 字符串；<b>可空</b>
 * @param streamData 各档位地址节点（{@code stream_data}），同样可能是字符串；<b>可空</b>
 */
public record LiveCorePullData(Object options, Object streamData) {

    public LiveCorePullData() {
        this(null, null);
    }
}
