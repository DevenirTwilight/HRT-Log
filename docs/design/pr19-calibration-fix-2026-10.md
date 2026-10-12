# PR #19：标定、候选与真实峰值修复

状态：应用源码 `6c663e0f62e32cdacb1836574781823a9ea76ddc` 已提交原分支并通过完整CI；本地最终异步测试等待修正后全量/构建验收完成。原 UI 不重新设计，本轮不签名、不交付、不公开发布 APK。下述数值由独立 Python/SciPy 程序复算，未把用户提供的参考数硬编码进测试或显示。

## 实际 GitHub 状态与依赖

| PR | 当前头提交 | 实际角色 |
|---|---|---|
| #1 | c0b9b6c53461610ea8c8460e610f1a1ed9aadf60 | 冻结 P2-X M2 数学核、15候选/40拟合来源 |
| #2 | cd56688e36c4dccaceab4be814773c562ef862cc | 独立实验页，未合并，未重新引入 |
| #3 | 4b264187b08f23ae4ef88a10715e29500d60ce29 | Price anchor 与共享比较科研组件，部分已复用 |
| #17 | 82c32a7fee993f8937e0f65f66bc0629956c46ce | P2-AZ：32 M1 / 216 M2，已见三研究留一形状比较 |
| #18 | 68ceac2717b487029d894a76f4185cdf1f890d35 | 基于 #17 的 P2-BA 用途/证据分级 |
| #19 | 起点8216f309c0d883cc7b54bcd391f1351a1dc043ca | 原血药浓度 UI + 模型选择，当前修复对象 |

以上均 open、未合并。默认分支 `claude/new-session-1959qb` 为 f53726ff6fa52fc5c78806a1576fdb80fcf29987。没有盲合并任何研究分支。P2-AZ结果独立取自固定 #17 SHA，归档 blob `38fb3ccc4521f8f0d683d2c755c0370539831ed9` 与原文件完全一致，不把复写摘要当原结果。

## 十项调用链回答

源码位置以本轮提交为准，旧行为以起点8216f309为准：

1. 旧主页面 M2 是 `ConcentrationPreview.kt:17` 的 `ResearchSublingualV01.candidates.first()`，即 p2x-14。旧核列表按40源表保留行的顺序排列，**不是排序后的准确性排名**。
2. p2x-14 的选用只有数组首项依据，不能称研究最佳或人体最佳。P2-X保留阈值为已见伪损失最小值+0.10，是探索性容差，非概率区间。
3. M1 同样来自 `.first()`，即 p2az-m1-00，n1/kf4/ke0.4/w0。它是 P2-AZ 32套格点之一，非 P2-V 的连续优化 M1。
4. `ResearchSublingualV01.kt:144` 定义 H(t)=h(t)/h(1h)，`relativeHistory:166` 只累加给药时间不晚于 t 的 mg×H。归一到1h，不是归一到峰。
5. 旧 preview `assumptions=CommonCalibration(25,100)`，随后 `assumptions.concentration(relativeHistory)`；B/A人为示例直接进入原主卡片/图表。新 preview `exact:29` 调用 `studyScenario`，经 `ResearchDisplayProtocol.anchor`→`PriceFigure1997.anchor/amplitude`。
6. 旧主页面没有使用 Price 加权幅度。已有 `StudyAnchors.kt:65` 的 amplitude 确实实现冻结 P2-X：ΣwH(y−B)/ΣwH²，w=1/max(读图宽度,0.15×y)²；每候选 B 为原假设。新主调用已接入此实现。原 `UnifiedModelComparison.kt:67` STUDY 是另一条共同1h幅度协议 A=450−B，**没有进入当前原 UI**，已有文字明确它非各候选加权重建；继续作为独立形状科研工具保留，不冒称本轮主页面协议。
7. 旧 M2 低于 Legacy 因 A被换成人为100，而非 Price 拟合约440；不能据此证明 M2低峰，更不能依据新高峰声称更准确。
8. 旧局部0.5/0.75/1h等网格会漏掉真实峰。新 `ResearchExtrema` 对所有括号局部极值细化，比较端点；单剂0–24h、一分钟搜索与五分钟搜索交叉收敛。图表额外插入单剂参考峰、剂间叠加局部峰及最新剂量24h窗口峰。`ConcChart.kt:130`/`ChartViewport.samples:8`保留全部可见采样点，无另外抽稀；点击 `ConcentrationScreen.kt:115` 调用 preview.valueAt 的真实同协议函数，非网格插值。
9. M1/M2 不读取化验/Legacy Scale/LabFit。`UnifiedConcentrationScreen` 的 labs只交由原页面显示；Legacy原结果仍归 `ConcentrationCalculator`/`Engine`/校准卡片所有。新增真实原 UI 开关、化验变更与重新选择测试阻止研究幅度被污染。研究选择说明明确下方校准/参数仅Legacy。图上化验点为实测总E2，不能当仅SL曲线的标定点。
10. 只读 `ResearchHistoricalSlAdapter` 使用实际ON_TIME/LATE、非删除、已服时间、正有限mg、记录/规则冻结快照、纯SL E2；180天记录范围，按时间叠加/B仅一次，无计划给药，不估计含服档或体重效应。非SL/其他药物不纳入研究总值，混合途径明确“仅SL、非总E2”。没合格SL而有其他记录退回Legacy；空记录不编造主卡片数值。旧比较工具自身的30天lookback没有套到新 preview；既有截断前残留/不完整历史并未获得个人验证。

