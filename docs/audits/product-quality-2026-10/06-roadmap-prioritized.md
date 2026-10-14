# 06 分级改进任务与分阶段路线图（阶段 D，2026-10-12）

## 0. 规则

- **P0**：发布阻断。必须有证据，且属于以下之一：数据丢失、严重泄露、医学误导、核心任务崩溃、合规冲突。
- **P1**：下一轮应做。
- **P2**：随后做。
- **P3**：顺手或延后。
- 排期用 **NOW / NEXT / LATER / NOT_RECOMMENDED**。
- 分数（用户价值 V、风险降低 R、成本 C，1–5 分）是**专家主观判断**，只用于排序。
- 工作量：S（≤1 个小 PR）/ M（多文件，需新测试）/ L（需迁移或跨模块设计）。
- 所有任务的状态目前都是“**未实施**”。本审计不修改生产代码。

**为什么“解释性和完整性”排在“新功能”前面**：这个应用的价值在于“可信的纵向记录”。同一事实在页面、PDF、通知中给出不同口径（库存三口径、PR #19 的 PDF/化验估算），或者一个研究性数字被读成个人浓度，都会直接削弱全部数据的可信度。修复这些通常是 S/M 级、不需要迁移；而 LabPanel、附件库等新功能都需要 Schema 和备份格式的变化。

## 1. P0

**没有找到经证实的数据丢失、安全失效、崩溃或医学误导类 P0。** 唯一的 P0 候选是合规问题：

| ID | 标题 | 证据 | 用户问题 | 建议 | 决策 | 成本 | 状态 |
|---|---|---|---|---|---|---|---|
| R-01 | v0.2.0 公开 Release 含未获许可的移植代码 | `docs/licensing.md`：v0.2.0 的 Release/标签/历史仍含移植自无 LICENSE 上游的 `Pk.kt` 等；授权请求“尚未发出” | 公开分发可能侵犯著作权 | 由用户三选一：(a) 发出授权请求；(b) 下架 v0.2.0 附件，改发独立重写后的版本；(c) 维持现状并记录风险 | D-5 | S（文档/Release 操作，不改代码） | 待决策 |

## 2. 任务总表

