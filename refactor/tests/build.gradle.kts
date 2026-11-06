// 测试工程模块：
//  - main 源集 = refactor/ 下 11 个既有源码目录（包根 <dir>/axtv/...）
//  - test 源集 = src/test/java 下的 JUnit 5 测试（由 .workbuddy/SmokeTest.java 工程化拆分而来）
plugins {
    `java-library`
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf(
                rootDir.resolve("api"),
                rootDir.resolve("app"),
                rootDir.resolve("iptv"),
                rootDir.resolve("live"),
                rootDir.resolve("model"),
                rootDir.resolve("panel"),
                rootDir.resolve("player"),
                rootDir.resolve("settings"),
                rootDir.resolve("sign"),
                rootDir.resolve("tv"),
                rootDir.resolve("ui"),
                rootDir.resolve("widget"),
            ))
        }
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-Dfile.encoding=UTF-8")
    testLogging {
        events("failed", "skipped")
        showStackTraces = true
    }
}
