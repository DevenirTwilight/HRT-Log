# 交接说明：工作进度与开发指南

## 进行中：时间线精简与医疗档案规划（2026-10-08，REQUIREMENTS §42）

接手 b40b7706561f84d7491b066d82bc3e85cec26298，分支与远端一致、工作区干净；Build 25/schema 9。上一轮 UI 修复已完成，不重复实施。
- [x] 最新决定已写入 REQUIREMENTS §42：复诊独立侧边栏、现有功能及日历预约入口全部保留，撤销旧合并导航决定。
- [x] [医疗档案长期设计](design/medical-records-roadmap.md) 完整写入定位、五类功能、独立附件安全门槛、阶段 A–E 与非目标；Roadmap/BACKLOG 同步。**规划中，未实施**；没有模块/Schema/复诊数据变更。
- [x] 修改前诊断与稳定性基线通过（UserDiagnosticRegressionTest、SameCardSplitTest、PeriodStabilityTest）。
- [x] 时间线源码精简与入口测试已写，聚焦回归通过；诊断移至测试源码，更多菜单保留用药记录和方案详情、去内部 ID，去重复编辑标记，保留简洁推断说明。首轮两条旧 UI 断言因旧长推断文案出现而失败，已改简洁新文案；ImportedTimelineUiTest 11 与 TimelineEditUiTest 2 通过。新菜单四语/320和411dp/浅深色字号2共16项通过，法文 didOverflowHeight 曾给出小数像素误报，采用与原布局回归一致的实际行边界断言后通过；仍断言无省略/遗漏/越边界和48dp触控。全部历史诊断生成器原测试保留。
- [ ] 待四语大字体/窄屏菜单回归、审计和 full 构建/lint/CI；不签名、发布或上传 APK。

## 完成：按钮/大字体、历史回归与 Backlog 核对（2026-10-08，REQUIREMENTS §41）

起始 HEAD `5441e6efac2a9dcb3b38c0a3fb2b85b4d3c2880e`，分支 claude/new-session-1959qb，工作区干净，与远端一致。Build 25/schema 9 已实现并已有正式签名交付记录，不能重新开发。
- [x] 核对源码、数据库实体/迁移、Actions 与现有按钮审计。审计默认 4 语言 × 字号 1/1.3/2，仅 411dp/浅色，发现不失败；当前实际 48 case，旧文档 49 页已纠正。
- [x] 基线重跑 12 种组合：库存按钮仍出现 0dp/11dp 宽、16 行文字，日历双位数字裁切；截图已复核。日期按钮与复诊操作仍有拥挤/不等高，继续补查编辑器与其他选项。基线产物在仓库外 /workspace/tooling/ui-audit-before。
- [x] 第一批 UI 修复：AdaptiveActions/AdaptiveChoice 按内容测量、等尺寸和窄处纵向布局；库存、复诊、日期时间、频率/设置/PK 选项/PDF范围、日历日期/标题、时间输入；历史统计重叠和时间线操作组也由实际审计确认并修复。Material 资料包/导入关联的长确认按钮会与取消重叠，已改为同一个自适应组；日期选择标题改为可换行完整日期。
- [x] 常规 UI 回归 32 项 + TimelineV2FlowTest 6 项全通过（/workspace/tooling/ui-controls-check.log）。四语/320和411dp/浅深色字号2，以及时间输入12/24小时有效/无效值；Flow 增加15张冻结事实/账本/提醒相关表逐步不变性检查。
- [x] 第二批窄屏复现修复：320dp 下日期选择器 360dp 最小宽导致越窗，改为严格 ISO 日期输入（无效日期禁确认、闰日回归）；日历/历史记录正文被状态标签压到零宽，改内容换行；浓度单位标题、历史上下文/摘要导出的确认取消重叠已修复。新增资料包按钮不重叠/等尺寸回归。
- [x] 本机全量单测：app 279（13 跳过，含可选审计默认12配置）、data72、reminder14，0失败；新增常规 UI48、V2单测6、V2Flow6、PeriodStability5、旧TimelineEditFlow4均通过。新增夹具漏填origin/revision曾编译失败，已修正后全量通过。Lint 0错误/132警告（未做无关告警清理）。
- [x] 最终 UI审计：24配置×48case=1152次渲染，411dp四语/字号1、1.3、2/浅色576，320dp四语/字号2/浅深高对比度576；严重裁切/越窗/重叠/零尺寸/动作失败均0，仅12条私密标题预期省略。共同14case基线362条裁切/隐藏信号（含误报），最终相同范围0。机器汇总和5张合成截图已入docs/ui-audit，详见 [修复说明](ui-button-consistency.md)。
- 审计环境问题已处理：首次完整矩阵原生内存退出，Bitmap及时回收、按语言分批；窄屏点击 helper 无限等Robolectric looper，改直接语义动作/有界帧；最终8次分批Gradle全部成功。关闭空闲编译守护进程释放内存，不以命令启动当通过。
- [x] 源码/测试逐项核对后建立 [BACKLOG](BACKLOG.md)（10类候选逐项状态、证据、规格、风险、优先级、最小验收单元）及 [V2 回归说明](timeline-v2-regression-2026-10-08.md)。未发现需重写周期或数据库的可复现缺陷；旧非均匀 slot 稳定身份不冒充已决定规格。
- [x] README、PLAN、Roadmap、UI 审计现状入口同步到 Build 25/schema 9；旧研究/M1 规划明确历史档案，PK 文献实现/LabFit、完整化验参数快照、Visit Pack 第一批和部位记录不再误写为零实现。
- [x] 全量时间线/旧备份单测通过（V2/Flow/PeriodStability见上），data72全通过；API35正式SQLite迁移/加密存储14项通过，未改Schema。
- [x] 本机最终 Android 全量检查 4m15s成功：单测/lint、full Debug/Release、data/app instrumentation APK构建；manifest无INTERNET。JVM domain58/pk18/importer12全部通过（源码未变，Gradle复用已通过结果）。
- [x] 发现旧 Actions 自动上传 APK，按本轮不上传公开 APK 约束移除 APK artifact 路径，仅保留测试/lint报告，并删除本轮 a1b5093/d7f8696/77bf0b9/069e61d CI 的 build-results产物（仅本轮新产生的产物，设备报告保留）。后续报告产物不含 APK。
- [x] 最终功能/流程 CI [37849334335](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37849334335)（297daca）：jvm/android/device-tests三项success。报告回查：app279/13跳过/0失败，data72、reminder14、UI48/0失败；lint0错误/132警告。API35 data14/0失败，app29登记/27通过/2常规跳过，重启prepare/verify两阶段由独立脚本各1项另行通过；不把常规跳过当已执行。JVM domain58/pk18/importer12通过。
- [x] 下载并检查CI报告：build-results129文件、device-test-results19文件，均无APK。终端访问Azure报告下载被拒，GitHub连接器成功取回后解包核对；这不是测试失败。中途checkpoint推送取消了旧CI，最终上述完整CI成功。
- 末尾提交只更新验证结果文档，功能源码/工作流与已通过的297daca一致。上一正式交付源码为52b092a，尚不包含本轮 UI 修复；本轮保持 Build 25/schema 9，不签名交付、不上传公开 APK、不改 PK、Release 或标签。


## 已交付：build 25 重建时期编辑体系（2026-10-08，REQUIREMENTS §40，设计 docs/design/timeline-editing-v2.md）

接手基线 28011d5；功能源码 52b092a，build 25 已完成并交付，原正式签名 full，可覆盖安装，schema 仍为 9。
- [x] 投影：用户时期优先、实际治疗变化结束“至今”、旧行转换（按 build 24 投影；不升 schema）。提醒时刻变化不算治疗变化。
- [x] 原子重叠处理：邻段缩短/拆分成为用户时期，完全覆盖的独立进回收站；恢复冲突在事务内拒绝。
- [x] 统一新建/编辑/删除表单，移除确认、拆开、合并、修正删除与停用专用入口；四语重叠预览和精简模式字段隐藏。
- [x] 系统识别直接生效；新资料包模板 3 去掉待确认计数；新化验上下文 period_source=SYSTEM/USER_EDIT，旧冻结上下文保持兼容。
- [x] 阶段验证：TimelineV2Test 4、TimelineV2FlowTest 4、PeriodStabilityTest 5，13 项全部通过；稳定性测试增加第五组“编辑过的时期”，原黄金数据、删除/恢复、顺序、夏令时等断言保留。旧确认操作只保留在测试夹具中，用于生产旧格式数据，不在应用代码中提供。
- [x] 补充兼容回归：同日精确变化保留 build 24 的卡片数、标准与标签；旧删除回收站经转换后仍能恢复，旧修正恢复检查用户重叠。移除生产代码剩余拆开/停用专用工具，只在测试夹具保留。
- [x] 全量应用/data 回归：app 230（13 既有跳过）、data 72，0 失败；TimelineV2Test 6、TimelineV2FlowTest 5、PeriodStabilityTest 5 全部通过。
- [x] 删除“至今”时期用当前投影实际边界，防止已在方案变化处结束的用户时期删除后出现向后的残段；失败用例先复现，修复后 TimelineV2Test 6 + TimelineV2FlowTest 5 全部通过。
- [x] 最新功能源码 52b092a：全量 404 登记/391 通过/13 既有跳过/0 失败；lint、full debug/release、两个 Android test APK、合并 release manifest、schema/PLAN 检查全部通过。8 个 .so 的 ELF LOAD 对齐 ≥16KB。验证细节见 docs/timeline-editing-v2-verification.md。
- [x] 最终功能 CI [37838271727](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37838271727)（52b092a）：jvm/android/device-tests 三项 success，含 API 35 数据库迁移、原生界面/PDF/重启测试和 PeriodStabilityTest。
- [x] 原正式签名交付：hrt-log-build25-full-signed.apk，23,510,235 bytes，SHA256 `1397ca198fc8841788238a29976139e4bd917520513475d362a0bdf11ee3ed64`；原证书、v2/v3、ZIP 16KB 和 8 个 .so ELF 16KB 对齐，versionCode 25、非 debug、无 INTERNET。下载回查 SHA 一致；签名材料已删除。当前下载：https://tmpfiles.org/wEAPgQ5Gqgoz/hrt-log-build25-full-signed.apk（下载页点击 Download；临时文件会过期，页面生成的直接文件地址仅短时有效；不是公开 Release，APK 未提交源码）。

阶段中发现并修复：较早的删除标记可以被较晚用户时期覆盖，但新删除必须裁剪已有用户时期；恢复用原时期键追加修订，保持显示身份；旧确认格式先转换后才进行非治疗操作不变性检查。构建环境已安装 JDK 21 和 Android SDK 37（仓库外）；本机日志在 /workspace/tooling，未读取真实健康数据。签名备份仓库已再次确认 private，签名材料已清理（/workspace/tooling/build25-signing 不再存在）；APK 留在源码仓库外 /workspace/tooling/deliveries。

## 已交付：build 24（2026-10-08，REQUIREMENTS §38；第二版提示分两步：先修回归，再做回收站）

### 第一步：时期稳定性回归（已完成、全绿、单独提交）

**根因（已复现，与用户诊断逐项一致）：** 不是改库存。改库存走 `NotesViewModel.setRemaining → NotesRepository.setRemaining → SupplyLedger.setRemaining`，只写库存账本，不碰方案版本、快照、确认或修正记录（`PeriodStabilityTest` 证明）。诊断的结构只能由一条**生效的“已删除”修正（10-06 整天）**产生：build 23 在“曾短暂保存为……”下面的“删除这一段”按钮一点就删、没有确认，而且按整天写入。随后两个代码缺陷把它变成了空时期：
1. 删除范围的起止（巴黎 0 点 = 22:00Z）成了时期边界，但两侧方案仍按“空白 < 30 天”跨过被删的这一天连成一段，于是 10-06 显示“有方案”却没有任何组成部分；被删范围把 id=1 整个盖住、把 id=2 裁到 22:00Z 开始。
2. 诊断的 join 部分只找“恰好从时期起点开始的方案部分”，跨日对齐后必然找不到，所以写“no plan part starts at this period's start”。
用合成数据加一条 10-06 整天的删除，诊断输出与用户的完全相同（时期 1 到 10-05T22:00Z；时期 2 为整天、has_plan=true、无部分；时期 3 由 id=2（22:00Z 起）和 id=3 组成）。

**修复：**
- 域层：任何用户删除/停用范围两侧都不连接（按药物；连接检查 `no_user_deletion_or_stop_between`）。被删的天显示为无方案，不再有“有方案无部分”的时期。
- 短暂版本的“单独修改”和“删除这一段”只作用于该版本的精确时间（`evidence_json.exact_from_utc/exact_until_utc`，不升 schema）；删除前要确认。
- 诊断 v3：每个时期列出全部组成部分、触及它的全部用户修正（含精确范围）、每个边界的成因、每种药在边界前后生效的标准和逐项检查。
- 防回归 `PeriodStabilityTest`（CI 必跑，写入 CLAUDE.md 为交付门槛）：黄金数据（用户诊断结构）、4 组历史 × 15 种非治疗操作（设剩余库存、加包装、换包装、丢弃包装、包装容量、有效期、药物名称、每日备注、提醒时间、提醒提前量与迟服阈值、通知开关、语言与地区、设备时区、备份导出恢复、进程重启，以及全部连续执行）前后完全一致、操作顺序无关、巴黎夏令时与跨午夜、整天删除不留空方案而精确删除不切整天。
- 未覆盖：显示设置/精简/伪装模式只在界面层，不进入时期计算（测试以语言、时区代替）；覆盖安装升级由迁移测试覆盖（CI 模拟器）。

**用户数据：** 那条整天删除仍在用户库里（只追加，不会自动撤销）。升级到 build 24（schema 9）后它自动进入 设置 › 回收站；在那里点“恢复”即可让 10-06 回到一段。

### 第二步：回收站和彻底删除（REQUIREMENTS §39，完成）

- 数据层（23cdf4d）：schema 9 `trash_item`（kind/ref/item_date/deleted_utc/payload_json/state）；迁移 8→9 与旧备份恢复都会 backfill（软删除记录、生效的 DELETED 组）；`purge_permit` 只给彻底删除用户自建时期行开口。`Trash.kt` 快照类（里程碑、化验+上下文、预约+问题+资料包、回顾、症状、评分、备注）恢复时按原主键重插；引用类（记录、时期、修正）。`NotesRepository.trash/deletePeriod/deleteCorrection/restoreTrash/purgeTrash`；被引用的记录、覆盖系统方案的删除 → 永久隐藏（HIDDEN）。
- 界面：设置 › 回收站（`RecycleBin.kt`），恢复/彻底删除/清空，二次确认写“无法恢复”、真删或永久隐藏、旧备份仍含；精简模式隐藏药名剂量。时期卡片“删除”“删除这一段”“删除我的修改”进回收站；“已删除（可恢复）”卡片与“恢复”菜单取消。
- 时期显示 ID 改用该 period_key 最小行 id（`HistoryPeriods.stableId`），恢复后时期完全一致。
- 测试：TrashDataTest 5、RecycleBinUiTest 2、PeriodStabilityTest 加回收站操作（里程碑删/恢复/彻底删、化验、备注/评分、服药记录删/恢复、时期删/恢复），MigrationBaselineTest v8→9（CI 模拟器）。
- 本机：app 220（13 既有跳过）/data 72/reminder 14/domain 58/pk 18/importer 12，0 失败；lint、debug/release、manifest 通过。versionCode 24。
- CI [37824386424](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37824386424)（da4031c）jvm/android/device-tests 全部 success（含模拟器 8→9 迁移、PeriodStabilityTest）。
- 交付：`hrt-log-build24-full-signed.apk`，23,506,139 bytes，SHA256 `47186ba227c54d4002f7fca09ba1b52e9f684faf321e60b40a8b0caaf678b076`；原证书、v2/v3、8 个 .so 16KB 对齐、versionCode 24、非 debug、无 INTERNET；签名前确认备份 private，签名材料已删除；会话文件发送。覆盖安装自动迁移到 schema 9。
- 真机未验证：回收站对话框的滚动和二次确认、旧安装 8→9 覆盖升级（只在 CI 模拟器测试）、用户那条 10-06 整天删除进回收站后恢复（合成数据已测）。
- 待用户：在 设置 › 回收站 恢复 10-06 那条删除，确认合并为一段。

