# 16 B1：关于页构建身份与 D-4 交付版本规则

产品负责人决定：REQUIREMENTS §67 第 2、3 项（2026-10-12）。分支 `claude/new-session-sbje5o`，只在工作分支上，**未进入默认分支**。

## 1. 提交

| 提交 | 内容 | 回滚 |
|---|---|---|
| `0b7a448` | 关于页“构建来源”卡片（`ui/SourceIdentity.kt`、`AboutScreen` 一行接线、四种语言共 11 条字符串、`SourceIdentityUiTest`） | `git revert 0b7a448` |
| `47e2716` | D-4：`docs/delivery/ledger.json` 和 `scripts/delivery_ledger.py`（含测试）；`apk_provenance.py` 读取包名和版本号，并可要求精确的源码 SHA；CI android 作业检查 Release APK 身份和交付账本 | `git revert 47e2716` |

| `cf3e580` | 修复两处本轮发现的缺陷：`PackageInfo.longVersionCode` 需要 API 28，而 minSdk 是 26，会让关于页在 Android 8.0/8.1 上崩溃（lint NewApi 错误），改用 `PackageInfoCompat`；`apk_provenance.py` 把 `compileSdkVersionCodename='17'` 误读成包名，改为整词匹配，并用真实 aapt2 输出作测试 | `git revert cf3e580`（须与 0b7a448/47e2716 一起回退） |

这些提交都没有修改 Schema、PK 内核或参数、权限、签名、applicationId、versionCode/versionName，也没有改动历史数据。B0 守卫在提交前后都通过：`SettingsScreen.kt`、`strings.xml` 是仅历史文件，没有新增任何豁免。

## 2. 关于页（CONFIRMED_SOURCE + TESTED_LOCAL）

关于页新增一张“构建来源”卡片，内容如下：
- **源码提交**：完整 40 位 SHA，等宽字体，可选中，窄屏自动换行。
- **提交时间（UTC）**：显示为 `2026-10-12 13:37:51`。标签写明是提交时间。没有构建时间字段，因此不会把两者混淆。
- **提交信息的来源**：三种状态分别是“从本项目的 Git 检出读取”、“构建时声明，未经核验”、“未知：构建时没有 Git 信息”。未知状态不会显示成可信版本。
- **含未提交改动**时显示红色提示：“此构建包含未提交的改动，不是正式构建。”
- **复制按钮**：剪贴板内容是 `version_name / version_code / source_sha / source_commit_time_utc / source_origin / source_dirty` 的 key=value 行，**不含应用名称**，避免在伪装模式下暴露应用身份。复制后按钮变为“已复制”，并带有文字标签，可被读屏读出。
- 不涉及健康数据、路径或令牌；应用仍然没有网络权限。

### 测试

| 测试 | 结果 |
|---|---|
| `SourceIdentityUiTest`（6 项）：完整 SHA 与时间、三种来源加 dirty 可区分、剪贴板内容精确且不含应用名、320dp × 2.0 字号下不截断且换行（原生图形模式实测文字布局）、简体中文标签、AboutScreen 显示编译进来的值 | 6/6 PASS |
| `TranslationsTest`（四语言键与占位符一致） | PASS |
| `ButtonAuditTest` 的 about 用例：en / zh-rCN / zh-rTW / fr-rFR × 字号 1.0/1.3/2.0 × 320/411dp × 浅色/深色 | 48/48 组合，**0** 条溢出或截断记录；已人工查看 zh-rCN 2.0/320 深色、zh-rTW 2.0/320 深色、fr 2.0/320 浅色、en 1.0/411 浅色截图 |
| 全量回归 | 见 §4 |

首轮截图发现提交时间在 320dp/2.0 字号下从数字中间断行（`14:0` / `0:10Z`），已改为日期和时间之间加空格，并缩短标签，复测后正常。

## 3. D-4 交付版本规则（CONFIRMED_SOURCE + TESTED_LOCAL）

**实测历史事实**：
- 2026-10-09 至 2026-10-12 之间，有多个不同的私下交付 APK 都是 versionCode 25 / 0.2.0（例如 APK `6cda8775…`、`2a34afa0…`、`d9d874e8…`，见默认分支和 PR #19 分支的 HANDOFF）。同证书、同版本号的覆盖安装可以成功，所以**仅凭版本号无法区分手机上装的是哪一个**。
- 公开 Release v0.2.0 的 versionCode 是 5。

