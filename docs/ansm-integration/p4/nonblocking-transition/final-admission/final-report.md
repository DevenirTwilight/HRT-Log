## PR #57 已完成受保护合并 — 2026-10-21

负责人明确指示“合并”后，复核最终源 `f420fbf1c4fec4b989f2694fd85e0b6b59dbbfc2` 为 Ready/CLEAN/MERGEABLE，六项必需检查全部通过、无未解决讨论，使用匹配 HEAD 的普通 merge 完成；未使用 admin、force push 或绕过保护。

实际合并提交：`b04358fdb1c79f04136afdf46865e9941c980deb`，时间 `2026-10-21T19:54:16Z`，默认分支 `claude/new-session-1959qb`。合并后 Android/domain run `39665680148` 记录时 QUEUED；合并前通过与合并后运行明确区分。原失败和未实测项目全部保留。

正式入口仍 OFF，P5/P6 未启用；没有签名、交付 APK 或发布 Release。原 5 秒失败作为历史及独立指标保留，非阻塞产品策略不豁免恢复安全。真机及人工 TalkBack 仍 NOT_RUN。

# P4 合并准入结论（2026-10-21）

**MERGE_READY。** PR #57 已转 Ready；GitHub 实际状态 `CLEAN / MERGEABLE`、检查汇总 `SUCCESS`、六项必需检查全部通过、零未解决审查讨论。没有自动合并、发布或开启正式入口。

- 待审 HEAD：`f420fbf1c4fec4b989f2694fd85e0b6b59dbbfc2`。
- 默认分支基线：`4ec11437acb257ecc0e9c92ed59907d954cbacae`，已正常整合。
- PR 测试合并提交：`575eef74038d5868e00cd73421f902a58d3bfa75`，其 tree 与待审 HEAD 均为 `4d85ff4668b0156c7c41219e8743fd6bfd71d460`。
- 本证据分支不用于生产集成；构建/合并以 PR 的上述 HEAD 为准。

## 完成的决定与实现

保留精确整数库存、冻结包装/来源、追加 CONSUME/REVERSE/ADJUST、UUID/epoch/revision/ABA、完整历史审计及原子回滚。保留 A/B 反例与独立冻结 oracle，没有接增量/可信缓存或方案 C。

采用已实现的进程级 SupervisorJob/IO owner、共享冲突门和真实 NotesApp 状态观察；离开页面不取消任务，可浏览受限只读页面。PENDING/SUCCESS/FAILED 与 UUID 绑定；只有根事务真实提交或独立完整原请求核对后 SUCCESS，未知结果不自动换 UUID/重放。不是持久队列，也不是承诺进程死亡后自动续写。服务范围为已冻结的独立实际用药；P5/P6 的正式表单与其他交互接线未启用。

依据 REQUIREMENTS§131，普通写入耗时独立记录；原 6.387/6.285/5.666 秒及旧 5 秒 FAIL 均保留，不宣称原预算通过。没有恢复性能攻关或再次执行大库求绿。审计/预测15秒、备份/恢复120秒、原大小/行数/资源约束不变。

## 最终真实验收

|检查|最终结果|
|---|---|
|Android/domain PR run39656387819|全部 SUCCESS|
|Android/domain push run39656377056|全部 SUCCESS|
|Science PR run39656387915|gate、deep 均 SUCCESS|
|Science push run39656376924|gate SUCCESS；deep 按既有分类器 SKIPPED|
|数据层单元|528 执行 PASS，0 fail/skip|
|App 单元|1924 列项，1605 执行 PASS，319 SKIPPED，0 fail；不把 skip 算通过|
|PeriodStability / 真实 owner 页面|5 / 2 明确 PASS|
|提醒单元|21 PASS|
|API26 / API29 SQLCipher|各335合同，0 fail/skip；均实际进程192MiB前置断言通过|
|API35|data407列项/0fail/5显式host探针skip；App282列项/0fail/10 action probe skip；对应host操作另行执行|
|逐 API 进程死亡|STOCK_CAPTURED、AFTER_COMMIT 外部强杀→新PID→精确数据/epoch/完整oracle/无重放均 PASS|
|lint / No INTERNET / 隔离 / APK来源 / 科学冻结回放|最终正常 CI PASS；未签名、未交付 APK|

官方原ZIP、SHA/CRC、关键原HTML、来源SHA和各 kill JSON均保存；见 `protected-f420/`，不是只凭job绿灯。完整127MB构建ZIP保留于官方工件和workspace scratch，仅必要原HTML进Git，避免巨型截图堆积。全套工具219 PASS，既有断言没有削弱。

## 恢复安全与失败证据

恢复作为独立门核对，没有因异步UI豁免。既有冻结 API26/512MiB RAM/192MiB heap、14600记录/2000容器/32603行/19660924字节的完整恢复为44.906秒，业务完全相等；另一次索引恢复完整窗口41.633秒。当前解密、索引读取和备份验证核心与该结构性修复一致；后续所有正常/异常/取消出口、旧备份、版本拒绝、替换回滚与全局oracle均经上述单元及三API合同验证。

已保存的恢复100ms观测峰：Java119895008B、native malloc34166360B、RSS125243392B；不能相加或当瞬时绝对峰。原准备/恢复LMK都保留，不把DOM推定为唯一原因，也不把有限验收解释为所有设备稳定。

本轮 API29 的真实失败是实际16MiB Java堆下的Argon2分配OOM，原日志/ZIP保留。修正是显式还原原192MiB并核对真实Runtime.maxMemory；32MiB Argon2id参数未改，不是扩大冻结预算。最终两个自动流程中的API29完整通过。较早AFTER_COMMIT一次超时的根因仍未证明；原FAIL保留，与随后多次严格原生通过并列，不伪称新增日志修复了生产缺陷。

## 边界与后续

当前既有候选 Room13 / 业务备份12（默认12/11），相关非破坏升级和旧PNBAK1兼容在原合同中验证；本轮没有再增加Schema或改变PNBAK1外层密码学、PK、权限、签名/版本。历史、库存账本、原快照不重写。

正式精确库存入口仍OFF；P5/P6、官方资料分发和MED-02附件均未启用。普通写入仍 O(历史)，耗时继续是可见性能指标。真机与人工TalkBack仍 NOT_RUN；本结论是既定范围的工程合并准入，不是获准发布或临床认证。
