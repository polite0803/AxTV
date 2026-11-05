# AxTV 壳工程混淆规则（release 起步，后续按需收紧）

# QuickJS JNI（sign 模块 JsRuntime 的实现依赖）
-keep class com.dokar.quickjs.** { *; }
-keepclassmembers class * extends com.dokar.quickjs.QuickJs { *; }

# kotlinx.serialization（如后续接入 JSON 生成器）
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# 核心层入口（JNI 回调 / 反射装配点，按需保留）
-keep class axtv.sign.** { *; }
-keep class axtv.panel.** { *; }

# axtv.api.ModelJsonCodec 通过反射按 @JsonKey 绑定 model record：
# 必须保留 model 类、字段名、注解与规范构造器，否则 release 解码会失效/错位。
-keep class axtv.model.** { *; }
