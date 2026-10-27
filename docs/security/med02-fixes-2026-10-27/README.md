# MED-02 有限安全修复

基线：默认分支 `03f724f41e91a1a866d98de31de673594d1105af`，其 #131 受审 HEAD 为 `9fafe3c27d563c32f5638029ff084db3e1b59d7f`。单一安全修复分支 `security/med02-f01-f04-20261027`。唯一PR [#132](https://github.com/DevenirTwilight/HRT-Log/pull/132)，本记录封版时为Draft。最终SHA及合并状态以该PR和最终交付回执为准。

| 范围 | 实现 | 本地回归 | 实际Android（747ed6e2） | 最终HEAD CI（封版时） |
|---|---|---|---|---|
| F01 | backup-only保持锁定，认证不创建key；恢复失败保留凭据 | PASS | PASS API26/27：真实PID死亡、恢复、损坏密文与缺key不重建 | NOT_RUN，封版后执行 |
| F02 | dispatch前中性UUID检查点，UNKNOWN/Checking不建新操作 | PASS | PASS API26/27/29/35：真实COMMIT→force-stop→新PID只读核对，opens不增加 | NOT_RUN，封版后执行 |
| F03 | 撤权所有权+有界off-main close；公开FileChannel响应cap撤权 | PASS | PASS API26/27/29/35：远端release前worker退出、根事务可写、无成功返回 | NOT_RUN，封版后执行 |
| F04 | 保留POTENTIAL；有限管道样本未复现主线程close阻塞 | 原宿主证据保留 | NOT_REPRODUCED（该主线程假设）；输入worker退出断言PASS | NOT_RUN，封版后执行 |

`evidence/original-audit-evidence.zip` 是审计原件（SHA256 `73d549175a9672cf348e7bd91e4500cd32fb25fc3a73867cd25d083d8b670b87`）；不会用新探针覆盖旧失败。`evidence/pr131-merged.json` 更正旧报告工程状态，记录原HEAD的合并及所列CI成功。不能据此宣称旧LMK根因已修复。

新增的原生验证只用模拟器、合成命名空间和测试APK中的provider；F01生成真实合成PIN凭据，在真实Android8 AtomicFile备份rename后杀目标PID，fresh PID检查恢复、错误输入及损坏密文不换key。该故障点通过测试File子类施加于平台primitive，未往生产AppLock放置杀进程hook。F02先真实gateway导入/COMMIT并保留未ack检查点，force-stop后真实controller仅核对原操作，统计provider opens无增加。F03真实SQLCipher集合导出写入不消费的provider pipe，撤权须在远端release前结束worker、释放根事务且无成功返回。F04另用不产出数据的读pipe观察本地关闭是否阻塞主线程；原始观测、时间和退出结果均保留。

这些探针不能证明任意OEM/kernel/provider都可中断。物理设备、硬件断电、人工TalkBack、GPU/IME/recents取证及独立外部审查均NOT_RUN。F04即使在上述设备未复现也只限该样本，不升级为普遍安全保证。

始终 `feature_enabled=0`、`PUBLIC_ENTRY_ENABLED=false`、`NOT_REVIEWED_EXTERNALLY`、`RELEASE_NOT_AUTHORIZED`。不改格式/参数/schema/配额/密钥协议，不签正式包、不交付APK、不启用真实医疗附件。本轮为AI内部修复与验证，不是独立第三方安全认证。

## 首轮记录（pre-final2470ca2d）

F01修复提交0f7c88a2，F02 e358b0a8，F03 cdfe354f。本地18项针对性回归PASS，0失败/错误/跳过；app/core AndroidTest源码编译PASS；原始XML在evidence/local-targeted，环境连接/JDK/CA初始失败和后续日志全部保留。

真实API26/27 push作业40461145091和PR作业40461203361均FAIL，不改写绿。API26 F01实际死亡及fresh PID恢复PASS（原始f01-verify.log在2470-original-api26.zip）；F02尚未建立真实COMMIT；F03/F04的DocumentsProvider未初始化，故相关漏洞动态判定NOT_RUN，不是漏洞被排除。API27因既有脚本拒绝27而NOT_RUN。已保留原始artifact12715230402 ZIP与失败日志。

