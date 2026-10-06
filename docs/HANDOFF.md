# 交接说明：工作进度与开发指南

最后更新：2026-10-06。需求见 `docs/REQUIREMENTS.md`，务必先读第 2 节"硬性规则"。

## 1. 当前状态一览

| 部分 | 状态 |
|---|---|
| M1 药物、提醒调度、日历 | 完成 |
| M2 历史、库存 | 完成（另加 HRT tracker 风格的历史页和批量补录） |
| M3 身心状态 | 完成 |
| M4 浓度估算、化验 | 引擎移植自 Transmtf（见第 4 节）；文献调研（M4a）进行中，尚未入库 |
| M5 Trans Memo 导入、加密备份、CSV/PDF 导出 | 完成；另加 HRT tracker 导入 |
| M6 应用锁、隐蔽通知、精简模式、多语言、对比度 | 完成 |
| M7 伪装模式（仅 full 变体） | 完成；2026-10-06 按新要求调整，**调整部分未跑测试**（见第 3 节） |
| 日历改版（日/周/月/年视图、库存预计） | 完成 |
| 版本 | 0.2.0（versionCode 2） |

## 2. 当前任务（`docs/REQUIREMENTS.md` 第 4 节）的进度

### 4.1 授权请求：**未完成，被阻塞**
- 2026-10-06 运行 `gh auth status`：未登录（环境里的 GH_TOKEN 无效）。按要求停下并已告知用户，**没有发 issue**。
- 本会话的 GitHub 连接只覆盖 DevenirTwilight/HRT-Log，无法代为在其他仓库发 issue。
- 需要用户登录 `gh` 后再执行，或由用户手动发出。issue 标题和正文在 `docs/licensing.md`，必须原样使用。
- **仓库目前是公开的**，与"授权明确前保持私有"的要求冲突；已告知用户，可见性只有用户能改。公开内容包括 `tools/pk-reference/upstream/`（上游 TS 原文件副本）和 CI 产物中的 APK。
- `docs/licensing.md` 已写好（来源链、许可状态、issue 文本），发出后补上链接和日期。

### 4.2 补齐小项：代码已写完，**未提交前的全量测试被用户中断**
1. **关于页**（`app/.../ui/SettingsScreen.kt` 的 `AboutScreen`）：已完成。新增 Chrysalide 致谢和捐助链接、"无隶属或合作关系"声明、浓度模型出处说明（含上游许可待确认），链接用系统浏览器打开。`THIRD_PARTY_NOTICES.md` 已补上游说明。
2. **补药通知**：已完成，并有单元测试 `core/reminder/src/test/.../StockAlertsTest.kt`（已通过）。
   - 代码在 `core/reminder/.../StockAlerts.kt`，由 `ReminderCoordinator.rebuild()` 末尾调用。
   - 规则：只看在用、开启通知、无待确认标记的药物；记录了库存（有开封或未开封的包装），且余量不够未来 7 天计划用量时提醒；已开封包装距开封后有效期 ≤3 天时提醒。每种药物每类提醒每天最多一次（记录在 SharedPreferences `stock_alerts`）。
   - 文字遵守隐蔽通知设置：只有用户开启"显示详情"时才写药名。
   - 阈值常量从 app 的 `StockScreen.kt` 统一到 `StockAlerts`。
3. **商品名**：已完成。`ui/Format.kt` 的 `brandNames()` 表，显示在编辑药物时分子和酯型下拉框的选项下方及已选值下方（`DropdownField` 新增 `detail` 参数）。说明书链接**未做**（规格为可选）。
4. **伪装模式**：
   - 开启流程第一步必须保存加密备份，写入成功才能点"开启"（`DisguiseSection.kt` 的 `SetupDialog`，调用 `NotesViewModel.backupTo`）。原来的"我已了解"勾选框已删除。
   - 开启后弹出说明页：怎么进入、摇一摇退出、立即锁定、怎么关闭、忘记密码怎么办。
   - 离开应用立即锁定：`MainActivity.onStop` 把 `Session.open` 置 false；例外是 `Session.externalPicker`，所有系统文件选择器都改用 `launchPicker()` 启动（`security/Session.kt`），返回 `onStart` 时清除该标记。
   - `DisguiseSection` 的签名改为 `(onDisabled, backup)`，play 变体的空实现同步修改。

**这些改动的验证情况**：`compileFullDebugKotlin` 和 `compilePlayDebugKotlin` 都通过；`StockAlertsTest` 通过；**全量 `test` 和 lint 还没跑**（运行中被用户中断）。接手后第一件事：跑第 5 节的完整检查，通过后再提交。

### 4.3 M4a 文献调研：**进行中，结果不在仓库**
- 已启动三个并行调研任务（雌二醇口服/舌下/肌注；透皮凝胶、贴片和 CPA；螺内酯含坎利酮、口服孕酮），要求每条引用都用 PubMed 或说明书核实，没有可靠来源就不填。
- 结果写在开发环境的临时目录，**没有进仓库**；如果会话已结束，结果可能丢失，需要重做。
- 还需完成：整理成 `docs/pk-model.md`（目前这个文件描述的是移植模型，需要改写或拆分）和 `pk-engine/src/main/resources/pk-params.json`，并附"移植参数 vs 文献参数"对照表，交用户审核。**在用户审核前不要改动模型代码。**
- 移植模型的现有参数在 `pk-engine/src/main/kotlin/net/plainnotes/app/pk/Pk.kt`、`Gel.kt`（例如口服 E2 ka 0.32/h，口服 EV ka 0.05/h，舌下分层 θ 0.01/0.04/0.11/0.18，肌注双库房 k3 0.041/h（EU 0.41），凝胶三室，贴片零级/一级输入，CPA 二室）。

