# B4-E 包装历史分页与长期可用性（2026-10-13）

## 基线与范围

PR23最新 `8749207ad883161275ead8a3ec458b0a352cc868` 已完成验收，保持OPEN未合并；独立分支 `feature/b4e-package-history-20261013`，后续PR指向 `feature/b4-stock-consistency-20261013`。原已验收PR23和正式build26 APK不动。需求记录§84；不签名、不交付、不自动合并。分支范围提交8013507的Android [38449774213](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38449774213) / Science [38449774128](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38449774128) 已完成success，Science gate/deep均实际执行通过。

## 实现

- 仅 `PackagesDialog` 改成高度最多420dp、同时服从窗口剩余空间的单个LazyColumn；移除管理窗口外层verticalScroll及逐包装Composable循环。四固定组的DSL循环只创建懒列表定义，不直接组合每个包装。首页库存摘要及统一入口原样保留。
- 使用中/未开封默认展开，已用完/已丢弃默认折叠。每组初次最多20条，按组追加20条，最后不足20时只追加余数，结束移除操作；高级VOIDED同样独立分页，关闭高级查看后再次打开重置20条。
- 包装项key是数据库包装Long ID，组头/继续显示/高级入口有各自稳定String key。开封日降序、同日ID降序；不依赖Room返回顺序。不同药物过滤、VOIDED普通隐藏、普通计数/四组/预测规则保持。
- 点击继续显示前记下首项索引及像素偏移，并requestScrollToItem保留这一位置，避免“继续显示”作为首个可见key后移动到新页尾部。其他分组状态不变；详情覆盖期间管理列表仍在同一组合中，返回保留位置；移组/移除后老行及其详情/作废/恢复按钮从列表消失。
- 页数与展开状态仅remember保留于当前对话框，不增加数据库缓存、后台轮询或持久分页状态；关闭重开采用默认。列表状态可标准hoist用于真实UI测试。

## 规模和可复现懒加载证据

合成数据已在内存：这是UI渐进展示，不声称数据库分页或减少首次查询。共同UI交互契约分别运行native Robolectric及真实API35，四语320dp有效布局/2倍字号；既核对所有包装ID的Lazy IndexForKey索引、全页无重复/遗漏/同日稳定排序，也记录空闲视口里实际挂载的包装语义节点。

| 总数 | 历史初始展示 | 展开首批 | 后续/末尾验证 |
|---:|---:|---:|---|
| 1 | 0 | 1 | 无继续显示 |
| 20 | 0 | 20 | 无继续显示 |
| 21 | 0 | 20 | 追加余1条至21，移除继续显示 |
| 100 | 0 | 20 | 按20至100完整核验；末尾无继续显示（最终本地加强验证通过） |
| 500 | 0 | 20 | 25批至500，每批全ID索引核验；末尾无继续显示 |
| 100 VOIDED | 0（高级入口默认关闭） | 主动打开后20 | 按20至100，末尾无继续显示；再次关闭/打开重置20 |

首轮修正后本地56项全部通过：新UI24、旧StockCheck12、旧VOIDED20；Android仪器包也编译成功5m59s。本地132条测量记录中，1条时挂载1个包装节点，20/21/100/500首批均挂载2个；四语首批均一致。500的25批全展开及100VOIDED完整展开，整个测量最大3个挂载包装节点。不是将500项定义误称500个已组合节点，也不是统计每次函数重组合调用次数。进一步补100正常包装末页并让合成SEALED/恢复EMPTY快照字段符合常规状态，最终本地新UI24复跑零失败/零跳过，新增100普通包装完整末页后共136条测量，最大3个挂载包装节点；真实设备数据随后补齐。

`PackageHistoryUiContract` 是两种运行器共用的交互测试（构建脚本仅增加测试源目录），不重复实现分页算法。真实设备日志JSON由 `collect_package_history_evidence.py` 收集到device-test-results；缺语言/规模/末页数据或挂载数达到20时明确失败；采集器3项回归通过。设备测试用局部密度构成320dp有效布局，2倍字体及四语Android窗口资源；测试后恢复原资源配置。此证据是合成Debug视口测量，不声称物理ARM/FPS/Release基准。

## 回归与首次失败保留

旧实现红测1/1失败：500历史管理没有ScrollToIndex，只是普通滚动Column。首轮新UI 24有7失败、旧StockCheck12有4失败；原VOIDED20全部通过。新失败来自详情关闭重开后未等待重测，以及对话框宿主Android资源仍默认英语（只改LocalContext不足以改变窗口资源）。测试设置宿主四语/2倍字号并After恢复配置，重开后等待UI空闲。旧提示已移到懒列表屏幕外；只补按稳定key滚动，再保留原“不可不安全作废”提示与所有断言，未删除/放松断言。修正后56项零失败/零跳过。

