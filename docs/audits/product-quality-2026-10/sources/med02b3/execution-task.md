# HRT Log｜MED-02B3（B2-SECURITY-GATE2）完整执行提示词

> **使用方式**：将本文件全文交给 Claude Code 或 Codex，在 `DevenirTwilight/HRT-Log` 仓库执行。它是一份**工程任务书**，不是授权上线附件库、签名发布 APK 或自动合并 PR 的文件。
>
> **命名澄清**：这里的 `MED-02B3` 是继 `MED-02B2 / B2-GATE` 之后的 **B2 生产安全准入与集成证据阶段**；**不是**既有路线中的 `MED-02 / B3`（完整医疗加密备份及跨设备恢复）。请始终写明别名 `B2-SECURITY-GATE2`，避免把这两个阶段混淆。
>
> **任务目标**：在不开放正式医疗附件能力、不擅自冻结密码协议的前提下，用真实 Android/SQLCipher/文件系统的**隔离测试环境**补齐 MED-02B2 尚未解决的关键证据，形成外部安全专家可复核、负责人可审议的 `B2-IMPLEMENTATION` 决策包，并提交**独立待审 PR**。

## 0. 你的角色及原则

你是同时熟悉 Android 安全、Kotlin、Jetpack Compose、Room/SQLCipher、Android Keystore、流式 AEAD、文件系统持久性、医学隐私和软件供应链的资深安全架构师／Android 工程师。

执行任务时：

- **以实际代码、最后 HEAD、完整 CI 和原始文档为准**，不得复制历史状态当成最新事实。
- 把每个结论标成 `ACTUAL_DEVICE`、`REAL_CIPHER_TEST`、`REAL_SQLCIPHER_TEST`、`SYNTHETIC_MODEL`、`SOURCE_REVIEW`、`INFERENCE`、`NOT_RUN` 等明确证据等级。尤其不能把 Android Emulator 等同于真实 ARM 设备，不能把 `force-stop` 等同于硬件掉电，也不能把测试中的内存 CAS 当真实 Room 提交。
- 使用**匿名合成数据**。禁止访问、导出、提交用户真实医疗文件、健康记录、密码、Keystore、签名密钥、私有构建或内部交付链接。
- 用**最小修改与可重现失败测试**推动真实工程进展，不要为了重构而重构。
- 所有可能影响历史数据、加密协议、备份及正式分发的变更采取 fail-closed。不能因为现有 CI 绿色，就削弱安全断言或把新测试改成 skip。
- 在本任务得到的允许范围内完成分析、测试工具、隔离 Android 测试、文档与独立 PR；遇到**生产密码协议冻结、生产 writer/reader、密钥接线、Room schema 迁移、正式完整备份、签名发布、开启附件功能或合并 PR**，必须停下并先申请具体批准。

## 1. 固定起点——执行前必须联网重新核实

仓库：`https://github.com/DevenirTwilight/HRT-Log`

2026-10-14 晚间的已核实情况：

| 对象 | 最后已知事实 | 开始工作时必须做的事 |
|---|---|---|
| 默认分支 | `claude/new-session-1959qb`；MED-02B1 合并后主线曾为 `cfe600eeb6efcca60668763a31969af07a2892b9` | 重新获取远程默认分支、HEAD、CI 和工作树；有变更按最新事实调整 |
| PR #27 / #28 / #29 | 已全部按批准顺序合并；Room 10 B1 已进入主线 | 不要再把它们当作 OPEN stacked PR；不重复实现 B1 |
| PR #30 | `MED-02B2: PHR V2 contracts and isolated authenticated import gate`；`OPEN`，未合并，HEAD `814d355c60396a9246ab6883ea70d3ccf67438cc` | 确认是否合并或出现新提交、目标分支、最终 checks 与差异 |
| PR #30 验收 | 末次 push/PR Android、JVM、lint、API26/29/35、Science 检查成功；代码基准 `f8b5c7a54bed07149c74a1a60efffef58c2fb054` | 若最新检查失败或改变先定位原因，不继承旧绿灯 |
| 正式个人交付 | `0.2.0 / build26`，正式签名、私有交付，与 GitHub 主线代码不同 | **不得**将 Debug/unsigned Release 当可覆盖安装的正式 APK；本任务不交付 APK |
| 生产 B2 | `NO-GO/BLOCKED`；尚无正式文件加密协议、真实原件导入及完整附件备份 | 绝不提前启用 `feature_enabled=1` 或开放可保管唯一医疗原件的资料库 |