| ID | 标题 | 模块 | 证据 | 优先级/排期 | 依赖 | 工作量 | V/R/C |
|---|---|---|---|---|---|---|---|
| **R-00** | **解除科研冻结对产品代码的全面锁定**：守卫只锁研究真正依赖的 PK 参数/核/数据，其他文件按 `start_sha` 从 git 历史复算 | tools/pk-research、production-baseline | 05 ARCH-10 | **P1 NOW，绝大多数批次的前置** | **D-13（必须由用户授权，代理不得自行放宽）** | M | 5/4/3 |
| R-02 | 构建可追溯：关于页显示源码 SHA + 构建时间；交付递增 versionCode | build、关于 | 05 ARCH-7 | P1 NOW | D-4（仅 versionCode 部分需要授权） | S | 4/4/1 |
| R-03 | PR #19 合并前一致性：PDF 浓度图、化验冻结估算在 M2-only 下的口径 | export、conc、Labs | 03 F-PK-1/2 | P1 NOW（合并 #19 之前） | D-1、D-2 | M | 4/5/3 |
| R-04 | 浓度页信息层级与文案（单一说明、数字层级、化验点轨道、混合途径说明） | ConcentrationScreen（受保护文件） | 03 F-PK-3/4/5/9/10、02 UX-6 | P1 NOW | D-1；需要追加协议偏离授权 | M | 5/5/3 |
| R-05 | 显示“上次备份”时间 + 可选备份提醒 | 设置 → 数据、reminder | 04 D-3 | P1 NOW | 无 | S～M | 5/5/2 |
| R-06 | 返回键先回日历，再退出 | MainActivity/AppShell | 02 UX-1 | P1 NOW | 伪装行为保持不变 | S | 4/2/1 |
| R-07 | 文档治理：`docs/STATUS.md` 单一事实入口；在 HANDOFF/BACKLOG 过期段落前加指向；开放 PR 分拣 | docs | 05 §5、README §5 | P1 NOW | D-9（关闭哪些 PR） | S | 3/4/1 |
| R-08 | 停止 30 s 全量轮询中的浓度计算；改为按需计算 + 基于失效通知刷新 | NotesViewModel | 05 ARCH-1 | P1 NEXT（先做设备基准） | 需要能跑基准的设备 | M | 3/3/3 |
| R-09 | 库存剩余天数统一为逐槽口径，并在界面写明 | Stock/Calendar/StockAlerts | 02 UX-3 | P2 NEXT | D-3 | M | 4/3/2 |
| R-10 | 首页“今日”摘要卡，或默认日视图 | CalendarScreen | 02 UX-2 | P2 NEXT（先截图核实） | D-7 | M | 4/2/2 |
| R-11 | Trans Memo 临时明文：`finally` 删除 + 启动清理 | NotesViewModel | 04 D-1 | P2 NEXT | 无 | S | 2/3/1 |
| R-12 | 导出明文提示 | DataSection、Visits、Wellbeing | 04 D-2 | P2 NEXT | 无 | S | 3/3/1 |
| R-13 | 日历格语义：TalkBack 能读出日期 + 状态 | CalendarScreen | 02 UX-4 | P2 NEXT | 无 | S～M | 3/2/2 |
| R-14 | 库存卡只显示在用/未开封包装，历史包装折叠，去掉 `#id` | StockScreen | 02 UX-5 | P2 NEXT | 无 | S | 3/1/1 |
| R-15 | 历史页可撤销误跳过 | HistoryScreen、Repository | 02 UX-9 | IMPLEMENTED / OWNER REVIEW | 报告27；最终报告HEAD CI须全绿，旧取消时段/缺证据/跨单位保守限制 | M | 3/2/3 |
| R-16 | 投影异常时显示提示，不再静默空标签 | AppShell、ViewModel | 05 ARCH-3 | P2 NEXT | 无 | S | 2/3/1 |
| R-17 | 时期投影移到 ViewModel 后台计算 | AppShell、Longitudinal | 05 ARCH-2 | P2 LATER | R-08 | M | 2/2/3 |
| R-18 | 伪装暗码强度/限速 | Disguise | 04 D-4 | P2 NEXT | D-8 | S | 2/3/1 |
| R-19 | 对话框状态可保存（`rememberSaveable`） | AppShell | 05 ARCH-4 | P2 LATER | 无 | M | 2/2/3 |
| R-20 | 时间线迁移从轮询中移出，改为一次性步骤 | ViewModel、Repository | 04 D-8 | P2 LATER | `PeriodStabilityTest` | M | 1/3/3 |
| R-21 | 恢复前提供“先导出当前数据”按钮 | DataSection | 04 D-9 | P2 NEXT | 无 | S | 3/3/1 |
| R-22 | 签名私钥离线加密备份 | 发布流程（用户操作） | 04 D-10 | P2 NEXT | 用户 | S | 2/4/1 |
| R-23 | 时间线卡片精简（细节进入详情） | LongitudinalScreen | 02 UX-7 | P2 LATER | 不改时期引擎 | M | 3/1/2 |
| R-24 | 补 UI 测试：日历确认服药、库存对话框、化验录入、返回键、TalkBack 语义 | app/test | 05 §3 | P2 NEXT（与相关任务同批） | — | M | 2/4/2 |
| R-25 | 症状页日期本地化 | WellbeingHub | 02 UX-8 | P3 | 无 | S | 1/1/1 |
| R-26 | 复诊问题删除进回收站 | Repository、Trash | 04 D-7 | P3 | Trash kind 扩展 | S～M | 1/2/2 |
| R-27 | 删除死代码（ImportedPlanDialog、ComingSoon） | app | 05 ARCH-5 | P3 | 检查 releaseAcceptanceTest 引用 | S | 1/1/1 |
| R-28 | 设置拆二级页 | SettingsScreen | 02 UX-10 | P3 LATER | 截图审计重做 | M | 2/1/3 |
| R-29 | 简洁模式失效时提示 | AppShell | 02 UX-12 | P3 | 无 | S | 1/1/1 |
| R-30 | 旋转后不重新锁屏（先真机核实） | MainActivity | 04 D-5 | P3 | 真机 | S | 2/1/1 |
| R-31 | 新增代码行宽规则 | 构建/CI | 05 ARCH-6 | P3 | 不重排受保护文件 | S | 1/2/1 |
| R-32 | 默认分支改名 `main` | GitHub | 05 ARCH-9 | P3 | D-10；同步 CI | S | 1/2/1 |
| R-33 | 协议守卫授权模型重设计 | tools/pk-research | 05 ARCH-8 | P3 LATER | D-12 | M | 1/3/3 |

