# M2单模型整合、兼容职责与独立验收（2026-10-12）

本轮从PR19 OPEN / b46dbf6增量修改，不合并PR1/2/3/17/18，不覆盖独立研究；默认分支与各PR的真实元数据/初始SHA见m2-only-evidence/start-state.json。上次正式签署包生产470b194，hash426bb468…；用户最新“要交付apk”已记§66，授权在完整验收后沿用原正式证书私下交付本轮APK；不公开发布。当前文件、源码SHA与全部结果需按本报告最终验收及HANDOFF判定，不能沿用旧CI冒称新任务通过。

## 调用链与根因

实际产品调用链：Room/repository→NotesViewModel.extra→AppShell.CONCENTRATION→UnifiedConcentrationScreen→ResearchHistoricalSlAdapter（不可变快照/实际时间/剂量，180天公开窗口）→ConcentrationPreview→PreparedResearchHistory（原冻结核等价求值）→ResearchDisplayProtocol固定p2x-39→PriceFigure1997加权幅度。原ConcentrationScreen负责原卡片/ConcChart/ChartViewport，绘图与点击均使用preview真实函数。

原并行兼容链：NotesViewModel.conc→ConcentrationCalculator.VERSION2→Engine/PkParams/LabFit→ConcentrationResult；其他药物、其他E2途径与旧化验快照仍依赖该链。M2字段不会读取其currentPgMl/calibration/bands。模型选择的持久化状态完全删除，普通界面不再选择M1/Legacy，不增加隐藏设置或第二套药代页面。

用户提到的CommonCalibration(25,100)/列表first属于历史版本。开工实际ConcentrationPreview已经使用ResearchDisplayProtocol固定p2x-39，Price B24/A434.835722…，经过上轮峰值与速度修复。科研比较工具UnifiedModelComparison仍保存人为共同25/100情景，但没有产品调用者；其研究测试/比较历史保留，不把研究示例接回产品。ConcentrationModelComparison/ResearchM1Catalog/M1结果完整保留。

## 候选审查及固定代表

P2-V主搜索n8；扩展阶数n9约0.03322与n10约0.03349相近，不能解释为人体分室数。P2-X对暴露研究拟合后冻结40行/保留15条；p2x-14的伪损失0.104881…，p2x-39为0.033486963…，两者的背景/慢尾/幅度不相同。P2-AZ重新设计216双输入网格，54个相关敏感性折/仅3个研究，M2对M1有48/54较优和6个Rosano反例；P2-BA再按研究分组给出12/18情景三研究同时优于M1。原P2-X15已见各留出研究，不能把重新排序包装成盲法验证。

因此保持现有明确版本p2x-39作为**可追溯冻结研究代表**：最低已见P2-X伪损失是公开可复算的代表选择规则，软件稳定性和明确来源支持保留；不是证明唯一临床赢家，不在本次UI退役时更换至P2-V连续版本或P2-AZ新格点。研究不确定性与M1反例不删除。独立m2_only_reference.py评估全部15冻结M2、P2-V主/扩展候选和216个AZ双输入候选的同B24 Price投影/早期/峰值/AUC/尾部/重复给药指标；新投影仅本轮比较，不覆盖原P2-V/AZ幅度或训练结果。

版本p2x-m2-research-v0.1 / price-figure-weighted-history-v1，参数n10、kf16.33057668138752 h⁻¹、ke1.382325006479187 h⁻¹、ks0.2616420808381402 h⁻¹、w0.5311770316375933。运行时完整参数SHA256：76467b35b1d032058e272f600cbd580d0777d82266fe3740613e23f4d81c3a20。冻结CSV数值序列SHA baedffcd…与运行时序列的既有末位文本舍入不同；原product-lock已分别记录二者。本轮新测试最初错误地直接比较两种文本SHA而失败，已保留失败XML并按来源区分精确文本身份，独立断言数值一致；没有修改冻结数据/模型/浮点数值断言或降低测试要求。

## 标定及数值

Price1997 Figure1，1mg SL，6名绝经后女性，人工读取组均值；按冻结规则A=HᵀW(y−B)/(HᵀWH)，W=diag[1/max(readWidth,0.15y)²]。B24是假设背景，不是个人给药前实测；A=434.8357220077087 pg/mL（1mg/1h增量）。C=B+AΣDiH(t-ti)，背景一次，原事件时间/剂量不改，未来只有已给药尾部，不编造计划剂量。

