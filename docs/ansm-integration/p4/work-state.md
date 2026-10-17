# P4 工作状态

2026-10-17 共享UNKNOWN/生命周期检查点：data362（共享87）、reminder21、App选定40和Python159本地PASS；原生测试源码编译PASS，新增87三API实际运行仍待验。四项实际红例已归档，旧无票据VOID/RESTORE被禁止操作精确包装，独立有票据生命周期和ABA/UUID隔离已通过。库存/日历/提醒不再把整数quanta或未知开封期限解释为当前药物mg/确定天数/有效期；四语中性UNKNOWN UI不启用旧写入口。四语/320和411dp/1×2×/深浅/三对比度矩阵32项PASS，实际生成192张合成截图；已人工检查法语320dp大字高对比库存及繁中320dp大字主页样图，无确定药量/天数或写按钮。真机/TalkBack/断电NOT_RUN。db1ef03六项必需CI均SUCCESS（不代表新未提交源码CI通过）；主线仍f1e9b4d，#57 DRAFT。见unknown-lifecycle-contract.md与local/red证据。后续先完成实际计划服药和后实盘独立协调，不能以安全拒绝代替完整流程。

2026-10-17 新检查点：实际Repository服药事务/幂等/ALREADY_ACCOUNTED/UNKNOWN/未关联物理量持久化、零差额和ABA票据失效的完整data349/共享75/原生源码编译/Python159 PASS。新75原生、当前实际接线后的App回归NOT_RUN。正式UI仍OFF；修订/删除/恢复/实盘票据及统一预测待完成。见actual-use-local-evidence.json。

当前：#57 DRAFT。内部真实Repository包装创建/服药/更正/删除/恢复/实盘，完整data356/共享82/原生源码编译/Python159 PASS。b95a0d9六CI与三API共享75运行已核验PASS，修订批82原生与当前App回归NOT_RUN。更正遇后续独立实盘保守拒绝，独立协调、生命周期票据、共享UNKNOWN和UI仍需完成。正式用户入口OFF；主线f1e9b4d Room11/业务10不变。V4补充见REQUIREMENTS105及ansm-completion/BASELINE.md。

2026-10-16：IN_PROGRESS，生产库存未接线。当前复用P3的de26444，普通合入P1源945512a，不直接复制未合并源码。#54已受保护合并为c95cea1；#55待主线检查及更新。

源9ed9aaa纯domain140项PASS，包含新14项StockQuantum测试；集成后Python157项PASS。精确分数、不同物理量、成分/来源版本、标签容量未知、用户声明容量区分、2^53边界、连续反向及极端汇总抵消均覆盖。未修改旧Scalar账本、Room、备份、预测或UI。纯核心不是生产库存集成验收。

下一动作：冻结商品/容器包装载荷与来源hash；明确新模式整数投影、旧LEGACY_UNKNOWN隔离和恢复精确归并；实现与真实记录同事务的显式包装确认、CONSUME/REVERSE/ADJUST幂等及故障回滚；在任何新模式入口启用前完成备份版本拒绝、旧路径分配/resize隔离及日历/库存/提醒未知保护。具体边界见quantum-bridge-contract.md。

不将名义64泵当实际可用；不将用户资料声明当官方核实；不从PK或当前引用推断实际包装。附件/联网/PK/签名/发布均不动。真机及TalkBack NOT_RUN。此分支尚不可自动合并，不能把14项纯测试当成正式库存已完成。

新增候选包装载荷codec12项首轮10通过/2失败：一项用BigDecimal.equals错误要求相同scale（80与8E+1数学相同，实际存储plain文本80），改为明确plain文本80断言；另一项负向测试在调用codec之前就由canonical(Double)抛IllegalState，未进入预期验证异常。修复read将外部解析/类型错误统一为IllegalArgumentException，并把非法Double原始JSON送入codec，保留其必须拒绝断言。来源、单位、精度等断言未削弱；后续完整回归待运行。当前Room11/业务10及拒绝新模式的守卫未改。

