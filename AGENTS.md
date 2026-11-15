# AxTV 开发环境约定（Agent 必读）

## 唯一真相

项目源码只存在于 WSL 原生 ext4 路径 `/home/polit/projects/AxTV`。
`Z:/AxTV` 是同一份文件的 SMB 只读镜像（WSL 内 `smbd` 的 `[projects] path = /home/polit/projects`）；
`\\wsl$\Ubuntu-24.04\home\polit\projects\AxTV` 是 9P 视图。三者不是副本，但后两者每个文件操作
都要跨 VM 边界：读 100 个不同 java 文件 SMB 213ms / 9P 240ms，ext4 读 314 个只要 7ms。

## 硬性规则

1. 项目内所有文件读写、搜索、重构、构建、测试、adb 一律经 SSH 批量会话完成：

       ssh wsl 'bash -s' <<'EOF'
       ... 多个文件的读、写、校验一次做完 ...
       EOF

   禁止逐条 ssh（每次建连约 342ms）。编辑用远程 `python3` 精确替换 + 读回比对 / sha256 校验。
2. 禁止在 Windows 侧对 `Z:` 执行 gradlew、adb、批量编辑、全库搜索；两端共用 `build/` 与
   `.gradle/` 会触发 `java.nio.file.NotDirectoryException`。
3. 统一入口：`/home/polit/projects/AxTV/scripts/axtv-dev.sh`（`--help` 看全部）。

       ssh wsl 'bash /home/polit/projects/AxTV/scripts/axtv-dev.sh <cmd>'

   子命令：`env / test / unit / lint / build / release / clean / adb / connect <IP> / logcat / sh / version`。
   `sh '<批量命令>'` 供 Agent 在单次会话内做多文件读写与校验。
4. 环境由脚本钉死：JDK 17（`/usr/lib/jvm/java-17-openjdk-amd64`，与 CI 一致）、
   `ANDROID_HOME=ANDROID_SDK_ROOT=/home/polit/android-sdk`、PATH 含 platform-tools。
   `android/local.properties` 的 `sdk.dir` 已是 WSL 路径，禁止改成 Windows 路径。
5. git 按用户级 2026-11-10 规范正常使用：只读查看与常规提交照常；reset --hard / force push / rebase / stash / --no-verify 等破坏性操作须先列风险并确认。旧版「禁止任何 git 命令」已作废。

6. **禁止把项目复制到 C:/D: 盘做「本地磁盘副本」再编译**：`Z:` 与 WSL 是同一份文件，
   副本改动不回流；`sdk.dir`、`gradlew`、CI 全是 Linux 形态，Windows 侧也跑不起来。
   `README.md` / `docs/PLAN-*` 里「复制到本地盘避开 aapt2 超时」是历史做法，已废弃。
   构建一律 `ssh wsl 'bash /home/polit/projects/AxTV/scripts/axtv-dev.sh build'`，IDE 只做编辑。

## IDE 打开方式（CodeBuddy remote / WorkBuddy 锚点 / Qoder 例外见末尾）

**对 CodeBuddy / VS Code 系是必须，不是可选**：凡要编辑、索引、跑语言服务或编译，IDE 一律以 WSL remote 工作区打开
`/home/polit/projects/AxTV`，让 IDE server、LSP、终端、文件 I/O 全落在 ext4 内。
用 `Z:/AxTV` 或 `\\wsl$\...` 打开，语言服务会持续跨 SMB/9P 读文件，编译故障与卡顿不会消失。

本机 **CodeBuddy CN 已具备，无需安装**：`remoteAuthority = codebuddy-wsl+ubuntu-24.04`，
WSL 侧有 `/home/polit/.codebuddy-server-cn/`，用户数据里三个项目都留有 remote 工作区记录。

    "C:\Users\polit\AppData\Local\Programs\CodeBuddy CN\bin\buddycn.cmd" --remote codebuddy-wsl+ubuntu-24.04 /home/polit/projects/AxTV

或在命令面板选「WSL 连接 / Remote-WSL: New Window」再打开该目录。
**唯一判据**：IDE 内置终端 `pwd` = `/home/polit/projects/AxTV`、`uname -s` = `Linux`；
若 `pwd` 是 `Z:\...` / `C:\...` 或 `uname` 显示 MINGW，仍是本地视图，必须重开。

WorkBuddy 个人版 5.9.3 没有 remote 工作区能力，也不需要：它只做上下文锚点，读写由 Agent
走规则 1 的 SSH 批量会话（命令在 Linux 侧执行、文件 I/O 留在 ext4、0 次文件 RPC）。
企业版 4.9.9+ 的 `Remote SSH native` 是逐文件 SFTP/exec channel 转发，反而不如批量 SSH。

**Qoder CN 例外（2026-11-11）**：Qoder 的 SSH 工作区不支持 BYOK 第三方模型，因此 Qoder
以**本地工作区 `Z:/AxTV`** 打开（模型层留本地），由 `.qoder/rules/00-execution-ssh.md`
（`alwaysApply: true`）强制所有构建 / 测试 / adb / git 经 `ssh -o BatchMode=yes wsl ...`
落到 ext4。**这不是放宽规则**：上面规则 1 / 2 / 6 完全不变，编译绝不在 SMB 上跑。
此时 IDE 终端 `pwd` 是 `Z:\...` 属正常，**不要**用上面的判据要求重开为 remote 工作区。