## 3. 重点任务卡（NOW 批次）

### R-02 构建可追溯

- **现象**：
  - Build 25 对应多个不同源码的私下交付包；
  - 关于页只显示 0.2.0；
  - 用户无法判断手机上装的是哪个包。
- **允许范围**：
  - 在 `app/build.gradle.kts` 增加 `buildConfigField("String","GIT_SHA",…)` 和构建时间；
  - 关于页显示一行“构建 abc1234 · 2026-10-12”。
  - 是否递增 versionCode 由 D-4 决定。
- **不改**：Schema、签名、包名、PK。
- **风险**：
  - 关于页属于 UI 文件，需确认不在协议锁定列表中（`AboutScreen` 位于 `SettingsScreen.kt`；`ui/` 前缀可豁免，名称不含 Conc/Lab/Chart）；
  - 构建在无 git 的环境（如 tarball）要有回退值 `unknown`。
- **验收**：
  - 单测断言 `BuildConfig.GIT_SHA` 非空；
  - 截图确认四语下文本不被截断；
  - unsigned Release 的 `BuildConfig` 中 SHA 与 `git rev-parse` 一致。
- **失败判据**：Release 构建失败，或 SHA 与源码不一致。

### R-03 PR #19 合并前一致性

- **现象**：03 F-PK-1、F-PK-2。
- **允许范围**：只改 `NotesViewModel.exportPdf` / `PdfReport` 的浓度图来源，以及 `LabEstimate.capture` 对舌下 E2 的处理，方式由 D-2 决定。
- **不改**：旧冻结快照、旧 PDF 生成记录、Legacy 参数。
- **验收**：03 §5 第 1、2 条。
- **风险**：`LabEstimate` 冻结格式有 `calculator_version` 语义；新增“舌下不可用”标记需要保证旧版本应用读取时兼容。

### R-04 浓度页层级

- **允许范围**：只改显示层级和文案。可选方向见 07 D-1。
- **验收**：
  - 四语 × 字号 1.0/2.0 截图；
  - 顶部只有一套说明；
  - 化验点与参考曲线在视觉上明确分开；
  - PR #19 的数值合约测试和变异测试全部保持不变。
- **风险**：`ConcentrationScreen.kt` 受协议哈希保护，需要按现行偏离链登记精确 SHA，并有用户的明确授权。

### R-05 上次备份

- **允许范围**：
  - 在 prefs 记录成功导出备份的时间（不属于健康数据；伪装模式下不在外壳显示）；
  - 设置中显示该时间；
  - 可选：超过 N 天后，在应用内显示提示条，不发系统通知，避免泄露。
- **验收**：
  - 备份成功后时间更新，失败时不更新；
  - `wipeAll` 时一并清除；
  - 伪装开关前后外壳中不出现该时间。

### R-06 返回键

- **允许范围**：在 `AppShell` 中加 `BackHandler(enabled = destination != CALENDAR) { destination = CALENDAR }`。复诊详情现有的 `BackHandler` 优先级保持不变。
- **验收**：
  - Robolectric：从化验页按返回回到日历；在日历按返回结束 Activity；
  - 伪装模式下仍退回外壳。

### R-07 文档治理

- **允许范围**：只改文档。
  - 新建 ≤100 行的 `docs/STATUS.md`；
  - 在 HANDOFF 顶部 P2-AX 段落前加“状态已过期，见 STATUS”；
  - 在 BACKLOG 顶部加指向；
  - 列出开放 PR 的处置建议。
- **注意**：不删除历史内容。

## 4. 未来功能评估（§8 候选）

