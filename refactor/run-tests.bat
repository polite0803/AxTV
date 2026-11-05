@echo off
rem 一键跑 refactor 全部 JUnit 5 测试（需 JDK 17，JAVA_HOME 指向 JDK17 或 java 在 PATH）。
setlocal
cd /d "%~dp0"
if exist gradlew.bat (
    call gradlew.bat :tests:test %*
) else (
    call gradle :tests:test %*
)
