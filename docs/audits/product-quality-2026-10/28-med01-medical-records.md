# 28 — MED-01 医疗档案现状审计与A独立验收

> 代码完整验收通过；实施过程中的待测/失败记录保留，最终结果以末节与交付PR正文为准。最终报告HEAD自身CI必须全绿后才交还。

基线fe8798634ccd0d92d2913f384abfcfff94d32274；Android38470156117 attempt2三作业success、Science38470156282 gate success/deep按纯文档规则skip。R15 PR25 OPEN，HEAD239a73c，独立分支保留，不复制未合并功能。MED-A独立分支，尚未实施/验收。REQ87。

| 已有能力 | 真正缺口 | 可复用组件 |
| --- | --- | --- |
| Visits/Appointment：医生practitioner、机构location、类别type、带zone的UTC时间、note，completed_utc只表示用户确认，不因过去自动完成 | 集中档案入口；过去的预约≠真实就诊；一次原子录入过去就诊与确认，成功前不关窗、失败后不重复写入 | AppointmentEntity、VisitsScreen、VisitDetail、DateTimeRow、既有事务 |
| visit_question保存问题/回答及显式ASKED状态；VisitPack保存不可变生成元数据，PDF实际在用户SAF目标 | 单独问题删除没有独立回收（既有R26），不是完整医疗文件归档；不得把回答解读为诊断 | 就诊删除既有Trash.APPOINTMENT原子快照包含三表，恢复同ID及关联；本期整次就诊恢复复用 |
| Labs有数值/单位/方法/参考范围/采样UTC+zone/机构/备注；LabContextEntity保存版本化采样上下文 | 原件缺失；无“属于本次就诊”的显式FK，日期邻近不能推断归属 | LabsScreen、LabDialog、LabContextSection，保留同一lab ID，不复制化验 |
| Longitudinal有规则版本/真实服药/化验/预约/里程碑事件及历史快照 | 汇总时间事件不是结构化诊断/文件库 | 链接现有HRT时间线，只浏览上下文，不建立隐含就诊归属 |
| PDF/CSV/VisitPack离线通过SAF输出，已有明确隐私告知；PDF是生成摘要 | 输出的文件不在app安全附件库，不能把SAF URI/路径当永久证据 | ExportPrivacyNotice、Session.launchPicker；B需新的可恢复稳定对象ID |
| SQLCipher数据库，Keystore AES-GCM包裹随机32字节DB密钥；PRIMARY/DECOY独立库/key/alias，应用FLAG_SECURE/锁定 | 当前DECOY是保留兼容的旧空间，不能假设伪装登录打开该库；full伪装为独立加密私密笔记，不显示医疗页 | DatabaseAccess.pinnedTo、现有Session和PrivateNotes，保持现有认证路由 |
| PNBAK1 Argon2id(32MiB/3轮/p1)+AES-GCM，JSON表数据整包；最大文件/JSON32MiB、100000行、单字符串1MiB，恢复验证后事务替换 | 不适合大量BLOB/base64文件（膨胀/内存/上限），不能偷偷增加附件目录后仍叫完整备份 | A新增记录在既有三表中正常备份；B独立版本化容器设计保留旧PNBAK1读写 |
| SAF导入、B2私有临时SQLite复制锁/白名单清理/进程死后清理、密码清除、恢复防迟到回调 | SQLite临时明文策略不是附件默认安全方案；PDF随机访问与缩略图cache必须另验 | 可复用操作生命周期/清理边界，不复制明文附件到cache |

## A实施边界

用户确认“已经发生”才写completed_utc；就诊实际时间仍at_utc，确认时间与发生时间区分。过去未确认预约可从档案打开既有详情明确确认，不伪造事实。历史编辑保留原zone；日期范围按该zone的日边界，稳定UTC/id排序。关联问题、回答、生成包始终保留原ID；化验只作为用户选择范围内上下文，详情复用现有化验组件。整次就诊删除/撤回二次确认，删除到原回收站，可原子恢复；自由文本只存用户所写，不解析医学结论。

现有“单独问题删除无独立回收”是审计发现的旧缺口，MED-A医疗入口不新增该不可恢复删除路径；整次就诊删除及恢复包含问题。以后独立R26/附件元数据规则扩展再确定。

B设计见29，不实现文件导入/预览、新表、新备份格式或加密机制。后续红测、失败修复、全量结果及最终SHA/PR/CI必须用实际结果补充，不能用本审计代替代码。

## 红测与设计阶段记录

