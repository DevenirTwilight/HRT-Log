# 30 — MED-02A 最终技术验证报告

2026-10-14；**POC_COMPLETE / SYNTHETIC_ONLY / NOT_PRODUCTION / NOT_SECURITY_CERTIFICATION**。

PR：[27](https://github.com/DevenirTwilight/HRT-Log/pull/27)，不自合并。代码HEAD `6521173330de1277090bbdc43095ce73c4fddd1b` 自身push/PR全部成功。报告提交的最终SHA及自身CI检查链接固定在PR正文/外部最终验收摘要，不在文件内循环修改自引用SHA。以下旧“待测/失败”是过程记录，以本节实际结果为准。

## 前置、实现及生产隔离

PR26 MED01合并051ee22主线Android38524575273/Science38524575321全绿。用户后续明确要求“PR25想办法合并”：在R15原分支合入主线，解决13处共享插入冲突，保留原和MED01代码/四语/全部合同/两套外部重启，c97d9c6本地目标通过，push/PR38525991389/38525995026及38525991267/38525995037全绿；合并PR25为c3bf2cbc。主线Android38527627941三任务与Science38527627946深度全部success，MED02已纳入最终验证基线，未覆盖/撤回R15。

独立`:attachment-poc`（tools/attachment-poc）提供JCA AES256GCM块、流式输入、完整认证EOF、随机读、标准向量与失败合同；app只有androidTestImplementation依赖。PDF/图片/Keystore/provider/进程实验均androidTest。实际debug DEX核验主APK不含ChunkObject、测试APK包含；无app/core生产main源码、Room9、PNBAK1/正式KDF、PK、versionCode26、签名配置改变。正式build26 SHA256仍0c6bd9132ab15ca7625f1da19b59c1c9450cf4cb0518b15100e30396c5ed3fa0；没有签名/上传/交付正式APK或修改真实医疗数据。

## 协议与完整性证据

SYNPOC01仅POC magic。每次尝试新随机256位DEK/64位nonce前缀，32位内容序号0..16383及保留ffffffff结束nonce；头68B绑定store/object/version UUID与blocksize，AAD再绑定类型域/序号/实际长度。每块128位tag，结束清单认证真实总长、块数、SHA256；未知长度流不信available/stat声明，实际计数最多16MiB，block 1KiB..1MiB。EOF+fsync后同目录atomic rename才出现object；重试不覆写原内容/复用nonce。

打开先认证结束清单、精确物理大小、全部块及有序明文hash，才暴露任何FD/字节；随机读目标块重新验证tag，跨块读失败清掉整个本响应、不返回部分结果。没有全文件明文ByteArray/文件；全认证打开有O(N)时间成本。默认64KiB的算法scratch上限196624B，不包括JCA/native/VM内部，不能冒称实测峰值。独立密码学review、目录fsync/断电/反回滚等仍未证明，见威胁边界。

## 自动测试与失败注入

| 范围 | 实际结果 | 证据/限定 |
| --- | --- | --- |
| JVM既有/POC | 194+28零失败、零skip | domain58/PK124/importer12；crypto20/recovery8，2m26s |
| Android native单测 | data160/reminder17/app865零失败 | app61原有条件skip，未增加；21m56s含lint/Debug/两仪器包 |
| API26/29 | 每端POC30零失败、零skip | crypto20/PDF+图片+Keystore6/restart1/真实source3 |
| API35完整 | data66/app212零失败 | app8原条件skip由外部脚本实际执行；POC30实际执行 |
| lint | 0错误/178既有警告 | 没有POC新警告 |
| Debug/unsignedRelease/仪器包 | CI成功 | clean source/provenance/无INTERNET/ledger保护通过；不交付 |
| Python/Science | scripts35；deep261/225归档及独立参考成功 | PK未改，PR与push deep均实际执行 |
| B2/B3/B4/R15/MED01 | 完整旧合同及外部新PID成功 | 包装lazy136viewport仍max3挂载；锁/明文SQLite清理/password kill/VOIDED/PNBAK1/R15/MED01保留 |

确定性用例：标准AES256GCM向量，空/1/块边界/多块/16MiB未知长度；随机跨块/重开；新DEK与nonce/对象；错key/space/object/version；头/内容/tag/manifest篡改、截断/追加、重排/重复/缺块/跨对象拼接、打开后变更均拒绝。source中途IOException、non-progress0读、quota溢出、取消、BLOCK/SYNC/RENAME和注入ENOSPC不发布残缺对象、旧object保持。100个实际密文对象分别保留，选中3个读取；1000元数据行每页20、不预解密文件；后者仅模型，不是正式UI性能证书。

真实ContentResolver读取测试APK专用生成provider：query SIZE=null、PFD statSize=-1、reliable pipe closeWithError实际IOException、MIME声称PNG而magic不符全部过；provider没有文件/DB访问且只测试manifest注册。主/伪装以独立UUID/key域和撤权模拟，未把真附件接入现有伪装笔记。portable envelope错密码/空间/key/tag拒绝，新设备key模拟恢复；实际Keystore不可提取、删除alias后旧handle失败，仅显式可信portable恢复才生成新synthetic key/重包裹。PBKDF2固定100k仅POC，不替换正式Argon2id。

恢复模型逐阶段中断旧generation/rows不动，旧PNBAK1型record-only恢复换epoch/解除链接，相同整数ID不能重连；误添加逻辑trash/恢复revision防陈旧，重复同关联合并、同hash不同显式关联保留。模型不是Room/新备份/跨资源原子部署。真实强制终止：prepare持续阻塞真实加密stream且part已写，Python确认instrumentation还活着再force-stop；新PID验证无结束认证的part不可用/清理、旧完整object+sentinel保持，三API实际成功。prepare的被杀日志是预期，不将其当成功完成测试；verify必须OK且freshPID。

## PDF与图片实证/性能

API26/29/35实际公开StorageManager.openProxyFileDescriptor + PdfRenderer均可seek/read/render；4页、跳0→3→1、zoom1024、重开、撤权后read/render拒绝、FD/native关闭、bitmap recycle通过。不需外部PDF库，不使用明文临时文件后备。先完整验证再打开Renderer，损坏/截断不能进去。新app files/cache没有完整明文PDF/以PDF头开头的新文件；这是受控目录观测，不是系统/SAF提供者全盘取证或RAM零痕迹证明。

JPEG/PNG/WebP魔数、声明MIME、bounds/像素上限、inSampleSize、thumb≤4MiB/512视口、巨大PNG/无效格式拒绝、decode后原字节精确往返全部过；原件不重写/不做EXIF净化。图片fixture原2k×1k，512×256缩略图实际524288B；没有声称扫描医学内容正确或支持所有解析恶意样本。

下面是4MiB合成PDF stream（cipher4196472B）导入+完整验证+render的**实际单次**x86_64 emulator观测，512×768 bitmap；采样间隔20ms，整个测试进程，不是文件专属/绝对瞬时峰值/ARM基准：

| API | elapsed ms | bitmap bytes | sampled Java max bytes | sampled native max bytes | sampled PSS max KiB | samples |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 26 | 308 | 1572864 | 22191544 | 14122008 | 139451 | 10 |
| 29 | 179 | 1572864 | 8443272 | 10077616 | 205899 | 10 |
| 35 | 182 | 1572864 | 8417376 | 12618656 | 199704 | 9 |

原始三API JSON与artifact SHA256见30-med02a-code-device-evidence.json；第一轮旧样本单列first-device-evidence，不混作final head结果。16MiB JVM流多次实际693/1154ms（不同运行条件，不做平均/ARM外推）。配额建议仍16MiB/256MiB/1000件待批准；1000件全部密文/256MiB整体吞吐/低内存ARM/电池成本NOT_RUN。

## 失败记录与恢复

- Android首轮编译PdfDocument不支持use，改显式try/finally，编译成功；不是已执行设备失败。
- dcb8beb首次API35：7项全在MODEL/FINGERPRINT启发式emulator守卫拒绝，未进入preview；26/29真实通过。改成getprop ro.kernel.qemu==1，无法证明仍拒绝，没有删guard/skip/断言，新API35实际全过。
- 6521173 PR API29第一次在UTP依赖解析前失败：protobuf-java4.28.3 POM“not found”；同push API29全部通过且canonical Maven POM核验HTTP200。仅job115740437418定向重跑一次，PR38530335636 attempt2成功；未改依赖版本/源码/测试，不重跑成功作业或取消CI。详细机器记录可见CI和最终外部报告。

## 威胁模型、限制与审批

保护对象：存储密文、内容/对象/空间身份、EOF、解锁会话后续访问和加密中间文件。攻击：离线改头/序/tag/hash/拼接/损坏/截断、错误密码/Keystore不可用、未知/恶意流、陈旧指针/重复导入、锁/换空间/kill、解析尺寸炸弹。合成测试不涉及真实医疗数据。

不覆盖恶意OS/root/解锁设备内存取证、外部导出副本、有效旧密文重放、所有PDF解析漏洞/页炸弹/嵌套/passwordPDF、硬件掉电/flash清除、物理ARM或真人TalkBack。Java/native/kernel RAM仅best effort；app lock为test viewer撤权模型，不是正式AppLock新页面集成。数据库元数据/迁移/完整医疗包只设计及模型，**不是产品ready**。ENOSPC是确定性故障seam，未填满磁盘；file fsync+rename不是DB跨资源事务，directory fsync及journal需后续证明。

十项决策及分阶段B0..B6见31：建议metadata SQLCipher+独立不可变分块AEAD、无明文PDF在三API可行/不需外部库、图片可进入经review的正式技术实施、保守配额、Room9→10新表/版本/link/epoch/journal、PNBAK1与新流式医疗包共存、旧ID隔离/人工关联、独立安全审查、先图片+可靠完整备份再正式开放受限PDF。需负责人批准生产协议/审查、迁移与新包/KDF/关联政策、配额/回收版本、首批PDF/分享范围与ARM验收、是否伪装医疗库；未自动开始MED02B。

## 新增/修改文件

- `.github/workflows/android.yml`
- `app/build.gradle.kts`
- `app/src/androidTest/AndroidManifest.xml`
- `app/src/androidTest/java/net/plainnotes/app/attachmentpoc/AttachmentCryptoAndroidTest.kt`
- `app/src/androidTest/java/net/plainnotes/app/attachmentpoc/AttachmentPreviewAndroidTest.kt`
- `app/src/androidTest/java/net/plainnotes/app/attachmentpoc/AttachmentRestartAndroidTest.kt`
- `app/src/androidTest/java/net/plainnotes/app/attachmentpoc/AttachmentSourceAndroidTest.kt`
- `app/src/androidTest/java/net/plainnotes/app/attachmentpoc/EncryptedPdfPoc.kt`
- `app/src/androidTest/java/net/plainnotes/app/attachmentpoc/ImagePoc.kt`
- `app/src/androidTest/java/net/plainnotes/app/attachmentpoc/LargeSyntheticPdf.kt`
- `app/src/androidTest/java/net/plainnotes/app/attachmentpoc/SampledMemory.kt`
- `app/src/androidTest/java/net/plainnotes/app/attachmentpoc/SyntheticEmulatorGuard.java`
- `app/src/androidTest/java/net/plainnotes/app/attachmentpoc/SyntheticStreamProvider.java`
- `docs/HANDOFF.md`
- `docs/REQUIREMENTS.md`
- `docs/STATUS.md`
- `docs/audits/product-quality-2026-10/30-med02a-attachment-poc.md`
- `docs/audits/product-quality-2026-10/30-med02a-code-device-evidence.json`
- `docs/audits/product-quality-2026-10/30-med02a-first-device-evidence.json`
- `docs/audits/product-quality-2026-10/30-med02a-validation-evidence.json`
- `docs/audits/product-quality-2026-10/31-med02b-minimum-implementation-plan.md`
- `docs/audits/product-quality-2026-10/README.md`
- `scripts/collect_attachment_poc_evidence.py`
- `scripts/run_attachment_poc_restart.py`
- `scripts/test_collect_attachment_poc_evidence.py`
- `settings.gradle.kts`
- `tools/attachment-poc/README.md`
- `tools/attachment-poc/build.gradle.kts`
- `tools/attachment-poc/src/contractTest/kotlin/net/plainnotes/poc/ChunkContract.kt`
- `tools/attachment-poc/src/main/kotlin/net/plainnotes/poc/ChunkObject.kt`
- `tools/attachment-poc/src/main/kotlin/net/plainnotes/poc/KeyEnvelope.kt`
- `tools/attachment-poc/src/main/kotlin/net/plainnotes/poc/RecoveryModel.kt`
- `tools/attachment-poc/src/test/kotlin/net/plainnotes/poc/ChunkTest.kt`
- `tools/attachment-poc/src/test/kotlin/net/plainnotes/poc/RecoveryTest.kt`

---

以下为保留的实施过程（旧待测状态不代替本节已验证结果）。

# 30 — MED-02A 医疗附件安全 POC

2026-10-14，IN_PROGRESS / SYNTHETIC_ONLY / NOT_PRODUCTION。主线051ee22验证通过后建立独立分支，R15合并/主线最终验证尚待。设计依据29；本轮不将任何原件导入正式应用，不新增生产表/备份协议。

## 隔离与威胁模型

独立JVM模块仅经testImplementation/androidTestImplementation进入测试；Android原型位于androidTest，正式main/Room9/PNBAK1/PK不接入。所有PDF/图片由测试生成，没有真实医疗数据。目录/Keystore alias仅专用synthetic/UUID命名，删除仅本POC自有文件，不清真实应用数据。

验证存储密文/对象完整性、头/序/长度/结束清单、跨对象/空间/密钥、错误流、并发撤权、进程中断。密码学采用JCA AES-256-GCM，不发明算法；分块协议仍需独立安全审查/nonce与AAD形式证明/已知向量跨provider。内存清理仅best effort，不保护恶意系统/root/解锁设备/内存取证，不承诺从GC或内核RAM抹除全部痕迹。SAF提供者可能云端；无INTERNET不能控制提供者。PDF native解析风险及用户主动外部副本均另列边界。

## 计划和验收边界

分块结束清单认证并全内容校验后才提供随机读；读取每块先认证再释放。文件发布采用fsync+同目录原子rename，仅完整对象可见，失败/进程死亡只留下不可用密文part。随机读无全文件ByteArray。PDF仅公开ProxyFD/PdfRenderer，不支持则记录实际失败，无明文fallback。图片先bounds/像素/采样限额，缩略图内存，不改原件EXIF。

将逐项填写实际JVM/API26/29/35、失败注入与内存测量、PR/最终HEAD CI；未运行项标NOT_RUN，不把模型当真实SQLCipher/迁移验证，不把POC当产品验收。

## 首轮JVM实际结果（非最终HEAD验收）

独立模块ChunkTest19/19、RecoveryTest7/7，零失败/零skip，2026-10-14。JCA标准AES256GCM零key/nonce16字节明文known-answer通过；空/边界/多块/随机读/重开、重试新DEK和nonce、错误key/身份、头/内容/tag/footer篡改、截断/追加/块顺序/重复/缺块/跨对象拼接及打开后篡改均安全拒绝。输入流中途IOException、注入ENOSPC、锁定取消、BLOCK/SYNC/RENAME故障不发布新对象且旧对象可读；未知长度16MiB上限和不进展流终止；close后不能读。

实际16MiB流write+完整验证+尾部randomread1154ms，64KiB块；算法scratch_bound196624字节，**不是实测峰值内存且不含JCA/native/VM内部**。Android实际PSS/位图/时延待设备JSON。密码portable/envelope错误密码/错space/错设备key/tag拒绝；跨新key恢复成功。恢复模型每阶段中断旧generation/记录保持，旧记录备份恢复取消原ID链接，1000条元数据每页20/无预览构建；此为模型，不是实际Room新表/新包协议。

R15集成c97d9c6本地data全量及刷新/导航目标成功9m42；push/PR Android38525991389/38525995026及Science38525991267/38525995037全绿，已按用户授权合并PR25为c3bf2cbc。该合并主线JVM/Science成功，Android/API35尚待。MED01主线051ee22全绿；POCscope32a1681 Android38526529630/Science38526529638成功。生产边界仍不变；最终POC需纳入R15并等最终HEAD全部检查。

首轮Android编译失败为PdfDocument未实现所用use receiver，改显式try/finally关闭后compileFullDebugAndroidTestKotlin成功46s；编译错误不算设备红测或PDF可行性结果。JVM后续代码未改变19+7合同。Android原型含4页/跳页/缩放/重开/撤权、损坏/截断拒绝、4MiB流式PDF、JPEG/PNG/WebP原字节保留与尺寸炸弹、真实Keystore及实时强停测试。将实际API26/29/35输出及新PID证据填入最终报告；物理ARM未运行，Native内存/系统磁盘不能从设计上冒称验证。

## API26/29首轮真实结果（dcb8beb，不是最终HEAD）

Android38528743955的API26/29定向作业实际success，每端26项（加密19+预览6+重启1）零失败/零skip，另live import force-stop后新PIDverify通过。原始artifact摘要与SHA256已核验，见30-med02a-first-device-evidence.json，11877436084/API26与11877292759/API29。真实公开ProxyFD/PdfRenderer多页/跳页/zoom/reopen/撤权、完整认证后才发FD、bitmaps释放、新app文件/cache没有PDF头/完整明文copy均通过。无外部PDF依赖；磁盘观测只覆盖新app-owned文件，不能当系统/provider全盘取证。

| 实测x86_64 emulator | API26 | API29 |
| --- | ---: | ---: |
| 4页PDF往返两次时延ms | 96 | 255 |
| 4MiB stream PDF cipher bytes | 4196472 | 4196472 |
| large PDF import+verify+render ms | 200 | 175 |
| 512×768 bitmap实际bytes | 1572864 | 1572864 |
| large PDF进程PSS before/after KiB | 119754→122785 | 197340→206952 |
| JPEG/PNG/WebP thumb bytes各 | 524288 | 524288 |
| JPEG/PNG/WebP decode ms | 4/20/7 | 5/23/9 |

PSS是整个测试进程前后观测，不是峰值/图片专属占用，不是ARM benchmark。大PDF算法scratch bound196624 bytes明确不含provider/native内部。下一批新增周期采样指标需最终设备实际结果；只记测得值。主线c3bf2cbc最终Android38527627941/JVM/API35、Science38527627946全部success，已经纳入POC分支f6761c4，不回退R15/MED01；完整JVM194+POC28已本地成功2m26，Python35成功。

## 仍需实测/审查

API35和下一批最终HEAD设备与完整回归仍待；物理ARM、真人TalkBack、256MiB全部对象备份、真实Room10迁移、独立密码学审查均NOT_RUN，不能称产品ready。原型没有新医疗包/真正附件迁移，没有生产AppLock viewer路由。使用真实ContentResolver的测试APK专用synthetic管道提供者（不注册正式APK）将补验未知statSize/空SIZE、可靠pipe读取错误与MIME不符；单纯InputStream seam与系统provider路径结果分开报告。ENOSPC为确定性注入，不填满设备。最终范围/审批见31。

## 首轮API35失败与修正

dcb8beb Android38528743955最终failure：Android/JVM/Science与API26/29 success，API35 app183中7失败，全部为fixture的“generic指纹或sdk机型名”启发式守卫拒绝（IllegalStateException: Synthetic emulator only）；19加密合同通过，PDF/图片/Keystore/重启body未执行。不能称API35预览已验证，也不无目的重跑。已改用/system/bin/getprop ro.kernel.qemu精确为1，与既有外部emulator脚本同一证据；失败/无法获取仍拒绝，仍不运行真机清理，不新增Assume/skip，不改任何业务断言。新批须在26/29/35全部实际运行验证。

另外真实测试APK ContentProvider只输出固定合成byte pipe，无路径/临床/数据库访问；只test manifest注册、正式APK没有provider。未知SIZE(null)/statSize=-1、可靠管道closeWithError实际IOException、声明MIME不符由ContentResolver实际验证；其测试尚待设备。新Keystore测试删除alias后旧handle拒绝，再仅通过可信synthetic便携envelope重建新key并重包裹。
