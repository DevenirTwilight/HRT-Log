# 单次完整审计内执行复用（2026-10-21）

基线99e536fe，主线320171cd。负责人本轮重新要求解阻，REQUIREMENTS §125限定一轮新候选；旧恢复候选及其运行次数不重置。P4 Draft/OFF，不自动合并。此文是待验收候选的证明合同，不是性能通过声明。

选择A：保持完整审计的规则与O(N)复杂度，减少同一事务只读审计的重复解码/读取。拒绝B：SAVEPOINT指纹反例、完整依赖闭包/失效证明缺口未解决，不建立跨事务可信缓存。C部分唯一索引未覆盖全部历史语义，不占Room号或改备份。写前链头复用另有反例：跨checkpoint后可能插入尚无父操作的未来子节点，append恰好修复非法前缀；只依赖最终合法不能证明原前缀曾合法，且已有成本仅数百ms。本轮不实施链头复用。

## 选定变更与等价条件

1. **纯消费解码结果复用。** 私有PresentationAudit按完整input_json文本保存原decoder得到的不可变request/capture/物理receipt line；不保留JSONObject、行头、operation UUID或unknown_reason。每次访问均检查当前AuditProof对UUID+完整文本的精确匹配以及原kind/request_sha/dataset/record/revision，重新创建带当前operation UUID及unknown_reason的receipt。跨行唯一性、全部allocation/CONFIRM集合、prefix/临床行/因果和零效果操作仍逐条验证。固定16MiB保守分配估值上限（不是RSS保证），0/耗尽则重新执行原解码；finally清空。已构造对象的强制共享引用仅计一次，其余字符串/BigDecimal/容器成本保守估计。不会将相同SHA、数据库指纹或曾验证的其他事务当作信任证据。
2. **原文已证明的词法门复用。** checkedChain此前已经验证平台UTF8(text)≤65536与canonical(JSONObject(text))==text；canonical值深度≤16，最多17层容器，低于原lexical深度32。良构Unicode的UTF16长度≤UTF8长度≤65536，故全文/任一引用token低于原32MiB/1MiB门；canonical正确转义并拒绝NUL/孤立surrogate。只有精确matches成功时省去重复checkJson/UTF8分配，不匹配保留原门。头缓存是否完整与canonical证明无关。
3. **已有reference扫描行直接消费。** 原SELECT *游标当前行直接使用原typed decoder，不再按同一PK重复读取；前驱/snapshot依赖、动作、requestHash及captureHash规则不变。只读同事务内PK唯一性保证重读值相同，仍由ReferenceAuditReads fence结束检查。
4. **captureHash避免序列化往返。** 仅NULL/String/Int/Long直接复制；canonical字节不变。Boolean/浮点/复杂类型仍走原JSONObject(toString)回退，保留其类型转换行为。冻结旧hash差分独立验证；不通过hash代替业务语义。

这些结果仅在当前同步只读审计生命周期存在。无可信基线、连接/epoch信任状态或重启恢复状态；回滚/SAVEPOINT/并发事务后的下一次审计重新读取并验证全部历史。若未知修改导致原规则失败或读scope变化，拒绝操作，外层事务回滚。未改触发器、写入口或最终COMMIT边界。

## 独立对照与安全验证

99e536fe Store、BackupValidation、ReferenceSchema冻结于historyTest，改名仅隔离测试对象；captureHash另冻结旧函数，源SHA及测试副本SHA在frozen-oracle.json。不随候选同步改写，不编入main/release。原A/B反例、原Receipt与列扫描oracle保留。预算0/1/8/16MiB、良/坏行头、同UUID异文本、未知reason、null/数字词法/Unicode/深度边界、零效果、多来源、损坏capture和SAVEPOINT恢复，分别由共享JVM/真实SQLCipher合同覆盖。完整原生固定夹具在原计时结束后另以冻结完整oracle比较投影结果；不替换原审计/性能断言。

## 性能和停止机制

现有9d诊断同笔提交7.608秒，其中完整审计5.972秒；该值含观测扰动，独立ART/诊断不可相加作预期节省。新候选有直接重复工作证据，但无法事前保证≤5秒，仍O(历史)。没有改SQLCipher写入、Schema/Room13、业务12、PNBAK1/KDF或旧备份合同。冷库/重启/恢复仍承担全部审计，没有移出原操作计时边界或启动后台预热。

先本地完整回归、冻结old/new差分和原生编译。冻结新源码后最多两个大库原生：首轮若写入明显FAIL或LMK则停止同源码复跑；只有首轮全部硬门通过才做第二轮有限稳定验证。原API26 SQLCipher512MiB/192MiB、14600/2000/32603及所有5s/15s/120s/大小门保持。小规模生命周期/增长合同另标注其数据范围，不能代替大库或冒充应用新进程。失败全部保存，不用单次绿色掩盖已有失败。