scope89a9277 Android38489468115三作业、Science38489468353均实际success。SDK28仓库13项红测实际13失败、0skip，13.093s，构建1m5s；临时UnsupportedOperationException seams只用于红测，生产提交前替换。两次测试返回类型写错的编译失败不当作红测；已按saveLab实际Unit返回，改为检查持久化lab ID，原业务断言未减少。四语UI/重复提交红测仍在运行，尚不计作通过。

MED-B设计29完成：推荐元数据+逐对象/版本DEK分块AEAD、独立空间KEK及可移植密码备份；未知SAF长度用认证结束清单而不是提前猜总长。PDF公开ProxyFD无明文预览必须实证，失败不采用明文缓存。新Schema/备份/加密及配额/旧备份关联恢复均等待产品批准，不阻塞A。没有实施附件机制。

## A首版实施与目标验证

历史录入/编辑复用appointment，原子写入明确确认，at_utc实际发生时间与completed_utc确认时间分开；完整预期记录比较拒绝陈旧编辑/撤回/删除。保存协调器区分“事务已提交”和后续提醒/刷新失败，busy+完成确认防止重复插入；错误保留草稿，取消不写。旧未知类别保持直到用户明确选择，医生/机构/日期/备注可纠正。新Medical入口复用Visits详情、原化验值/上下文、HRT时间线、现有回收站；MED详情不提供旧的单独问题不可恢复删除。

UTC/id稳定排序；未确认过去预约独立分组，不推断为就诊。编辑保留原zone与未更改的精确UTC；DST gap不静默平移、overlap要求明确offset，原有精确instant和用户显式改选offset都正确保留。范围化验按就诊zone的日边界，仅上下文，明确不建立归属；原LabDetail/Context复用，未复制数据或产生诊断。

实际本地：SDK28数据合同18/18（18.143s）、SDK35原生四语UI36/36（36.725s）、索引/时区6/6、保存协调器3/3、DataRefresh8/8（10.703s）、AppShellBackNavigation7/7（25.407s，包含所有Destination及新MED页）零失败/零新增skip。UI实际320dp宽/2倍字号，确认/取消/错误草稿、原zone/ID、原化验详情/明确日期上下文、回收站入口和TalkBack Button role验证。

失败修正如实保留：UI测试两处语法/类型编译问题没有形成有效红测；随后业务执行60项有5失败，4项来自assertTextContains默认精确匹配误用，改为验证完整本地化zone标签（更完整，并未删掉断言）。1项DST显式offset旧编译产物仍采用原instant，构建期间源代码改动造成产物没有反映修正；冻结源码并强制compileFullDebugKotlin重新构建，原业务断言全部通过。数据重启新测试曾返回Boolean导致JUnit初始化失败，补Unit类型；invalid zone为DateTimeException，测试明确识别此拒绝类型，保留无写入断言。旧类别兼容及明确offset选择均补防回归。

新增API35主套件方法及外部force-stop新PID脚本：仅专用synthetic主/旧DECOY两个命名库、合成就诊/问题回答/化验及PNBAK1，证明重启/恢复/回收及隔离，不pm clear真实应用数据；主套件无新增Assume/skip，真实设备结果待CI。生产SQLCipher/锁定/当前伪装笔记认证路由完全保持，合成Room fixture不冒称新的加密机制。

MED-A未改Schema/实体/PK/正式版本/签名/PNBAK1；MED-B29仅设计且审批项齐全。下一步完整JVM/Android/lint/Debug/unsignedRelease/API35/Science及B2/B3/B4回归，在最终HEAD上验证后创建PR和最终独立报告。当前目标通过不等于完整验收完成。

## 全量发现修正与时区可纠错

968ffa0的JVM和API35成功（新PID脚本实际PASS），但Android与本地全data因共享HistoricalVisitContract误声明open、被直接当作无运行器JUnit类发现而失败。SDK28 wrapper的18项本身通过；API35也重复执行了裸base和wrapper。因此改为abstract共用合同，仍由SDK28/API35 wrapper各执行同一18项，不加Ignore/skip或测试排除、不删业务断言。全data实际127/127通过；不是把首轮968ffa0失败称成功。其设备artifact11863154906 digest be5b341c9f81381872d401f0111211b7c118b8786251523adc34ef1be5b81dba，原始控制台Finished161重复计8个原skip事件，报告应读HTML而非把其当新增测试。

补可搜索时区选择，复用at_zone、不新增表；选区按新zone解释当前本地日期/时间，有明确说明，原记录默认精确UTC不变。可主动纠正时区，UTC差异/DST不猜；列表有界LazyColumn、帮助/候选滚动，选定隐藏键盘，避免小屏/2x挤掉候选。新增每语1项，共四语UI40/40，加时区索引7/7、保存3/3、刷新8/8、导航7/7，共65目标项全部零失败/0skip；完整JVM194已通过。下一步完整app/reminder/lint/Debug/两仪器包与新HEAD完整CI/unsignedRelease/API35，不提前验收。