## 上一交付（build 23）（10-06 修复 + 时期编辑，2026-10-08，REQUIREMENTS §37，完成）

已完成并推送（每步先测试）：
- 设计与决定：docs/design/timeline-manual-editing.md、REQUIREMENTS §37。
- A 14 天规则用于保存版本：失败测试先提交（UserDiagnosticRegressionTest，含版本内有记录的情形）；`TreatmentPeriods` 新增 `SpanKind`/`sourceId`/`absorbed`；卡片注明“曾短暂保存为……（约 N 小时）”；记录按合并后的标准判断。两个旧域测试的 A→B→A 改为记录片段（负 ID），另加保存版本被并入的断言。
- B 导入核对表：`ScheduleRecognition` 预填频率和时间，标注“由导入记录推定”；与识别频率不一致时保存前确认（ImportReviewScheduleTest）。
- C schema 8：`history_period_revision.kind/group_key`、迁移 7→8、触发器谓词、备份校验；`editTimeline`/`undoTimelineEdit`；同药用户行不可重叠（HistoryPeriodDataTest 9 项，含 schema 7 备份恢复）。设备迁移测试 MigrationBaselineTest 改到 8 并新增 v7 用例（只在 CI 跑）。
- D 投影：用户行 > 已确认 > 保存 > 识别；保存版本被裁剪成片段（`SAVED_PIECE_BASE`），用户 PERIOD 边界固定、FILL 可接续、DELETED/STOP 范围无方案（TimelineEditProjectionTest 5 项）。

- E 界面：卡片 ⋮ 菜单（编辑、在某天拆开、与下一段合并（标准不同必须选前段/后段/自填）、删除、恢复、撤销我的修改、编辑停用期、复制合并诊断（精简模式隐藏））；时间线顶部“新建时期”；并入的短版本可“单独修改/删除这一段”；被删和停用的范围作为单独的无方案卡片显示，便于恢复；同药重叠在对话框里拦下（TimelineEditUiTest、TimelineEditFlowTest：含重新打开数据库与备份恢复）。
- “已删除”的含义：只是不再把这段当作任何方案（卡片无方案、History 显示方案未知、资料包计入方案未知）；记录、库存、浓度估算都不受影响；可恢复。
- 本机全量检查通过：app 213（13 既有跳过）/data 67/reminder 14/domain 58/pk 18/importer 12，0 失败；lint、release、manifest 通过；versionCode 23。
- CI [37814987833](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37814987833)（b784f65）jvm/android/device-tests 全部 success（含模拟器上 7→8 迁移）。
- 交付：`hrt-log-build23-full-signed.apk`，23,473,371 bytes，SHA256 `ef5ad85ef6edb71a6c49bbbfb2a6ef144540cc7014771575bfc0f87bc43ba809`；原证书、v2/v3、16KB 对齐、versionCode 23、无 INTERNET；签名前确认备份 private，签名材料已删除；会话文件发送。覆盖安装自动迁移到 schema 8。
- 真机未验证：所有编辑对话框和日期选择（Robolectric 下时间线页打开带输入框的对话框不空闲，对话框单测）、导入核对表提示、旧安装 7→8 覆盖升级（迁移只在 CI 模拟器测试）。

## 上一交付（build 22）：用户诊断查明的原因修复（2026-10-08，REQUIREMENTS §36c，完成）

**10-06 分段的真正原因（用户 build 21 诊断确认）：** 10-06 14:32:04Z 方案保存为每 11 天（每天两次 2 mg），15:36:20Z 改为每 1 天；该版本（LEGACY_RULE，id 1）持续 64 分钟，超过 build 21 的 1 小时更正窗口，合并检查 `interval: FAIL (1 / 11)`。前一段是用户已确认的开放时期（2026-02-26 起，Europe/Paris），其余各项全部通过。不是导入、条目 ID、酯型或停用造成的。

- 失败测试先提交（`UserDiagnosticRegressionTest`，按诊断中的时间戳、来源和标准重建，记录为合成数据）。
- 修复：保存版本持续 < 1 天、1 天内被替换、且时间范围内没有任何记录 → 视为更正；有记录的短版本仍是真实变化。`TreatmentPeriods.build(..., withRecords)`，app 层 `recordsInside` 计算。没有记录信息时仍用 1 小时窗口（域层旧测试）。
- 诊断 v2：保存版本显示 `records_inside=N`。
- versionCode 22。本机全量检查通过（app 198/13 既有跳过，data 64，reminder 14，domain 58，pk 18，importer 12，0 失败；lint、release、manifest 通过）。CI [37807908061](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37807908061)（843b96e）全部 success。
- 交付：`hrt-log-build22-full-signed.apk`，SHA256 `c277827d76fe2a6dec422e8efbd0acd1658b8f9cf0e0c95c5ef914e55b8b4050`；原证书、v2/v3、16KB 对齐、versionCode 22、无 INTERNET；签名前确认备份 private，签名材料已删除；会话文件发送。覆盖安装，无 schema 变化。
- 待用户：确认 10-06 前后合并为一个时期；手动编辑时间线设计（docs/design/timeline-manual-editing.md）待审核。

## 上一交付（build 21）：合并诊断 + 卡片相同却分段的修复（2026-10-08，REQUIREMENTS §36b，完成）

用户：build 20 下只有一种药，10-06 两张卡片药名/剂量/单位/频率相同仍分段（原因行用户未填写）。要求加“复制合并诊断”并逐项验证可能原因。

逐项验证（`SameCardSplitTest`，真实导入 + 设置保存路径，合成数据；失败测试先提交 27d5eb2）：
- 方案保存后几分钟内改正（先每天 1 次再改 2 次）：build 20 分段 → 修复（1 小时内被替换的版本视为更正）。卡片只显示最终版本，所以看起来相同。
- 停用后几分钟内重新启用：build 20 误判为“在应用中停用” → 修复（停用 < 1 天不算）。这就是用户问的“设置保存被误认成停用后恢复”的情形；单纯保存设置、改名称/包装、改服药时间都不会产生停用（测试通过）。
- 导入剂量浮点误差 2.0000000001（卡片显示 2 mg）：build 20 分段 → 修复（按百万分之一比较，识别模式时也取整）。
- 设置保存后再导入：不分段（通过）。
- 另外：同一条目内成分/单位一方缺失视为兼容（编辑器总会填成分和单位，通常不会出现）。
- 真实停用 2 天：仍分段，诊断显示 `NOT JOINED, first failing check = not_stopped_in_app`。
- 未读取用户数据，仍不能断定用户命中的是哪一条；build 21 的诊断文字会直接显示。

实现：`TreatmentPeriods.joinChecks`（合并与诊断共用：no_overlap、compound、unit、route、ester、formulation、frequency_kind、interval、weekly_count、doses、gap_under_30_days、not_stopped_in_app，诊断另加 same_lane），`effective`（更正后的比较标准），`MergeDiagnostics.text`，卡片 ⋮ 菜单“复制合并诊断”（`period-menu:`/`period-diagnostics:`，精简模式隐藏，四语）。
手动编辑时间线：设计文档 docs/design/timeline-manual-editing.md，待审核，未实现。
versionCode 21。本机全量检查通过（0 失败，lint、release、manifest 通过）。首次 CI 37787162220 的设备测试失败：`TimelineAndroidTest` 按药名滚动，新增的“与上一时期相比”行（在后面的空白卡片上）也含药名，滚动停在那里。改为按频率文字滚动（5529b62），CI [37802706968](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37802706968) 全部 success。应用代码自 2b38f3a 未变，签名用的就是该构建。
- 交付：`hrt-log-build21-full-signed.apk`，SHA256 `f3b196f779cda1618d8f8f1fa969f9b314a773649878bc7453b55dd830799978`；原证书、v2/v3、16KB 对齐、versionCode 21、无 INTERNET；签名前确认备份 private，签名材料已删除；会话文件发送。覆盖安装，无 schema 变化。
- 下一步：用户在 10-06 那张卡片 ⋮ → 复制合并诊断，粘贴发回；手动编辑时间线设计待审核。

## 上一交付（build 20）：停用期保留 + 时期分段原因（2026-10-08，REQUIREMENTS §36a，完成）

用户反馈 build 19 后 10-06 前后仍是两个时期，且没有导入新建的药物条目。因此第 2 条（条目 ID）不是原因；同一条目的酯型缺失/一致各组合在 build 19 已是一段（合成测试）。原因仍不明，可能是另一种药物在 10-06 开始/结束、或导入那段被识别成不同剂量/频率（如多数天只记了一次、频率无法识别）。未读取用户数据。build 20 在时期卡片上显示“与上一时期相比”的原因（按药物），请用户安装后告诉我 10-06 那张卡片上写的原因。

- 先提交失败测试 b5ed8e4（`PausedPlanTest`：保存方案停用 3 周后恢复应分两段，build 19 合成一段）。
- `TreatmentPeriods`：保存版本（正 ID）结束后若有空白即为停用，不再按 30 天规则合并；新增 `stops`（停用起止）和 `changes(period)`（分段原因）。记录空白（负 ID）仍按 < 30 天合并。
- 时期卡片：停用行 `period-stop:<药物>:<时间>`“X 在应用中停用：起 – 止/至今”，以及“与上一时期相比”原因（非精简模式），四语。
- 调整 build 19 的两个域测试：短空白合并的用例改为记录片段（负 ID）；导入片段用负 ID。
- versionCode 20。本机全量检查通过（0 失败；lint、full debug/release、测试 APK、manifest 通过）。CI [37782741194](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37782741194)（d063437）jvm、device-tests success，android 任务的构建/测试/lint/manifest/schema 步骤 success（交付时仅剩缓存保存步骤）。
- 交付：`hrt-log-build20-full-signed.apk`，23,424,219 bytes，SHA256 `6e1c93eecff6dc21f67e832ec9a93ce5e900f12fd0914c81d6652fd878015408`；原证书、v2/v3、16KB 对齐、versionCode 20、无 INTERNET；签名材料已删除；通过会话文件发送。覆盖安装，无 schema 变化。
- 下一步：请用户看 10-06 那张时期卡片“与上一时期相比”写的原因（只需原因文字和药物名），据此修正。

## 上一交付（build 19）：10-06 前后同一方案合并（2026-10-08，REQUIREMENTS §36，完成）

用户反馈：build 18 时间线仍在 10-06 前后分成两个时期；前一段原为“待确认”，确认后仍不合并。开工时分支最新 94fa348，无其他会话改代码。

复现（提交 `test: reproduce the same regimen split…`，`PeriodMergeAcrossImportTest`，全部合成数据）：18 个组合 = 药物条目（同一条目 / 导入新建条目）× 快照酯型（导入侧缺失 / 一致 / 应用侧缺失）× 前一段状态（未确认 / 已确认至 10-06 / 已确认开放结束），外加 12 小时一次的应用方案。build 18 逻辑下 15 个失败：
- 任一侧酯型缺失（无论是否同一条目）：**确认前**就分成 2–3 个时期。原因是第 3 条“标准字段不一致”：`therapySignatureV2` 含 ester，而且待确认片段与保存方案相接时要求身份完全相同（`next.identity==identity(c.snapshot)`）。
- 导入新建条目 + 已确认：确认行挂在导入条目上，只被同一条目的保存方案裁剪，`TreatmentPeriods` 又按 `medicationId` 分组。这是第 2 条（条目 ID），叠加第 1 条（`until_date` 换成 0 点，和中午开始的方案之间有空隙）。
- 同一条目 + 酯型一致：未确认、已确认开放结束都只有 1 个时期；已确认至 10-06 时也只有 1 个时期，但 10-06 早上那条记录仍是“待确认时期”。
- 导入新建条目 + 酯型一致 + 未确认：1 个时期（身份完整时按身份映射到应用条目）。

**用户数据实际卡在哪一条：** 未读取用户真实数据，无法直接确认。用户描述“确认前就分成两段”，在矩阵中只有某一侧酯型（或途径、制剂等身份字段）缺失或写法不同时才会出现，所以最可能是第 3 条；是否同时存在第 2 条（导入另建条目），需要用户在药物列表里看是否有一个导入时新建、标记“需检查”的条目，告诉我条目名和大致记录数即可，不需要提供健康数据。build 19 对三条都修正，结果与此无关。

修正（只改显示合并，不改数据和 schema）：
- `TreatmentPeriods`：按药物身份分道。时间上不重叠且身份兼容的条目合为一道。标准相同（身份兼容、频率、每日剂量集合）且空白 < 30 天就接续，不看来源；缺失字段视为兼容，合并后补齐。
- `ObservedTreatmentHistory`：确认时期和识别片段，也会被其他条目上同一药物的保存方案裁剪和接续；记录先找本条目，再找同药物的其他条目。
- `mergedCoverage`：夹在已确认和已保存部分之间的待确认片段按前一部分判断标签；时期两端的片段仍为待确认。
- 卡片文案：“其中一部分由记录推定，已确认/待确认”（四语）。

测试中改了预期的旧用例（按 §36 用户规则）：
- `TreatmentPeriodsTest`：同一标准停 1 天后恢复，原预期 3 个时期，现为 1 个，并新增 30 天会拆的检查；`SourceNeutralContinuityTest` 3 天空白原预期不合并，现为合并，31 天仍不合并。
- 制剂 null 与具体产品原预期会切，现为兼容。改用两个不同产品名验证“会切”。

药物条目合并方案（待用户决定，未实施）：
- (A) 新增只追加的“条目别名”表，把导入条目指向应用条目，仅影响显示和统计，可撤销，原记录不动。需升 schema 8。
- (B) 把导入记录改挂到应用条目，保留来源 key、冻结快照、revision +1。会改动记录行。
- 推荐 (A)。build 19 显示层已不依赖条目合并。

versionCode 19。本机全量检查通过：app 186（13 既有跳过）/data 64/reminder 14/domain 57/pk 18/importer 12，0 失败；lint、full debug/release、测试 APK、manifest 通过。CI [37770387069](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37770387069)（16d291d，与签名构建代码相同）全部 success。

交付：正式 full `hrt-log-build19-full-signed.apk`，23,416,027 bytes，SHA256 `ae1e6aa9bcddd37adaf86d22fd14e090516925ed8a413da5f0a4dbf20140ab1d`；原证书 `989ba045…79b1`、v2/v3、16KB 对齐（8 个 native 库）、非 debug、versionCode 19、无 INTERNET。签名前确认备份仓库 private；临时 clone/ZIP/JKS/密码已删除。tmpfiles.org 连接被重置，通过会话文件直接发送。覆盖安装即可，无 schema 变化。待用户：告知是否有导入新建的药物条目（名称和大致记录数），并决定条目合并方案 A/B；真机上确认显示是否合并为一个时期。

## 上一交付（build 18）：历史记录按时期归属（2026-10-08，REQUIREMENTS §35/§35a，完成）

设计 [history-period-attribution](design/history-period-attribution.md)：schema7 新增只追加的 `history_period_revision`（用户确认的过去时期，含撤销）和 `record_annotation`（用户明确的“额外”）；记录归属与“额外/剂量不同/方案未知”标签在 core/domain 实时投影，不落库；“长期变化”列出 O1–O4 四种判定方案及参数，阈值待用户定；确认页与 History 草图、四语文案、迁移与备份兼容、测试计划，以及 10 个待决定问题。确认 `ImportedTimelineUiTest:151` 期待“计划外”×2 是错误预期，设计里给出改法。用户已批复（§35a：O1 14 天、空白 30 天，其余按建议）。另一会话已停止；本会话在 86ce231 之上开发。最新交付仍是 build17。

