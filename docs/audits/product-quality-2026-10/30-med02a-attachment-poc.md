# 30 — MED-02A 医疗附件安全 POC

2026-10-14，IN_PROGRESS / SYNTHETIC_ONLY / NOT_PRODUCTION。主线051ee22验证通过后建立独立分支，R15合并/主线最终验证尚待。设计依据29；本轮不将任何原件导入正式应用，不新增生产表/备份协议。

## 隔离与威胁模型

独立JVM模块仅经testImplementation/androidTestImplementation进入测试；Android原型位于androidTest，正式main/Room9/PNBAK1/PK不接入。所有PDF/图片由测试生成，没有真实医疗数据。目录/Keystore alias仅专用synthetic/UUID命名，删除仅本POC自有文件，不清真实应用数据。

验证存储密文/对象完整性、头/序/长度/结束清单、跨对象/空间/密钥、错误流、并发撤权、进程中断。密码学采用JCA AES-256-GCM，不发明算法；分块协议仍需独立安全审查/nonce与AAD形式证明/已知向量跨provider。内存清理仅best effort，不保护恶意系统/root/解锁设备/内存取证，不承诺从GC或内核RAM抹除全部痕迹。SAF提供者可能云端；无INTERNET不能控制提供者。PDF native解析风险及用户主动外部副本均另列边界。

## 计划和验收边界

分块结束清单认证并全内容校验后才提供随机读；读取每块先认证再释放。文件发布采用fsync+同目录原子rename，仅完整对象可见，失败/进程死亡只留下不可用密文part。随机读无全文件ByteArray。PDF仅公开ProxyFD/PdfRenderer，不支持则记录实际失败，无明文fallback。图片先bounds/像素/采样限额，缩略图内存，不改原件EXIF。

将逐项填写实际JVM/API26/29/35、失败注入与内存测量、PR/最终HEAD CI；未运行项标NOT_RUN，不把模型当真实SQLCipher/迁移验证，不把POC当产品验收。
