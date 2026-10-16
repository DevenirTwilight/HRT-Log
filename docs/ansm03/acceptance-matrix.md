# 验收映射（原型与生产分开）

运行状态只取verification/local-verification和最终PR检查，不由下表声明通过。

|用户场景|实际验证入口|仍未实现的正式部分|
|---|---|---|
|mg/µg/g；mL无浓度不可换|MeasurementCoreTest质量/量纲/注射方法|正式资料浓度适配器|
|分数片、精度、不支持1/3|fractional tablet、high precision、explicit inverse性质方法|任意分片含量依据/用户确认UI|
|不同规格或成分、盐酯水合物、SA/FT/ST|same compound different strength、compound ester hydrate、multicomponent、chemical equivalence方法|官方字段归一化/冲突处置；不求和|
|28片2mg，用户剂量与规格不符|fractional tablet、declared mass discrepancy；两事实保持|不替用户更改剂量/历史|
|凝胶80g/64名义次/1.25g/0.75mg|pumped gel、unverified actual capacity方法|灌泵、损耗、实际可用库存；名义信息不授权扣减|
|贴片数量/载药量/释放速率|patch total load、manufacture release拒绝方法|贴用、提前脱落、移除事件新语义NOT_IMPLEMENTED|
|胶囊多途径|capsule方法只算质量，不暴露途径/PK写API|正式途径选择和容器接线NOT_IMPLEMENTED|
|20mg/mL × 0.25mL|injection方法输出所声明酯型5mg；不同身份拒绝|不新增生产注射产品支持|
|未知来源/容量/历史；CIP或版本错|missing history/source、wrong product、snapshot borrow、remaining方法|旧容器维持legacy unknown，无回填|
|多包装消耗、不同规格并存|既有SupplyLedgerTest跨容器；新core拒绝跨规格计数相加|新型多规格自动分配NOT_IMPLEMENTED|
|历史规格/目录/计划变化|SyntheticHistoryContractTest冻结捕获；既有PlanEditTest/PeriodStabilityTest|新持久化历史表NOT_IMPLEMENTED|
|重复操作、逆事件、耗尽|合成single-container oracle；原SupplyLedgerTest、StockCorrectionTest真实Room|新事务/新转换捕获的强停集成NOT_IMPLEMENTED|
|VOIDED/REVERSE/ADJUST/restore|原StockCorrectionTest、SupplyLedgerTest，CI原设备回归|不重构原生产策略；合成voided标记只检验拒绝消费|
|负数、零、超大数、高精度|invalid numeric/quantity、exhaustion、actual use方法|地区数字输入UI不在本轮|
|旧PNBAK恢复、Room兼容|DataTransferTest、AttachmentMigrationTest，最终API26/29/35原测试|新业务schema及新数据roundtrip NOT_IMPLEMENTED|
|实际服药修订|StockCorrectionTest.fixingTheDoseRecordFirstThenCheckingStockLandsOnTheCount等；原CI全量|新来源元数据修订不得触发实际量冲销|
|时区/DST/日期边界|原StockForecastTest.daylightSavingSwitchKeepsLocalDates、PeriodStabilityTest|核心无时钟输入；不以不相关日期循环冒充库存时区集成|
|PK结果、锁/伪装、No INTERNET、四语|生产路径git对象完全一致；原全JVM/最终CI权限/科学/设备/四语守卫|无正式接线/新UI/人工真机证据|

35项新测试的精确数目有XML佐证；固定种子性质循环不是数千个独立JUnit测试。
生产B3附件/医疗文件功能仍关闭，与ANSM-03B编号无关。无新增依赖、Gradle/CI/主线字符串/Schema修改。
