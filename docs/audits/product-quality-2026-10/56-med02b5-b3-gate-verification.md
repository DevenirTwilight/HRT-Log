# 56 — MED-02B5 / B3-BACKUP-GATE 验证记录（PR #36）

2026-10-15。只记录 PR-B（#36）；PR-A（#35）见报告 54。基线：默认分支 `claude/new-session-1959qb` @ `104cdab`（#35 合并；合并后 Android [38715560071](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38715560071) 五 job、Science [38715559977](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38715559977) 全部成功）。PR #34 仍 OPEN，等待负责人独立处理，本 PR 不含其内容。

结论：**B3-BACKUP-GATE RESEARCH_GO**（仅隔离研究/测试验收）；**生产 B3 NOT_IMPLEMENTED**；**正式 B2 NO-GO**；**NOT_REVIEWED_EXTERNALLY**。

## 证据 HEAD 与 CI

证据 HEAD `bee25b6`（代码最终提交）。本节写入后形成仅文档的最终 HEAD，合并以其必需检查全部成功为准。

| 运行 | 结论 |
| --- | --- |
| [Android PR 38721589784](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38721589784) | jvm、android、device-tests（API35）、attachment-poc-compatibility 26/29 全部 success |
| [Android push 38721583749](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38721583749) | 同上五项 success |
| Science PR 38721589776 / push 38721583744 | science-change-gate success；research-deep PR success、push 按条件 skipped |

## 真实执行（原始日志与报告 XML/HTML）

| 测试/脚本 | 层级 | API26 | API29 | API35 |
| --- | --- | --- | --- | --- |
| `B3GateAndroidTest`（18 方法） | REAL_ANDROID_SQLCIPHER + REAL_CIPHER_IO + TEST_ONLY + SIMULATED_NEW_DEVICE；seam = FAULT_INJECTION_ONLY | 18/18 | 18/18 | 18/18 |
| `B3RestartAndroidTest`（1） | 普通运行（进程内模拟死亡） | 1/1 | 1/1 | 1/1 |
| `run_b3_gate_restart.py BEFORE_COMMIT` / `AFTER_COMMIT` | REAL_FORCE_STOP + fresh PID（不是掉电） | PASS/PASS | PASS/PASS | PASS/PASS |
| `run_b3_gate_red.py`（manifest、CAS 两项红对照） | 红→绿 | PASS | PASS | PASS |
| core/data 设备测试合计 | — | 70 | 70 | 137 |

单元与静态（android job）：app 893（61 既有 skip，与基线相同）、reminder 19、data 180，0 失败；lint、full Debug、unsigned Release、release 隔离检查（新增禁止标记 `B3Vault`、`HRTLOG-B3-TEST-ONLY`）PASS。本 PR 不新增 skip。

### 关键证据行（三个 API 一致，PID 省略）

```
B3_GATE test=e2e versions=4 links=6 bundleBytes=196111 sourceAliasExists=false result=RESTORED_INTO_FRESH_NAMESPACE
B3_GATE test=keyDomains result=DESTINATION_ONLY
B3_GATE test=frameTamper case=final_manifest_missing rejected=MANIFEST_MISSING
B3_GATE test=frameTamper case=frame_after_the_manifest rejected=TRAILING_AFTER_MANIFEST
B3_GATE test=frameTamper case=duplicated_object_frame rejected=DUPLICATE_ENTRY
B3_GATE test=frameTamper case=reordered_objects rejected=MANIFEST_MISMATCH
B3_GATE test=frameTamper case=object_dropped_after_the_manifest rejected=MANIFEST_MISMATCH
B3_GATE test=frameTamper case=retained_version's_object_missing rejected=OBJECT_MISSING
B3_GATE test=frameTamper case=trashed_version's_object_missing rejected=OBJECT_MISSING
B3_GATE test=frameTamper case=objects_of_two_versions_swapped rejected=OBJECT_HASH
B3_GATE test=frameTamper case=swapped_objects_with_consistent_hashes_(wrong_version_identity) rejected=OBJECT_AUTH
B3_GATE test=frameTamper case=embedded_object_ciphertext_with_a_broken_tag_(hashes_made_consistent) rejected=OBJECT_AUTH
B3_GATE test=frameTamper case=portable_key_missing rejected=DEK_MISSING
B3_GATE test=frameTamper case=portable_key_truncated rejected=DEK_MALFORMED
B3_GATE test=frameTamper case=live_link_to_a_purged_record rejected=GRAPH_INVALID
B3_GATE test=frameTamper case=trashed_link_silently_made_live rejected=GRAPH_INVALID
B3_GATE test=frameTamper case=two_CURRENT_versions_of_one_document rejected=GRAPH_INVALID
B3_GATE test=frameTamper case=purged_tombstone_with_content rejected=GRAPH_INVALID
B3_GATE test=byteTamper cases=14 kdfCallsForRefusedProfiles=0
B3_GATE test=concurrentRestore loser=CAS_STALE winner=COMMITTED
B3_GATE test=orphan seam=AFTER_STAGE orphans=4 reconcile=ABORTED
B3_GATE test=orphan seam=BEFORE_COMMIT orphans=8 reconcile=ABORTED
B3_GATE test=snapshot writerHeldOffMs=300 gcRefusedUnderLease=true result=CONSISTENT
B3_GATE test=manifestRed red=true outcome=ACCEPTED        ← 红对照，预期失败
B3_GATE test=manifestRed red=false outcome=MANIFEST_MISMATCH
B3_GATE test=casRed red=true outcome=COMMITTED_OVER_STALE ← 红对照，预期失败
B3_GATE test=casRed red=false outcome=CAS_STALE
B3_GATE test=restart seam=BEFORE_COMMIT phaseFound=SYNCED   （新 PID：reconcile→ABORTED，旧 CURRENT 不变，同包再次恢复成功）
B3_GATE test=restart seam=AFTER_COMMIT phaseFound=COMMITTED （新 PID：reconcile→COMPLETE，全部对象经目标别名解密）
```

