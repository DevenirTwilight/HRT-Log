# MED-02 有限安全修复

基线：默认分支 `03f724f41e91a1a866d98de31de673594d1105af`，其 #131 受审 HEAD 为 `9fafe3c27d563c32f5638029ff084db3e1b59d7f`。单一安全修复分支 `security/med02-f01-f04-20261027`。PR/最终 SHA 和真实结果将在取得后记录；当前不得称已验证或已合并。

| 范围 | 实现 | 针对性本地 | API26/27实际Android | 最终SHA CI |
|---|---|---|---|---|
| F01 | backup-only仍需PIN；认证不创建缺失key，恢复失败保留凭据 | PENDING | NOT_RUN | NOT_RUN |
| F02 | dispatch前持久化scope/typed target隔离的中性UUID核对身份；UNKNOWN/Checking禁止新操作 | PENDING | NOT_RUN | NOT_RUN |
| F03 | PNMEDBK2输出由capability拥有；仅调度有界close，worker等待实际关闭结果后才能成功 | PENDING | NOT_RUN | NOT_RUN |
| F04 | 独立DocumentsProvider进程的真实阻塞管道观察，暂保留POTENTIAL | 原宿主证据保留 | NOT_RUN | NOT_RUN |

`evidence/original-audit-evidence.zip` 是审计原件（SHA256 `73d549175a9672cf348e7bd91e4500cd32fb25fc3a73867cd25d083d8b670b87`）；不会用新探针覆盖旧失败。`evidence/pr131-merged.json` 更正旧报告工程状态，记录原HEAD的合并及所列CI成功。不能据此宣称旧LMK根因已修复。

新增的原生验证只用模拟器、合成命名空间和测试APK中的provider；F01生成真实合成PIN凭据，在真实Android8 AtomicFile备份rename后杀目标PID，fresh PID检查恢复、错误输入及损坏密文不换key。该故障点通过测试File子类施加于平台primitive，未往生产AppLock放置杀进程hook。F02先真实gateway导入/COMMIT并保留未ack检查点，force-stop后真实controller仅核对原操作，统计provider opens无增加。F03真实SQLCipher集合导出写入不消费的provider pipe，撤权须在远端release前结束worker、释放根事务且无成功返回。F04另用不产出数据的读pipe观察本地关闭是否阻塞主线程；原始观测、时间和退出结果均保留。

这些探针不能证明任意OEM/kernel/provider都可中断。物理设备、硬件断电、人工TalkBack、GPU/IME/recents取证及独立外部审查均NOT_RUN。F04即使在上述设备未复现也只限该样本，不升级为普遍安全保证。

始终 `feature_enabled=0`、`PUBLIC_ENTRY_ENABLED=false`、`NOT_REVIEWED_EXTERNALLY`、`RELEASE_NOT_AUTHORIZED`。不改格式/参数/schema/配额/密钥协议，不签正式包、不交付APK、不启用真实医疗附件。本轮为AI内部修复与验证，不是独立第三方安全认证。
