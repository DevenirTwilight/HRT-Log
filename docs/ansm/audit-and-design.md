# ANSM-01 阶段 A：审计与独立资源设计

日期 2026-10-15；基线 `94ceb81c55425c51e2339d54e4b59cd6017c85b5`。
按 AGENTS/CLAUDE 指定顺序先读 REQUIREMENTS、HANDOFF、licensing，随后核对
BACKLOG、STATUS、BDPM 摘录与索引、医疗档案路线图、pk-model、AI-DEVELOPMENT。
这些文档包含许多历史状态，当前事实以源码/GitHub为准。

## GitHub 与协作

默认分支 `claude/new-session-1959qb`；开工干净克隆，无其他本地工作树。
基线六项必需检查全部 success：jvm、android、device-tests、
attachment-poc-compatibility (26)/(29)、science-change-gate；research-deep按既有门控skip。
Android运行38804244100、Science运行38804244145。ruleset25120328启用，
要求最新分支检查、PR及解决评审线程；不修改保护规则。

开放PR1/2/3/6/10/15/16/17/18为历史科研/归档，34是待负责人处理的交接，
44是UI收尾，正在修改app/build.gradle.kts及四个共享进度文档。
PR43状态拆分已合并，不能按旧HANDOFF重做。无法从GitHub判断未推送的其他工作，
因此本批只新增 `docs/ansm/`、`docs/reference/fr/` 和独立 `scripts/bdpm_reference.py` /
`scripts/test_bdpm_reference.py`。不改共享UI、ViewModel、Gradle、CI或主线文档。
完整开工SHA/PR/检查/ruleset证据见repository-audit.json。

## 现有模型与目标映射

|源码|当前含义|目标映射与兼容边界|
|---|---|---|
|core/data/.../Entities.kt：MedicationEntity|用户药物，molecule/route/unit、每次剂量、默认包装容量|不是商品。没有CIS/CIP、商品强度或稳定官方引用|
|同文件：ProfileEntity|PK酯型/途径/凝胶产品号/贴片释放速率|不得借作官方商品身份，不根据BDPM替换其值|
|MedicationSnapshot.kt|v2记录/规则输入快照，名称/分子/途径/单位/PK profile|不含官方引用；历史不自动补齐，旧格式保持不变|
|RecordEntity / RegimenHistory / RegimenDefinition|实际量/时间/来源/修订，独立方案版本|官方更新不能改事实、冻结快照或时期|
|ContainerEntity|每包装capacity/used/initial_used，批号/来源/开封日/状态|包装无自身单位或强度，不可安全混用不同物理量|
|SupplyEntryEntity / SupplyLedger.kt|只追加CONSUME/REVERSE/ADJUST，反向流水而非重写|不接入目录，不重算历史；快照单位与当前单位不同则不分配|
|StockForecast.kt|按未完成Timeline槽逐次扣减，366天窗，7日低库存|库存页/日历/提醒共用；不能在单一页面添加不同换算|
|NotesRepository.saveMedication|事务保存；可按明确开关调整当前IN_USE/SEALED容量，保留已用量|选择商品不得隐式触发resizeContainers或变更实际剂量|
|ui/MedicationEditor.kt|手动填写，剂量/容量必填；UNITS含MG/TABLET/ML/PUMP/PATCH/OTHER|可表达0.5数值但无强度换算；缺专用胶囊单位，非E2途径存null|
|ui/Format.kt brandNames|静态中法商品名提示|不是经核实的产品/别名表，不作为等效关系|
|ui/MedicationsScreen.kt|药物卡点击编辑，没有独立商品详情|ANSM-02/04须独立页面/只读区域及四语验收|
|symptom-sources.json / SymptomCatalog|独立的官方/第三方来源目录和历史来源快照|不从新商品目录直接推导安全提示，不替换旧症状事实|
|NotesDatabase / DataTransfer / BackupValidation|Room10；PNBAK1结构化序列化仍9|不增加生产表、不改JSON快照语义或备份格式|
|pk-engine及LabEstimate/PDF|文献模型、冻结calculator1/2和研究情景|完全不修改；RCP5.2不是参数覆盖许可|

库存流：用户完成服药 → Repository事务保存实际记录及快照 → SupplyLedger按药物分配
已开包装/必要时开新包装 → 账本触发器维护used → 同一读取快照刷新各页 →
StockForecasts遍历尚未完成的槽 → 日历、库存与StockAlerts共享结果。
编辑/删除通过REVERSE保护旧消耗；VOIDED与恢复走既有保守校验，不混入本批。

已读相关回归：SupplyLedgerTest、StockForecastTest（含巴黎DST）、PlanEditTest、
DataTransferTest、BackupValidation与现有PeriodStability/Room迁移/PNBAK兼容测试入口。
这只是静态审计，运行结果必须另外记录。

