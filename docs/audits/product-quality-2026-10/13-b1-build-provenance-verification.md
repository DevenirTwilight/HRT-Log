# 13 B1 源码身份：复核、加固与验证（2026-10-12）

取代 `10-build-provenance-implementation.md` 的“验证结果”部分；10 保留为初版记录。

## 1. 授权与范围

| 项 | 状态 |
|---|---|
| 构建侧源码身份（BuildConfig、Release 校验、R8 保留、测试、读取脚本） | **APPROVED**：用户 B1 指令，以及本轮任务书“B1 复核与必要修正” |
| 关于页显示 | **WAITING_APPROVAL**：`SettingsScreen.kt` 受 P2 基线锁定；补丁已准备并验证，未应用 |
| versionCode（D-4） | **WAITING_APPROVAL**，保持 25 |
| 签名 / 交付 / Release | 未做，未授权 |

## 2. 复核发现与修正（提交 `0d99591`）

初版（`6f13c66`）存在以下问题，均已修正：

| # | 初版问题 | 风险 | 修正 |
|---|---|---|---|
| 1 | 没有检查 git 是否属于**本项目**：源码包解压在另一个 git 仓库内时，会拿到外层仓库的 HEAD | 错误的身份 | 要求 `git rev-parse --show-toplevel` 等于项目根目录（场景 S7 验证外层 HEAD `517d376…` 被忽略） |
| 2 | dirty 判断忽略未跟踪文件 | 新增但未提交的源文件不会被标记 | 改为 `--untracked-files=normal`（ignored 文件除外） |
| 3 | `-PsourceSha` 只校验格式，并覆盖 git 结果；此时 dirty 被强制为 false | 可伪造或误报身份 | 有 checkout 时注入值必须等于 HEAD，否则构建失败；没有 checkout 时标为 `declared-unverified` |
| 4 | dirty 的 Release 仍可构建 | 交付包可能含未提交改动 | Release 要求 `origin=git` 且 `dirty=false` |
| 5 | 无法区分“来自 git”和“外部声明” | 身份可信度不明 | 新增 `BuildConfig.SOURCE_ORIGIN` |
| 6 | 只能靠字节 grep 判断 APK 中是否含 SHA | 不可靠 | 新增 `scripts/apk_provenance.py`：用 `dexdump -l xml` 解析 `net.plainnotes.app.BuildConfig` 的静态常量，并**分别**输出 APK sha256、签名证书 SHA-256（apksigner）、源码字段；附带单元测试 |

## 3. 三种身份（不得混用）

| 身份 | 证明什么 | 如何获取 |
|---|---|---|
| 源码 SHA（`SOURCE_SHA` + `SOURCE_ORIGIN` + `SOURCE_DIRTY`） | 构建**声称**来自哪个提交；只有 `origin=git` 且 `dirty=false` 时才对应一个精确提交。它不是密码学证明 | `apk_provenance.py` → `source`；批准后也可在关于页查看 |
| APK 文件 sha256 | 这一个文件的字节 | `apk_provenance.py` → `apk_sha256`；交付清单中记录 |
| 签名证书 SHA-256 | 谁签的名（能否覆盖安装） | `apk_provenance.py` → `signing_cert_sha256`（apksigner）；unsigned 包显示“unsigned” |

## 4. 场景矩阵（干净克隆 @ `0d99591`，每项都实际调用 Gradle）

