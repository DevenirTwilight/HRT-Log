# P1-B：逐条化验历史资格门控

2026-10-09，起始b9a5393a7435accd3afe02407b38709a0e40f179，§47授权。状态：生产实施与本地完整验收通过，API35 CI待核验。Build25/0.2.0/schema9不变，人口参数和P1-A算法2不变。P1-C/P2不在本轮实施。

## 编码前核实的生产链路

`NotesViewModel.loadConcentration → repo.transaction → NotesDao.records()/labs()/rules() → Dispatchers.Default ConcentrationCalculator.compute → LabFit.bands/fit/lastDiagnostics → ConcState → ConcentrationScreen/CalibrationCard`。

DAO records SQL读取全部未删除记录，无时间LIMIT；Repository未先截断。Calculator只保留taken_utc≥now−180天，仍把全部E2化验送LabFit，导致范围前化验成为首个可见事件之前的假基线。原始观测独立用于图层。u/v仅内存重算，无持久化拟合参数。

`saveLab/rebuildLabContext（用户显式选择）→ LabEstimate.capture`独立读取DAO全记录后过滤采样时刻前180天，调用compute(calibrate=false,labs=[])。已有calculator_version2/envelope1，validator读1/2拒绝未知版本。该路径不拟合化验，本轮不改变其捕获输入/格式/版本或既有冻结内容；PDF/Visit Pack/备份读取冻结事实，不调用动态拟合覆盖它们。

## 资格语义

纯逻辑层每条E2化验输出覆盖COMPLETE/INCOMPLETE/UNKNOWN、冻结配置KNOWN/INCOMPLETE/UNKNOWN、基线NOT_PRE_TREATMENT/UNKNOWN（当前生产无确认机制，不产生CONFIRMED_PRE_TREATMENT）、拟合ELIGIBLE/EXCLUDED/NEEDS_REVIEW、结构原因和证据记录/范围。COMPLETE仅针对已保存且该模型所需的输入，不声称掌握全部现实用药或一生治疗史。

调用者必须显式提供“全量已保存记录”及读取时点元数据；默认未声明的输入不能当完整。无实际E2前史、首次记录、epoch/regimen开始、导入首条均不能确认治疗前。LabFit改为仅在调用方显式提供已确认治疗前ID时使用基线，默认不从事件最小时间推断；生产传空集合。

有效的实际ON_TIME/LATE、未删除、taken瞬时、实际剂量、冻结历史配置/历史rule补全为证据。当前药物/profile不能补过去。冻结成分明确非E2的缺失不关闭E2；成分未知的实际给药可能是E2，不能当不存在。混合暴露有任何相关未知输入，整条观测不参与拟合。未来计划不能证明过去历史。

## PK窗口与保守残余规则

180天只保持现有显示/模拟预算，不是零残余或治疗开始证明。采样在模拟窗口外则排除。窗口内相关未知配置/剂量/时间不全则排除；窗口前未知路线无法证明无影响，继续保守待审查。

对窗口前已知舌下E2，可利用P1-A完整核速率固定、共同幅度的性质，用每个指数绝对贡献和的解析上界与该采样时刻窗口内舌下贡献比较；只有累计遗漏上界/已含舌下贡献≤1e-6才能不阻断拟合。对数域计算避免下溢被误作真正零；这是相对于固定数学核的工程误差预算，不是人体PK证明，且对任何共同幅度仍成立。没有正的已含舌下贡献则不放行。

窗口前非舌下E2（含口服/注射/贴片）的个体速率或佩戴历史不能由此SL证明约束，保守待审查，不假定180天后归零。未来可单独设计模型特定的有界历史加载与误差预算，不在本轮放宽先验或改人口模型。未知暴露不能因年龄大自动忽略。

不增加DAO查询、不扩大模拟网格到无限历史。冻结配置每记录解析一次，资格索引排序一次；相关未知按最早阻断点判断。解析残余工作有预算，超过返回NEEDS_REVIEW/RESOURCE_LIMIT，保留观测和人口曲线。大历史/取消/资源边界验收需覆盖实际代码。

## 单一数据选择与验收

资格结果统一决定bands、当前曲线、summary、diagnostics所使用的化验子集；所有合法原始点继续绘制，每条显示未参与或待审查原因。没有合格点时不返回个人校准摘要、不添加常数基线，人口中心/参数区间与关闭校准一致。离群计数另外标明P1-C尚未一致。

转换500/220假基线特征化断言，旧P0/P1-A文件只读保留；新机器结果命名sublingual-p1b-*。至少两合格正例、子集/单位/开关、冻结途径/更正/时区、未知进口/非E2/混合/窗口/长效/资源、显式基线API和冻结备份/PDF回归。完整JVM/Android/Python/lint/full构建及API35 CI日志/产物回查后再标完成；数值和工程验收不代表人体临床准确性。

## 实施与验收边界

见 [验收记录](sublingual-p1b-verification.md) 和 [机器报告](results/sublingual-p1b-report.md)。新资格计算残余预算200000、相关事实预算50000；超限待审查，不改观测。现有DAO全量读取保留，不承诺数据库无限历史内存有界。取消从Default协程传递，逐记录/逐化验检查。四语逐条原因只解释拟合资格，不暴露数据库主键。摘要为空时既有模式选择仍显示，避免门控导致原有交互消失；不改其样式或保存逻辑。