A继续使用既有日期+时间模型；不把“仅知日期”暗变为精确医疗时刻，未来date precision及结构化就诊原因/经过/随访字段应另行设计和批准。本期用户明确核对表单的日期/时间/zone；没有解析自由文本医学结论。

## 完整代码验收

代码SHA `b793ded2c004556af0dac3cff231c49aa9f0be09`。

| 检查 | 实际结果 |
| --- | --- |
| 完整JVM | domain58/PK124/importer12，共194，0失败/0skip |
| 完整Android原生 | data127/reminder17/app839，0失败；app61项原条件skip，新增skip为0 |
| 医疗仓库合同 | SDK28/API35各同一18项全部执行通过；裸共用base不作为无平台测试重复发现 |
| 四语320dp/2x UI | 简中/繁中/英/法各10项共40，原生SDK35和API35全部执行通过；实际宽度测量及Button role |
| 时间与保存 | 索引7、保存协调器3、刷新8、导航7全部通过；DST gap/overlap/显式offset/跨区日期/重复确认/已提交后错误 |
| API35完整套件 | data33/0失败/0skip，app157/0失败/8原条件skip；8原重启方法通过独立脚本真实执行 |
| 医疗新进程 | 主套件1/1、外部prepare/force-stop后verify各1/1；不同PID、原ID/zone/问题回答/数值上下文/PNBAK1/回收还原/旧空间隔离 |
| 构建 | fullDebug与data/app仪器包本地完成；CI unsigned fullRelease及来源/干净检出/清单无INTERNET/交付账本通过 |
| Science | 代码push gate success/deep按路径规则skip；最终PR deep必须实际执行成功，不借用旧绿色 |

lint：0错误，178项警告（完整列表以HTML/XML报告为准，不抑制）。

Android：https://github.com/DevenirTwilight/HRT-Log/actions/runs/38497285919 。Science：https://github.com/DevenirTwilight/HRT-Log/actions/runs/38497285943 。最终文档提交/PR的精确HEAD和其自身CI固定于交付PR正文及独立最终报告，避免追加自身SHA循环。

独立下载API35 artifact11864487520，SHA256 `36e9f81c5520c7901d4b75ebb7f9c73635aa8ebc282375fa90336066a2fe3b89`与GitHub匹配；直接读取HTML确认data33/app157、医疗合同18/UI40无失败/跳过。控制台Finished165重复计入8项skip结束事件，不当作额外用例。日志确认B2认证/明文清理/真实密码检查点强杀及新PID、B3日历返回、B4预测/VOIDED备份恢复、B4-E136次视口测量最多3个挂载节点和新医疗进程证明全部通过。

## 功能清单与逐项验收

| 要求 | 实现/证据 |
| --- | --- |
| 集中医疗档案 | 新顶层入口，原appointment/lab/HRT数据视图，不复制临床表；原未来预约仍在Visits |
| 查看过去就诊 | 已确认与未确认过去预约分组，不能把时间过去推断为就诊；组内UTC/ID降序、LazyColumn稳定key |
| 新增已发生就诊 | 明确过去日期/时间表单，事务内同时存一次原预约和确认时间，拒绝未来时刻 |
| 文字/日期/归档类别 | 医生、机构、备注、原类别（含旧未知非空类别）可编辑；陈旧完整快照拒绝，原ID/确认时间及子记录保持 |
| 时区与精度 | 默认原zone及精确UTC，用户可搜索并明确纠正zone；同一当地日期/时间按新zone解释，文案说明；DST重叠要求选择offset，gap拒绝 |
| 撤销与删除 | 撤回确认保留原预约/备注/问题；整次删除二次确认、现有回收站入口，可恢复原ID与问题/回答/不可变Visit Pack元数据；重复/陈旧安全拒绝 |
| 化验上下文 | 用户选择日期范围、明确zone及“范围≠归属”，日边界与下一日exclusive，原lab ID/数值/保存上下文详情和原编辑流程；无自动关联或临床解释 |
| HRT/导出复用 | 可进入现有HRT时间线/化验；原VisitPack/PDF/CSV仍是用户选定生成摘要，不冒称文件原件档案 |
| 取消/失败/重启 | UI取消0提交；事务注入失败回滚；已提交后提醒/刷新失败仍确认保存、阻止重复；文件库重开与外部新PID证明 |
| 数据不丢/隔离 | 既有预约、lab/context、问题/包、旧类别、回收和PNBAK1往返保留；NotesViewModel原PRIMARY固定及独立旧DECOY store fixture；真实伪装私密笔记/SQLCipher/锁定行为原回归保持 |

