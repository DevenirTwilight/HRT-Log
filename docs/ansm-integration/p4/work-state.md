# P4 工作状态

当前：Room12候选源码与迁移本地验证通过，业务备份仍10、计量格式接受OFF；无Repository/App入口。PR #57始终DRAFT。数据268/Period+Privacy8零失败，原生编译PASS；新Room12三API与六检查待当前HEAD。主线仍Room11，最新f1e9b4d已合并P3，合并后CI待验。

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