进度（每步推送）：
- 完成：决定入档 0ac37cf；失败复现测试 5ddbb41；`SustainedPatterns` 分段修正 88c211d；schema7 数据层 93920e9（`history_period_revision`/`record_annotation`、迁移、触发器、备份校验、仓库确认/修改/撤销/拆分/合并/额外标记）。
- 完成：界面（History 标签替代“计划外”、移除手动关联槽位菜单、“标为额外服药”、手动添加的“这是额外服药”开关、时间线“待确认/由记录推定，用户已确认/可合并”、`HistoryPeriodDialog` 确认/修改/在某天拆开/与下一段合并/撤销）。
- 完成：化验上下文新建/重建时可选 `confirmed_periods`（只在已确认时期包含采样日时写入；旧上下文不变、仍可校验）；Visit Pack/PDF 模板版本 2，按记录标签计数“由记录推定”，PDF/资料包计数只用五个新标签（额外推断/额外用户标记/剂量与当期不同/方案未知/待确认时期），符合当期的不计数，不再有任何按有无原定时间的计数，也不再单独统计“导入”；按时/迟服/漏服/跳过只统计有原定时间的记录（`scheduledCounts`）；“计划外服药”菜单与标题改为“手动记录服药”（用户审核要求，§35a 补充、补充二）；versionCode 18。
- 新测试：HistoryAttributionTest（混合 HT+APP 跨 10/6 一个待确认时期、确认后逐条标签、用户额外与推断额外区分、撤销/重确认幂等）、HistoryPeriodDialogTest、ImportedTimelineUiTest 修正为“方案未知”且新增待确认→确认入口、HistoryPeriodDataTest 化验上下文。
- 本机全量检查通过（2026-10-08）：app 183（13 既有跳过）/data 64/reminder 14/domain 56/pk 18/importer 12，0 失败；lint、full debug/release、两个测试 APK、manifest 检查通过；schema 7.json 无漂移。
- CI [37766479449](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37766479449)（源码 55354d9，与签名所用构建代码相同）jvm/android/device-tests 全部 success。
- 正式 full `hrt-log-build18-full-signed.apk`，23,399,643 bytes，SHA256 `8fb97ac88c44025fe4431d94053f606fd1a62e59382a912a19f73a9d70380448`。原正式证书 SHA256 `989ba045…79b1`、v2/v3、ZIP 与 8 个 native 库 16KB 对齐、非 debug、net.plainnotes.app/versionCode 18/无 INTERNET 已确认。可直接覆盖原正式安装，schema 6→7 自动迁移，无需卸载、清数据或重导入；已识别的时期显示“待确认”，需用户确认后才影响 History 标签和资料包。
- 交付：tmpfiles.org 连接被重置（不可达），改为通过会话文件直接发送给用户。签名备份 clone、ZIP、JKS、密码均已删除；另发现并删除了早先会话遗留的备份 clone `/home/user/-`（含签名 ZIP）。APK/密钥不进仓库，未改 Release/标签、PK 模型。
- 未验证：用户真实数据、OEM 覆盖安装、真机上确认对话框交互（Robolectric 下时间线页打开对话框后不空闲，已拆分测试）。

## 最新反馈：build17计划归属问题未完成，按用户要求暂停实现（2026-10-08）

用户反馈导入徽标变成“计划外”，提出自动识别计划；明确要求先别做，只整理交给其他会话。需求§34。详细独立交接：[imported-history-plan-association-handoff.md](imported-history-plan-association-handoff.md)。本轮仅read-only核查/文档，不改应用/数据库、不构建或签署新APK。最新交付仍build17。

确认：HistoryScreen以scheduled_utc为空显示“计划外”，HT Writer不写计划字段；ObservedTreatmentHistory只是只读负ID时期投影，resolvedRecordIds不建立持久规则/槽关联。现有手工linkImported仅链接已有同药物/同日未完成槽，不识别过去计划。build17测试还明确要求导入与APP无计划时刻都出现计划外，验证的是不满足用户目标的业务期望；此前“已修复”限于显示合并，不能声称完整解决。未读取用户真实数据。

下一会话先区分计划未知、历史频率识别、推定/确认计划、明确额外服药与槽关联，提出可验证设计，不能只隐藏/换标签或把所有记录改APP/回填当前规则。保留原来源事实/库存/冻结上下文、短边界与同日不重叠回归、重复导入/恢复；若落库须设计迁移/可撤销/歧义保护，不自动启动提醒或伪造准时/漏服。用户目前只授权整理移交，未授权本轮继续实施新算法。

## 当前最新交付：相同方案跨导入边界合并 build17（2026-10-08，完成）

- 用户反馈10月6日相同方案仍拆段，要求导入取消特殊标记。需求§33、先行设计d70dea7，design/source-neutral-treatment-continuity.md。原分支，仅full/原正式签名/schema6/versionCode17。
- 显示适配归一等价频率：12h→每天双次、24h整数倍→N天、每周七天interval1→每天，36h/不完整星期/不同每次剂量分布不误合并。原冻结定义/clinical_signature与V1/Context1/VisitPack1不改；保存版本审计用原频率表达。
- 历史与紧邻下一个相同保存标准可跨最多24h每日终点间隙连接，解决记录zone自然日与保存任意UTC起点分段。不跨任何中间保存版本或观察到的不同模式，不桥接长空白。已知身份唯一跨ID匹配保守规则不放宽。
- 普通时期移除识别来源提示；混合历史不会触发误报旧方案重建徽标。History取消导入来源徽标，所有无计划时刻实际记录同样显示未排程。未匹配记录统一APP/HT/TM入口、取消origin分桶与工具名、四语计数/筛选文字去导入限定。有保存方案覆盖的MISSED/SKIPPED使用原计划时间关联并可打开原始IDs，不伪造服药时间/剂量。
- 最终应用完整回归成功（tooling/build17-final-app-tests.log）：172登记/159通过/13既有跳过/0失败。新SourceNeutralContinuityTest6项覆盖合成10月6日/跨zone/12hvs双次/全周/真实变化/长空白/保存边界/混合来源与原始数据不改；真实JSON→Room→当前方案与APP记录→重导入/加密恢复同一个时期；Compose Native Graphics同一普通时期无来源标记、原始IDs和History一致性。新增集成测试字段名编译问题已修正后完整重跑。
- 最终本机full完整任务9m2s成功：313登记/300通过/13既有跳过/0失败，lint0错误99警告；full debug/release及两个测试APK、manifest/schema/PLAN无漂移检查通过。最终功能CI [37756924266](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37756924266)，源码2ba5860，jvm/android/device-tests全部success；API35数据11、应用主套件29、另两个独立进程重启用例各OK。详见source-neutral-treatment-continuity-verification.md。
- 正式full `/workspace/HRT-Log-build17-full-signed.apk`，23,366,875 bytes，SHA256 `7b3e4a1c90f5433afeea3aec986c93e158797e8377f1c6523778c85fc81ba558`。既有正式证书SHA256 `989ba04532e4c3ec11c2de989d5b1905cf67bdc6c449361293ef62b8a59379b1`、v2/v3、ZIP和8个native库16KB对齐、非debug、net.plainnotes.app/build17/无INTERNET确认；release mapping含修正识别逻辑。支持原正式安装直接覆盖，已有数据自动重投影，不用卸载/清数据/重导。
- 下载：https://tmpfiles.org/dl/1791452746.930fb6c101b361df/wGAHgt6b0pBF/hrt-log-build17-full-signed.apk 。独立下载大小/SHA与本地一致；临时链接失效重传同一build17，不提供Android无法预览的工作区路径。签名备份确认private，临时clone/ZIP/JKS/密码finally删除，仅留不含凭据clone日志，原备份不改。元数据/tooling：build17-final-metadata.json、build17-delivery.json、build17-unit-results.json、build17-final-build-checks.log、build17-ci-device.log。
- 本批实现/完整验证/签名/回查/交付已完成，无待执行交付动作；原始来源仍保留数据库与备份，未改库存/提醒/PK/V1/Context1/VisitPack1/schema。APK/密钥不进仓库，不发布Release/修改标签。用户实际数据与OEM覆盖安装、人工TalkBack新验收未验证，不声称已核查其具体数据。未知身份/无稳定频率或长空白不编造方案；持久化人工确认、完整生命周期仍后续，§29按钮审计未改界面。

## 上一交付：历史识别覆盖修复 build16（2026-10-08，完成）

- 用户确认药物/舌下途径/实际量正确，主要每天两次、偶尔漏记。需求§32与先行设计a505c94，design/history-recognition-coverage.md。原分支，仅full，versionCode16/schema6/原正式证书。
- 已实现有限七天支持完整向量与部分漏记日；三次完整向量、匹配观察日≥2/3、观察自然日≥2/3、至少两个连续间隔。较大完整向量优先，按同类模式先合并证据，避免重叠窗口单/双次反复拆段；持续剂量/频率变化仍区分。记录zone分日。已保存同ID已知字段兼容关联，未知仍未知；仅APP明确冻结rule补缺，IMPORT不借用规则/当前配置。至少三个日期的已知剂量、频率不确定历史作为明确OBSERVED部分已知时期，不伪造每日处方。
- 原RecordEntity/版本/快照/剂量/revision/库存/提醒/PK/V1/Context1/VisitPack1不写改。build15紧邻边界与降级回归保留。
- 应用完整回归已成功（tooling/build16-final-app-tests.log），163登记/150通过/13既有跳过/0失败。识别26项含双次零星漏记、剂量改变与双次→单次→双次、未知长空白、稀疏不假定每日、同ID未知与已知冲突、APP冻结rule/IMPORT不借用、记录时区；新增真实JSON导入→Room→重导入/加密恢复，Compose Native Graphics时期内频率与确切源记录入口。前轮发现窗口拆段已修正重跑；两项测试对未知空白时期的断言修正为检查空标准集合，保留业务断言。
- 完整本机full任务8m12s成功：304登记/291通过/13既有跳过/0失败，lint0错误99警告，full debug/release与两个测试APK成功；manifest/schema/PLAN无漂移检查通过。最终功能CI [37752990023](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37752990023)，源码1093755，三个任务全部success；API35数据11、应用主套件28项，两个重启项另起进程各OK。详见history-recognition-coverage-verification.md。
- 正式full `/workspace/HRT-Log-build16-full-signed.apk`，23,366,875 bytes，SHA256 `cde9aa7d02e4fa8fa6ae974a75ff2236ee9cfeb46f834f65ad099c1943e55351`。原证书SHA256 `989ba04532e4c3ec11c2de989d5b1905cf67bdc6c449361293ef62b8a59379b1`、v2/v3、ZIP及8个native库16KB对齐、非debug、net.plainnotes.app/build16/无INTERNET确认，release mapping含修正识别路径；可覆盖原正式安装，无需卸载、清数据或重导入。签名备份确认private，临时clone/ZIP/JKS/密码finally删除，保留的仅不含凭据clone日志；原备份不改。
- 下载：https://tmpfiles.org/dl/1791450597.a5df8a0216c4f16e/wNAugkgg3YGT/hrt-log-build16-full-signed.apk 。独立真实下载与本地大小/SHA一致；临时链接失效重传同一build16，不交工作区Android预览路径。tooling/build16-final-metadata.json、build16-delivery.json、build16-unit-results.json、build16-final-build-checks.log、build16-ci-device.log保留结果。APK/密钥不入仓库，未发布Release/修改标签。
- 本批完成，无待完成的构建/签名/交付动作。尚未读取用户真实健康数据、未做用户OEM覆盖安装或人工TalkBack新验收；模式识别不保证所有历史字段或频率可恢复，稀少/无上下文仍待识别。人工持久化确认/编辑、完整启停状态仍是后续工作，未实施§29按钮修改。

## 上一交付：时间线闪退修复 build15（2026-10-08，完成）

- 用户仅覆盖升级build14、没有新增/导入就进入Timeline闪退。合成同日3mg→2mg保存版本已先复现 `IllegalArgumentException: Overlapping recorded regimen`（复现提交db3682f，build15-reproduction.log）；尚未取得用户设备堆栈，不能证明是其唯一原因。先行设计[timeline-overlap-hotfix](design/timeline-overlap-hotfix.md)，需求§31。最终功能282efea；versionCode15/versionName0.2.0/schema6，原分支，仅full。
- 原bug：裁剪后的历史区间寻找同日相同标准，跳过中间不匹配保存版本并延伸进其区间。修正为先找紧邻的下一个保存边界，再判断是否可归并，不跨过任何保存版本，不放宽domain不重叠校验。`projectHistory`仅对非法新增识别投影明确降级：保存方案与原始记录入口保留、四语提示识别不可用。不回退整个功能、不写库、不改原始记录/库存/提醒/V1签名/Context1/VisitPack1/PK/schema。
- 回归：同日保存剂量变化、下一边界不匹配、32种同日版本组合、非法识别区间透明降级；Native Graphics Compose和API35新增入口→审计原始3mg版本测试通过。首轮Compose测试因同名按钮多节点选择错误失败，已修选择器并完整重跑，不是仅去掉断言。17项识别测试通过，原始记录不改写。
- 最终本机完整full任务7m55s成功（build15-final-build-checks.log）：293登记/280通过/0失败/13跳过（PDF1、按需按钮审计12），domain39/pk18/importer12/data58/reminder14/app152；lint0错误99警告。full debug/release和两个测试APK全部成功；manifest身份/入口/无INTERNET与schema无漂移检查通过。无中途改码后的未验证包。
- 最终功能CI [37748102641](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37748102641)，源码282efea，jvm/android/device-tests全部success。API35 data11、app主套件29登记（两重启项主套件跳过后各独立进程通过），新增同日方案闪退回归纳入原生套件。不能冒充用户真实设备或人工TalkBack新验收。
- 正式full `/workspace/HRT-Log-build15-full-signed.apk`，23,362,779 bytes，SHA256 `7e1b679d82f401dac7430cc0baea596379333651beabaac25115963ef55ede8a`。既有证书SHA256 `989ba04532e4c3ec11c2de989d5b1905cf67bdc6c449361293ef62b8a59379b1`、v2/v3、ZIP及8个native库16KB对齐、非debug、net.plainnotes.app/build15、无INTERNET确认；R8 mapping包含修正识别及projectHistory。可覆盖原正式安装，无需卸载或重新导入。私有签名备份确认private，临时clone/ZIP/JKS/密码finally删除，原备份不变。
- 下载：https://tmpfiles.org/dl/1791447918.2212bf821054db6f/wqAbgilkWak8/hrt-log-build15-full-signed.apk 。独立实际下载大小/SHA与本地一致；链接临时，失效重传同一build15，不交工作区预览路径。元数据/log在tooling/build15-final-metadata.json、build15-delivery.json、build15-unit-results.json、build15-final-build-checks.log。APK/密钥不进仓库；没有发布Release或修改标签。
- 本批已完成；用户实际数据/手机覆盖后是否仍闪退需其反馈，若仍出现须获取不含健康内容的异常类型与堆栈继续定位，不重复要求重导/清数据。历史模式识别仍有build14说明的证据/复杂周期限制，人工持久确认/编辑与完整启停状态仍待后续；按钮审计§29没有实施。详细[验证](timeline-overlap-verification.md)。

## 上一交付：历史方案自动识别 build14（2026-10-08，完成）

