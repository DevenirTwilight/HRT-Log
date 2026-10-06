# 交接说明：工作进度与开发指南

最后更新：2026-10-06。需求见 `docs/REQUIREMENTS.md`，务必先读第 2 节"硬性规则"。**准备正式发布时，先读本文件第 7 节：已有私有签名备份，必须恢复并沿用，不能重新生成替代密钥。**

## 0. 交接规则（每个接手者都必须遵守）

- AI 看不到用户账户的额度或用量，无法自己判断额度何时用完。因此本文件必须**一直保持最新**：每完成一步就更新本文件并提交、推送，不要攒到最后。
- 用户提醒"额度快用完"或要求交接时：立即停下，先把当前进度、未完成的事、卡点和下一步写进本文件并推送，再做别的。
- 只在后台运行、尚未写入仓库的结果会随会话结束丢失；能保存的及时提交，不能保存的在这里写明。
- 同样的规则写在仓库根目录的 `CLAUDE.md` 和 `AGENTS.md`，供各种 AI 工具自动读取。

## 1. 当前状态一览

| 部分 | 状态 |
|---|---|
| M1 药物、提醒调度、日历 | 完成 |
| M2 历史、库存 | 完成（另加 HRT tracker 风格的历史页和批量补录） |
| M3 身心状态 | 完成 |
| M4 浓度估算、化验 | 引擎移植自 Transmtf；M4a 文献调研已完成并入库，**等待用户审核**（`docs/pk-model.md` 末节） |
| M5 Trans Memo 导入、加密备份、CSV/PDF 导出 | 完成；另加 HRT tracker 导入 |
| M6 应用锁、隐蔽通知、精简模式、多语言、对比度 | 完成 |
| M7 伪装模式（仅 full 变体） | 完成；2026-10-06 的调整已通过发布前全量检查 |
| 日历改版（日/周/月/年视图、库存预计） | 完成 |
| 版本 | 源码 0.3.0（versionCode 3），**已就绪但未发布**；公开 Release 仍是 0.2.0 |

## 2. 当前任务（`docs/REQUIREMENTS.md` 第 4 节）的进度

### 4.1 授权请求：**未完成，被阻塞**
- 2026-10-06 运行 `gh auth status`：未登录（环境里的 GH_TOKEN 无效）。按要求停下并已告知用户，**没有发 issue**。
- 本会话的 GitHub 连接只覆盖 DevenirTwilight/HRT-Log，无法代为在其他仓库发 issue。
- 需要用户登录 `gh` 后再执行，或由用户手动发出。issue 标题和正文在 `docs/licensing.md`，必须原样使用。
- **仓库目前是公开的**，与"授权明确前保持私有"的要求冲突；已告知用户，可见性只有用户能改。（0.3.0 起移植代码和上游副本已从当前分支删除，但仍在 git 历史和 0.2.0 Release 中。）
- `docs/licensing.md` 已写好（来源链、许可状态、issue 文本），发出后补上链接和日期。

### 4.2 补齐小项：代码与发布前全量检查已完成
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

**发布前复检**：`test` 共执行 179 项，177 项通过、2 项 PDF 写入测试因 Robolectric 不支持原生 PdfDocument 而跳过，无失败。`lintFullDebug` / `lintPlayDebug` 均为 0 错误，分别有 64 / 59 条警告；full 与 play release 构建通过，签名后 APK 的包名、0.2.0/versionCode 2、私有签名、16 KB 对齐、非调试构建和无 INTERNET 权限检查均通过。源代码 `fcca559` 的 CI（JVM、Android、API 35 模拟器数据库测试）全部通过。

