# 中国社群 Beta 准入账本 — 2026-10-18

状态：**P4_BLOCKED / NO-GO**。本文件是准备与缺口清单，不是签名、发布或替换现有 Release 的授权。P4/P5/P6 不能借用已发布 build29 的证据；build29 也不能宣传尚未合并的精确库存。

## 可核查的现有发行身份

| 项目 | 已有发行记录（不是新候选） |
|---|---|
| 仓库审计基线 | `claude/new-session-1959qb` / `417e383ecba8e8482f3cf6c043c62b7be8ee7cbf`，#66–#70 合并后；本轮主线CI需独立复验 |
| 已发布版本 | `v0.2.0` / versionCode **29** / full Universal |
| 发行源码 / tag | `76627bfe215987122666b38ea7a42ee08731cdde` |
| 包名 | `net.plainnotes.app` |
| APK SHA256 | `0d950bbb4bb281089318e080e3272400498b48cd094f4ffb4675fbabe6f93201` |
| 签名证书 SHA256 | `989ba04532e4c3ec11c2de989d5b1905cf67bdc6c449361293ef62b8a59379b1` |
| 记录来源 | `docs/delivery/ledger.json`、`docs/releases/0.2.0-rebuild-2026-10-17.md` |
| 发行源码数据版本 | Room **11**；PNBAK1 业务 Schema **10**；外层 format **1**。来源：该源码 `NotesDatabase.kt`、`RecordBackupCompatibility.kt`、`DataTransfer.kt` |
| P4 未合并候选数据版本 | Room **12** / 业务 Schema **11**；外层 format **1**，不是 build29 中已发行功能 |
| 本轮重新下载/验签 APK | **PASS（只读）**；证据见 `build29-readonly-verification.json` |
| P4/P5/P6 后端候选 APK | **NOT_IMPLEMENTED**；主线另行交付的私密 build30 不是本P4候选 |

2026-10-17 本轮直接下载现有公开 APK 并只读核对：上述文件哈希/证书、嵌入 clean git 源码 SHA 与实际 tag 完全一致；v2/v3 签名、包名/版本、非 debug、minSdk26/target37、四 ABI、ZIP CRC、APK ZIP 16KiB 对齐及 No INTERNET 均 PASS。实际权限还包含既有 `ACCESS_NETWORK_STATE`，完整列表保存在 JSON；没有修改权限。补充只读 GNU readelf 检查：四 ABI 共8个原生库的全部 PT_LOAD p_align 均为16384，见 `build29-elf-alignment.json`；这是静态文件检查，16KiB页设备运行、真实安装和覆盖升级仍NOT_RUN。GitHub Release 的历史 `targetCommitish=c52c8518…` 与实际 tag 不同，按实际 tag 与二进制来源核对，未改 Release 元数据。

已发布build29仍对应76627bfe，主线后来合并的#65及#66–#70没有自动进入该APK；本审计没有重新签名或替换公开资产。

### 独立的主线私密 build30

合并账本 `docs/delivery/build30-china-beta-p0-2026-10-18.json` 记录：负责人按主线 REQUIREMENTS §107 单独批准并由另一开发线交付 versionCode30，源码 `dfb21c79d23c053f0d0e0f40242aa85db12f6a1c`，APK SHA256 `f0e209f1a08c1bb6a2c65c907ecb20d365ab6c52d70127abe5e312c60c6703c8`，证书与build29相同，交付渠道为会话私密文件。此处是 **DECLARED_BY_MERGED_LEDGER**，本审计未重新下载、验签或交付build30，不借用build29只读验签来证明该二进制。

build30源码包含#65、#66和#67，主线后来#70的“一次测试只发一条通知”改动不在该构建内；P4 #57也未进入它。Room11/业务10与正式附件OFF边界不变。主线417的新CI、build30源码CI、P4候选0fd的CI/性能和真实设备验收分别记录，不能互相代替。国产ROM/ARM安装、覆盖升级、真实触发和恢复仍NOT_RUN。公开Release与tag未改变；不因此启动build31或扩大一次性交付授权。

发布已完成，本任务不重签、不上传 APK、不移动 tag、不发布社交公告。未来候选构建只在负责人另批后进行，沿用证书、包名和非降级版本，不要求卸载/清数据。

## 工程与产品准入

| 门 | 目前证据 / 下一步 | 状态 |
|---|---|---|
| 主线完整六项 CI | 旧基线8ebb719合并后六项PASS及三API日常4项证据保留；随后1abfeea的device-tests实际FAIL于定时提醒链（run39120871006/job117656041418）。最新主线417e383已含#70，自己的六CI待核，不能借用旧绿灯 | PENDING |
| P4 原生低内存 | 未合并源3ee5110 / run39121269126：audit11286/forecast11600/restored10395ms本轮<15s，三写入11959/11020/11524ms仍>5s。二进制运输与trace ACK已PASS；76108样本/152边界未闭合帧仅PARTIAL_DIAGNOSTIC，SQLCipher游标为主要成本。全部早期失败保留。排序引用索引候选0fd0ca3本地data417/原生源码编译/同规模投影相等与SQL20198→14189通过；真实API26 run39122744634待验 | FAIL / NEW_CANDIDATE_PENDING |
| 日常快照恢复 | #65已获独立批准合并，主线8ebb719六CI及API26/29/35各4新SQLCipher用例0fail/skip、官方工件SHA/CRC核验。build29不含修复；按合并源码祖先关系build30包含修复，但本审计未验证build30二进制或真实安装 | PASS_MAIN / BUILD30_SOURCE_ONLY |
| P4 正式库存完整安全门 | #57 DRAFT/正式入口OFF。旧源3ee六CI和三API引用23项全PASS；当前0fd普通合并主线417后，最终六CI与原生预算待验；不能用JVM1.49–1.59s诊断代替原生5s门 | BLOCKED |
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

覆盖升级：build30→未来P4候选、build5→候选、25→候选、27→候选、28→候选以及29→未来候选均 **NOT_RUN**。既有发行流程的“允许覆盖安装”不是实际升级证据。每条需安装前后合成药物/计划/实际记录/库存/化验/锁定状态相等，旧 `.pnbak` 可恢复；错误密码、篡改、取消、存储耗尽和进程中断不能部分覆盖目标。不得以失败为由清库或重建密钥。Room1…当前与业务Schema1…当前测试结果须分列，不把 Room12 误称备份Schema12。

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

## 当前证据索引

- P4未合并候选固定源码：[0fd0ca3](https://github.com/DevenirTwilight/HRT-Log/commit/0fd0ca38bd94db910632067bffb17e2fe2f96f9a)。证据目录 [performance/](https://github.com/DevenirTwilight/HRT-Log/tree/0fd0ca38bd94db910632067bffb17e2fe2f96f9a/docs/ansm-integration/p4/performance) 包含native-profile-3ee5110、whole-string-final-checks及三API、secondary-sorted和被拒绝Map方案的原始诊断；不是主线已集成资源。
- 本文档前一HEAD49236a2自身六项必需CI实际PASS，详见docs-49236a2-final-checks.json；本次普通同步main417后的新文档HEAD必须取得自己的检查结果，当前PENDING。
- 状态仍是 **PRODUCTION_BLOCKED / P4_BLOCKED / NO-GO**。没有把P5/P6、正式附件或真实中国设备测试写成已实现。
