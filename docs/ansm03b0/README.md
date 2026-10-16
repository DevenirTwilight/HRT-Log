# ANSM-03B0 工作交接

2026-10-16。状态：TEST_ONLY；生产结论 PRODUCTION_BLOCKED。
本任务明确只授权隔离集成、合成数据、候选SQL/备份验证和审批报告，不批准03B1生产修改。
本目录代替并行开发共享HANDOFF/REQUIREMENTS记录新增要求与阶段进度，不修改正式UI。

## 基线与依赖

默认分支claude/new-session-1959qb，2f4b958425e1cd67dad1d97deb8ab724eb8ecac1。
PR45 OPEN/348c68def84ccc627a46be57ace463ade3635a15，PR48 OPEN/df0a615f78589e9c7df5352fda75f2364ebcddaa，
PR50 OPEN/606696edbccd6583a50d1fc28d6ed8069b2fcb5c；三个均六检查通过，但均没有review/comment或合并。
最近49仅交付账本/文档；未发现其他开放库存/迁移开发PR，无法确认未推送的其他工作。
本分支stacked于50固定HEAD，后续PR以50分支为base；不复制01/02数据或UI、不自动合并父PR。
50若先合并，须在单独集成步骤重新核对base与diff，不把未合并父代码当正式主线。
许可版本、FT/ST解释、02B正式接口/03B1具体授权仍未具备；当前只作来源/审批阻断，不分发官方临床原文。

## 真实数据流（静态审计基线）

用户计划完成/手动记录→NotesRepository事务保存RecordEntity及MedicationSnapshot v2→SupplyLedger按open(opened_on/id)、sealed(id)分配，
CONSUME触发器维护used，短缺存unallocated；ViewModel读取一个提交快照→StockForecast遍历未完成Timeline槽→日历/库存/StockAlerts共用。
StockForecast同med的IN_USE/SEALED标量相加，窗口366日、低库存7日、日期差按当地日历。
批量backfill/导入计划关联不再扣库存。实际记录更正/删除先REVERSE旧消费，再修订/重分配；逆操作用原delta，不按当前规格重算。
CONSUME/REVERSE/ADJUST append-only。PackageCorrections拒绝有实际CONSUME/REVERSE的容器VOIDED，允许仅ADJUST历史，恢复核对原账本与上下文哈希。
MedicationSnapshot无CIS/CIP；ContainerEntity无独立单位/规格/来源快照。saveMedication会reconcile且显式resize可改容量；未来引用更新不得调用此路径。
Room10与PNBAK1业务schema9分开，DOMAIN_TABLES/列白名单显式；旧恢复有既有缓存重建，不是新计量重算授权。

审查发现的边界：内部SupplyLedger.allocate不接受业务opUUID；公共complete靠slot已完成检查幂等，reverse靠reversal_of去重。
新业务UUID冲突拒绝不能假称已由现有入口统一提供。未知旧快照单位目前可以继续旧标量分配；影子验证应返回UNKNOWN，不能反过来改历史。

## 隔离集成计划

- 原03A的MeasurementCore.kt及35项测试逐字保留，先复核/重跑；新ShadowMeasurementAllocator在domain test源集。
- core/data Gradle仅增加test/androidTest源码配置：Sync任务从原文件生成测试编译输入（明确两个文件），不生成main源码、不加依赖。
  公共合成合同放src/test/shared/java，单位测试用Robolectric，设备测试使用独立文件名与合成SQLCipher密钥。
- CI只扩展原API26/29类列表（保留全部既有类）及独立release隔离检查，不改六检查、权限、条件或原断言。
  不改与PR48共享的check_attachment_release_isolation.py，新增独立检查器。
- 影子仅接不可变DTO/原分配观察值，无Repository/DAO/数据库句柄、IO或写接口。
  真实写操作只由隔离测试调用既有Repository/账本；验证前后全表指纹证明影子无副作用。
- 候选表全部ansm_lab_命名，仅在合成数据库测试内创建；PRAGMA user_version仍10，正式Room/schema/PNBAK不动。
- 产物区分TEST_ONLY、DESIGN_VERIFIED、INTEGRATION_VERIFIED、PRODUCTION_BLOCKED；PRODUCTION_IMPLEMENTED/RELEASED均否。

## 阶段记录

审计与隔离计划完成；原35项基线重跑PASS，源码/断言哈希不变。
新增只读影子23项、真实Repository/Room合同13项、提醒边界1项，完整domain121/data193/reminder20本地PASS。
真实集成揭示非空REVERSE账本恢复外键拒绝、候选域残留及守卫丢失；均未改生产，详见[兼容评估](compatibility-findings.md)。
首轮API26/29/35新增原生合同各13项通过；首轮Android lint生成依赖失败已定位并补齐，最终六项CI以PR #51的最终HEAD/正文为准。见[验证账本](verification.md)。
[准入与四PR方案](production-admission.md)及[统一预测合同](forecast-contract.md)已提交供审批。

## 本批文件范围

- core/domain/src/test/.../shadow/：只读影子与23项负向/边界测试。
- core/data/src/test/shared/java/.../Ansm03b0*：真实业务共同合同与候选SQL；test/java、androidTest/java两个入口。
- core/reminder/src/test/.../Ansm03b0ForecastBoundaryTest.kt：现有统一预测/提醒DST基线。
- core/data/build.gradle.kts：两个测试源文件Sync及测试编译依赖；不增加main源/依赖。
- .github/workflows/android.yml：追加设备合同、独立release守卫、JSON报告归档；保留原六项检查。
- scripts/check_ansm03b0_isolation.py、test_ansm03b0_isolation.py、ansm03b0_report.py：隔离及实际结果摘要。
- docs/ansm03b0/：独立审计/实验合同/恢复风险/审批/验收交接。

正式app源码、Room实体/迁移/schema、PNBAK1、库存/预测/提醒生产源码、PK及参数、AppLock/伪装、四语正式字符串均不改。
本批无新UI、无截图交付，UI真机/TalkBack NOT_RUN；不把父PR48的合成UI当本批正式关联。

02B接口与混合规格人工确认的独立提案见[接口合同](interface-contract.md)，不是已实现UI/持久化。

首轮9598093：JVM320项、数据193、提醒20、应用1198（205原有条件SKIPPED），均0失败；PeriodStability5/0/0。
设备API35 data150/app264（8原有条件SKIPPED），API26/29各data83/app70，均0失败；首次Android工作流因新lint依赖遗漏FAIL。
依赖已补齐，本地lintDebug PASS。其余原生/科学任务完整结束，没有取消或弱化；详见[first CI证据](first-ci-evidence.json)。
本交接记录提交时证据；最终HEAD/SHA、六项最终CI及合成报告摘要在[PR #51](https://github.com/DevenirTwilight/HRT-Log/pull/51)正文及Checks核验，不能用首轮结果代替。
截至本提交：B0已完成可验证实现，B1仍PRODUCTION_BLOCKED；无生产接线/发布。物理真机与新协议强停NOT_RUN。
