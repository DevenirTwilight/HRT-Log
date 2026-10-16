# P1 统一持久化与恢复合同（实现前冻结）

2026-10-16；依赖 P0 #53，不将未合并实现冒称主线。当前候选 Room11 / PNBAK1业务Schema10；实施前再次查默认分支、并行PR和实际版本。外层PNBAK1加密不变，官方资料分发OFF。

## 事实与身份

引用、实际来源、用户用药事实、旧库存标量分域。当前商品引用的专用事务不得调用saveMedication，不调整剂量/计划/容器或PK。旧记录默认无捕获、旧容器默认LEGACY_UNKNOWN。

统一结构：

|表/实体|稳定键与合同|
|---|---|
|reference_dataset|UUID；当前结构化数据集，随业务备份保存|
|reference_runtime|仅本地singleton + dataset UUID + fresh epoch，不备份；整库替换退休，避免旧编辑器凭相同UUID/整数ID继续写入|
|reference_identity|UUID、dataset FK、MEDICATION/RECORD/CONTAINER、original handle、nullable live handle、owner UUID；唯一(dataset,kind,live handle)；删除旧行仅使live handle变null，保留墓碑和历史|
|reference_snapshot|UUID、dataset FK、PRODUCT/PRESENTATION、来源namespace/版本、canonical JSON、SHA256；只追加，字符串十进制，不使用浮点JSON表达精确量|
|reference_event|operation UUID、dataset、subject identity FK、revision、previous event FK、LINK/UNLINK/CORRECT、snapshot FK、request hash；唯一(subject,revision)，CAS，幂等|
|record_reference_capture|UUID、record identity/revision、独立capture revision、context event FK、实际presentation/容器可空；默认UNKNOWN，仅显式CONFIRM才有实际来源，CLEAR为新事件|
|measured_operation|operation UUID、dataset、record identity/revision可空、kind、request hash、冻结输入、未分配精确数量/单位/原因；同UUID不同payload严格拒绝|
|measured_allocation|UUID、operation FK、container identity、presentation event、legacy ledger entry FK、精确decimal/unit、原消费capture可空；只追加|

不复用Profile/needs_review/备注/附件身份表。混合规格按包装事件快照分组，P4不允许跨组自动拼凑。新精确消费以decimal capture为真值，旧Double账本投影不声称恢复原始精度；逆操作使用原条目投影和原精确capture，不用当前规格。P1只建合同/存储，不启用新分配器。

## 完整性与操作

- 所有UUID验证规范、尺寸与JSON预算有界；canonical对象key排序、array顺序保留、数值只允许明确整数或十进制字符串，SHA256在插入和恢复时复算。
- source namespace 与verification独立；USER_ENTERED不可标成官方核实。用户输入CIP须明确声明对应CIS，但此结构关系不代表已完成官方所属核实。SA/FT/ST原码保留，不归一或求和。
- 引用事件的前一事件/subject/dataset/revision连续；事务内expected revision对比，双编辑器过期拒绝。
- 同operation UUID、同canonical request幂等；不同内容拒绝。必须先验证当前dataset/epoch，不能因为重试UUID已存在跳过域隔离。
- UUID、dataset、kind、owner不可更新；handle只能从原ID变null，不能重新绑定整数ID。物理删除触发独立墓碑维护；active=false不算删除。
- 新表不可变触发器由SchemaGuards统一安装。恢复维护只执行可信当前DDL，保留所有既存额外守卫。自FK依赖使用有界迭代拓扑计划，包含事件、身份owner、逆消费关系，不能依赖文件行序。
- 历史捕获与实际记录revision分别建模。关联更正不得生成记录revision、库存冲销或历史回填。取消操作不产生任何行。

## 迁移/备份/回滚

非破坏迁移只新增表、索引、触发器；不改旧表、MedicationSnapshot、PK或附件功能开关。旧1…10完整迁移链保持；新Room Schema必须导出并由Room校验，旧APK降级安全拒绝。

DOMAIN_TABLES/显式columns/validator/clear/restore统一扩展；新业务Schema10必要表缺失拒绝，旧业务1…9缺失新表合法且默认UNKNOWN，导出必须如实写10。旧reader在写库前NewerBackup。运行期epoch不备份，恢复成功后生成新epoch；旧编辑票据无效。被恢复的业务UUID自包含，当前目录移除不影响历史。

P0预演内存库包含所有新业务表，完整检查FK、摘要、事件链、归属、capture/ledger关系；仅预演成功再退休本地域及按依赖清理。新旧数据库全部替换，不遗留目标库引用。任何故障由同一SQLCipher事务回滚业务行、身份、epoch、触发器；不得destructive fallback。

## 验证门

旧1…10 fixture → Room新版本、业务备份1…9 → 新reader；新完整roundtrip、旧reader拒绝、新旧数据集ID碰撞、当前目录不存在、坏摘要/孤儿/循环/重复UUID/过期CAS、不同UUID scope、部分DDL/写入/提交前故障、删除/恢复/墓碑、原账本和计划/PK内容哈希不变。API26/29/35原生SQLCipher需实际运行；物理设备/断电未跑则NOT_RUN。

实现补充：捕获的record_revision/capture_revision及事件revision限于正Int范围；仍存在的记录不能捕获未来revision。记录已物理删除的墓碑保持历史，不按复用整数ID的新行解释。canonical JSON明确自行转义、拒绝未配对surrogate，跨API摘要采用固定向量验证。旧业务头若夹带新引用表必须拒绝，不能用旧头绕过新格式准入。

空引用预览修正：reference_runtime在数据库初始化/打开时即保有本地epoch及预留dataset UUID；业务reference_dataset仍为空，预览/取消不创建或更改业务引用。MedicationReferenceState新增previewEpoch，首次关联必须携带它。恢复空的旧/新备份也生成全新本地epoch；删除medication/dose_record/supply_container退休旧编辑票据，禁止REPLACE已识别对象，未识别对象的同ID替换亦退休epoch。仅本地缓存行变化，不回填官方身份，不进业务备份，Room11/业务10保持。
