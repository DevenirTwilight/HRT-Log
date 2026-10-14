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
