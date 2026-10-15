# 55 — MED-02B5 / B3-BACKUP-GATE：完整加密备份与新命名空间恢复的设计记录（ADR）与待决政策

2026-10-15。状态：**TEST_ONLY 可执行候选 + 设计记录**；不是 B3 生产实现、不是已批准协议。B2/B3 生产仍 **NO-GO**，**NOT_REVIEWED_EXTERNALLY**。MED-02B5 是开发批次名，与附件路线 B2（正式加密导入）、B3（完整可信医疗备份）、B4（图片）、B5（PDF）、B6（发布验收）不同。

实现位置（只在 core/data 的独立测试 APK `net.plainnotes.app.data.test` 内）：`core/data/src/androidTest/java/net/plainnotes/app/data/b3/{B3Gate,B3GateAndroidTest,B3RestartAndroidTest}.kt`；脚本 `scripts/run_b3_gate_restart.py`、`scripts/run_b3_gate_red.py`；release 隔离标记 `B3Vault`、`HRTLOG-B3-TEST-ONLY`。设计依据：报告 34/35（备份草案与恢复状态机）、44/45（密码决定与风险）、50（孤儿/配额）、51（外部审查包）。实测结果见报告 56。

## 1. 与既有格式的边界（不变）

- `PNBAK1` 仍是**结构化记录备份**：Argon2id 32MiB/t3/p1、AES-256-GCM、结构化序列化 9；**不含**医疗原件、DEK、附件元数据。本批不改其 magic、参数、reader/writer，也不宣称它“升级为完整备份”。
- AppLock 的 Argon2id 19MiB/2/1 是另一个域，与任何备份 KDF 无关。
- Room runtime 10、29 个既有实体与 B1 八张附件表、`feature_enabled=0`、VERIFIED/VALIDATED 守卫均不动；没有 Room11 迁移。

## 2. 完整包覆盖范围（候选；实际实现只覆盖合成子集）

未来完整包必须覆盖：结构化医疗事实（MED01/Room10 stable UUID 与 typed ID）、Links、Inbox/日期精度/来源（**仅 ADR**，字段未来定义）、全部 CURRENT/Trash/保留版本的密文、可携密钥材料、缺失/损坏状态、最终清单与精确 EOF、整个集合的真实性、恢复 receipt。

本批实现的合成子集：dataset（epoch、logical crypto vault）、record（VISIT/LAB，LIVE/TRASHED/PURGED 墓碑）、文档版本（CURRENT/RETAINED/TRASHED、更正链 `corrects`）、每版本 DEK、link（LIVE/TRASHED/PURGED/QUARANTINED）。不覆盖：真实 Room10 表、Inbox、日期精度、来源字段、PK 估算——这些在正式 B3 才定义。

## 3. 身份层次（不可混用）

| 层 | 含义 | 恢复时 |
| --- | --- | --- |
| localDataset（SQL `dataset.uuid`） | 本机查询/CAS 的作用域 | 目标端**新建**，与源不同；源 id 只记录为 `source_dataset` |
| recordEpoch | 一次数据集生命周期；同整数 ID 跨 epoch 不同 | 目标端**新 epoch**，不复用源或旧 epoch |
| logicalCryptoVault | 对象 AAD 的密码学身份 | **保留**（否则必须重新加密，不能只改 header） |
| physical generation（`dataset.generation`，目录 `objects/<generation>/`） | 本机密文文件集合（报告 34 的 `generation_uuid`），不是内容 AAD | 目标端**新建**；恢复操作先记录在 journal，提交时成为新 dataset 的 generation；旧 dataset 的 generation 目录原样保留 |
| version / object UUID | 内容不可变版本与物理对象 | 保留；目标已有同 UUID → 拒绝（INVARIANT），不重绑不覆盖 |
| DEK handle | 每版本密钥句柄 | 保留句柄；包裹**按目标别名重做** |
| attempt / operation | 恢复操作与幂等键 | 目标端 journal；同 operation 重放返回同 receipt |
| 设备别名（Keystore） | 每命名空间独立 `<ns>.kek` | **源别名绝不当作凭据**；目标只用自己的别名 |

