# B0 验收映射

TEST_ONLY全部合成；设备/CI实际执行版本见verification及PR #51最终HEAD。没有测试未运行就计PASS的条目。

| 要求 | 可复现合同/证据 | 边界 |
|---|---|---|
| 原精确模型/35测试 | 原四文件SHA256、prototype-review | 不移动main |
| 同规格FIFO/耗尽/余额/短缺 | ShadowMeasurementAllocatorTest；真实baseline | 精确量与旧binary64只近似比较 |
| 多规格/明确来源 | mixed strength与explicit source影子；真实mixed pool | UNKNOWN，不拼剂量 |
| 凝胶泵/mg/未知容量 | 影子gel两项、真实gel合同 | 名义64不作实际容量 |
| 贴用/脱落/移除/修订 | 影子patch、真实patch历史 | 移除无新消费 |
| 旧库存/药物改名/变规格 | 真实unknownLegacyContainers | 不补身份，不恢复旧精度 |
| 完成/更正/冲销/恢复/调整 | baselinePublicRecord...与ledger-baseline.json | 全表/DDL前后指纹 |
| VOIDED/恢复/拒绝已消费 | voidRestore...与void-restore.json | 保留原限制及ledger前缀 |
| 幂等/同op冲突 | 公开complete、候选operation、影子replay测试 | 内部allocate缺业务op的边界显式暴露 |
| 重复/错误行/顺序/revision | 影子compare负向测试；真实二次allocate检测 | 不仅比较总量 |
| 错误标量适配器 | unsafeMassScalarBridge... | 故意TEST适配器错误，不改生产制造失败 |
| 不可变源/capture/作用域/复用ID | snapshotCaptureForeignKeys... | 其他FK成立时拒绝旧域独有容器 |
| 事务故障/DDL回滚 | recordLedger...四边界；candidateDDL...每条SQL | 异常回滚，不等于断电/强停 |
| Room1..10旧链 | allOldRoomVersions... | 真实schema+allMigrations，仍Room10，无正式N迁移 |
| PNBAK1/schema9 | 真实导出/解密、fresh-target逐表相等 | 非空REVERSE恢复失败并完整回滚，未修复 |
| 旧恢复遗留新身份/守卫 | oldBackupCannotSilently... | 已揭示接线风险；外层包装器仅测试 |
| 新业务schema旧reader拒绝 | 同上NewerBackup与全库指纹 | 没有新schema完整writer/reader |
| 无当前目录读历史来源 | 冻结payload→readPayload→convert | SYNTH摘要，不是官方合法分发证明 |
| 日历/预测/提醒/DST | state实际StockForecast；Ansm03b0ForecastBoundaryTest | 不改366/7，不发送通知 |
| 未知预测合同 | shadow forecast tests、forecast-contract | 正式UI/可靠日期计算NOT_IMPLEMENTED |
| 生产隔离 | 八组Git树对象、35测试哈希、JAR/APK守卫 | 无正式数据/模型/权限/历史写入 |
| PeriodStability/锁/伪装/PK/备份旧回归 | 原六CI原断言，API35完整回归 | 不用新Mock替代既有测试 |
| 真机/ARM/新协议强停 | NOT_RUN | 不能借原有重启用例宣称新协议已验证 |
| 正式B1/发布 | PRODUCTION_BLOCKED / NOT_IMPLEMENTED / 未发布 | 12准入门及具体授权均需另审 |
