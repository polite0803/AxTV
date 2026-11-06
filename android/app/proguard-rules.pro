# AxTV 壳工程混淆规则（release 起步，后续按需收紧）

# QuickJS JNI（sign 模块 JsRuntime 的实现依赖）
# 绑定类在 app/src/main/java/com/whl/quickjs/，native 侧按类名 FindClass 查找，
# 类名或成员被 R8 裁剪/改名会在 createContext 时 SIGABRT，必须整包保留。
-keep class com.whl.quickjs.** { *; }

# kotlinx.serialization（如后续接入 JSON 生成器）
# MethodParameters：record 被 D8 降级后，ModelJsonCodec 依赖构造器参数名对齐字段，
# 丢掉这个属性会让参数名退化成 arg0…，release 解码直接失败。
-keepattributes *Annotation*, InnerClasses, MethodParameters
-dontnote kotlinx.serialization.**

# 核心层入口（JNI 回调 / 反射装配点，按需保留）
-keep class axtv.sign.** { *; }

# panel：仅保留具体实现类（Class.forName("axtv.panel.JdkPanelServer") 反射调用点 + PanelServer 抽象类）
-keep class axtv.panel.PanelServer { *; }
-keep class axtv.panel.JdkPanelServer { *; }

# axtv.api.ModelJsonCodec 通过反射按 @JsonKey 绑定 model record：
# 必须保留 model 类、字段名、注解与规范构造器，否则 release 解码会失效/错位。
-keep class axtv.model.** { *; }

# WebView JS 桥（精选网页取数）：@JavascriptInterface 方法只在 JS 侧按名调用，
# R8 看不到调用点会整类裁掉/改名，必须保留。
-keepclasseswithmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
