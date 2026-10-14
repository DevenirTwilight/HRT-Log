# HRT Log — MED-02B2 下一阶段执行提示词（接续 MED-02B1，吸收 PHR V2 与欧盟适配修订）

> **用途**：把本文件直接交给 Claude Code / Codex，让开发代理在仓库中开展下一阶段工作。  
> **版本**：2026-10-14，依据当日最新 GitHub 记录与《HRT Log｜医疗档案与附件系统竞品研究及升级方案 V2》。  
> **执行级别**：**允许研究、审计、补充设计、添加隔离的合成验证/失败先行测试、形成可审查 PR；并非授权把未经独立评审的新密码协议投入生产、向真实用户开放附件、自动合并、正式签名或交付 APK。**  
> **仓库**：https://github.com/DevenirTwilight/HRT-Log  
> **原始依赖**：默认分支 → PR #27 MED-02A → PR #28 MED-02B0 → PR #29 MED-02B1 → **本阶段 MED-02B2**。  
> **本阶段建议名称**：**MED-02B2 / B2-GATE：PHR V2 产品契约与安全加密导入生产准入**。如果未来批准 B2 生产实现，另建 B2-IMPLEMENTATION 小 PR，不能把本阶段设计结论等同生产批准。

---

## 0. 角色、目标与优先级

你是一名拥有 Android/Kotlin、Jetpack Compose、Room/SQLCipher、Android Keystore、移动设备安全、密码学工程、离线 PHR（Personal Health Record）产品设计、测试和隐私审查经验的高级工程师。请**实际读取最新仓库、设计文档、PR 和 CI**，不要仅复述旧计划。

当前任务：**在 MED-02B1 通过验收的基础上继续推进 MED-02B2**，使医疗档案后续 B2（安全原件导入）、B3（完整医疗备份与跨设备恢复）、B4（图片与资料库）、B5（PDF 与选择性导出）、B6（正式产品验收）有明确且彼此兼容的实施依据。要解决真实用户问题，而不只是再研究一次加密算法。

新版产品定位：HRT Log 是**离线优先、患者本人管理的长期 HRT 与医疗档案 PHR**，不是普通加密文件夹，也不是医院 EHR 的拷贝。必须形成完整的三条链路：

1. **存得住**：医疗 PDF/图片原件、用户记录、来源、显式关联、版本、纠错、密钥和完整备份一致保存；中断/损坏不能导致旧数据消失。
2. **找得到**：待整理收件箱、日期/机构/分类/标签/关联的检索与筛选，能从化验找到原报告、从报告找到用户确认的结构化记录；不自动猜测归属。
3. **用得上**：就诊前明确选择少量数据和原件形成资料包；导出范围可以检查；不无意分享整个医疗历史。

本阶段必须**向生产可实施方案前进**，但高风险能力未满足条件时必须遵守止步线，不能以“任务完成”为由跳过安全审查。若发现重大矛盾，可建议修改原计划，但需给出证据、替代方案、兼容性影响和下一次用户批准项。

---

## 1. 立即刷新真实 GitHub 状态；旧 SHA 仅用于定位

### 1.1 本提示词编写时已核实的基线

