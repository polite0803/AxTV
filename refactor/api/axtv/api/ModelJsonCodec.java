/*
 * 来源: src/app/p000/n11.java 的 8 类解析协程（ez0 / gz0 / h11 ... 的 Json.decodeFromString）
 *       + src/app/com/github/mytv/p004dv/model/*$$serializer.java
 * 原职责: 把抖音响应文本反序列化成 com.github.mytv.p004dv.model.* 数据类
 * 重构说明: 核心层不引入 JSON 库，本类提供 JsonCodec 的默认实现：
 *           1) 用 axtv.panel.Json 把文本解析成纯 JDK 的 JSON 树（Map/List/String/Long/Double/Boolean/null）；
 *           2) 按 @JsonKey（含 alternates）把树绑定到 axtv.model 的 record；
 *           3) 标量统一走 FlexibleJson，保持原 Kotlin 序列化器「字符串/数字/布尔混发」的宽松语义；
 *           4) 通过 record 的规范构造器实例化，保留 compact constructor 里的 null 归一化默认值。
 *           不依赖 kotlinx.serialization / Gson / Moshi，minSdk 23 无需额外 desugaring。
 * 注意: 反射绑定依赖 axtv.model 的字段名、@JsonKey 与字段声明顺序，
 *       release 必须在 proguard-rules.pro 中保留 axtv.model.**（见 android/app/proguard-rules.pro）。
 */
package axtv.api;

import axtv.model.FlexibleJson;
import axtv.model.JsonKey;
import axtv.panel.Json;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 {@code @JsonKey} 的 record 解码器。
 *
 * <p>解析失败（非法 JSON、根节点不是对象、字段类型不匹配、构造器抛错）统一抛
 * {@link IllegalArgumentException}；由 {@code DouyinApi.callFor} 转成 {@code ApiException.DECODE}，
 * 与原实现「解析异常向上抛」的行为一致，不返回 null 伪装成功。
 */
public final class ModelJsonCodec implements JsonCodec {

    /** 字段在 JSON 中缺席的哨兵（与「存在但值为 null」区分开）。 */
    private static final Object MISSING = new Object();

    /** 每个类型的绑定元数据只构建一次（反射开销一次性，后续走数组与构造器）。 */
    private static final Map<Class<?>, Shape> SHAPES = new ConcurrentHashMap<>();

    @Override
    public <T> T decode(String json, Class<T> type) throws Exception {
        if (type == null) {
            throw new IllegalArgumentException("type 不能为 null");
        }
        Object tree = Json.parse(json);
        return type.cast(bind(tree, type));
    }

    // ------------------------------------------------------------------ 绑定

    private static Object bind(Object value, Type type) {
        Class<?> raw = rawClass(type);
        if (value == null) {
            return null;
        }
        if (raw == Object.class) {
            return unwrapEmbedded(value);
        }
        if (raw == String.class) {
            // 标量转 token 文本（数字/布尔按字面量）；对象/数组返回 null，交给字段默认值。
            return FlexibleJson.rawText(value);
        }
        if (raw == int.class || raw == Integer.class) {
            return Integer.valueOf(FlexibleJson.toInt(value));
        }
        if (raw == long.class || raw == Long.class) {
            return Long.valueOf(FlexibleJson.toLong(value));
        }
        if (raw == boolean.class || raw == Boolean.class) {
            return Boolean.valueOf(FlexibleJson.toBoolean(value));
        }
        if (raw == double.class || raw == Double.class) {
            return toDouble(value);
        }
        if (raw == float.class || raw == Float.class) {
            Double d = toDouble(value);
            return d == null ? null : Float.valueOf(d.floatValue());
        }
        if (raw == short.class || raw == Short.class) {
            return Short.valueOf((short) FlexibleJson.toInt(value));
        }
        if (raw == byte.class || raw == Byte.class) {
            return Byte.valueOf((byte) FlexibleJson.toInt(value));
        }
        if (List.class.isAssignableFrom(raw)) {
            return bindList(value, type);
        }
        if (Map.class.isAssignableFrom(raw)) {
            return bindMap(value, type);
        }
        if (raw.isArray()) {
            return bindArray(value, raw.getComponentType());
        }
        return bindRecord(value, raw);
    }

