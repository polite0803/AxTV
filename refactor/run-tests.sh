#!/usr/bin/env bash
# 一键跑 refactor 全部 JUnit 5 测试（需 JDK 17，JAVA_HOME 指向 JDK17 或 java 在 PATH）。
set -e
cd "$(dirname "$0")"
if [ -x "./gradlew" ]; then
    ./gradlew :tests:test "$@"
else
    gradle :tests:test "$@"
fi
