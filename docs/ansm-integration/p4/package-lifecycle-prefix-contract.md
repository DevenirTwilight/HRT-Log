# 包装生命周期的冻结前缀

TESTED_LOCAL：完整data336零失败；新增两项红例先失败后通过。原生测试源码编译PASS；新用例的SQLCipher运行尚待CI。

v2作废证据增加ledger_head_id（十进制字符串，0表示当时无流水）。校验每一条历史VOID/RESTORE，而非只看当前VOIDED状态：

- 容器UUID必须对应同药物的当前稳定身份；旧v1只支持LEGACY，不升级为精确身份。
- 当时的流水前缀必须存在、末ID一致、摘要一致且只有原本允许的ADJUST。后续合法流水不覆盖旧证据。
- 精确容器的历史capacity/initial_used/used必须保持整数词法与有界范围；冻结包装的StockQuantum容量须一致，并用同一核心重放前缀得到历史used。
- RESTORE正文只允许修改operation_id/action并添加restores指针；包装事实、来源、摘要和前缀必须与原VOID一致。
- 现有“有CONSUME/REVERSE不得作废”、当前VOIDED全行/完整流水一致性及上下文变化阻止恢复保持不变。

这不是容器删除后按整数ID重建身份的机制，也不新增商品推断。v2是本未合并分支的候选格式，旧公开v1保留原兼容算法；不能声称旧Double精度被恢复。公开精确库存UI尚未启用。