候选容器事务适配器已在main源码准备，但仅由合成Room测试调用，NotesRepository/App没有入口，当前备份守卫仍明确拒绝新格式。创建事务先验epoch、独立UUID/不可变包装、整数容量，重试同op返回原容器，异载荷/owner/商品op冲突拒绝；源/容器创建故障同事务回滚。新增8项真实Room测试首轮7通过/1失败：测试直接写VOIDED被既有Package correction evidence守卫正确拒绝。改为调用真实PackageCorrections.void/restore，继续断言证据追加、重复创建不恢复库存和来源不变；未改守卫或生产VOIDED行为。正在完整回归。未来新库存格式需要版本门，当前Room/业务版本未改变。

源783606a完整data257/domain140 PASS（0失败/错误/跳过），原生测试源码编译PASS。容器创建8项真实Room全绿，含故障注入、scope/UUID冲突、旧库存不变、真实VOIDED/恢复和已删除ID复用。不是正式接线完成。随后将同一8项抽到historyTest共享合同并增加关闭/重开保留来源/原生加密头断言；Robolectric和SQLCipher共用9项，现有CI三API清单只追加新类、不替换旧类。新共享合同待本地及CI真实执行，不能以旧8项通过冒充原生9项通过。

源13828f0共享合同回归完成：data258/domain140零失败/错误/跳过，新的9项容器合同在JVM PASS，原生编译PASS；SQLCipher三API实际执行仍NOT_RUN。旧SupplyLedger/StockForecast/PackageCorrections源码逐字未变，PK tree与c95cea1相同，摘要保存在local-evidence.json。新模式仍没有Repository/App入口、当前备份仍拒绝PRESENTATION；不得自动合并草稿或称正式库存已完成。后续先合入P3/P2真实依赖和Gate3自检修复，再验证完整整合分支。

源5bcf8d3已普通合入P3最新e1c40bd（含P2主线b5d9751与Gate3对照顺序修复），完整整合data264/domain140/App75全PASS，原生编译PASS。PR #56已六检查成功并保护合并b5d9751；完整App1379零失败205条件跳过/data243/reminder20/Period5及192图的CI摘要/CRC已校验，证据p2/ci-evidence.json，主线复验仍待结果。当前P4新模式仍未启用。

进一步消除适配器误接线风险：create只接收单一NotesDatabase，由其同时获取DAO和SQL连接，禁止独立传入来自两个数据集的DAO/SQL。共享合同增加相同整数ID不同数据集票据的拒绝与另一库完全不写入检查；新10项容器合同待运行，不以旧9项通过替代。PUBLIC Repository/App入口仍不存在。

Room版本门先红测试源37f84cb真实FAIL：旧Room11读取器成功打开含新整数包装的数据库，预期拒绝断言失败；机器证据room-fence-red-evidence.json。未修改生产代码制造失败。随后在重核开放PR（只有本PR涉及新迁移，主线Room11）后实现候选Room12：11→12不修改表/旧行，仅拒绝未识别旧格式计量数据并验证既有引用。业务备份仍10、PRESENTATION正式接受仍OFF。既有迁移断言只将当前目标11更新12，Room10/旧备份夹具保持；覆盖链扩大1…11，故障注入的后续路径显式补11→12，未删除原断言。新版本源码尚未验收，不宣称迁移/CI成功。

源7fcc671同一Room11旧读取器门测试转绿，1/1 PASS；生成的Room12导出与Room11的45实体及views逐项相同，未改旧表结构。业务备份仍10，新包装接受仍OFF。继续增加11→12故障DDL注入回滚（保留旧引用/epoch）及旧11未知计量数据拒绝/原库不清除，并完整回归全部原有迁移。目标拒绝测试成功不代表三API/全回归通过，随后记录实际结果。

源e4d5ddb完整Room12验证：data268（含迁移6/容器10/包装12）零失败/跳过，Period5+Privacy3通过，原生源码编译PASS。目录更新、药物当前引用、PK、原Scalar账本/预测与正式库存入口均未改；模式未启用。Room12源尚未通过远端当前CI，不得套用旧5bcf8d3六绿灯。

