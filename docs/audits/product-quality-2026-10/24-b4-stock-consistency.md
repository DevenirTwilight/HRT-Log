# 24 B4 库存一致性与库存纠错验收

> 最新B4-D：产品负责人已批准VOIDED，ADJUST-only方案待决定项已解决；READY_FOR_OWNER_REVIEW（当前HEAD全部检查完成且全绿后生效）；实现代码及首轮验收文档CI已全绿，以末节B4-D为准。下列B4-A/B/C及旧待决定说明保留阶段证据，不代表D的验收结果。

2026-10-13；分支 `feature/b4-stock-consistency-20261013`，起点默认分支 `bab53d3`（PR #22 合并后 Android 38397467180、Science 38397467156 全绿）。路线图 R-09/D-3、R-14 及产品负责人补充任务“库存核对与纠错”。未合并、未签名、未交付。

## 统一规则

- **一个预测**：`core/data/StockForecast.kt`。按时间顺序走该药物的待服剂量，从可用库存中逐次扣减。
  - 可用库存 = 在用包装剩余量 + 未开封包装容量。
  - 待服剂量由领域 Timeline 产生：跳过、改期、剂量覆盖、计划版本和已服记录都由 Timeline 处理，本模块不另行生成计划。
  - 已服剂量此前已从包装扣过，不会再扣一次。
  - 输入是同一个窗口 `NotesRepository.stockDemand`：自跟踪开始尚未完成的剂量，加上未来 366 天。
- **谁在用**：库存页、日历、库存提醒都读这一个预测。ViewModel 每次刷新计算一次；提醒协调器调用同一个仓库函数。
- **天数**：首次无法覆盖的那次服药所在的**日历日**，减去今天的日期。0 表示今天那次就不够。界面上的日期和天数都指这次服药。
- **低库存**：今天起 7 个日历日内（第 0–6 天）有一次服药无法覆盖。库存页标签、日历警示、库存通知用同一个判断。
  - 是否真的发通知仍单独决定：每种药的通知开关、待审核状态、通知权限、每天一次、中性措辞、伪装模式。
  - 关闭通知不改变页面上的低库存判断。
- **窗口内都够**：显示“至少够用到 某日”，不再说“够用一年以上”这类可能被理解为无限的话。
- **未跟踪**（没有在用或未开封包装）：不做猜测。**无计划**：显示无法估算。
- 开封后有效期提醒与数量判断相互独立，两者可以同时出现。过期包装是否计入数量，维持原规则不变。

## 修改前后三条路径对照（同一合成输入）

| 场景 | 修改前：库存页（平均日耗） | 修改前：日历（逐次，按 24 小时取整） | 修改前：提醒（7×24 小时内剂量总和） | 修改后：三处共同结果 |
|---|---|---|---|---|
| 周一和周四各 2 mg，剩 4 mg | 7 天，不低 | 9 天（19 日），不低 | 不低 | 19 日，9 天，不低 |
| 每天 2 mg，22:00 查看，剩 12 mg | 6 天，低 | 6 天，低 | 低 | 17 日，7 天，不低（第 7 个日历日） |
| 每天 1 mg + 3 mg，剩 9 mg | 2 天，低 | 2 天，低 | 低 | 12 日 20:00，2 天，低 |
| 每天 2 mg，跳过两次，剩 12 mg | 6 天，**低** | 8 天，不低 | 不低 | 18 日，8 天，不低 |
| 每 8 小时 1 mg，剩 20 mg | 6 天，低 | 6 天，低 | 低 | 17 日，7 天，不低 |

修改前的数据来自一个临时探针，用的是旧函数本身（不提交）。修改后的结果由 `StockConsistencyTest` 固定，额外加一个“库存为零”场景。

口径变化有两处：
- 库存页从平均日耗改为逐次扣减，跳过、每周特定日期等情况由此正确反映。
- 7 天边界从 24 小时块改为日历日：第 7 个日历日才不足的，不再算作低库存。

## 库存核对与纠错（补充任务）