## 4. 候选包结构（TEST_ONLY，未冻结）

- bootstrap（固定 124B，明文）：magic `HRTLOG-B3-TEST-ONLY-NOT-PROD`(28) ‖ u16 protocol=1 ‖ u16 KDF profile ‖ salt16 ‖ archiveUUID16 ‖ nonce12 ‖ wrapped master(32+16)。
  - profile allowlist：`0x7E01 TEST_FAST`（Argon2id 1MiB/t1/p1，**仅测试成本，不是候选生产参数**），`0x7E02 TEST_HEAVY`（已知但故意超出预算，用来证明资源拒绝）。未知 profile 在任何 KDF 调用前拒绝。
  - master key 随机 32B，用 password-KEK AES-256-GCM 包裹，AAD=`B3-TEST-WRAP`‖bootstrapUnsigned（含 archiveUUID 与 salt）。
- body：Tink `AesGcmHkdfStreaming`（候选 A，64KiB 段），key=HKDF(master, salt=archiveUUID, info=`B3-TEST-BODY`)，AAD=SHA256(bootstrap)。截断/尾随字节/段重排由流式 AEAD 的末段标记与段序号检测。
- 帧（在认证流内）：`type u8 ‖ len u32 ‖ payload`，上限 2MiB/帧、4096 帧。META（源 dataset、epoch、vault）、RECORDS、VERSIONS、LINKS、DEK（句柄+原始 32B）、OBJECT（版本+对象 UUID+**原内层密文，不改 AAD**）、MANIFEST（最后一帧：archive、源 dataset、帧数、DEK 数、每对象密文 SHA-256、前面全部帧的 transcript SHA-256）。
- 严格语法：MANIFEST 之后任何帧 → TRAILING_AFTER_MANIFEST；无 MANIFEST → MANIFEST_MISSING；重复 → DUPLICATE_ENTRY；transcript/计数不符 → MANIFEST_MISMATCH。帧层认证不代替最终集合认证。

## 5. 导出：一致快照 + 备份租约

1. 在 SQLCipher **独占事务门**内读取 CURRENT dataset 的全部 rows 与 DEK（解包）——门内其他连接无法提交写入（实测：并发写入者在门内被挡住，门外才提交）。
2. 门释放后，快照引用的对象由**备份租约**钉住：GC 对被钉对象拒绝（`gcObject` 返回 false），导出结束才释放。对象不可变，读取时再核对快照内的密文 SHA-256。
3. 每帧边界检查 `AttachmentSession.Capability`（锁定/取消/空间切换撤销）；撤销后不再写帧并删除半成品；成功条件=写完 MANIFEST、流关闭、文件 fsync、目录 fsync。
4. 简化（如实标注）：rows 快照放在内存（合成数据很小），**没有**实现报告 35 的加密 SQLCipher spool；正式实现必须改为加密 spool 与容量预约。SAF/云 provider 的 close 语义未覆盖（本批只写本地文件）。

## 6. 恢复：最后一步才切换 SQL 权威

