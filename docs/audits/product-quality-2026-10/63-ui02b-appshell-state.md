# 63 — UI-02B：NotesApp 状态拆分（纯重构，2026-10-15）

- 任务书：UI-03 第 3 节；完整执行 `sources/ui02/ui02-task-book-2026-10-15.md` 第 3 节。
- 分支：`feature/ui02b-appshell-state-20261015`，基于 UI-03A 合并后的主线 f1d1308。

## 1. 做了什么

`NotesApp`（`AppShell.kt`）原来把 24 个页面私有的对话框和临时状态放在函数开头，作为散落的局部变量。现在按页面收进新文件 `ui/ShellHosts.kt` 中的 6 个状态 holder，相应的对话框也提取为同文件里的 composable 函数：

| holder | 原变量 | 原语义 | 提取的对话框函数 |
|---|---|---|---|
| `StockDialogs` | addStock、checkStock、managePackages、restorePackage、undoPackage、packageInfo | remember | `StockPageDialogs` |
| `HistoryDialogs` | editRecord、deleteRecord、batch、batchDone | remember | `HistoryRecordDialogs`、`HistoryBatchDialog`（含 batchDone 的 snackbar `LaunchedEffect`） |
| `LabDialogs` | labEdit、labNew | remember | `LabEditDialog` |
| `MedicalDialogs` | medicalEditing、medicalEdit、medicalLab | remember | `MedicalVisitEditor`、`MedicalLabDetail` |
| `MedicationDialogs` | archive | remember | `ArchiveMedicationDialog` |
| `WellbeingDialogs` | manageItems / **summary** | remember / **rememberSaveable** | — |

- **生命周期不变**：每个 holder 仍在 `NotesApp` 里用 `remember { … }` 只创建一次。
- **`summary` 仍是 rememberSaveable**：`rememberWellbeingDialogs()` 先用 `rememberSaveable { mutableStateOf(false) }` 保存它，再把这个 state 交给 holder。`manageItems` 和以前一样只用 remember，配置变化后照旧复位。
- **组合位置和顺序不变**：对话框仍在原处、按原顺序组合，仍在 `LocalSimpleMode` provider 之外。
- **跨页面共享的状态留在 NotesApp**：completeEntry、overrideEntry、manual、appointment、addMenu、pickStart、disclaimer、destination、visitId、medicalOrigin、importedRecordIds、snackbar、afterIntake。

净变化：`AppShell.kt` 减 61 行、加 33 行；新增 `ShellHosts.kt` 113 行。

未改动：`NotesViewModel`、仓库、数据类、PK 相关文件、字符串、可见布局。保护矩阵上，`AppShell.kt` 是 HISTORY_ONLY；`ShellHosts.kt` 和测试文件不在矩阵中。

## 2. 重构前先补的安全网（提交 2b84800，在拆分前运行）

| 跨页面流程 | 测试 | 重构前 | 重构后 |
|---|---|---|---|
| 复核横幅 → 药物方案页 | `NotesAppCrossPageFlowTest.reviewBannerOpensMedications`（Robolectric） | 通过 | 通过 |
| PK 免责声明首次弹出：拒绝回日历，接受后记住 | `…pkDisclaimerFirstVisitDeclineReturnsToCalendarAcceptRemembers` | 通过 | 通过 |
| 医疗保存失败（编辑器外）显示 snackbar | `…medicalSaveFailureOutsideTheEditorShowsASnackbar` | 通过 | 通过 |
| 通知入口打开服药对话框，且只打开一次 | `NotificationEntryAndroidTest`（设备） | 通过（CI 38799833629，API35） | 见 PR CI |
| **服药后身心提示**：保存服药后出现提示，点操作进入身心状态页 | `AfterIntakePromptAndroidTest`（设备，UI-03 新增要求） | 通过（CI 38799833629，API35，未跳过） | 见 PR CI |

首次推送（c5719af）的 CI 运行里，`AfterIntakePromptAndroidTest` 失败了，原因在测试本身，与应用代码无关：
- 测试点击了对话框标题“完成”（`R.string.complete`），而标题文字没有点击操作；
- 6316d96 改为先确认标题存在，再点击“保存”按钮。这项修正同样在重构之前提交，并单独跑过 CI。

两项设备测试放在设备上，是因为服药对话框含文本输入框，在 Robolectric 里无法到达空闲（`CalendarIntakeAndroidTest` 有同样记录）。PR 的第一次推送只包含安全网提交，用 CI 设备测试证明它们在重构前就通过，之后才推送重构。

## 3. 像素比对

- **结果**：重构前后各 384 张，全部像素一致。详见 `docs/ui-audit/2026-10-15-ui02b/`。
- **覆盖**：
  - 真实外壳 192 张，包括 UI-03A 的新抽屉；
  - `Ui03ScreenshotTest` 192 张，包括抽屉底部和 4 个改名页面。
- **浓度页的修正**：首轮有 2 张浓度页截图不同，原因是截图测试截到了后台计算的不同时刻，与重构无关。修正方法是让截图测试等计算完成再截，然后重新渲染前后两组。没有接受任何差异。

## 4. 测试改动清单（零断言修改）

- **既有测试的断言**：一条都没有改；也没有因 composable 移动而需要修改的 import 或调用签名。
- **新增文件**（`AfterIntakePromptAndroidTest` 的确认按钮在 6316d96 修正）：`NotesAppCrossPageFlowTest`、`NotificationEntryAndroidTest`、`AfterIntakePromptAndroidTest`、`NotesAppShotTest`。
- **`Ui03ScreenshotTest`**（UI-03A 加入的截图证据，默认跳过，没有断言）：拍浓度页前增加一个等待，等后台计算完成。这只是截图时机，不是测试断言。

## 5. 本地验证

重构后在本地运行 21 个类，201 例，0 失败，0 跳过：
- 交付门槛 `PeriodStabilityTest`；
- `NotesAppCrossPageFlowTest`、`AppShellBackNavigationTest`、CalendarCoreFlow、MedicalHomeConfirmFlow、`DrawerScrollUiTest`、TimelineV2Flow、CalendarSelectionRestore、UnifiedConcentration*、`SettingsOverviewTest`、MedicalVisitSaving、DataRefresh、`Ui03DrawerTest`、`Ui03ShellTitleTest`、`TimelineAndDrawerHierarchyTest`；
- 截图测试。

androidTest 编译通过。完整 `:app:testFullDebugUnitTest`、lint、设备测试见 PR 的 CI。

## 6. NOT_RUN 与待决定

- 真机清单没有新增项。纯重构不应有可见变化，UI-01/02/03 清单仍然有效，全部 NOT_RUN。
- 待负责人决定的事项不变，见报告 62 §9 和即将写的 UI 阶段总结。