**规则**（由 `scripts/delivery_ledger.py check` 强制）：
1. 只有**实际交付给用户**的 APK 才写入 `docs/delivery/ledger.json`。开发提交不递增 versionCode。
2. 每次交付的 versionCode 必须严格大于此前所有交付（政策前的最高值为 25，所以下一次交付至少 26；`delivery_ledger.py next` 会输出这个数）。
3. applicationId 必须等于 `net.plainnotes.app`，签名证书 SHA-256 必须等于原证书 `989ba045…79b1`，否则无法原地覆盖安装。
4. 源码必须是完整 SHA，且来自干净的 git 检出（origin=git、dirty=false）。
5. APK SHA-256 必须存在、不重复，且不能等于证书指纹。源码 SHA、APK SHA-256、证书指纹三者分开记录。
6. 源码中的 versionCode 不能低于最近一次交付，否则从该源码构建的包无法覆盖手机上的版本。

交付批次的操作：先把 `app/build.gradle.kts` 的 versionCode 改为 `delivery_ledger.py next` 的值，再构建、签名、核验。然后用 `apk_provenance.py BUILD_TOOLS APK --expect-source-sha <SHA>` 读出三种身份，追加一条账本记录。**本轮没有交付，所以 versionCode 仍为 25，账本 deliveries 为空。** 私有下载链接、密钥和密码一律不写入账本。

| 测试 | 结果 |
|---|---|
| `scripts/test_delivery_ledger.py`（8 项：仓库账本通过；递增通过；重复或降低版本号、包名或证书改变、源码不可信、APK 哈希缺失/重复/等于证书、缺字段、源码版本低于已交付版本，都被拒绝） | PASS |
| `scripts/test_apk_provenance.py`（6 项：只读 app 的 BuildConfig、包名与版本解析、精确 SHA/干净/git 才通过） | PASS |
| `scripts` 全部测试 | 18/18 PASS（含 cf3e580 新增的 aapt2 真实输出用例） |
| CI：R8 压缩后的 Release APK 必须恰好声明 `GITHUB_SHA` | 本地等价检查通过（见 §4）；CI 结果见 §4 |

## 4. 全量回归

| 运行 | 结果 |
|---|---|
| 第一次全量（提交 `47e2716`，干净树）：`core:data` / `core:reminder` / `app` 单测 + `lintFullDebug` + Debug/Release 构建 | 测试：data 72/0 失败、reminder 14/0、app 411/0（13 项是原有的环境性跳过），`PeriodStabilityTest` 5/5。**lint 失败：1 个 NewApi 错误**（见 `cf3e580`），因此 Release 未构建，BUILD FAILED |
| 修复后（提交 `cf3e580`，干净树）：`SourceIdentityUiTest` + `PeriodStabilityTest` + `BuildProvenanceTest` + lint + Debug/Release | BUILD SUCCESSFUL；6/6、5/5、4/4；lint **0 错误**，新文件没有警告 |
| Release APK 身份（`apk_provenance.py --expect-source-sha cf3e580…`） | 通过：`source_sha=cf3e58096039bcaf4765c98ef35243441247302f`、origin=git、dirty=false；包名 `net.plainnotes.app`，versionCode 25 / 0.2.0；unsigned APK sha256 `5f57014e…`（这是**未签名**测试包，不是交付物） |
| `check_release_manifest.py`（无 INTERNET） / `delivery_ledger.py check` | PASS / PASS（下一次交付至少 26） |
| B0 守卫 `check_protocol.py` | PASS（普通产品文件改动不触发守卫） |
| GitHub Actions（`cf3e580`） | run 38262091486，**等待结果** |

## 5. 未验证与风险

- **NOT_DEVICE_VERIFIED**：没有物理 ARM 设备验证。CI 的 API 35 x86_64 模拟器不能代替真机。
- TalkBack 只验证了语义（按钮带文字标签、字段为普通文本），没有用真实读屏实测朗读顺序。
- 账本只能约束**记录了的**交付；没有记录的私下交付规则管不到。需要每次交付时遵守流程。
- 无 `.git` 时 Release 构建失败（B1 构建侧，已在 13 号报告验证），这一轮没有改动。
