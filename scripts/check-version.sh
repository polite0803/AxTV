#!/usr/bin/env bash
# 版本一致性门：README「当前版本 X.Y.Z」必须与 android/app/build.gradle.kts 的 versionName 一致。
# 版本号改了 README 没跟上是发布前的常客；CI 里作为独立 job 跑，本地也可直接执行。
set -euo pipefail

gradle_version="$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' android/app/build.gradle.kts | head -n 1)"
readme_version="$(sed -n 's/^> 当前版本 \([0-9][0-9.]*\).*/\1/p' README.md | head -n 1)"

if [ -z "$gradle_version" ]; then
  echo "check-version: android/app/build.gradle.kts 里没找到 versionName" >&2
  exit 1
fi
if [ -z "$readme_version" ]; then
  echo "check-version: README.md 里没找到「> 当前版本 X.Y.Z」行" >&2
  exit 1
fi
if [ "$gradle_version" != "$readme_version" ]; then
  echo "check-version: 版本不一致 — build.gradle.kts versionName=$gradle_version, README.md 当前版本=$readme_version" >&2
  exit 1
fi
echo "check-version: OK ($readme_version)"
