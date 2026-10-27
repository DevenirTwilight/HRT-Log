# 本轮独立内部审计证据

受审源码固定为 `9fafe3c27d563c32f5638029ff084db3e1b59d7f`。主报告见 [MED-02-INTERNAL-SECURITY-AUDIT.md](../../../MED-02-INTERNAL-SECURITY-AUDIT.md)。本目录仅含合成测试源码、依赖指纹和文本证据，没有医疗原件、PIN、真实密码、私钥或 APK。

| 探针 | 实际受审代码 | 替身/限制 |
|---|---|---|
| LockAtomicProbe | AppLock.kt；Android8 android.util.AtomicFile实际类 | Context/SharedPreferences替身；合成60字节凭据，不跑Keystore/PIN解密。实际rename后注入异常，不是Android杀进程。 |
| UnknownProbe | MedicalAttachmentsController、AttachmentFlowController、AttachmentSession | 合成ActivityResult类型；Gateway接口从受审文件提取；Backend用内存COMMIT事实模拟丢回执，无Room/SQLCipher；Viewer未调用。 |
| BlockingProbe | AttachmentSession、AttachmentInputStream | 合成阻塞InputStream.close；不等价于真实ContentProvider或内核FD关闭。 |
| CryptoProbe | AttachmentObjectStreams、AttachmentObjectContext、AttachmentSession、AttachmentInputStream | 同版本Tink JVM；内存channel，无AndroidKeyStore/SQLCipher/native decoder；样本有限。 |
| CollectionCancelProbe | MedicalCollectionWriter、MedicalCollectionIndex、MedicalArchiveManifest、MedicalArchiveCrypto、ArchiveKdfWorkspace、AttachmentBackupManifest、capability | Snapshot和MedicalArchiveRecords.count受控替身，不能证明有效业务JSON/备份恢复；只测实际writer对阻塞输出的撤权所有权。 |

环境：Linux x86_64，Java21，Kotlin compiler2.2.20；原件源码没有做为了测试的改写。`probes/stubs` 全部是测试边界，不会加入应用构建。依赖 SHA-256 由 `probes/dependencies.json` 固定；下载文件存于调用者 scratch，不收进仓库。

```bash
bash docs/audits/med02-internal-2026-10-26/probes/run-probes.sh /absolute/scratch/med02-probes
```

脚本要求 git HEAD 为冻结提交，编译实际文件；可选第二参数指定已有依赖jar目录。它不会启用应用、构建APK或访问真实用户资料。无需Android SDK。`-Xmx192m`只约束两个JVM探针，不能代替Android低内存测试。

`evidence/*.txt` 是初次成功探针输出；打包后统一复现见 `evidence/reproduction-console.txt`。编译警告保留：Tink getPrimitive API弃用、Kotlin Header.copy未来可见性变化，均未通过修改生产文件消除。`inventory.json` 记录原包132项源码哈希核对结果；`reviewed-source-sha256.json` 是重点源码阅读清单。原项目静态守卫结果仅说明这些规则通过，不是完整SAST，也未验证真实APK。

尚未执行的 Android、原生、实际跨设备和物理攻击范围以主报告为准。本目录不能当作外部认证或独立专家签字。
