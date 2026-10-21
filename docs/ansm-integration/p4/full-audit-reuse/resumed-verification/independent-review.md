# 独立只读复核（2026-10-21）

源码f220b1f2，范围99e536fe..f220b1f2的domain/data执行复用。未发现必须修改的正确性反例；本结论不代替测试或原生验收。

- MeasuredOperationChain只保存精确不可变全文到Link/SHA纯函数结果；当前UUID、逐行/全局预算、序列、前驱和完整规则每次仍读库验证。
- clear轮换资源token，旧reader不能在清空后回填其明文；不把token或cache当数据库可信基线。
- 消费缓存不保存行头合法性。当前kind、request SHA、dataset、record revision、proof全文匹配以及unknown_reason保留。
- canonical最大16层且良构Unicode/UTF8≤65536蕴含旧词法32层、1MiB token/32MiB字节门；不匹配proof仍执行原门。
- reference扫描当前行与只读事务原PK重读等价；前驱/来源检查不变。captureHash只对NULL/String/Int/Long避免往返，其他类型走原算法。
- 冻结旧完整审计与旧Chain仍是独立对照。

风险：跨调用纯codec16MiB与单审计消费缓存16MiB是估算保留预算，可叠加且非RSS上界。全量扫描仍O(N)。最新完整回归/Android编译/API26/29/35/原生内存性能未结束，不能宣称LMK消除或5秒达标。