### 4.3 M4a 文献调研：完成，等待用户审核
- 已启动三个并行调研任务（雌二醇口服/舌下/肌注；透皮凝胶、贴片和 CPA；螺内酯含坎利酮、口服孕酮），要求每条引用都用 PubMed 或说明书核实，没有可靠来源就不填。
- 第一次调研在会话重启时中断，只留下下载的说明书和论文，没有报告。2026-10-06 已重新启动三个调研任务（要求边做边保存）；结果整理后提交到 `docs/pk-research/`，再据此写 `docs/pk-model.md` 和 `pk-params.json`。
  - 已入库：`docs/pk-research/transdermal_cpa.md` / `.json`（凝胶、贴片、CPA；39 条文献、154 个数据点）。贴片和 CPA 数据扎实；凝胶停药后的下降速度、涂抹部位差异、低剂量和隔日 CPA 数据不足。
  - 已入库：`docs/pk-research/spironolactone_progesterone.*`（28 条文献、137 个数据点）。螺内酯数据较扎实（含坎利酮链式模型，但来自口服混悬液、仅印度男性）；孕酮较薄（无群体模型，半衰期无可靠标签值，免疫法比质谱法高约 8 倍）。
  - 已入库：`docs/pk-research/estradiol_oral_sl_im.*`（39 条文献、129 个数据点）。口服 EV、口服 E2 较扎实；舌下和肌注较薄；所有途径都没有找到已发表的房室或群体药代模型和可靠的 ka。
  - 已完成：`docs/pk-model.md` 末节"文献调研（M4a）"（总体结论、各药物数据充分程度、移植模型 vs 文献对照表、需要用户决定的 4 个问题）；`pk-engine/src/main/resources/pk-params.json`（三份调研合并，106 条文献、420 个数据点，引擎尚未使用）。
  - **等待用户审核**。审核前不要改动模型代码。主要差异：口服 17β-E2 表观半衰期（模型约 2.2 h，文献约 14 h）、贴片撕掉后下降（模型 1.7 h，文献 5.9–7.7 h）、舌下暴露（模型约 4.6 倍口服，唯一研究约 1.8 倍）。
- 还需完成：整理成 `docs/pk-model.md`（目前这个文件描述的是移植模型，需要改写或拆分）和 `pk-engine/src/main/resources/pk-params.json`，并附"移植参数 vs 文献参数"对照表，交用户审核。**在用户审核前不要改动模型代码。**
- 移植模型的现有参数在 `pk-engine/src/main/kotlin/net/plainnotes/app/pk/Pk.kt`、`Gel.kt`（例如口服 E2 ka 0.32/h，口服 EV ka 0.05/h，舌下分层 θ 0.01/0.04/0.11/0.18，肌注双库房 k3 0.041/h（EU 0.41），凝胶三室，贴片零级/一级输入，CPA 二室）。

## 2a. 当前工作：按文献重写浓度模型（`REQUIREMENTS.md` 第 8 节，2026-10-06 开始）

**发布约束**：0.3.0 完成后只提交到仓库，**不建 Release、不传 APK、不动 0.2.0 Release 和标签**。发布时必须用第 7 节的私有签名备份，不得生成新密钥；0.2.0 Release 的处理（删除或标为撤回）要先问用户。

分步计划与进度（每完成一步就更新这里）：

**待用户决定（舌下）**：Doll 2022 的三个目标（1 h 达峰、峰值 144、0–8 h AUC 为该研究口服组的 1.8 倍）只能在"吞下的部分约为 0"时同时满足，结果 1 mg 舌下 24 h 后约 0.03 pg/mL，与 Cortez 2024 每天 6.2 mg 时谷值约 95 pg/mL 明显矛盾。目前按用户指示以 Doll 为准实现，8 h 后标"外推"。备选：按质量守恒让大部分剂量走口服吸收（0–8 h AUC 约为口服的 2.8 倍，谷值更合理）。

注意：app 目前只模拟分子为 E2 的药物（`ConcentrationCalculator.compute` 里的 `usable`），CPA、螺内酯、孕酮还没有曲线，步骤 E 需要加多药物显示。