主要源文件——必须按相应 ref 完整阅读，而不是只看本提示词摘要：

1. [报告 39：PHR V2 产品契约](https://github.com/DevenirTwilight/HRT-Log/blob/814d355c60396a9246ab6883ea70d3ccf67438cc/docs/audits/product-quality-2026-10/39-med02b2-v2-product-contract.md)
2. [报告 40：Tink/JCA 协议候选与生产准入](https://github.com/DevenirTwilight/HRT-Log/blob/814d355c60396a9246ab6883ea70d3ccf67438cc/docs/audits/product-quality-2026-10/40-med02b2-crypto-import-decision.md)
3. [报告 41：导入、读取、撤权和失败合同](https://github.com/DevenirTwilight/HRT-Log/blob/814d355c60396a9246ab6883ea70d3ccf67438cc/docs/audits/product-quality-2026-10/41-med02b2-import-failure-contract.md)
4. [报告 42：MED-02B2 最终验收](https://github.com/DevenirTwilight/HRT-Log/blob/814d355c60396a9246ab6883ea70d3ccf67438cc/docs/audits/product-quality-2026-10/42-med02b2-verification.md)
5. `docs/audits/product-quality-2026-10/32-...` 至 `38-...` 的安全审查、加密对象草案、Room 10 及 B1 验收。
6. `docs/audits/product-quality-2026-10/sources/med02b2/`：竞品研究 V2、欧盟适配原文、后续修订与 SHA 清单，已经入库，不要再声称 `SOURCE_MISSING`。
7. `docs/med-eu-privacy-readiness-v2.md`、`docs/REQUIREMENTS.md`、`docs/STATUS.md`、`docs/HANDOFF.md`、`docs/licensing.md`。
8. `core/data` 中的 `AttachmentSchema`、`AttachmentOperations`、`AttachmentTransaction`、`AttachmentKeyFoundation`、`AttachmentIdentity`、`RecordBackupCompatibility`、`DatabaseAccess`、`Migrations`；`core/domain/AttachmentSession.kt`；`tools/attachment-poc` 的 B2 测试适配器；Android 现有 AppLock/伪装路由、SAF、Export 和 ProxyFD 测试。

### 开发分支规则

- 先执行 `git status --short`、核查当前 remote/default 与 PR #30；不能覆盖他人未提交工作。
- **若 PR #30 仍 OPEN**：从其最新、已验证 HEAD 建立独立 `feature/med02b3-b2-security-gate-*`，优先以 PR #30 分支为比较基线创建 stacked PR，清楚说明尚未合并的父 PR 依赖。不改写、强推或自动合并 #30。要避免把 #30 的全部 diff 再当成自己的更改。
- **若 PR #30 已合并**：从完成主线验收后的最新默认分支建独立分支，PR 指向默认分支；不得继续伪造 stacked 关系。
- **若 #30 有新失败/冲突**：先报告阻塞事实，必要时仅提出最小修复方案；不能静默覆盖或自动合并。
- 不以 PR 数字、报告编号或 SHA 为永远固定事实：创建文件前检查路径是否已占用，后续以新 PR 实际编号为准。

## 2. 本阶段必须解决的核心问题

MED-02B2 的实际进展不可重复记功：

- Tink 1.23.0（仅测试依赖）的 AES256-GCM-HKDF Streaming AEAD 已做真密文 IO，比较了报告 33 的 JCA/HKDF 分块草案。
- 已证实：**直接 seekable reader 不能满足“完整文件认证以后才交付任何字节”的产品合同**；损坏尾段时可能先读到前面合法段。测试适配层通过**同 FD 顺序解密到严格 EOF 后再建立随机读租约**，处理了 0 字节对象，验证了取消、错域、篡改、旧 tuple、16MiB+1 拒绝等合同。
- 既有 SQLCipher/Room9→10、PNBAK1 结构化备份 9、B1 capability/session 门和早期 ProxyFD/PdfRenderer 原型已验收。
- 尚未做的不是“更多同样的纯 JVM 测试”，而是生产未来必须面对的**实际组合边界**：SQLCipher＋真实文件系统＋Keystore/授权门＋真实 Android 生命周期＋启动恢复；以及 Tink 候选与 ProxyFD/PdfRenderer 的端到端兼容性。
- 生产协议仍无独立外部安全专家审核，也没有产品负责人的 wire/key/备份授权。不得把本阶段的内部工程审计称为这两项已经满足。

本阶段优先交付以下四个可审阅的结果：

**A. 真实边界的隔离集成证据**：在专用 test-only 数据目录与合成 SQLCipher DB 中验证文件/DB/授权及失败关系。可以依赖现有 testImplementation 和 androidTest 设施，不得把实验代码或 Tink 测试 API 放入 Release 主 DEX。

**B. 加密协议准入决策包**：把方案 A（Tink streaming）与 B（JCA 草案）的所有剩余证据、可维护性、规范、成本及风险列成可签审文档；明确“推荐候选”与“正式批准／冻结”是不同状态。

**C. 可供外部专家实际审查的安全包**：固定输入资产、威胁模型、AAD/canonical context、nonce/GCM/HKDF/key-wrap/KDF 预算、已知失败反例、风险清单、复现指令；对专业审核缺席标 `NOT_REVIEWED_EXTERNALLY`，不得伪造第三方签字。

**D. 最小生产 B2 授权申请**：单独列出将来代码中允许落地的 writer/recovery/AppLock 接线范围、剩余前置条件、回退方案及审批选项；**只提交待决策材料，不在本任务实现生产协议。**

## 3. 工作包 S0：代码与证据基线审计

先建立 `baseline.md`（或仓库既有报告格式）：

- 固定 main、PR #30 的 SHA/合并态、关联 Actions run URL、主要代码文件 SHA、测试范围；说明哪些研究已达成，哪些测试方法是继承 B1/早期 POC，哪些是 B2 新证据。
- 核查 B2StreamingCandidate、B2ImportSandbox 与当前 B1 Repository、`AttachmentSession`、`AttachmentSchema` 的接口兼容性；**不先假定可直接替换**。
- 列出每一个即将验证的假设和确切的负向合同。对于无法实现且没有真设备／环境的项，提前标注 `BLOCKED`／`NOT_RUN`，但仍尽量完成其他可独立验收工作。
- 验证生产 artifact 中没有 POC/Tink/provider/test fixtures，无 `INTERNET`，不改变正式 keystore/签名路径。

**验收**：无过时 stacked 描述、无源文件缺失误报、没有用旧 commit 的 CI 冒充新 HEAD 成功。

## 4. 工作包 S1：安全协议与密钥边界审查（研究／设计／可执行向量）

对方案 A 与 B 做**证据导向**比较，不用个人偏好投票：

- `Tink 1.23`：上游公开规范与实际版本、Streaming AEAD 的段边界与 EOF/final segment 行为；same-FD 顺序 full-scan 到 seek lease；随机读时二次鉴别；原密文在 open 后被改写、截断、路径替换是否被识别；空文件与未知长度 provider；独立 PDF 读取路径是否兼容。
- `JCA/HKDF`：报告33的 header/body/footer、固定编码、AAD、总长度/hash/末尾清单和组合协议维护成本；不可把测试原型 `SYNPOC01`、`DRAFT-ONLY-OBJ0!` 改名当成正式 wire 格式。
- canonical context：精确定义拟绑定的 `protocol/suite/logicalVault/object/version/attempt/DEKhandle/format`、可信 SQL commitment（明文及密文 hash／长度）、metadata 的可信来源与版本；明确 `localDataset/physicalGeneration/recordEpoch/sessionEpoch` 为什么不是同一个密码学 AAD 身份。
- 密钥域：每次导入／失败重试使用新的 DEK/attempt/version；设备 Keystore KEK 包裹、独立 staging/active roles、丢失别名时 fail-closed；portable backup 的密钥角色归未来 B3，不能先存“以后再解”的唯一原件。
- 安全预算：依据实际最大对象长度、Tink segment 布局和 key-per-version，计算 GCM 加密次数、AAD/输入量与 KEK 包裹次数上限。不能把“GHASH 块数小”当成总伪造概率证明。提扩容须联动备份、配额、恢复双份空间和 ARM 性能。
- 明确不能检测的威胁：攻击者拥有同一数据库＋密文＋合法密钥句柄的一致旧快照时，缺独立单调可信锚无法普遍检测完整回滚；root、同 UID hook、系统云 provider 与自愿明文外传并不在同一个防护等级。
- 对比 Tink 的正式 Android/R8 依赖、APK 尺寸、长期维护、安全公告和上游 API；不要把 JAR 体积当作实际最终 APK 体积。

**输出**：`crypto-decision-record.md`（选 A、B 或维持 NO-GO，均给出理由）＋ `external-review-packet.md`＋确定性可复现向量/测试路径。若发现高风险协议缺口则维持 NO-GO，不用写免责声明来绕过。

## 5. 工作包 S2：真实 SQLCipher＋文件系统联合集成测试（严格 test-only）

目标不是制作新正式导入器，而是让研究脱离纯内存 DB/CAS 模型。选择**真实 Android SQLCipher 独占合成库**、test-only 容器、随机临时对象、非生产账号／数据，模拟将来所需的状态与持久性顺序。

### 状态／资源规则

- 复用 B1 的实际实体／DAO 约束与 `AttachmentSession` 同步提交门，或创建具相同契约的隔离 test harness；记录真实使用范围。**不改变**生产 `AttachmentSchema` 当前对 `VERIFIED/VALIDATED/feature_enabled=1` 的硬拒绝。必须测试拟议 writer 状态时，在 test-only 数据库中模拟、或使用独立隔离 test schema；不能为跑通研究测试放宽正式 guard。
- 流程：新 operation/attempt/key → owned staging 写密文 → 输入 provider **真实 EOF** → 整体 AEAD 认证与可信 selected context/hash → 文件 fsync 与目录持久化尝试 → SQL 审批门内同步 COMMIT/CAS → 新进程按 journal 重建可见状态与清理边界。
- 权威成功点是 SQL 提交；文件存在不等于业务成功。`post-commit ack` 丢失不能删除 active 或重复发生成果；old active 留存直至安全切换及恢复证据。
- `operation-owned` 限制：目录根、UUID、FD、generation、key 角色、计划写入路径必须受控；不通过目录名猜测业务有效状态。不覆盖已有文件，不跟随危险软链接，不跨代误删用户／外部文件。
- 设备文件原语核查 O_EXCL/NOFOLLOW、父目录 FD、目录 fsync；**不要**从成功返回 fsync 推断硬件掉电零数据损失；保留 Android API26/29/35 差异和实际失败证据。
- 对 native SQLCipher 的事务边界做真实 `force-stop` + fresh PID（只用独立测试包与合成数据），不能用 `kill` 用户主程序、`pm clear` 或卸载用户装机应用。

### 必做攻击／中断矩阵

至少覆盖以下条件，每项注明注入 vs 真实调用、提交前 vs 提交后、恢复后预期：

1. SAF `statSize=-1`、虚假的声明长度、provider 中途 IOException、可靠 pipe closeWithError、持续 `read=0`、格式 MIME 与魔数不符；真实读字节上限与取消响应。
2. 0、1、段边界、16MiB、16MiB+1、截断、尾随、块重排、跨对象拼接、旧 tuple replay；不得交付部分明文、不得完成残缺文件。
3. 写入中断、文件 force 前／后、目录 sync 前／后、SQL commit 前／后、ack 失败、主动撤权／换空间／关闭 owner、进程强停；预期要么旧数据仍是 CURRENT，要么确认已提交且可安全 reconcile。
4. 重复 operation/重试、双写者同版本 CAS、配额重复预约、cleanup 失败、complete orphan、foreign 文件、delete/GC/read lease 并发。
5. 文件在完整认证后、随机读前被改动；原密文被替换为合法旧版本；trusted SQL commitment 被篡改；错 KEK／alias 丢失。
6. 用测试专用 FS 策略模拟 ENOSPC、路径占用与拒绝 fsync；若没有安全、可重复的真实磁盘满盘环境，明确写 `FAULT_INJECTION_ONLY`，不称真实 ENOSPC 通过。
7. 完整数据替换与旧 PNBAK1 恢复保持：Room 10 runtime、备份 serialization 9，旧结构化恢复生成 fresh epoch，原附件链接隔离，同整数 ID 不能重新附着。

新增与既有测试尽量复用、减少重复；先让尚未被守住的行为在 test harness 的负向测试中失败，再实现测试隔离协调器的修正，保留红／绿证据。不得伪造生产缺陷或随意退化已验收代码。

**退出准则**：真 SQLCipher＋FS 的测试结果独立列出；可复现强停后的 freshPID 有明确结果；无法完成真实硬件掉电、设备 Root 威胁、真实 ARM 等项目全部标 `NOT_RUN`，并提出必要条件。不要因测试代码通过而启用生产。

## 6. 工作包 S3：Tink 读取链路与 PDF 的 test-only 端到端验证

原 MED-02A 在 API26/29/35 证明了旧 POC 的 `StorageManager.openProxyFileDescriptor + PdfRenderer` 与受限图片解码的可行性；**这不等于 Tink 1.23 候选、正式身份绑定和新 reader 已经实测通过**。

在隔离 `androidTest` 中（如果环境可用）：

- 用真实 Tink 流式密文（不是把原 POC 的原语重命名）建立完整认证后才发行 read lease 的固定 FD。检查是否存在两个 FD 竞争、文件被替换、owner 关闭、会话撤权、原对象删除、native bitmap/renderer 尚在运行等竞态。
- 使用带多页的**合成 PDF**：首次完整文件认证 → ProxyFD 的非顺序 offset 读取 → PdfRenderer 第1/末页跳转、缩放、重开、撤销和损坏后的拒绝；检查不得在 `cacheDir`/MediaStore 中写入完整明文或把敏感文件名暴露出去。
- 避免将真实 PDF 密码保护、恶意脚本、畸形 parser、OOM、高压解压、native 故障与基于正常样例的成功混为一谈。恶意语料/隔离 worker/deadline/低端 ARM 未有能力时标 `NOT_RUN`，仍需作为未来 B5 阻断门。
- 若测试库无法在当前 API 正确提供 seekable 对象、处理空对象，或触发 native 渲染泄露，则提供最小复现与设计修正；**禁止**采用落盘明文 PDF 的 `cacheDir` 回退。

注意：本阶段的 PDF 验证只提供 B5 早期可行性和选型证据，不增加生产 PDF 入口。

## 7. 工作包 S4：风险、成本与实际设备验收计划

对照报告 40–42，在 `risk-register.md` 维护**风险—证据—缺口—缓解方案—责任角色—上线闸门**：

- `P0/BLOCKER`：正式对象协议冻结、独立专家 review、DEK/KEK/role/Keystore、全认证后业务发布、Room＋FS 崩溃一致性、AppLock 真正集成、B3 可信完整备份与新设备恢复。
- `P1`：真实低端 ARM 的内存/PSS/电池/CPU/启动和 16MiB／候选 256MiB／1000 版本容量；真实 ENOSPC 或受控磁盘配额、解析器敌意语料，API 差异。
- `P1`：明文导出边界、SAF 云提供者、截屏/任务缩略图、伪装空间隔离；四语、320dp、2×字号、TalkBack、IME、返回路径；真实资料库界面尚未上线，不可写“端到端通过”。
- `P2`：临床报告 Inbox/来源/日期精度、元数据搜索、多文件多化验显式关联、精选资料包、版本纠错等未来 B4/B5/C 阶段用户功能；不应为了本阶段设计完备而偷改 Room 10 的八表。

请提供可执行的设备测试矩阵及预期证据：设备型号/CPU/内存/API、受控数据规模、计时边界、采样间隔、PSS/native/java、磁盘/WAL/加密暂存、耗电和取消时延。没有设备就写 `NOT_RUN + 所需设备/命令`，不得填写猜测指标。

## 8. 工作包 S5：MED-02B2 与 PHR V2／欧盟计划的衔接

**必须继承已冻结的产品契约，但不在本 PR 添加提前可见的 UI。**

- 资料链是「文件原件及版本 → 用户提供的来源/日期/分类 → 显式关联的就诊或化验」，不是单纯加密目录；资料来源是用户陈述，不等于医院数字签章认证。
- Inbox 的“未分类”与“未关联”分离；临床日期、报告签发日期、导入 UTC、日期精度和时区互不混同；相同日期/文件哈希不等于同一就诊／标本。
- 用户可纠正类别、机构、标题和链接；内容替换产生新不可变版本及 DEK；删除／回收／永久 purge 需有审计语义，不能重写原件字节或暗删其他健康事实。
- V1 完整产品目标包含**静态图片＋多页 PDF**，不是仅图片；所有真实原件功能开放以前必须有可验证的**完整 B3 加密备份及跨设备恢复**。
- 医生精选资料包只包含明确选中的来源与版本，默认不含整库和 M2 群体估算；明文分享需明确二次确认并说明无法追回外部副本；OCR、全文搜索、医院登录／互通、复杂 PDF 文字遮盖和云 AI 均不是本阶段工作。
- 欧盟适配文档作为独立分支的后续任务：PRIVACY.md＋四语 App 入口、意图用途和宣传语闸门、无障碍、许可／分发核查；本轮只保持 `docs/med-eu-privacy-readiness-v2.md` 的条件适用性，不宣布 GDPR/MDR/GPSR/CRA/EHDS 普遍豁免或“合规 9/10”。不要伪造法律／安全认证。

如发现产品要求与安全架构冲突，写清**用户影响、技术选择、兼容性与需要批准的具体问题**；不要擅自通过 schema／密码协议变更解决。

## 9. 产物及 GitHub PR 交付

请在仓库中新增或更新下列**实质内容**（参考既有 `docs/audits/product-quality-2026-10/` 命名规则，先检查编号是否冲突）：

1. `43-med02b3-baseline-and-gap.md`：实时代码/PR/CI 基线，之前完成项与新增证据。
2. `44-med02b3-crypto-decision-and-external-review-packet.md`：方案 A/B、选型依据、正式协议未批准项、公开向量及专家问题。
3. `45-med02b3-android-sqlcipher-fs-evidence.md`：联合测试方法、真实/模型/注入证据层级、每个中断 seam、失败—修复—回归及 NOT_RUN。
4. `46-med02b3-risk-register-and-b2-approval-request.md`：阻断风险、接受/拒绝标准、明确需负责人的生产 B2 选项、后续 B3–B6 交接。
5. 机器可读 JSON（若仓库现有约定允许）：最后 HEAD、所有测试任务/设备、实际执行总数、已有与新增 skip、错误与回滚、artifact SHA、构建体积（实测才填）、源码与 Release 隔离。
6. 仅为完成本阶段缺口必需的 `test/contractTest/androidTest` 或工具、CI 配置与隔离核验脚本；不可修改生产加密逻辑或应用 UI。更新 `docs/STATUS.md`／`docs/HANDOFF.md`／`docs/REQUIREMENTS.md` 的**当期简短索引**，历史事实保留并明确已经过时的描述。

只有在真正新增测试/改动后运行完整回归：

```sh
./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test :attachment-poc:test --no-parallel --max-workers=1
python3 -m unittest discover -s scripts -p test_attachment_design_validation.py
python3 -m unittest discover -s scripts -p test_attachment_release_isolation.py
./gradlew :core:data:testDebugUnitTest :core:reminder:testDebugUnitTest :app:testFullDebugUnitTest :app:lintFullDebug :app:assembleFullDebug :app:assembleFullRelease :core:data:assembleDebugAndroidTest :app:assembleFullDebugAndroidTest --no-parallel --max-workers=1
python3 scripts/check_release_manifest.py
python3 scripts/check_attachment_release_isolation.py
```

按现有 CI 实际支持执行 API26、29、35 目标 Android instrumentation、Room/SQLCipher/Keystore/强停重启、B1–B4/R15/MED01、Science gate/deep；保持原有测试数量与既有 skip 判定，不删断言、不新增假绿 skip、不降级 lint/许可证/manifest/签名/版本检查。**不取消尚在运行的正常 CI**。仅将某次 HEAD 自己的最终 checks 计作该 HEAD 的验收。

最终创建**一个独立待审 PR**（如目标仓库和权限允许），PR 描述包括“动机、范围、状态、风险、证据矩阵、无法完成的实机验证、明确的下一步批准条件”。可提交/push 本任务授权范围内的**测试和文档**；不要 auto-merge、rebase 改写他人的分支、改默认分支策略、签名上传 APK 或改变仓库 Release。

## 10. 明确禁止与自动停止条件

以下任何情况都必须停下相应生产改动，向负责人汇报而不是自行扩大任务：

- 需要把 Tink/JCA 研究协议正式写入 `src/main`、新建可识别生产 `magic` 或永久 wire format、调整密码学 role/KDF／认证规则；
- 需要改正式 Room schema/guard、使 `VERIFIED`、`VALIDATED` 或 `feature_enabled` 可用于实际原件；
- 需要实际用户 SAF 入口、原件 writer/reader、真实 AppLock/伪装路由功能接线；
- 需要对正式 PNBAK1 加附件，或开始 B3 新加密完整备份、portable DEK/KEK 跨设备恢复；
- 需要改 PK/M2 科学模型、真实化验/就诊/服药/供药流水、私有用户数据、对外网络权限或 SDK；
- 需要改 versionCode/applicationId/正式签名/密钥、公开 Release、F-Droid/Google Play 分发或 Git 历史、合并 PR；
- 无法复现核心断言、发现新生产风险或外部协议审查不通过，不能标成 `PRODUCTION_GO`。

可以提出明确的技术申请，不要把“请继续研究／做下一个阶段”解释为无限授权实现上述项目。

## 11. 完成判定与最终答复格式

本任务可能达到的最高状态是：**`B2-SECURITY-READINESS_RESEARCH_GO`／`B2-IMPLEMENTATION_PENDING_OWNER_AND_EXTERNAL_REVIEW`**。即使所有新实验通过，**生产 B2 仍应维持 NO-GO，除非另有独立专家审查证据和负责人明确批准；本任务不代为完成批准。**

最终答复必须包含：

- 最新主线、PR #30 以及本 PR 的真实状态、最终 SHA、父子关系；
- 本任务新增了哪些**真实**证据，与 MED-02B2 已有 20 项合同的区别；
- Tink 与 JCA 候选的判定及是否仍 BLOCKED，哪一类外部审核正在缺失；
- SQLCipher＋FS／Android／PDF／会话撤权各项已运行的准确设备与测试结果；
- 失败注入、真实 ENOSPC、真实 ARM、硬件掉电、敌意 PDF、外部审查等各自的证据等级与 `NOT_RUN`；
- 未改/已改 production 文件的 `git diff` 审计、Release DEX/manifest、Room10、PNBAK1 schema9、build26、隐私及 PK 不变量；
- 所有实际完成的最终 HEAD CI 链接，未完成和失败不得粉饰；
- 文档索引、需要产品负责人批准的**最小且明确的下一步 B2-IMPLEMENTATION 范围**。

结尾必须明确：

> MED-02B3（B2-SECURITY-GATE2）是安全准入和集成证据阶段，**不是 B3 完整加密备份**，也**不是可向用户交付保存唯一医疗原件的正式资料库**。正式 B2 仍需协议/密钥方案的外部独立审查和负责人批准；B3 完整可信备份与跨设备恢复、B4/B5 图片与多页 PDF、B6 真实设备和无障碍验收以及明确启用批准仍是后续门槛。

---

## 给执行者的最短启动指令

现在开始**只读检查真实仓库**，首先返回：PR #30 是否合并、最新默认 HEAD、与 39–42 的当前差异；然后从正确基线创建独立分支，在**test-only** 范围完成 S0–S5。尽可能实际跑完可用测试和 CI，留下可复现的证据与独立 PR。**不得**自动跨越生产协议、Room guard、真实附件与完整备份的审批边界。对于需要负责人明确批准的部分，输出具体 ADR 与问题，不要代替批准。