| 候选 | 已有/缺口 | 受益任务 | 前置依赖 | 迁移/权限 | 新风险 | 最小可交付版本 | 建议 |
|---|---|---|---|---|---|---|---|
| 同次采样 LabPanel | 有单项化验 + 冻结上下文；缺采样分组 | S7 录多项化验 | 无 | **需要 Schema**（分组表或 panel_id）+ 备份格式 | 低 | 先做“复制上一条的时间/实验室”的录入快捷方式（无迁移），再做分组 | NEXT（快捷录入）；LATER（分组） |
| 纵向历史 CSV/PDF 与事实摘要 | CSV、PDF、Visit Pack 已有部分 | S8、复诊 | 时期投影 | 无（只读） | 低 | BACKLOG 建议的“时期/阶段专用 CSV” | NEXT |
| 医疗附件安全库 | 无 | 就诊资料 | 备份格式 v2（分块）、临时文件生命周期（R-11 泛化）、加密设计 | **Schema + 备份大改** | 高（隐私/容量） | 先只做设计文档和门槛验收 | LATER |
| 医生/机构档案 | 预约有自由文本医生/地点 | 复诊 | 附件库的信息架构 | Schema | 低 | 预约中医生名字自动补全（无迁移） | LATER |
| 库存口径统一 | 三口径 | S6 | D-3 | 无 | 低 | R-09 | NEXT |
| 注射 mg/mL 与部位历史 | 有部位、轮换建议 | 注射用户 | 药物字段 | Schema | 中（换算错误） | 编辑器中只做显示换算，不存储 | LATER |
| 完整药物修订链 / PK 算法版本回放 | 有快照、方案版本、冻结化验估算 | 审计/复诊 | 策略决定 | 可能需要 Schema | 中 | — | LATER |
| Widget（今日/快速记录） | 无 | S2 | 锁定/伪装策略 | 无 | **高**：桌面可见内容与伪装冲突 | 只在未开启伪装时提供，且显示中性内容 | NOT_RECOMMENDED（当前） |
| Health Connect / Wear OS | 无 | — | — | 权限 | 高：把健康数据交给系统共享存储，违背本地隐私定位 | — | NOT_RECOMMENDED |
| 周期方案、更多途径、T PK | 无 | 少数用户 | 人体证据 | 规则引擎 | PK 需要先做研究 | — | 先研究证据再说，不投产 |

## 5. 建议实施顺序（可独立执行的开发批次）

**前置条件**：除 B1（只改 `build.gradle.kts` 和 `ui/SettingsScreen.kt`，可通过现有 UI 偏离机制登记）和 B5（只改文档）外，其余批次都要修改被“永不豁免”锁定的文件（`NotesViewModel.kt`、`MainActivity.kt`、`Exports.kt`、`LabEstimate.kt`、四种语言的 `strings.xml`、core/data 等）。在 R-00/D-13 决定之前，这些批次无法合规地通过 CI。也不应仿照 PR #19 用新建资源文件来规避锁定。

每个批次都可以单独交给 Codex/Claude Code，并单独验收。所有批次都要求：`:app:testFullDebugUnitTest`（含 `PeriodStabilityTest`）、lint、full Debug/unsigned Release 在 CI 中通过；不改 Schema、PK 参数和签名。

| 批次 | 内容 | 前置决策 | 风险 |
|---|---|---|---|
| **B0 守卫范围调整** | R-00 | D-13 | 中；必须保留全部负向测试 |
| **B1 构建可追溯** | R-02 的 SHA/时间显示部分（不动 versionCode）。若关于页文字需要新字符串，会碰到锁定的 `strings.xml`，可先用不需翻译的纯数据行（SHA·日期） | 无（SettingsScreen.kt 需登记 UI 偏离） | 极低 |
| **B2 数据安全小修** | R-11 + R-12 + R-21 + R-05 | 无 | 低；只有 prefs 新键 |
| **B3 导航与可访问性** | R-06 + R-13 + R-24 中对应的测试 | 无 | 低 |
| **B4 PR #19 一致性与浓度页层级** | R-03 + R-04 | D-1、D-2；协议偏离授权 | 中；涉及受保护文件 |
| **B5 文档治理** | R-07 | D-9 | 极低（不碰锁定文件） |

之后（NEXT）：R-09（D-3）→ R-08（需设备基准）→ R-10（截图核实后）→ R-14/R-16/R-18 小项 → 纵向 CSV。
LATER：LabPanel 分组、附件库设计门槛、R-17/R-19/R-20 架构整理。

**高风险事项必须单独立项并再次授权**：Schema/备份格式变更、PK 模型替换（P2-C HOLD）、签名/版本策略、导航结构改版（方案 B）。