独立SciPy gamma、全局括号搜索与quad；Kotlin原核/细化峰值和密集积分交叉检查。1mg Cmax481.924256/Tmax0.854839h；2mg939.848512/同Tmax。增量AUC0–4/8/24分别732.529858/879.123884/955.227114（2mg乘2）；包含背景总AUC分别828.529858/1071.123884/1531.227114，2mg1561.059716/1950.247767/2486.454227。单位pg·h/mL。1mg 1h458.835722、2h205.381445、8h44.236208、12h31.098445、24h24.307328 pg/mL；转换系数3.671。1h不是峰，图表插入细化峰点；单剂峰与包含既往残留的最新剂量+24h窗口峰分开。完整233候选比较、1/2mg与背景敏感性在independent-reference.json。

Price Figure假设0前值AUC1557.5与Table2109±1031统计口径/数字冲突保留；8–24h慢尾、重复给药与个人幅度没有临床认证。

## UI与兼容边界

原卡片/7、14、60天/样式/缩放/平移/精确点击/单位/历史修复/化验观察/其他药物保持。主卡明确“M2文献参考浓度”、图表“舌下E2参考曲线”，化验标签明确未用于M2拟合；不显示旧SL概率带为M2区间。M2隐藏旧SL校准结果/开关与SL模型参数，保留原风格的小校准说明和化验入口；体重/兼容参数仅明确标为其他途径/药物。无大型参数编辑/AUC卡片/研究面板。

混合其他E2或未知记录：**M2只显示已合格SL分量，合并总E2明确不可用**。没有统一个人总E2输出，其他途径贡献保留在原内部兼容链，不把其值换名M2。纯其他途径且兼容结果没有SL分量：原曲线和限定范围校准可见。兼容结果含SL（包括计划SL）不能作为M2或独立其他途径曲线显示。未知/不完整SL历史给出明确不可用/修复提示，不回退旧SL。SL EV不套M2，原Unsupported保留。M2失败显示错误/重试，其他药物仍可见；加载状态隔离旧读数，完成后保持原范围。

## Legacy保留的必要职责和删除条件

Engine/PkParams：口服E2/EV、贴片释放与移除、凝胶产品、IM EV、CPA、SPI母药/坎利酮、孕酮，以及SL兼容核/吞咽口服分量；LabFit：原E2资格/因果/回顾校准和旧核数值；ConcentrationCalculator：共享时间/缺字段、计划/实际/其他药物、历史版本及校准结果；LabEstimate→LabContext不可变化验浓度/参数快照，版本1/2与备份/导出/PDF回放仍依赖它们。NotesViewModel、LabsScreen、HistoricalContextDialog、其他屏幕时间轴也使用计算器的公用接口。删除Engine或PkParams会破坏上述职责，本轮不删除、不改变模型版本或数据库。

正式个人SL迁移仍未完成：需要另获批准的固定绝对预测/个人校准算法与独立误差/尾部/重复给药证据、旧模型快照/备份版本可回放、其他途径完整隔离及全量兼容验收。只有没有上述调用者后，才删除旧SL实现。当前产品入口退役完成不代表底层生产或临床迁移完成。

## 精确协议与验收

本轮用户明确授权原页最小显示接线/校准隔离，真实AppShell/ConcentrationScreen旧新SHA/最小diff保存m2-only-ui-authorization.json及m2-only-ui.patch，追加在旧F1/统一页/原UI链后；检查器仅接受两文件的新精确记录。旧锁/基线/偏离记录完整保留，任意字段伪造/乱序/额外一字节/其他保护文件变化均拒绝；不扩大通用豁免。

七类错误共九次真实源码变异均由已执行断言击杀，逐项XML/日志判定/恢复SHA在m2-only-evidence/mutations；包含实际Legacy LabFit幅度注入及SL-only伪称Total E2。源码完全恢复后重新全量正向验收。全量/设备/Debug/Release/lint/体积及GitHub最终结果见m2-only-evidence/results.json；未执行或环境不可用标NOT_RUN/BLOCKED。没有读取用户健康记录。