## 官方来源与核实方式

本环境能够读取BDPM和ANSM主页。已实际取得：

- https://base-donnees-publique.medicaments.gouv.fr/telechargement
- 该页实际链接的 `CIS_bdpm.txt`、`CIS_CIP_bdpm.txt`、`CIS_COMPO_bdpm.txt`、
  `CIS_CIP_Dispo_Spec.txt` 及格式说明PDF，均在 `/download/file/` 下。
- https://base-donnees-publique.medicaments.gouv.fr/medicament/63071975/extrait
  的RCP6.5明确80g、64剂、每剂1.25g/0.75mg。

网站页显示更新2026-09-29；格式PDF显示2026-10-13：两者独立记录，不能把获取日
或文档修订日冒充药品数据更新日。下载文件编码不一致：CIS及成分为cp1252，
CIP为UTF-8；必须严格解码，不能把乱码当官方原名。
格式说明将治疗部分标为ST，实际成分文件出现FT，保留原代码并记录差异，不丢失或并加。

下一步以下载原始行的有限摘录+全文件SHA建立确定性离线输入；逐产品获取页核对
名称及RCP/Notice面板存在性。HTTP200本身不是产品核实成功，须核对CIS页面与官方名称。
每个字段记录来源文件/原行号或页面位置；失败填UNVERIFIED，不能沿用旧摘录声称本轮已核实。

## ANSM-01 架构与字段合同

不需要Room迁移。第一批目录位于 `docs/reference/fr/`，由Python标准库离线生成JSON，
不接入Android运行时。后续资源打包/只读加载可在ANSM-02评审时确定；本批不声称已有App目录UI。

- 产品按CIS识别；同品牌不同强度为不同CIS；产品包含多个给药途径。
- 成分保留官方物质代码、原名、SA/FT类型、关联组、原始强度及分母；
  盐/酯/水合物与治疗部分不能相加，不生成跨品牌等效关系。
- 包装按CIP13识别，保留CIP7和所属CIS；包装销售声明独立于产品授权。
- authorization：ACTIVE/ARCHIVED/REPEALED/EXPIRED/UNVERIFIED，保留法语原文。
- marketing：MARKETED_DECLARED/STOPPED_DECLARED/NOT_MARKETED/UNVERIFIED。
  声明在售不等于实时可买到，非销售不等于已停产。
- supply：原供应公告单列日期/CIS/可选CIP，不与上述状态压成单枚举；
  无公告为UNVERIFIED，不推断供应正常；不宣称实时库存。
- RCP与Notice分别记录已见面板和页面入口，缺失原因明确；不复制全部说明书。
- packageQuantity、formulationPerUse、activeIngredientPerUse、useCount各自独立。
  仅正式来源明确支持才填标准化量；无法可靠解析的包装保留原文且数值UNKNOWN。
- ReferenceVersion包含源URL、获取时间、来源更新日、原文件SHA、摘录SHA、生成器版本；
  构建不联网、无当前时钟参与确定性输出。所有缺失/冲突列于机器报告。

下载页注明数据可再利用，要求来源/更新日、不得改变原义或暗示监管背书；页脚链接
Etalab2.0。目录附署名与再利用说明，不以项目MIT授权覆盖官方原文。
RCP/Notice全文及医学摘要的文本范围仍需ANSM-04独立核查，不默认全文再分发。

## 修改范围、风险和依赖

|批次|可安全先做|阻断/需另批|
|---|---|---|
|01|独立离线输入/结构/生成器/校验/报告；不写用户库|来源缺失不得变肯定事实，数据审阅与完整CI后交负责人|
|02|选择器/详情设计，保留手动流程|当前无官方关联存储；不能把引用塞needs_review/PK字段/偏好侧车规避迁移；先给独立迁移与备份兼容方案等批准|
|03|单位与包装转换规格和纯验证工具|库存接线、流水/预测语义变化须独立审查与负责人批准|
|04|只读客观字段及RCP/Notice入口设计|产品身份需可靠，四语/无障碍/安全文案及再利用范围独立验收|
|05/06/PK|后续设计与风险矩阵|不实现个人风险分级、累计量阈值、监测提醒、自动上报或正式PK改动|

本轮先完成01独立PR并交审，不跨越其验收门进入依赖它的批次。
测试至少拒绝缺字段/重复CIS或CIP/错误所属关系/未知状态/冲突/失效，严格量纲与
内容校验和，重复生成逐字节一致。既有CI不删改；新增测试纳入既有scripts发现。
没有界面改动；截图、物理设备、TalkBack均NOT_RUN，不声称已验收或可发布。
