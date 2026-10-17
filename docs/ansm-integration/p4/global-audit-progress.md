# 全局候选验证实施记录

2026-10-17，续作任务原§104（现§105）。工作基线10480c6，当前候选仍未连接正式备份或Repository。

10480c6的push/PR六必需检查全PASS；API26/29/35均实际执行共享35项，0失败，报告GitHub SHA256/ZIP CRC已核验，无APK。见staging-ci-evidence.json。

已复现并留原日志的两项真实负例：

- 仅遍历有包装的投影会接受没有流水的伪造UNKNOWN结果。测试实际FAIL，随后全操作扫描转绿；global-red-evidence.json。
- 零差额观察可伪造为引用消费/冲销之后的同余额账本头，仅看数值和容器前缀会接受。测试实际FAIL；global-causal-red-evidence.json。

首轮全操作候选共享40项PASS（global-initial-green-evidence.json）。编译首轮因Quantity/Long结果联合类型丢失数值类型FAIL，明确为编译错误；修正显式MeasureResult<Long>，未修改断言。此首轮不是最终源码/全数据集验收。

当前补充：CONSUME执行捕获v2冻结源包装状态、先前账本头与准确数量；稳定用户请求仍v1且原SHA语义不变。CONSUME/ADJUST/REVERSE采用v2执行envelope，单独保留稳定请求SHA，并保存有序chain（规范sequence、前序操作UUID及其完整执行载荷SHA）。只在新操作实际提交时取下一链节点；成功重试不推进链，也不重新捕获观察。当前格式之前从未启用正式入口或备份接受；缺新执行证据的旧试验载荷明确拒绝，不回填、不静默升级。

全局候选通过操作序列核对每个CONSUME/ADJUST的实际先前容器账本头，包含没有流水的操作。全局序列不依赖created_utc或rowid。未来相同余额的陈旧确认票据可绑定此版本，但正式ABA票据/UI尚未实施。新增负例在篡改观察头后重新计算完整前序摘要，必须仍因因果不一致而拒绝，不能只依靠摘要碰巧不匹配。

当前新链实现正在运行共享41项，不能标PASS。退役身份、生命周期、全域严格类型/预算、完整逆观察协调及正式恢复仍需补齐验证；业务备份Schema10拒绝门、Room12候选版本、生产计量入口OFF均不变。不得把本阶段进度视为P4-A或P4整体完成。

最新全量本地data324/共享50/原生测试源码编译PASS，Python158 PASS。global-full-local-evidence.json保存来源指纹。独立观察后逆操作的全局遗漏已真实FAIL并修正（global-reconciliation-red-evidence.json）；当前App时期稳定性/隐私8项回归运行中。

下一步必须补库存编码自描述标记及独立业务Schema11（stock-encoding-version-contract.md），先用引用表整体缺失的负例证实边界。当前仍不能把这一检查点称P4-A整体完成或启用恢复。

最终本地data324/共享50/原生编译/Period5/Privacy3/Python158全部PASS，新50项原生实际执行NOT_RUN；完整来源指纹见global-full-local-evidence.json。已增加原IO/PK/导入边界脚本对新操作因果链的负向守卫，未削弱既有断言。下一步骤的独立库存编码仍必需，P4未完成/未激活。

2026-10-17 历史包装补强：两个真实红例证明已RESTORED行的正文/历史容量曾未校验；现v2保存作废时ledger_head_id，验证冻结前缀hash/ADJUST-only以及StockQuantum精确投影，并强制RESTORE正文沿用原VOID正文。v1发布兼容保留，不能将v1解释成新模式。完整data336零失败，原生源码编译PASS；新原生运行NOT_RUN。见lifecycle-body-{red,green}-evidence.json。

Python边界守卫发现PackageCorrections新增MeasureResult依赖超出既有白名单。根据§102/104仅增加此文件的完整明确imports角色；网络、文件IO、隐式时钟、反射、PK、旧自动分配禁令保持，并在临时副本上逐项注入违禁token及额外import验证拒绝。新增负向断言还发现初版角色条件未并入总status，已修正，未删减旧断言。

接着实现同一RawData暂存/事务替换的内部精确恢复入口，正式Repository捕获目标Space和本地epoch；新公开入口和UI仍关闭。完整回归尚在执行，不能提前标INTEGRATION_VERIFIED。
