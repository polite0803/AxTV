// 重构工程 Gradle 入口。
//
// 设计说明：refactor/ 下 11 个源码目录（api/app/live/model/panel/player/
// settings/sign/tv/ui/widget）之间存在依赖环（api ↔ live ↔ sign），
// 且与 android/ 壳工程共用同一份 .java 文件，因此这里不做强隔离多模块，
// 而是统一收敛到 :tests 模块：main 源集直接指向 11 个既有目录，
// test 源集承载 JUnit 5 测试。任何 java 文件都不移动、不复制。

rootProject.name = "axtv-refactor"

include(":tests")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
