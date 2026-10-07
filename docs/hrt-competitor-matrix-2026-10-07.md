# 竞品功能矩阵与证据目录

核查日期：2026-10-07；比较默认分支源码，商店版本可能不同。阅读指南：**✓**=审阅源码存在；**◐**=部分/有边界；**○**=官方宣称、未验证全应用源码；**—**=审阅范围未发现；**?**=资料不足；**R**=路线图/未合并。`—` 不等于全仓库不存在的证明。

“其他”逐项写产品名，不把不同产品拼成一个全能产品。**TMtf**=Transmtf Web，**TT**=已归档 TransTracks，**HM**=HRTMe，**TRT**=MyTRT。MyHRT 只有安全层公开且禁止复用，功能按官方资料；其安全层能验证的单独用 ✓。Trans Memo 的 ◐导入 表示 HRT Log 支持的 user_version8 数据格式存在字段，不代表本次运行过原应用。

## 固定源码、模型、最近进度与许可

| 项目/代号 | 固定提交及默认分支最近提交 | 架构/实际审阅路径 | 许可与进度边界 |
|---|---|---|---|
| HRT Log | [759ee88](https://github.com/DevenirTwilight/HRT-Log/commit/759ee88) · 2026-10-07 | Kotlin Compose，多模块 Room/SQLCipher；Entities/Repository/ScheduleEngine/ConcentrationCalculator/Exports/SymptomCatalog/迁移 | 未发现项目 LICENSE；[现状核查](hrt-product-research-2026-10-07.md)；API 当前 open issues=0，roadmap 在 docs |
| Featherline | [e9a3d18](https://github.com/mkx173/Featherline/commit/e9a3d180fef7c32bf59f06e42832a04762eb1d2d) · 2026-09-05 | Kotlin Compose/Hilt；model/medication、model/pk、model/bloodtest；data/repository、data/backup、widget；Room schema9 | GPL-3.0；[PR99 校准](https://github.com/mkx173/Featherline/pull/99) 未合并；Health Connect/Wear OS 是 issue，非现有实现 |
| Mona | [be2be01](https://github.com/mona-hrt/mona/commit/be2be016772824817cbb88b0a538811f2ca63676) · 2026-10-04 | Flutter，lib/data/model/*、services/db/db_tables.dart、app_database.dart（schema22）、backup_service.dart、home_widget_service.dart | AGPL-3.0；v1.24.0 附近提交；[issue402 包装模型](https://github.com/mona-hrt/mona/issues/402) 和 [PR190 sync](https://github.com/mona-hrt/mona/pull/190) 不能列为已实现 |
| Chrysalide | [73ea943](https://github.com/kushiemoon-dev/chrysalide/commit/73ea9436c037ea966285fd0b46934394f06ef3a0) · 2026-09-24 | SvelteKit PWA/Dexie；src/lib/types.ts、db-medications.ts、db-treatment-changes.ts、db-journal.ts、db-blood-tests.ts、db-appointments.ts、db-export-import.ts | MIT；近期 catch-up/auto-validation 修复；与 Chrysalide 协会的 Trans Memo 无关 |
| HRT-Tracker | [a484b24](https://github.com/Yuuki-Sakura/HRT-Tracker/commit/a484b2455507f34621da832264c1750d7011fcc0) · 2026-03-28 | SwiftUI/SwiftData；Packages/HRTShared/Sources/{HRTModels,HRTPKEngine,HRTServices}；DoseEvent/DoseTemplate/LabResult，LabCalibration，HealthKit，ExportService；Watch app | MIT；HealthKit 编译条件排除 OPENSOURCE；DoseTemplate 不是 recurring regimen |
| HRT Recorder | [28597d9](https://github.com/NoMTF/HRT-Recorder/commit/28597d90fd073acea27cfe123d8e8f4885699328) · 2026-05-13 | Kotlin Compose；app/src/main/kotlin/com/nanxin/hrtrecorder/{Models,PkEngine,CalibrationEngine,MainActivity}.kt；SharedPreferences/JSON | 未发现 LICENSE；不要复制实现、参数表或校准代码；近期提交是 README，不能视作活跃功能开发 |
| TMtf | [8c9abdd](https://github.com/TransmtfTeam/Transmtf-HRT-Tracker/commit/8c9abdde36494719dd8b55be03a0aadc79269ccb) · 2026-09-15 | React/TS；types.ts、pk.ts、personalModel.ts、calibration.ts、src/contexts/{AppData,Auth,CloudSync,SecurityPassword}Context.tsx | MIT，但 PK 来源链有无许可证上游风险；HRT Log importer 的目标是本项目 |
| TT | [f8560a1](https://github.com/TransTracks/TransTracks-Android/commit/f8560a1aa643e06fa9cfb9dd7c8b4bcbf802f5c9) · 2026-04-12 | Android/Realm；Photo/Milestone 与 zip backup；照片进展应用 | GPL-3.0-or-later；已 archived/从 Play 退役，仅作独特设计 prior art |
| MyHRT 安全子集 | [08cffbd](https://github.com/stugalabs/myhrt-security/commit/08cffbd0c9fdf0d4d7cb3ca0707b6b19d4db24fc) · 2026-09-14 | React Native/Expo 安全层；src/aesGcm.ts、secureStorage.ts、doseLogsDb.ts、appAuth.ts、localBackup.ts；ARCHITECTURE.md/构建 hardening | Source Verification License，允许核查而禁止在其他产品使用；不是完整应用开源 |

证据入口（从入口可定位上述文件，固定提交不随 main 改变）：

- [HRT Log 源码树](https://github.com/DevenirTwilight/HRT-Log/tree/759ee88)；[最近提交](https://github.com/DevenirTwilight/HRT-Log/commits/759ee88)；[issues](https://github.com/DevenirTwilight/HRT-Log/issues)。
- [Featherline 源码树](https://github.com/mkx173/Featherline/tree/e9a3d180fef7c32bf59f06e42832a04762eb1d2d)；[最近提交](https://github.com/mkx173/Featherline/commits/e9a3d180fef7c32bf59f06e42832a04762eb1d2d)；[issues](https://github.com/mkx173/Featherline/issues)。
- [Mona 源码树](https://github.com/mona-hrt/mona/tree/be2be016772824817cbb88b0a538811f2ca63676)；[最近提交](https://github.com/mona-hrt/mona/commits/be2be016772824817cbb88b0a538811f2ca63676)；[issues](https://github.com/mona-hrt/mona/issues)。
- [Chrysalide 源码树](https://github.com/kushiemoon-dev/chrysalide/tree/73ea9436c037ea966285fd0b46934394f06ef3a0)；[最近提交](https://github.com/kushiemoon-dev/chrysalide/commits/73ea9436c037ea966285fd0b46934394f06ef3a0)；[issues](https://github.com/kushiemoon-dev/chrysalide/issues)。
- [HRT-Tracker 源码树](https://github.com/Yuuki-Sakura/HRT-Tracker/tree/a484b2455507f34621da832264c1750d7011fcc0)；[最近提交](https://github.com/Yuuki-Sakura/HRT-Tracker/commits/a484b2455507f34621da832264c1750d7011fcc0)；[issues](https://github.com/Yuuki-Sakura/HRT-Tracker/issues)。
- [HRT Recorder 源码树](https://github.com/NoMTF/HRT-Recorder/tree/28597d90fd073acea27cfe123d8e8f4885699328)；[最近提交](https://github.com/NoMTF/HRT-Recorder/commits/28597d90fd073acea27cfe123d8e8f4885699328)；[issues](https://github.com/NoMTF/HRT-Recorder/issues)。
- [TMtf 源码树](https://github.com/TransmtfTeam/Transmtf-HRT-Tracker/tree/8c9abdde36494719dd8b55be03a0aadc79269ccb)；[最近提交](https://github.com/TransmtfTeam/Transmtf-HRT-Tracker/commits/8c9abdde36494719dd8b55be03a0aadc79269ccb)；[issues](https://github.com/TransmtfTeam/Transmtf-HRT-Tracker/issues)。
- [TT 源码树](https://github.com/TransTracks/TransTracks-Android/tree/f8560a1aa643e06fa9cfb9dd7c8b4bcbf802f5c9)；[最近提交](https://github.com/TransTracks/TransTracks-Android/commits/f8560a1aa643e06fa9cfb9dd7c8b4bcbf802f5c9)；[issues](https://github.com/TransTracks/TransTracks-Android/issues)。
- [MyHRT 安全子集 源码树](https://github.com/stugalabs/myhrt-security/tree/08cffbd0c9fdf0d4d7cb3ca0707b6b19d4db24fc)；[最近提交](https://github.com/stugalabs/myhrt-security/commits/08cffbd0c9fdf0d4d7cb3ca0707b6b19d4db24fc)；[issues](https://github.com/stugalabs/myhrt-security/issues)。

闭源/部分公开产品官方证据：

- MyHRT：[产品](https://myhrt.health/)、[FAQ](https://myhrt.health/faq/)、[隐私](https://myhrt.health/privacy/)、[安全层源码](https://github.com/stugalabs/myhrt-security)。Android 可用，iOS 页面写 coming soon；产品功能不可由安全子仓库验证。外部便携导出与本机同密钥安全备份恢复是两条流程，FAQ 不保证外部导出能再导入。
- Trans Memo：[协会官方页面](https://chrysalide-asso.fr/trans-memo/)，提醒/用药/盒容量/规律度、多语言；页面提到 2023 的 4.2.0 修复，2026 持续维护情况不确定。原应用没有在本轮安装；只对已验证导出格式补充字段存在性。
- HRTMe：[产品](https://hrt-me.com/)、[支持](https://hrt-me.com/support)，menopause sequential/cyclical、GP 报告、Apple Watch。官方说本地/iCloud、免账户，同时提到 TelemetryDeck，不应写成“无网络/无分析”。
- MyTRT：[产品](https://www.mytrt.app/)、[Mira](https://www.mytrt.app/mira)、[跟踪说明](https://www.mytrt.app/learn/articles/how-to-track-trt-with-ai)：注射/药瓶/化验/状态/整合，跨平台同步账户、可选云 AI；只核查公开资料，未验证模型、加密和临床效果。

issues 核查是本次 API 返回的近期条目及对应议题，不是全部历史 issues 的穷尽审计。0 open issues 不等于没有缺陷或社区需求。最新默认分支提交日期也不保证项目活跃度；本次没有联系任何作者。

### 可直接核查的源码位置

下列链接对应矩阵各产品的主要证据组；更多 Featherline 细节在独立设计的 I.A–E。

- Featherline：[MedicationLogModels.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/model/medication/MedicationLogModels.kt)；[BloodTestModels.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/model/bloodtest/BloodTestModels.kt)；[PkSimulation.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt)；[QuickLogActionCallback.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/widget/QuickLogActionCallback.kt)。
- Mona：[db_tables.dart](https://github.com/mona-hrt/mona/blob/be2be016772824817cbb88b0a538811f2ca63676/lib/services/db/db_tables.dart)；[scheduling_strategy.dart](https://github.com/mona-hrt/mona/blob/be2be016772824817cbb88b0a538811f2ca63676/lib/data/model/scheduling_strategy.dart)；[graph_calculator.dart](https://github.com/mona-hrt/mona/blob/be2be016772824817cbb88b0a538811f2ca63676/lib/data/model/graph_calculator.dart)；[backup_service.dart](https://github.com/mona-hrt/mona/blob/be2be016772824817cbb88b0a538811f2ca63676/lib/services/backup_service.dart)；[home_widget_service.dart](https://github.com/mona-hrt/mona/blob/be2be016772824817cbb88b0a538811f2ca63676/lib/services/home_widget_service.dart)。
- Chrysalide：[types.ts](https://github.com/kushiemoon-dev/chrysalide/blob/73ea9436c037ea966285fd0b46934394f06ef3a0/src/lib/types.ts)；[db-medications.ts](https://github.com/kushiemoon-dev/chrysalide/blob/73ea9436c037ea966285fd0b46934394f06ef3a0/src/lib/db-medications.ts)；[db-treatment-changes.ts](https://github.com/kushiemoon-dev/chrysalide/blob/73ea9436c037ea966285fd0b46934394f06ef3a0/src/lib/db-treatment-changes.ts)；[db-export-import.ts](https://github.com/kushiemoon-dev/chrysalide/blob/73ea9436c037ea966285fd0b46934394f06ef3a0/src/lib/db-export-import.ts)。
- HRT-Tracker：[DoseEvent.swift](https://github.com/Yuuki-Sakura/HRT-Tracker/blob/a484b2455507f34621da832264c1750d7011fcc0/Packages/HRTShared/Sources/HRTModels/DoseEvent.swift)；[DoseTemplate.swift](https://github.com/Yuuki-Sakura/HRT-Tracker/blob/a484b2455507f34621da832264c1750d7011fcc0/Packages/HRTShared/Sources/HRTModels/DoseTemplate.swift)；[LabCalibration.swift](https://github.com/Yuuki-Sakura/HRT-Tracker/blob/a484b2455507f34621da832264c1750d7011fcc0/Packages/HRTShared/Sources/HRTPKEngine/LabCalibration.swift)；[HealthKitService.swift](https://github.com/Yuuki-Sakura/HRT-Tracker/blob/a484b2455507f34621da832264c1750d7011fcc0/Packages/HRTShared/Sources/HRTServices/HealthKit/HealthKitService.swift)；[ExportService.swift](https://github.com/Yuuki-Sakura/HRT-Tracker/blob/a484b2455507f34621da832264c1750d7011fcc0/Packages/HRTShared/Sources/HRTServices/Export/ExportService.swift)。
- HRT Recorder：[Models.kt](https://github.com/NoMTF/HRT-Recorder/blob/28597d90fd073acea27cfe123d8e8f4885699328/app/src/main/kotlin/com/nanxin/hrtrecorder/Models.kt)；[PkEngine.kt](https://github.com/NoMTF/HRT-Recorder/blob/28597d90fd073acea27cfe123d8e8f4885699328/app/src/main/kotlin/com/nanxin/hrtrecorder/PkEngine.kt)；[CalibrationEngine.kt](https://github.com/NoMTF/HRT-Recorder/blob/28597d90fd073acea27cfe123d8e8f4885699328/app/src/main/kotlin/com/nanxin/hrtrecorder/CalibrationEngine.kt)；[MainActivity.kt](https://github.com/NoMTF/HRT-Recorder/blob/28597d90fd073acea27cfe123d8e8f4885699328/app/src/main/kotlin/com/nanxin/hrtrecorder/MainActivity.kt)。
- TMtf：[types.ts](https://github.com/TransmtfTeam/Transmtf-HRT-Tracker/blob/8c9abdde36494719dd8b55be03a0aadc79269ccb/types.ts)；[pk.ts](https://github.com/TransmtfTeam/Transmtf-HRT-Tracker/blob/8c9abdde36494719dd8b55be03a0aadc79269ccb/pk.ts)；[personalModel.ts](https://github.com/TransmtfTeam/Transmtf-HRT-Tracker/blob/8c9abdde36494719dd8b55be03a0aadc79269ccb/personalModel.ts)；[CloudSyncContext.tsx](https://github.com/TransmtfTeam/Transmtf-HRT-Tracker/blob/8c9abdde36494719dd8b55be03a0aadc79269ccb/src/contexts/CloudSyncContext.tsx)。
- MyHRT 安全层：[LICENSE](https://github.com/stugalabs/myhrt-security/blob/08cffbd0c9fdf0d4d7cb3ca0707b6b19d4db24fc/LICENSE)；[doseLogsDb.ts](https://github.com/stugalabs/myhrt-security/blob/08cffbd0c9fdf0d4d7cb3ca0707b6b19d4db24fc/src/doseLogsDb.ts)；[appAuth.ts](https://github.com/stugalabs/myhrt-security/blob/08cffbd0c9fdf0d4d7cb3ca0707b6b19d4db24fc/src/appAuth.ts)；[localBackup.ts](https://github.com/stugalabs/myhrt-security/blob/08cffbd0c9fdf0d4d7cb3ca0707b6b19d4db24fc/src/localBackup.ts)；[ARCHITECTURE.md](https://github.com/stugalabs/myhrt-security/blob/08cffbd0c9fdf0d4d7cb3ca0707b6b19d4db24fc/ARCHITECTURE.md)。

## 完整矩阵

### 用药

| 功能 | HRT Log | Featherline | Mona | Chrysalide | MyHRT | HRT-Tracker | HRT Recorder | Trans Memo | 其他 |
|---|---|---|---|---|---|---|---|---|---|
| medication catalog | ◐ 自建+成分选项 | ✓ 内建/自建 | ✓ 分子/自建 | ✓ 类型/自建 | ○ | ◐ 识别/模板 | ◐ 成分预设 | ○ 产品 | HM○；TRT○ |
| regimen | ✓ 单药 | ✓ 多药group | ✓ 单药 | ✓ 单药 | ○ | ◐ 剂量模板 | ✓ Daily/Weekly | ○ | HM○；TRT○ |
| schedule version | ✓ Rule有效期 | ◐ group替换链/时间槽有效期 | — 独立版本 | ◐ replaces/change表 | ? | — | — | ? | TMtf— |
| recurring schedule | ✓ 日/小时/周 | ✓ 日/周间隔 | ✓ 日/周/月/动态间隔 | ✓ | ○ | — 模板无时间表 | ✓ 日/周 | ○ | HM○；TRT○ |
| cyclical regimen | — ON/OFF | — ON/OFF | — ON/OFF | ? | ○ ON/OFF | — | — | ? | HM○ phased；TRT○ alternating weeks |
| injection | ✓ 记录/部分E2 PK | ✓ | ✓ IM/SC/器材 | ✓ | ○ | ✓ | ✓ | ◐ 导入部位 | TRT○；TMtf✓ |
| gel | ✓ 产品profile | ✓ | ✓ | ✓ | ○ | ✓ | ✓ | ? | TMtf✓ |
| patch | ◐ 隐含移除 | ✓ apply/off | ✓ | ✓ | ○ | ✓ apply/remove | ✓ apply/remove | ? | TMtf✓ 实例配对 |
| oral | ✓ | ✓ | ✓ | ✓ | ○ | ✓ | ✓ | ◐ 导入药物 | HM○；TMtf✓ |
| sublingual | ✓ | ✓ | ✓ | ✓ pill route | ○ | ✓ | ✓ | ? | TMtf✓ |
| implant | — 结构化 | — | ✓ 记录route | ✓ 记录method | ○ | — | — | ? | PK均勿推定 |
| manual dose | ✓ | ✓ | ✓ | ◐ taken log无独立amount | ○ | ✓ | ✓ | ◐ 导入 | TRT○；TMtf✓ |
| late dose | ✓ 阈值快照 | ◐ 窗外记录 | ◐ overdue/实际时间 | ◐ 延迟补记 | ○ | ◐ 时间记录无计划阈值 | ◐ plan状态 | ◐ 导入LATE | TRT○ |
| missed dose | ◐ 推定/记录混合 | ◐ 未记录槽位 | ◐ overdue | ◐ taken=false | ○ | — | ✓ plan status | ◐ 导入MISSED | HM○ |
| adherence | ✓ 计数/报告，语义待改 | ✓ 槽位进度 | ◐ 已服/逾期 | ◐ logs | ○ | — | ◐ plan counts | ○ | HM○；TRT○ |

### 库存

| 功能 | HRT Log | Featherline | Mona | Chrysalide | MyHRT | HRT-Tracker | HRT Recorder | Trans Memo | 其他 |
|---|---|---|---|---|---|---|---|---|---|
| 包装 | ✓ 独立Container | ◐ 每medicine stock | ◐ supply item非box层 | ◐ stock scalar | ○ | — | ✓ PillBottle | ○ 盒容量 | TRT○ 多vial |
| 开封包装 | ✓ | ✓ openContainerAmount | ◐ used amount | — 独立包装 | ○ | — | ◐ bottle | ◐ 导入openDate | TRT○ |
| 批次 | ✓ 可选batch | — | ? | — | ? | — | — | ? | TRT? |
| 来源 | ✓ 可选source note | — | ? | — | ? | — | — | ? | TRT? |
| runway | ✓ 两种口径 | ✓ 未来simulation | ◐ 供应剩余 | ◐ 频率估算 | ○ | — | ◐ 药瓶计数 | ? | TRT○ |
| low stock | ✓ 预警/日历 | ✓ warnAtDays | ◐ supply UI | ✓ stockAlert | ○ | — | ◐ remaining | ? | TRT○ |
| scheduled consumption simulation | ✓ 366天upcoming | ✓ 365天 | — 未确认完整future walk | — | ? | — | — | ? | TRT? |
| 实际 consumption rate | — | — scheduled rate非observed | — | — | ? | — | — | ? | TRT? |

### PK

| 功能 | HRT Log | Featherline | Mona | Chrysalide | MyHRT | HRT-Tracker | HRT Recorder | Trans Memo | 其他 |
|---|---|---|---|---|---|---|---|---|---|
| E2 | ✓ 部分制剂/途径 | ✓ | ✓ 注射图 | — | ? | ✓ | ✓ | — 未见官方证据 | TMtf✓ |
| T | — | ✓ 源码main有 | — | — | ? | — | ✓ | — | TRT? 实测图不等于PK |
| CPA | ✓ 口服 | — | — | — | ? | — | ✓ | — | TMtf✓ |
| progesterone | ◐ 示意 | — | — | — | ? | — | — | — | TMtf— |
| antiandrogen | ◐ CPA/SPI | — | — | — | ? | — | ✓ 多成分趋势 | — | TMtf✓ CPA/BICA等 |
| 多途径 | ✓ E2有支持矩阵 | ✓ E2/T有支持矩阵 | ◐ 注射PK | — | ? | ✓ | ✓ | — | TMtf✓ |
| 个体校准 | ✓ E2 fit | R PR99 | — | — | ? | ✓ ratio/time | ✓ | — | TMtf✓ |
| lab calibration | ✓ E2 | R 非main | — | — | ? | ✓ E2 | ✓ E2 | — | TMtf✓ E2 |
| uncertainty / CI | ◐ 模型分位带 | — main未见 | — | — | ? | — | ✓ 模型带 | — | TMtf✓ 模型带，非临床保证 |
| historical parameter snapshot | ◐ 不完整且calculator忽略 | ◐ equivalent量非整套PK参数 | ◐ intake成分/route非PK包 | — | ? | ◐ event extras非模型版本 | ◐ event extras非模型版本 | — | TMtf◐ weight/event；custom gel可重解释 |

### 化验

| 功能 | HRT Log | Featherline | Mona | Chrysalide | MyHRT | HRT-Tracker | HRT Recorder | Trans Memo | 其他 |
|---|---|---|---|---|---|---|---|---|---|
| analyte catalog | ◐ 固定14项 | ✓ 内建+custom | ◐ E2/T | ✓ 多marker | ○ 自定义 | ◐ E2 | ◐ hormone | — 官方未见 | TRT○ 多项 |
| 单位转换 | ✓ 部分marker | ✓ canonical/original | ✓ E2/T | ◐ marker units | ○ | ✓ E2 | ✓ | ? | TMtf✓ E2 |
| reference range | ✓ 用户范围不解读 | — model未见 | — | ◐ profile targets非单次范围 | ○ 逐次范围 | — | ? | ? | TRT○ |
| 与最近实际服药时间关系 | ◐ 动态E2 list | ✓ panel E2/T间隔 | ? | — | ? | — | ? | ? | HM?；TRT? |
| 与 regimen version 关系 | — 固定关联 | — panel未见groupversion | — | — | ? | — | — | ? | 其他未确认 |
| longitudinal comparison | ✓ | ✓ | ✓ E2/T图 | ✓ 多panel | ○ | ✓ | ✓ | ? | TRT○；TMtf✓ |

### wellbeing

| 功能 | HRT Log | Featherline | Mona | Chrysalide | MyHRT | HRT-Tracker | HRT Recorder | Trans Memo | 其他 |
|---|---|---|---|---|---|---|---|---|---|
| daily tracking | ✓ 三项+custom | — 结构化 | — | ✓ mood/energy/sleep | ○ | — | — | ◐ 导入scores | HM○；TRT○ |
| symptom tracking | ✓ 来源匹配 | — | — | ✓ journal/sideEffects | ○ | — | — | ? | HM○；TRT○ |
| side-effect tracking | ◐ 中性症状非诊断 | — | ◐ intake notes | ✓ 文本 | ○ | — | ◐ notes | ? | HM○；TRT○ |
| source provenance | ◐ 当前目录有源，历史无snapshot | — | — | — | ? | — | — | ? | 其他未确认immutable snapshot |
| stage review | ✓ | — | — | — | ○ weekly check-in非epoch | — | — | — | HM○ review非epoch |
| journal | ◐ 日备注 | ✓ note journal | — | ✓ | ○ diary | — | ◐ notes非journal | ◐ 导入notes | HM○；TRT○ |
| free notes | ✓ | ✓ | ✓ | ✓ | ○ | ◐ dose/lab边界 | ✓ | ◐ 导入 | TRT○；TMtf◐ |
| bleeding | ◐ legacy PERIOD_LIKE非flow | — | — | ◐ 自定义文本非专用 | ○ flow | — | — | ◐ legacy scores非flow | HM○ |
| body measurements | ◐ 回顾BP/weight非专用时间序列 | — | — | ✓ 多measurements | ○ | — | ◐ 当前体重非历史 | ? | TRT○ |
| injection-site rotation | ◐ site配置/记录，无体积模型 | — 已审log未见site | ✓ placement记录，rotation图未确认 | ◐ gel/patch zones | ○ | ◐ site picker，rotation图未确认 | — | ◐ 导入realSide | TRT○ site history |
| photos | — | — | — | ✓ local progress | ? | — | — | ? | TT✓；TRT○ |

### longitudinal / timeline

| 功能 | HRT Log | Featherline | Mona | Chrysalide | MyHRT | HRT-Tracker | HRT Recorder | Trans Memo | 其他 |
|---|---|---|---|---|---|---|---|---|---|
| treatment phases | — | ◐ group替换非组合epoch | — | ◐ treatment Gantt | ? | — | — | — | HM○ cycle phase非history epoch |
| milestones | — | ✓ TrackedDate | — | ✓ objectives/milestones | ? | — | — | — | TT✓；TRT○ |
| regimen changes | ◐ Rule版本非eventfeed | ◐ replacedBy chain | ◐ current schedules | ✓ TreatmentChange | ○ history | — | — | ? | HM○ |
| lab events | ◐ 独立labs页 | ◐ 独立blood页 | ◐ 独立tests | ✓ 时间型panel | ○ | ✓ timeline/graph | ✓ graph | ? | TRT○；TMtf✓ |
| symptoms timeline | ◐ 日期记录独立页 | — | — | ◐ journal日期 | ○ | — | — | ? | HM○ |
| appointments timeline | ◐ 日历已有 | — | — | ✓ calendar | ? | — | — | ◐ 导入appts | HM?；TRT? |
| unified timeline | — 跨模块整合 | — dose/history+独立journal | — | ◐ 多页面/治疗图非完整统一feed | ? | ◐ dose/lab非综合 | ◐ dose/lab非综合 | — | 其他未确认完整组合 |

### 医疗沟通

| 功能 | HRT Log | Featherline | Mona | Chrysalide | MyHRT | HRT-Tracker | HRT Recorder | Trans Memo | 其他 |
|---|---|---|---|---|---|---|---|---|---|
| appointment | ✓ | — | — | ✓ | ? | — | — | ◐ 导入 | HM?；TRT? |
| practitioner | ◐ 字符串 | — | — | ✓ directory | ? | — | — | ◐ doctorName | 其他? |
| visit preparation | ◐ 日期摘要 | — | — | ◐ appointment objective | ? | — | — | — | HM○ GP report |
| PDF | ✓ | — audited backup/export未见 | — | ? | ? | — CSV/JSON | — HTML非PDF | ? | HM○ |
| longitudinal report | ◐ 区间report非epoch | — | — | ◐ treatment chart | ○ CSV/history非确认PDF | ◐ CSV | ◐ HTML/CSV | ? | HM○；TRT○ sharing |
| selectable export | ◐ 区间/图开关 | ◐ 格式/backup | ◐ backup | ◐ whole JSON | ○ formats | ◐ formats | ◐ formats | ◐ DB backup | HM○ period |
| doctor-facing summary | ✓ 复诊PDF但历史语义待改 | — | — | — 完整visitpack未见 | ? | — | — | — | HM○ GP PDF |

### 隐私

| 功能 | HRT Log | Featherline | Mona | Chrysalide | MyHRT | HRT-Tracker | HRT Recorder | Trans Memo | 其他 |
|---|---|---|---|---|---|---|---|---|---|
| offline | ✓ 无INTERNET | ✓ 本地health，外部链接另计 | ✓ 本地；update可联网 | ✓ 本地PWA | ✓ 安全配置去INTERNET | ✓ local；集成另计 | ✓ local | ○ 不收输入 | HM○ local+iCloud；TMtf✓ guest |
| no account | ✓ | ✓ | ✓ | ✓ | ○ | ✓ | ✓ | ○ | HM○；TMtf✓ guest；TRT—sync账户 |
| SQLCipher | ✓ | ✓ | — sqflite | — IndexedDB | — 行级AES非SQLCipher | — SwiftData | — prefs | ? | 其他未确认 |
| encrypted backup | ✓ portablepassword | ✓ portablepassword | — plainJSON | — plainJSON | ✓ 本机同key；○外部AESZIP | ✓ AESGCM/PBKDF2 export | — plain默认 | — 支持格式明文SQLite | TMtf?；HM○ iCloud≠独立密码容器 |
| app lock | ✓ PIN/biometric | ✓ 系统biometric/credential | — 未见 | — privateflag非lock | ✓ PIN/biometric/recovery | — | — | ? | TMtf◐ security password云流程 |
| disguise | ✓ 两种launcher+shell | — | — | — | ○ calculatoralias | — | — | ? | 其他? |
| neutral notifications | ✓ 内容可中性/OS名暴露 | ✓ hideMedicationDetails | ? | ? | ○ | — 没有recurring reminder | ? | ? | 其他? |
| privacy widget | — | ◐ hide details+配置 | ? | — nativewidget | ○ hide/rename | — | — | — | HM?；TRT? |
| screenshot blocking | ✓ FLAG_SECURE | ✓ 可配置保护 | — 未见 | — PWA | ○ 可选 | — | — 未见 | ? | 其他? |

### 系统集成

| 功能 | HRT Log | Featherline | Mona | Chrysalide | MyHRT | HRT-Tracker | HRT Recorder | Trans Memo | 其他 |
|---|---|---|---|---|---|---|---|---|---|
| Widget | — | ✓ dose+Anchor | ✓ Android | — nativewidget | ○ Android | — | — | — | HM○ iOS；TRT○ iOS/Android |
| quick log | ✓ 通知/应用入口；无widget | ✓ widget/notification | ✓ widget | ◐ app操作 | ○ widget | ✓ Watch | ◐ app操作 | ◐ app操作 | HM○ Watch；TRT○ widget/watch |
| Health Connect | — | R issue26 | — | — | ? | — iOS | — | — | TRT○ weight；别推定读写药物 |
| HealthKit | — Android | — Android | — | — | — Android目前 | ◐ 非OPENSOURCE构建条件 | — | — | HM?；TRT? |
| Apple Watch | — | — | — | — | — | ✓ | — | — | HM○；TRT○ |
| Wear OS | — | R issue79 | — | — | ? | — | — | — | 其他? |
| shortcuts | — 系统AppShortcut | — 未确认 | — 未确认 | — | ? | — 未确认AppIntents | — | — | 其他? |
| Quick Settings tile | — | — | — | — | ? | — iOS | — | — | 其他? |

## 容易误读的比较结果

1. 记录 route/compound ≠该 route/compound 有 PK。Mona 的 implant/suppository 是记录能力，不能据此说有植入 PK。
2. 化验图 ≠lab calibration，区间图 ≠经过临床校准的置信区间；Featherline README 的 E2 描述落后于源码 E2/T，校准则仍是未合并方案。
3. “有版本”分三层：时间槽/单药计划、药物属性 revision、整个治疗组合 epoch。竞品的替换链与 HRT Log Rule 都不自动满足第三层。
4. MyHRT 安全层是行级 AES-GCM SQLite，而非 SQLCipher；其全应用健康记录覆盖范围还依赖未公开 storage。公开源码不能证明商店 binary 完全一致。本轮不做独立安全认证。
5. 原代码来源链必须单独审计。Transmtf/Featherline/Yuuki 顶层许可不自动证明全部 PK 参数/移植代码都获得上游授权；本轮不能宣称其有侵权，也不能宣称授权链完整。HRT Log 的旧问题已记录在 licensing.md，当前实现按文献重写。
6. TransTracks 已归档，Trans Memo 的目前维护状态未知；它们保留在矩阵是因为历史/独特设计，不列为“仍活跃”的证据。Recorder 最新 README 提交也不是持续维护的充分证据。
7. 竞品功能覆盖很广。HRT Log 不应把 stock、widget、journal 当市场空白；真正可经营的是自己的历史上下文、来源快照与医疗沟通整合，仍需用户访谈验证其需求优先级。