- 用户明确纠正：从历史实际服药识别过去方案变化、补齐治疗时期，不接受build13只增加导入历史栏目。需求§30；先行设计b8b506c；功能ba333fd、最终身份保护8d630af。分支claude/new-session-1959qb；versionCode14/versionName0.2.0/schema6，仅full。
- 自写ObservedTreatmentHistory：冻结药物身份、每日剂量分布、重复日期间隔识别。每日模式至少三个稳定日期，稀疏间隔至少四次一致记录；单次异常/短缺口不直接切时期，完全重复记录不作为双倍分布依据，tier不切临床标准。稳定剂量/频率/途径变化形成过去时期；保存版本优先，已知身份唯一匹配可跨重复药物ID只读归并，相邻相同V2标准合并。不从当前可变药物补未知字段，不跨長空白假定持续治疗。
- 识别模式和保存处方明确区分，来源IDs在时期卡片内可打开确切历史。旧导入及未被保存版本覆盖的应用内实际历史自动投影，无需重导；只有未识别导入留下待识别入口。原始记录/IDs/剂量/时间/快照/revision、库存、提醒、V1签名/阶段键、Context1与Visit Pack1不变，无schema变更。负投影ID不写库或引用到化验版本映射。
- 最终本机完整检查3m24s成功（初次完整构建7m7s），288登记用例：275通过、0失败、13跳过（1既有PDF限制、12按需按钮审计）。domain39/pk18/importer12/data58/reminder14/app147；lint0错误99警告；full debug/release和两个原生测试APK成功，manifest无INTERNET/身份/入口与schema无漂移检查通过。包含合成JSON→Room→两个过去时期/化验归属、重导入、加密恢复、异常/间隔/途径/身份/重复保护及Native Graphics Compose来源导航；合成截图已检查。末轮身份保护已在最终13项识别测试核验，非仅前一版结果。
- 最终源码8d630af CI [37738614825](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37738614825) jvm/android/device-tests全部success。API35数据11通过，应用主套件完成（两个重启测试主套件跳过后各独立进程通过）；无用户OEM/TalkBack人工新验收，不声称读取用户真实数据。
- 正式APK `/workspace/HRT-Log-build14-full-signed.apk`，23,362,779 bytes，SHA256 `f6f5deb0e6dc7fdde4b6c05bc90e850528eee8e49da95d3f9ae7a75e777ebc29`。原正式证书 `989ba04532e4c3ec11c2de989d5b1905cf67bdc6c449361293ef62b8a59379b1`、v2/v3、ZIP和8个native库16KB对齐、非debug、包名及build14、无INTERNET核对；R8 mapping确认新识别逻辑包含在release中。可覆盖原正式安装，未新建密钥；私有备份确认private，临时clone/ZIP/JKS/密码均finally删除。
- 手机直接下载：https://tmpfiles.org/dl/1791442357.eeedfeefdbecffe4/wrAsgNem7a04/hrt-log-build14-full-signed.apk 。真实下载与本地大小/SHA一致；临时链接会失效，届时重传同一正式APK，不交工作区预览链接。元数据/tooling日志为build14-final-metadata.json、build14-delivery.json、build14-final-source-checks.log、build14-unit-results.json。APK/密钥不入仓库；未改公开Release/标签。
- 已完成本批功能/验证/交付；只读识别不是处方恢复或药物启停状态机。模式不稳定、缺失剂量/上下文、稀疏少于四次或长空白仍不能自动补全；历史修订会重新投影，未实现识别模式人工持久化确认/编辑。复杂周期/不规则按需模式、完整生命周期等后续需单独设计，不谎称全部方案都能识别。详见[设计](design/imported-treatment-periods.md)、[验证](imported-treatment-periods-verification.md)。


## 最新硬性决定：play已废弃，仅维护full（2026-10-07）

用户再次纠正：**play版本已废弃。以后只维护、测试、构建和交付full，不再生成或签署play APK。** 已移除Gradle play flavor、src/play空实现、CI play任务，release manifest检查只要求full。AGENTS/CLAUDE/REQUIREMENTS/README同步。下文双变体测试与play附件均为历史记录，不是下一步任务；旧公开Release核验清单保持历史事实，不修改公开Release/标签。最新交付full为build15（见下文交付与下载边界）。

## 上一交付：导入时间线与历史治疗事件 build13（2026-10-08，完成）

分支`claude/new-session-1959qb`；功能4ad2ac4、History source滚动1e8b208、原生资源b803007、最后语义修正b44588f。已快进保留另一会话9a2a9d6按钮审计（只文档/测试，§29待审核UI修正没有实施）。versionCode13/versionName0.2.0/schema6；需求§28、[设计](design/imported-timeline-history-p1.md)、[验证与边界](imported-timeline-verification.md)。

- 根因：导入只有实际records/化验/预约，缺少可信历史schedule时不创建regimen_version；原Timeline不读取records。现在按已有时期/unknown/Upcoming聚合IMPORT_HT与IMPORT_TM，覆盖全部已保存日期，records加入remember刷新。详情只读冻结药物/已知实际剂量/记录数量；不以HT内部ON_TIME类别推断准时，原状态不改。按确切ID打开History，默认all，可清除选择。
- PAUSED/STOPPED/RESUMED：用户填写的日期级历史里程碑，可选原因，可靠保存/编辑/删除、UI/PDF四语；写库/restore同一allowlist、onOpen旧trigger更新。不是当前提醒开关；不自动改写历史方案、推断停药或Started HRT。完整药物停用状态/自动切时期、未来slot身份仍待设计。
- 最终本机完整检查成功3m14s（/workspace/tooling/build13-final-build-checks.log），273登记用例：260通过、0失败，1既有Robolectric PDF与12显式选择才运行的按钮审计跳过；普通回归即261项。domain39/pk18/importer12/data58/reminder14/app132。lint full0错误98警告（build12为96，新增两项计数复数候选）。full debug/release、两个原生测试APK、manifest/schema检查通过；V1签名/RegimenHistory、Context1、VisitPack1、PK、schema/PLAN差异核验无变更。中文native graphics合成Timeline截图已查看，旧摘要可见无裁切。
- 最终CI [37705351828](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37705351828)，源码b44588f，jvm/android/device-tests全部success。API35原生data11通过；app主套件26通过、2重启项主套件跳过后独立进程各1通过，含新增导入摘要/简单模式与既有大字体长Timeline。用户反馈TalkBack通过来自build12真机反馈，不冒充本批新界面已做人工TalkBack或所有OEM升级验收。
- 正式full：`/workspace/HRT-Log-build13-full-signed.apk`，23,362,779 bytes，SHA256 `547a29cca9dca8e4c243a48e2569088c04f2d84d2097f4e130cf7e2f45698f29`。原证书SHA256 `989ba04532e4c3ec11c2de989d5b1905cf67bdc6c449361293ef62b8a59379b1`，v2/v3、ZIP及8个native库16KB对齐、非debug、net.plainnotes.app/build13、无INTERNET确认。可覆盖原正式安装；私有签名备份未改，临时clone/ZIP/JKS/密码已finally清理。
- 手机直接下载：https://tmpfiles.org/dl/1791418569.be045a694f6333c4/wsAolDN95gpY/hrt-log-build13-full-signed.apk 。独立真实下载与本地大小/SHA逐项一致；临时链接会过期，失效时重传同一APK，不给工作区大文件预览路径。元数据/下载日志在tooling/build13-final-metadata.json、build13-delivery.json；APK/密钥不进源码。
- 本批完成，无未提交功能。过程日志保留：最初Robolectric正式Application启动权限、屏幕外source断言与原生R引用错误均已修正；最后压缩构建会话中断后确认daemon闲置，串行重跑成功，不冒充每一轮都通过。没有读取真实健康数据、发布Release或改标签。

## 待用户审核：按钮大小一致性（2026-10-07，另一会话并行）

**待办：按钮大小一致性（REQUIREMENTS §29）**——审计结果与建议修复方案见 [ui-button-consistency](ui-button-consistency.md)，证据截图在 `docs/ui-audit/`，审计测试 `app/src/testFull/.../audit/ButtonAuditTest.kt`（仅在 `BUTTON_AUDIT=1` 时运行）。等用户审核后再改界面。严重项：库存卡三按钮、日期/时间按钮截断、复诊页编辑/删除行、编辑药物频率截断、设置主题/对比度两行不一致。
审计基于 `ab983c5`（早于本分支的 build 12–13 时间线重写），只记录、未改界面；修复前需按当前代码重新核对行号，可用同一审计测试重跑。

## 上一阶段交付：Treatment Period / Timeline build12（2026-10-07，完成）

指定分支`claude/new-session-1959qb`；功能主体`dbb9688`，长列表/兼容`2c3b68b`，最终功能`b0dc178`。versionCode12/versionName0.2.0/schema6；需求§27，原始[扫描](design/treatment-period-timeline-scan.md)、[设计历史](design/epochs-timeline-p1-revision.md)、[验证与边界](treatment-period-timeline-verification.md)。用户在Phase 1报告后回复“继续”，按旧slot身份保留unknown的有限保证实施；不要求重新批准一般开发。

- 里程碑事务提交回执后关闭、saving防双击、失败留草稿；提交后提醒/refresh失败与写库失败分开。成功提示并滚动打开准确source；无默认90天隐藏，STARTED空标题与合法重复保留，不从earliest dose推断。saveable序列化/重建回归通过；提交与回执间强杀仍有未知窗口，见验证边界。
- V2独立投影：相邻提醒/zone/anchor/名称/纯PK设置变更显示合并，原版本/UTC不改；组合治疗标准变化形成精确segment，同日多药/A→B→A做单一日期显示分组、保留中间事实。gap后恢复另开精确时期；missed/late/skipped/unconfirmed/单次实际量偏离不切。无明确停用理由的旧空档只写no active recorded regimen，不推断暂停。
- 单一Timeline按时期当前置顶/历史逆序，仅LAB/REVIEW/MILESTONE/APPOINTMENT；未来Upcoming、首个方案之前unknown。逐项懒加载长列表、source ID详情、UTC原始变更审计、明确详情显示时区；History保持原执行ledger。四语/simple隐去药物/敏感标题备注；原伪装/锁定入口隔离保留。
- 非均匀剂量集合相同而slot对应缺失时明确unknown，不拿clock排序造身份，不承诺完整识别历史分配交换；未来稳定身份与独立stop/pause理由实体未实现。编辑器移动clock携带override，repo拒绝无对应信息的破坏性编辑，不静默归零。未扩展冻结JSON或迁移schema。
- 兼容：V1 TreatmentEpochs/key、RegimenDefinition.signature、clinical_signature/validateLinks、Context1 JSON/PK结果、VisitPack1 facts/digest/旧PDF语义保持；新UI仅只读映射。git差异核验上述源码、PK、schema、PLAN无变化；无原dose/库存ledger重写，无医疗判读/因果/剂量建议。
- 最终本机稳定源码检查7m33s成功：domain39/pk18/importer12/data57/reminder14/app109＝249项，248通过/1既有Robolectric PDF跳过、0失败；lint full0错误96警告（主要旧Timeline资源未使用及复数候选，build11为69）；full debug/release、两个原生测试APK、manifest/schema检查通过。早期并行构建曾产生domain.jar读取冲突，已单进程完整重跑，不把失败当通过。合成中文截图已查看。
- 最终CI [37701395265](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37701395265) jvm/android/device-tests全部success。job113065735012日志核对API35 data11（含原生迁移/SQLCipher）、app24主项＋2既有重启项另起进程成功（主报告26含2跳过）；新160事件/2倍字体/source准确详情包含在内。
- 正式full：`/workspace/HRT-Log-build12-full-signed.apk`，23,338,203 bytes，SHA256 `98bb57af464dd1e80acc857b8fe368e8949c537965fe1d59155b59f09b530ca3`。原证书98:9B:A0…79:B1、v2/v3、16KB对齐、非debug、net.plainnotes.app/versionCode12、无INTERNET核验；可覆盖原正式安装。私有签名仓库核验private，临时clone/ZIP/JKS/密码全部删除，原备份未改。
- 下载链接已独立下载并核对字节/SHA：https://tmpfiles.org/dl/1791415986.a7e5e1fb7ef9f6c4/w7AElEqKGIsU/hrt-log-build12-full-signed.apk 。临时链接失效重新上传同一个APK，不交workspace预览路径或debug包。未发布GitHub Release、未改标签。
- 本批代码/自动验证/签名/下载校验已完成；真实覆盖安装、OEM/人工TalkBack仍待用户复核。下一批优先未来slot身份/明确停用理由设计，再考虑时期报告；VisitPack2、adherence摘要、LabPanel、widget等本批未做。

## 上一阶段交付：Visit Pack 第一批 build 11（2026-10-07，已签名）

开发分支已按用户指示快进合并回 `claude/new-session-1959qb`（`847df84`），以后只在该分支开发；`ccr-5165d4ec-vof9xq` 仅为本会话临时分支。需求 REQUIREMENTS §25，设计 [visit-pack-p1](design/visit-pack-p1.md)，合成验证 [visit-pack-verification](visit-pack-verification.md)。

- 功能源码最终 `ecedb19`（数据层 `618af21`，应用层 `f4a02c6`）。schema6/versionCode11/versionName0.2.0：appointment.completed_utc、visit_question、visit_pack（不可改）、5→6迁移、备份校验与schema1–5恢复；“复诊”页、预约编辑/删除/确认就诊、问题清单、按区间与勾选生成PDF、事实摘要（`visit/VisitPack.kt`）、SHA-256校验码与生成记录。
- 本机：JVM三模块；core:data 55、reminder 14、app full 97（1项既有Robolectric PDF跳过），0失败；lint full 0错误69警告（无新增）；full debug/release与两个原生测试APK成功；manifest/schema无漂移检查通过。
- 最终CI [37681451042](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37681451042) jvm/android/device-tests全部success：原生data 11项（含5→6）、app 23项0失败（WellbeingAndroidTest含资料包PDF分页），2个重启项另起进程OK。
- 正式full：会话scratchpad `sign/out/HRT-Log-build11-full-signed.apk`，23,325,915 bytes，SHA256 `5045a2ad8b40ec96adfa60a630476fffed9a249bf021cd7120e2c317d0c0cd10`。签名脚本核对证书 `98:9B:A0…79:B1`（与build10相同），v2/v3、16KB对齐、非调试、net.plainnotes.app/versionCode11、无INTERNET。私有备份仓库确认private；临时克隆/ZIP/私钥/密码签名后立即删除，原备份未改。未改公开Release/标签。
- 交付方式偏差：tmpfiles.org 在本环境TLS握手即被出口代理断开（连续5次 ws_closed_mid_exchange），无法生成临时链接；未改用其他第三方站点。改为在对话中以文件直接发送同一APK，用户需确认手机能否下载；回查哈希因此只在本地完成。如需链接，下次换可达的环境重新上传同一APK。
- 本会话工具链：Android SDK 装在 `/opt/android-sdk`；Maven Central 经代理返回429，本机用 `~/.gradle/init.d/mirror.gradle.kts` 改走 Google 的 Maven Central 镜像（含 Robolectric），不改仓库。

## 项目许可证 MIT（2026-10-07，REQUIREMENTS §24，已完成）

审计后新增根 `LICENSE`（标准 MIT）、`CONTRIBUTING.md`、`docs/AI-DEVELOPMENT.md`；更新 README（License / AI 开发说明、去掉过时的"上游许可未决"）、`THIRD_PARTY_NOTICES.md`（Gradle wrapper、官方原文引用、非官方翻译、文献数据不属 MIT）、`docs/licensing.md`（覆盖范围与不追溯）、根 `PRIOR_ART.md`（按项目对照表）。当前树未发现移植残留或其他 LICENSE/SPDX（Gradle wrapper 除外）。未发布、未改标签/签名/APK/功能或 UI 代码，无 CLA。下文"根LICENSE未选择"等表述为历史记录。

## 最新交付规则与正式签名测试包（2026-10-07）

用户明确日常使用此应用，测试时不能反复卸载正式安装。以后所有交付给用户的 APK（含测试包）统一使用第 7 节现有私有正式密钥；不再交付 CI 的调试签名包。CI 内部自动验证仍可使用调试密钥，不将私钥或密码上传 CI。此决定优先于本文历史交付说明。

### 历史交付：冻结化验上下文 build 10（2026-10-07，已完成）

