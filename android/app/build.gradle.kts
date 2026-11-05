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

android {
    namespace = "com.github.axtv.dv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.github.axtv.dv"
        minSdk = 23          // 原版 AndroidManifest minSdkVersion=23
        targetSdk = 35       // 原版 37（compileSdk 37 / Android 17 预览），构建用 35 起步
        versionCode = 43     // 原版 versionCode=43
        versionName = "2.3.43"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // minSdk 23 保持，Java 8+ API 由 desugaring 在运行期提供：
        // java.time、java.util.stream、List.of/Map.of/Set.of、String.isBlank 等（见官方 desugared_apis 清单）。
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    sourceSets {
        getByName("main") {
            java.srcDirs(refactorModules)
        }
    }
}

// 排除 JVM-only 实现（Android 的 android.jar 不含 java.net.http / com.sun.net.httpserver，
// 这些类在 Android 侧由原生实现替代）。放在 compileJava 任务上执行排除最可靠。
// 注意：AGP 不允许在 JavaCompile 上用 options.release（会阻断 bootclasspath 注入），
// 故用 sourceCompatibility/targetCompatibility（见上面的 android.compileOptions）。
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    exclude(jvmOnlyExcludes)
}
dependencies {
    // 核心库 desugaring：把 java.time / java.util.stream / List.of·Map.of·Set.of / String.isBlank 等
    // Java 8+ API 反向移植到 minSdk 23 的运行时。java.nio.file 与 InputStream.readAllBytes 不在其覆盖范围，
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
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime)
    // quickjs-android 暂缓：JitPack 对 com.github.dokar3:quickjs-quickjs-android-wrapper 返回 401
    // （无构建记录）。本轮改为内置 APK 自带的 libquickjs-android-wrapper.so（jniLibs/）
    // + com.whl.quickjs 绑定类（src/main/java/com/whl/quickjs/），零新增 Maven 依赖。
    // implementation(libs.quickjs.android)

    // EPG：TvContractCompat（TvProviderAndroid 适配类）
    implementation(libs.tvprovider)

    testImplementation("junit:junit:4.13.2")
}
