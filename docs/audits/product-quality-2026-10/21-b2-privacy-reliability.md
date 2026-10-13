# B2 隐私与数据安全实施验收（2026-10-12）

分支 `feature/b2-privacy-reliability-20261012`；merge-base/最新默认分支 `1e584285cb1e6f7a4b09a3e20585418d35a28735`。PR19 的报告20是已合并历史终态，不由本报告覆盖。S0 复核四项缺口仍存在；原 SQLite parser 关闭句柄并物化数据，允许结束读取即删除，不扩大 scope。

| 批次 | 状态 | 提交/CI |
|---|---|---|
| A | COMPLETE | `ed5682e7a5fd6cc0540e42ac15f4dddca23210f9`；[38291254654](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38291254654) jvm/android/device-tests 全 success；[38291254617](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38291254617) reference success |
| B | COMPLETE | 主提交 `348064c251d7a21338ae7637b4d5394f6e4122da` + 修复 `5f839e98eb085eaab92c415a434d00fcc956e8ba`；[38301348940](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38301348940) 三任务全success，[38301348930](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38301348930) reference success |
| C | COMPLETE | `8e97e7a620ba0a22559c8d7fb7c4d01a7e744280`；[38362778379](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38362778379) 三任务全success；[38362778161](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38362778161) reference success |
| D | WAITING_CI | C对应四项CI全绿后实施；本批提交/CI待登记 |

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


B 首次提交 `348064c251d7a21338ae7637b4d5394f6e4122da` 的 [38298443443](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38298443443)：jvm/device-tests success，但android FAIL，原因仅为 debug 合成宿主直接 `ViewModelStore.put` 触发 RestrictedApi lint（1错误174警告），不是数据/研究/布局回归。独立 [38298443463](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38298443463) reference success。真实API35密码对话框已通过；首批B不能因此标COMPLETE。改为公开 ViewModelProvider 注册，保留生命周期与所有断言，不加lint忽略、不改门槛；修复提交须重新完整CI。

本地首次全量因cgroup8GiB触发OOM kill（事件计数1）中断，不计业务失败或变异；清理旧Kotlin守护进程/孤立测试worker，CLI改为进程内编译、单worker重跑，577项app（61既有条件跳过）、data72/reminder14全部零失败，随后同一 RestrictedApi lint 错误被捕获。此处保留失败与环境边界，不用旧绿色代替修复提交验收。

B 公开API修复的本地聚焦55项、lint（0错误174警告）及instrumentation完整编译/打包已通过；没有关闭RestrictedApi检查。完整source/CI仍以随后修复提交为准。


B 最终绿色仅对应 `5f839e98eb085eaab92c415a434d00fcc956e8ba`：jvm/android/device-tests/reference 全success，原失败记录保留。CI下载 ZIP 的官方 SHA-256核对：build `6b1955cd26b2e56b8a4622b8e9f5cb2c10c35c2f03d3231b89e5e91fcbf2fb5b`，device `4ce72bccff1c2c429ca133d71b718eb905c8b56332b9e1b1fbe7805caaf9f1c0`。app577（61既有条件跳过）/data72/reminder14零失败；API35 data14/app74（4重启方法主套件跳过后各1独立通过），真实备份密码流程1/1通过。lint0错误174警告、full Debug/unsigned Release/instrumentation、无INTERNET及来源/ledger通过。B回滚须逆序 `git revert 5f839e98eb085eaab92c415a434d00fcc956e8ba`、`git revert 348064c251d7a21338ae7637b4d5394f6e4122da`；后续依赖批次先回滚，不改数据/账本。

## C：加密备份完成时间和页面提醒（验收中）

主空间与伪装入口的加密导出共用 `BackupWriter`：先加密并清除提交的密码字符数组，再打开用户选定的 SAF 输出流，写入并正常关闭后才提交 UTC 操作时间；失败与取消使用旧元数据快照回滚。`BackupHistory` 只存操作时间和默认关闭的提醒配置，不存 URI、密码或用户文件名；清除数据递增代次，使旧导出无法在清除后补写成功记录。恢复路径未写此时间。主空间只在页面前台展示本地时间、7/14/30 天提醒和“记录时间不证明文件存在、同步或可恢复”的说明；无定时器、通知、后台任务。主空间及伪装入口现在先选 SAF，再索取密码；取消选择不询问密码。