最小测试接线修正：按Android8 DocumentsProvider.attachInfo要求在测试APK声明exported/grantUriPermissions/MANAGE_DOCUMENTS；不改生产组件权限。F02使用既有可访问合成只读ContentProvider并统计opens，未伪造系统SAF grant；原真实DocumentsUI回归保留。加入27白名单和匹配Robolectric离线jar；192MiB ART预算、数据规模和失败断言保持。新-source原生结果待运行，F04仍POTENTIAL。

## 第二轮真实管道证据（55c62f3e）

API26/27的F01及F02进程死亡/恢复、实际COMMIT和只读核对均PASS，见55c-original-api26.zip及55c-original-api27.zip。F03仍FAIL：撤权1/2ms返回、关闭不在主线程、没有成功回执，但worker在provider释放前未退出。F04主线程close阻塞假设未复现（均1/2ms及时返回），同时证实读worker也不能依靠关闭PFD退出；整体保留POTENTIAL。完整原始失败和两份ZIP已保留。

基于这些实际证据，新增有限SAF descriptor适配：单次open、O_NONBLOCK、50ms poll、既有cap检查；worker持有dup防关闭时FD编号复用；30s无进度拒绝，可靠pipe EOF仍检查provider错误。只调整传输，不重写加密/事务/格式，不放宽原worker退出/根事务断言。regular/FUSE或open本身不响应时仍不承诺通用终止。输入和输出都走相同实际边界，避免只测一个与生产不同的实验流。

另保留UI隔离守卫原始失败：中性检查点存储放在ui包触犯原守卫；已移到application attachments层，守卫不改。后续相关JVM回归和AndroidTest编译PASS；既有4个Robolectric dialog跳过没有删改。新IO源码的原生/最终CI尚未PASS，不以55c低内存绿色替代。

## 原生针对性通过与最终封版

