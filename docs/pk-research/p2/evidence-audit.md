# P2 原始证据审计

七研究复用并重新核对P0必要摘要；逐数值位置见evidence-catalog.json records.source_location。Doll、Price、Casper、Pines、Burnier仍仅原始摘要，不能声称取得完整浓度表。Cortez本轮Europe PMC全文XML已取；Yaish重核P0全文HTML，fresh PMC是浏览器挑战（HTTP200不是全文），Europe PMC拒绝；缓存哈希写入source-manifest。全文不再分发。

Doll摘要含10人、采样0/1/2/3/4/6/8、LC-MS/MS144/35和AUC比例1.8；绝对AUC、基线扣除、含服时间未知。Price的4/12/24采样只明确E1S，不能补为E2；Casper41-fold仅within30min，不能造精确30min点。Pines1759±704是固定60min均值，±类型和检测法未知。Crossref两种DOI均同题作者，保留别名，不宣布旧DOI无效。

Yaish90min1994±1457pmol/L（统计方法SD）、范围229–4736；这是治疗期间固定点，不是真实Cmax。入组SL11不代表确认90min分析分母；unknown保持null。原治疗谷204.5、两个段落SD63.3/63.5保留差异，不能当内源基线。Cortez表3有95.3±10.5/79.4±11.6；6.2mg/day是研究期平均，不是精确采样日方案，表与图SD/SE语义不统一，不拟合。

Burnier单次组5人，0.5mg1h26-fold、24h回24pg/mL；不是完整尾曲线，24不能假设每个体基线。其余5人隔日组独立描述不冒充单次。旧模型0.24%是拟合剩余，不是测量吞咽。没有图形数字化、没有个体数据、没有追加人体采样。

检索原始请求/失败/网页等级见source-manifest；临时下载正文不进Git。新增检索线索筛除Doll重复会议摘要、评论、不同分析物/输入不足的记录；并非穷尽检索。外测不足不会由数学测试“转绿”。
