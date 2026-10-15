# 64 — UI 阶段总结：UI-01、UI-02A、UI-03A、UI-02B（2026-10-15）

本轮界面工作到此结束。医疗文件库界面仍受 B2–B6 安全审批约束，不在本阶段范围内。

| 批次 | PR | 合并提交 | 报告 | 授权 |
|---|---|---|---|---|
| UI-01 界面层级 | #38 | 47bd1b2 | [60](60-ui01-visual-hierarchy.md) | REQUIREMENTS §96 |
| UI-02A 设置子页面与收尾 | #40 | 926a5e1 | [61](61-ui02a-settings-and-wrapup.md) | §97 |
| UI-03A 抽屉按信息来源分组 | #42 | f1d1308 | [62](62-ui03a-drawer-groups.md) | §99 |
| UI-02B NotesApp 状态拆分 | #43 | 94ceb81 | [63](63-ui02b-appshell-state.md) | §99（执行 UI-02 任务书第 3 节） |

每个 PR 都在最终 HEAD 的 6 项必需检查全部成功后普通合并，没有用 `--auto` 或 `--admin`，也没有改 ruleset。合并后都复验了默认分支 CI。

## 1. 各批次做了什么

### UI-01：视觉层级
- 日历首页新增“今天”卡：显示已服 / 应服、下一次服药和“现在服用”主按钮。
- 首页库存卡只在库存偏低或用完时出现。
- 月历格子用 ✓ / ½ / ✕ 加文字双重编码，TalkBack 读完整日期和状态。
- 库存卡只保留一个主按钮，其余操作收进 ⋮；包装列表可折叠。
- 时间线：加事件类型徽标；时区与手机不同时才提示。
- 抽屉首次分组（UI-03A 已重排）。
- 医疗档案首页：下次就诊、“去过了吗？”确认，以及按年份分组。
- 截图：[对比图](../../ui-audit/2026-10-15-ui01/compare/)，改前和改后各 160 张。

### UI-02A：设置子页面与 UI-01 收尾
- 设置改为“总览 + 6 个子页面”，原有各节原样迁入。总览每行显示一句摘要，例如“提醒：全部正常”。
- 首页库存卡可以直接“添加库存”，与库存页用同一个对话框。
- 时间线增加类型筛选（全部 / 化验 / 就诊 / 里程碑 / 其他），屏幕旋转后筛选保留。
- 新增间距和圆角 token（`Spacing`、`Corners`）。
- 删除 5 个已不显示的旧字符串。
- 加入 WCAG AA 对比度断言，并修正暗色低库存卡、“下次就诊”按钮的对比度。
- 截图：[UI-02 截图](../../ui-audit/2026-10-15-ui02/)。底部导航的评估见报告 61 第 6 节。

### UI-03A：抽屉按信息来源分组
- 分组标准统一为“信息从哪里来”。
- 第 3 组标题用“就医资料”，避免与其下的“医疗档案”入口同名、TalkBack 连读重复。
- 4 个入口改名：服药记录、治疗时间线、药物方案、浓度估算。
- AGENTS.md 补齐交付门槛规则，与 CLAUDE.md 一致。
- 截图：[对比图](../../ui-audit/2026-10-15-ui03/compare/)，改前和改后各 192 张。

### UI-02B：NotesApp 状态拆分（纯重构）
- 24 个页面私有的对话框状态收进 6 个 holder（`ui/ShellHosts.kt`）。`remember` / `rememberSaveable` 的语义、对话框的组合位置和顺序都不变。
- 拆分前先补了 5 条跨页面流程测试：通知入口、复核横幅、PK 免责声明、医疗保存失败 snackbar、服药后身心提示。
- 前后 384 张截图逐像素一致，覆盖新抽屉和改名页面。
- 零断言修改。
- 截图证据：[比对说明](../../ui-audit/2026-10-15-ui02b/README.md)。

## 2. 信息架构的最终形态

### 抽屉

```
HRT 日志
  日历
  治疗时间线
日常记录
  服药记录
  身心状态
用药管理
  药物方案
  库存
  浓度估算
就医资料
  医疗档案
  复诊          ← 仍为独立入口（§42）
  化验
──────────
  设置
  关于
```

### 设置

```
设置（总览，每行带摘要）
  ├─ 提醒
  ├─ 外观与语言
  ├─ 身心状态
  ├─ 隐私与安全
  ├─ 数据与备份
  └─ 回收站
```

子页面的顶栏有返回箭头。返回键的顺序是：子页面 → 总览 → 日历 → 系统。

## 3. 测试资产

