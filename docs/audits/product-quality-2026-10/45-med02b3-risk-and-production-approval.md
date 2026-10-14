# 45 — B2-SECURITY-GATE2 风险、设备计划及待批准实施范围

2026-10-14。待负责人审议材料；本PR不实施生产。PHR V2/欧盟来源及报告39契约保持，本文不新增法律豁免判断。

## 风险登记

| 优先级/风险 | 证据与缺口 | 缓解/责任角色/启用闸门 |
| --- | --- | --- |
| P0 正式协议/组合审核 | A推荐候选、标准向量和真实cipher测试；NOT_REVIEWED_EXTERNALLY | 独立密码专家审查44固定SHA；负责人冻结wire/context/key角色后才生产B2 |
| P0 KEK/DEK生命周期 | 本测试包actualKeystore role隔离/丢alias拒绝；非生产服务，无portable | 安全工程师明确key预算/alias轮换/失败/可携恢复，专家审核；不接用户key |
| P0 完整认证发布 | 真SQLCipher CAS/完整EOF/丢ack保护及授权线性化新增 | 生产安全writer/所有读取consumer独立批准；公开hash不能代替AEAD |
| P0 FS+SQL崩溃一致性 | native fsync/日志/force-stop freshPID；硬件掉电NOT_RUN | 存储工程师owned资源/恢复状态机；没有跨FS/DB原子保证，保持旧active直到验证 |
| P0 恢复与写入者互斥（复核47-M1） | 原协调器reconcile无条件中止并删除未完成密文、无writer租约。**已在测试协调器修正**：import全程持有每operation的OS建议锁（同进程OverlappingFileLock、他进程tryLock=null，进程死亡由内核释放），reconcile与确认释放逐operation取锁，持锁者返回SKIP_LIVE_WRITER；同一operation第二写入者被拒。负向测试覆盖同进程并发，四处强停覆盖死亡后释放；跨进程“活着的他进程写入者”未单独实测 | 生产writer须采用同等互斥并经外部审核；建议锁是同UID协作机制，不防恶意同UID/root |
| P1 隔离孤儿保留/配额（复核47-M2） | 原无释放路径、配额被永久占用，且CREATE_NEW后至记录inode前崩溃会把自己的文件永久认作foreign。**已在测试协调器修正**：(1) 进入WRITING时记录“本operation新建的父目录”dev/inode，创建后未记录的叶子只在该父目录内、为普通文件时才可清理，新增AFTER_CREATE强停证明新PID清理自己的空叶子；父目录非本operation新建时仍隔离不删；(2) `releaseQuarantined(confirmed=true)`显式确认释放：释放配额，只删可证明属于自己的文件，外来文件永不删除（RELEASED_FOREIGN），active不可释放；真实IN_COMMIT崩溃孤儿在新PID确认释放后CLEANED | 仍待产品负责人：孤儿保留期、谁/何时确认、用户提示文案；退役epoch的active指针处理归B3完整备份/恢复设计；不做自动静默删除 |
| P0 AppLock接线 | capability测试及旧锁定回归；正式附件registry NOT_RUN | app安全负责人PRIMARY session/worker/ProxyFD/native显示统一撤权；不能以接口通过代替 |
| P0 完整B3可信备份 | PNBAK9旧包真实隔离；不包含附件，新完整包未实现 | 备份负责人完整集合+DEK认证、双份容量、新设备恢复；唯一原件保管仍禁止 |
| P1 真实ARM/容量/资源 | emulator真实调用不是ARM；16MiB/256MiB/1000仍候选 | 设备QA按下表采样/冻结配额；不能把40MiB合成预约预算当产品额度 |
| P1 真ENOSPC/掉电 | seam异常FAULT_INJECTION_ONLY；fsync成功不是flash持久性 | 授权测试设备/受控配额目录，旧数据保留及明确失败；不可填猜测指标 |
| P1 PDF/native敌意输入 | 良性四页Tink公共API；恶意parser/密码/worker deadline NOT_RUN | PDF负责人独立隔离worker/解析预算/恶意语料/真实ARM；B5阻断 |
| P1 导出/SAF/隐私 | 本地合成provider；云provider/外部副本/屏幕/IME边界未新验证 | Privacy P0独立PR，四语真实数据流提示；不宣称系统零外传 |
| P1 UX/无障碍 | 没有正式附件UI；旧B2/B3/B4/MED01回归继续 | B4/B6四语320dp/2×/TalkBack、锁定/返回/失败路径；附件端到端NOT_RUN |
| P2 PHR整理/显式关联 | 39冻结契约，无提前UI/八表改动 | 产品负责人B4/B5/C批次：Inbox分类/关联分离、日期精度、来源、搜索、多Lab与精选包 |

