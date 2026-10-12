# 08 验证日志（阶段 A，2026-10-12）

环境：Claude Code 云容器，Linux x86_64，JDK 21.0.12，Gradle wrapper 9.3.1，Python 3（系统）。**无 Android SDK、无 KVM、无模拟器、无物理设备。** 工作分支 `claude/new-session-sbje5o`，源码等同 `f53726f`（审计文档除外）。

| # | 命令 | 退出状态 | 结果 |
|---|---|---|---|
| 1 | `git status --short`、`git fetch origin --prune`、`git rev-parse` | 0 | 工作树开工时干净；默认分支 HEAD f53726f；本地与远端一致 |
| 2 | `./gradlew -PjvmOnly test`（第 1 次） | 1 | 插件解析失败（缓存了 Kotlin 插件“不存在”的负结果）；`curl` 同一 URL 返回 200 → 环境问题，不是项目缺陷 |
| 3 | `./gradlew -PjvmOnly help --refresh-dependencies` | 0 | 插件解析恢复 |
| 4 | `./gradlew -PjvmOnly test`（第 2–5 次） | 1 | Maven Central 返回 **HTTP 429 Too Many Requests**，每次下载更多依赖；按退避重试 |
| 5 | `./gradlew -PjvmOnly test`（第 6 次） | **0** | `:core:domain` **58/58**、`:pk-engine` **58/58**、`:importer` **12/12** 通过，0 失败 0 跳过（JUnit XML 汇总） |
| 6 | `python3 -m unittest discover -s tools/pk-research -p 'test_*.py'`（与 `android.yml:21` 相同） | **0** | **252** 项通过（含协议锁/冻结守卫测试） |
| 7 | `python3 -I tools/pk-research/check_protocol.py` | 0 | 打印协议锁 JSON（PRE_FIT，`protocol_deviations: []`）；守卫的实际拦截由第 6 项单测执行 |
| 8 | GitHub API：默认分支 `android.yml` 运行 | — | run 38137617053（f53726f）**success** |
| 9 | GitHub API：PR #19 check runs（head aecbd7d） | — | 7 个 check run 全 success（android×2、jvm×2、device-tests×2、reference） |

## NOT_RUN / BLOCKED

| 项目 | 状态 | 原因 |
|---|---|---|
| `:app:testFullDebugUnitTest`（含 `PeriodStabilityTest`）、`:core:data`、`:core:reminder` 单测 | **BLOCKED（本地）** | 无 Android SDK；只能引用 CI（第 8、9 项） |
| lint、assembleFullDebug/Release、APK 体积 | **BLOCKED（本地）** | 同上 |
| 仪器测试 / 截图 / TalkBack | **BLOCKED** | 无模拟器/设备 |
| PR #19 私下交付 APK 的 SHA/证书核验 | **NOT_RUN** | 审计不持有 APK，也不访问签名材料 |
| 用户手机安装版本 | **UNKNOWN** | 需产品负责人提供 |
