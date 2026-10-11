# HRT Log 舌下雌二醇研究全景整理与历史证据导航

> **整理日期：2026-10-11｜性质：原有研究归档整理，不是新药代拟合，也不修改正式模型。**  
> 此页为后续研究接手的**当前状态入口**。它并不追溯重写原始历史报告：原报告中“尚未入库／OSF未打开／研究分支未合并”等措辞可能忠实反映当时，而今天的状态以此页、GitHub实际提交和引用证据为准。机器可读的逐阶段索引：[HISTORICAL_RESEARCH_CATALOG.json](HISTORICAL_RESEARCH_CATALOG.json)。

## 一句话总判断

HRT Log 原 Legacy E2_SL 是**已经在产品中使用、但尚缺严格独立个人浓度验证**的经验核；实验 M2（P2-X 冻结的 15 候选）在 Price/Rosano/Komesaroff **已经用于模型开发的群体曲线**上，得到更好的描述性归一形状拟合，不能因此宣称对未来患者/24h谷值的绝对预测准确。P2-C 正式替换闸门仍然 **HOLD**。

## 1. 完整范围：真正存在的研究、历史补研及工程代码

- P2 之前的基础：[`docs/pk-model.md`](../../pk-model.md)、[早期各给药途径证据汇总](../estradiol_oral_sl_im.md)。涉及口服、舌下、肌注与药代参数汇总，不能直接等价于后续独立舌下 M2 验证。
- P2-A/B：[原生研究总览](README.md)、[冻结协议](validation-protocol.md)、[研究资料分割](split-manifest.json)、[候选数学](candidate-models.md)、[原始比较结论](model-selection.md)。不是“丢了两个单独的历史ZIP”。
- P2-C 是**软件替换与人工批准**的独立闸门；[历史补录](history-2026-10-10/reports/p2-c.md)存在不代表曾获批准。
- P2-D–R 是仓库中已经有对应研究报告与数值审计的原生研究序列；后续 P2-S–AT 是另一个阶段序列，并非废除前者。
- 2026-10-11 以后另开展 P2-AR-REAL、P2-AU、AV、AW；**P2-AX 核查时仍是待审草稿 PR**，不能算成默认分支合并成果。

## 2. 从问题找证据：六大主题

| 科学问题 | 首先读什么 | 目前可靠结论与未解限制 |
|---|---|---|
| 为什么不能按 Doll 单一目标判断谁准确 | [P2-K](p2k-featherline-hrt-balanced-evidence.md)、[P2-E](p2e-primary-source-upgrade.md)、[P2-AT](history-2026-10-10/reports/p2-at.md) | Price 部分发表 AUC 支持 Featherline；M2 在已经接触的更多时点组均值形状比正式 Legacy 更吻合。两结论可以同时成立，不是独立赢家认证。|
| 为什么 Price Figure1 与 Table1 不匹配 | [P2-I](p2i-price-figure-reconciliation.md)、[P2-J](p2j-price-figure-baseline-consistency.md)、[P2-AH–AK](history-2026-10-10/README.md) | 原文 1mg Table1 AUC0–24 报 2109 pg·h/mL，人工读 Figure1 约 1550–1568；基线、图像、群体与作者积分算法未解决，**不能称论文错误**。|
| 为什么相似峰值给出不同谷值 | [P2-O](p2o-tail-structural-nonidentifiability.md)、[P2-Q](p2q-unknown-baseline-gain-noise-identifiability.md)、[P2-AF](history-2026-10-10/reports/p2-af.md) | 快慢输入/消除与未知基线高度混淆，8–24h 及重复给药谷值不具可靠个体预测精度。|
| 如何选新的采样时点 | [P2-P](p2p-conditional-sampling-info.md)、[P2-AM](history-2026-10-10/reports/p2-am.md)、[P2-AW](p2aw-identifiability-design/P2-AW-REPORT.md) | 只追求早期理论区分分数不等于长尾验证；实际给药/采血时钟、基线、测定限决定信息量。|
| 拿到的 OSF 数据究竟是什么 | [P2-AP](history-2026-10-10/reports/p2-ap.md)、[P2-AQ](history-2026-10-10/reports/p2-aq.md)、[P2-AR-REAL](p2ar-real/README.md)、[P2-AU](p2au-background-profile/README.md) | AP 是**早期访问受阻的历史快照**；AQ 后来取得私人表。VNC54有11人20次配对访视，但缺逐人密集曲线及准确用药时钟，不能当真正独立PK胜负。|
| 怎样独立验证与批准上线 | [P2-R](p2r-cross-study-evidence-upgrade-gates.md)、[P2-AV](p2av-external-source-gate/P2-AV-REPORT.md)、[P2-C](history-2026-10-10/reports/p2-c.md) | 看过/用于调参的队列不能再充当“未见盲测”；来源去重、剂型、检测法与合法重用必须分开审核；正式替换仍不批准。|

