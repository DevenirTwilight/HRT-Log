# build17 相同方案跨来源连续显示验证

2026-10-08；先行设计d70dea7，最终功能2ba5860；versionCode17/versionName0.2.0/schema6，仅full。

用户反馈10月6日导入前后相同方案仍分段，要求导入取消特殊标记。没有读取用户实际健康数据。

## 规则与验证

- 仅UI方案适配器归一12h一次与每天双次、24h整数倍与N天、每周七天interval1与每日；36h、不完整星期、不同每次剂量/分布/次数/途径不误合并。原冻结JSON、clinical_signature/时钟/提醒不改。保存版本审计读取原频率表达。
- 同已知身份和完整临床标准的紧邻保存版本允许每日最多24h短边界连接，覆盖上海记录自然日→UTC保存起点；不跨中间保存变化/观察到的不同模式或长空白。保留build15不重叠与透明降级保护。
- SourceNeutralContinuityTest6项包含合成9月25日至10月5日双次历史→10月6日12h保存版本、混合APP实际记录与唯一跨ID身份，只有一个时期/标准，全部原始IDs可读，化验仍只关联正保存ID；日历排程同样合并。未知空白/剂量次数变化仍分段。混合未匹配来源只出现一个汇总；MISSED/SKIPPED使用原计划时间关联，actual/taken仍null，不作为识别模式证据。
- ImportedHistoryIntegrationTest真实JSON→Room→当前保存方案+APP记录：一个时期；重导入与加密备份恢复后原RecordEntity、保存版本及投影完全相同；不新增规则或库存。
- Compose Native Graphics与API35新入口：一个普通时期、没有过去段/识别/误报旧方案重建标记，点击打开全部确切IDs。History同样无导入徽标、无计划时刻按所有来源显示未排程。已检查合成历史时期截图无来源标记/截断；不是用户真机或目标10月6日截图。
- 未匹配APP/HT/TM按时期/未来共同分桶；UI不显示来源工具名，四语计数/清除筛选去掉导入限定，仍保留未知信息说明和原始入口。origin/source key/快照/revision保留，数据不改写。

## 最终本机检查

build17-final-build-checks.log完整full任务9m2s成功；domain39/pk18/importer12/data58/reminder14/app172，共313登记/300通过/13既有跳过/0失败（PDF1/按需按钮审计12）。lint0错误99警告，full debug/release与两个Android测试APK成功；manifest身份/入口/无INTERNET、schema/PLAN无漂移通过。新增集成测试字段名编译错误修正后完整重跑。

最终功能CI [37756924266](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37756924266)，源码2ba5860，jvm/android/device-tests三个任务success；API35数据11、应用主套件29项，另两个重启用例分别独立进程各OK。正式签名与下载回查待完成。用户具体数据/OEM覆盖安装和人工TalkBack新验收未验证；部分未知字段/无稳定频率不冒充确认处方，完整人工确认/药物启停状态仍后续。没有修改公开Release或标签。
