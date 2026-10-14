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