## 3. 按阶段逐一导航：早期设计与证据（A–R）

| 阶段 | 报告/研究内容 | 当前资料身份 |
|---|---|---|
| P2-A/B | [首批冻结证据/协议、研究候选与评估](README.md) | 原仓库冻结研究/非独立ZIP |
| P2-C | [生产模型替换审批闸门](history-2026-10-10/reports/p2-c.md) | **未实施：P2-C HOLD** |
| P2-D | [人体初证、Komesaroff增长约束](p2d-evidence-theory.md) | 原仓库研究文件 |
| P2-E | [Price/Rosano原始发表数值](p2e-primary-source-upgrade.md) | 原仓库研究文件 |
| P2-F | [TransPrEP途径混合与输入阶数](p2f-transprep-and-transit-theory.md) | 原仓库研究文件 |
| P2-G | [数据获取、授权与队列身份](p2g-data-acquisition-theory.md) | 原仓库研究文件 |
| P2-H | [原版PDF、两室半衰期口径](p2h-original-pdfs-half-life-audit.md) | 原仓库研究文件 |
| P2-I | [Price图/表AUC冲突](p2i-price-figure-reconciliation.md) | 原仓库研究文件 |
| P2-J | [Price基线与积分代数](p2j-price-figure-baseline-consistency.md) | 原仓库研究文件 |
| P2-K | [Featherline与HRT对称比较](p2k-featherline-hrt-balanced-evidence.md) | 原仓库研究文件 |
| P2-L | [重复给药与已见队列](p2l-repeated-dose-new-cohorts.md) | 原仓库研究文件 |
| P2-M | [跨研究早期曲线形状](p2m-cross-study-mechanistic-curves.md) | 原仓库研究文件 |
| P2-N | [快慢双输入与中央室卷积](p2n-transit-convolution-cross-study.md) | 原仓库研究文件 |
| P2-O | [慢尾结构不可辨识](p2o-tail-structural-nonidentifiability.md) | 原仓库研究文件 |
| P2-P | [候选额外采样点辨识条件](p2p-conditional-sampling-info.md) | 原仓库研究文件 |
| P2-Q | [未知基线/幅度/误差下模型稳健性](p2q-unknown-baseline-gain-noise-identifiability.md) | 原仓库研究文件 |
| P2-R | [分级证据与模型升级审查门槛](p2r-cross-study-evidence-upgrade-gates.md) | 原仓库研究文件 |

## 4. 按阶段逐一导航：完整历史研究（S–AC）

| 阶段 | 报告/研究内容 | 当前资料身份 |
|---|---|---|
| P2-S | [早期增长数学上界与测定方法](history-2026-10-10/reports/p2-s.md) | 原ZIP已在默认分支 |
| P2-T | [原始资料可得性与队列身份](history-2026-10-10/reports/p2-t.md) | 原ZIP已在默认分支 |
| P2-U | [跨研究群体点共同形状限制](history-2026-10-10/reports/p2-u.md) | 原ZIP已在默认分支 |
| P2-V | [连续机制模型族](history-2026-10-10/reports/p2-v.md) | 原ZIP已在默认分支 |
| P2-W | [重复给药与慢尾压力](history-2026-10-10/reports/p2-w.md) | 原ZIP已在默认分支 |
| P2-X | [40拟合与15个冻结M2候选](history-2026-10-10/reports/p2-x.md) | 原ZIP已在默认分支 |
| P2-Y | [模型族跨研究反证](history-2026-10-10/reports/p2-y.md) | 原ZIP已在默认分支 |
| P2-Z | [未知实际给药时钟](history-2026-10-10/reports/p2-z.md) | 原ZIP已在默认分支 |
| P2-AA | [预测函数量与跨研究误差补研](history-2026-10-10/reports/p2-aa.md) | **2026-10-10补研/非原件** |
| P2-AB | [Kotlin研究核数学不变量](history-2026-10-10/reports/p2-ab.md) | 来自工程集成审计 |
| P2-AC | [P2-X源到Kotlin一致性](history-2026-10-10/reports/p2-ac.md) | 来自工程集成审计 |

## 5. 按阶段逐一导航：中后期历史研究（AD–AT）