| 类别 | 测试 |
|---|---|
| UI-01 | TodayCard、HomeStock、StockCardActions、CalendarDayMark、`TimelineAndDrawerHierarchyTest`、MedicalHome |
| UI-02A | `SettingsOverviewTest`、`TimelineFilterAndHomeStockTest`、`Ui02ContrastTest`、`AppShellBackNavigationTest`（新增 5 个设置返回测试）、`HomeAddStockAndroidTest`（设备） |
| UI-03A | `Ui03DrawerTest`（四语 × 320/411dp × 字号 1/2，共 64 例）、`Ui03ShellTitleTest` |
| UI-02B | `NotesAppCrossPageFlowTest`、`NotificationEntryAndroidTest`（设备）、`AfterIntakePromptAndroidTest`（设备） |
| 一直保留的门槛 | `PeriodStabilityTest`（交付门槛）、`DrawerScrollUiTest`、`TranslationsTest`、`ButtonLayoutRegressionTest` |

截图测试默认跳过，设置相应环境变量后运行。输出在 `app/build/<目录>/<label>/`，不含像素断言：

| 环境变量 | 测试类 | 内容 |
|---|---|---|
| `UI01_SHOTS=<label>` | `Ui01ScreenshotTest` | UI-01 各界面，四语 × 尺寸 × 字号 × 明暗 |
| `UI01_CONTRAST=MEDIUM\|HIGH` | 同上 | 中/高对比度主题 |
| `UI02_SHOTS=<label>` | `Ui02ScreenshotTest` | 设置总览和 6 个子页面、首页库存卡、时间线筛选 |
| `UI02_CONTRAST=MEDIUM\|HIGH` | 同上 | 中/高对比度主题 |
| `UI02B_SHOTS=<label>` | `NotesAppShotTest` | 真实外壳：日历、抽屉、10 个页面 |
| `UI03_SHOTS=<label>` | `Ui03ScreenshotTest` | 抽屉、抽屉底部、4 个改名页面 |

示例：`UI03_SHOTS=after ./gradlew :app:testFullDebugUnitTest --tests net.plainnotes.app.Ui03ScreenshotTest`。

注意：只改环境变量时，Gradle 可能认为测试任务已是最新而不运行。这时在测试任务后加 `--rerun`。

对比度断言在 `Ui02ContrastTest` 中：文字 ≥ 4.5:1，徽标和日历标记 ≥ 3:1，覆盖明暗两种主题。

## 4. 仍为 NOT_RUN 的项（负责人在真机上执行）

开发方没有、也不会把下列任何一项标为通过：
- [UI-01 / UI-02 清单](../../ui-audit/2026-10-15-ui02/manual-checklist.md)，第 1–7 节：TalkBack 朗读、最大字号和显示大小、短屏和分屏、输入法、手势与三键返回、伪装和应用锁、删除全部数据（只在测试设备上做）。
- [UI-03A 清单](../../ui-audit/2026-10-15-ui03/manual-checklist.md)，第 8 节：抽屉分组的朗读顺序、改名后顶栏与抽屉一致、320dp 加最大字号时能滚动到“关于”、通知入口和伪装模式下的抽屉。

## 5. 留待负责人决定

1. **底部导航是否立项**：报告 61 第 6 节的评估结论是暂不实施。
2. **复诊何时并入医疗档案**：按 §42 的条件，在此之前复诊保持独立入口。
3. **医疗文件库界面**：等待 B2–B6 的安全审批（B2 NO-GO，B3 NOT_IMPLEMENTED，附件功能开关为 0）。
4. **浓度页的两处旧名称**：“历史”按钮和“可在历史页编辑服药记录”，见报告 62 §3.3。它们属于 PK_DISPLAY_REVIEW 范围，是否授权统一为“服药记录”由负责人决定。

## 6. 致负责人

这一阶段的界面工作已经全部完成。
- 首页有了“今天”卡，月历一眼能看出每天的服药状态。
- 库存和时间线更清楚，医疗档案有了首页。
- 设置拆成总览加 6 个子页面。
- 抽屉按“日常记录 / 用药管理 / 就医资料”重新分组，4 个入口换成更明确的名字。
- 代码内部把外壳的状态拆分整理好了，界面一个像素都没变。

所有改动都只在展示层，没有碰数据库、备份、加密、应用锁、伪装或浓度计算。每一步都有四种语言、两种屏宽、两种字号、明暗主题的自动测试和前后截图。

**需要你亲自做的**：在自己的手机上按上面两份清单逐项检查，并在清单里填写结果。主要是 TalkBack 朗读顺序、系统最大字号、手势和三键返回、伪装模式和应用锁。这些只有真机才能验证，我不会替你标为通过。请用测试数据或先导出加密备份，不要在真实数据上执行“删除全部数据”。