AppShell:176→UnifiedConcentrationScreen→ConcentrationPreview→原 ConcentrationScreen:94/101/115→原ConcChart；数据库/NotesViewModel历史→只读adapter。原Legacy另一路 NotesViewModel.loadConcentration:91→Calculator.compute→Engine/LabFit。研究M1/M2共享进入页面时的asOf；Legacy保留自身loadConcentration快照时钟，在校准重载后可能略晚，不能把三个主卡片在不同刷新时刻的数当精确同时比较；数值验收统一单剂t0/同时间轴。任意点击给定绝对小时使用当前模型真实核。

## 代表选择与参数锁定

M2 `p2x-m2-research-v0.1 / p2x-39`：冻结P2-X 40套中最低已见三研究等权伪损失（0.03348696307362355，ID为平局规则）。保留15候选原顺序和源码，不再用first。

M1 `p2az-m1-grid-v1 / p2az-m1-09`：固定 #17 54个敏感性折中未惩罚M1选择众数28次，平局ID升序。其余选择是m1-20 20次、m1-31 4次、m1-30 2次；这只有3个已见研究，不是54个独立试验。此规则取可解释研究代表，不能称独立验证唯一赢家，也不以峰值最大作选择。

| 候选 | n | kf / ke / ks (h⁻¹) | w | 版本/条件 |
|---|---:|---|---:|---|
| M2 p2x-39 | 10 | 16.33057668138752 / 1.382325006479187 / 0.2616420808381402 | 0.5311770316375933 | Price B24，Rosano cap225；原P2-X |
| 旧M2 p2x-14 | 8 | 12.31990565827258 / 1.449638446625054 / 0.1937751830647134 | 0.5699014723612454 | Price B18，Rosano cap100；损失0.1048814342880041 |
| M1 p2az-m1-09 | 3 | 4 / 1.2 / 0.2（ks未使用） | 0 | P2-AZ格点；**新** Price projection v1，假设B24 |
| 旧M1 p2az-m1-00 | 1 | 4 / 0.4 / 0.2（未使用） | 0 | 数组首项，无拟合赢家依据 |
| P2-V M1（未采用） | 8 | 14.424207816930702 / 0.5006048829941504 / 无 | 0 | 独立连续优化，原Price A308.47544485764786/B24 |
| P2-V M2（未采用） | 9 | 14.440199238807665 / 1.3518070625675727 / 0.249970121099137 | 0.5139380956563052 | 扩展n≤16连续扫描，原Price A436.3038462642318/B24 |

P2-V精确参数来自原source ZIP内 p2v-candidate-parameters.json。P2-AZ216套M2格点用于留一重选，**不是当前P2-X15候选**。P2-BA把敏感性折归为3真实研究：M2在18/18情景三研究同时优于Legacy、12/18同时优于M1，保留Rosano反例；这些是暴露群体相对形状证据，无新盲法个人绝对验证。研究代表选择目标不相同，不能用本页峰高宣称公平临床准确性排序；新M1/M2幅度拟合则严格使用相同Price点、权重、B24和时钟。