| 步骤 | 内容 | 状态 |
|---|---|---|
| A | 核实 EKF 校准代码的来源 | 完成：无法确认。Transmtf README 只说药代算法、模型和参数来自上游，没有说明 EKF 是自己写的；本地克隆只有 1 个提交，看不到文件历史；上游仓库不在可查看范围内。按规则 **EKF 一起重写** |
| B | 新引擎框架：从 `pk-params.json` 读取模型参数；对外接口（`Pk.simulate` 等）不变 | 新引擎 `Engine.kt` 完成并通过测试（`EngineTest`）：按事件选模型、递推求和、贴片匀速释放、曲线标记、不支持原因；**尚未接入 app**（`Pk.simulate`、`ConcentrationCalculator` 仍用移植引擎），接入要和新校准一起做（步骤 F） |
| C | 口服 EV、CPA（多次服药半衰期变长、约 2 倍蓄积）+ 文献验证测试 | 完成：EV_ORAL、E2_ORAL、CPA_ORAL 已拟合，`LiteratureValidationTest` 通过；已知偏差见 `pk-model.md` 末节 |
| D | 口服 17β-E2、贴片（撕掉后按表观半衰期）、凝胶按产品、戊酸雌二醇肌注（核对约 2 天达峰）、其他酯类 | 完成：拟合并在 `EngineTest` 验证（贴片 Vivelle-Dot 4 个剂量 Cavg、撕掉后 t½ 6.8 h；Divigel 用独立研究 Sirviö 2026 核对；肌注 EV 48 h 达峰）；其他酯类无文献，返回"不支持" |
| E | 舌下（Doll 2022 单档 + 外推标记、舌下 EV 不提供曲线）、螺内酯（母药 + 坎利酮）、孕酮示意曲线 + 化验检测方法 | 拟合完成（E2_SL、SPI_PARENT、SPI_CANRENONE、P4_ORAL）；**舌下有数据冲突，需用户决定**（见下）；界面（检测方法选择、多药物曲线、标记）未开始 |
| F | 新校准（替代 EKF）+ 蒙特卡洛 5%–95% 区间 + 接入 app（多药物曲线、标记、参数和文献、孕酮化验检测方法） | **完成**：`LabFit.kt` + `LabFitTest`；app 接入：`ConcentrationCalculator` 改用 `Engine`/`LabFit`（体重只在有 CPA 时需要，凝胶部位/面积不再必填）；浓度页始终显示 25–75% 与 5–95% 区间、曲线标记说明、"这些药物没有曲线"及原因（舌下 EV 等）、"其他药物"卡（CPA/螺内酯/坎利酮/孕酮各自坐标轴，带"浓度≠抗雄效果"和孕酮示意说明）、"模型与文献"卡（每个模型的 ka、半衰期、CV、假设和文献）；PDF 报告用 5–95% 区间；化验页孕酮分免疫法 `P4_IA`/质谱法 `P4_MS`/不知道 `P4`，分开成图；四种语言字符串；app 测试与截图通过 |
| G | 删除移植代码、上游副本和对照测试；更新 licensing、关于页、NOTICE | **完成**：删除 `Pk.kt`/`Gel.kt`/`Calibration.kt`、`UpstreamParityTest` 及数据、`tools/pk-reference/`、`UPSTREAM_LICENSE`；`Types.kt` 重写，新 `Units.kt`（分子量、单位换算、插值）；编辑页去掉凝胶涂抹范围/面积（模型不用，旧值保留在数据库），部位改为仅记录；关于页、`THIRD_PARTY_NOTICES.md`、`licensing.md`、`pk-model.md` 已更新；全部测试通过 |
| H | 全量测试、版本 0.3.0、提交推送（不发布） | **完成**：版本 0.3.0 / versionCode 3；`-PjvmOnly` JVM 测试、core/data、core/reminder、app（full/play）单元测试共 162 项，0 失败、2 项跳过（PDF 写入，Robolectric 不支持）；full/play debug 与 release 构建通过；lint full 0 错误 63 警告、play 0 错误 58 警告 |

## 2b. 0.3.0 状态（必读）

- **0.3.0 已就绪但未发布。** 按 2026-10-06 的决定：不建 GitHub Release、不上传 APK、不动 0.2.0 的 Release 和标签，直到用户另行通知。APK、签名密钥和密码永远不提交到仓库。
- 用户同意发布时：正式包**必须用第 7 节的私有签名备份签名**，绝不生成新密钥（否则用户无法覆盖安装）。签名后用 `apksigner verify --print-certs` 核对第 7 节的证书指纹。
- 0.2.0 Release 含移植代码：删除、撤回或改说明之前**必须先问用户**。
- 待用户决定：舌下雌二醇 8 小时后的处理（Doll 2022 与 Cortez 2024 谷浓度矛盾，见 `pk-model.md`"已知偏差"）。当前实现按 Doll 校准并标注外推；备选方案是按质量守恒设吞咽比例（AUC0-8 约 2.8 倍口服）。
- 实现中的小决定：编辑页去掉了凝胶"涂抹范围/面积"（新模型不用，数值来自上游，旧数据保留在数据库），部位只作记录；浓度页的体重只在有 CPA 时需要。