顺序：不变量（至多一个 CURRENT、authority 指向 CURRENT）→ bootstrap/magic/protocol/profile → 资源预算（profile 内存 ≤ maxMemory/4）→ 密码（GCM 解包）→ 配额预约（旧数据 + 新副本同时容纳，不先删旧）→ 流式解析，对象写入 operation 自有 staging 文件（CREATE_NEW+NOFOLLOW，先 journal 后写）→ 全对象认证（密文长度/SHA、DEK 存在且 32B、按**版本自身身份**的内层 AEAD 解密、明文 SHA）、图校验（每文档至多一个 CURRENT、更正目标存在、墓碑无内容、LIVE link 只指向 LIVE 记录且文档有 CURRENT、TRASHED/PURGED 状态一致）、目标端身份冲突检查 → staging rows 写入 SQLCipher 内的 stage 表（静态加密，普通查询不可见），DEK 用目标别名以 operation 作用域 AAD 重新包裹 → file + 目录 fsync → 发布：移到本次恢复新建的 generation 目录下的最终对象名（目录必须是新建的；对象名存在即拒绝，因为 rename 会静默覆盖），目录 fsync，阶段 SYNCED → **单个 SQL 事务**：CAS（authority.current 与 revision 等于开始时读到的值）→ 旧 dataset `RETIRED`（`retired_by`=operation）、其 LIVE link → `QUARANTINED` → 新 dataset `CURRENT`（新 localDataset、新 epoch、保留 vault）→ 从 stage 表导入 rows → DEK 改用最终 dataset 作用域重新包裹 → authority 指向新 dataset、revision+1 → operation `COMMITTED` → 清理 stage 与 staging（失败则 `COMMITTED_CLEANUP_PENDING`，由 reconcile 完成）。

任何提交前拒绝：`abort` 只删除本 operation journal 中**未被任何版本引用**的文件、清 stage 行、释放预约（ABORTED）；外来文件永不触碰。进程死亡后由 `reconcile` 做同样的事；已 COMMITTED 的只补完清理。重放同一 operation：已提交则返回同一 receipt（不新建 dataset、不重复计配额）；archive 不同则 ARCHIVE_MISMATCH。

## 7. 旧数据集与 ACTIVE 指针

恢复后只有一个 CURRENT。旧 dataset 转为 `RETIRED`（可追溯：`retired_by`），其行、版本、DEK 包裹与密文**全部保留**作为安全回退，LIVE link 改为 `QUARANTINED`，绝不重新关联到新 dataset 的同整数 ID 记录。旧 dataset 何时可清理是负责人决定项（§9 P3）。

## 8. 证据分级定义

`TEST_ONLY_REAL_IO`：真实 SQLCipher + 真实 Keystore + 真实文件 IO，在 emulator 的独立测试包内。`FAULT_INJECTION_ONLY`：seam 抛异常模拟失败点。`MODEL_ONLY`：无。`NOT_RUN`：真实 ARM、两台物理设备、真实 ENOSPC、掉电、正式签名旧版本升级、PSS/耗时大规模、外部专家。**一台 emulator 上的两个命名空间（各有别名）不是两台物理手机互相恢复。**

## 9. 待负责人决定的政策（OWNER_DECISION_REQUIRED，均未在生产实现）

当前已有证据的最保守基线（Gate2/Gate3/本批测试协调器）：不按文件年龄自动删除；只在明确确认后释放可证明属于自己的孤儿；外来路径不删；active 不可释放；重复释放不重复计配额。没有正式 UI 或业务政策批准。

**P1 孤儿保留与清理**
- 选项 A（推荐，当前行为）：永不自动删；用户在已解锁 PRIMARY 的存储管理里看到“未完成的导入/恢复残留”，逐项确认清理；只清自有对象。
- 选项 B：自有、未提交、非 active 的孤儿在 N 天后自动清理（N 待定），清理前在应用内提示一次。风险：唯一副本误判时不可逆。
- 选项 C：存储压力下自动清理自有孤儿。风险最高，不推荐；存储压力不得静默触发删除唯一原件。

**P2 谁能确认清理**：A（推荐）仅 PRIMARY 已解锁用户在应用内确认；B 再要求重新输入 PIN/生物识别。伪装外壳、通知、DECOY 永不出现医疗字样或清理入口。

**P3 旧数据集（RETIRED）保留**
- 选项 A（推荐）：保留到下一次成功完整备份 + 用户明确确认后才可清理；在此之前占用双份空间。
- 选项 B：恢复成功并重启复核（receipt COMPLETE）后保留 N 天再提示清理。
- 选项 C：恢复后立即释放旧数据。风险：失去回退；不推荐。