- **核对库存**：只要这种药物有在用包装或“已用完”包装，库存卡就一直显示这个入口，即使已经没有在用或未开封包装。
  - 只能选择在用或已用完的包装。已丢弃和未开封的包装不在候选里。
  - 有多个候选时不预选，必须由用户明确选择。
  - 输入的是该药物的库存单位（如 mg），并显示“约等于 N 次服药（每次 X）”。一片 2 mg 的药应填 2，不是 1。
  - 写入一条既有的 ADJUST 流水：不新增包装，不改服药记录。已用完的包装有了剩余量会恢复为“使用中”；输入与当前相同的数值不会产生新流水。
- **三个操作分开**：“开新的一盒”（真正开始用下一盒）、“核对库存”（修正现有包装）、“添加库存”（真正获得了新包装）。只剩历史包装时，卡片显示“目前没有正在使用或未开封的包装”，同时提供“核对库存”，不再只剩“开始记录”。
- **撤销误添加**：在“管理包装”中，只有完全没有库存流水的包装（没有消耗、回补或调整）才显示“撤销误添加”，并要二次确认，确认后删除这一行。
  - 有任何流水的包装一律拒绝删除；界面说明原因和安全的纠正方式：修正对应的服药记录，或用“核对库存”。
  - 不会伪造“丢弃”事件。
- **管理包装**：库存主页每种药只保留一个“管理包装（N）”入口，不再逐盒列出。
  - 包装按使用中、未开封、已用完、已丢弃分组；后两组默认折叠。
  - 不显示内部 `#id`。容量、剩余、开封日期、来源、批次照常可见，包装信息仍可编辑。
- **与统一预测配合**：每次核对或撤销后都会刷新，库存页、日历和提醒用同一份新数据。被恢复的旧包装开封日期较早，会被优先消耗（测试确认不重复计入）。服药回补沿用不可重复的 REVERSE。

### 需要产品负责人决定的边界（未擅自实施）

用户真实遇到的情况是：误添加了一盒，又对它做过“修正剩余”。这盒已经有 ADJUST 流水，而流水表只允许追加（触发器禁止更新和删除），所以不能删除，也不能在不伪造事实的前提下让它不再计入库存。可选方案：

1. 增加一种“作废/录入错误”包装状态，且不计入库存。需要修改 Schema 守卫的状态集合，并评估备份兼容。
2. 允许在专门许可下删除“仅有 ADJUST、没有消耗”的包装及其流水，类似现有的 purge_permit。需要修改流水的不可变触发器。
3. 维持现状：用“核对库存”把这盒的剩余量改为 0，它会显示为“已用完”，但这与事实不符。

B4-C当时只实现无流水撤销并等待决定。产品负责人现已在B4-D批准VOIDED及最小状态约束修改，详见末节；不采用删除ADJUST流水方案。

### 双重回补

“核对库存”填写的是绝对数量。如果先核对、后来又删除了一条错误的服药记录，系统会把那次服药回补一次。这是正确的流水语义，但可能让数量高于实物。界面提示：差异来自记错的服药时，先修正记录，再核对库存。测试固定了推荐顺序以及回补不会重复应用。

## 测试

| 测试 | 结果 |
|---|---|
| `StockForecastTest`（core/data，10 项）：每日、一日多次不同剂量、隔日、每周特定日、每 N 小时、跳过/改期/剂量覆盖、已服不重复扣、计划变更、在用 + 未开封、恰好覆盖、差 0.001、零库存、未跟踪、无计划、7 日边界（含 22:00）、窗口内都够、巴黎夏令时 | 10/10。另有 3 个人为引入的错误（低库存判断改为 ≤7 天、把跳过的剂量也计入、恰好够时判为不够），均被测试捕获 |
| `StockCorrectionTest`（core/data，9 项，真实 Schema 守卫）：已用完 → 2 mg 恢复使用中，不新增包装，ADJUST 不重复；在用包装纠正只改变真实差额；多盒时只改所选包装；未开封和已丢弃不可纠正；恢复的包装下次被优先消耗且不重复计入；未使用的误添加包装可撤销；有消耗或调整流水的包装拒绝删除；先修正服药再核对、回补不重复；无包装、无记录 | 9/9 |
| `StockAlertsTest`（4 项）：低库存、开封有效期、关闭通知不改变判断、低库存与有效期同时出现 | 4/4 |
| `StockConsistencyTest`：6 个场景下库存页、日历、提醒三处结论一致 | 1/1 |
| `StockSummaryTest`（4 项）、`CalendarModelTest`（3 项）、`DataRefreshTest`（5 项，含仓库预测等于页面预测） | 全部通过 |
| `StockCheckUiTest`（英、简、繁、法 × 320 dp × 2 倍字号，每语言 3 项）：只剩已用完包装时有“核对库存”且不只有“添加”；多个候选不预选、已丢弃不出现、按库存单位保存（2.0）；包装分组、历史折叠、无 `#id`、只有无流水包装可撤销 | 12/12 |
| `ButtonLayoutRegressionTest`（库存页三操作等宽、≥48 dp、四语不截断）、`TranslationsTest` | 通过 |