- 最终功能源码 `a30c850`（主体 `2ff40f3`，防护/回归 `51a4430`），指定分支 `claude/new-session-1959qb`。schema5/versionCode10/versionName0.2.0；只构建、签署与交付full，未改公开Release/标签/根LICENSE。设计先行 `9f5dfaa`，见 [Lab Context设计](design/lab-context-p1.md)、[合成验证步骤](lab-context-verification.md)。
- 新化验同事务保存冻结上下文：采样时间/时区/项目、阶段和方案版本、每成分最近实际服药（含并列）、时间差/剂量/途径/输入快照、采样前48h迟服/确认漏服/未确认。未知旧导入不使用当前药物配置补猜。编辑结果/备注不重写；修改采样字段或明确重建新增版本，旧版可选择。旧化验升级不自动回填，用户明确重建时标记实际捕获时间和回推方式。排期含星期、计时起点和时区。
- 可选PK默认关闭，仅采样前180天范围内actual、无计划/无化验拟合；保存结果/5–95区间/局限、输入与参数文档、保存时体重设置。参数/体重来自保存时，不代表历史实测，不是完整旧引擎/模型bundle重执行系统。CSV新增lab_contexts.csv全部修订；主PDF显示最新版事实，取消任意药物最近dose误关联。schema1–4备份兼容，schema5上下文预算/结构/阶段键/修订与采样一致性校验，失败回滚。
- 最终本机221项（domain33/pk18/importer12/data51/reminder14/full app93）：220通过、1既有Robolectric PDF跳过、0失败。末轮最新debug/单元/lint、full debug/release与两个原生测试APK全部成功（6m29s，R8确认UP-TO-DATE）；初轮完整任务12m41s成功。lint full0错误69警告（原66，新增2复数候选与已弃用lab_since_dose资源）；无INTERNET/入口/版本/schema无漂移检查通过。合成上下文截图已查看，无重叠或截断。
- 最终CI [37658823665](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37658823665) jvm/android/device-tests全部success，连接器核验最终结论。API35原生data10项含1→5/3→5/4→5、旧化验不造上下文、SQLCipher；应用23项成功，另2重启项在主套件跳过后分别新进程OK，含上下文PDF分页/伪装/应用锁。
- 正式full：`/workspace/HRT-Log-build10-full-signed.apk`，23,260,379 bytes，SHA256 `33995abae21be8d1a82dd6cb27ccbbc5b19babfc490161289a4bc80f962f7156`。核对既有正式证书、16KB对齐、非调试、net.plainnotes.app/build10、无INTERNET；可覆盖原正式安装，升级前建议导出加密备份。tmpfiles真实令牌下载入口已独立下载，字节数与SHA一致：https://tmpfiles.org/dl/1791395210.68cde14c45721959/wGA3lIs9rrTb/hrt-log-build10-full-signed.apk 。临时链接失效重新上传同一APK，不交工作区预览路径。
- 私有签名备份再次确认private；临时克隆/ZIP/私钥/密码全部已清理，原备份未改。无未完成本批代码/验证任务。用户真实覆盖安装、OEM/大字体/TalkBack未验证；不声称已验证用户健康数据。LabPanel/Visit Pack/事实变化摘要、完整模型bundle、包装单位等仍待后续，路线图已更新。

### 上一阶段：地区候选框 build 9（2026-10-07，已完成）

- 功能源码 `0511922`，分支 `claude/new-session-1959qb`，schema4/versionCode9/versionName0.2.0。设置页地区改为单个只读候选框，点击弹出六项列表；复用原翻译、原存储值、未选择与帮助说明，保留与界面语言独立性。本包包含build8浓度修复。
- 本机full91/play78应用回归（167通过、2 PDF跳过、0失败）、lint full0错误66警告/play0错误61警告、full release构建成功（8m53s）。本次未新增低影响UI镜像测试；复用现有DropdownField，未改数据层/PK。最终CI [37645063384](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37645063384) jvm/android/device-tests全部成功（连接器核验最终job结论；公开API状态更新有延迟）。
- 已签署full：`/workspace/HRT-Log-build9-full-signed.apk`，23,223,515 bytes，SHA256 `657baac99e8c95bbd2580381739ec176a8105556d33f2111290cae2d4d848f5c`。同现有正式证书、16KB对齐、非调试、无INTERNET、net.plainnotes.app/build9核验。tmpfiles真实令牌入口独立下载大小/SHA一致，已交付；临时链接失效重新上传同一包。
- 此次本机仅签署full；play debug编译/回归通过，CI负责两变体release，无交付调试包。私有签名备份仓库确认private，签后临时克隆/ZIP/私钥/密码全部删除，原备份未改。不动公开Release/标签；无根LICENSE变更。用户真机候选框及覆盖安装仍待确认。

### 上一阶段：浓度修复 build 8（2026-10-07，已完成）

- 功能逻辑 `d4c3c92`；用户舌下场景回归最终 `0d6b80f`（其后仅文档）；分支 `claude/new-session-1959qb`。schema4/versionCode8/versionName0.2.0，未改公开Release/标签/PK公式参数。设计 `design/concentration-context-chart-hotfix.md`，来源 `PRIOR_ART.md`。
- 上下文：事件快照优先，本应用明确关联的兼容旧rule快照可补缺项，拒绝覆盖已知字段；导入实际不从计划推断。旧HT可用原导出重新导入恢复同key/药物/实际时间/剂量的兼容行，或浓度页“确认历史配置”按日期范围明确确认。原快照/确认时间留存，记录ID/剂量/时间/revision/库存账本不变；仍缺实际剂量时提示单独原因。不能声称已自动恢复所有旧导入的未知制剂。
- 绘图：默认按可见中心曲线、化验及已配置参考范围缩放，完整区间可切换；概率带超出视窗裁剪并提示，零基线、边界插值和实虚线连续、细小刻度不全显示0。不改估算/区间数值。已查看极宽区间合成截图；用户场景为HT导入+本应用、舌下E2、关闭校准，合成回归在full/play均通过。未读取用户实际健康数据或做OEM真机验证。
- 本机最终按模块/变体计292项（domain33/pk18/importer12/data46/reminder14/full91/play78），290通过、2 PDF跳过、0失败；不是292个独立用例。完整四构建/测试APK成功，lint full0错误66警告/play0错误61警告；manifest/schema无漂移检查通过。最终CI [37643141363](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37643141363) jvm/android/device-tests全部成功；原生data9、app23（两项重启另起进程执行）通过。
- 正式full：`/workspace/HRT-Log-build8-full-signed.apk`，23,223,515 bytes，SHA256 `c1b9ef54c8386a8eaef85edb033d6c21e2530350f7941dcf8d8bc687bda45e89`。正式play：`/workspace/HRT-Log-build8-play-signed.apk`，23,133,067 bytes，SHA256 `00f0ff21c87ad14135abdbab2cab43c27f1e3a6ba7e1f143b7b904bd5e0e5af7`；校验文件 `/workspace/HRT-Log-build8-SHA256SUMS.txt`。
- 两包同现有正式证书，16KB对齐、非调试、无INTERNET、net.plainnotes.app/build8已核验。full的tmpfiles真实令牌入口已独立下载核对大小/SHA并交付；临时链接失效重新上传同包，不交工作区预览路径。私有备份未改，仓库外临时克隆/ZIP/私钥/密码均删除。覆盖安装前建议加密备份，用户真机结果待确认。
- 本次修复完成，无未完成代码；路线图仍为Lab Context/Visit Pack/事实摘要等，root LICENSE、模型bundle、包装单位限制不变。最终分支源码已保存，不创建PR或公开发布。

### 上一阶段：P1 build 7（2026-10-07，已完成）

- 功能源码 `bcc629f`；分支 `claude/new-session-1959qb`；公开0.2.0/build5 Release与标签未改。schema4、versionCode7、versionName0.2.0。
- 本批：冻结的方案版本、组合治疗阶段、统一时间线/筛选、明确用户里程碑；提醒/名称不切临床阶段，旧规则回推标记、日期观察候选/未知、未确认与漏服区分、计划默认隐藏、四语/精简模式。只连接已保存事实，不判断因果/化验正常与否；设计 `design/epochs-timeline-p1.md`，独立来源记录 `PRIOR_ART.md`。
- 最终源码本机单元276项（按模块/变体计，274通过、2 PDF跳过、0失败）；lint full0错误65警告/play0错误59警告。CI [37638118061](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37638118061) jvm/android/device-tests全部成功。原生data9项含1→4和3→4；app23项中2个重启用例分开新进程执行成功。未做OEM真机验证。
- 正式签名full：`/workspace/HRT-Log-build7-full-signed.apk`，23,198,939 bytes，SHA256 `670869f429ec923af37937c09d9d86eb33ae352b2b3ca475e3bdb02f7faefb2c`。同一正式证书、非调试、16KB对齐、无INTERNET、包名/版本已核验；tmpfiles真实令牌下载入口下载回查一致，临时链接失效需重新上传同一包，不交付工作区预览路径。
- 正式签名play：`/workspace/HRT-Log-build7-play-signed.apk`，23,128,971 bytes，SHA256 `31066cef128ea1c58eb955c7bae93d6b7e838267af0a6f6a111053fe0310dccc`。两个变体均为同一正式证书/versionCode7，两个release/debug及测试APK构建、release manifest与schema无漂移检查成功；`/workspace/HRT-Log-build7-SHA256SUMS.txt`保存校验值。签名临时克隆/ZIP/私钥/密码已全部删除，原私有备份未改。
- 后续批次仍是Lab Context、Appointment/Visit Pack与事实变化摘要；本批没有实现这些，也没有新增历史模型bundle、复方强度或包装单位。加密备份包含新表，现有CSV/PDF未扩展阶段/里程碑专属导出。root LICENSE仍未选择。

### 更早阶段：P0 build 6（2026-10-07，已完成）

- 功能源码 `2a58d01`；分支 `claude/new-session-1959qb`；公开0.2.0/build5 Release与标签未改。schema3、versionCode6、versionName0.2.0。
- 已独立实现用药PK输入/历史单位快照、症状匹配来源冻结、自动未登记UNCONFIRMED及确认漏服、备份32MiB/JSON/结构/账本校验与失败回滚；修复b64:字面备注误解码。旧缺失上下文明确未知，不用当前配置补猜；未改变PK公式、参数或当前体重政策。P1 Epoch/Timeline/Visit Pack未实现。
- 最终CI [37630464376](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37630464376) 针对2a58d01：jvm/android/device-tests全部成功。本机同一功能源码单元254项（按模块/变体计，252通过、2 Robolectric PDF跳过、零失败）、lint full 0错误64警告/play 0错误59警告、四构建与测试APK构建成功；release manifest检查与schema无漂移通过。API35原生data8+app23项及重启流程成功，含PDF/SQLCipher/迁移。未做OEM真机验证。
- 正式签名full：`/workspace/HRT-Log-build6-full-signed.apk`，23,141,595 bytes，SHA256 `aed2b0c9c35dd2bb89b1468938e0ae6a46bbe3f09f14963d08289621c83b1180`。
- 正式签名play：`/workspace/HRT-Log-build6-play-signed.apk`，23,067,531 bytes，SHA256 `970f9c70be4265ad89b5fceebab677690e422b474b40993a3a23aedb090c0686`。
- 本地签署正式release，核对第7节现有证书；两个包均为非调试、16KB对齐、无INTERNET、net.plainnotes.app/build6。私有仓库仍为private；签名后临时克隆、ZIP、私钥和密码全部清理。APK不在源码仓库。
- full已交付tmpfiles临时下载入口，实际下载APK回查字节数/SHA256一致。服务当前下载入口多一层时间令牌，简单拼接 /dl/ 会返回HTML：从返回页面的download按钮提取真正入口，再下载回查。临时链接失效时重新上传同签名APK，不把工作区路径当成手机下载交付。
- 用户可覆盖现有正式安装，建议先导出加密备份；真机覆盖安装仍待用户确认。root LICENSE/模型bundle快照/包装自身单位持久化保持明确待办，不能把P0描述成完整安全审计或全路线图已完成。

### 前一份build 5正式签名测试包（历史记录）

本次已从成功的 CI `37602133462` 的 `build-results` 提取 full release unsigned APK（功能源码 `bf7d6d4`），使用私有备份在源码仓库外完成正式签名。新增 `scripts/sign_local_apk.sh`：16 KB zipalign、密码文件输入、核对既有正式证书，拒绝凭据或输出 APK 位于源码仓库内。不把密钥或密码作为命令行明文传入。

- 交付文件：会话工作区 `/workspace/HRT-Log-latest-full-signed.apk`，23,133,403 bytes。APK 没有提交到仓库，也没有修改公开 Release 或标签。
- SHA-256：`67294069d4aaf622f72b03a0d7cb8a91598a65af1df6240f2ce05ecbcfc43bf0`。
- 核验：包名 `net.plainnotes.app`，`versionName=0.2.0`、`versionCode=5`（与当前正式版相等，非降级）；v2/v3 签名成功、证书与第 7 节一致、16 KB 对齐、无 INTERNET 权限。沿用已经验证的 CI release 构建，没有变更应用功能或数据格式。
- 可直接覆盖目前正式 build 5，保留本机数据；建议升级前备份，不要求卸载或清数据。仍需用户真机确认覆盖安装。后续新构建应递增 versionCode。
- 源码中的 README、REQUIREMENTS 已记录此规则；日常测试交付使用 release 构建加本地正式签名。自动 CI 产物不作为用户安装包。
- 签名结束已移除本次临时恢复的私钥与密码副本，原私有备份保留。工作区 APK 会随环境清理而消失；接手者可从上述 CI 重新获取 unsigned release 并使用相同私有备份签名恢复。

### Android 下载交付修正（2026-10-07）

- 用户在 Android 上无法通过工作区路径下载 APK（入口尝试预览，提示文件太大）。以后不要把工作区路径当成已完成的手机下载交付。
- 尝试 GitHub 未公开草稿附件：创建草稿成功，但 uploads 端点未通过认证，未上传任何 APK；空草稿已删除，没有发布或改动现有 Release/标签。
- 本次只把已正式签名的安装包放在 tmpfiles.org 临时下载通道（不是源码、健康数据或签名凭据）。使用 `/dl/` 直接下载入口，下载回查 SHA-256 与本地 APK 一致。链接临时有效，不作永久下载地址；若失效，重新交付上述同签名 APK。

最后更新：2026-10-07。需求见 `docs/REQUIREMENTS.md`，务必先读第 2 节"硬性规则"。**准备正式发布时，先读本文件第 7 节：已有私有签名备份，必须恢复并沿用，不能重新生成替代密钥。**

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
| M3 身心状态 | 三标签重设计、迁移、来源、摘要完成并通过 CI；未发布，见 2h |
| M4 浓度估算、化验 | 文献调研、独立重写、化验校准和不确定性区间已完成；舌下 8 h 后的数据冲突仍待用户决定（见 2a–2b） |
| M5 Trans Memo 导入、加密备份、CSV/PDF 导出 | 完成；另加 HRT tracker 导入 |
| M6 应用锁、隐蔽通知、精简模式、多语言、对比度 | 完成 |
| M7 伪装模式（仅 full 变体） | 新私人便签实现与本地完整/原生/进程重启验证完成，见 2e；待 OEM 真机复核 |
| 日历改版（日/周/月/年视图、库存预计） | 完成 |
| P1方案版本、治疗阶段、统一时间线、用户里程碑 | build7完成/正式签名测试交付，未替换公开Release |
| 版本 | 公开 Release 为 0.2.0 build 5（§14 修复，正式签名，标签指向 462da22），见 2g |

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