真实仓库的纠错/恢复/预测由原data/reminder/app全量和API35脚本继续验证；共同UI契约的作废/恢复使用合成快照，验证分组、数量及操作移除，不用它替代数据库一致性证明。B4共享预测/ADJUST/CONSUME/REVERSE/VOIDED规则与B2/B3行为没有修改。

## 交付边界与待验收

Room9、SupplyLedger及流水、VOIDED业务规则、PK、PNBAK1/加密、versionCode26、签名配置和交付账本均保持。已交付正式APK文件重新读SHA256仍 `0c6bd9132ab15ca7625f1da19b59c1c9450cf4cb0518b15100e30396c5ed3fa0`，未重建/重签/重发。未操作真实健康数据。

代码验收HEAD `589d19241da5f095849995d6461a5a1458c10e39` 完整push/PR均已通过，独立后续PR为 [PR24](https://github.com/DevenirTwilight/HRT-Log/pull/24)，指向B4分支；PR23保持8749207未改动。完整结果如下。最终验收提交另清理3条新增lint警告：共享测试Composable改首字母大写、固定20条文案去掉无必要的格式参数；四语可见文案字节和分页行为相同，不加功能。最后HEAD自身仍完整执行CI，以PR24固定正文中的最终完整SHA及四组链接为准，不借用旧绿色、不循环追加记录自身SHA。

## 代码HEAD完整CI与真实设备结果

| 事件 | Android JVM / Android / API35 | Science gate / deep |
|---|---|---|
| push | [38453937357](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38453937357)，三项success | [38453937324](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38453937324)，两项实际success |
| PR24 | [38453994837](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38453994837)，三项success | [38453994527](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38453994527)，两项实际success |

- 本地完整JVM194（domain58/PK124/importer12）零失败/零跳过，34项脚本全部通过。CI JVM同样194零失败，data109/reminder17/app787零失败；app保留61项既有条件跳过。新增UI24实际全部通过，原StockCheck12/VOIDED20、库存一致性/预测/纠错、Period5/DataRefresh6及导航等原回归全部保留。
- API35 x86_64：data15/15、app116项零失败/8个原条件跳过，新增包装历史24/24无跳过，原Unified浓度仪器6/6无跳过；B3日历设备回归继续通过。作废重启prepare/新PID verify随后分别`OK (1 test)`，加密备份/恢复/安全复原PASS；B2伪装重启、导入残留清理、密码恢复中强杀的新进程脚本全部PASS。不存在取消运行中CI或降低断言；B4-E每轮实际attempt1通过。
- 真实API35设备采集136条测量，最高3个挂载包装语义节点；所有语言1条首批挂载1个。20/21/100/500条首批：英语/简中/繁中均挂载2个，法语1个；500全部25批的最大值：英语2、简中3、繁中3、法语2。100普通包装与100 VOIDED末页四语均验证完毕。每次追加保留首项索引/像素偏移，详情返回保留位置，重开管理窗口默认历史0条。
- 代码HEAD lint0错误181警告，其中3条新告警（共享测试命名重复2条及固定数字的PluralsCandidate1条）在最终验收提交清理，不使用warning抑制或减少lint。Debug/unsigned Release/两仪器包、无INTERNET、来源身份与交付账本通过，Room结构无diff。未正式签名或交付。
- 三个PR24报告ZIP独立下载，SHA256与GitHub artifact digest一致：build `49e7c7430a6926eb0cf4586a48100040e6e01c1f4bfc7becb0708d59317bd54a`；device `ab1581e354b2f2261068e7bd376268fc7dbcc225d879cfb488da001f3fa7a39e`；JVM `277fec898915591ed77a6d5d15b3a6fcf532a4a56267459bd68818bc1167e98c`。具体计数来自实际HTML/XML和设备JSON，不只看绿色状态。机器摘要见 `25-b4e-validation-evidence.json`；每批完整JSON保存在对应device-test-results。

验收状态 READY_FOR_OWNER_REVIEW 生效条件：PR24最终HEAD自身全部必需检查完成通过。最终SHA/检查链接固定在PR24最终验收正文，报告不循环记录自己的提交SHA。真实物理ARM/真人操作未执行；测试是合成native Robolectric/真实API35模拟器，并非实机或Release性能基准。默认分支、PR23、build26、数据库/库存规则、签名和加密备份均未改动；不自动合并，不签名发包。