全量本地回归、lint、设备测试，以及 Debug/unsigned Release 构建，以 PR 的 CI 为准（见 PR）。

## 未执行与限制

- 真实 ARM 设备和人工操作：NOT_RUN。
- 片数与 mg 的换算：数据库没有“每片规格”字段。核对时使用药物的库存单位，并显示折合多少次服药。按片输入需要先增加规格字段（需决定）。
- 未修改 Schema、备份格式、PK、版本号或签名。B2、B3 行为不变。

## B4-D：误添加包装作废（2026-10-13，代码验收完成）

产品负责人新决定覆盖 B4-C“有ADJUST不可撤销”的限制。起点 `4afa8b058dddc31627185dc5e3bf3b36bb0534a7`，PR23 OPEN；对应两组Android/Science已completed/success，无运行中CI。

最小设计：Room9/表结构/PNBAK1不变，扩展包装字符串状态为VOIDED；正常查询及预测/分配排除。只含ADJUST或无流水时原子写一条隐藏纠错证据，再改状态；原ADJUST、原始包装字段/缓存/服药事实/其他包装不改。证据使用已有`trash_item`的CORRECTION/HIDDEN记录，保存时间、UUID操作、作废前包装及流水/相关库存与服药指纹；普通回收站不展示。新增针对该证据的不可变保护，库存流水原不可变触发器保留。作废事务不调用RawData.unguarded；既有完整恢复/清空维护路径仍执行原验证和重装保护。

恢复：必须有最新VOID证据、包装和ADJUST流水仍匹配、同药物包装/记录/流水没有后续更正；原子追加RESTORE证据并恢复原状态（不强制IN_USE），失败全部回滚。相同药物的后续库存或服药变更使自动恢复保守拒绝，并解释需进一步纠错；不猜测是否等价。

情况C本轮安全限制：任何CONSUME/REVERSE（包括已反转）均不能直接作废。下一阶段设计须针对完整未反转CONSUME追加唯一对应REVERSE，保留原dose_record；再按照现有分配规则把同一实际剂量只分配一次到真实包装，不足部分记录unallocated_supply_amount；多包装/多修订/既有REVERSE逐项幂等，在一个事务里验证每笔实际剂量的分配总量与未分配量守恒，失败回滚所有补偿。不重写/删除原流水，不靠用户重新录入服药。此设计本轮不实施或上线，需专项测试和产品审阅。

实现、本地回归和代码HEAD `6061ab3f0ed2c8843117db3066dcd5b60edde450` 的完整CI/实际API35已通过，详见下表；验收文档提交也必须等待自己的完整最终HEAD检查。未合并/正式签名/交付。

### B4-D 本地证据与保留的首次失败

