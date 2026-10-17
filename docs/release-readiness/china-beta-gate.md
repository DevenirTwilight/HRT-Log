# 中国社群 Beta 准入账本 — 2026-10-17

状态：**P4_BLOCKED / NO-GO**。本文件是准备与缺口清单，不是签名、发布或替换现有 Release 的授权。P4/P5/P6 不能借用已发布 build29 的证据；build29 也不能宣传尚未合并的精确库存。

## 可核查的现有发行身份

| 项目 | 已有发行记录（不是新候选） |
|---|---|
| 仓库审计基线 | `claude/new-session-1959qb` / `d13048b18ad15eb4e7aba70f63c3c5d94a705660`，PR #63 合并后 |
| 已发布版本 | `v0.2.0` / versionCode **29** / full Universal |
| 发行源码 / tag | `76627bfe215987122666b38ea7a42ee08731cdde` |
| 包名 | `net.plainnotes.app` |
| APK SHA256 | `0d950bbb4bb281089318e080e3272400498b48cd094f4ffb4675fbabe6f93201` |
| 签名证书 SHA256 | `989ba04532e4c3ec11c2de989d5b1905cf67bdc6c449361293ef62b8a59379b1` |
| 记录来源 | `docs/delivery/ledger.json`、`docs/releases/0.2.0-rebuild-2026-10-17.md` |
| 发行源码数据版本 | Room **11**；PNBAK1 业务 Schema **10**；外层 format **1**。来源：该源码 `NotesDatabase.kt`、`RecordBackupCompatibility.kt`、`DataTransfer.kt` |
| P4 未合并候选数据版本 | Room **12** / 业务 Schema **11**；外层 format **1**，不是 build29 中已发行功能 |
| 本轮重新下载/验签 APK | **PASS（只读）**；证据见 `build29-readonly-verification.json` |
| 后端 Beta 候选 APK | **NOT_IMPLEMENTED**，无候选构建、签名或版本号 |

2026-10-17 本轮直接下载现有公开 APK 并只读核对：上述文件哈希/证书、嵌入 clean git 源码 SHA 与实际 tag 完全一致；v2/v3 签名、包名/版本、非 debug、minSdk26/target37、四 ABI、ZIP CRC、APK ZIP 16KiB 对齐及 No INTERNET 均 PASS。实际权限还包含既有 `ACCESS_NETWORK_STATE`，完整列表保存在 JSON；没有修改权限。真实安装/覆盖升级和 ELF 段对齐复核本轮未跑。GitHub Release 的历史 `targetCommitish=c52c8518…` 与实际 tag 不同，按实际 tag 与二进制来源核对，未改 Release 元数据。

发布已完成，本任务不重签、不上传 APK、不移动 tag、不发布社交公告。未来候选构建只在负责人另批后进行，沿用证书、包名和非降级版本，不要求卸载/清数据。

## 工程与产品准入

| 门 | 目前证据 / 下一步 | 状态 |
|---|---|---|
| 主线完整六项 CI | 原失败为测试初始化时外部 SDK 依赖获取；独立 #63 已合并为 d13048b，最终源80c31ce全检查通过；合并后 Android39065037526 五项及 Science39065037594 全部成功 | PASS |
| P4 原生低内存 | #57 a3e95e4 run39064593821：总RAM520962048B、最大堆201326592B；审计18135ms、预测15773ms、恢复后审计15620ms、写入17638–17848ms仍FAIL。新候选 db34e87（事务内只读行缓存）原生结果待定 | FAIL |
| P4 正式库存完整安全门 | #57 DRAFT，正式入口 OFF；不得用 JVM 诊断代替 API26 原生预算验收 | BLOCKED |
| P5 最小业务接线 | 复用已有真实 Compose 控件，统一不可变库存 DTO；不能拿 PR60 概念图当实现 | NOT_IMPLEMENTED |
| P6 正常 UI 端到端 | UI→Repository→SQLCipher→重启→备份→新设备/非空库恢复→共享预测与提醒；合成数据 | NOT_IMPLEMENTED |
| 新旧兼容 | Room 与业务备份 schema 分开；旧未知库存保留未知，不推断 CIS/CIP；原子失败回滚、VOIDED/REVERSE/UUID/ABA 必须具备候选最终 SHA 证据 | PENDING |
| 科学与安全守卫 | PeriodStability、PK、No INTERNET、锁/伪装、release test-only 隔离：须汇总候选最终 SHA 实测，不借用其他 PR | PENDING |
| 官方分发 / FT-ST | 法国官方资源仍 OFF，许可证冲突及字段解释未批准，不阻断通用手填记录的工程开发 | BLOCKED_EXTERNAL |
| 医疗原件 | 研究/演示阶段，生产入口 OFF；无正式附件加密与完整恢复的验收，不可宣传可用 | NOT_IMPLEMENTED |

