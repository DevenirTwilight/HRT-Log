# build16 历史识别覆盖验证

2026-10-08；功能源码1093755，先行设计a505c94；versionCode16/versionName0.2.0/schema6，仅full。

用户确认历史药物/舌下途径/实际剂量正确，主要每天两次、偶尔漏记。没有读取用户实际健康文件。

## 实现与回归

- ObservedTreatmentHistory：有限窗口支持完整剂量向量及部分漏记日；没有三个连续完整双次日的21天合成历史仍识别一个双次模式，全部原记录进入时期，无待识别摘要。
- 26项识别回归覆盖持续量/频率变化、双次→单次→双次、途径、稀疏重复间隔、不规则稀疏日期不伪造每日、长空白未知、完全重复记录不翻倍、同日保存版本不跨越、不重叠与透明降级。
- 同持久ID的记录与已有时期比较已知字段，缺失仍未知；已知route/unit冲突不归并；跨ID缺失身份不任意合并。仅APP明确冻结rule补缺，IMPORT不借用。记录自身保存zone分日。
- 至少三个日期的部分已知历史可以显示OBSERVED/频率未确认时期；不建立新处方/提醒规则，不将漏记判成漏服、不推定停药。
- ImportedHistoryIntegrationTest：合成HT JSON→真实Room→时期；重复导入和加密备份恢复后所有RecordEntity与投影相同，regimen/rule/container仍为空。
- Native Graphics Compose与API35：双次部分漏记在时期内显示每日两次，待识别卡不存在；点击时期历史打开所有确切源IDs。保留同日保存变化闪退回归。

## 本机完整检查

最终build16-final-build-checks.log：8m12s BUILD SUCCESSFUL；domain39/pk18/importer12/data58/reminder14/app163，共304登记、291通过、0失败、13既有跳过（PDF1/按需按钮审计12）。lint0错误99警告；full debug/release与两个Android测试APK成功；release manifest身份/入口/无INTERNET与schema/PLAN无漂移通过。功能实现后完整构建未改码，签署只用最终release。

前轮编译遇跨模块可空字段智能转换限制，改为显式可空访问后重跑。扩展测试发现窗口交错拆段，改为同模式先合并；UI测试对“空白时期”原本错误假定没有时期，改为检查空标准集合，保留未知覆盖断言，最终全部通过。

最终功能CI [37752990023](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37752990023)，源码1093755，jvm/android/device-tests三个任务全部success；API35数据11、应用主套件28项通过，另两个重启用例独立进程各OK。正式签名交付待完成。不声称用户OEM覆盖安装、真实历史全量识别或人工TalkBack新验收。识别是只读记录模式，尚无人工持久化确认/编辑或完整药物生命周期状态机。