- 红测2项先失败：旧无流水撤销物理删除，随后读原行为空；旧ADJUST-only撤销报`Package has stock history`。实现后这两项通过。
- 数据层完整109项、提醒17项通过。新增`VoidedPackageTest`18项涵盖无流水/一条及多条ADJUST、原流水/其他包装不变、重复操作、预测排除、只VOIDED空状态、自动分配/更换包装排除、服药编辑/删除不复活、CONSUME和已REVERSE均拒绝直接作废、原状态恢复、后续库存/实际服药变化拒绝且回滚、失败作废事务回滚、加密备份往返及缺纠错证据拒绝、审计/流水不可变。
- 原盒误显示EMPTY，经核对恢复2mg；假盒容量30mg经ADJUST剩2mg。**总量4mg→作废假盒后2mg，普通包装2盒→1盒**；原盒完整字段、假盒ADJUST列表逐项相同，假盒原行保持VOIDED/used=28mg。
- 四语320dp/2倍字号原生绘制20项通过：普通数量排除VOIDED、只VOIDED为空状态、默认隐藏/主动展开高级记录、指定包装与二次确认、取消恢复零提交。实际ViewModel的即时刷新新增回归通过（DataRefreshTest 6项全通过），库存/日历/提醒仍共享B4-A预测。
- 本地JVM domain58/PK124/importer12通过；历史164/164与5/5、科研261项通过。正式PK/参数、Room9结构JSON、PNBAK1及交付账本未改。
- 首次全应用763项有1项既有`CalendarCoreFlowTest.pickingADayShowsItsDoses`失败，61原条件跳过：LazyColumn尚未创建“明天”标题，旧测试直接assertExists。保持原选中日期、标题、服药按钮、药名断言，加入用户实际滚动到各目标项后检查；10项聚焦回归通过，完整复跑763项已零失败（61原条件跳过）、lint0错误178警告、fullDebug及两套仪器包完成，失败XML已保存。没有放宽断言/新增skip。
- 首次新Android仪器包编译因测试源看不到Room类型而失败；新增仅`androidTestCompileOnly`的现有Room/sqlite类型（不新增运行依赖），两个仪器包随后编译成功。随后API35旧Schema9打开和外部强制结束后新PID验证已实际通过，见CI证据。

旧备份继续读取；含VOIDED的新备份须由支持该语义的版本恢复，旧二进制不支持此新增状态/纠错证据，不能宣称降级兼容。未改变正式加密格式或结构Schema。若以后已有真实VOIDED数据，回滚界面也必须保留状态读取、查询排除及审计验证；不能直接回退到B4-C并删除/自动重写作废数据。本轮没有交付到用户设备、执行回滚或读取真实数据。

情况C下一阶段影响范围为`SupplyLedger`、`NotesRepository`、状态转换守卫、备份验证及版本化纠错证据；仅在完整补偿/重新分配与每笔实际剂量守恒得到独立测试证明后，才可扩大当前“只ADJUST”门槛。当前安全限制不伪造“已用完/已丢弃”，不让用户直接SQL改库。

最后加强“多条VOIDED、零有效包装”的明确样本：3条VOIDED记录时仓库/预测为未跟踪，日常UI无正常包装或历史包装计数；数据18项与四语UI20项聚焦复跑全部通过。此最终代码样本已在干净检出的GitHub push/PR完整检查通过；实际设备与unsignedRelease证据如下。

### B4-D 代码HEAD完整CI验收

被测源码：`6061ab3f0ed2c8843117db3066dcd5b60edde450`。四个run的head_sha均逐项核对，不套用基线或本地结果。

| 事件 | Android（JVM / Android / API35 device-tests） | Science（gate / research-deep） |
|---|---|---|
| push | [38412061193](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38412061193) attempt 2，全success | [38412061245](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38412061245)，两项实际success |
| PR #23 | [38412065846](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38412065846)，全success | [38412065777](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38412065777)，两项实际success |

