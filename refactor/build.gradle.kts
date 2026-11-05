// 根工程：构建逻辑全部在 :tests 模块（tests/build.gradle.kts）。
// 用法（需 JDK 17）：
//   ./gradlew :tests:test        跑全部 JUnit 5 测试
//   ./gradlew :tests:clean :tests:test   全量重编 + 测试

allprojects {
    group = "com.github.axtv"
    version = "0.0.3"
}
