# 57 — MED-02B5 外部独立安全审查：B3 恢复补充附录与邀请清单

2026-10-15。状态：**NOT_REVIEWED_EXTERNALLY**。本附录补充报告 **44、45、51**，由项目作者与 AI 工程整理，**不是审查结论**。没有真实独立专家参与；下方审查记录字段保持空白，禁止代填。专家审查与负责人对正式 B2/B3 生产实现（默认关闭）的批准是**两个独立闸门**：审查通过不自动合并任何生产 PR；专家建议改协议/密钥时，先单独 ADR 与负责人决策，不在测试 PR 中直接改生产代码。

## 1. 审查包索引（全部公开、合成）

| 内容 | 位置 |
| --- | --- |
| 威胁模型、JCA 备选草案 B | 报告 32、33 |
| Tink 候选 A 决策、canonical context、预算、威胁边界 | 报告 40、44 |
| 风险与生产审批前置 | 报告 45 |
| 内核 ProxyFD 撤权与 page cache 边界、两 PID 写入者租约 | 报告 49、52 / `52-med02b4-verification.json` |
| 孤儿/配额合同 | 报告 50 |
| B2 外部审查主包（问题 1–9） | 报告 51 |
| **B3 完整备份/新命名空间恢复 ADR 与待决政策** | 报告 55 |
| **B3 实测证据（API26/29/35 原始日志、红绿、强停）** | 报告 56 / `56-med02b5-b3-gate-verification.json` |
| 代码（test-only） | `core/data/src/androidTest/java/net/plainnotes/app/data/b3/`、`.../gate2/`、`tools/attachment-poc/src/contractTest/kotlin/net/plainnotes/poc/`、`app/src/androidTest/java/net/plainnotes/app/attachmentpoc/` |
| 固定 SHA | 以报告 56 记录的 PR-B 最终 HEAD 与合并后默认分支 SHA 为准；MED-02B4 合并 `b248294`（#32）、`b43d109`（#33）可追溯 |

只提供合成资料与安全问题；不交付用户密钥、真实医疗数据、签名密钥、私人 APK。

## 2. 复现

```
# x86_64 emulator（ro.kernel.qemu=1），只操作独立测试包 net.plainnotes.app.data.test：
./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.plainnotes.app.data.B3GateAndroidTest,net.plainnotes.app.data.B3RestartAndroidTest
python3 scripts/run_b3_gate_restart.py BEFORE_COMMIT
python3 scripts/run_b3_gate_restart.py AFTER_COMMIT
python3 scripts/run_b3_gate_red.py
adb logcat -d -s B3_GATE:I      # 每个用例的证据行（拒绝原因、PID、API、堆信息）
./gradlew :app:assembleFullRelease && python3 scripts/check_attachment_release_isolation.py   # 测试标记不进 release DEX
```

## 3. 请专家回答的 B3 问题（在报告 51 问题 1–9 之外）

1. **候选 A vs B 用于完整包**：本批外层用 Tink `AesGcmHkdfStreaming`（单流 AAD=SHA256(bootstrap)），帧语法与最终 manifest 在认证流内。是否足以代替报告 33 B 的 header/body/footer 分 key 设计？外层流 + 内层对象原密文的嵌套是否引入问题？
2. **bootstrap 与密码 KDF**：master key 随机、password-KEK 包裹、AAD 绑定 magic/protocol/profile/salt/archiveUUID；profile allowlist 在 KDF 前拒绝未知/超预算。可携 KDF 的正式参数（需在真实 ARM 低内存上测量）、离线猜测成本、是否需要独立 header MAC 与 key commitment（多密钥 GCM 的 key-commitment 问题）。
3. **canonical framing 与 EOF**：`type‖len‖payload`、上限、MANIFEST 必须最后且其后 EOF、transcript 覆盖全部前置帧。是否需要长度前缀的版本域、帧类型 allowlist 版本化、manifest 是否应再绑定 bootstrap 哈希（目前通过流 AAD 间接绑定）。
4. **对象身份与重放**：对象内层 AAD=`vault|document|version|object`，恢复时按图中版本身份解密（换对象/换版本 → OBJECT_AUTH）；同一版本 UUID 已在目标存在 → 拒绝。是否还需绑定 dataset/epoch？跨 vault 迁移时重新加密的规则。
5. **DEK 可携包装与设备 KEK**：包内 DEK 由外层流保护；目标端先以 operation 作用域 AAD、提交时以最终 dataset 作用域 AAD 用目标别名重包裹；源别名永不使用。GCM 随机 IV 次数预算、角色分离是否充分；是否应改为每包派生的独立 key-wrap key。
6. **快照一致性**：导出在 SQLCipher 独占事务门内读取全部 rows 与 DEK，门外依靠不可变对象 + 备份租约 + 读取时 SHA 复核。正式实现的加密 spool（报告 35）是否必需；长导出期间 GC/版本替换/purge 的线性化。
7. **SQL/FS 原子性**：staging（SQLCipher stage 表 + operation 自有文件）→ fsync → 发布到最终名（存在即拒绝；rename 会覆盖，单写入者无竞争的假设）→ 单事务 CAS 切换权威 → receipt → 清理。提交后 ACK 丢失的幂等、清理失败的 journal、reconcile 只删 journal 中未被引用的自有文件。force-stop ≠ 掉电；真实 fsync/flash 持久性未测。
8. **旧数据集与回滚**：旧 dataset RETIRED 并保留全部密文/包裹作为回退，LIVE link → QUARANTINED，同整数 ID 不重绑。没有可信单调锚时，攻击者用一致的旧完整包“恢复”到旧状态（whole-vault rollback）无法普遍检测——是否可接受，或需要用户可见的备份时间/序号确认。
9. **资源与拒绝服务**：每帧/帧数/对象上限、KDF 内存预算=maxMemory/4、配额要求旧+新同时容纳。恶意包使恢复耗尽存储/内存的剩余路径；低内存设备上的失败表现（参见 API29 16MiB 堆事件，报告 55 §10）。
10. **有界 PDF/恶意输入**：B3 恢复不解析对象内容，只做 AEAD 与哈希；恢复后的 PDF 渲染沿用 B2/B5 的隔离要求（报告 41/49），恶意 PDF 与 native parser 隔离仍 NOT_RUN。

11. **密钥清零与库生命周期**：本批实测发现，过早清零交给 SQLCipher 的密钥数组会让连接池重开时以全零密钥加密（报告 55 §6a）。请确认正式实现中所有交给原生/库的密钥缓冲区（SQLCipher、Tink、Keystore 包裹）的清零时机与所有权规则。

## 4. 期望的审查交付

审查者真实身份与独立性声明、审查的固定 SHA、方法（代码审读/形式化/测试复现/模糊测试等）、每项发现的严重度、整改建议、整改后复测结果、明确的签审意见（可接受/有条件/不可接受）与日期。

## 5. 审查记录（待填，禁止代填）

| 字段 | 内容 |
| --- | --- |
| 审查者姓名/机构 | （空） |
| 独立性声明 | （空） |
| 审查的固定 SHA | （空） |
| 方法与范围 | （空） |
| 发现与风险评级 | （空） |
| 整改与复测结果 | （空） |
| 签审结论与日期 | （空） |