Room12检查点0843450六项CI在push/PR全部SUCCESS；当前仍DRAFT，不以此替代后续代码验收。后续MeasuredUsePayload六项本地PASS，旧Double剂量仅以原始IEEE754位保存上下文，实际库存量为独立精确Quantity。MeasuredConsumptionStore新增八项真实Room测试PASS：精确分数、相同UUID幂等/冲突、不同规格逐项事实不求和、未知换算不扣、耗尽余量、外药物/旧epoch拒绝及新记录/来源/流水故障全回滚。适配器仍无Repository/App调用者，当前备份仍拒绝此格式，旧SupplyLedger/预测未改。正在增加原始量逆操作与故障回滚；没有启用正式库存。

源7320a89新增精确消耗/原量冲销/实际剩余调整的12项合成Room事务测试及Python157项边界守卫PASS。旧剂量原始Double位仅保留上下文，物理库存量独立；重复op冲突拒绝，调整CAS拒绝覆盖并发实际消耗，零量也检查量纲，原量逆操作与分配故障完整回滚。证据consumption-local-evidence.json。当前将同12项抽到historyTest共享合同，JVM改用真实文件库，SQLCipher原生类与CI只追加清单；完整回归/原生执行尚待本次新源码结果。没有启用正式入口、备份格式接受或预测。主线f1e9b4d六项检查已全部成功。

0843450远端报告已下载并核对GitHub摘要/ZIP CRC：API26数据127、API29数据127、API35数据194，均零失败/跳过；每个API均包含引用21/迁移6/容器10合同。App1379零失败205条件跳过/data268/reminder20/PK40，机器证据room12-ci-evidence.json。该源不含后续消耗适配器，不能套用为最新代码验收。新共享合同完整JVM数据286全部通过（零失败/错误/跳过）；原生编译待结束，原生新增12合同尚未执行。

共享消耗12合同在701fb52本地完整data286及原生测试源码编译均PASS（不是原生运行PASS）。新增回执负向合同先红：字符串版本号被误接受（Expected INVALID_SOURCE）；改严格类型/封闭字段/规范整数/范围/UUID后13合同全部PASS，7种伪造载荷均拒绝且原消费不变，机器证据receipt-red/green-evidence.json。未来备份仍需按不可变包装/分配/原流水交叉验证，不能只靠此结构解析。

旧路径首轮3个负向合同实际FAIL（旧分配扣了新包装0.1刻度、旧剩余量接受刻度、药物resize把56刻度改100），证据legacy-isolation-red-evidence.json。随后按容器历史PRESENTATION稳定身份排除旧分配/resize，并拒绝旧修改/冲销入口处理计量记录；正式旧标量历史不重写。完整data290（共享消耗16）零失败/错误/跳过，原生编译PASS，Python157 PASS，逐文件摘要见legacy-isolation-green-evidence.json。此前“旧SupplyLedger未修改”是历史检查点，本次开始明确的旧路径隔离改动，非正式入口启用。剩余replace/discard旧入口隔离、备份/预测/修订UI仍待完成。

更换包装入口负向测试先红（旧replace确实关闭计量来源），补require保护与旧discard保护后共享20合同PASS。新增删除后整数ID复用仍走旧模式、已计量记录不得再旧分配、明确有向mg→泵换算/用户60泵而标签64泵、不同酯来源不足拒绝均PASS；证据replacement-red/green-evidence.json。新增手工实盘后逆操作不得改写实盘结果的负向测试正在运行，尚未记PASS。

原量冲销会覆盖后续实盘的负向测试实际FAIL（Expected OPERATION_CONFLICT）；现对受影响容器任何ADJUST观察保守拒绝新的逆操作，包括没有流水差额的实盘，同UUID已成功逆操作仍可重放。完整data296（共享22）与原生源码编译PASS，Python157 PASS；Period/Privacy本轮仍待完成。观察发生在消费之前也暂时保守阻断，不能声称完整协调已实现；下一步为明确账本因果锚与请求/执行捕获分离，避免依赖时钟。证据observation-red/green-evidence.json与observation-reconciliation.md。