M1独立幅度协议 `p2az-m1-price-projection-v1`：保持原格点形状不变，B24为与代表M2相同的**显式假设**，使用现有Price加权算式拟合A。这是新条件映射，不冒称P2-AZ已有绝对浓度拟合。总协议 `price-figure-weighted-history-v1`。

运行时参数SHA-256：M2 `76467b35b1d032058e272f600cbd580d0777d82266fe3740613e23f4d81c3a20`；M1 `ad7a7ab74d93fc3c94d18b785a0ecb23f2e690f96427b10ea2243c588c63c24d`。签名字段与协议独立锁在 [product-lock.json](pr19-calibration-evidence/product-lock.json)。独立CSV参数保留更长原十进制，个别值与原Kotlin在最后1 ULP不同，所以CSV签名哈希与运行时哈希不同；不改冻结源，数值一致性按1e-8浓度验证，来源/版本分别锁定。全部47候选的参数、峰高/时刻、8/24h响应、基线0/12/24固定形状幅度敏感性见 [reference.json](pr19-calibration-evidence/reference.json)，不是参数生理置信区间。

## 独立单次1mg复算

无前次剂量、Legacy默认含服档2、70kg、无个人校准。Cmax为0–24h数值极值。AUC单位pg·h/mL；增量=扣各协议**假设**背景，括号为含背景总面积。不同来源/人群/测定条件，不能当同证据等级或个人准确性比较。

| 模型 | 候选版本 | 标定来源 | 1mg Cmax pg/mL | Tmax min | AUC0–24增量（总） |
|---|---|---|---:|---:|---:|
| Legacy | 0.1.0-literature-draft / E2_SL+吞咽E2_ORAL，tier2 | 原Doll相关群体拟合，B0 | 144.023808 | 60.008201 | 392.609636（392.609636） |
| M1 | p2az-m1-grid-v1 / m1-09；projection-v1 | Price Figure1新条件映射，B24/A454.306268 | 478.607264 | 61.381591 | 812.627804（1388.627804） |
| M2 | p2x-m2-research-v0.1 / p2x-39 | 冻结Price Figure1加权幅度，B24/A434.835722 | 481.924256 | 51.290341 | 955.227114（1531.227114） |

Legacy非严格60.000min，是实际完整SL含微小吞咽口服部分的峰，不能只复算黏膜项。新M2在1h为458.835722，新M1为478.306268；都不是Cmax，也不强制等于Price人工1h点450，因为拟合全部9点。

旧审计重现：p2x-14人为B25/A100为127.250288、Tmax54.073522min；其真实Price拟合B18/A440.821975为468.741741，相同Tmax，增量AUC1060.800608。m1-00人为协议为131.876166、38.376419min；这不代表M1正式人口模型。人造示例只留在明确共同条件科研工具，**不再作为产品默认**。

Price图梯形（假设0h为0）1557.5，与Table1扣治疗前均值2109±1031仍冲突。M2 frozen1201点梯形955.220987与独立quad955.227114相差0.006127，来自积分分辨率，原冻结参考没有被改写或强制追表。当前M2尾H(8)=0.0465376/H(24)=0.000706767，p2x-14为0.0655409/0.00294980；M1-09 H(24)=1.94424e-12，m1-20=0.000103745，尾部差异很大。冻结15集合跨基线24h约95倍归一化谷值范围仍不是人体90%预测区间。

## 实际执行验收与限制

