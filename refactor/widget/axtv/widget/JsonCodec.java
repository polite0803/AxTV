/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetStore.java:38 (json = sp0.m10310d(...))
 *       + 5 个 *$$serializer.java (kotlinx.serialization KSerializer)
 * 原职责: 用 kotlinx.serialization 的 Json{...} 把 Snapshot 编码成 JSON 字符串存进 SharedPreferences，
 *         读取时 decodeFromString；解析失败返回默认快照
 * 重构说明: 核心层不绑具体 JSON 库，用接口隔离。Android 侧实现须用 kotlinx.serialization 的
 *           VideoListSnapshot/LiveSnapshot/MixSnapshot/SearchSnapshot 对应 serializer，
 *           并按 SERIAL_NAME 精确匹配；配置位见 NOTES.md 存疑清单。
 */
package axtv.widget;

/** JSON 编解码抽象。Android 侧用 kotlinx.serialization 实现。 */
public interface JsonCodec {

    /** 把快照/模型编码为 JSON 字符串。 */
    String encode(Object value);

    /**
     * 把 JSON 字符串解码为指定类型；解析失败抛异常（由 WidgetStore 兜底为默认快照）。
     *
     * @param json     原始字符串
     * @param type     目标类型（Snapshot 之一）
     * @param <T>      目标类型
     */
    <T> T decode(String json, Class<T> type) throws Exception;
}
