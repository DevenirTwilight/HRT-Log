# B2 隐私与数据安全实施验收（2026-10-12）

分支 `feature/b2-privacy-reliability-20261012`；merge-base/最新默认分支 `1e584285cb1e6f7a4b09a3e20585418d35a28735`。PR19 的报告20是已合并历史终态，不由本报告覆盖。S0 复核四项缺口仍存在；原 SQLite parser 关闭句柄并物化数据，允许结束读取即删除，不扩大 scope。

| 批次 | 状态 | 提交/CI |
|---|---|---|
| A | COMPLETE | `ed5682e7a5fd6cc0540e42ac15f4dddca23210f9`；[38291254654](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38291254654) jvm/android/device-tests 全 success；[38291254617](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38291254617) reference success |
| B | WAITING_CI | 本批独立提交后登记完整SHA及运行；不得沿用A绿色 |
| C | 尚未开始 | 前批 CI 全绿后推进 |
| D | 尚未开始 | 前批 CI 全绿后推进 |

## A：最短生命周期

旧代码在预览或无效版本失败后留下 transmemo.db；新增两条真实 ViewModel→SQLite 回归在旧代码均因“副本应在预览发布前删除”断言失败（环境配置问题纠正后才计入红灯）。修复集中于 `privacy/TransMemoImportCopies.kt`，唯一名称+互斥+finally；VM 取消旧读取并取消旧状态发布，Application 解锁安全启动清理。原 SAF 原件不改；数据库/导入算法不改。副本仅在复制/SQLite 读取期间短暂明文，硬杀期间不能执行 finally，下次安全启动及新导入前重试清理。拒绝符号链接目录，仅匹配普通文件，不递归，保留无关文件。

新增测试 `TransMemoImportCopiesTest` 10 项：关闭/sidecar、复制/close/解析/空输入失败、协程取消、并发唯一名称、仅自身文件/链接安全、锁定目录不访问、目录不可用。`TransMemoImportFlowTest` 4 项：预览后仍能实际导入9条合成记录、格式错误、快速重新打开/取消、切换 HRT Tracker。聚焦14项零失败/跳过。两个新 instrumentation 方法主套件条件跳过，但由 `run_import_cleanup_restart.py` 分别在强制停止前后执行，必须断言各 `OK (1 test)`，尚待本批 CI 真正执行。

本地保护复核：78 个文件 SHA-256 不变；历史164/164从Git对象复现、5/5独立研究结果逐字节相同。完整研究/数值/兼容/导出/锁/伪装回归与 lint/构建由本批完整测试和 CI 核实，不提前填绿。

无 APK 上传/签名/交付、版本号/账本更改、新权限/网络、Schema/备份协议/PK 变更。测试仅合成数据；日志不输出原件内容或用户文件名。真实 ARM、真实云最终持久化、真 TalkBack、OEM 差异未验证。精确 revert SHA、CI URL/数量将在该提交验收后追加。

A 负向验证：正常14项先通过；故意禁用 finally 删除后，实际预览回归1/1因相同明文残留断言失败（非环境/超时），已恢复源码，后续全量再次验证。科研守卫302、旧PK审计13均通过；机器摘要见 `21-b2-a-test-evidence.json`。


A 最终验收：本地 JVM domain58/PK124/importer12、data72/reminder14/app524（61既有条件跳过）、PeriodStability5/5、lint0错误174警告、full Debug/unsigned Release/两套 instrumentation 编译全部成功；历史164/164+结果5/5、科研302/旧PK13。CI 报告 ZIP 下载并与官方 SHA-256 核对：build `f6582cc30bd61f26026ca8a8539a897d7c52a8794cb21e264a854fdef13ee28d`，device `bc4d6bc0cf93f89a2706a3a19abd6667e69c34c7a783dceab9ef4cae9a88cd61`；无 APK。CI app524/data72/reminder14零失败；API35 data14/app73（4重启方法条件跳过后，旧伪装2项+新清理2项独立各1通过）。CI unsigned Release 来源为该 SHA/git/clean、无 INTERNET；本地同样核验。不是物理ARM。回滚：`git revert ed5682e7a5fd6cc0540e42ac15f4dddca23210f9`，只回滚开发改动，不删除账本或研究。


## B：导出前告知

检查全部 CreateDocument/明文健康导出：只有 Data CSV ZIP、报告 PDF、Visit Pack PDF、纵向摘要 PDF 四类；其余 CreateDocument 是加密备份。四个真实入口在旧实现各因缺少提示节点失败（4/4）；CSV 原来已直接打开 SAF。现在 CSV 一次确认，三个PDF在现有对话框顶部告知且正文可滚动。四语说明应用不加密此类文件，目标保护依赖权限和提供方；不宣称所有应用可读，不把 .pnbak 混为明文。不改导出回调、命名、实际文件字节生成、图表加载顺序或 D-2 渲染器。

`PlainExportEntryTest` 5项：实际四入口在保存器前显示提示，各可取消，保留MIME/名称规则、Visit目的地正确传递；加密备份行保持原说明。`ExportNoticeLayoutTest` 四语×320/411dp×font1/1.3/2×浅深共48组 **实际执行，无跳过**：640dp短屏完整可访问文字、实际行宽/完整字符/无省略、48dp动作和取消/继续。加上两项翻译守卫聚焦55项通过。矩阵覆盖复用的CSV告知对话框；其余三个完整选项对话框仅默认英语411dp行为检查，未冒称全部入口全矩阵。真实 TalkBack/物理ARM/云最终落盘/OEM差异未验证；未制作虚假的对话框截图。

严查时 `hasVisualOverflow` 在所有标题上报警；诊断样本 Export CSV：Text框362px，段落约束816px，但实际行宽361.7578px、10/10字符、无省略。段落约束标志不是字符截断，改为直接逐行几何/字符/省略断言，**实际行宽必须≤框宽，不增加容忍阈值**。新备份密码对话框的 Robolectric native/legacy 均 AppNotIdle（60s），未计作业务变异或覆盖通过；该完整密码流程断言保留到 `PlainExportPrivacyAndroidTest`，由真实API35执行。debug宿主 `DataPrivacyTestActivity` 仅合成内存Schema9，保留ViewModel跨重建，非导出且FLAG_SECURE；Release无此宿主。

仅 `app/build.gradle.kts` 增加既有Room/sqlite的 debugCompileOnly 编译类型，不新增运行包库或版本变动。初始额外基准78文件中77逐字节不变；这一普通构建文件按精确两行编译依赖差异核对，原版本/签名/生产配置不动。真实55实时研究锁、15显示文件、schema/core-data/备份协议/ledger均保持。历史164/164、独立结果5/5再次通过，科研302/旧PK13通过，原研究守卫不改。

负向：55项合法源码先通过；故意不显示 ExportPrivacyNotice，CSV实际入口1/1因找不到告知节点失败，非环境/超时。已恢复代码，完整回归/CI仍需本提交验收。逐批提交/运行与精确revert待本批绿色后登记；无默认合并、正式签名、交付或上传APK。