| 阶段 | 报告/研究内容 | 当前资料身份 |
|---|---|---|
| P2-AD | [背景与长尾辨识](history-2026-10-10/reports/p2-ad.md) | 原ZIP已在默认分支 |
| P2-AE | [快慢输入与基线自由度](history-2026-10-10/reports/p2-ae.md) | 原ZIP已在默认分支 |
| P2-AF | [慢吸收/消除联合不确定](history-2026-10-10/reports/p2-af.md) | 原ZIP已在默认分支 |
| P2-AG | [研究型M1/M2泛化与过拟合](history-2026-10-10/reports/p2-ag.md) | 原ZIP已在默认分支 |
| P2-AH | [Price图与表AUC计量口径](history-2026-10-10/reports/p2-ah.md) | 原ZIP已在默认分支 |
| P2-AI | [Price图像来源/剂量比例](history-2026-10-10/reports/p2-ai.md) | 原ZIP已在默认分支 |
| P2-AJ | [Price原刊1mg图重新取点](history-2026-10-10/reports/p2-aj.md) | 脱版权图ZIP已合并 |
| P2-AK | [Price低剂量读图区间](history-2026-10-10/reports/p2-ak.md) | 脱版权图ZIP已合并 |
| P2-AL | [模型选择条件与准入](history-2026-10-10/reports/p2-al.md) | 原ZIP已在默认分支 |
| P2-AM | [独立人体采样钟及验证方案](history-2026-10-10/reports/p2-am.md) | 原ZIP已在默认分支 |
| P2-AN | [测定误差/缺测的统计敏感性](history-2026-10-10/reports/p2-an.md) | 原ZIP已在默认分支 |
| P2-AO | [独立人体证据搜寻](history-2026-10-10/reports/p2-ao.md) | 原ZIP已在默认分支 |
| P2-AP | [OSF早期访问受阻的历史记录](history-2026-10-10/reports/p2-ap.md) | 原ZIP已在默认分支 |
| P2-AQ | [后来已获取OSF工作簿的隐私安全审计](history-2026-10-10/reports/p2-aq.md) | 原ZIP已在默认分支 |
| P2-AR | [历史遗失研究方向的解析补研](history-2026-10-10/reports/p2-ar.md) | **2026-10-10补研/非原件** |
| P2-AS | [正式Legacy E2_SL数学核审计](history-2026-10-10/reports/p2-as.md) | 原ZIP已在默认分支 |
| P2-AT | [正式Legacy与M2归一化形状对照](history-2026-10-10/reports/p2-at.md) | **原ZIP遗失/另有非原始重建** |

## 6. 后续独立延伸（不要同旧编号混淆）

