/*
 * 来源: src/app/com/github/mytv/p004dv/model/ 下 45 个 *$$serializer.java 中 ip3.m5695k("...") 注册的
 *       SerialDescriptor 元素名 + @c72(names = {...}) 的 JsonNames 备用名
 * 原职责: kotlinx.serialization 用 SerialName/JsonNames 把 Kotlin 属性名映射到抖音接口的 JSON key
 * 重构说明: 核心层不依赖 kotlinx.serialization，用一个纯标记注解承载同样的映射信息。
 *           反编译产物里只有 descriptor 保留了真实 key，字段名本身（jadx 还原的 Kotlin 属性名）
 *           是驼峰，因此本注解是字段 ↔ JSON 的唯一权威来源。
 */
package axtv.model;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 record 分量对应的 JSON key。
 *
 * <p>{@link #value()} 是主键名（反编译 descriptor 里的 {@code SerialName}），
 * {@link #alternates()} 是同一字段可接受的备用键名（反编译里的 {@code JsonNames}）。
 * 解析实现应「先按主键，命中失败再依次尝试备用键」。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD})
public @interface JsonKey {

    /** 主 JSON key。 */
    String value();

    /** 备用 JSON key（抖音不同接口版本混用多个命名）。 */
    String[] alternates() default {};
}
