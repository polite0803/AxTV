/*
 * 来源: n11.java 中所有 "ez0 / gz0 / h11 ..." 解析协程（kotlinx.serialization 的 Json.decodeFromString）
 * 原职责: 把响应字符串反序列化成 com.github.axtv.dv.model.* 数据类
 * 重构说明: 核心层不绑定 JSON 库，抽象成函数式接口；Android 侧实现可用 kotlinx.serialization
 *           （直接复用原 model 类及其 $$serializer），也可用 Gson/Moshi + 自定义适配器。
 */
package axtv.api;

public interface JsonCodec {

    /**
     * 反序列化。
     *
     * @param json 响应文本
     * @param type 目标类型（com.github.mytv.p004dv.model.* 下的数据类）
     */
    <T> T decode(String json, Class<T> type) throws Exception;
}
