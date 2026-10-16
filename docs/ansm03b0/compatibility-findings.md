# B0 实际账本差异与候选恢复评估

所有输入为合成数据，非真实健康记录。完整逐事件JSON由单元测试生成到core/data/build/reports/ansm03b0，CI作为build-results保存；摘要可由scripts/ansm03b0_report.py重建。
原有生产源码/Schema/备份均不修改。测试调用真实NotesRepository/SupplyLedger/PackageCorrections，而不是Mock替身；影子仅读取DTO，前后全表+DDL指纹一致。
Robolectric使用FrameworkSQLite；设备合同另用原生SQLCipher，两者证据不可互换。设备最终结果见verification。

## 真实事件基线

ledger-baseline.json保存创建、打开、完成、公共入口重复、更正、删除冲销、恢复、手动调整及备份前后各表、容器、预测。
合成两盒各2单位：流水行数0→1→1→3（CONSUME/REVERSE/CONSUME）→4→5→6。
相同公开complete不重复扣减；重复删除/同剩余量调整无新流水。更正与恢复保持原有流水前缀逐行相同。
VOIDED案例仅ADJUST允许作废，期间available=null、无LOW判断；恢复保留流水和原余额；有实际消耗则拒绝作废。
上述是现有政策基线，不因新计量功能放宽VOIDED限制。

## 可检测差异

- 单位故障注入：仅测试把2mg错误作为2片传入现有标量入口；旧分配2片，已确认2mg/片的影子只应1片。必须报告DIFFERENCE，不能以总数字匹配冒充安全换算。
- 内部allocate二次调用没有业务op参数，可产生额外消费；影子检测重复分配行数。公开complete已有幂等守卫，不把两者混称。
- 原账本UPDATE/DELETE被append-only触发器拒绝；候选op同UUID同payload无操作、不同payload拒绝且指纹不变。
- 不同来源版本/规格、旧单位未知/删除名称变动均不能自动统一；mixed-strength返回UNKNOWN，不拼剂量。
- 凝胶名义64泵不是实际可用64泵；合成实际确认容量独立。mg→泵仅有向明确关系和实际来源确认成立才可比较；无实际容量返回UNKNOWN。
- 贴用一次与移除/脱落分开；REMOVE/DETACH无新消耗。实际记录更正依旧逆原量并追加，不依据释放速率。

## 本轮揭示的恢复阻断

1. **现有非空账本恢复边界**：含CONSUME与REVERSE的合成备份恢复到自身时，RawData.restore批量DELETE supply_transaction受到reversal_of_id自引用ON DELETE RESTRICT约束拒绝。
   测试明确验证失败及整个业务/DDL指纹回滚；恢复至新建空目标另测成功并逐表相等。此项“预期失败被安全捕获”的测试PASS，不代表非空恢复功能已修复。
   生产接线/发布阻断，需单独批准并回归安全删除顺序及既有数据，不在本轮改正式恢复。
2. **新候选身份遗留**：候选表未在DOMAIN_TABLES，因此旧备份既不导出它们也不替换；旧CURRENT epoch及同整数ID映射可遗留。
3. **新守卫丢失**：RawData.unguarded删除所有非附件触发器；SchemaGuards只重装既有守卫。新增表的append-only触发器会消失。
4. 测试用外层事务包裹“退休旧数据集→原恢复→建立新UNKNOWN域→重装候选守卫”验证可行边界，并在恢复后注入失败证明完整回滚；这是实验包装器，不是正式修复。
5. 新候选业务schema10用现有PNBAK1加密后，旧reader在写前NewerBackup拒绝，数据库指纹不变。仅验证拒绝策略，不宣称已有新schema完整导出/导入支持。

## 候选SQL验证范围

独立ansm_lab表，无Room Entity/版本占用，无原业务行改写；量用TEXT十进制，payload规范JSON排序+摘要，禁止Double作为精确值。
dataset/med/record revision/operation/container/source复合外键防串联，原捕获/来源不可更改，消费后容器不能替换snapshot。
测试退役域、tombstone旧handle、下一域复用相同整数ID，禁止跨域重关联；无目录也可从冻结payload恢复换算。
record+ledger、identity、capture、外键失败各故障点，验证事务前后全表及DDL相等。
DDL每条语句后故障，验证无部分表/索引残留、user_version仍10。
Room1..10用仓库真实schema SQL和已有allMigrations升级至10，保留合成旧记录/库存、FK检查，并加候选表但不回填身份。

## 明确未证明

候选DDL不是完整生产设计：ADJUST方向/类型合法性、业务内容验证、全捕获与ledger的双向一致性、新备份完整roundtrip、导出大小界限、并发写及迁移强停仍需B1实现/审查。
payload摘要证明实验快照未变，不等于官方来源已授权/官方数据内容已核实；源ref为SYNTH占位身份。
没有升级原Room版本，未运行一个不存在的RoomN迁移；没有真实用户库/真机、无新协议进程强停或设备断电证据。
保留现有基线回归不等于生产新接线获准。最终状态PRODUCTION_BLOCKED。