- 默认分支名称：`claude/new-session-1959qb`，原转移包中主线 HEAD 为 `c3bf2cbce6663167814aa48a0e8e9e1300adb2e4`。以任务开始时实际 `git rev-parse` / GitHub 为准。
- [PR #27](https://github.com/DevenirTwilight/HRT-Log/pull/27)：MED-02A 合成 POC；HEAD `1c8d38553b30734565ac6c44751f0bb1dc19c9a7`；核查时 OPEN。
- [PR #28](https://github.com/DevenirTwilight/HRT-Log/pull/28)：MED-02B0 安全审查和协议草案；HEAD `977e07796cdcec831ebd6d3142e0b80daafa2ea1`；核查时 OPEN，base 为 #27。
- [PR #29](https://github.com/DevenirTwilight/HRT-Log/pull/29)：MED-02B1 Room9→10 附件基础；最终文档 HEAD `772f7ecba354dc4d5c36399db53a4f19eb7bc98f`，实施代码基准 `f04c3fe11d8e5021f2404064ff01fc25f55a1831`。**PR 正文最终记录显示 push/PR Android、JVM、lint、API26/29/35 与 Science 检查全部通过**，一项 push API35 首次是 SDK 下载网络故障，经仅重试失败 job 的 attempt 2 成功；仍 OPEN，base 为 #28。
- #29 已实现：默认关闭的 Room10 additive 八表、typed stable identity / tombstone、localDataset 与 crypto logicalVault 与 physicalGeneration 分域、recordEpoch、session capability 与同步提交线性化门、key-role 接口、旧结构化 `PNBAK1` 序列化版本9、真实 Room/SQLCipher 升级、合成强停和既有业务回归。
- #29 **尚未提供**正式医疗文件加密导入、生产文件密文、完整医疗备份、已接线的生产 AppLock 原件读取、正式 PDF/图片附件 UI。真实 ARM / 硬件掉电 / 外部独立安全认证未完成。
- 已向用户私下交付的正式 APK 曾为 `0.2.0 / build26`、Room9；**仓库默认分支、未合并 #29 与用户手机实际安装包不是同一个状态**。

### 1.2 必须操作

1. 运行 `git status --short`, `git remote -v`, `git branch -vv`, `git rev-parse HEAD`，刷新 #27/#28/#29 的最新 HEAD/base/state/mergeability/CI；记录 UTC 与本地日期时间。若仅通过 connector 不能查看 job-level checks，明确标记“未核验”，不要用 PR 文案取代实际 CI。
2. 若 #29 已合并、被替换、存在重大冲突或后续 PR 已覆盖 B2，暂停旧分支假设，先形成**状态差异报告**，调整最小安全实施路径，不在过时 HEAD 上重复实现。
3. 若仍为 stacked PR：从 **#29 的最新 HEAD** 建立独立 `feature/med02b2-v2-import-gate` 工作分支；对应新 PR 的 base 设为 **#29 当前分支**，不要直接对默认分支提交隐藏的三层依赖，也不要重写 #27/#28/#29 历史或强推父分支。
4. 冻结参考树/commit SHA/dirty-worktree 检查结果。任何本地存在的真实健康记录、密钥或用户应用数据一律不接触；所有验证都使用独立命名的合成库/夹具。
5. 代码与文档发生冲突时，用具体路径、行号、测试和最新提交识别差异；不要把 `docs/HANDOFF.md` 里过期的历史“待执行”直接搬到新任务。

### 1.3 必读材料（以最新版内容为准）

- `docs/STATUS.md`、`docs/REQUIREMENTS.md`、`docs/HANDOFF.md`、`docs/BACKLOG.md`、`docs/licensing.md`；
- `docs/design/medical-records-roadmap.md`；
- `docs/audits/product-quality-2026-10/29-med01b-encrypted-attachments-design.md`；
- 报告 **30、31、32、33、34、35、36、37、38**，尤其 33 的候选加密协议、34 的 Room 10 身份草案、35 的完整备份设计、36 的 B1–B6 门槛、38 的 B1 代码验收；
- `core/data/src/main/java/net/plainnotes/app/data/Attachment*.kt`、`RecordBackupCompatibility.kt`、`DataTransfer.kt`、`DatabaseAccess.kt`、`Migrations.kt`、`SchemaGuards.kt`、`Trash.kt`；
- `core/domain/src/main/kotlin/net/plainnotes/app/domain/AttachmentSession.kt`；
- PR #27 的 `tools/attachment-poc/`、Android 合成 PDF 与图片及 provider 测试；PR #28 的 `tools/attachment-design-validation/`、安全交叉审查原稿；PR #29 的 Room/SQLCipher 原生仪器测试；
- 用户提供的 **《HRT Log｜医疗档案与附件系统竞品研究及升级方案 V2》（2026-10-14）** 和 **《HRT Log 欧盟适配评估：全部修改意见》及其后续修订讨论**。如文件不在仓库或本地，明确缺少来源：不得声称完整读过，也不得仅凭标题编造内容；可利用本提示词固定的 V2 决策起草候选 ADR。

---

## 2. 必须完成的第一项交付：MED-02 产品 V2 与 B2 安全协议的需求契约

新增独立的 **MED-V2 Requirements / ADR**，把对照竞品得到的功能目标转成以后每批可测试的契约。不要现在创建大量空 UI、空分类数据库、无权限假按钮，也不要擅自改变 B1 既有 8 表。

### 2.1 将功能分到实际阶段，不能因更换规划而挤进 B2

| 功能 | 计划阶段 | 当前必须决定/准备的事项 |
|---|---|---|
| 安全流式原件导入、完整认证、故障回滚 | **B2** | 输入/存储/密钥接口、operation journal、取消/撤权/失败状态合同 |
| 完整医疗加密备份、所有版本、跨设备新 Keystore 恢复 | **B3** | B2 格式必须满足 B3 可携带性、固定身份与密钥封装约束 |
| Inbox、分类、双/多日期、来源、标签、元数据搜索 | **B4** | 先定语义和字段计划，评估是否须独立 additive migration，不改 B1 成品 schema |
| 图片安全预览、显式文件↔就诊/化验链接、纠错、Trash | **B4** | 定义只含已认证文件的可见状态；早期功能开关保持关闭 |
| PDF 原件和多页预览、原件级选择性导出 | **B5** | B2 需要 seekable 受控随机读取，不以明文 cacheDir 作为后备 |
| V1 正式医疗资料库可用验收 | **B6** | 不只图片；完整 V1 以**图片+PDF**及备份、关联、检索、真实设备稳定性为目标 |
| LabPanel/同次采样、报告↔多个 LabValue | **C1** | 先定义来源/显式确认/快照不变，不自动猜测同一天等于同份标本 |
| 按就诊选取的医生资料包 | **C2** | 后续基于现有 VisitPack 扩展，清单预览、敏感数据最小披露 |
| 离线 OCR、FHIR/CDA/医院互通、全文检索 | **C3/待评估** | 暂不引入模型/网络/新临床事实，FHIR 先只借鉴语义 |

注意：**PDF 重要性上调，不意味着本批必须上生产 PDF viewer；B2 需保证其所需的随机访问/撤权能力，B5 再实现。** 不能为了早点展示 PDF 而跳过 B3 完整备份。

### 2.2 必须写清这些用户行为（不得只写架构类名）

- **导入**：从 Android SAF 选 PDF/JPEG/PNG/WebP；未知长度、提供者说谎、取消、重复、旧 AppLock 会话失效时的表现。B2 只创建可信、不可见的密文基础；不允许正式界面显示未备份的用户文件。
- **待整理 Inbox**：能先安全保存、后分类；title / type / source / clinical date / authored date / imported time 清晰区别，缺失日期可未知；不得强制先创立就诊或化验实体。
- **找原件**：标签、机构、已录入标题、日期、文件类型、显式关联的筛选；首期只是加密数据库中的**元数据搜索**，不说 PDF 全文搜索。
- **关联**：同一 PDF 可关联多个 LabValue；同一次就诊可关联多个文件；同一天不同机构不自动合并；同文件 hash 不自动等同同一检查；只由用户确定强临床关系。
- **纠错**：错误标题/分类/关联可撤回；真正的文件替换创建新的不可变版本；Trash 恢复保留原 stable ID；PERMANENT DELETE 要解释旧备份不受控制。
- **查看原件**：安全图片解码、PDF 有界随机读取、原件损坏/密码 PDF 不支持时明确错误，不能空白装正常。技术 UUID、hash、算法无需在常规 UI 上抢占注意力。
- **选择性披露**：用户选择文件/实际用药/化验/Visit 记录，显示共享清单；外部 SAF provider 可能是云端，应用撤销不了对方已经保存的副本；不能称此为远程过期权限。

产出可以是 UX 流程、状态图、字段/迁移影响表、四语关键文案、无障碍用例；**本批不实现这些未来产品页面**。

---

## 3. B2 最大技术决策：正式对象协议选型与独立安全准入

原 `SYNPOC01` 只属于 POC；`DRAFT-ONLY-OBJ0!` 是研究转录，不是正式格式。你不能直接把 magic 改个名称就投入生产。要求进行针对真实 Android 项目的**决策级对照与可复现验证**：

### 3.1 对比至少两种候选

**方案 A：成熟的、维护中的 Streaming AEAD 实现**（例如 Tink；必须实际核对 Android/JVM 最低 API、seekable/random-access、完整 EOF/final tag、取消/reader 撤权、许可证、发布体积、维护与迁移能力，不因知名就假定满足）与 **方案 B：仓库报告 33 的 JCA + HKDF-SHA256 + AES-GCM 有界分块 + 完整结束清单组合草案**。

对每项逐条评分或用 PASS/FAIL/UNKNOWN（不得编造性能分数）：

- 现有 Android API 26/29/35 支持；release 体积/构建可复现性；维护状态与依赖许可；
- 常量内存流式写；未知长度 SAF InputStream；多个实际文件大小；严控单原件/总体密文大小；
- **先认证完整对象和严格 EOF，再给予 reader/FD 可见性**；截断/尾随/重排/复制/错域与跨版本拒绝；
- seekable 的 PDF `openProxyFileDescriptor` 按偏移读取，以及打开后单块认证与授权重新核验；
- nonce/DEK 生命周期、所有 AES-GCM GHASH 与次数预算、上下文 AAD、header/footer 域分离与 byte transcript；
- Android Keystore 设备角色、未来跨设备便携封装角色、未知/缺失旧 key fail-closed；
- DB CAS、snapshot、版本替换、回滚/旧完整 tuple 重放边界；
- 容量提升时是否需要改变格式/nonce/备份 frame/恢复双份存储；
- 能否引入标准的完整认证以及生产测试向量，不做自创加密原语。

若方案 A 不支持可靠随机读取或完整文件结束认证，不可强行套用；若方案 B 没有独立安全审查，不可把 POC 通知为生产安全认证。允许提出第三选型，须同样用可复现证据比较。

### 3.2 硬性安全不变量

1. **每一个不可变版本和失败重试都使用新的随机 DEK、独立 nonce 域与操作身份**；不得以同一个 key/nonce 加密不同明文。每条密钥包裹也需独立角色及限额。
2. DB 内的 localDataset UUID（运行时 metadata scope）、crypto logicalVault UUID（密文 AAD 身份）、physicalGeneration UUID（磁盘代际）、recordEpoch 与 sessionEpoch 的语义始终不同。跨设备恢复可以迁移 metadata identity，但不可以随意重写旧 ciphertext AAD 或让同一密文身份指向其他内容。
3. PRIMARY 与伪装笔记：完全不同的密钥、目录、索引和活动能力；**伪装空间不可显示 PRIMARY 的附件存在、数目、标题、缩略图或错误消息**。保持应用不申请 INTERNET。
4. 所有文件必须位于应用受控的私有目录，原路径/SAF URI 不能成为长期唯一真相；不保存明文完整文件到 `cacheDir`/`filesDir`、外部存储、媒体库，也不让日志记录用户敏感 metadata。
5. 文件对象**不可原位修改**；内容版本完整认证成功后，短同步 gate 下核验会话/epoch 和 SQL `expected CURRENT` CAS 才能把对象标记成业务可见。先获得 gate 的提交可完成，先获得 gate 的撤权使之后提交失败。
6. 已打开的 reader/ProxyFD、图片解码任务、待发布 UI 缓冲、工作队列、备份输出、子 reader 都必须遵守 owner close、锁定/切空间的撤权语义；取消旧异步任务不能仍将解密结果交给新会话。
7. 硬件掉电和 DB/文件系统不是同一个事务：使用 operation-owned 标识、`O_CREAT|O_EXCL|O_NOFOLLOW` no-replace、file + directory fsync（按平台实际可行性）、operation journal、DB commit 作权威可见性点、重启 reconcile；**不能**把 fsync 成功称为抗硬件掉电证明，也不能重试时删除旧 active 对象。
8. 任何未经完整认证/缺少 footer 的对象都不可进入 READY/VERIFIED；有完整密文 orphan 也不能靠目录扫描自动变成有效医疗文件。
9. 完整备份的最终目标必须包含**结构化资料 + 关联 + 加密原件及所有保留版本/回收站 + 便携密钥材料**。旧 `PNBAK1` 只备份旧结构化记录；Room10 运行时版本不等于旧备份序列化版本9。
10. 历史 Lab、DoseRecord、SupplyLedger、VOIDED、MED01 Appointment、VisitPack、PK 冻结数据都是事实源。不能拿文件名/时间/hash 自动解释医学关系，不允许改写实际服药和原始化验事实。

### 3.3 对候选安全参数保持“候选”身份

原提案 16MiB/文件、256MiB/空间、1000版本、完整替换时额外旧+新双份恢复容量都不是已批准的成品指标。不得私自把 16MiB 提高到64/128MiB，也不得因为大 PDF 常见就默许无界输入。

按至少 **0/1 字节、块边界、1MiB、16MiB、16MiB+1、未知长度、MIME 冲突、压缩图片元数据、假 PDF / 损坏 PDF、20/100/1000 对象元数据**构造合成测试与上限参数推导。16MiB+1 的预期结果是在当前试验候选配置下**明确拒绝**，不是暗中忽略/截断。提升容量需另外验证 nonce/GHASH/backup frame/存储余量/低端 ARM 后形成单独产品批准项。

---

## 4. 本次可以实施的工程工作（不用碰生产安全红线）

这部分是本次**可直接执行的代码任务**；不能只写一堆“未来建议”而无测试与交付。

### 4.1 建立隔离的 B2 实验/合成验证目标

- 在既有 `tools/attachment-poc` / `tools/attachment-design-validation` 体系内，新增**明确为 NON_PRODUCTION / TEST_ONLY** 的协议评估、输入流、密文发布和事务一致性测试；如使用新依赖，限制在 test/POC 依赖，不自动增加正式 `app` 的运行时代码或改变 R8/Release。
- 基于真实 Room10 已有 `AttachmentSession`、`AttachmentDao`、`AttachmentTransaction`、operations/key-role interfaces，形成一份精确“将来 B2 如何接入”的代码映射，标注每个接口是已验收还是仅 TODO。可在测试源中实现驱动/模拟 adapter，**不得借测试 adapter 使用户真实文件能在生产中导入**。
- 失败先行测试覆盖：流式 import 在 block/write/fsync/publish/cancel/ENOSPC/provider EOF 错误时旧版本不丢；Session revoke before commit、during write、after complete but before publish、owner close while child reader active；文件系统对象拥有者、链接/孤儿的 reconcile 分类；并发同一版本不可覆写；同 crypto UUID byte commitments 冲突拒绝。
- 恢复模型覆盖：记录备份/附件新包身份的映射、旧 `PNBAK1` restore 后 quarantine、不同 integer ID 与旧记录断联、所有 Stage 对象/版本/链接的必要图验证；**不要本阶段写完整医疗备份生产 writer**。
- 保护 release main 源码：POC 不得被正式 APK DEX 或 release dependency 收录。新增自动校验（静态或受控 DEX 核验），至少能发现误把合成密钥、POC magic、test-only provider 注册进正式应用。

### 4.2 产出可执行 B2 API 契约（可以是 test source 的接口+stub）

需覆盖（以实际命名对应最新仓库，不应平行重造 B1 已有能力）：

- `ImportRequest`: 输入 SAF URI 仅临时存活、会话 capability、candidate MIME、user-selected metadata，而不是持久保存原 URI；
- `ImportOperation`: 稳定 operation UUID、attempt id、reservation/quota、state transition、关键持久化 seam；
- `AuthenticatedObject`: 经验证的 immutable crypto identity + version + length/hash evidence，不暴露未认证 byte stream；
- `ReadLease`: 权威版本、reader/owner 关系、revoke token/epoch、按偏移认证读取；
- `Finalize`: file/dir durable -> authorized short commit -> verified readable state，不是用 rename 作为 DB 事务替代品；
- `Reconcile`: 所有权判定、孤儿隔离/保留、可重试 cleanup；不清扫其他 space/旧资料。

**编程时优先沿用现有代码而不是生造同名接口**。本阶段主要冻结契约/证明失败点；未经独立评审及新的生产协议批准，不实现通向真实附件功能入口的正式 writer/reader，也不把 `attachment_feature_enabled` 改为 true。

### 4.3 测试体系：明确每类结果的证据等级

- 纯 JVM：协议转录正负向量、导入取消、错误输入、session/transaction 竞态、尺寸与预算；
- 本地 Android 单元测试：DAO / Repository 结构校验；
- API26/29/35 Android 仪器测试：**真实 SQLCipher** 与既有 Room9→10 迁移回归，独占合成沙箱下 `Os.open(O_EXCL/NOFOLLOW)`, fsync, StorageManager ProxyFD, content provider 断流等（若本次没有新增设备实验，明确仅继承此前结果）；
- 真实强停 fresh PID：正在写密文时进程被 kill，新 PID 只保留上一代 active、无不完整可见对象；如没有可用设备与外部 orchestrator，标 `NOT_RUN` 而非 SUCCESS；
- 低端真实 ARM：耗时、PSS、CPU、GC、能耗、流式吞吐、足量文件的恢复空间；**本次可以只提出验收方案，不得用 x86_64 模拟器冒充真机结果**；
- 硬件掉电和攻击者 root/同 UID：未能实测时明确威胁边界与不能证明的性质；不是成功复现 force-stop 就等价完成。

新增加的测试用例不得用 `@Ignore`/skip/mock hardcoded success 取得假绿，也不能删弱 B1 既有 29 旧表/PNBAK9/实体 UUID/ledger/锁定与真实历史回归断言。

---

## 5. 将欧盟适配纳入 V2 作为真实产品质量门槛，而不是临时做合规大重构

参考用户提供的《HRT Log 欧盟适配评估》，但**必须采用随后经核查的修订结论**；绝不能把未确认的法律义务写成既定豁免或认证。

本次需输出一份**面向 B2–B6 的合规影响矩阵**，推荐放入 `docs/med-eu-privacy-readiness-v2.md`（或仓库既有等效文件），至少包含：

1. **隐私实情**：无 `INTERNET` / 无应用内账号遥测，并不表示用户通过 SAF 或 Android 系统云端提供者“技术上无法发送任何数据”。分清应用主动网络、用户主动选择第三方文件提供者、明文 CSV/PDF 导出、加密包、Android 分享和外部副本。每项可声明的事实须指向代码/测试，用户文案不能过度承诺。
2. **Privacy P0 分支**：建议后续独立小 PR 添加 `PRIVACY.md`、README/关于页四语可访问入口、导出/卸载/备份限制说明。若与本 B2 分支测试无直接依赖，**不要为了赶进度把全部 App UI 隐私入口和 strings.xml 改动混入**；产出任务说明和验收用例即可。
3. **预期用途**：记录/提醒、非诊断/非剂量建议；基于研究参数的 M2 曲线不能替代实测。MDR 定性取决于实际 intended purpose 与功能/对外宣传，不是添加一句“非医疗器械”就自动豁免；医疗附件保存/就诊打包不得自动产出临床建议。
4. **GPSR / CRA / EAA / GDPR / EHDS**：以具体经营方式、产品范围、功能宣称、数据流与法规时间表进行条件评估；既不武断标注普遍适用，也不武断标注全部豁免。CRA 2026-09-11 报告义务与 2027-12-11 主要适用日属于有条件义务时间节点；EAA 并非所有健康 App 自动适用；未来 FHIR / EHDS 互通声明须单独评估，不能把“支持本地 FHIR 语义”吹成“医院系统认证”。
5. **安全、许可证、分发**：公开历史与旧第三方授权需独立核查，不能认定 MIT 自动覆盖历史未授权代码；F-Droid/Play 商店、签名兼容与正式交付属于另一个专项 PR，**不在本阶段申请上架**。
6. **可访问性作为 B4–B6 闸门**：EN 301 549 为审查对照，四语、320dp、2x 字号、屏幕阅读器、焦点顺序、锁定/备份/恢复/出错流程；本阶段生成可执行测试计划，等真正有页面后再声称端到端通过。
7. **医疗数据安全**：所有 UI 要分清原件、手工转录、用户确认的关联与未经核验的机构来源；不自动诊断/解释化验、不将研究模型输出伪装为化验。

若要引用法规/标准，请优先核对官方原文并记录查询日期、URL、条款与适用前提。该文件是软件工程的合规准备记录，**不是律师意见、CE 认证、CNIL 审核或安全认证**。

---

## 6. 必须生成的仓库交付物

根据仓库既有文档命名体系调整文件名，避免覆盖历史报告。默认建议：

1. `docs/audits/product-quality-2026-10/39-med02b2-v2-product-contract.md`  
   V2 PHR 三条价值链、用户故事、日期/来源、Inbox/检索/关联/纠错/导出行为；功能→B2–C3映射；对 B1 八表字段逐项复用/缺口表；需迁移处只提议不擅自改 schema。
2. `docs/audits/product-quality-2026-10/40-med02b2-crypto-import-decision.md`  
   方案 A/B 对比、证据等级、正式协议候选、威胁模型/nonce-GHASH-key 预算与迁移后果、专业评审问题列表、无明文/random-read/backups 可行性；说明哪些安全条件尚未满足。
3. `docs/audits/product-quality-2026-10/41-med02b2-import-failure-contract.md`  
   B2 的 API 与状态图、operation 状态与故障回滚、文件系统/DB 事务接缝、session/revoke/read lease 的线性化点、旧 active/完整文件 orphan 的处理；每条配对应测试 ID/方法/实际结果。
4. `docs/med-eu-privacy-readiness-v2.md`（若已有等效文件，按仓库惯例归档）  
   P0 隐私声明任务、实际数据流、MDR/GDPR/EAA/GPSR/CRA/EHDS 条件判断、无障碍验证、公开许可证/F-Droid 门槛。只记录可靠事实，不凭空获得法律豁免。
5. `docs/audits/product-quality-2026-10/42-med02b2-verification.md`  
   当前源码与 parent SHA、PR/CI、逐项构建/测试输出、fail→fix 历史、明确 `PASS` / `FAIL` / `NOT_RUN` / `BLOCKED`、可重复命令、样本合成证明、差异清单、下一阶段 B2 production go/no-go 审批点。
6. 可复现、独立于生产 APK 的 Kotlin/JVM 和 Android instrumented 合成测试代码、fixtures、如适用的 CI job；必要时更新 `docs/STATUS.md`、`docs/HANDOFF.md`、`docs/REQUIREMENTS.md`，只追加**当前状态索引**，不要把历史研究记录全部重写或删除。

**如果为了完成本 PR 发现需要更改 Room10 八表、旧 29 张表、PNBAK1、新正式密钥/协议、AppLock 正式接线或发布配置：停止该高风险修改；提出一条独立审批记录、影响分析和专门 PR 计划。** 不要把未通过审批的改变混在“修一个测试”的提交中。

---

## 7. CI、回归与接受标准

### 7.1 必须维持的旧不变量

- Android 包名 `net.plainnotes.app`、`full` 单一发行路线、无 `INTERNET`、无分析/遥测、PRIMARY/伪装笔记隔离；
- B1 通过的 **Room runtime 10** 与 **PNBAK1 serialization schema 9** 区分不变；29个旧表完整内容/索引/ledger/supply/Trash/历史 ID/就诊/化验/时间戳不改；
- 历史 R15 误跳过纠错、B4/B4-E 库存预测与 VOIDED 规则、MED01 就诊、加密备份恢复、四语、科学 PK 合同保持；
- 研究用 M2 不变，`m2_absolute_pgml=false` 的科学使用限制保持；不加剂量建议或自动临床解释；
- 当前非正式附件功能保持关闭，无完整医疗备份前不接受用户原件库正式启用，不得出现假的附件成功提示；
- 现有正式签名、versionCode、旧 Release、用户手机健康数据以及私有构建/交付件不动。

### 7.2 要跑的检查（根据实际 Gradle 模块/CI 名称核实）

至少包括：

```bash
git status --short
./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test :attachment-poc:test --no-parallel --max-workers=1
python3 -m unittest discover -s scripts -p test_attachment_design_validation.py
./gradlew :core:data:testDebugUnitTest :core:reminder:testDebugUnitTest :app:testFullDebugUnitTest --no-parallel --max-workers=1
./gradlew :app:lintFullDebug :app:assembleFullDebug :app:assembleFullRelease --no-parallel --max-workers=1
python3 scripts/check_release_manifest.py
```

以上为参考，若仓库当前 task/variant 名称不同，以实际 CI 可复现命令为准；不能因为命令不适配就简单宣称通过。新的 Android instrumented 合同如涉及 API26/29/35，应复用已有工作流、真实 SQLCipher instrumentation 并保存原始 XML/log/artifact SHA；如需新进程测试必须实际启动新 PID。

- PR 和 push 上的 Android、Science 适用检查，针对**最后的文档/代码 HEAD** 全部结束后才能标记完成；不可借用前一个绿色 SHA；
- GitHub API / SDK 下载失败等环境错误须如实分类，允许有根据地重跑单个失败任务，不得删除测试、禁用门控、取消正常运行 job 以制造“通过”；
- 对潜在的 Scope/Privacy/Room/PK 影响做 `git diff` 与生产 APK 依赖检查；
- 不能把“无 INTERNET”当成绝对防止 SAF 云端外传的结论；
- 真实 ARM、恶意 PDF、容量 256MiB/1000件、掉电/低空间专项项本轮若未跑，报告 `NOT_RUN`，不以 POC/screenshot/unit test 外推。

### 7.3 完成定义（DoD）

当且仅当同时满足：

1. 已确认并记录 #29 当前合并状态/最终源码、父分支及 PR 依赖；
2. V2 的用户故事/数据字段/来源语义可用于 B2→B6 而不矛盾；
3. 协议候选比较有真实技术依据，明确 **GO / NO-GO / BLOCKED**，未给 `SYNPOC01` 正式化；
4. 新增至少一组实质性 B2 输入失败、撤权或操作原子性负向测试，记录前后代码及结果；如果无法新增某测试，解释具体缺少的设备/接口而非补空断言；
5. 本批的生产 APK 不包含测试 crypto/provider/magic，也不出现真实附件入口；
6. 已完整记录 B3 portable full backup 的必需约束，不因 B2 实现顺序牺牲跨设备恢复；
7. 现有 B1/旧历史回归全过、适用 CI 最终 HEAD 检查完毕，未有未解释的新增 skip/错误；
8. 标出下一步生产 B2 必须由用户批准的**具体代码范围、协议版本、密钥与恢复方案、实验和风险**；
9. 独立 PR 已提交、推送并给出 SHA 与 checks 链接，**没有自动合并/签名/发布**。

---

## 8. 禁止事项与审批边界（不可解释为“之后顺便可以做”）

本提示词授权对仓库做本阶段**设计和隔离测试**，不是给任意高风险开发的空白授权。**未经新的产品负责人明确批准，不得：**

- 将 `SYNPOC01`、`DRAFT-ONLY-OBJ0!`、新候选 JCA 密文协议或 Tink adapter 直接变成 app 生产加密 writer/reader；正式密钥策略及 GCM 边界未获独立审查之前不得称生产安全；
- 开启用户可访问的医疗附件功能、导入/保管真实原件、展示安全性未证明的 PDF/图片 UI；
- 实施 B3 的新医疗完整备份 writer/restore CAS 生产逻辑、改变 `PNBAK1` 的 magic/KDF/Argon2id/结构化备份版本、增加不相容附件元数据进旧包；
- 擅改 Room runtime schema、迁移旧表、放宽 typed FK/guard/trigger、绕开 session gate；
- 将 V2 Inbox、FHIR、LabPanel、OCR、医院互通等需求一次塞进 B1/B2 生产数据库或新增云同步；
- 以“医学价值”为理由引入个人用药剂量建议、化验结果自动解读或医学异常警报；
- 自动合并 #27/#28/#29/本 PR，改默认分支、推公开 Release、更新 versionCode、签名/上传正式 APK，接触真实数据、密钥或私有个人文件。

如果用户先后给出的其他指示与本提示词冲突，遵循**最新、明确且与当前阶段直接相关的批准**；记录批准来源与代码范围。不能根据“继续做”、“把未来计划升级”自动推断其授权了生产密码学或正式数据迁移。

---

## 9. 向用户交付的最终报告格式

最终回复使用中文，提供一个紧凑状态表和结论，不输出长篇源代码或冗长 CI 日志：

| 项目 | 应报告的内容 |
|---|---|
| 最新状态 | 默认分支 HEAD、#27/#28/#29 HEAD/state/base/CI、本 PR HEAD/base |
| 本阶段实际完成 | 文档、实验测试、接口契约及对应文件；如某项未做说明为什么 |
| 密码协议 | 方案 A/B 的结论及是否获独立评审；生产能否开始：GO / NO-GO / BLOCKED |
| 数据兼容 | Room10/旧 29 表/PNBAK1 schema9/PRIMARY 与伪装隔离是否完整 |
| 验证 | JVM/Android/API26/29/35/Science、失败/原有skip、新增skip、日志证据；ARM与断电真实状态 |
| 风险 | 最严重的 3–5 项以及明确安全边界 |
| 交付 | Git commit SHA、新 PR 地址、关键文档与 CI 网址 |
| 下一个小 PR | 生产 B2 的具体最小范围及需用户批准的事项 |

最后必须明确写出：**“MED-02B2 本阶段不等于医疗附件功能已上线；B3 完整可信备份与跨设备恢复通过、后续 B4/B5 安全和用户体验验收、负责人明确批准前，不允许向用户交付可保管唯一医疗原件的正式资料库。”**

**现在开始：先刷新仓库和 PR/CI 状态，再执行本阶段设计、合成合同与独立 PR。只要满足既定边界就继续推进；遇到真正需要产品负责人审批的高风险点，给出具体可选择的方案并暂停该部分，而不是假定许可。**