真实push运行 [40464182728](https://github.com/DevenirTwilight/HRT-Log/actions/runs/40464182728) 的HEAD严格为6eb350ba78bac67a8e383f9ac91f8fb085e9e264，两个API作业均SUCCESS；PR运行40464190169也SUCCESS，但以前者实际branch SHA为针对性证据。原始ZIP分别6eb-native-api26.zip、6eb-native-api27.zip，含全部PID/COMMIT核对日志和provider观测；未用合成COMMIT替代真实提交。

| API | F03撤权耗时 | 输出关闭在main | 远端release前worker退出 | 成功返回 | F04撤权耗时/读worker退出 |
|---|---|---|---|---|---|
| 26 | 1ms | false | true | false | 0ms / true |
| 27 | 2ms | false | true | false | 1ms / true |

F03 testcase还真实验证根事务在5秒内重新取得、全部业务事实字节不变及LEGACY authority不变。F04较早失败已证明原始PFD close不打断读worker；该额外可用性问题通过同一SAF边界修正。不要将此说成已证明GPU/IME/ProxyFD/FUSE/任意provider无风险。

生产修复提交：F01 0f7c88a2；F02 e358b0a8（中性存储移至application层见22524070）；F03 cdfe354f 的所有权修复和22524070 的真实管道修正。其余提交保留证据/测试接线，不占第二PR。补充F01 TEST-OWNED alias失效后不得重建key/替换原凭据的负向断言，等待最终CI的API26/27执行；不操作真实密钥。

**本记录先于最终HEAD CI封版。** 该文档提交之后的完整CI结果以[#132 checks](https://github.com/DevenirTwilight/HRT-Log/pull/132/checks)及最终交付回执记录的精确SHA、run ID和merge SHA为准；不得拿6eb针对性结果冒充后续文档提交的最终CI。只有全部必需/相关原生/低内存成功且无冲突和未解决评审时才正常保护合并；Draft/失败时禁止合并。不再为记录CI绿而反复产生新源码SHA。

## 精确 HEAD db6053b0 失败与公开 API 修正 — 2026-10-27

- 最终候选 db6053b0 的 API29 兼容性作业 FAIL：`Os.fcntlInt` 在该平台抛 `NoSuchMethodError`；SDK API 表确认该符号自 API30 才公开。API26/27 专用安全回归虽 PASS（含缺 key 不重建），不能替代 API29。API26 AutomaticImport 超时 FAIL，根因尚未证明，保留原件，不放宽断言。
- 精确分支 lowmem 40466469228：library PASS，Featherline FAIL，内核记录前台进程 LMK；分配根因 NOT_PROVEN，不将后续绿色改称已修复。原 ZIP、API29/低内存失败日志存 evidence/db605-*。
- 撤销非公开 fcntl 路径，改用公开 FileChannel 的 interruptible close；清理仍有界且 off-main，正常 IO 等待关闭失败。输入持有并清零固定 64KiB direct buffer，避免隐式临时缓存残留；不改密码学、格式、事务或配额。阻塞 open/FUSE/OEM 的可中断性仍未证明，不保证通用截止时间。
- 本地针对性 AppLock/controller/store/UI guard/output-close 回归及 app/core 仪器源码编译 PASS。新的真实管道验证扩展 API26/27/29/35，当前 NOT_RUN；先验证再冻结精确 SHA 完整 CI。仍唯一 Draft #132，不合并失败候选。OFF / NOT_REVIEWED_EXTERNALLY。


## 四 API 真实针对性通过，最终源码冻结

[push 40469859893](https://github.com/DevenirTwilight/HRT-Log/actions/runs/40469859893) 的受测 SHA `747ed6e2a7f133101b2b2edd5f5aaeae7c008ae3`，API26/27/29/35 四作业全部 PASS。原 ZIP 为 evidence/747-native-api*.zip，原 run 元数据亦已保存。

| API | F01实际死亡/恢复/缺key不重建 | F02实际COMMIT重启核对 | F03撤权ms/worker退出/close不在main/失败回执 | F04撤权ms/worker退出 |
|---|---|---|---|---|
|26|PASS|PASS|2 / PASS / PASS / PASS|1 / PASS|
|27|PASS|PASS|1 / PASS / PASS / PASS|1 / PASS|
|29|NOT_RUN（本需求限定26/27）|PASS|1 / PASS / PASS / PASS|23 / PASS|
|35|NOT_RUN（本需求限定26/27）|PASS|1 / PASS / PASS / PASS|8 / PASS|

F03每项仍验证根事务及时释放、全部业务事实相等及LEGACY authority不变。F04主线程阻塞假设在这些样本中 NOT_REPRODUCED，整体仍 POTENTIAL。F03最终生产传输修正见 dbac92c3，取代22524070中在API29失败的fcntl实现。最终补充 channel/缓冲区分配失败时关闭刚打开的stream，防止低内存初始化异常遗漏资源；非线程架构重构，不改失败条件。

本地发布隔离/设计/配置70项 PASS；最终补充后再次运行相关JVM回归与AndroidTest编译。此后精确SHA的完整CI、四API专用回归及低内存作业均须执行并在PR最终回执区分 PASS/FAIL/NOT_RUN。先前db605低内存 FAIL不会被覆盖，也不因后续绿而宣称LMK根因修复。合格前保持Draft；未合格不得合并。

本地补充 lint 与最终针对性并发运行引起资源压力，已主动终止；未完成部分标 NOT_RUN，日志 final-local-aborted.log / local-lint-aborted.log 留存。只调整本地调度串行复测，不改变 CI 预算或任何断言。最终完整 lint 由精确 SHA CI 执行。

最终初始化失败清理后的本地串行复测 PASS：21项JVM（0 fail/error/skip），app/core AndroidTest编译 PASS，日志 final-local-serial-pass.log / final-local-targeted XML保留。此前资源压力中止部分仍NOT_RUN。下一步固定最终SHA完整CI，不以本地代替原生/低内存验证。

## MED-02 #132 全套CI发现擦除遗漏，有限补正 — 2026-10-27

- 6d8273b1四API专用原生push40472207972全部PASS，原件在最终交付证据；本地21项PASS。但完整Android 40472208031 FAIL：2486项、1 fail、561 skip，唯一失败是原有LocalDataWipeCoverageTest，指出F02新存储attachment-reconciliation-v1未接入删除全部数据。原始HTML/作业日志保留在evidence/6d-*；不是放宽守卫。
- 最小补正：生产wipeAll真实清除该存储并检查commit与空状态；与retain共用锁、擦除epoch使旧owner不能在wipe后写回中性ID。新回归验证所有scope清空、旧owner拒绝及新owner正常。算法/格式/schema/配额/开关/预算不变。先相关针对性回归再冻结新精确HEAD完整CI，当前NOT_RUN。
- 6d实际branch低内存40472224042：Featherline PASS，library FAIL；13项base+heap及3项集合编解码/认证/破坏拒绝PASS，完整业务集成PID3959 adj0被kernel OOM killer杀死（anon RSS130356KiB），后续重启阶段NOT_RUN。PR预览40472213786 library PASS、Featherline FAIL，前台PID3892 adj0被LMK杀死（222100KiB）。两份原ZIP保留，分配根因NOT_PROVEN。不能互相替代或宣称后续绿修复根因；禁止为绿调整RAM/heap/数据/断言或无根据反复重试。
- 唯一Draft #132维持阻断，不合并、不签名/发布/启用。新HEAD仍需完整必需/四API/低内存结果；最终CI回执和未验证范围写PR及交付包，避免为记录结果无限改变SHA。OFF / NOT_REVIEWED_EXTERNALLY。

擦除补正后针对性回归 PASS：24项JVM（0 fail/error/skip），app/core AndroidTest编译PASS，原XML和日志见wipe-targeted-*；70项静态约束PASS。下一步推本次新HEAD，运行完整精确SHA CI、四API安全原生和分支HEAD低内存两项。6d低内存FAIL仍未修复根因；保持Draft直到全部条件满足，否则禁止合并。

## MED-02 #132 API27专用配置接线修正 — 2026-10-27

- 6c6eee90生产代码在四API专用原生40476432331全部PASS，本地24项及仪器编译PASS。完整PR Android主构建/单元/lint/隔离步骤通过后，235前身的233 Python守卫发现通用性能矩阵应默认拒绝27（原test_ansm_native_performance，AttributeError暴露错误接线）；原日志保留6c-pr-android-full-failed.log.gz。
- 修正仅脚本/workflow/新回归：通用configure继续只允许26/29/35；27必须由专用MED-02安全工作流显式--security-api27。原性能矩阵守卫不改；设备身份/模拟器/root/192MiB检查不改，新测试验证opt-in不能绕过。全235 Python测试PASS（security-api27-all-python-pass.log）；生产AppLock/UNKNOWN/擦除/IO代码不再改变。下一精确SHA完整CI待执行。
- 6c实际branch lowmem40476452481两个作业PASS（含后续完整重启）；PR预览40476438280 Featherline PASS/library FAIL：base13、集合编解码4、业务集成2、generation四阶段和attachment operation四阶段重启均PASS；集合PREPARED准备60秒内未到seam，脚本finally force-stop，不是已证明OOM，根因NOT_PROVEN，集合恢复/后续阶段NOT_RUN。原ZIP/全日志保留；不增时限/预算/减数据，不为绿反复重试。旧6d/db605实际OOM/LMK原件仍保留。
- 仍唯一Draft #132、禁止合并失败候选；推接线修正后的精确SHA运行完整必需+四API+低内存，结果通过全部条件后才正常保护merge，否则维持阻断。最终PR回执/证据包记录精确结果；OFF / NOT_REVIEWED_EXTERNALLY。