- JVM domain58 / PK124 / importer12；Android data109 / reminder17 / app763，均零失败。app61个既有条件跳过如实保留，PeriodStability5、Calendar10、DataRefresh6、四语320dp/2倍字号VOIDED UI20通过。
- API35 x86_64：data15/15、app92项零失败/8个条件跳过；作废重启两个方法在主套件条件跳过，随后独立运行prepare和verify各`OK (1 test)`，不是将跳过视为通过。旧B2伪装、导入清理、恢复新进程脚本均继续执行通过；B3日历设备回归保留。
- `MigrationBaselineTest.existingSchemaNineOpensWithExtendedStateGuardsAndRetainsVoided` 在实际API35打开已有v9数据库，包含旧四状态触发器，当前onOpen重装扩展保护后ADJUST-only作废成功；关闭重开仍VOIDED，version仍9。没有结构迁移。
- 新作废脚本在外部force-stop后断言新PID不同；新进程普通查询为空，后台VOIDED/used=28保持；加密备份恢复仍VOIDED，再安全恢复为原IN_USE/剩2mg。PR设备日志14:24:28/14:24:32两次`OK (1 test)`，最终`PASS: fresh process retains VOIDED, encrypted backup and safe restoration`；push重试设备同样通过。
- lint0错误178警告；fullDebug、unsigned fullRelease和两仪器APK构建成功。Release来源身份与clean检出、无INTERNET、交付账本、Room结构JSON无diff检查均通过。仅CI构建unsigned APK，未正式签名/发布/交付。
- Science深验证在两事件均实际执行：历史164/164及5/5、科研261项、双SciPy参考通过，PK正式模型/参数未改。
- 保留基础设施失败：push attempt1的device在下载Android Emulator时`Error on ZipFile unknown archive`，模拟器尚未启动，测试为NOT_RUN。等待该run结束后仅请求重试失败设备job，未取消运行中的CI。attempt2的JVM/Android展示继承的绿色（开始/结束时间与attempt1相同），设备14:29:39–14:38:41实际重跑成功。
- 从PR run独立下载三个报告ZIP，SHA256与GitHub artifact digest逐项一致：build-results `a6228d2f85a3704a613fff55796c2617cd9441e868bd91b4c0a238c5ac7d4476`；device-test-results `f18983b54ffbf52bf708fcb1f4467d54390d35c388d8f5cbe054c57a28c8c558`；jvm-test-results `0ea5af580c627ef01c360bc400eb64c6365a6a925113b750ae20bf3bb3b8a8ec`。计数取实际报告，不仅看绿色状态。

### 交付与最终提交边界

A（无流水）与B（仅ADJUST，包括多次）完整实现并验证；C（任何真实消耗/回补关联）保留安全拒绝，补偿方案及失败回滚已写明，未擅自上线。自动恢复会重新检查原状态、流水、同药物包装及服药上下文，后续变化时保守拒绝。A/B本轮无需产品负责人再作决定；C扩大范围需下一阶段专项设计审阅。

REQUIREMENTS §82已加入“所有用户录入/确认的信息都应存在可发现纠错路径；用版本、逻辑作废或补偿保留可追溯性，区分真实事件、误录入和修正”的长期原则。本轮未扩展服药/化验/预约历史重构。真实ARM/真人四语操作NOT_RUN；没有操作真实用户数据库。PR23保持OPEN，不合并默认分支、不正式签名、不发包。验收文档提交后的最终SHA及检查可在PR23实时核对，必须全部完成后才交还验收；文档更新不借用代码HEAD绿色，也不再循环追加记录自身SHA的提交。

### 首轮验收文档HEAD与最终状态

首轮验收文档 `e9d9b991280b1f6d4b16984046d732f109bd810d`：Android [push 38415622705 attempt 2](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38415622705) / [PR 38415606299](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38415606299) 的JVM、Android、API35均success；Science [push 38415622649](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38415622649) gate success、deep因仅文档变更按原路径门控skipped，[PR 38415606892](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38415606892) gate/deep实际success。跳过未冒称深验证执行，完整PR深验证和代码HEAD深验证均实际通过。

当前验收状态为 **READY_FOR_OWNER_REVIEW**，生效条件是PR23当前最终HEAD自己的所有必需检查完成且全绿。最后状态提交不改生产/测试/Schema/备份/PK；仅另给device-tests增加45分钟作业超时，避免SDK安装无期限挂起；其具体SHA与实际检查链接在PR23正文固定记录，提交后仍完整执行Android与Science，再交还验收，不循环追加自身SHA的文档提交。限制与未执行项不因绿色CI解除，PR保持OPEN。

首轮文档push设备attempt1保留为CANCELLED / NOT_RUN：14:42:48开始`sdkmanager --install emulator --channel=0`后持续无输出，45分钟仍未结束；没有启动模拟器、没有执行测试、没有测试artifact。为了取得诊断日志，在45分钟边界仅结束该唯一未完成作业，不是因为代码变更取消正常CI。日志确认SDK安装挂起后只重试该设备job；JVM/Android已绿色并保留，Science也不重跑。最终状态提交增加device-tests的45分钟显式超时，不更换API35、动作版本、模拟器构建或测试集合，不跳过任何现有检查。此取消不冒称通过；attempt2设备job `115382600209` 已实际success，完整设备测试及独立作废重启/加密备份/安全恢复再次通过，JVM/Android为继承原绿色，首轮文档四个run最终均success。

