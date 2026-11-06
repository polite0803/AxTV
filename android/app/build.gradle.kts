import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 核心层源码目录（refactor/ 下 11 个模块）。
// 采用 sourceSets 直连而非拷贝：核心层只有一份真相，改造自动同步，
// 不会出现「壳工程里的拷贝落后于 refactor/」的漂移。
val refactorModules = listOf(
    "api", "app", "live", "model", "panel", "player", "settings", "sign", "tv", "ui", "widget"
).map { rootProject.file("../refactor/$it") }

// JVM-only 实现：Android 的 android.jar 不含 java.net.http / com.sun.net.httpserver，
// 这些类在 Android 侧由原生实现替代（见 app/src/main/java/com/github/axtv/dv/）。
val jvmOnlyExcludes = listOf(
    "**/live/JdkWebSocketTransport.java",
    "**/settings/JdkHttpFetcher.java",
    "**/panel/JdkPanelServer.java"
)

// Release 签名凭据：从 android/local.properties 读取（该文件不入库）。
// 缺失时回退 debug 签名并告警，避免本地构建直接失败。
val signingProps = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}
fun signingValue(key: String): String? =
    signingProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }

val hasReleaseSigning = listOf(
    "AXTV_STORE_FILE", "AXTV_STORE_PASSWORD", "AXTV_KEY_ALIAS", "AXTV_KEY_PASSWORD"
).all { signingValue(it) != null }
if (!hasReleaseSigning) {
    logger.warn("警告: 缺少 AXTV 签名凭据（local.properties 或同名环境变量），release 产物将被拒绝构建")
}

// 绝不让 release 产物悄悄回退到 debug 签名：显式 release 任务在配置期失败，
// assemble / build 这类聚合任务则在 release 任务真正执行前失败。
val releaseTaskRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}
if (releaseTaskRequested && !hasReleaseSigning) {
    throw GradleException("缺少 AXTV 发布签名凭据，拒绝构建 release 产物。请配置 local.properties 或同名环境变量。")
}
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" || it.name == "installRelease" }
    .configureEach {
        doFirst {
            if (!hasReleaseSigning) {
                throw GradleException("缺少 AXTV 发布签名凭据，拒绝构建 release 产物。")
            }
        }
    }

android {
    namespace = "com.github.axtv.dv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.axtv.dv"
        // 原版 23。API 24/25 上 D8 降级 record 后无法按参数名绑定（getParameters 需 API 26），
        // 会导致所有接口数据解码失败，故抬到 26（Android 8.0）。见 ModelJsonCodec。
        minSdk = 26
        targetSdk = 36       // 原版 37（compileSdk 37 / Android 17 预览），档 2 构建用 36
        versionCode = 6        // 0.1.2: 修复设置导入后搜索登录态缺失（Cookie 同步接线）
        versionName = "0.1.2"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingValue("AXTV_STORE_FILE")!!)
                storePassword = signingValue("AXTV_STORE_PASSWORD")
                keyAlias = signingValue("AXTV_KEY_ALIAS")
                keyPassword = signingValue("AXTV_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 无凭据时为 null（产出未签名包），不会伪装成 debug 签名。
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Java 8+ API 由 desugaring 在运行期提供：
        // java.time、java.util.stream、List.of/Map.of/Set.of、String.isBlank 等（见官方 desugared_apis 清单）。
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures { compose = true; buildConfig = true }

    lint {
        lintConfig = file("lint.xml")
        abortOnError = true
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(refactorModules)
        }
    }
}

// Kotlin 2.3+ 必须用顶层 kotlin {} DSL 设置 jvmTarget（android.kotlinOptions 里的字符串写法已废弃）。
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// 排除 JVM-only 实现（Android 的 android.jar 不含 java.net.http / com.sun.net.httpserver，
// 这些类在 Android 侧由原生实现替代）。放在 compileJava 任务上执行排除最可靠。
// 注意：AGP 不允许在 JavaCompile 上用 options.release（会阻断 bootclasspath 注入），
// 故用 sourceCompatibility/targetCompatibility（见上面的 android.compileOptions）。
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // D8 会把 record 降级成普通类并重排实例字段（字母序），ModelJsonCodec 只能靠
    // 规范构造器的【参数名】把参数对齐回字段。没有 -parameters 时参数名会变成 arg0…。
    options.compilerArgs.add("-parameters")
    exclude(jvmOnlyExcludes)
}

dependencies {
    // 核心库 desugaring：把 java.time / java.util.stream / List.of·Map.of·Set.of / String.isBlank 等
    // Java 8+ API 反向移植到 minSdk 26 的运行时。java.nio.file 与 InputStream.readAllBytes 不在其覆盖范围，
    // 已在 CrashHandler / MyApplication / UpdateManager / LiveFrameCodec 内改用 java.io 实现。
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tv.material)
    implementation(libs.compose.tv)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.coroutines.android)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.session)

    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.androidx.palette)
    implementation(libs.work.runtime)
    // quickjs-android 暂缓：JitPack 对 com.github.dokar3:quickjs-quickjs-android-wrapper 返回 401
    // （无构建记录）。本轮改为内置 APK 自带的 libquickjs-android-wrapper.so（jniLibs/）
    // + com.whl.quickjs 绑定类（src/main/java/com/whl/quickjs/），零新增 Maven 依赖。
    // implementation(libs.quickjs.android)

    // EPG：TvContractCompat（TvProviderAndroid 适配类）
    implementation(libs.tvprovider)
    // 本地渲染仓库二维码（ui/settings/SettingsActionDialogs.kt），避免依赖外部在线二维码服务。
    implementation(libs.zxing.core)

    testImplementation("junit:junit:4.13.2")
}