## 2c. 用户反馈修复（2026-10-06，`REQUIREMENTS.md` 第 9 节）：完成

- 库存与日历：`NotesViewModel.loadExtra()` 改为最新一次为准（取消旧任务），`sync()`（回到前台时调用）后也重新加载；`CalendarScreen` 和 `loadExtra` 合并计划时先去重再过滤。
- 导入记录：历史页显示"导入"而不是"计划外"；PDF 报告的服药统计多一项"导入"。没有把导入记录自动匹配到计划（来源没有这类信息）。如果用户希望按现有计划自动匹配，需要先确认规则再做。
- 每天几次：`Dialogs.kt` 的 `evenTimes(n)` 和 `TimesPerDayRow`，用于药物编辑页和批量补录；测试 `TimesPerDayTest`。

## 3. 代码结构

| 模块 | 内容 |
|---|---|
| `app` | 界面（Compose）、ViewModel、导出（CSV/PDF）、应用锁、伪装模式。`src/full`：伪装外壳（计算器、便签）、activity-alias、`DisguiseSection`；`src/play`：空实现 |
| `core/domain` | 纯 Kotlin：给药规则展开、槽位 key（`wall:<ver>@<local>`）、夏令时规则（跳过的时间取第一个有效时刻，重复的时间取较早的偏移）、迟服和漏服判定 |
| `core/data` | Room + SQLCipher、`NotesRepository`、SQL 触发器约束（`SchemaGuards`，每次打开数据库都重建）、只追加的库存流水（`SupplyLedger`）、备份（`DataTransfer.kt`，Argon2id + AES-GCM）、Trans Memo / HRT tracker 写入、数据空间（`Space.PRIMARY` / `DECOY`，诱饵空间是独立的 `notes_b.db`） |
| `core/reminder` | 精确闹钟、直接启动（Direct Boot）缓存、通知（`NotificationPrefs`）、补药通知（`StockAlerts`）。提醒始终只读真实空间 |
| `core/ui` | 主题（`NotesTheme`、对比度） |
| `pk-engine` | 纯 Kotlin 浓度引擎（按文献独立编写）：`FittedModels.kt`、`Engine.kt`、`LabFit.kt`（化验校准 + 蒙特卡洛区间）、`Types.kt`、`Units.kt`；参数在 `src/main/resources/pk-params.json`；测试 `LiteratureValidationTest`、`EngineTest`、`LabFitTest` |
| `importer` | 纯 Kotlin：Trans Memo（`TransMemo.kt`，SQLite `user_version` 8）和 HRT tracker（`HrtTracker.kt`，JSON v2）解析和映射 |
| `tools/pk-fit` | `fit.py`：按文献拟合模型参数并写入 `pk-params.json`（纯 Python，可重复运行） |

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
- 签名：debug 继续使用公开的 `app/debug.keystore`，仅供调试。0.2.0 的正式发布使用新生成的独立私有密钥，保存在仓库外，私有备份已按产品负责人授权存入专用私有仓库，位置与恢复步骤见第 7 节；不得上传为公开附件或提交到公开应用仓库。正式包不能覆盖旧调试签名安装，必须先导出加密备份，再换装与恢复。后续正式更新必须沿用同一私有密钥。
- 发布附件工作流：`.github/workflows/release-assets.yml` 根据 `.github/release-assets.json` 从 Git blob 取回已在本地签名的 APK，校验 SHA-256、大小后上传到草稿 Release；工作流不接触签名密钥、不自动公开 Release。这样避免当前开发环境的二进制上传 `Bad Content-Length` 错误。
- 截图测试：`ScreenshotTest`、`ShellScreenshotTest` 输出到 `app/build/screenshots/`。在 Robolectric 里，对话框中的 TextField 在手机尺寸限定符下不会进入空闲状态，所以对话框的交互测试使用默认屏幕尺寸（见 `HtImportWizardTest`）。
- 翻译：新增文字要同时加到 `values`、`values-zh`、`values-b+zh+Hant`、`values-fr`（app、app/src/full、core/reminder 各自的 res 目录），`TranslationsTest` 会检查各语言的键和占位符是否一致。