- 应用源码6c663e0f：GitHub Android/JVM/API35 CI [38171086963](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38171086963) 三任务success；独立参考重放 [38171086905](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38171086905) success。JVM domain58/PK118/importer12、Android data72/reminder14均0失败/跳过；app422零失败13跳过（12可选native图审，1既有native PDF）。API35 data14零失败/跳过，app69零失败2既有restart-harness跳过；浓度仪器5/5实际执行通过。归档SHA和解析计数在 evidence/ci-artifact-hashes.json、ci-test-counts.json。
- 最终本地全量重新执行426，0失败/错误、1既有native PDF跳过；32张**本轮**native PNG，中文/英文/繁中/法文×320/411×字体1/2×原浓度/含选择器页面。磁盘另8张旧图不计本轮。Debug、unsigned Release、AndroidTest编译、lint同一轮成功8m21s；lint0错误172既有警告。最窄中文字体2截图保留原卡片/范围/图表；原时间轴端点裁切属于原布局限制，不宣称已修复。
- 保留真实失败：首次数值积分网格超过原0.002 AUC误差，改到0.0005h未降低断言后PK118通过；首次本地全426有1缩放后异步读数时序失败，加waitForIdle/waitUntil后专项1及全426通过，原数值/存在性断言保留。详见validation-history.json和local-final-validation.json。
- Python科研269实际通过，四语占位符/模型source-lock/check_protocol实际通过；源码四类变异均被真实执行断言拦截并逐字节恢复：默认换25/100、候选换first、去精细峰点、化验幅度污染。不是仅源码字符串检测；结果mutations.json保留失败证据。
- SciPy独立10秒全局括号+局部优化/quad与Kotlin一分钟/五分钟峰收敛覆盖47候选。显式峰点数据误差≤1e-5 pg/mL，pmol/L乘3.671；像素精度、裁切边界直线插值不属于此保证，点击始终真实核。单次1/2mg、重复/近邻、不同候选/背景、非法值、Legacy校准隔离、真Room历史读取且无写回、旋转/恢复/手势均覆盖。
- 实际product preview设备性能：60剂/30天，10442点，592.292139ms，进程PSS220053→221721KiB；历史窗口峰487.857304697，图表最高点相同。另一旧比较科研基准2001点/2830.620061ms单独归档，不混作产品基准。无物理ARM、FPS、Release运行性能或新原生PDF验收结论；本地无KVM，设备结果来自GitHub API35 x86_64模拟器。

## APK、源码和资源体积

同工具链、本轮unsigned Release与修复前原UI版本对照：12,736,319→12,741,571 B，**+5,252 B（+0.04124%）**。完整ZIP CRC通过，4ABI/8原生库内容逐字节相同，406条ZIP成员不变；apksigner verify预期失败Missing META-INF/MANIFEST.MF，未正式签名。前SHA c145fb953f5c6ae0ab1963fff0bce758f65f8699cc3135f53e7ad9fe7ea39b3a，后SHA91ac845fecaa3e1e0f451b22f9e47064f0de6e59204d5192f735b9bfd321b6fb。原APK来源此前原UI源码425a7ab（起点8216为文档续接、相同应用源码），新应用源码6c663e0f，后续仅测试等待/文档变化；不是同源码重复构建或已签名包比较。

修改生产源码文件集合前5281→后7902 B（+2621），新增PK显示协议/极值源码3869 B；四语修改资源22023→24122 B（+2099）。具体文件/bytes见source-size.json；APK ZIP构成见[apk-size-audit.md](pr19-calibration-evidence/apk-size-audit.md)。本轮没有删除任何组件；旧独立实验页/抽屉入口此前已移除且继续保持不存在，科研比较/证据和Legacy全保留。

全部实际变更文件见[modified-files.txt](pr19-calibration-evidence/modified-files.txt)，与任务起点8216比较；报告本身及交接/需求更新也列入。构建命令保留于validation-commands.txt；所有保护文件、协议链和生产参数相对起点无差异。

## 工程边界

无新增页面/入口：唯一CONCENTRATION→原ConcentrationScreen，模型选择附于原布局。删除旧实验入口的历史成果保留；本轮不删除科研组件或Legacy核。原ConcentrationScreen、AppShell、Engine、Calculator、ConcChart、ChartViewport、pk-params、协议/deviation/baseline/lock和原授权patch全部字节不变，无新保护偏离或待批准代码。四语科研说明更新、新数学显示契约和独立数值/测试/CI证据在保护文件之外。

仍缺合法独立未见的完整个体人体序列、个体幅度/基线标定、测定/剂型/含服时钟/真实历史元数据、公平预冻结选模、重复给药与8–24h尾部验证。此缺口阻止正式M2个人pg/mL生产替换，**没有停止本轮标定修复**。Legacy临床准确性也未被确立，未宣称是可靠金标准。
