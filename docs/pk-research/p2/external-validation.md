# 按研究条件比较：没有锁定外测，也没有总体赢家

全部输入及统计/出处见冻结catalog；原始数值不是待验证模型输出。HRT用既有人口核，Featherline只固定公开数字/独立ODE、条件80kg，A/B用预先固定canonical形状和Doll单点幅度。未知基线0/25/50是预定工程情景，未另拟任何外部研究专属基线。四模型详细误差见results/study-comparisons.csv。禁止跨方法合成分数。

| 研究与实际定义 | 观察 | HRT | Featherline80kg | A默认 | B默认 | 可比性与失败 |
|---|---:|---:|---:|---:|---:|---|
| Doll TRAIN，1mg/1h LC-MS/MS | 144pg/mL | 144.024 | 480.759 | 144 | 144 | 条件基线0；候选训练匹配不是外部预测效度 |
| Pines DESIGN_EXPOSED，4mg/固定60min | 1759pg/mL | 576.095 | 1923.035 | 576 | 576 | ±704类型/检测/基线未知，4倍剂量只是线性外推；A/B在此未改善 |
| Yaish DESIGN_EXPOSED，0.5mg/固定90min | 1994pmol/L=543.176pg/mL | 66.477 | 272.964 | 66.321 | 68.620 | 免疫法SD1457、实际历史未知；仅合成q6h30天条件，不能声称准确恢复受试者史 |
| Burnier DESIGN_EXPOSED，0.5mg/1h | 26-fold | 4.000 | 11.016 | 4.000 | 4.000 | 条件基线24pg/mL，原论文fold保持fold，不将推算绝对值包装实测 |

B默认在Yaish仅略改变，其他形状可给出43.78–866.29pg/mL，包括更近点和更差点；没有外测信息支持挑选更近档。A情景14.62–263.63仍未匹配Yaish固定点；Pines所有A/B情景426–576，均低于1759，反映训练点加线性剂量假设的局限。Featherline在Pines更接近但Doll高约3.34倍、Yaish/Burnier又偏低，不能按914/278选赢家。不能断言均值差是个体模型错误，也不能以免疫偏差假设直接修正倍率。

Price无取得数值PK、Casper仅within30min且无基线绝对值、Cortez无采样日精确剂量/间隔/历史，均不做误差拟合。Burnier24h返基线仅支持小队列定性尾判断，未验证0–24曲线。Doll1h为最大已采样值，不能与连续模型Tmax做“精度认证”；AUC比例1.8不作为绝对AUC目标，旧390.65代理仅留历史。

TRAIN1、DESIGN_EXPOSED3、QUALITATIVE3、LOCKED_EXTERNAL0、NOT_COMPARABLE0（按整研究；某些记录只能定性，不改研究分割）。七项研究不是十三个独立研究；密集生成点另标synthetic_only。独立研究存在不等于输入可匹配，且P0已见不能标盲法。

结论 **external_validation_insufficient**，`clinical_accuracy_established=false`。算术正确和所有软件CI通过均不能改变该科学状态。