接受既有V2目标：原件/不可变版本→用户填写来源/分类/精度日期→明确选择typed就诊/Lab；同日/同hash不自动认定同事件。资料来源不是机构数字签章。用户可更正元数据/链接，原件替换新版本/DEK；删除默认隐藏且可恢复，永久删除不得暗改真实健康事实。完整产品V1包含静态图片+多页PDF，开放前完整B3备份必须验证。
精选包只含用户明确勾选的事实/版本，研究估算默认不选；新B3完整备份≠现有结构化PNBAK1。Privacy P0/MDR用途变更/无障碍/许可分发独立，不把“离线”当法律普遍豁免。OCR/全文/FHIR、医院抓取/云AI等接受/拒绝/延期沿用39和欧盟条件矩阵；本PR不偷偷扩Schema/UI。

## 实际设备执行计划（当前ARM均NOT_RUN）

受控设备：至少低内存ARM64（2–4GiB RAM、API26/29）与当前ARM64 API35，记录厂商/型号/SoC/CPU ABI/可用内存/flash/API/安全补丁；缺设备不填假型号/成绩。
数据仅合成：0/1/段边界/1MiB/16MiB/+1B；1/20/100/1000保留版本（含Trash/失败pending），候选256MiB满配；同version竞争/导入时backup/取消/GC/锁定。给测试包独占目录设置受控存储配额，严禁填满用户设备或清除真实app。

复现native接口见46的connectedDebugAndroidTest目标。ARM准入须专门准备明确隔离的测试设备/fixture（现有emulator-only orchestrator不能假装ARM运行）：`adb shell getprop ro.product.model`、`ro.product.cpu.abi`、`ro.build.version.sdk`，`adb shell dumpsys meminfo net.plainnotes.app.data.test`；每100ms记录PSS/native/java（命令开销必须记录），每阶段目录cipher/SQL/-wal/-shm大小。计时用elapsedRealtime：prepare→读取EOF→全认证→file+dirsync→commit→freshPID reopen；取消指令→最后交付/事务失败分别计。
CPU/能耗用受控Perfetto/电池状态采样，记录温度/充电/后台负载；实际采样峰值及间隔，不把算法64KiB scratch当峰值PSS。磁盘满盘、断电需专门授权台架，标设备与次数；force-stop仅进程中断。生产Tink接入后的最终R8 APK尺寸/DEX方法增量由获批独立分支同构建参数对照，不用JAR字节替代。

## 最小生产B2授权申请（未执行）

建议审批选项：维持NO-GO并外部审查；或批准专家整改后的**默认关闭**最小B2实现。没有本任务内的默认同意。

需明确批准：A或B正式protocol/context及可信承诺；PRIMARY-only KEK/DEK/device role与丢key策略；只处理合成数据的生产服务验证；必要的最小guard/journal字段修订另做additive ADR；真实SAF流限额、owned no-replace密文、全认证、file+dirsync、Room同步CAS及journal恢复（恢复必须与活动写入者互斥，协调器已用每operation OS锁示范，见47-M1）；孤儿确认释放与配额回收（协调器已示范）及保留期/用户提示的产品决定（47-M2）；AppLock/space switch/取消/所有consumer registry接线。长IO不持授权门；提交线性化点必须含native SQL commit。

前置：外部审核固定SHA/原语及组合预算签审、红绿/native/freshPID最终HEAD CI、ARM/ENOSPC计划、明确配额与key轮换、错误资源保留和回退策略。生产代码仍默认关闭；部署若未达到协议/key/PNBAK兼容则保留已验收主线且不导入原件。发生缺失/损坏/旧epoch只隔离，绝不自动激活或批量改历史。

后续独立批准：B3完整流式医疗备份与真实新设备恢复；B4图片/Inbox/搜索/链接/纠错；B5恶意PDF/导出/解析隔离；B6真实ARM/四语无障碍/整套回归；**显式功能开启批准**。本MED-02B3名称不改变路线依赖，不能声称B3备份已完成。
