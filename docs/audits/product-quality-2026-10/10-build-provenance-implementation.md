# 10 B1 构建身份与可追溯（2026-10-12）

## 批次信息

| 项 | 内容 |
|---|---|
| 基准 | 审计分支 `claude/new-session-sbje5o`，起点 `e925bfd9`（其应用代码与默认分支 `f53726f` 相同） |
| 授权状态 | 构建侧：**APPROVED**（用户 B1 指令 §5：不涉及签名/Schema/PK 的最小改动）。关于页显示：**REQUIRES_APPROVAL**（见下） |
| 数据/算法/签名影响 | 数据 NO，PK NO，Schema NO，签名 NO，versionCode NO（D-4 未批准，保持 25） |
| 修改文件 | `app/build.gradle.kts`、`app/proguard-rules.pro`、新增 `app/src/test/.../BuildProvenanceTest.kt`。三者**都不在** P2 `production-baseline.json` 中 |
| 回滚 | revert 本批提交即可，无数据或格式残留 |

## 实现

1. `app/build.gradle.kts`：
   - 在配置阶段通过 `providers.exec` 读取 `git rev-parse HEAD`、提交时间（`TZ=UTC`，ISO-8601 `Z`）、`git status --porcelain --untracked-files=no`；
   - 写入 `BuildConfig.SOURCE_SHA` / `SOURCE_COMMIT_TIME_UTC` / `SOURCE_DIRTY`；
   - 使用**提交时间而非构建时间**，同一源码在任何机器上得到相同的值；
   - 可用 `-PsourceSha=<40 位小写十六进制>`（可选 `-PsourceCommitTimeUtc`）做可信注入，格式错误时直接失败。
2. 新任务 `verifyReleaseProvenance`，挂在 `preFullReleaseBuild` 上：Release 构建若 `SOURCE_SHA=unknown` 则**失败**；Debug 允许 `unknown`。
3. `proguard-rules.pro` 只保留这 3 个字段。否则 R8 会因为没有代码引用而删除 `BuildConfig`。已实测：不加这条规则时，Release dex 中 SHA 出现 0 次。
4. 测试 `BuildProvenanceTest`（3 项）：
   - SHA 只能是 40 位十六进制或 `unknown`；
   - 时间与 SHA 同时存在或同时为 `unknown`；
   - 在 GitHub Actions 中 `SOURCE_SHA == $GITHUB_SHA` 且不是 dirty。

## 验证结果

本地环境：会话内安装的 Android SDK（platform 37、build-tools 37.0.0）。Maven Central 返回 HTTP 429，因此使用**仅本会话**的 Gradle init 脚本，把依赖源换成 Google 的 Maven Central 镜像（`~/.gradle/init.d/`，不在仓库中）。

| 检查 | 结果 |
|---|---|
| `:app:testFullDebugUnitTest` | **PASS**：400 项，0 失败，13 项为已知的环境条件跳过。其中 `CalibrationEligibilityUiTest` 首轮因 Robolectric 下载 android-all 时遇到网络失败而 error，单独重跑 5/5 通过。`PeriodStabilityTest` 5/5；`BuildProvenanceTest` 3/3（本地没有 `GITHUB_SHA`，CI 等值断言按设计跳过） |
| `:core:data:testDebugUnitTest` / `:core:reminder:testDebugUnitTest` | **PASS** 72/72、14/14 |
| `lintFullDebug` | **PASS**：0 错误，131 警告（与历史相同） |
| `assembleFullDebug` / `assembleFullRelease` | **PASS**；Release 执行了 `verifyReleaseProvenance` |
| `scripts/check_release_manifest.py` | **PASS**（无 INTERNET） |
| P2 研究/协议守卫（252 项） | **PASS**（未触碰任何锁定文件） |
| Debug `BuildConfig` | SHA 为当时的 HEAD `e925bfd9…`，时间 `2026-10-12T10:36:08Z`，dirty=true（当时有未提交改动，符合预期） |
| Release dex 含 SHA | 加 keep 规则前 0 次，加后 1 次 |
| unsigned Release 体积 | 12,707,995 → 12,708,171 字节（+176 B） |
| 无 `.git` 的源码副本 | Release 校验 **FAIL**（预期，提示需要 git 或 `-PsourceSha`）；Debug 生成 `unknown`；合法注入 PASS；格式错误的注入 FAIL |
| GitHub CI（push 本批后） | 见 `08-verification-log.md` |
| 真机 / 截图 | NOT_RUN |

**如何从一个 APK 文件核对源码**（适用于本批之后构建的包）：

```
unzip -p app.apk classes.dex | grep -a -c <候选 40 位 SHA>
```

结果为 1 表示该 APK 由这个提交构建。对本批之前的旧 APK 无效，旧包仍需按 07 D-14 用文件哈希比对。

**注意**：在 `pull_request` 事件的 CI 中，checkout 的是 GitHub 生成的合并提交，SHA 会是那个合并提交，不是 PR head。私下交付的包应从分支的干净 checkout 构建。

## 关于页显示：已准备，未应用（REQUIRES_APPROVAL）

- 补丁：`10-about-source-identity.patch`（`git apply --check` 通过）。
- 内容：
  - 在关于页版本行下加一行可选择复制的等宽文本：`<完整 SHA> · <提交时间 UTC> [· dirty]`；
  - **不新增字符串资源**，不改四语 `strings.xml`。
- 阻断原因：`app/src/main/java/net/plainnotes/app/ui/SettingsScreen.kt` 在 P2 基线中（`86fe09b6…`）。补丁后哈希为 `4bb2a0ff874b7b947bb5c2093467d23a98a695eafc484d61d7822f7e7752b890`。
  - 按现行守卫，这个文件可以登记 UI 偏离（位于 `ui/`，文件名不含 Conc/Lab/Chart，与 PK 无关）。
  - 但偏离记录的 `authorized_by` 必须来自产品负责人。代理不能代填，也不能用新建文件等方式绕开。
- 用户批准后的步骤：
  1. 应用补丁；
  2. 在 `protocol-deviations.json` 末尾追加一条（`id: PD-2026-10-12-ABOUT-SOURCE`，baseline `86fe09b6…` → new `4bb2a0ff…`，`pk_relevant: false`，`authorized_by` 引用用户的批准原文和日期）；
  3. 增加一个 Robolectric 测试，断言 `about-source` 节点存在且文本以 `BuildConfig.SOURCE_SHA` 开头；
  4. 做四语、320/411dp、字号 1.0/2.0 截图审计。
- 如果 D-13 批准方案 A，`SettingsScreen.kt` 会归入 HISTORY_ONLY，就不再需要偏离记录。
