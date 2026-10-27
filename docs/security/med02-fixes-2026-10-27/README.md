# MED-02 有限安全修复

基线：默认分支 `03f724f41e91a1a866d98de31de673594d1105af`，其 #131 受审 HEAD 为 `9fafe3c27d563c32f5638029ff084db3e1b59d7f`。单一安全修复分支 `security/med02-f01-f04-20261027`。唯一PR [#132](https://github.com/DevenirTwilight/HRT-Log/pull/132)，本记录封版时为Draft。最终SHA及合并状态以该PR和最终交付回执为准。

| 范围 | 实现 | 本地回归 | API26/27实际Android（6eb350ba） | 最终HEAD CI（封版时） |
|---|---|---|---|---|
| F01 | backup-only保持锁定，认证不创建key；恢复失败保留凭据 | PASS | PASS：真实PID死亡、恢复及损坏密文不换key；缺key补充断言随最终CI执行 | NOT_RUN，封版后执行 |
| F02 | dispatch前中性UUID检查点，UNKNOWN/Checking不建新操作 | PASS | PASS：真实COMMIT→force-stop→新PID只读核对，provider opens不增加 | NOT_RUN，封版后执行 |
| F03 | 撤权所有权+有界off-main close；SAF非阻塞IO响应cap撤权 | PASS | PASS：远端release前worker退出、根事务可写、无成功返回 | NOT_RUN，封版后执行 |
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