| 阶段 | 新增研究内容 | 当前状态 |
|---|---|---|
| P2-AR-REAL | [VNC54真实配对访视与未知钟/基线](p2ar-real/P2-AR-REAL-REPORT.md) | 仅公开匿名聚合，源表不公开 |
| P2-AU | [VNC54背景份额/给药钟敏感性](p2au-background-profile/P2-AU-REPORT.md) | 仅公开匿名聚合，源表不公开 |
| P2-AV | [外部舌下E2来源去重与可用性准入](p2av-external-source-gate/P2-AV-REPORT.md) | 仅公开来源元数据 |
| P2-AW | [冻结核采样时点辨识/时钟压力](p2aw-identifiability-design/P2-AW-REPORT.md) | 模型内模拟，无人体新数据 |
| P2-AX | [独立证据字段契约与阴性对照](https://github.com/DevenirTwilight/HRT-Log/pull/14) | **PR#14 Draft，未合并** |

注意，**P2-AR 科学解析补研 ≠ PR #3 工程页面命名的 “P2-AR” ≠ P2-AR-REAL 真实私人 VNC54 复查**；这三件事不能互相代替。同样 **P2-AG 的研究型 M1** 不是生产 Legacy 模型，P2-AT 比的才是实际正式 Legacy 与冻结 M2。

## 7. 原始研究成果到底保住了多少？

参照 [历史来源哈希清单](history-2026-10-10/ARCHIVE_MANIFEST.md)、[真实 Git Blob ZIP 索引](history-2026-10-10/source-packages/README.md)、[研究上传状态](history-2026-10-10/P2-RESEARCH-UPLOAD-STATUS.md) 和 [归档政策](RESEARCH_ARCHIVAL_POLICY.md)，以下为截至本次查阅的**默认分支已合并状态**：

| 资产范围 | 现况 | 严格解释 |
|---|---|---|
| 24 个历史独立研究 ZIP 阶段 | **23/24** 已有实际 GitHub ZIP 对象 | 不是仅有 SHA/摘要；原内容对应阶段可下载 |
| 其中21个 | **原始 ZIP 字节一致** | S–Z、AD–AI、AL–AO、AP、AQ、AS |
| P2-AJ、P2-AK | **两个可再分发脱图版本** | 移除版权原刊扫描裁剪图；不与私有原包宣称字节一致 |
| P2-AT 历史 ZIP | **遗失** | 原件历史 SHA-256 `a636beee77525043a69a8393d88d03b20d9ee14adf2e29c6995960b192b4c14a`、大小390066字节，仅作为找回核对证据 |
| P2-AT 重新实现 | [新建34,698字节重建包](history-2026-10-10/reconstructed/README.md) | 8项新测试的复算成果，**不是找回原始AT文件** |
| P2-AA、科学 P2-AR | 2026-10-10 解析性补研 | 未找到原独立完整报告，保留 `BACKFILL_NOT_ORIGINAL`，不倒填历史 |
| P2-AB、AC | 对应 Kotlin 研究核及源到核审计 | 工程原生资料与研究报告，不伪称23个历史独立ZIP |
| VNC54 / TCRUW 私人 OSF Excel | **不在公开仓库** | 隐私保护；只留合法可公开聚合分析和源文件哈希；公开CI不能按私人个体记录复算 |

**关键修正：** [旧 ARCHIVE_MANIFEST](history-2026-10-10/ARCHIVE_MANIFEST.md) 的原正文里“这批ZIP不在GitHub”是归档前的历史快照；2026-10-11 已有实际ZIP默认分支合并。旧 [上传状态](history-2026-10-10/P2-RESEARCH-UPLOAD-STATUS.md) 还写“PR#7 未合并”，如今也是过期时间点描述。审计时要以原始证据、实际Git对象和最新状态说明为准，不能把历史句子误读为当前事实。

## 8. 哪些结论经过验证，哪些仍不能声称？

**已经成立（限定用途）：**
- P2-S 全仓回放已补齐早期隔离ZIP缺依赖，2026-10-11 [GitHub专项CI](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38123783828) 跑通 **11/11** 原测试并逐字节比对冻结JSON。
- P2-AT 重建可重复得到已见组均值的归一化形状对照：Price 8点 Legacy RMSE≈0.11665，15套M2≈0.00903–0.05313；Rosano 3点 Legacy≈0.29361，M2≈0.03885–0.10840。**这些不是独立外测RMSE，不是个人pg/mL误差**。
- P2-O/Q/AF 等以条件数学、合成扫描证实参数辨识与晚期长尾存在严重歧义；P2-AR-REAL/AU 对已见真实两点访视的相容性敏感性说明，任意背景/幅度可以改变模型名次。
- P2-AV 对所选 12 条公开记录去重为8队列，严格外测数据准入0；P2-AW 基于冻结模型对采样时间给出数学设计结果。这不等于世界上没有原始数据。

**禁止把以下内容写成已证明：** M2胜过Legacy的未来患者预测精度；Legacy因为默认安装就经过临床验证；P2-AT重建等于原件；匿名聚合CI等于可公开患者行级重跑；15候选范围等于人体90%预测区间；Price已证实发表错误；已经通过P2-C或允许根据模型自行改变激素用量。

## 9. 值得继续的待办，按研究价值排序

1. **证据可获得性**：优先合法申请 Doll 真正按给药后时间逐点的 E2 LC-MS/MS 汇总、Price逐人原时点/原作者AUC口径、Rosano PK25 真正基线与时钟；申请时同时要求制剂、剂量、LOD/LLOQ、缺失规则与复用许可。
2. **未见外部人群**：在任何新人体数值开封前锁定 Legacy/M2 参数、预测尺度和共同背景/幅度规则、时钟、评价功能量和事前失败界限。至少预先审定样本量与聚类统计精度，不能把反复使用过的已见组级数据重新命名为新外测。
3. **有证据才比较绝对曲线**：分别评价早期0–2h上升、2–8h下降、真实8–24h单剂尾部和重复给药稳态谷值；一律分开组均值形状吻合、个体绝对 pg/mL 准确与实验室测定误差。
4. **研究归档维护**：随每项新研究更新阶段索引、完整源码/测试/数值结果、许可证/隐私限制、远端 SHA 与交接文档，按照 [强制实物入库规则](RESEARCH_ARCHIVAL_POLICY.md)，禁止仅保存聊天里的 ZIP 摘要。

**当前科学边界：原模型和实验模型都没有足够独立人体外部验证来支持默认替换。P2-C HOLD。**