## 兼容边界及负责人决策

MED-01A无Schema、实体列、正式PNBAK1、加密参数、PK内核、versionCode26或签名配置改变，没有导入真实PDF/照片/处方、修改真实用户医疗数据、签名/交付APK或自动合并。R15 PR25/独立分支HEAD239a73c未改；本分支从主线fe879863，不混入其未合并代码，不能冒称MED分支执行了尚未合入的R15新增测试。

当前PNBAK1仍32MiB整包限制；就诊原因/诊疗经过/随访用用户自由备注，不变成app诊断。日期仍既有日期+时间模型，只有日期未知具体时刻的精度字段及结构化扩展需要后续方案/批准，不能伪造精确时刻。原单独问题删除的R26缺口仍在普通旧Visits，MED入口不新增这条不可恢复删除；整次就诊回收完整包含问题/回答。真人TalkBack及真实ARM性能基准NOT_RUN，自动化语义/四语/小屏/2x/API35实际验证不等同于这些实测。

MED-01B设计29是独立技术方案，未实现新表/加密/导入/预览。推荐加密元数据+独立分块AEAD对象：比较BLOB与逐文件成本、各空间KEK/版本DEK、未知SAF长度认证结束清单、原子generation恢复、去重/替换/回收/损坏、无明文预览POC、独立流式医疗包保留PNBAK1、异常/进程死/URI隐私边界均给出具体步骤。

需要负责人批准：新元数据/版本/链接/恢复journal迁移与密码学POC审查；新完整医疗包及旧PNBAK1恢复附件隔离/人工重新关联政策；提议容量与版本/回收清除策略；PDF无明文ProxyFD预览实证后才启用及密码PDF/分享首批范围；是否确需独立伪装医疗库；未来就诊结构化及date precision。正常MED-A没有阻塞性产品决定；未因B等待审批停下A。

## 新增/修改文件

- `.github/workflows/android.yml`
- `app/build.gradle.kts`
- `app/src/androidTest/java/net/plainnotes/app/medical/MedicalRecordsAndroidTest.kt`
- `app/src/androidTest/java/net/plainnotes/app/medical/MedicalRecordsRestartAndroidTest.kt`
- `app/src/main/java/net/plainnotes/app/NotesViewModel.kt`
- `app/src/main/java/net/plainnotes/app/medical/MedicalRecordIndex.kt`
- `app/src/main/java/net/plainnotes/app/medical/MedicalVisitSaving.kt`
- `app/src/main/java/net/plainnotes/app/ui/AppShell.kt`
- `app/src/main/java/net/plainnotes/app/ui/LabsScreen.kt`
- `app/src/main/java/net/plainnotes/app/ui/MedicalRecordsScreen.kt`
- `app/src/main/java/net/plainnotes/app/ui/VisitsScreen.kt`
- `app/src/main/res/values-b+zh+Hant/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
- `app/src/main/res/values-zh/strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/medicalTest/java/net/plainnotes/app/medical/MedicalRecordsUiContract.kt`
- `app/src/test/java/net/plainnotes/app/DataRefreshTest.kt`
- `app/src/test/java/net/plainnotes/app/MedicalRecordIndexTest.kt`
- `app/src/test/java/net/plainnotes/app/MedicalRecordsUiTest.kt`
- `app/src/test/java/net/plainnotes/app/MedicalVisitSavingTest.kt`
- `core/data/build.gradle.kts`
- `core/data/src/androidTest/java/net/plainnotes/app/data/HistoricalVisitAndroidTest.kt`
- `core/data/src/main/java/net/plainnotes/app/data/MedicalVisitConflict.kt`
- `core/data/src/main/java/net/plainnotes/app/data/NotesRepository.kt`
- `core/data/src/medicalTest/java/net/plainnotes/app/data/HistoricalVisitContract.kt`
- `core/data/src/test/java/net/plainnotes/app/data/HistoricalVisitTest.kt`
- `docs/HANDOFF.md`
- `docs/REQUIREMENTS.md`
- `docs/audits/product-quality-2026-10/28-med01-medical-records.md`
- `docs/audits/product-quality-2026-10/29-med01b-encrypted-attachments-design.md`
- `docs/audits/product-quality-2026-10/README.md`
- `scripts/run_medical_records_restart.py`