源b3ba292完整data296、共享22、原生源码编译及PeriodStability5/Privacy3全部PASS，零失败/错误/跳过；完整构建6m35s。当前源已推送#57，最新六CI待运行，不能套用0843450绿灯。下一步按明确账本头锚精化观察先后判断，不修改旧记录/库存/PK。

新增“较早观察不应阻断较晚消费逆操作”先红（b3ba292保守策略返回OPERATION_CONFLICT），证据anchor-red-evidence.json。当前本地候选将ADJUST稳定用户请求与执行时账本头分离、严格解析并按因果锚判断；初版辅助函数漏写return导致编译FAIL（非测试失败），已修正重跑，结果待验。没有更改Schema/备份接受或正式入口，不能把候选代码写成生产集成完成。

因果锚完整数据层回归data300（共享26）、原生源码编译与Python157全部PASS；本轮Period/Privacy仍在执行。ADJUST稳定请求与执行锚分离，重放不重采；先于消费的零/非零实盘允许逆操作，包含原消费的观察拒绝未经协调逆操作；倒退时钟无影响。不存在锚先红（Expected INVALID_SOURCE），补数据库存在性/容器归属检查后，非法/溢出/不存在/异容器7类锚均拒绝且两个容器余额不变。证据anchor-*-evidence.json。尚非完整备份历史验证；补录是否已计入实盘及ABA确认票据、正式更正UI仍未实施，正式入口/备份接受保持OFF。

单容器只读投影首轮共享30项及原生源码编译PASS，Python157 PASS。包含有向换算重核、来源/量纲/物理量与整数流水配对、ADJUST/CONSUME/REVERSE重算、缓存差异只报告不修复、缺失配对流水拒绝。随后补严格REVERSE封闭字段/版本、当前记录修订边界及多包装原始/逆操作集合完整性；新增31项当前完整data回归运行中，不能套用首轮30成功。PRES备份接受仍OFF，完整全局验证尤其零效果/未知操作仍未完成。

单容器投影当前完整data306（共享32）/原生编译/Python157全部PASS。新增故意缺另一容器逆操作证据的合成恢复状态：通过既有原子maintenance helper构造，全部可信trigger DDL安装回去并逐项相等断言后，审计本容器仍必须拒绝整个不完整逆集合；既有记录/四条流水及两个余额保持不变。没有弱化生产守卫，没有自动修复缓存。证据projection-final-evidence.json。Period/Privacy上一源2d2a289八项PASS，本次只增加无调用者只读审计；最新完整CI待当前源。

2026-10-17续：重新核对默认分支f1e9b4d及开放PR，无其他库存/数据并行修改。源c7daddf六项必需CI在push/PR全SUCCESS；四份原生/JVM报告已校验官方SHA256、ZIP CRC且不含APK。API26/29数据各159、API35数据226，32项共享计量合同及10容器/6迁移/21引用合同在三API均PASS，PK124/domain140 PASS，证据projection-ci-evidence.json。新增暂存库SQL复用改动与3项合同仍在本地运行，不套用c7绿灯；正式备份接受、全局历史验证、实际记录入口和新预测仍未实施。

暂存库复用最终本地验证PASS：data309/domain140、共享35、原生源码编译、PeriodStability5、PrivacyNoticeFacts3、Python157；所有实际运行零失败/错误/跳过。应用首个筛选只匹配PeriodStability，未把不存在的PrivacyBoundaryTest计为通过；随后明确补跑真实PrivacyNoticeFactsTest并保存独立XML摘要。源哈希/日志摘要见staging-local-evidence.json。共享SQL拒绝数值列文本，不恢复本地epoch，不修改源/暂存业务数据；PK、正式恢复、备份版本、数据库版本、预测及Manifest相对c7均无改动。当前提交新CI待验；继续全局候选恢复校验，#57保持DRAFT。

续作§104已归档原文及SHA256（5128e10f…49c8）。10480c6最新六CI全PASS，三API共享35项实际PASS，见staging-ci-evidence.json。全局扫描首轮共享40 PASS；UNKNOWN无流水伪造与未来同余额实盘头两项负例均实际FAIL并留证。正在补序列化操作因果链并跑共享41，新源码尚未验收，正式恢复/入口继续OFF；下一步不停止为单容器研究报告，按global-audit-progress.md完成全局/恢复/真实接线。

