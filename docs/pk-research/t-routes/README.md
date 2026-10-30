# HRT Log 性激素各剂型曲线 — 自动研究入库规则

> 本目录是 **2026-10-30 起新剂型 T/E2 曲线研究**的非冻结索引。不要修改 P2 研究已冻结文件，也不要把本研究误认为产品已启用的浓度曲线。
>
> **负责人长期指令：研究成果自动、实时入 GitHub 仓库。** 一轮研究完成时要同步提交来源、原始证据（允许公布的非个人数据）、数值结果、曲线生成代码、测试、局限和交接索引，随后回复具体 commit / PR；**只有聊天附件/本地 ZIP/图或只写一份报告不算入库完成。**

## 本轮已经保存的源文件

| 文件 | 作用 | 地位 |
|---|---|---|
| [跨剂型证据总表](../sex-steroid-route-coverage-20261030.md) | E2/T 剂型全覆盖与官方 RCP 来源 | 研究 |
| [T-01 官方参数约束报告](../t-route-fit-01-report.md) | ANDROGEL、ANDROTARDYL、NEBIDO 三个非同质制剂的条件模型、残差和不确定性 | 研究 |
| [T-02 人体采样数据报告](../t-route-fit-02-ichihara-2020.md) | 日本跨性别男性 250 mg TE 肌注 n=10，8 个时间点，5 拟合、2 留出，非独立验证 | 研究 |
| [8 时点人体群体均值、SD 和预测 CSV](ichihara_2020_points_and_fit.csv) | 可直接核对的人体公开文献数值及候选曲线对照（无个人数据） | 原始组均值 + 模型结果 |
| [机器结果总账](results-manifest-20261030.json) | 拟合摘要、残差、源代码对应关系和先前本地 ZIP/CSV/PNG 的 SHA-256 校验值 | 研究产物索引 |
| [人体 8 时点曲线 SVG](ichihara_2020_comparison.svg) | 入库可直接查看的实验均值与两个候选模型图 | 可视化，不是验证 |
| [ANDROGEL 凝胶候选曲线 SVG](androgel_50_illustrative.svg) | 三组吸收速率假设对曲线影响 | 示意，未验证 |
| [ANDROTARDYL 庚酸睾酮 SVG](androtardyl_illustrative.svg) | 不同基线假设的峰值和尾部 | 示意，未验证 |
| [NEBIDO 十一酸睾酮 SVG](nebido_illustrative.svg) | 研究用固定基线混合储库候选曲线 | 示意，未验证 |

## 已提交的可重现程序

- [三制剂数学模型](../../../tools/pk-research/t_route_calibration_01.py) 与 [独立测试](../../../tools/pk-research/test_t_route_calibration_01.py)；生成 `results.json` 和完整 `illustrative_curves.csv`。
- [Ichihara 2020 数据拟合](../../../tools/pk-research/t_ichihara_2020_fit.py) 与 [独立测试](../../../tools/pk-research/test_t_ichihara_2020_fit.py)；生成可机读拟合结果。
- [重建可视化及表格](../../../tools/pk-research/export_t_route_figures.py)：从仓库中的曲线模型生成 SVG，亦可选用 matplotlib 生成 PNG。不依赖本地 ZIP 的存在。

```sh
python3 tools/pk-research/t_route_calibration_01.py /tmp/hrt-t-fit
python3 tools/pk-research/t_ichihara_2020_fit.py /tmp/hrt-t-ichihara.json
python3 tools/pk-research/export_t_route_figures.py /tmp/hrt-t-fit
python3 -m unittest discover -s tools/pk-research -p 'test_t_*fit.py'
```

生成的高密度 CSV/PNG 属于从已入库源代码**可重建产物**；历史本地原件的哈希在 `results-manifest-20261030.json`，**此处不谎称未提交的 PNG/大型 CSV 已逐字节进入 Git**。未来产物应优先入仓可移植 CSV/JSON/SVG、来源和测试。若某项证据不能提交（权限/版权/大小/隐私），必须写出缺口、理由及复现方法，不能声称已全部入库。

## 新研究自动入库验收清单

1. 来源元数据：官方说明书或原论文 DOI/页码/剂量/制剂/人群/检测方法/采样时间/统计口径。不得将不同人群的群体均值拼成某个人的曲线。
2. 源数据：公开可分发的原始组统计，机器可读 CSV/JSON。包含证据许可与身份；禁止加入真实用户的用药、化验或私密附件。
3. 算法/数学：参数与单位、基线假设、对照模型、训练/留出切分、数值误差及可复现运行入口。
4. 输出：明确 `RESEARCH_ONLY`、`NONIDENTIFIED`、`NOT_VALIDATED` 等状态；每个拟合保存残差和失败约束。
5. 验证：提交单测和可在 CI 执行的研究重建脚本；区分 `LOCAL_PASS`、`CI_PASS` 和 `NOT_RUN`。
6. 交接：更新本索引，给出 commit SHA/PR；生产 PK/UI/历史/用户数据另行评估和批准，不暗中自动接入。

**当前结果：** 三种 T 制剂的研究代码已提交；庚酸睾酮有公开 8 时点拟合，留出两点但尚无独立研究人群交叉验证。ANDROGEL/NEBIDO 仍仅有官方稀疏摘要约束，因此不能标记为稳定的绝对血药浓度模型。