## 5. 已知限制与注意事项

- 伪装模式下，系统应用列表、系统设置和通知顶部仍显示真实应用名（运行时无法更改），设置页已如实说明。
- 进入系统设置页（例如精确闹钟、电池优化）也会触发伪装模式的立即锁定，只有文件选择器例外（按用户要求）。
- 补药通知在提醒重建时检查（打开应用、服药、闹钟触发、开机等），没有单独的每日定时任务。
- 导入 HRT tracker 时，如果把某组记录导入到参数不同的已有药物（例如把 E2 记录导入到 EV 药物），浓度估算会按该药物的参数计算；界面只给出提示，没有阻止。
- 所有功能都只在 Robolectric 和截图中验证过，没有在真机上做系统测试；用户在自己的手机上测试，并反馈过问题（导入对话框的选项选不了，已修复）。

## 6. 给接手者的工作顺序建议

1. 4.2 已提交并通过发布前完整检查；新增功能继续运行同样的检查。
2. 和用户确认：`gh` 是否已登录（可以发授权 issue）、仓库可见性。
3. 完成或重做 M4a 文献调研，提交 `docs/pk-model.md` 和 `pk-params.json` 交用户审核。
4. 按用户的审核结果决定浓度模型是重写还是补齐，再做蒙特卡洛区间和文献验证测试。

## 7. 正式签名备份：接手者必须知道

2026-10-06，产品负责人创建专用私有仓库并明确授权上传签名备份。已确认仓库为 **Private**，上传后逐字节核对 GitHub 返回的文件与本地 ZIP 一致；ZIP 完整性检查通过。

- 私有仓库：[DevenirTwilight/-](https://github.com/DevenirTwilight/-)（仓库名是单个连字符 `-`，不要误认为链接占位符）。
- 签名备份：[plainnotes-release-signing-backup.zip](https://github.com/DevenirTwilight/-/blob/main/plainnotes-release-signing-backup.zip)。
- 私有恢复说明：[README.md](https://github.com/DevenirTwilight/-/blob/main/README.md)。
- ZIP 含 `release.jks`（PKCS12 私钥）、`password.txt`（密钥库与私钥使用同一密码）及 `README.txt`。公开交接文档只记录位置，不记录密码或私钥内容。
- 应用包名：`net.plainnotes.app`；alias：`release`。这把密钥已签署公开 0.2.0 的 full/play APK；后续所有正式更新必须沿用。
- 证书 SHA-256：`98:9B:A0:45:32:E4:C3:EC:11:C2:DE:98:9D:5B:19:05:CF:67:BD:C6:C4:49:36:12:93:EF:62:B8:A5:93:79:B1`。也可从公开 Release 的 `SIGNING_CERTIFICATE.txt` 核对。

恢复步骤：确认备份仓库仍为 Private → 下载 ZIP → 在公开应用源码仓库外解压 → 从密码文件读取密码签名 → 用 `apksigner verify --verbose --print-certs` 核对证书指纹。不在日志、聊天、公开文档或公开 Release 中输出密钥或密码。

如果接手环境访问私有仓库返回 404/403，让产品负责人把该仓库加入 GitHub 连接可访问范围；**不要因此生成新密钥或改用公开调试密钥**。当前工作区消失不影响恢复；源码开发与单元测试本身也不需要正式签名密钥。产品负责人应额外保存离线副本，并保持备份仓库私有。

签名时使用 `--ks-pass file:password.txt`，同密码的 PKCS12 私钥默认回退即可；不要同时对同一个单行文件设置 `--key-pass file:`，以免重复读取产生 EOF。

公开发布页已增加中文、English、Français 折叠说明；以后修改发布说明，保持下载、换装备份、浓度估算局限与上游授权待确认等关键信息三语一致。
