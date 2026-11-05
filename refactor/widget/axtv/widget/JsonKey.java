/*
 * 来源: src/app/com/github/mytv/p004dv/widget/ 下 5 个 *$$serializer.java 中 ip3.m5695k("...") 注册的
 *       SerialDescriptor 元素名（另有 4 个 Snapshot 的 descriptor 在 write$Self$app 里按 index 暴露）
 * 原职责: kotlinx.serialization 用 SerialName 把 Kotlin 属性名映射到持久化 JSON key
 * 重构说明: 核心层不依赖 kotlinx.serialization，用纯标记注解承载字段 ↔ JSON key 映射。
 *           本模块单独编译，不复用 axtv.model.JsonKey。
 */
package axtv.widget;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明 record 分量 / 字段对应的持久化 JSON key（来自反编译 descriptor）。 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD})
public @interface JsonKey {
    /** 主 JSON key。 */
    String value();
}