## 真机、国产 ROM、升级与恢复

下表均没有设备证据；型号/系统/权限不得用模拟器信息代填。需逐台保存匿名合成测试日志、日期、候选 SHA 和操作结果。

| 设备族 | 型号 / Android / ROM | 安装升级 | 提醒/Doze/重启/权限撤销 | 锁屏隐私/伪装 | 输入法/四语/大字 | 状态 |
|---|---|---|---|---|---|---|
| 小米 / 红米 | UNAVAILABLE | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | BLOCKED_EXTERNAL |
| OPPO / vivo | UNAVAILABLE | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | BLOCKED_EXTERNAL |
| 华为 / 荣耀（Android 兼容设备） | UNAVAILABLE | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | BLOCKED_EXTERNAL |
| 其他可用 Android 8+ ARM | UNAVAILABLE | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | BLOCKED_EXTERNAL |

覆盖升级：build5→候选、25→候选、27→候选、28→候选以及29→未来候选均 **NOT_RUN**。既有发行流程的“允许覆盖安装”不是实际升级证据。每条需安装前后合成药物/计划/实际记录/库存/化验/锁定状态相等，旧 `.pnbak` 可恢复；错误密码、篡改、取消、存储耗尽和进程中断不能部分覆盖目标。不得以失败为由清库或重建密钥。Room1…当前与业务Schema1…当前测试结果须分列，不把 Room12 误称备份Schema12。

## 中文用户操作与隐私草稿

1. 从 [项目官方 Release](https://github.com/DevenirTwilight/HRT-Log/releases/tag/v0.2.0) 获取现有发行；核对文件 SHA256，并用 Android SDK `apksigner verify --print-certs <文件>` 核对上述证书。未来候选必须使用自己的源码与哈希账本。
2. 覆盖安装前通过应用现有备份入口导出加密 `.pnbak`，在受控环境验证可恢复并保管密码。不要卸载、清除数据或发送密码/备份作为反馈。
3. 首次填写药物时保留手动输入；剂量和实际使用事实由用户填写。名称相同、成分相同不证明不同规格/国家/途径可互换。
4. 提醒需按设备实际授予通知/精确闹钟等权限，并检查厂商后台限制；未实测不能保证每种国产 ROM 的准时提醒，不增加自动治疗判断。
5. 数据库与加密备份的保护不延伸到导出的 **PDF/CSV 明文**及用户交给其他软件的副本；伪装便签不是加密健康空间。应用无账户、无 INTERNET 权限、无遥测，用户明确打开外部网页后由浏览器处理网络访问。
6. 反馈通过官方仓库入口提交最小技术信息：版本、系统、空白合成步骤和错误类别；先检查截图/日志并去掉姓名、药物、剂量、化验、路径和其他健康信息。不要求真实健康记录、数据库、私钥、密码或个人备份。

## 宣传事实与后续缺口

| 类别 | 可以陈述的边界 | 后续 |
|---|---|---|
| 已有 build29 | 四语、用户手填药物与记录、旧库存、化验、导出与加密备份；具体操作以同发行源码的真实证据为准 | 真机升级/恢复仍待验证 |
| P4–P6 候选 | 不宣传精确库存已可从正式 UI 使用，不宣传可靠剩余天数或全国产 ROM 验收 | 工程 P0 阻断 |
| 中国药品资料 | 不包含已核实 NMPA 产品库；不能以法国商品资料证明中国上市/供应或跨国等效 | P1/P2 独立来源审阅 |
| 注射与 T 途径 | 不承诺所有注射药瓶 mg↔mL、酯/盐质量或 FtM/MtF 途径完整支持 | P1/P2 独立规格、迁移与备份验收 |
| 中文化验单位 | T `ng/mL`、血红蛋白/红细胞压积等尚需逐项能力审计 | P1/P2，不自动诊断 |
| PK | 群体模型估算，不能替代化验与临床判断；本任务不修改模型 | 独立科学审阅 |
| 图片 | PR60 八张为 REFERENCE_ONLY，标“设计概念”，不能当实际 App 截图 | 真实截图须来自对应构建与合成数据 |

不必等 ANSM-04/05/06、额外 PK 研究或正式医疗附件全部完成，才准备通用记录 Beta；但上述实际工程 P0 与真机缺口未解决前，结论不是 `BETA_CANDIDATE_READY_FOR_OWNER_APPROVAL`。

结论：**P4_BLOCKED**。本报告文档审查不等于 DESIGN_VERIFIED、INTEGRATION_VERIFIED、PRODUCTION_IMPLEMENTED 或 RELEASED。