### §83 正式签名APK私下交付（2026-10-13）

用户明确要求“整完给我个apk，正式签名”，覆盖本报告之前的不签名/不交付边界；不授权合并或公开Release。依D-4将versionCode25→26（versionName仍0.2.0）。实际构建源 `c72531027a4d9782ea0a31dc181acd238e1c83fe`，干净git检出；本地fullRelease成功11m57s。该源Android push [38426457889](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38426457889) 与PR [38426465766](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38426465766) 的JVM/Android/API35均success；Science PR [38426465277](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38426465277) gate/deep实际success，push [38426458127](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38426458127) gate success、deep按普通交付版本变更门控skipped，没有冒称执行。签名仅在本地，不进入CI。

`HRT-Log-build26-B4D-full-official.apk`：12,901,236 bytes，APK SHA256 `0c6bd9132ab15ca7625f1da19b59c1c9450cf4cb0518b15100e30396c5ed3fa0`；原正式证书SHA256 `989ba04532e4c3ec11c2de989d5b1905cf67bdc6c449361293ef62b8a59379b1`；包名net.plainnotes.app/build26，v2/v3、四ABI/8 ELF原生库16KB与ZIP16KB、非debuggable、无INTERNET检查通过。签名前后所有原ZIP条目逐项内容/CRC相同，只增加三项标准签名META-INF，未重写代码、资源或模型。

恢复前重新确认签名备份仓库Private；密钥/密码仅在仓库外700目录/600文件中短暂恢复，签名后finally删除，原永久备份未改。Drive上传后元数据shared=false、唯一owner权限；独立下载回查大小、SHA256、完整源身份、版本与原证书均一致。实际交付后追加账本26，下一次实际交付至少27。私有下载链接、文件ID、密钥与密码不写进公开记录；详细机器证据见`docs/delivery/b4d-build26-2026-10-13.json`。

首次签名后来源核验失败，原因是SDK37输出`V3.0 Signer: certificate SHA-256 digest:`，旧工具按第一个冒号拆分，误把说明文字并入证书字段；实际apksigner签名和原证书检查当时已通过。补充真实新/旧输出格式回归，新格式红测失败后修复为按证书摘要标记提取；8项来源与8项账本测试通过，实际签名文件重新核验通过。仅核验工具修复，不进入APK，不更换证书或重新造包。严格全条目对比也记录了标准签名META-INF新增，原代码/资源字节均不变。后续账本/工具提交不是另一个APK构建源，包内源码SHA保持c725310。

真实ARM安装、覆盖更新后提醒和用户手机数据保留仍NOT_RUN，不把静态验证或API35 debug测试冒称手机验收。安装前先加密备份，只接受同包名原正式证书的“更新”，不卸载/清数据。A/B VOIDED能力与C安全限制照本报告，不扩大医学宣称。PR23未合并，无公开Release/标签。

### 交付后仪器测试线程修复

交付后测试框架修复（2026-10-13）：b965273记录/来源工具HEAD的push Android38429993057全success，PR38430002274在既有UnifiedConcentrationAndroidTest出现CalledFromWrongThreadException，未冒称全绿；其Science PR38430002398 gate/deep实际success、push38429993004按原门控deep skip。栈证据显示Compose1.9.3测试默认UnconfinedTestDispatcher将frame/apply恢复到Default worker；仅该Android仪器测试规则改用StandardTestDispatcher，保留全部6项原断言/模型参数/时限，不改生产APK。两仪器依赖编译及assembleFullDebugAndroidTest成功（2m27s）。已等前轮全部结束，现提交本修复并等待本HEAD完整Android/API35/Science检查；结果以PR23实时CI为准，不借用旧绿色，不盲目重试或取消正常CI。已交付APK源c725310与哈希不变，无重建/重签/新交付。