## 3. 代码结构

| 模块 | 内容 |
|---|---|
| `app` | 界面（Compose）、ViewModel、导出（CSV/PDF）、应用锁、伪装模式。`src/full`：伪装外壳（计算器、便签）、activity-alias、`DisguiseSection`；`src/play`：空实现 |
| `core/domain` | 纯 Kotlin：给药规则展开、槽位 key（`wall:<ver>@<local>`）、夏令时规则（跳过的时间取第一个有效时刻，重复的时间取较早的偏移）、迟服和漏服判定 |
| `core/data` | Room + SQLCipher、`NotesRepository`、SQL 触发器约束（`SchemaGuards`，每次打开数据库都重建）、只追加的库存流水（`SupplyLedger`）、备份（`DataTransfer.kt`，Argon2id + AES-GCM）、Trans Memo / HRT tracker 写入、数据空间（`Space.PRIMARY` / `DECOY`，诱饵空间是独立的 `notes_b.db`） |
| `core/reminder` | 精确闹钟、直接启动（Direct Boot）缓存、通知（`NotificationPrefs`）、补药通知（`StockAlerts`）。提醒始终只读真实空间 |
| `core/ui` | 主题（`NotesTheme`、对比度） |
| `pk-engine` | 纯 Kotlin 浓度引擎和 EKF 校准（移植自 Transmtf），`UpstreamParityTest` 用 36 个合成场景和上游 TS 输出比对 |
| `importer` | 纯 Kotlin：Trans Memo（`TransMemo.kt`，SQLite `user_version` 8）和 HRT tracker（`HrtTracker.kt`，JSON v2）解析和映射 |
| `tools/pk-reference` | 运行上游 TS 生成对照数据的脚本（含上游原文件副本，许可见 `docs/licensing.md`） |

文档：`docs/PLAN.md`（原始设计和后续补充）、`docs/pk-model.md`、`docs/licensing.md`、`docs/milestones/M1.md`、`README.md`、`THIRD_PARTY_NOTICES.md`。

## 4. 构建与测试

- 工具链：JDK 21、Gradle 9.3.1、AGP 9.1.1、Kotlin 2.2.20、Compose BOM 2025.10.00、compile/target SDK 37。
- 本地需要 `local.properties` 写 `sdk.dir`（已在 .gitignore 中）。
- Robolectric 在无法直连 Maven 的环境里可以离线运行：`-ProbolectricDir=<放 android-all jar 的目录>`（需要 SDK 9、12、15 对应的 jar）。
- 完整检查（提交前必跑）：
  ```
  ./gradlew test lintFullDebug lintPlayDebug          # 加上 -ProbolectricDir=... 如需离线
  ./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test   # CI 的 jvm 任务
  ```
  2026-10-06 之前最后一次全量结果：177 个测试全部通过，lint 0 错误。
- CI：`.github/workflows/android.yml`（jvm、android、device-tests 三个任务）。android 任务把所有 APK 上传为 `build-results` 产物；可安装的是 `apk/full/debug/app-full-debug.apk`。
- 签名：debug 构建固定用仓库里的 `app/debug.keystore`（仅调试用的公开密钥），所以本地和 CI 的 debug APK 能互相覆盖安装。发给用户的 release APK 也用这个密钥手动签名（zipalign + apksigner），以便覆盖安装；**正式发布必须换成私有密钥**。
- 截图测试：`ScreenshotTest`、`ShellScreenshotTest` 输出到 `app/build/screenshots/`。在 Robolectric 里，对话框中的 TextField 在手机尺寸限定符下不会进入空闲状态，所以对话框的交互测试使用默认屏幕尺寸（见 `HtImportWizardTest`）。
- 翻译：新增文字要同时加到 `values`、`values-zh`、`values-b+zh+Hant`、`values-fr`（app、app/src/full、core/reminder 各自的 res 目录），`TranslationsTest` 会检查各语言的键和占位符是否一致。

## 5. 已知限制与注意事项

- 伪装模式下，系统应用列表、系统设置和通知顶部仍显示真实应用名（运行时无法更改），设置页已如实说明。
- 进入系统设置页（例如精确闹钟、电池优化）也会触发伪装模式的立即锁定，只有文件选择器例外（按用户要求）。
- 补药通知在提醒重建时检查（打开应用、服药、闹钟触发、开机等），没有单独的每日定时任务。
- 导入 HRT tracker 时，如果把某组记录导入到参数不同的已有药物（例如把 E2 记录导入到 EV 药物），浓度估算会按该药物的参数计算；界面只给出提示，没有阻止。
- 所有功能都只在 Robolectric 和截图中验证过，没有在真机上做系统测试；用户在自己的手机上测试，并反馈过问题（导入对话框的选项选不了，已修复）。

## 6. 给接手者的工作顺序建议

1. 跑第 4 节的完整检查，修复问题后提交 4.2 的改动。
2. 和用户确认：`gh` 是否已登录（可以发授权 issue）、仓库可见性。
3. 完成或重做 M4a 文献调研，提交 `docs/pk-model.md` 和 `pk-params.json` 交用户审核。
4. 按用户的审核结果决定浓度模型是重写还是补齐，再做蒙特卡洛区间和文献验证测试。
