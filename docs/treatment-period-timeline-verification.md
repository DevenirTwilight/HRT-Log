# Treatment Period / Timeline build12：验证与边界

2026-10-07。功能提交 `dbb9688`、长列表及兼容回归 `2c3b68b`、时区标注和旅行回归 `b0dc178`。full only、versionCode12/versionName0.2.0、schema6。用户在Phase 1卡点报告后回复“继续”，采用历史slot对应未知的保守边界。

## 最终职责

- Regimen：已保存的计划。Treatment Period：记录的治疗标准的显示投影，不是疗效或实际服药推断。
- History：逐次planned/actual/late/missed/skipped/unconfirmed、时间剂量、编辑删除，保持原实现和记录。
- Timeline：当前时期置顶、过去时期逆序；仅LAB/REVIEW/MILESTONE/APPOINTMENT。事件按source ID打开详情；不展示逐次服药、wellbeing、症状卡或adherence评分。未来事实单列Upcoming，不造未来方案；首个记录之前明确unknown。
- 没有active记录的空档与恢复形成不同精确segment，跨日期另显示时期。旧库未存独立stop/pause原因，界面使用“没有有效的已记录方案”，不把版本结束自动写成“用户暂停治疗”。一天内的stop/resume仍保留在精确原始变化中，即使同日显示分组。
- 里程碑使用用户选择日期；提交回执后关闭、saving防重复、失败留草稿、提交后刷新失败区别提示。保存后滚动并打开该source详情，400天前和未来记录均可定位。空标题STARTED及合法重复STARTED保持原语义。

## 模型与兼容

`core/domain/.../TreatmentPeriods.kt`：RawTreatmentInterval→TreatmentStandardSpan→ClinicalSegment→DisplayPeriod。临床签名V2忽略clock、zone、anchor、名称和PK模型选择；包含medication identity（组合span）、成分/酯型/途径/明确产品、剂量集合、次数和周期频率。精确UTC从未倒推午夜。固定显示时区默认首个记录的zone，无记录为UTC，界面明示；设备旅行不自动重新分组历史。

- 相邻且标准相同的版本只在显示层合并，保留每个原版本ID与UTC。间隔空档不合并，即使恢复方案相同。
- 一天内的多药变化只有一个显示分组；卡摘要为该日最终记录标准，不丢A→B→A精确段。
- 相同剂量集合的旧非均匀时段交换不能确认其含义，显示“时段对应关系未记录”。不按钟点排序强判变化，也不声称完整恢复历史分配身份。可确定的剂量集合/次数/频率/途径/产品变化仍切时期。未来稳定slot identity和操作意图存储尚未实施。
- MedicationEditor移动时间保留平行剂量override，不重新按钟点排序丢失对应；repository拒绝没有剂量对应信息的破坏性非均匀计划编辑。已有override显示在时间标签中。改变每日次数是明确重新配置，而非提醒移动。
- V1 TreatmentEpochs、RegimenDefinition.signature、clinical_signature/validateLinks与schema均不变；新JSON字段没有写入冻结定义。旧Context1保持原key/PK结果；详情把保存上下文与当前只读版本归属分别标明。VisitPack1 facts/digest仍计数原始版本开始结束，不换临床变化数、不生成template2、不更新旧PDF。

## 自动验证

所有数据为synthetic。最终稳定源码b0dc178已完成全部检查：

| 验证 | 当前证据 |
|---|---|
| domain | 39项通过，含连续提醒版本合并/原ID、gap恢复、临床变化、旧剂量排列未知、同日多药与A→B→A、精确采样、DST/月边界与多药组合 |
| 初轮全模块 | domain39/pk18/importer12/data57/reminder14/app106，共246项，0失败，1既有Robolectric PDF跳过。后续稳定源码app109项通过（含VM整合、冻结报告、旅行时区回归），合计249项、248通过/1既有跳过 |
| Milestone针对性 | saving防双击、事务失败、提交后提醒/refresh失败、STARTED空标题/重复/CRUD、400天可见、source详情、saveable草稿恢复 |
| 兼容回归 | 旧V1保留2段但V2合并1时期；Context1旧key有效，VisitPack1仍为2次开始/1次结束且digest不变；数据备份roundtrip/validateLinks/旧definition与signature不变 |
| 本机release首轮 | 两个本机构建并发导致domain.jar读取冲突；失败，不算通过。已改为单一构建进程重跑最终任务 |
| 最新本机构建/lint | 稳定完整任务7m33s成功：full debug/release、data/app原生测试APK；manifest与schema无漂移通过。lint0错误96警告（主要旧Timeline未用资源/复数候选，基线69） |
| 原生测试 | 新增API35长历史160事件、2倍字体、source按钮语义与准确详情验证；迁移/SQLCipher复用现有原生套件。最新CI37701395265 device-tests已success：data11、app24主项及2重启项另起进程成功（主报告含2既有跳过共26）；包括新增长列表/2倍字体source详情。android构建任务也已success |

当前CI：[37701395265](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37701395265)，对应功能 `b0dc178`。jvm/android/device-tests全部success；原生日志job113065735012核对data11与app主套件及独立重启进程。先前37700705349在新提交后由分支并发策略取消，不代表原生已通过。本机完整串行重跑成功。正式full已用原私有证书签署并清理临时凭据，APK 23,338,203 bytes、SHA256 `98bb57af464dd1e80acc857b8fe368e8949c537965fe1d59155b59f09b530ca3`；非debug/versionCode12/无INTERNET/16KB验证，临时下载回查一致。不交付debug或unsigned包，不公开发布或改标签。

## 用户复核

1. 时间线添加“开始激素治疗”，选400天前、空标题，保存；确认saving、已保存、立即打开对应详情。返回后旧里程碑仍可在unknown或历史时期找到。
2. 仅移动均匀剂量提醒时间，时期不变；改剂量或每日次数、增加/停用方案、空档后恢复，则检查时期变化。查看原始方案变化核对精确UTC。
3. History核对原服药记录完整；Timeline不列逐次taken/late/missed/skipped/unconfirmed。连续漏服不得出现推断的停用。
4. 同日改变方案前后的两次合成化验：保存上下文仍是各自原版本，Timeline日期组不回写冻结事实。复诊资料包计数继续按template1，不改历史PDF。
5. 添加未来预约/里程碑检查Upcoming；开启简洁/伪装路径，核对没有敏感标题/药物或真实空间泄漏。大字体、屏幕阅读器及长历史滚动做真机复核。

## 已知边界

- 未升级schema，未实现未来跨版本slot身份、独立stop/pause理由实体、可选adherence摘要或VisitPack2。
- 日期级事实仅归显示日期组，不能宣称某个精确clinical segment；LAB仍按sampledAt精确解析原版本。
- saveable恢复测试验证状态序列化/重建，不等同于所有OEM强杀。进程恰好在写库提交与回执之间终止时，恢复的未确认草稿可能需要先查看已保存事实再决定是否重试；未以内容去重破坏合法重复里程碑。
- 自动按钮语义与2倍字体验证不等同于人工TalkBack/OEM真机验收。日期范围按真实UTC边界截取日期，变更日可能出现在相邻时期两端；不把下午边界倒推到午夜。无用户真实健康数据验证，无医疗判断/因果推断/剂量建议。未改PK公式或参数、库存流水、公开Release/标签。