| 场景 | 结果 | 期望 |
|---|---|---|
| S1 干净 checkout：`assembleFullRelease` | PASS；DEX 解析：SHA=`0d99591…`、time=`2026-10-12T11:25:30Z`、dirty=false、origin=git；APK sha256 `bfa58c96…` | ✔ |
| S2 修改已跟踪文件 | Debug：dirty=true；Release 校验 FAIL（“uncommitted or untracked changes”） | ✔ |
| S3 新增未跟踪文件 | Debug：dirty=true；Release 校验 FAIL | ✔ |
| S4 `-PsourceSha` 与 HEAD 不符 | 配置阶段 FAIL（“does not match the checkout HEAD”） | ✔ |
| S5 `-PsourceSha` 与 HEAD 相符 | PASS | ✔ |
| S6 无 `.git`（`git archive` 导出） | Debug：SHA=`unknown`、origin=unknown；Release FAIL | ✔ |
| S6' 无 `.git` + `-PsourceSha` | Debug：origin=`declared-unverified`；Release FAIL | ✔ |
| S7 无 `.git` 且位于另一个 git 仓库内 | Debug：unknown（外层 HEAD 未被采用）；Release FAIL | ✔ |
| 可复现性 | 同一源码在本机两次独立构建，unsigned Release sha256 都是 `bfa58c96…` | 单机观察，不代表跨机器可复现 |

## 5. 全量回归（干净克隆 @ `0d99591`，本地 Android SDK 37）

| 检查 | 结果 |
|---|---|
| `:core:domain` / `:pk-engine` / `:importer` | 58 / 58 / 12，0 失败 |
| `:core:data` / `:core:reminder` | 72 / 14，0 失败 |
| `:app:testFullDebugUnitTest` | 405 项，0 失败，13 项环境条件跳过；`PeriodStabilityTest` 5/5；`BuildProvenanceTest` 4/4 |
| `lintFullDebug` | 0 错误，131 警告（与基线相同） |
| `assembleFullDebug` / `assembleFullRelease` | PASS；Release 12,708,227 B（基线 12,707,995 B，+232 B 为 4 个保留字段） |
| `check_release_manifest.py` | PASS（无 INTERNET） |
| P2 研究/守卫测试 | OK |
| `scripts/test_apk_provenance.py` | 2/2 |
| 网络 | Maven Central 返回 429。使用仅本会话的 `~/.gradle/init.d/central-mirror.gradle.kts`，改用 Google 镜像；仓库文件未改 |
| 真机 / 模拟器 / 截图 | NOT_RUN（容器无 KVM） |

## 6. CI

| run | 提交 | 结果 |
|---|---|---|
| 38241151594 | `6f13c66`（初版 B1） | jvm / android / device-tests 全部 **success**。android 作业日志显示 `:app:verifyReleaseProvenance` 已执行，`assembleFullRelease` 成功。`BuildProvenanceTest` 中与 `GITHUB_SHA` 的等值断言：Actions 会给每个步骤注入 `GITHUB_SHA`，Gradle 测试进程继承该环境，因此**推断**已执行；日志没有直接打印，未下载测试产物核对 |
| 38244314171 | `1e7b79d`（含 `0d99591` 代码） | cancelled：被下一次推送按 concurrency 规则取消，不是失败 |
| 38244330717 | `5b2993d`（代码同 `0d99591`，之后只有文档提交） | jvm / android / device-tests 全部 **success**（11:41–11:55Z） |

## 7. 关于页补丁 v2（未应用）

- `10-about-source-identity.patch` v2：显示 `<完整 SHA> · <提交时间 UTC> [· <origin≠git>] [· dirty]`，全文可选择复制；不新增字符串资源。
- 在干净克隆中**临时**应用后：
  - 编译通过；
  - 临时 Robolectric 测试（320dp）1/1 通过，节点文本与 BuildConfig 一致；
  - 现行守卫 `EvidenceGuardTest.test_lock_and_production…` **报错**：`Production or old evidence changed: …/SettingsScreen.kt`。这证明批准门槛有效。
  - 验证后已还原，未提交。
- 补丁后 `SettingsScreen.kt` 的 SHA-256：`c1eef34cf943f7f7f2ffcdaa58dc2fa815e3818bd213db38b5c73942d8d7f08c`（基线 `86fe09b6…`）。
- 已知局限：为了不碰锁定的 `strings.xml`，这一行没有本地化标签（如“源码提交”）。D-13 批准后，可以改为带四语标签的版本。
- 批准后还需：四语 × 320/411dp × 字号 1/1.3/2 × 浅/深 截图审计。
