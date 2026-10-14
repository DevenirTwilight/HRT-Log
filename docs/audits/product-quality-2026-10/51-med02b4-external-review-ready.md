# 51 — MED-02B4 外部密码学与存储安全审查包（可直接交给独立专家）

2026-10-14。状态：**NOT_REVIEWED_EXTERNALLY**。本文件由项目作者及 AI 工程复核整理，**不是**审查结论；作者以外的 AI 复核（报告47）也不是外部认证。审查者身份、签审与整改字段留空，不得代填。

## 1. 审查范围与固定输入

- 仓库：https://github.com/DevenirTwilight/HRT-Log（MIT，公开）。审查基线：本批 PR-A 合并后的主线 SHA（见报告52与 PR 正文，请以该 SHA 检出，不用活动分支）。上一基线 `9d99fd9027c3e5f3e4789c0a30e92a4c8d090ac3`。
- 只含合成数据：不包含任何真实医疗文件、用户密码、签名密钥、私有构建或分发链接。测试用 Keystore alias 只属于独立测试包。
- 资料：报告32（威胁模型）、33（JCA/HKDF 备选草案）、40（Tink/JCA 决策）、41（导入/读取/撤权/失败合同）、44（canonical context、预算、威胁边界）、45（风险与审批）、47（独立 AI 复核及处理）、48–50、52（本批）。
- 代码（全部 test-only，不在生产 src/main）：`tools/attachment-poc/src/contractTest/kotlin/net/plainnotes/poc/{B2StreamingCandidate,Gate2Context,B2InputPolicyContract}.kt`；`core/data/src/androidTest/java/net/plainnotes/app/data/gate2/*`；`app/src/androidTest/java/net/plainnotes/app/attachmentpoc/{EncryptedTinkPdfGate2,TinkPdfGate2AndroidTest,KernelProxyFdGate3AndroidTest}.kt`。
- 合成向量：`tools/attachment-design-validation/gate2-context-vector.json`（114B，Python stdlib 独立重算于 `test_gate2_context_vector.py`）；`draft_vectors.json`（报告33备选，含公开 Wycheproof 子集）。这些向量是**自生成**，不能替代第三方测试向量或审查。

## 2. 复现命令

```
./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test :attachment-poc:test
python3 -m unittest discover -s scripts -p test_attachment_design_validation.py
python3 -m unittest discover -s scripts -p test_attachment_release_isolation.py
./gradlew :app:assembleFullRelease && python3 scripts/check_release_manifest.py && python3 scripts/check_attachment_release_isolation.py
# 以下需要 x86_64 emulator（ro.kernel.qemu=1），只操作独立测试包：
./gradlew :core:data:connectedDebugAndroidTest :app:connectedFullDebugAndroidTest
python3 scripts/run_attachment_gate2_red.py
python3 scripts/run_attachment_gate2_restart.py {AFTER_CREATE,WRITE,IN_COMMIT,AFTER_COMMIT}
python3 scripts/run_attachment_gate3_red.py
```

## 3. 候选方案与保留争议

- **推荐候选 A**：Tink 1.23 `AesGcmHkdfStreaming`（AES256-GCM-HKDF-SHA256，64KiB 密文段，40B 头）。实验使用的是 subtle API，不是 Tink 承诺的长期公开 key 管理 API；正式接入需改用受支持的 primitive/keyset API 或书面接受维护风险，并锁定依赖、跟踪安全公告、实测 R8 后 APK/DEX 增量（目前 NOT_RUN，JAR 大小不能代替）。
- **备选 B**：报告33 JCA/HKDF header/body/footer 自组合协议；可审计但序列化、终结清单和组合正确性负担更大。`SYNPOC01`/`DRAFT-ONLY-OBJ0!` 等测试标记永不改名为生产格式。
- 争议：A 的“完整 EOF 认证后才发读租约”是否足够，或需要额外 manifest/总长度承诺；B 是否值得自维护。

## 4. 请专家判断的问题

1. **canonical context**：`B2-SECURITY-GATE2-TEST-ONLY\0 ‖ u16 protocol ‖ u16 suite ‖ UUID(logicalVault, object, version, attempt, DEKhandle) ‖ u16 format`（114B，报告44）。字段是否充分、是否需要长度前缀/版本域、正式 protocol ID 如何分配。
2. **可信 SQL 承诺与 anti-replay**：SQLCipher 中选定版本的明文长度、plainSHA256、cipherSHA256 与 AEAD 全对象验证共同构成承诺；攻击者持有一致的旧数据库+密文+合法 key 句柄快照时，没有外部单调锚无法普遍检测回滚（已知限制）。是否可接受，或需要什么锚。
3. **密钥角色**：每对象随机 32B DEK；设备 KEK（Android Keystore AES256-GCM）分 staging/active 两个 alias、不同 AAD 域（报告44）；每次重试全新 attempt/version/object/DEKhandle；alias 丢失 fail-closed、不重建。KEK GCM 调用计数/轮换预算是否正确；portable KDF/跨设备包装必须与旧 PNBAK1（Argon2id 32MiB/t3/p1、AES-GCM、serialization 9，不含附件）分离——这是 B3 前置。
4. **流式 AEAD 预算**：16MiB 候选上限、≤257 段 GCM 调用/DEK；“GHASH 块数小”不是总伪造概率证明，请给出多用户、多对象聚合界。
5. **完整 EOF 与 seek 重验证**：同 FD 顺序认证到严格 EOF 再建随机读租约；块缓存已认证后同尺寸改写未读块的风险（报告44）；路径替换下固定 FD 读原 inode、新 open 核对 SQL 承诺。
6. **内核 ProxyFD 撤权边界（报告49）**：应用能保证“撤权后开始的读取不返回明文”；内核页缓存可能重放撤权前已交付的字节，`dup()` 后的消费者 FD 在拥有者关闭自身 FD 后仍可读、只能靠回调撤权。请确认这一契约是否足够，以及生产是否必须禁止向第三方进程暴露 PFD。
7. **FS + SQL 一致性**：journal 阶段、CREATE_NEW+NOFOLLOW、文件与目录 fsync、Room 同步 CAS 在授权门内提交、每 operation fcntl 租约（报告49修正了“同进程第二描述符关闭即丢锁”的缺陷）、孤儿保留/确认释放（报告50）。`fsync` 成功与 force-stop 都不是掉电证明。
8. **AppLock 与撤权线性化**：capability 撤权与发布共用监视器、commit 在门内；生产 AppLock/space switch/所有 consumer registry 尚未接线。
9. **不在本应用防护等级内**：root、同 UID hook、已解锁内存、用户主动明文导出、系统/云 SAF provider。

## 5. 正式实施前置（不由本包批准）

外部审查与整改签审；负责人冻结 wire/context/suite/key 角色；B3 可靠完整加密备份与新设备恢复（启用正式附件库的前置）；真实 ARM、受控 ENOSPC、掉电台架、敌意 PDF/隔离解析 worker；AppLock/consumer 接线。专家报告与负责人授权是两个独立条件，互不替代。

## 6. 审查记录（待填，禁止代填）

| 字段 | 内容 |
| --- | --- |
| 审查者姓名/机构 | （空） |
| 独立性声明（与项目无利益关系） | （空） |
| 审查的固定 SHA | （空） |
| 方法与范围 | （空） |
| 发现与风险评级 | （空） |
| 整改与复测结果 | （空） |
| 签审结论与日期 | （空） |