每个用例另外断言：目标端 authority/revision、dataset 状态、CURRENT 的 rows/versions/links、对象文件集合、stage 行与预约全部不变（或按预期变化），外来文件不被触碰，目标目录内不存在明文标记。字节级 14 项含：错密码、bootstrap 截断、header 后截断、末字节截断、段边界截断、尾随字节、正文翻转、尾部损坏（正确前缀）、salt/archive 翻转、magic、protocol、未知 KDF profile 与超预算 profile（两项均在零次 KDF 调用时拒绝）。其余：配额差 1 字节拒绝/恰好够则成功；提交事务内失败回滚；ACK 丢失后同 operation 重放得到同一 receipt、不重复 dataset/配额，换包 ARCHIVE_MISMATCH；清理失败报告 pending 后 reconcile 完成；两个 CURRENT 在 staging 前拒绝；两恢复并发恰好一个提交；植入目录的合法密文不被激活；导出取消不留半包、恢复取消不改目标；独立连接与重开必须用真实密钥读到同一数据。

### 资源证据（B6）

`B3_GATE test=runtime`：API26 maxMemory 384MiB（heapsize 384m，heapgrowthlimit 未设）；API29 512MiB（heapsize 512m）；API35 192MiB（heapgrowthlimit 192m，heapsize 576m）；ABI x86_64(+x86)。这只是本次 CI emulator 的值：PR #32 同 HEAD `704c542` 的 API29 首轮实际堆上限为 16MiB（配置 `qemu.dalvik.vm.heapsize=512m` 并不代表进程实际堆），5 项既有 Argon2id/备份测试 OOM，唯一一次重跑与同 SHA push 成功——该环境不一致和低端设备兼容风险**仍未解决**，真实低内存 ARM NOT_RUN。本 PR 不改 PNBAK1 工作因子、不加 largeHeap、不跳过测试。

## CI 过程记录（保留失败证据）

1. `2019925`（首次推送）：三个 API 均只有 `exportSnapshotIsConsistent…` 失败（第二连接 `SQLITE_NOTADB`）；其余 B3 测试通过；脚本因 Gradle 先失败未运行。
2. `d4d62b3`：假设为独占门阻塞打开，改为门前建立连接——仍失败，证明假设错误。
3. `bee25b6`：根因是测试夹具在打开后立即清零交给 SQLCipher 的密钥数组，连接池启用 WAL 时重新读取该数组，库文件实际以全零密钥加密（报告 55 §6a）。改为密钥与库同寿命、关闭时清零，并加回归测试 `anIndependentConnectionOpensTheVaultWithItsRealKey`。全部通过。生产 `DatabaseAccess` 本来就在关闭时才清零，不受影响。

## 阅读索引（本轮实际打开）

| 路径 | 继承的不变量 / 与本轮关系 |
| --- | --- |
| 34-med02b0-room10-migration-draft.md | store_uuid / logical_vault_uuid / generation_uuid 三命名空间 → 本批 dataset/vault/generation |
| 35-med02b0-medical-backup-draft.md | 包结构、恢复状态机、SQL 最后切换、旧 PNBAK1 分离 → B3Gate 实现顺序 |
| 36-med02b0-product-policy-and-pr-plan.md | 16MiB/256MiB/1000 为候选非标准、双份空间 → 报告 55 §9 P5 |
| 39–42（V2 契约、Tink 决策、导入失败合同、验证） | PHR V2 不变量、Tink 1.23 候选 A、完整 EOF → 外层流/内层对象 |
| 44、45、47 | canonical context、预算、风险登记、作者以外 AI 复核≠外部审查 |
| 50、51 | 孤儿/配额矩阵、外部审查主包 → 报告 55 §9、报告 57 |
| 53、54 | 隐私声明与 PRIVACY-P1 → 本批未改隐私文本 |
| HANDOFF/STATUS/REQUIREMENTS 顶部章节 | 当前状态与 §94/§95 授权 |
| `Gate2Store.kt`、`Gate2EmulatorGuard.java`、`B2StreamingCandidate.kt`、`AttachmentSession.kt`、`DatabaseAccess.kt` | 复用 emulator 守卫、Tink 流、capability；密钥生命周期对照 |

SOURCE_NOT_READ（本轮未重新打开全文，沿用此前会话已入库结论）：`docs/PLAN.md`、`docs/BACKLOG.md`、`docs/audits/product-quality-2026-10/sources/med02b2/` 原文、`docs/med-eu-privacy-readiness-v2.md` 全文、Room10 八张附件表的实体源码、`DataTransfer.kt`/`RecordBackupCompatibility.kt` 全文（本批不触及生产备份）。

## 未运行 / 局限

NOT_RUN：真实 ARM64 与低内存设备、两台物理设备互相恢复（本批为 SIMULATED_NEW_DEVICE）、真实 ENOSPC、硬件掉电、正式签名旧版本升级、100/1000 对象与满容量的耗时/峰值 PSS、恶意 PDF/native worker、正式 Tink/R8 增量、外部人类专家审查。简化（如实标注）：导出快照 rows 在内存而非加密 spool；单写入者假设下“存在即拒绝”的发布检查；合成图只覆盖报告 55 §2 的子集。