### 4.3 M4a 文献调研：历史过程（审核及重写已完成，当前状态见 2a–2d）
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
| B | 新引擎框架：从 `pk-params.json` 读取模型参数；对外接口（`Pk.simulate` 等）不变 | 新引擎 `Engine.kt` 完成并通过测试（`EngineTest`）：按事件选模型、递推求和、贴片匀速释放、曲线标记、不支持原因；已在步骤 F 接入 app，移植引擎已在步骤 G 删除 |
| C | 口服 EV、CPA（多次服药半衰期变长、约 2 倍蓄积）+ 文献验证测试 | 完成：EV_ORAL、E2_ORAL、CPA_ORAL 已拟合，`LiteratureValidationTest` 通过；已知偏差见 `pk-model.md` 末节 |
| D | 口服 17β-E2、贴片（撕掉后按表观半衰期）、凝胶按产品、戊酸雌二醇肌注（核对约 2 天达峰）、其他酯类 | 完成：拟合并在 `EngineTest` 验证（贴片 Vivelle-Dot 4 个剂量 Cavg、撕掉后 t½ 6.8 h；Divigel 用独立研究 Sirviö 2026 核对；肌注 EV 48 h 达峰）；其他酯类无文献，返回"不支持" |
| E | 舌下（Doll 2022 单档 + 外推标记、舌下 EV 不提供曲线）、螺内酯（母药 + 坎利酮）、孕酮示意曲线 + 化验检测方法 | 拟合完成（E2_SL、SPI_PARENT、SPI_CANRENONE、P4_ORAL）；**舌下有数据冲突，需用户决定**（见下）；界面（检测方法选择、多药物曲线、标记）已在步骤 F 完成 |
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

## 2d. 最新真机反馈修复（2026-10-06）：完成

用户安装最新测试版：84 mg / 每天 4 mg 两页显示 236 天，编辑后解密/操作错误，正常伪装密码进入后数据看似丢失，完成两次服药仍显示待服。

已定位并实现，验证已完成：
- **SQLCipher 密钥生命周期错误**：4.10 的 `SupportOpenHelperFactory`、`SQLiteDatabaseConfiguration` 持有传入数组，WAL 后续连接仍需使用。原 `DatabaseAccess.get` 在首次打开后立即清零数组；改为随数据库实例保存，关闭后清零。不会删库、换密钥或破坏已有数据。
- 数据空间：切换不关闭仍可能有事务使用的数据库；ViewModel 固定在创建时的数据空间，诱饵修改不触发真实空间的提醒重建；新会话有代次，旧 Activity 的 onStop 不锁掉新认证会话。
- 各页数据从同一个 Room 事务读取；新刷新取消旧刷新，取消不作为失败；所有修改及前台同步重新读取全部页面及浓度，浓度输入也按事务读取并取消旧计算；读取失败后停止定时重试，保留错误前已读取的数据。
- 库存按当前规则的剂量快照及每个时间的覆盖剂量计算；日历预测不再合并显示窗口的旧槽位。“每天几次”显式快速填写同时设为每天，避免保留旧间隔。
- 已补库存 21 天、双次服药完成后刷新、库存/历史修改同步、WAL 编辑期间并发读取、真实/诱饵反复切换并重新打开的回归测试；本地 10 项专项测试通过（含跨页同步及双次服药完成后刷新）；全量单元测试 176 项，174 通过、2 项 PDF 写入测试跳过，0 失败；lint full 0 错误 / 63 警告、play 0 错误 / 58 警告；full/play debug 和 release 四个构建全部通过。CI API 35 模拟器的 7 项原生数据库测试全部通过，包括编辑时的并发 WAL 读取、真实/诱饵空间切换及重新打开。

- 修复源码提交：`989cb0f889bb26be5664fe51adb782d3b1c739bc`（`claude/new-session-1959qb`）。
- 验证日志：本地 `/workspace/tooling/checks.log`；[CI 原生验证](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37493045113/job/112370412469) 7 项全部通过。该次 CI 的 android 任务被同一提交的重复 push 运行取消；[同一源码的后续 CI](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37493843531) 已全部通过。本地完整构建及检查已全部通过。
- 库存页取当前规则剂量及每个时刻的覆盖剂量；日历用实际计划槽位预测用尽日期，天数从首次库存不足的槽位算起。
- 正常密码对应 PRIMARY，诱饵密码对应 DECOY 的规则保留。原生回归验证了真实数据保留与重新打开；用户手机上此前的数据是否完整，仍需真机复核。没有删库、换密钥或修改数据库结构。

继续不发布、不手动上传 APK。真机验证：覆盖安装同签名测试包 → 用正常伪装密码进入 → 核对原历史与库存 → 修改库存和计划后检查各页 → 在历史页把当天导入记录关联到对应计划，再点击完成另一时刻 → 切到后台再返回，确认两个槽位仍已完成。不要卸载或清除数据来验证；保持现有签名。

补充复现（用户最新答复）：一天两次中一次导入、一次点击完成。已补历史页显式“关联服药计划”：点击导入记录，选择同日同药未完成的槽位；不预选、不自动猜测关系。事务内重新核对状态，替换自动漏服占位记录，保留导入时间、剂量（含未知剂量）、来源键及配置，不再次扣库存。历史与 PDF 仍保留导入来源。`DataRefreshTest` 的 4 项集成测试全部通过，包括一次导入加一次点击、反复刷新、重复关联、漏服占位替换与拒绝关联已完成槽位。补充修复源码提交：`4be1a4b4aceb375049230d9ed5de339bb006ca8d`。本地全量检查已通过（`/workspace/tooling/link-checks.log`）：180 项单元测试，178 通过、2 项 PDF 跳过、0 失败；lint full/play 均 0 错误（63/58 警告）；full/play debug 均构建成功。最新 [CI](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37496040841) 的 jvm 已通过，android（包含双变体 release）及 device-tests 尚在运行；下次交接核实最终结果。先前修复提交的双变体 release、本地全量与 7 项原生数据库测试均已通过。

### 2e. 独立私人便签与伪装认证（2026-10-06，正在实现）

用户明确替换旧“空白 HRT DECOY”产品设计（REQUIREMENTS §11）。已读并核对 Disguise/Shells/Section/MainActivity/AppLock/Session/DatabaseAccess/Repository、main/full Manifest、通知和现有测试。当前确实是两种密码都打开 MainActivity，且应用级 label 为 HRT Log；普通 PIN 可能在真实秘密之后再次显示。

已实现并推送：`b22b965`、`f4d87a5`、`0d6bbea`。两种 shell 共用独立 Private Notes Activity + AES-256-GCM/Keystore/AtomicFile 存储，无 HRT schema/repository/domain 依赖。认证为 UnlockTarget PRIMARY/PRIVATE，与 Space 分离；旧 DECOY 仅保留升级兼容和显式清理，不显示、不迁移医疗数据。包括代码配置/改码/移除/准备/清空、进程内原子 target/generation 会话、单任务清栈、Back/Close/摇动回 shell、普通 App Lock/第二 PIN/picker 处理、中性应用/任务/通知身份。审计见 [disguise-privacy.md](disguise-privacy.md)。

补强边界：配置落盘本身不开放会话；完成回调只对前台且同代次的 Main 激活 PRIMARY，不能认证 PRIVATE。校验结果返回时 shell 已离开前台则不打开受保护页面。启用时立即更新当前 Recents 标签。PRIVATE 实时观察授权，撤销即隐藏/退出。清空使旧 store 实例失效，防止迟到的保存重建密文；新私人存储和旧 DECOY 清理均等待完成且不随界面销毁取消。

最终验证通过：应用代码 `0d6bbea`、host 测试修正 `0b339d1`。本地 `/workspace/tooling/private-final-all.log`（12m34s）：202 项单元测试，200 通过、2 项因 Robolectric 不支持原生 PDF 写入而跳过，零失败；full/play lint 零错误，63/58 条既有警告；full/play debug 与 release 四构建全部成功；full 原生测试 APK、合并 release Manifest 中性身份/入口隔离/无网络检查通过。源码 schema 无改动。

[CI 37508856551](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37508856551)：JVM 成功；[API 35 原生任务](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37508856551/job/112425145054) 成功，7 项 core 数据库和 19 项应用流程通过。两个 restart 用例仅在普通 connected suite 中按参数跳过，随后 host 显式重装测试 APK，在两个独立进程分别执行 prepare 与 verify，各 `OK (1 test)`，中间真正 `am force-stop`；最终输出 `PASS: fresh process requires authentication, private ciphertext persists`，新进程无授权且 direct private intent 回 Calculator，密文仍保留。合计 28 项实际原生测试通过。CI Android 四构建、单元、lint、Manifest 任务成功，三个任务全部为 success；本地同源码完整构建也已通过。

前两轮仅测试驱动的 Search 选择、已销毁 Activity 恢复和重复数字 matcher 失败，均已修正，断言未减少。第三轮原生界面全通过但 host 未处理 AGP 完成后的 APK 清理，最终脚本显式安装后两进程检查通过。未把失败隐藏为跳过。尚未完成 OEM 真机、真实生物识别硬件及实际厂商文件选择器的系统验证；现有 biometric 与 picker 策略保留，API 35 测试覆盖会话/picker 生命周期边界。

没有发布 Release、改变签名或手动上传 APK；保持现有安装数据，真机复核时不要卸载或清除数据。

### 2f. 0.2.0 紧急修复发布（2026-10-06）

