# 42 — MED-02B2 / B2-GATE 验证与交付

2026-10-14。**代码阶段完整验收通过；最终文档HEAD的精确SHA/CI以PR30最终交付记录为准，交付前仍必须核验其全部结束成功。** 此批是设计及隔离合成合同，不是正式医疗附件库。

## 真实基线与状态差异

默认claude/new-session-1959qb最新验证交接cfe600eeb6efcca60668763a31969af07a2892b9；merge代码be953672f503940a10880e23dabaaa853d3017bb。与附件旧OPEN状态不同：

| PR | accepted HEAD | merge | 当前 |
| --- | --- | --- | --- |
| 27 | 1c8d38553b30734565ac6c44751f0bb1dc19c9a7 | d4564fc50201b1d8132d63467028c230e87521fa | MERGED，base default |
| 28 | 977e07796cdcec831ebd6d3142e0b80daafa2ea1 | 79f50e81c03a2077ede999c18952dd104df548b1 | MERGED，retarget default后差异一致 |
| 29 | 772f7ecba354dc4d5c36399db53a4f19eb7bc98f | be953672f503940a10880e23dabaaa853d3017bb | MERGED，retarget default后差异一致 |

主线MERGED_CI_VERIFIED：[Android38598762817](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38598762817)全5jobs成功，[Science38598762944](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38598762944)gate成功、纯文档deep按既定门控skip。代码be953的[Android38596888161](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38596888161)和[Science38596888022](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38596888022)全成功且deep实际运行。

独立feature/med02b2-v2-import-gate从cfe创建，parent clean，远程HTTPS为DevenirTwilight/HRT-Log；不重写/修改27–29。北京时间=UTC+8仅换算，环境Etc/UTC，实际查询日期2026-10-14；正式build26与仓库默认不同。新PR目标默认；最终SHA/PR/final HEAD checks链接在PR30正文固定，不能借用旧SHA。

## 本批实际改动

- 报告39产品契约/来源差异、40Tink/JCA比较、41失败与B1接入契约、本报告及欧盟条件矩阵。
- 四份补充Markdown与执行提示词原字节归档、SHA清单；SOURCE_MISSING→SOURCE_SUPPLIED_READ，没有把后续法律修订改进原稿。
- 仅contractTest/test/androidTest的B2StreamingCandidate/ImportSandbox/InputPolicy；Tink1.23仅测试依赖。cipher IO真实，DB/CAS/dir seam明确模型。
- 五项新图模型、Release DEX/manifest隔离检查与27项污染subcases；CI保留所有旧任务并增加隔离检查。
- 不修改任何生产Kotlin/Room10八表/旧29表/SchemaGuards/PNBAK1/PK/版本/正式sign，feature仍0。真实医疗文件、用户数据/密钥未接触。

## 失败先行与修复（不删断言）

1. 新测试适配层直接调用Tink seekable reader，故意未加全认证门：corruptFinalSegmentCannotObtainAuthenticatedReader **1 test / 1 failure**，损坏末tag仍交付首字节。此为适配合同缺口，不宣称Tink原语漏洞或现有应用已上线漏洞。
2. 增加同FD全seek扫描后，0字节matrix真实失败：9方法/1failure，非进展EOF。固定1.23的seek空对象返回0；改为同FD顺序decrypting stream全EOF认证，再创建随机租约，不删除空对象断言、不改库源码。
3. 再加parent close、session撤销、解密后交付前撤销、旧合法tuple/trustedcommitment、块修改和input/cancel/cleanup/receipt合同；本地聚焦随后通过。红/绿原始日志存外部artifacts，报告只摘要实际结果，不用编译失败替代红用例。

## 证据等级与执行矩阵

| 检查 | 当前结果/等级 |
| --- | --- |
| 新候选JVM合同 | 新增20方法PASS、全POC60与domain63/PK124/importer12共259PASS，0fail/skip；真实JCA/Tink/file IO，不是生产SQL进口器 |
| 设计Python | 58方法PASS（继承53+新图5）；pure模型不证明真backup |
| Release检查器单测 | 3方法PASS，9marker×3污染位置=27负向subcases；f8本地及CI实际unsigned Release扫描PASS，1个DEX |
| 全JVM | f8本地及CI：domain63/PK124/importer12/POC60=259，零失败/错误/skip；新增20，不删原40 |
| Android unit/lint/Debug/unsignedRelease | f8本地完整31m05及push/PR全部PASS；data180/rem17/app865，61原有app skip；lint/两variant构建/两test APK通过 |
| API26/29/35 | f8三API实际PASS：26/29每API data21+app61，零fail/skip；35 data88/app报告243、零fail、8原有skip。新增20同源合同三端通过；B1 SQLCipher/迁移/强停与旧B2/B3/B4/R15/MED01重启回归全部执行通过 |
| Science | f8 push及PR gate/deep全部实际SUCCESS |
| 新B2 filesystem/Room联合force-stop/freshPID | NOT_RUN，未实现正式importer；不能拿继承B1未提交事务强停当本批新协议验证 |
| 真实ENOSPC/硬件掉电/祖先替换 | NOT_RUN；异常注入、fsync返回不代表真正满盘/掉电 |
| 低端ARM/PSS/能耗/256MiB真实库1000件 | NOT_RUN；JAR尺寸/算法缓冲和模型1000节点不是ARM性能 |
| 四语320dp/2×/TalkBack | 未来资料库UX合同，未新增UI；旧B3/B4/MED01设备回归沿用完整CI，未来附件UI端到端NOT_RUN |
| 外部独立安全认证 | NOT_RUN；作者/自动测试不等于专家认证 |

