# 精确库存恢复实施顺序

状态：IMPLEMENTED_INTERNAL / LOCAL_INTEGRATION_VERIFIED（依据REQUIREMENTS §102、104；不是另行请求权限）。

公开入口在最终验收前维持legacy-only。实现应复用RawData与正式NotesRepository，不添加绕过校验的测试代理。

1. 捕获目标Space，解密后严格UTF-8/JSON、独立业务版本、必需表/字段和整数词法校验；错误版本不得打开目标数据库。精确恢复限定PRIMARY并必须匹配调用前展示的本地epoch。
2. 在当前可信DDL构造的内存库内插入候选业务行；不导入runtime/preview epoch、不执行备份DDL。供给流水及引用按既有RESTRICT依赖顺序写入。
3. 仅LEGACY_SCALAR使用旧缓存重建逻辑；INTEGER_QUANTA_V1不得进入SUM(Double)。先检验每个精确容器的冻结包装、全操作及完整历史，再验证缓存是否一致；当前策略是拒绝不一致，不修复用户历史。
4. 同一个外层Room事务捕获目标、退休旧runtime、清理业务依赖、插入候选、重复完整验证、建立新本地epoch。任一步失败必须回滚所有业务表、身份、触发器和epoch。
5. 新设备/非空覆盖/重复恢复、零差额ADJUST、空消费、完整跨包装REVERSE、ID复用及退役身份均进入正式Repository合同。源码编译、JVM和SQLCipher API26/29/35分别标记。

历史VOID/RESTORE正文一致性及冻结前缀校验已完成本地336项回归。内部精确恢复路径已经接入正式Repository，完整data342/共享68合同本地通过；原生运行仍NOT_RUN，不代表正式入口验收。公开库存UI/旧标量预测不能因此自动处理新模式；统一不确定性合同就绪前继续关闭入口。