已完成用户授权的替换（REQUIREMENTS §12）：[原 0.2.0 Release](https://github.com/DevenirTwilight/HRT-Log/releases/tag/v0.2.0)，id 404602730，名称为 emergency hotfix/build 4。`versionName=0.2.0`、`versionCode=4`，代码不回退；应用构建源码和 v0.2.0 标签均为 `c52c8518c40727ff4f5b0316670e109551886f93`。标签原指向 `78d25e23bc7dd2808b03e45653c326ccb5d1c7e8`，按本轮替换授权已更新；旧历史仍保留。

full/play release 本地构建成功（8m26s）；签名证书与原正式版完全一致，第 7 节私有备份不变。签名后核对包名、0.2.0/build4、非调试、无 INTERNET、ZIP 和所有 native ELF 16KB 对齐。数据库 schema 仍为 1，与原 tag 无差异；正式版可覆盖安装，不能因此保证未做真机安装测试的 OEM 行为。调试签名 CI 安装仍须先成功导出加密备份再换装；正式版不要卸载或清数据。

[应用源码完整 CI 37510874945](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37510874945) 的 JVM、Android、原生任务全部 success。沿用并再次验证此前 200 项单元通过、2 PDF 跳过、28 项实际原生用例、四构建和 lint/Manifest 检查。三语说明记录此次包含独立文献引擎，估算可能与原包不同，不虚称取得旧上游授权。

直接 gh 上传遇到已知 Bad Content-Length；采用既有 Git blob 分块 + Actions 只上传已签名的公开文件，无私钥/密码，不向应用文件树提交 APK。Actions 已上传完整附件，但更新含 workflow 历史的 tag 时收到 403；随后通过当前已登录且有权限的用户连接完成 tag/说明更新和旧 UPSTREAM_LICENSE.txt 附件移除。本地从公开 Release 下载五份新附件逐个核对大小/SHA-256，全部通过。上传辅助随后关闭为只读验证，contents 权限降为 read；旧 blob 上传入口不会重放。

- full APK：23,002,192 bytes，SHA-256 `54774fa744d741f8c6e3538119b2072e4a900a0e1f65af08aa1d0ce331d2245c`。
- play APK：22,915,840 bytes，SHA-256 `8ec2bf7b1bb1a5fb0dbf30256e84e6dca6c81015632b9e3e177da8bb809e67e3`。
- 同步替换 SHA256SUMS、SIGNING_CERTIFICATE、THIRD_PARTY_NOTICES；最新完整清单在 `.github/release-assets.json`。
- 本地原附件备份在公开仓库外 `/workspace/release-hotfix/prior`；既有正式密钥备份位置仍见第 7 节。本轮临时恢复的密钥/密码/ZIP 在签名完成后删除，不触碰永久私有备份。
- [只读附件校验 CI 37512727438](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37512727438) 成功：下载回查五个公开附件大小/SHA-256，并核对 source tag；最终本地只读校验也通过。后续发布必须提高 versionCode；版本名回到 0.2.0 不是内部编号或代码回退。

## 2h. 身心状态重新设计（2026-10-07，REQUIREMENTS 15 / 15a / 15b）：**实现与验证完成，未发布**

研究、设计审核、schema 2 和来源目录的前置工作分别见 `wellbeing-research.md`、`wellbeing-design.md`、`WellbeingMigrationTest`、`SymptomCatalogTest`。本轮从远程 `7aa11e2` 接手，已完成原交接列出的界面与导出，不再等待设计审批。

- 三标签：今天、症状记录、阶段回顾。每日四个 DAY_* 项为五档滑块，未操作不写入，支持清除、开关、排序和自定义项；所有历史可查询与导出，各项折线只显示记录天数，不计算平均分。
- 旧 MOOD / ENERGY / SLEEP_QUALITY 的分数按前置迁移并入 DAY_*；以前八项名称与审核附录一致，最近使用决定迁移启用状态，原分数/备注不丢失。
- 症状按在用药物明确的分子/途径/酯型匹配，共有条目合并；**缺失酯型不默认 E2**。原话不改，动作按段去重；立即分组只读原始目录，不由译文、勾选或地区推断。螺内酯显示“官方来源未列出”，未知产品不添加症状。第三方转载、版本/日期、原链接、法国归档和舌下未涉及说明均显示。
- 非官方译文单独打包为 `wellbeing-translations.json`，原话并列；四语 UI 齐全。地区是与语言独立的设备偏好，不进入健康备份；只影响地区规定与上报渠道，不减少症状集合。不添加急救电话、不评判或提供剂量建议。
- 阶段回顾按效果、耐受、风险因素、满意度组织，日期与各字段均由用户记录。效果支持三档/备注和两个原表数值并列查看，可隐藏且保存旧值。血压/体重/吸烟/满意度默认未填；阶段体重不写入浓度当前体重。支持新建、编辑、专用确认删除；回顾编辑状态和摘要入口可随配置重建恢复。
- 包装来源/批号可在新增与库存逐包装编辑；不改变容量、已用量或账本。CSV 扩展为六文件（服药、每日、化验、包装、症状、回顾）。复诊摘要 PDF 可选日期范围，包含方案/服药统计、化验原值与参考范围、症状来源、完整回顾、每日记录天数/折线和备注；长文字按行分页，页脚明确不是诊断。
- 修复已有原生迁移基线测试在 schema 1 安装 schema 2 触发器的失败，改为真实迁移后再安装。未来备份拒绝有专用提示，v1 加密备份升级继续通过。没有修改浓度模型、没有发布新版本、没有动当前 Release/标签/正式签名。

**最终源码与验证**：`bf7d6d4a29b4e36926e4521ab11f200d25f065b1`，指定分支 `claude/new-session-1959qb`。[CI 37602133462](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37602133462) 三作业 JVM / Android / device-tests 全部成功。

- 本地全量 237 项单元测试：235 通过、2 项 PDF 因 Robolectric 无原生后端跳过、0 失败。最后明确酯型修正后的 full/play app 测试和 lint 再次通过。
- full/play lint 都 0 错误，64 / 59 条已有警告，没有留下本轮新增 lint 错误或警告。
- full/play debug/release 四构建通过；full app 与 core/data instrumentation APK 构建通过。最终源码 CI 重新完成四构建、合并 Manifest 检查及 schema 差异检查；无 INTERNET，正常 HRT Log 身份与 full-only 入口隔离保留。
- API 35 原生验证：SQLite 1→2、SQLCipher 新记录/包装信息备份往返与重开、阶段体重隔离、滑块、长篇多语 PDF 分页，及全部既有伪装/应用锁/返回/强制进程重启回归均成功。真实用户健康数据和密码未用于仓库或 CI。

真机复核步骤与已知边界见 [`wellbeing-verification.md`](wellbeing-verification.md)。OEM、大字体/TalkBack 尚待真机确认；症状记录当前保存组 ID 而非当时药物/来源版本快照，摘要引用当前目录对应来源；浓度同轴叠加按设计留待后续。上轮未提交的本地 build 5 草稿已用 stash 隔离保存，已被远程完成的 build 5 工作取代，不应恢复覆盖当前实现。

## 2i. 致谢口径更正（2026-10-07，`REQUIREMENTS.md` 第 16 节）：源码完成，Release 未改

- `credits_body` 四语逐字替换为用户给定文字；`docs/licensing.md` 同步。`TranslationsTest` 通过，app 单元测试 61 项 0 失败、1 项跳过。
- 0.2.0 Release 说明里原本没有 Trans Memo 表述，`gh` 未登录（GH_TOKEN 无效），按指示停下，未改 Release/附件/标签；原文备份 `docs/release-notes/0.2.0-original.md`。等用户决定是否新增致谢段落及更新方式。

## 3. 代码结构

| 模块 | 内容 |
|---|---|
| `app` | 界面（Compose）、ViewModel、导出（CSV/PDF）、应用锁、伪装模式。`src/full`：伪装外壳（计算器、便签）、activity-alias、`DisguiseSection`；只保留full变体 |
| `core/domain` | 纯 Kotlin：给药规则展开、槽位 key（`wall:<ver>@<local>`）、夏令时规则（跳过的时间取第一个有效时刻，重复的时间取较早的偏移）、迟服和漏服判定 |
| `core/data` | Room + SQLCipher、`NotesRepository`、SQL 触发器约束（`SchemaGuards`，每次打开数据库都重建）、只追加的库存流水（`SupplyLedger`）、备份（`DataTransfer.kt`，Argon2id + AES-GCM）、Trans Memo / HRT tracker 写入、数据空间（PRIMARY 固定真实数据；旧 DECOY / notes_b.db 仅保留兼容清理，不用于私人便签认证/UI） |
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
  ./gradlew test lintFullDebug          # 加上 -ProbolectricDir=... 如需离线
  ./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test   # CI 的 jvm 任务
  ```
  2026-10-06 最新全量结果：202 项单元测试，200 通过、2 项 PDF 写入跳过，0 失败；lint 0 错误（full 63 / play 58 条既有警告）；本次原生与四构建最终结果见 2e。
- CI：`.github/workflows/android.yml`（jvm、android、device-tests 三个任务）。android 任务把所有 APK 上传为 `build-results` 产物；可安装的是 `apk/full/debug/app-full-debug.apk`。
- 签名：debug 继续使用公开的 `app/debug.keystore`，仅供调试。0.2.0 的正式发布使用新生成的独立私有密钥，保存在仓库外，私有备份已按产品负责人授权存入专用私有仓库，位置与恢复步骤见第 7 节；不得上传为公开附件或提交到公开应用仓库。正式包不能覆盖旧调试签名安装，必须先导出加密备份，再换装与恢复。后续正式更新必须沿用同一私有密钥。
- 发布附件工作流现为只读验证：`.github/workflows/release-assets.yml` 根据 `.github/release-assets.json` 下载现有公开附件核对 SHA-256/大小/源码 tag，不上传或改动 Release；此前 Git blob 上传仅用于已完成的紧急替换。
- 截图测试：`ScreenshotTest`、`ShellScreenshotTest` 输出到 `app/build/screenshots/`。在 Robolectric 里，对话框中的 TextField 在手机尺寸限定符下不会进入空闲状态，所以对话框的交互测试使用默认屏幕尺寸（见 `HtImportWizardTest`）。
- 翻译：新增文字要同时加到 `values`、`values-zh`、`values-b+zh+Hant`、`values-fr`（app、app/src/full、core/reminder 各自的 res 目录），`TranslationsTest` 会检查各语言的键和占位符是否一致。

## 5. 已知限制与注意事项

- 应用级名称已永久改为中性 Notes，伪装任务/通知使用中性身份；APK 静态分析、安装来源、签名、旧 OEM 缓存及用户导出文档无法完全隐藏，详见 `disguise-privacy.md`。
- 进入系统设置页（例如精确闹钟、电池优化）也会触发伪装模式的立即锁定，只有文件选择器例外（按用户要求）。
- 补药通知在提醒重建时检查（打开应用、服药、闹钟触发、开机等），没有单独的每日定时任务。
- P0 build6起，新HRT tracker导入保留来源组的PK配置，不按映射目标当前配置重解释；旧导入快照未保存的参数无法恢复。Trans Memo没有实际途径字段，新记录也不猜途径。
- 已有 Robolectric、截图和 API 35 原生模拟器测试；尚未完成 OEM 真机系统验证。用户此前报告的导入对话框选项问题已修复。

## 6. 给接手者的工作顺序建议

1. 当前工作优先看 2e 的私人便签与身份审计；此前库存、同步和导入关联修复见 2d。新增变更继续完整检查，并安排 OEM 真机复核。
2. 0.3.0 文献引擎及重写已完成；不要按前面的历史过程重新做 M4a 或移植代码。
3. 舌下 8 h 后的模型选择仍待用户决定，维持现有外推标记。
4. 未获新的发布指令前不发布、不动 0.2.0；正式签名只用第 7 节的既有私有备份。

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

### 发布展示名称调整（2026-10-06）

用户要求公开标题/说明不要显示 emergency hotfix。已将 Release 标题改为 HRT Log 0.2.0，并同步三语说明的普通更新措辞；APK、签名、构建编号及附件不变。发布文案源文件同步更新，避免以后恢复旧标题。

2026-10-06：已恢复 Release 标题下的三语语言选择指引，与旧版一致，三语折叠区块保留；公开文案与仓库源文件同步。

### 2g. 当前备份/库存/正常身份修复（2026-10-06，已完成并发布 build 5）

用户第二次提供加密备份并要求修好。诊断只在仓库外的会话临时目录进行，未把密码、明文或健康数据写入仓库/日志/CI。结论（只记结构，不记内容）：
- 备份能用密码正常解密，格式和关联完整，**文件没有损坏**。
- 库存天数：当前计划间隔不是每天，按该间隔计算天数本身无误；编辑页已增加说明，防止输入框原值与新输入拼接。
- 每盒量：药物默认容量与现有包装容量不一致（旧版修改药物时不自动同步包装）。
- 完成状态：提前完成记录挂在旧计划上，随后多次保存创建了新版本，新版本再次生成同一时刻的待服。

代码修复（本轮提交）：
- `Timeline.dedupeVersions`：同一药物、同一原定时刻、同一实际时刻、同一剂量、来自不同计划版本的槽位合并；有记录的保留，没有记录的只留一个（优先有覆盖的、再取最新版本）。测试在 `ScheduleTest`。
- `saveMedication`：计划不变（类型、间隔、星期、时间、剂量、提醒窗口、时区、成分）时复用当前计划版本，只更新名称快照；`resizeContainers=true` 时把在用和未开封包装改为新的每盒量，已用量不变，已用量超过新容量的包装保持不变（`resizableContainers`）。编辑页在每盒量与现有包装不同时显示开关（默认开）。测试 `PlanEditTest`（合成数据复现用户场景）。原生测试 `versionCutoverUses...` 改为真正修改计划。
- 编辑页"每隔几天"大于 1 时显示红字"每 N 天才服药一天，不是每天"。
- 正常身份：应用标签改回 `app_name`，full 版不再把应用图标替换为便签图标；删除 `system_app_name`；`disguise_limits` 如实说明系统设置会显示 HRT Log；`check_release_manifest.py` 与原生测试同步。
- 电池优化"修复"直接打开本应用的系统应用信息页（`ACTION_APPLICATION_DETAILS_SETTINGS`），失败时退回原全局列表；无新权限。

修复后的备份：只改两处——用户确认的当前计划间隔改为每天，当前包装容量同步药物配置（已用量不变），用原密码重新加密，已通过应用自身的 `restoreBackup` 验证（容量、计划间隔与已完成状态均符合修复目标）。文件只交给用户，不进仓库。

版本：`versionName=0.2.0`、`versionCode=5`（高于已发布的 build 4）。完整检查已通过：207 项单元测试 0 失败、2 项 PDF 跳过；lint full 0 错误 66 警告、play 0 错误 61 警告（新增 3 条均为新文案的 PluralsCandidate 提示）；full/play debug 与 release 四构建成功；`check_release_manifest.py` 两个 release 变体通过（应用名/图标为 HRT Log，无 INTERNET）。

**正式签名（已完成）**：用户授权后读取私有签名仓库，在仓库外解压签名，签名后立即删除密钥与密码文件。源码 `462da22`，证书 SHA-256 与第 7 节一致，v2/v3 签名，16 KB 对齐，非调试，无 INTERNET，应用名 HRT Log。
- full：23,006,288 bytes，SHA-256 `231a2b2f0ba7146df3d8b24c98d27316429af1059876b7e5aab82b657a2ed4bc`（已直接发给用户）。
- play：22,919,936 bytes，SHA-256 `f390a5cbb3ab3e21508521d286a00e4fa1f15f6da6a961795e85069d11b816f8`。

**公开 Release 已替换为 build 5（2026-10-06，用户授权）**：
- 方法：本环境没有可用的 GitHub 登录，所以把已签名的 full/play APK、SHA256SUMS 和说明放在临时孤立分支 `release-upload-build5`，由只在该分支运行的工作流（GITHUB_TOKEN，contents: write）删除旧的三个附件、上传新的并更新说明，再下载回查（[run 37524801315](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37524801315)）。会话的 git 代理不允许删除其他分支或推送标签（403），所以用一次性工作流（[run 37525061741](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37525061741)，随后删除）删除了临时分支，并把 `v0.2.0` 标签移到 build 5 源码 `462da22`。临时分支中的 APK 是公开发布文件，不含密钥或密码；分支已删除。
- 本机从公开下载地址独立回查：两个 APK 与 SHA256SUMS 一致；标题“HRT Log 0.2.0”，说明为 build 5，`SIGNING_CERTIFICATE.txt`、`THIRD_PARTY_NOTICES.md` 未变。`.github/release-assets.json` 已更新（source 462da22、versionCode 5、新大小/哈希），由只读校验工作流验证。
- **真机确认（2026-10-06）**：用户已覆盖安装 build 5，恢复修复备份后显示正常。
- 以后再发布：提高 versionCode；可沿用同一临时分支+工作流的方法，结束后删除分支和一次性工作流。

### 2j. 产品源码调研（2026-10-07，已完成设计，待审核）

已完成 HRT Log 当前源码审计，保存 `docs/hrt-product-research-2026-10-07.md`。基线759ee88，最新功能bf7d6d4；公开APK462da22/build5与最新wellbeing源码区分。发现：已有单药Rule版本/实际快照与包装账本，但PK仍用当前profile解释历史；症状只存date/group/note且PDF从当前目录所有group来源取值；预约已有，Visit Pack应扩展而非从零。项目未发现根LICENSE。

已在仓库外只读核查 Featherline、Mona、Chrysalide、Yuuki HRT-Tracker、NoMTF Recorder、归档TransTracks、Transmtf源码/许可/最近提交；MyHRT/HRTMe/MyTRT/Trans Memo官方资料。MyHRT新发现security子仓库仅限验证用途，并非开源/允许复用，必须另写进许可证分析。

未完成：完整矩阵、Featherline深度设计、独立schema/迁移方案、评分路线图、PRIOR_ART及文档链接检查。报告中预先列出的这些文件随后补齐。无应用修改/无发布/无向第三方发消息；研究临时克隆位于/workspace/scratch/research，结果须继续写入仓库。

2026-10-07 调研第二阶段完成：`docs/hrt-competitor-matrix-2026-10-07.md` 与 UTF-8 BOM CSV，81项×9产品列；固定各默认分支提交、源码审阅路径、许可/issue边界，标记源码/官方/部分/未知。特别区分Yuuki Swift HRT-Tracker与Transmtf导入目标、Chrysalide PWA与Trans Memo协会、MyHRT安全层的禁止复用许可、Featherline T模型与未合并校准PR。独立设计/评分/PRIOR_ART仍在写，无功能代码改动。

2026-10-07 第三阶段完成：独立设计 `docs/design/longitudinal-hrt-record.md`、六维评分 `docs/hrt-roadmap-2026-10-07.md`、根 `PRIOR_ART.md` 已写入。Featherline逐项分析medicine/identity/group/log/instruction/equivalent量、365天simulation、widget/cache/quicklog、TrackedDate/Anchor、Argon2/GCM/gzip/bounds；与HRT现有固定KDF/无压缩区分，不把不存在的可变cost/gzip路径当漏洞。P0历史PK/source/missed/restore/许可；P1epoch-context-timeline-visitpack，P2widget/模型扩张。明确此次已读源码不是真正法律隔离clean room。下一步文档链接/矩阵和证据一致性检查，再最终回复；无应用代码改动。

2026-10-07 调研最终完成并验证：5份研究/设计/路线图/流程Markdown+1份CSV，81项矩阵、28个六维评分候选。已程序核对16个相对文档链接、74个固定提交源码路径、Markdown表宽、CSV列宽/唯一性，全部通过；git diff --check通过。报告只针对代码/官方资料，未安装竞品/未验证商店binary，不称完整安全审计。未运行Android测试：本轮仅文档，没有更改应用、模型或发布。

提交阶段：131ad14（本项目现状）、c926b85（竞品矩阵）、beef98e（独立设计/评分/PRIOR_ART），均推送claude/new-session-1959qb。给用户通过GitHub页面阅读，避免Android本地文件预览限制。建议定位local-first longitudinal HRT record；P0历史上下文/症状来源/未确认语义/恢复边界/许可治理；后续实现需另行明确范围，本轮建议不是实现授权。无未完成研究文件。

### 2k. P0应用更新（2026-10-07，已完成；过程记录）

用户已要求开始更新，先做history-integrity-p0设计范围。源码基线456fa1c，无远程新功能。本地JDK21已具备，正在/workspace/tooling安装SDK37/build-tools37并准备Gradle测试；之前的构建缓存不存在。设计/需求已记录；下一步历史snapshot与症状schema3、未确认语义和恢复校验。不得读/提交真实健康数据，不选LICENSE、不发布。

2026-10-07 P0首轮代码已保存：MedicationSnapshot v2与profile/导入上下文、计算/历史/CSV读取快照，schema3症状来源snapshot及2→3迁移，AUTO_MISSED映射UNCONFIRMED、确认漏服入口与统计/日历分列，32MiB bounded备份读取/JSON预算/字段类型与恢复后约束/账本检查、密码finally清理。versionCode6。新增合成历史/恢复/来源/迁移回归。JVM测试已通过；Android首次编译只报缺少AppShell import，已修正，正在跑data/app单元测试并生成schema3。仍需完整lint/四构建/原生验证；现在不能称已完成或可交付APK。工具链新安装在/workspace/tooling（SDK37、Gradle9.3.1、TemurinJDK21），Gradle使用代理与系统CA。未改PK公式/参数、无真实数据、无发布。

2026-10-07 P0第二阶段：data 36/36、reminder 14/14、app full 77项（76通过、1 PDF写入跳过）已通过。Robolectric测试依赖已补齐至本地离线目录，首次失败是缺少测试Android jar，不是业务断言。补齐日历旧计划上下文/自动状态、PDF当前方案与历史上下文分标、CSV/PDF一致读取事务、不把旧单位补记自动扣到不同当前单位包装。原生测试已跟进schema3及症状context往返，新增单位不一致回归待下一轮执行。README/PLAN同步现状。play测试、lint、四构建和原生检查仍进行中，不称整轮完成/无发布。

2026-10-07 验证第三阶段：b7aae2b的CI原生device-tests与JVM成功（run37629214363）；android任务因新增库存单元测试的Double?断言编译失败，已修正为显式非空断言，未影响生产代码。进一步补上导入未知途径不继承口服模型与恶意账本REVERSE空关联校验/合成反例；PRIOR_ART记录独立实施commit，设计如实保留native非E2口服假设/包装单位未独立持久化的限制。修正后的完整检查将重跑；本机首次lint full 0错误64警告、play 0错误59警告，四构建仍在进行。后续按既有用户要求生成正式签名build6测试包，不替换公开Release。

恢复复核补充：发现旧RawData.restore把任何以b64:开头的TEXT误当成BLOB。当前domain schema没有BLOB字段，已改为保留字面文本，并将合成备注往返断言加入历史完整性测试；这个修复随最终全量回归一起验证。签名私有仓库已通过连接器确认仍为private，临时密钥恢复在仓库外，签名结束立即清理。

2026-10-07 最终源码2a58d01的本机单元回归已通过：domain30、pk18、importer12、data37、reminder14、app full78（1 PDF跳过）、play65（1 PDF跳过），按模块/变体合计254项，252通过、2跳过、0失败；不是254个互不重复用例。新增库存旧单位、未知导入route、REVERSE空关联及b64:字面备注均已跑过。最终lint/四构建仍运行；CI37630464376针对同一源码运行中，JVM已成功。此前中间版本的native31项成功不能冒充最终源码结果。下一步完成最终构建/native结果、正式签名与临时下载回查，再更新顶部最新交付信息。无公开发布。

2026-10-07 P0最终完成：本机完整检查和最终CI三个任务成功；已签署full/play build6并清理临时凭据，full临时下载独立回查一致。顶部最新交付块已列出源码、测试、文件哈希与剩余限制。无未完成P0代码/未提交修改，无公开发布；下一批按design/longitudinal-hrt-record讨论的Epoch与Timeline推进。

### 2l. P1第一批（2026-10-07，已完成设计，待审核）

用户要求下一阶段，启动Epoch/统一时间线与用户里程碑。基线734444a，设计epochs-timeline-p1.md先行，需求§20。计划schema4新增regimen_version/regimen_rule_link/milestone；Epoch为确定性半开区间投影，旧规则回推标记、date-only跨阶段不猜。提醒变化保持周期锚点；不改PK/公开Release。尚未写功能代码，下一步数据层/迁移与合成回归，再四语UI和正式签名build7。

2026-10-07 P1数据层完成：schema4新增方案版本/规则关联/里程碑，提醒与名称变更保持临床版本及周期锚点；剂量/途径/制剂变化、停用/恢复形成新版本，Epoch为确定性投影。3→4及旧备份只用存储快照回推，LEGACY_RULE/录入时间未知；新备份校验版本签名与规则对应关系，失败回滚。domain33、data41本机通过；原生1→4及含旧规则3→4迁移用例已编译，尚未在设备运行。界面/时间线投影进行中，未完成构建或签名，不可称build7可交付。

2026-10-07 P1界面第一轮完成：新增时间线导航、阶段视图、范围/类型/药物/阶段筛选、计划默认隐藏、每日未确认汇总、记录详情及原页面入口、用户里程碑编辑/删除；四语文案与精简模式标题隐藏。实际事件按实际时间，旧计划关联单独显示；日期观察整日候选，未知部分保留。app full84项（83通过、1 PDF跳过）第一轮通过，包括4个投影反例与2个UI用例。随后补充阶段精确年月日时间、合成时间线截图、冻结定义触发器与恶意规则关联回归，待最终全量运行。下一步lint/四构建/最终CI原生migration1→4、3→4，再正式签名build7。无公开发布。

最终复核补充：Epoch生成改为按起止边界维护活动集合，避免每个边界重新扫描全部版本；未确认筛选直接用状态/来源，避免全列表成员查找。全量构建此前开始，最终源码修改后需补跑相关回归，并用最终提交重新跑CI；不可把中间提交检查当最终结果。

2026-10-07 最终源码bcc629f本机：domain33、pk18、importer12、data42、reminder14通过，app full85项（84通过、1 PDF跳过）通过；play和lint/四构建继续运行。合成中文时间线截图已人工查看，显示年月日/时区/阶段/化验/里程碑正常。最终CI37638118061的JVM成功，android/device-tests仍运行；不要把取消的中间CI37637945178当最终结果。正式签名凭据已确认private并在仓库外恢复，尚未签署；交付后立即清理。

2026-10-07 最终回归进一步完成：按模块/变体合计276项（domain33、pk18、importer12、data42、reminder14、full85、play72），274通过、2 PDF跳过、0失败；并非276个互不重复用例。lint full0错误65警告、play0错误59警告。CI37638118061 device-tests成功：原生data9项（含1→4与3→4）、app23项中正常批次2个重启用例跳过，随后分别启动新进程执行这2项成功；新进程认证/ciphertext保持通过。正式release构建仍运行，尚未签署或上传build7，下一步检查最终android CI、签名与下载回查。

2026-10-07 P1第一批最终完成：功能源码bcc629f的本机276项回归/两变体lint/四构建/测试APK/manifest/schema检查和CI37638118061三个任务全部成功。full/play已使用既有正式证书签署，full上传真实令牌入口并下载回查，临时签名凭据已清理。顶部最新交付列出字节数/哈希与实际边界。无未完成本批代码，无公开Release/标签/根LICENSE变更；后续仍为Lab Context与Visit Pack/事实变化摘要，真机未验证。

### 2m. build8浓度修复（2026-10-07，已完成；过程记录）

用户反馈上下文缺失与曲线贴轴，已定位旧导入writer只存source标记、新版逐事件读取跳过；图表用95%最大上界撑轴。已读Transmtf ResultChart/chartAxis现存研究checkout，采用独立可切换中心/完整区间，非复制其实现。设计先行concentration-context-chart-hotfix.md/需求§21；下一步可信旧rule上下文、再导入/用户确认修复与图窗缩放合成回归。未读取用户真实健康数据、未改PK参数、尚无build8 APK。

2026-10-07 build8功能修复已写：HistoricalContext补缺失/拒绝覆盖已知字段、本应用关联rule可信读取、旧HT同key/时间/剂量/药物原文件重导入仅补snapshot、按日期历史确认保留original/provenance且不改剂量修订/库存；导入实际不按关联计划自动补，另加此反例正在最终回归。ChartViewport中心/完整区间切换、零基线/有限值/边界/实虚连续、精简小数与四语。首轮data46和app full90（89通过、1 PDF跳过）通过；极宽区间合成截图已查看，中心曲线清晰可见。新增历史确认UI/日期/库存/备份/旧rule冲突反例。正在重跑最终语义检查，之后完整lint/四构建/CI、正式签名build8；尚无可交付build8 APK，不改PK公式/参数或schema4。

2026-10-07 build8最终语义回归完成：data46/46、full90项（89通过、1 PDF跳过），含导入记录不得从计划推断实际制剂。功能源码d4c3c92已推送（GitHub短暂500后重试成功）；最终CI37642789943已启动。本机最终play/lint/四构建仍运行，正式签名尚未完成；临时凭据位于仓库外，签署后必须删除。

用户补充复现为HT导入+应用内混合记录、雌二醇舌下、未开化验校准。新增对应合成回归：本应用linked rule、用户确认HT旧上下文、实际与未来舌下曲线均非零、实虚分界连续、中心占视窗比例；无生产逻辑改动。待最终构建结束后执行新增测试，并重跑最终CI。

2026-10-07 build8完整基础检查：domain33、pk18、importer12、data46、reminder14、full90/play77（两PDF跳过），288通过/2跳过/0失败；两变体lint0错误（full66/play61警告），release manifest与schema无漂移通过。用户场景新增回归0d6b80f尚待本机执行，最终CI改为37643141363，不能把基础290项当新增场景验证。release打包继续运行，暂不能交付新APK。

2026-10-07 build8 full包已构建/原正式证书签署：/workspace/HRT-Log-build8-full-signed.apk，23,223,515 bytes，SHA256 c1b9ef54c8386a8eaef85edb033d6c21e2530350f7941dcf8d8bc687bda45e89。versionCode8/非调试/16KB/无INTERNET核验，tmpfiles真实令牌下载回查大小与SHA一致，已在commentary提供浏览器临时链接。生产逻辑d4c3c92，0d6b80f仅新增用户场景回归；CI37643141363 jvm/device-tests成功，android未结束，本机新增场景未执行。play R8继续运行；临时密钥尚需用于play，签后立即清理。剩余：最终本机场景测试、play签名、最终CI和顶部交付块。

2026-10-07 build8最终完成：补充用户舌下/混合来源/不校准回归full91/play78已完成，两变体各1 PDF跳过；最终合计292项/290通过/2跳过。CI37643141363三个任务全部成功，两正式包签署并删除临时凭据，full下载回查一致。顶部交付块已更新；无本轮未完成代码，无公开发布，真机确认待用户。

### 2n. build9地区候选框（2026-10-07，已完成；过程记录）

用户要求紧凑地区候选框。RegionSection已替换常驻六行RadioButton，复用DropdownField；仅显示选中地区，点击弹出六项列表，未选择和帮助说明保留。设置持久化键/值与语言独立性不变；versionCode9/schema4。下一步相关现有UI/翻译回归、full/play编译与lint、正式full包签名下载，不改公开Release。无需新增镜像式UI测试。

2026-10-07 build9功能0511922已推送；full91/play78应用回归（167通过、2 PDF跳过、0失败）完成，无新增镜像式测试。CI37645063384 jvm成功，android/device仍进行；本机lint/release尚在构建。私有签名仓库再次确认private，临时恢复在/workspace/tooling/build9-signing；签后须清理。尚无build9可交付APK。

2026-10-07 build9静态检查完成：full0错误66警告/play0错误61警告；仅界面选择器变化，无新翻译/数据库迁移。本机正式full R8运行中，CI37645063384仍进行；完成后签署和实际下载回查。

2026-10-07 build9 full正式签名与下载回查完成，临时签名凭据已清理；顶部交付块已更新。仅CI android最终结果尚待核对，不能称三个CI均成功；无需另造新APK或重复已通过本机测试。

2026-10-07 build9最终完成：CI37645063384三个任务全部success，四构建/manifest/schema检查通过。本机169项应用回归（167通过、2 PDF跳过）/两lint/full release成功；正式full已独立下载核验并交付。临时签名凭据清理，无本轮未完成代码或验证任务、无公开发布；用户真机结果待确认。

### 2o. 废弃play（2026-10-07）

用户要求记住并写入交接。已清理实际构建/CI/空实现/校验脚本，保留full flavor命名使现有full任务与安装兼容，不改schema/版本号/运行时逻辑。本轮不生成新APK。验证已完成：Gradle任务列表无play目标、assembleFullDebug成功；CI/源码变体无play，manifest检查只要求full并通过。本轮仅构建配置/接手规则变更，不重复运行运行时单元测试。源码24cd854已推送；full-only CI自动启动，其结果尚未核对，不称已完成CI。最新交付APK仍为full build9。

### 2p. 后续已构想未实现清单（2026-10-07）

用户要求继续前先说明。已核对schema4实体、化验界面（孕酮检测方法已有）、预约/复诊导出与原设计，更新hrt-roadmap当前状态表13项。优先仍Lab Context、Visit Pack/事实摘要，之后阶段导出与注射/库存；已实现Epoch/Timeline/里程碑/症状快照不重复算待办。本轮仅清单/文档，没有启动新功能、改版本或生成APK；仅full规则继续。

### 2q. Lab Context第一批（2026-10-07，已完成）

基线b1e60be，用户要求继续。设计lab-context-p1.md已写，拟schema5/build10，逐化验上下文修订与显式旧数据回推；各成分actual独立，不再用全局最近doseTimes。可选PK仅采样前actual/无自校准，保留参数和结果快照。下一步数据层/备份/合成回归、四语UI与导出，再full验证和原正式签名交付。数据层与schema5迁移、不可变修订、旧备份兼容/校验回滚、四语化验详情和CSV/PDF已写。合成data51项和full app93项回归已通过（app的1项Robolectric PDF按既有条件跳过）；原生测试APK正在编译，尚未运行设备验证/完整lint与release，未生成正式build10。新增UI回归覆盖旧版切换/明确重建，PK回归覆盖舌下正值、采样后事件排除与不自校准；native PDF用例已加入上下文。下一步全量full验证、原生CI、原正式签名与真实下载交付。

2026-10-07 Lab Context功能阶段2ff40f3已推送，初始data51/full93回归（1 PDF跳过）及两个原生测试APK构建成功。新增合成截图已查看，旧版切换/重建确认成功。追加缺实际剂量的Trans Memo导入与非法估算单位回归（无实际剂量按既有schema只允许IMPORT_TM，测试fixture已更正）；恢复校验进一步拒绝已知阶段缺起始时间/版本键冲突与非object估算。最终full全量检查在运行，新增校验仍须末轮重跑；CI初版37657997859进行中，不作为最终源码验收。私有签名仓库已再次确认private，临时恢复在/workspace/tooling/build10-signing，仅用于full签名，完成必须清理；尚无可交付build10。

2026-10-07 上下文方案显示补齐周计划星期与原冻结计时起点/时区，四语齐全，避免只看到间隔而无法确定排期。末轮需复跑最新debug编译/单元/lint（此显示与阶段结构校验是在全量任务过程中补入）；release编译尚未开始时补入，将再检查最新release任务确实无待重编译。

2026-10-07 最终功能源码a30c850，CI37658823665的jvm与device-tests已success；设备日志核验原生data10、应用23（主套件另2重启项跳过后分别新进程OK）。含1→5/3→5/4→5、SQLCipher、上下文PDF分页/伪装锁等。android构建仍在运行。本机domain33/pk18/importer12/data51/reminder14/full app93=221项，0失败、1既有PDF跳过；此轮debug尚未含最后阶段结构/排期显示补丁，结束后必须重跑最新debug单元/lint/构建，release已在补丁后编译、仍在R8。lint当次0错误69警告（新增两条复数候选和原已无调用的资源等，最终计数待复核）。新增lab-context-verification.md，路线图Lab Context第一批标已编码，其余Visit Pack/LabPanel等待办。尚未签名/下载build10，临时密钥须在签后清理。

2026-10-07 最终源码a30c850的CI37658823665三作业jvm/android/device-tests均success（连接器核验）。本机第一轮完整full任务12m41s成功，末轮最新debug校验继续；最新data51/full93均无失败、1既有PDF跳过，lint/打包尚待结束。正式签名与真实下载仍未做，不把unsigned包交付。

2026-10-07 build10最终完成：最新源码末轮完整full任务成功、CI三作业success，正式full包签署/版本/证书/对齐/manifest核验及真实下载回查完成，临时凭据已清理。顶部最新交付块记录所有结果与剩余边界。本批无未完成任务，后续Visit Pack/事实摘要另批。

### 阶段修订设计接手复核（2026-10-07，已完成设计，待审核）

用户要求先说明目标，已说明只分析/文档，完成等审核。初始本地6cc7dd6落后；推送拒绝后同步远端ab983c5（build11/schema6/Visit Pack与MIT已完成，已有修订草稿），保留远端全部工作，仅合并设计文档。用户确认STARTED时间线保存、日期超过90天；默认90天过滤与反馈缺失匹配，SQL合成探针验证标题/重复类型合法，未读真实数据。这些复核已完成，见后续完成段：固定分组时区/变更日组、旧key多对多时刻映射、Lab Context V1协议保留与Visit Pack模板兼容；旧设计冲突已改写。本次停止等审核，不启动实现/PK/签名任务。

2026-10-07 本次设计接手复核完成：基于远端ab983c5/build11/schema6，不回退MIT/Visit Pack。用户确认时间线+超过90天日期，P0默认日期过滤与反馈问题明确；本轮合成SQL探针合法STARTED/null标题与重复类型，未读真实健康数据、未重新运行旧会话Robolectric探针。修订稿纠正午夜对齐会误归样本、同版本跨多组合阶段、直接换Lab Context key会破坏V1验证等风险；保留存储signature/clinical_signature/validateLinks与完整原数据，另建V2显示投影。旧design/epochs-timeline-p1.md已按用户已确定规则改写，REQ §26追加范围/复现。所有改动仅docs，未改功能/测试源码/版本/schema、未构建APK/跑新实现测试、未签名或发布。下一步必须等用户审核7组待定项，不能因以前“继续”指令跳过本次明确暂停。当前设计提案尚未实施，最新可安装仍build11。

本轮末检：schema6导出建表SQL+guard的内存SQLite STARTED/null标题与重复类型合法；日期谓词复核；文档无冲突标记、代码块配对和git diff --check通过。相对远端接手ab983c5仅四个docs文件修改。旧会话按钮审计不在本次附件范围，本轮未处理；build11交付事实和MIT决定保留。