复现命令：

```
./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test :attachment-poc:test --no-parallel --max-workers=1
python3 -m unittest discover -s scripts -p test_attachment_design_validation.py
python3 -m unittest discover -s scripts -p test_attachment_release_isolation.py
./gradlew :core:data:testDebugUnitTest :core:reminder:testDebugUnitTest :app:testFullDebugUnitTest :app:lintFullDebug :app:assembleFullDebug :app:assembleFullRelease :core:data:assembleDebugAndroidTest :app:assembleFullDebugAndroidTest --no-parallel --max-workers=1
python3 scripts/check_release_manifest.py
python3 scripts/check_attachment_release_isolation.py
```

API26/29 package=net.plainnotes.app.attachmentpoc和B1 library目标按现有CI执行；API35全app/data+所有外部重启回归；Science保留原gate/deep，不新增skip。只在上一批CI全部结束后推修正，不取消运行任务。

## 下一最小生产PR准确状态

B2-GATE研究GO，B2-IMPLEMENTATION **NO-GO/BLOCKED，尚未授权开始**。需要负责人明确批准的最小代码范围：

1. 专家review后冻结A候选或B备用及canonical context、正式protocol ID、DEK/KEK/wrap预算和丢key策略；禁止采用实验magic。
2. PRIMARY-only默认关闭writer：真实SAF流、固定16MiB候选限额、operation-owned O_EXCL/NOFOLLOW/FD父目录/fsync，真实Room日志/CAS，AppLock会话注册及所有worker/reader/结果交付门。
3. 专门审查B1拒VERIFIED/VALIDATED的最小guard调整及journal错误/cleanup证据；需要新字段时独立additive ADR，不随意修改已验收八表。
4. 真SQLCipher+FS每seam kill/freshPID、真实ENOSPC、重复操作/并发quota、旧active保留、恢复前隔离与cleanup失败证据。新cipher key不可恢复前不能存唯一原件。
5. B3另批批准完整流式包/portableKDF/新device/整图激活/双份容量；本批不写正式backup。

无需负责人逐步批准本B2-GATE的安全研究/测试；以上只阻断未来生产子项。资料库开放另需B3可信完整恢复、B4图片/UX、B5恶意PDF/ARM、B6完整产品验收及显式开启批准。

**MED-02B2 本阶段不等于医疗附件功能已上线；B3 完整可信备份与跨设备恢复通过、后续 B4/B5 安全和用户体验验收、负责人明确批准前，不允许向用户交付可保管唯一医疗原件的正式资料库。**

## 已核验代码CI与实测证据

[PR30](https://github.com/DevenirTwilight/HRT-Log/pull/30)，OPEN/未合并，base默认；代码f8b5c7a54bed07149c74a1a60efffef58c2fb054。

| 检查 | 链接/结果 |
| --- | --- |
| PR Android/JVM/lint/Release/API26/29/35 | [38602239109](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38602239109)，5jobs全success |
| push Android | [38602160691](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38602160691)，5jobs全success |
| PR Science | [38602239260](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38602239260)，gate/deep实际success |
| push Science | [38602160448](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38602160448)，gate/deep实际success |

下载核验5份报告ZIP，不含APK：[原始artifact身份及API计数](42-med02b2-code-evidence.json)。API26 SHA d071ff1769bb1c12ef689e70afd31a1c8a222be8d600ed41a3fbe52516671b9a；29 de6aceed3b59d063335fa56e38845c5b27d57a811a3ead827b080874745f51d9；35 1367823a58d8f15690e7f8020db4adb5fa6f78681228f0764d3a3d8608ee09ff；build 569a55f78b6f6e78a5e179fba86076b9162b2aabc0e49f7b71713eff56c2f4df；JVM c6aced5bc1683cefad15322e43fe4376ec31ddf69d6c4f52ef4bdbe8df7b4484。

实际测试耗时（不能当吞吐/PSS/ARM基准）：本地POC60方法4.950s；B2 matrix9方法API26 0.360s、API29 0.286s；Import7方法API26 2.669s、29 1.815s。矩阵包括0/1/65480/65481/65536/65537/1MiB/16MiB随机读，16MiB+1明确拒绝；20/100/1000图仅synthetic元数据模型，不是1000件真实UI或容量。8KiB输入/64KiB候选segment是算法buffer，不是测得PSS；native/整设备资源仍NOT_RUN。

最终git scope核对：production Kotlin、core/data/schemas、PK、AppLock、PNBAK/guard、main manifest、R8和版本/sign配置无差异；只新增测试依赖、docs/scripts/CI及隔离tests。正式build26 SHA仍0c6bd9132ab15ca7625f1da19b59c1c9450cf4cb0518b15100e30396c5ed3fa0，不重新签名或交付。

本末次文档提交记录这些已完成代码结果；其最终精确HEAD/完整CI在PR30正文与checks固定，必须等待其全部成功才结束交付，不能借用本表f8状态。
