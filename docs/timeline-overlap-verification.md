# build15 Timeline闪退热修验证

2026-10-08，最终功能282efea，复现先行db3682f。

## 复现与修正证据

用户报告仅升级build14后进入时间线闪退，没有新导入/新增记录。尚无用户设备堆栈。合成历史2mg每日、同日保存3mg→2mg：修正前`ObservedTreatmentHistoryTest.sameDaySavedDoseChangesMustNotBeBridgedByObservedHistory`失败，异常`IllegalArgumentException: Overlapping recorded regimen`来自TreatmentPeriods.kt:49，经PeriodTimelineProjection入口抛出。

根因是桥接同日后续匹配标准时跳过中间不匹配保存版本。现在先找紧邻下一个保存边界，后判断相同身份/标准/日期能否接续。保存版本永远优先，domain严格不重叠校验不变。

新增识别投影若非法，`projectHistory`只回落至合法保存方案投影，清除错误识别结果的resolved IDs，保留全部原始记录入口，并显示识别不可用说明。不修改数据库、记录快照、提醒、库存或旧上下文；不是默认关闭历史识别。

## 最终验证

- 17项识别测试：原13项 + 同日重叠复现 + 紧邻边界不匹配 + 32种同日版本组合 + 非法派生区间降级。
- Native Graphics Compose进入Timeline、当前时期显示、打开审计仍可查看原始3mg保存版本；对应新增API35原生回归通过。初次UI测试选择两个同名按钮错误已修正，并保留原始版本可见断言重跑。
- 293登记用例，280通过、0失败、13跳过（既有PDF限制1、按需按钮审计12）；完整full构建7m55s成功，lint0错误99警告。所有模块、full debug/release和两个instrumentation APK完成。schema/PLAN无漂移，正式manifest身份/入口/无INTERNET检查通过。
- CI https://github.com/DevenirTwilight/HRT-Log/actions/runs/37748102641 三任务success，最终源码282efea。API35 data11、app29登记（两重启项主套件跳过后各独立通过）。
- 正式签名、版本、非debug、16KB ZIP/8个ELF、release mapping和独立真实下载SHA/大小检查通过；交付元数据在HANDOFF顶部。

未读取真实健康数据，没有用户设备堆栈，不能认定修正的合成漏洞是用户设备的唯一触发路径。用户覆盖安装后的真机结果仍待反馈；不得要求卸载/清数据来规避。build14的稀疏/未知/复杂模式限制保持，医疗模型与数据存储无变更。