改动前 `BackupExportFlowTest` 两条真实 ViewModel→内存 Room→加密输出回归因缺少完成时间失败。改动后聚焦 `BackupHistoryTest` 16、`BackupExportFlowTest` 3、`BackupStatusLayoutTest` 48、入口6、B布局48、翻译2，共123项零失败/跳过。48布局组合为四语×320/411dp×字号1/1.3/2×浅深，实际验证整行可点开关、完整时间/免责声明及短屏滚动；真实 TalkBack/OEM差异仍待实测。Android新增合成 SAF 结果测试，验证取消、真实加密写入/清除、输入密码后重建；待本批设备CI。Android编译和lint已通过，完整回归、错误成功标记负向变异、对应CI尚未结束，不能填绿。

C 负向变异：先在合法源码下聚焦123项通过；故意把记录成功时间移到打开输出流和正常关闭之前，目标测试1/1因 `No success timestamp before normal close` 断言失败，旧时间2026-01-01与错误新时间2026-01-02被准确区分，并非超时/环境缺件。已恢复原源码，恢复后测试及本批完整CI尚待收尾。

C 全量本地回归首次发现 A 的切换入口竞态：旧 Trans Memo 读取作业收到取消后，HT Tracker 读取可先发布预览，而旧临时副本仍在 `finally` 清理中；`switchingToHrtTrackerCancelsTheTransMemoRead` 真实断言失败。C 的 ViewModel 改动中补充顺序屏障：新读取先取消并 `join` 旧读取（含其 finally），再开始新入口读取。原断言未放宽；聚焦及完整重跑结果待补。该补强不改读取/导入交易算法。

C 本地第二轮全量：JVM194、data72/reminder14/app645（61既有条件跳过）零失败，切换入口4项聚焦与全量均通过，PeriodStability5/5保留。恢复后错误成功标记目标1/1通过。lint/构建收尾，设备和unsignedRelease完整验收仍以该提交CI为准；机器摘要见 `21-b2-c-test-evidence.json`。

C 最终验收：完整SHA如表，四项CI均全success。官方ZIP SHA-256核对 build `0111377e00b984d5e3b233071d7057f574a7246b2d4507524b0a9c6a74402abf`、device `21712eecc8c132163b9d4a138d2502aaa2feacc9bc1ce941bced4d68f8766255`，未下载APK。CI app645/data72/reminder14零失败，app61既有条件跳过；API35 app77/data14零失败，4重启方法主套件跳过后分别独立验证，新增C3项通过。本地完整Debug及instrumentation打包通过；CI完整unsignedRelease/来源/无INTERNET/ledger通过。lint0错误179警告。C回滚为 `git revert 8e97e7a620ba0a22559c8d7fb7c4d01a7e744280`，先回滚后续D；不删除数据/研究/账本。D不改RawData.restore或事务。

## D：恢复前保护选择（验收中）

用户取得恢复来源后进入三选择页面。可先导出当前加密数据，再回到同一来源的选择页；可明确继续到原替换警告与密码，也可取消。`RestoreProtection` 仅维护当前界面的阶段，无副作用/密码/持久化，备份结果不能授权恢复；直接相同 URI 在取得输出后、任何写入前拒绝。迟到保存器回调及重建从 Idle 开始，不继续旧意图。SAF 不同 URI 指向同一底层文件的提供方别名无法靠 URI 相等可靠识别，未假称覆盖全部云提供方。

先补9条状态测试和48组四语/宽度/字体/主题短屏布局后实施，聚焦含翻译59项通过；布局实际验证三按钮48dp、完整字符、实际字行几何和滚动。实际入口取消备份回归、API35真实加密文件/数据回归、重建和外部强杀测试正在验证。强杀测试须先在真实密码字段输入后写无秘密的PID检查点，由外部脚本终止该进程，再以不同PID的新进程断言无待恢复状态/密码、未自动写入数据、原加密来源完整；不把被主动终止的prepare阶段谎称完成测试。

RawData.restore/事务/Schema/核心数据及冻结协议未修改。原恢复失败/兼容、锁/伪装/通知及科研/数值门槛照常全跑；物理ARM、真实云最终持久化、真TalkBack/OEM差异仍未验证。最终CI/测试数/回滚待本批验收后登记。

D 取消后自动恢复负向：正常生产DataSection及真实ActivityResultRegistry回归1/1先通过，Android测试完整编译通过。故意在保护备份保存器返回取消时调用 `model.restoreBackup`；同一入口测试1/1因 `Cancelled backup picker must never start a restore operation` 断言失败，非环境/超时。已恢复源码，恢复后同测与完整回归正在执行；详细机器摘要待本批最终CI。

D 变异恢复后目标1/1通过；完整Android测试源码编译成功。完整本地JVM/Android和本批CI仍在验收中，当前不填COMPLETE。