**P4 所有权不足的预约配额（外来路径）**
- 选项 A（当前测试协调器，报告 50）：物理文件永不删除；经审计与明确确认后只释放本 operation 自己的 SQL 预约记账（RELEASED_FOREIGN），并保留“该路径有外来文件”的记录。
- 选项 B（更保守）：外来路径的预约永不释放，隔离并提示用户。代价：配额被永久占用。
- 推荐 A，但需负责人确认“无可信证据表明本 operation 仍需占用”的判断标准。

**P5 临时存储预算**
- 选项 A（推荐）：恢复前要求 旧数据 + 完整新副本 + staging 同时可容纳；不足则拒绝（本批实现即此：QUOTA）。
- 选项 B：允许“先删旧再恢复”。不推荐：恢复失败将同时失去两份。
- 候选数值（16MiB/对象、256MiB/包、1000 对象、512MiB 临时额度）**未验收**，不得当作生产常量；需真实 ARM 低内存与 PSS 测量后由负责人冻结。

四语提示文案草稿（不进入应用，等待批准）：
- en：“An unfinished import or restore left encrypted files on this phone. They are not part of your records. Review and delete them?”
- zh-CN：“上一次未完成的导入或恢复在本机留下了加密文件，它们不属于你的记录。要查看并删除吗？”
- zh-TW：「上一次未完成的匯入或還原在本機留下了加密檔案，它們不屬於你的紀錄。要查看並刪除嗎？」
- fr-FR : « Une importation ou une restauration inachevée a laissé des fichiers chiffrés sur ce téléphone. Ils ne font pas partie de vos données. Les examiner et les supprimer ? »

## 10. B6：设备与资源矩阵（计划；无设备即 NOT_RUN）

| 项 | 方法 | 状态 |
| --- | --- | --- |
| API26/29/35 x86_64 emulator | CI，本批 B3 测试与两段强停 | 见报告 56 |
| 真实 ARM64 低内存（≤3GB RAM，API26/29/35 各一） | 独占测试设备、独立测试包，不碰用户数据 | NOT_RUN（无设备） |
| 两台物理设备互相恢复 | 源机导出 → 目标机恢复 → 删除源别名后重开 | NOT_RUN |
| 真实 ENOSPC | 受控独立分区/配额，不写满 runner 或用户设备 | NOT_RUN |
| 可控掉电 | 硬件台架 | NOT_RUN |
| 正式签名旧版本升级 | 需负责人提供授权测试设备，不使用私人 APK 作为 CI fixture | NOT_RUN |
| 100/1000 对象、最大容量版本的耗时与峰值 PSS | 合成数据，`dumpsys meminfo` 采样 | NOT_RUN（本批只有 4 个对象） |

PR #32 同 HEAD `704c542` 的 API29 OOM：首轮 PR job 进程实际堆上限 16MiB（启动参数 `qemu.dalvik.vm.heapsize=512m` 不代表进程实际堆），5 项既有 Argon2id/备份测试 OOM；唯一一次重跑成功；同 SHA push 成功。这是**已解决的合并阻断**，不是已解决的低内存设备风险。本批 `runtimeResourceEvidence` 在每个 API 记录 `Runtime.maxMemory()`、`dalvik.vm.heapgrowthlimit`、`dalvik.vm.heapsize`、`qemu.dalvik.vm.heapsize`、ABI。不修改既有备份工作因子、不删除或跳过那 5 个测试、不给生产包加 largeHeap、不无限重试。真正低内存设备上既有 PNBAK1（32MiB）备份的可用性仍需在真实 ARM 上调查。

## 11. 医疗档案 V2 不变量（持续有效）

Inbox 与“未分类”独立于“未关联”；采样/临床/签发/导入日期各有精度与时区；医院/机构是用户陈述，不冒充签章；一份报告可显式链接多条 Lab/Visit，哈希/日期不能自动推断临床事件；内容不可变版本与纠错元数据分离；给医生的精选包默认不含全库或 PK 研究估算；完整 V1 包含**图片及多页 PDF**；B3 可信完整恢复必须先于保管唯一原件的正式启用。OCR、全文检索、FHIR/医院互通、云同步、诊断/剂量建议均不在本批范围。