因果链候选41项共享JVM已PASS（global-chain-green-evidence.json）；未来头负例在重算链摘要仍合法后拒绝。新增修订号4294967297被getInt截成1、无分配真实剂量与冻结输入不一致两项负例实际FAIL（global-record-red-evidence.json），已补Int上界/同revision原事实精确位比对，43项共享回归正在运行。当前仍非完整全域/退役/备份接受验收，旧默认PRODUCT-only守卫保留。

43项共享合同已PASS；GLOBAL候选仅引用校验遗漏既有VOIDED生命周期，真实负例FAIL（global-lifecycle-red-evidence.json）。当前复用BackupValidation原有全业务只读校验，不复制/放宽PackageCorrections守卫；补严格存储类型/总行数、跨参考事件操作UUID冲突及退役容器/记录整数ID复用。49项共享及原生源码编译正在运行，退役身份从不按original_handle重连当前新行；有分配却退休且仍连整数账本的状态拒绝。默认业务10/旧恢复拒绝新格式保持。

当前全局候选最终本地完整data324、共享50、原生源码编译、时期稳定性5/隐私3及Python158全部PASS；机器证据global-full-local-evidence.json。GLOBAL逆操作越过独立实盘的负例确实FAIL，随后按操作因果链筛选逆操作之前的观察并重用原包装/原数量保护，转绿。所有红日志保留，不将新源码当已有原生CI成功。下一步实际实施stock-encoding-version-contract.md的独立编码及Schema11；不能仅删除拒绝门或只根据外部引用是否还在来解释Double库存。

库存编码结构负例实际FAIL（encoding-red-evidence.json）后，候选Room12新增inventory_encoding默认LEGACY_SCALAR，明确新建才INTEGER_QUANTA_V1；业务Schema11写入/旧格式拒绝门同步准备，精确恢复仍OFF。原缺引用表负例已1项转绿。初轮全325项12FAIL如实保存encoding-compat-initial-evidence.json，涉及旧版本断言、发布前夹具读列及允许新列的精确DDL比较。

真实旧VOIDED v1快照恢复另复现PackageRestoreConflict（encoding-old-void-red-evidence.json），非模拟器偶发。当前保留旧v1的Legacy字段/上下文哈希比较，新v2审计显式稳定容器UUID并追踪已知身份；原消费/上下文变化守卫不放宽。旧Room夹具使用测试专用PublishedBusinessFixture读发布列；旧37实体所有旧值/位与DDL仍比较，只精确剥离本次新增的单一DDL片段并单独断言默认Legacy，未放宽其它结构。当前完整326项及原生源码编译运行中，新Schema11兼容性尚未PASS，不启用、不合并。

库存编码检查点最终data330（共享56）/原生源码编译/Period5/Privacy3/Python159全部PASS。旧v1VOID证据已通过实际Repository恢复；v2每条历史审计都校验稳定容器UUID，含已恢复状态的伪造UUID负例确实FAIL后转绿；旧9/10导入、旧最大9/10 reader拒绝11、版本错误先于目标库打开及编码双向UPDATE禁止均覆盖。新SourceMode字段为存储标记，不是官方身份/单位推断。来源指纹见encoding-final-local-evidence.json，原失败保留。当前未连接精确备份/实际写入UI；下一步严格JSON整数词法、不可变生命周期前缀与完整精确恢复，不能套用旧0a418eb原生50通过当新56通过。

2026-10-17 严格输入：完整data334/共享60 JVM PASS，原生源码编译PASS；尾随JSON真实红例与夹具初始化顺序失败均已归档。Python159 PASS（首次从仓库根直接调用产生3个导入错误，改用PYTHONPATH=scripts及discover后完整159运行通过，未改测试）。API35旧固定业务版本断言正在修正并补充Legacy标记断言；公开精确恢复/新UI仍OFF。见strict-backup-input-contract.md。