    private static List<Object> bindList(Object value, Type type) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("期望 JSON 数组，实际为 " + kindOf(value));
        }
        Type elementType = typeArgument(type, 0);
        List<Object> out = new ArrayList<>(list.size());
        for (Object item : list) {
            out.add(bind(item, elementType));
        }
        return out;
    }

    private static Map<Object, Object> bindMap(Object value, Type type) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("期望 JSON 对象，实际为 " + kindOf(value));
        }
        Type keyType = typeArgument(type, 0);
        Type valueType = typeArgument(type, 1);
        Map<Object, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            out.put(bindKey(entry.getKey(), keyType), bind(entry.getValue(), valueType));
        }
        return out;
    }

    private static Object bindKey(Object key, Type type) {
        Class<?> raw = rawClass(type);
        if (raw == String.class || raw == Object.class) {
            return String.valueOf(key);
        }
        if (raw == Integer.class || raw == int.class) {
            return Integer.valueOf(FlexibleJson.toInt(String.valueOf(key)));
        }
        if (raw == Long.class || raw == long.class) {
            return Long.valueOf(FlexibleJson.toLong(String.valueOf(key)));
        }
        return String.valueOf(key);
    }

    private static Object[] bindArray(Object value, Class<?> componentType) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("期望 JSON 数组，实际为 " + kindOf(value));
        }
        Object[] out = new Object[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object item = bind(list.get(i), componentType);
            out[i] = item == null ? primitiveDefault(componentType) : item;
        }
        return out;
    }

    private static Object bindRecord(Object value, Class<?> type) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(
                    "期望 JSON 对象（" + type.getName() + "），实际为 " + kindOf(value));
        }
        Shape shape = shape(type);
        Object[] args = new Object[shape.components.length];
        for (int i = 0; i < shape.components.length; i++) {
            Component component = shape.components[i];
            Object raw = lookup(map, component);
            Object decoded = raw == MISSING ? null : bind(raw, component.type);
            if (decoded == null && component.primitive) {
                decoded = primitiveDefault(component.field.getType());
            }
            args[i] = decoded;
        }
        try {
            return shape.constructor.newInstance(args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalArgumentException("构造 " + type.getName() + " 失败", e);
        }
    }

    // ------------------------------------------------------------------ 类型元数据

    private static Shape shape(Class<?> type) {
        Shape cached = SHAPES.get(type);
        if (cached != null) {
            return cached;
        }
        Shape built = buildShape(type);
        Shape existing = SHAPES.putIfAbsent(type, built);
        return existing == null ? built : existing;
    }

    private static Shape buildShape(Class<?> type) {
        Field[] declared = type.getDeclaredFields();
        List<Field> fields = new ArrayList<>(declared.length);
        for (Field field : declared) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            fields.add(field);
        }
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("类型没有可绑定字段: " + type.getName());
        }
        Map<String, Field> byName = new LinkedHashMap<>();
        for (Field field : fields) {
            byName.put(field.getName(), field);
        }

        // JVM 上 record 完整：RecordComponent 就是规范构造器的参数顺序。
        List<String> order = recordComponentNames(type);
        if (order != null) {
            Component[] components = new Component[order.size()];
            Class<?>[] paramTypes = new Class<?>[order.size()];
            for (int i = 0; i < order.size(); i++) {
                Field field = byName.get(order.get(i));
                if (field == null) {
                    throw new IllegalArgumentException(
                            "record 分量无同名字段: " + type.getName() + "." + order.get(i));
                }
                components[i] = component(field);
                paramTypes[i] = field.getType();
            }
            return shapeOf(type, findConstructor(type, paramTypes), components);
        }

        // Android release：D8 把 record 降级成普通类，实例字段顺序会被重排（字母序），
        // 只有规范构造器参数仍保持声明顺序。因此按【参数名】对齐，而不是按字段顺序。
        Map<String, Component> byNameComponents = new LinkedHashMap<>();
        for (Field field : fields) {
            byNameComponents.put(field.getName(), component(field));
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            java.lang.reflect.Parameter[] params;
            try {
                params = constructor.getParameters();
            } catch (Throwable t) {
                // Executable.getParameters() 是 API 26 才有的反射方法（Android 7.x 上直接
                // NoSuchMethodError）。这里不做「退回按字段顺序绑定」的降级：D8 降级后字段是
                // 字母序，按它绑定会悄悄解错数据。宁可把崩溃换成能读懂的失败原因。
                throw new IllegalArgumentException(
                        "解包失败：" + type.getName() + " 需要按构造器参数名绑定，而本机反射没有 "
                                + "Executable.getParameters()（需 Android 8.0 / API 26 及以上）", t);
            }
            if (params.length != fields.size()) {
                continue;
            }
            Component[] components = new Component[params.length];
            boolean matched = true;
            for (int i = 0; i < params.length; i++) {
                Component component = byNameComponents.get(params[i].getName());
                if (component == null || !params[i].getType().equals(component.field.getType())) {
                    matched = false;
                    break;
                }
                components[i] = component;
            }
            if (matched) {
                return shapeOf(type, constructor, components);
            }
        }
        throw new IllegalArgumentException(
                "找不到可按参数名对齐的规范构造器: " + type.getName()
                        + "（record 降级后需保留 MethodParameters，见 proguard-rules.pro 与 -parameters）");
    }

    private static Shape shapeOf(Class<?> type, Constructor<?> constructor, Component[] components) {
        if (constructor == null) {
            throw new IllegalArgumentException("找不到规范构造器: " + type.getName());
        }
        constructor.setAccessible(true);
        return new Shape(constructor, components);
    }

    private static Component component(Field field) {
        JsonKey key = field.getAnnotation(JsonKey.class);
        String primary = key == null || key.value().isEmpty() ? field.getName() : key.value();
        String[] alternates = key == null ? new String[0] : key.alternates();
        return new Component(primary, alternates, field, field.getGenericType());
    }

    /**
     * 取 record 分量的声明顺序；非 record（含 D8 降级后的类）返回 {@code null}。
     * 反射 API 在低版本 Android 上不存在，因此整体包在 try/catch 里。
     */
    private static List<String> recordComponentNames(Class<?> type) {
        try {
            if (!type.isRecord()) {
                return null;
            }
            java.lang.reflect.RecordComponent[] components = type.getRecordComponents();
            if (components == null || components.length == 0) {
                return null;
            }
            List<String> names = new ArrayList<>(components.length);
            for (java.lang.reflect.RecordComponent component : components) {
                names.add(component.getName());
            }
            return names;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Constructor<?> findConstructor(Class<?> type, Class<?>[] paramTypes) {
        try {
            return type.getDeclaredConstructor(paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 工具

    private static Object lookup(Map<?, ?> map, Component component) {
        if (map.containsKey(component.key)) {
            return map.get(component.key);
        }
        for (String alternate : component.alternates) {
            if (map.containsKey(alternate)) {
                return map.get(alternate);
            }
        }
        return MISSING;
    }

    /**
     * 原 {@code DataModelKt.unwrapEmbeddedJsonStringOrNull}：
     * 值是字符串且 trim 后以 {@code {} / {@code [} 开头时再解析一次；解析失败视为 null。
     */
    private static Object unwrapEmbedded(Object value) {
        if (!(value instanceof String text)) {
            return value;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty() || (trimmed.charAt(0) != '{' && trimmed.charAt(0) != '[')) {
            return value;
        }
        try {
            return Json.parse(trimmed);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Double toDouble(Object value) {
        String text = FlexibleJson.rawText(value);
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized) {
            Object raw = parameterized.getRawType();
            if (raw instanceof Class<?> clazz) {
                return clazz;
            }
        }
        if (type instanceof WildcardType wildcard) {
            Type[] upper = wildcard.getUpperBounds();
            return upper.length == 0 ? Object.class : rawClass(upper[0]);
        }
        return Object.class;
    }

    private static Type typeArgument(Type type, int index) {
        if (type instanceof ParameterizedType parameterized) {
            Type[] args = parameterized.getActualTypeArguments();
            if (index < args.length) {
                return args[index];
            }
        }
        return Object.class;
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == double.class) {
            return 0.0d;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    private static String kindOf(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map) {
            return "对象";
        }
        if (value instanceof List) {
            return "数组";
        }
        if (value instanceof String) {
            return "字符串";
        }
        if (value instanceof Boolean) {
            return "布尔";
        }
        if (value instanceof Number) {
            return "数字";
        }
        return value.getClass().getSimpleName();
    }

    private static final class Component {
        final String key;
        final String[] alternates;
        final Field field;
        final Type type;
        final boolean primitive;

        Component(String key, String[] alternates, Field field, Type type) {
            this.key = key;
            this.alternates = alternates;
            this.field = field;
            this.type = type;
            this.primitive = field.getType().isPrimitive();
        }
    }

    private static final class Shape {
        final Constructor<?> constructor;
        final Component[] components;

        Shape(Constructor<?> constructor, Component[] components) {
            this.constructor = constructor;
            this.components = components;
        }
    }
}
