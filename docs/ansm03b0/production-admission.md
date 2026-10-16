# ANSM-03B1 准入及逐项审批方案

2026-10-16，结论 **PRODUCTION_BLOCKED**。本文件是提案，不是审批，也不实施生产变更。

| 门 | 本轮核查 | 结论/待批准项 |
|---|---|---|
| 1 ANSM-01审核合并 | PR45 OPEN，无review | BLOCKED |
| 2 ANSM-02A审核合并 | PR48 OPEN，无review | BLOCKED |
| 3 再利用及打包 | LO2页脚与下载PDF1.0冲突，未见负责人结论 | BLOCKED；不分发临床原文 |
| 4 FT/ST | 保留原码，不静默合并，不求和；尚未批准解释 | BLOCKED |
| 5 02B正式引用 | 无正式持久化接口 | NOT_IMPLEMENTED |
| 6 实际商品/包装确认 | B0仅合成明确选择；不可从mg/名称倒推包装 | 需产品与交互审阅 |
| 7 容器冻结身份 | 候选SQL/不可变捕获可验证，未批准生产实体 | 需批准 |
| 8 旧库存UNKNOWN | 保留旧标量历史，不回填身份/恢复原精度 | 需批准 |
| 9 混合规格 | 不自动拼凑；实际每份来源须单独确认 | 需批准 |
| 10 Room/备份 | Room10/业务schema9保持；新版本待定 | 需批准迁移、恢复协议及兼容策略 |
| 11 预测/提醒 | 统一未知结果提案，不接生产 | 需批准四语与显示语义 |
| 12 失败恢复计划 | B0异常回滚可测，不等于B1进程强停验收 | 需批准B1故障/撤销/恢复计划 |

即使以上技术门全部满足，仍须负责人**明确批准具体文件、Schema、备份及库存行为修改**。
不得将本任务书、绿色CI、候选数据库成功或下一步提示词视为批准。不得自动合并45/48/50或本PR。

## 建议分四个独立PR

### A 正式计量核心（不接库存/数据库）

批准后才在core/domain/src/main引入API；不能直接搬测试文件冒充完成。
需要经过验证的构造入口、非法状态防护、来源payload规范/摘要校验、算法版本、精度界限/错误合同。
保留03A全部35测试，建立相同向量对生产核心和实验原型的差分测试，禁止长期维护两套静默分叉算法。
B0来源ref是明确的合成标识；未证明官方payload到摘要的生产信任链，也未解决授权分发。

### B 包装身份、快照及恢复协议（不改分配）

候选实体：DatasetIdentity、MedicationOfficialReference、ContainerPresentationSnapshot、RecordMeasurementCapture、MeasurementOperation。
正式命名/Room N须与02B和其他数据库开发统一，**不预占Room11**。
med引用可空、可解除；CIS/CIP不放PK Profile、自由文本或needs_review。
容器UUID+数据集UUID稳定身份，旧整数ID仅当前数据集handle；药物UUID与记录UUID/revision域隔离。
不可变快照包括原始成分身份/角色/组、规格、独立库存单位、名义容量、实际确认可用容量、来源版本/摘要、算法、确认事实。
实际容量是每容器事实，不能因为同一商品而强制相同；当前参考更新不得改旧快照。
消耗捕获须保存准确量、方向、原ledger ID、实际来源及完整op指纹；REVERSE引用原消费，不重新转换。
删除为tombstone，保留消费证明；恢复核对原身份与账本，不能按新整数ID重关联。
未知旧容器保持旧数据与UNKNOWN；不按当前名称/单位/规格回填。旧Double仍旧Double，不宣称恢复原始十进制。

迁移：从Room1起逐级运行已有链至10，再实施经批准10→N非破坏迁移；不重写旧业务表，不创建推断的官方身份。
须覆盖完整旧行、外键/索引/触发器/Room身份哈希、旧空库/VOlDED/已删除药物、失败后重新打开；禁止破坏性fallback。
B0仅验证旧1..10→10再加独立候选表，**未验证一个尚未批准的Room N**。

备份：PNBAK1封装version1与业务schema9不同。业务schema下一版仅候选，不抢占10。
新表需显式纳入导出表/列白名单、严格校验、源payload与捕获一致性校验及恢复事务。
新备份应自包含冻结来源，目录缺失仍可读历史；来源丢失/摘要错误应拒绝相应转换，不能静默猜测。
旧schema1..9导入先验证，原业务完整保留；新数据集epoch，旧库存不推断身份。候选新schema被旧reader安全拒绝，禁止伪装schema9。
同数据集roundtrip与替换恢复需定义不同身份策略：有碰撞则原子重映射所有域引用，不能一边保留一边随机改UUID。
恢复前退休旧epoch，清除/导入候选表顺序，重装所有新守卫，重建派生缓存；任一步失败必须回滚所有业务及身份。
**现有RawData非附件触发器移除/只装既有守卫、固定表列表均必须显式适配**。
另外既有非空REVERSE账本的批量清除受自引用RESTRICT外键限制；需单独审查安全删除顺序/策略，不可关闭约束掩盖。
降级旧APP不支持新库：安全拒绝并指引保留原文件/备份，不能删除重建；应用降级实际验收待实现。

### C 账本事务接线

仅经确认同规格/同单位池FIFO，open opened_on/id再sealed id；不同规格不拼一次剂量。
缺实际来源、容量或单位，返回UNKNOWN/需确认，不默认扣减。已知不足保留未分配实际用量，不篡改事实。
记录+revision+op捕获+CONSUME必须同一事务；相同UUID同payload重放无副作用，不同payload拒绝。
公共complete的幂等不能替代底层业务op约束；内部allocate目前无此接口。
更正先冻结原捕获逆量，再追加新revision捕获；CONSUME/REVERSE/ADJUST仍只追加，禁止新算法重写。
元数据引用更新走专用事务，**不得调用saveMedication resize路径**，不触发record revision、冲销、PK或方案编辑。
VOIDED/恢复继续PackageCorrections守卫，完整历史哈希含新增捕获。失败/取消不提交部分记录，强停重试按业务op去重。
验收须有故障点：记录后、ledger后、capture后、commit前/后、进程强停、导入替换、重复op、冲突op、两数据集同整数ID。

### D 统一预测和UI

仅账本稳定后改变StockForecast/StockAlerts及日历/库存页，不各算各的。遵循[预测合同](forecast-contract.md)。
批准文件范围预期涉及core/data/entities/database/migrations/SchemaGuards/NotesRepository/SupplyLedger/PackageCorrections/DataTransfer/BackupValidation/StockForecast、core/reminder/StockAlerts、02B接口及UI/四语/CI。
最终实际清单必须另列负责人确认，不能以此泛化提案获得任意文件授权。
保持366日窗口、7日阈值、巴黎DST；变动需独立批准。UI需320/411dp、2x、四语/主题/对比度/TalkBack/取消错误恢复。

## 回滚与发布

A可独立撤回。B启用新schema后不允许用旧APP写库；生产前须有经验证兼容恢复路径，不能依赖降级安装回滚。
C/D失败默认不启用新行为；开关不能将已有新捕获误解为旧标量。任何正式启用都需审批与全套回归。
无正式签名、版本/包名/渠道/Release/APK交付，无INTERNET/遥测/真实健康数据，无PK修改。
