# 主动跳过服药的时间线修复 — PR #177

原始持久记录正确；历史归属忽略未来时刻，未归属记录随后落入待识别概要。候选进一步修正：APP、非空slot、规则和预定时刻明确的SKIPPED是已经确认的操作，在原计划时刻所属阶段展示，未来时刻也不进入Upcoming。原始taken_utc/actual_dose不变，不推断停药；导入或缺少证据的历史保持原流程。冻结药名、计划剂量和原时区可查看，简易/伪装模式原隐私语义保持。撤销继续调用原Repository安全事务。

实际本地结果见local-results.json。真实Room保存、普通加密备份/恢复、时间线重建、同日其他时段、无库存扣减、无实际浓度输入和依从性跳过分类通过。新MainActivity真实日程→跳过→时间线→新Activity历史→撤销合同编译通过，已接既有API26/29兼容作业和API35完整作业；运行结果必须以准确SHA的CI为准，编译不是原生PASS。

## 保留失败

- initial-projection-failure.xml：首次本地候选（基于446c618d加确认事件分类）12项中1失败；原PR的“无关联”夹具却用了已知药品ID，旧识别器正常归属，实际unknown为空。改夹具到真正无计划关联药品，未改识别器。
- initial-backup-fixture-compile.log：新增测试把ProfileEntity误传给ester:String参数，修正为E2 ester，没有生产代码放宽。
- initial-backup-window-failure.xml：新增测试将14日calendar窗口误当单日；改为先按目标日筛选，仍精确要求一个SKIPPED和另一个未跳过时段。
- initial-ui-selector-failure.xml：方案和跳过事件均正确显示药名；新增测试误要求全页药名只能有一处，改为精确验证对应skip行的名称。
- second-ui-text-matcher-failure.xml：完整时间确含UTC，新测试误用非子串Text matcher，改为明确substring=true；保留UTC断言和计划剂量断言。

没有删旧断言、提高时限、改数据库/服药事实/药代参数/库存/提醒或签名。四语320dp/2倍字为自动Compose验证；真机、人工TalkBack未运行。无本机KVM，原生按既有CI实际运行。

## 最终对话框/无障碍校验

新增真实OverrideDialog四语交互，发现合并语义树中三个未具名Switch无法按可见兄弟标签唯一选择（override-unlabelled-switch-failure.xml）。在现有SwitchRow上为Switch添加自身label的contentDescription，不改布局、开关顺序或业务回调；MainActivity原生合同按该真实名称操作。四语下明确点击跳过、保存，结果严格等于仅skipped=true的原SlotOverride。最终8项四语界面/真实开关保存及AndroidTest编译PASS；全部直接App目标合计57项，core原撤销33项PASS。final-four-language-ui.xml为实际最终运行原件。最终精确HEAD的完整CI和原生仍待验收，先不合并。

## 已保存的改时/剂量覆盖丢失：实际生产缺陷

四语真实OverrideDialog明确复现4/4：已有rescheduled/zone/dose=3.0的覆盖，在点击跳过后提交成全部null（原件saved-override-loss-before-fix.xml）。这会改变用户此前已经确认的计划槽位，不能只修时间线而掩盖。生产Dialogs.kt现在仅在skip=true时沿用initial已保存覆盖；隐藏的未保存表单草稿不作为新剂量提交；普通改时/改量、取消、重置及Repository原安全撤销均保持。修复后12项四语UI全PASS，App直接目标合计61项、core原撤销33项。原生MainActivity先准备已保存改时/3.0剂量，然后真实菜单跳过、COMMIT和历史撤销，严格断言原有效时间/剂量保持、无实际服药/库存变化，仍须准确HEAD的三API实际执行。
