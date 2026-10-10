# HRT Log P2-AP｜OSF 数据仓库文件可访问性与准入审计

日期：2026-10-10。独立科研工作；未修改 App/GitHub/PR。

## 结论

**两个 OSF DOI 的项目网页或 API 目前未能在本执行环境成功读取，因而未获得文件清单，也未取得任何个体浓度数据。** 不能据此断言仓库为空、项目不存在或数据不开放。P2-AO 的“可用独立外测=0”仍然成立，尚未提高到文件级核验。

| 项目 | DOI / 入口 | 当前资料性质 | 本次访问结果 | 用于 M2 外部验证？ |
| --- | --- | --- | --- | --- |
| VNC54 | https://doi.org/10.17605/OSF.IO/VNC54 | 2025 年低剂量口服/舌下 E2、六个月体成分变化论文声明关联数据集；与已知研究潜在暴露/重叠须核查 | 网页/OSF API 无法取得文件列表 | 否，未核验 |
| TCRUW | https://doi.org/10.17605/OSF.IO/TCRUW | NCT07145281 登记称公开匿名化个体激素/凝血及六个月随访数据 | 网页访问返回 403；API 无可读结果 | 否，未核验 |

## 主要证据与限制

- 临床登记：https://clinicaltrials.gov/study/NCT07145281 ，数据共享声明明确声称 OSF 已上传并开放。此声明不等于当前环境已成功取回文件。资料描述围绕六个月干预的激素、代谢和止血指标，不保证给药后 0.5/1/2/4/6/8/12/24 h 连续测量。
- VNC54 论文索引：https://www.researchgate.net/publication/389256441_Early_body_composition_changes_in_trans_women_on_low-dose_estradiol_comparing_oral_vs_sublingual_administration_using_dual_energy_absorptiometry_and_bioelectrical_impedance_analysis 。文章声明数据集 DOI，但主要结局为体成分而非 PK 时间曲线。
- OSF 官方 API：https://developer.osf.io/ 。常规公开节点经 `/v2/nodes/{id}/files/` 列存储提供方，再经提供方 `files` 链接枚举目录，可分页；可公开访问不必然意味着在所有网络环境可用。
- 本次在 web 网页通道对两个 osf.io 项目和 api.osf.io 文件列表的尝试未得到成功数据响应；容器在请求 api.osf.io 时发生 DNS/网络解析失败。注意 **web 工具侧不可访问与容器网络失败属于两种访问条件，不应解释成 OSF 服务全局故障**。

## 交付的文件清单检查器

`tools/osf_inventory.py`：仅访问官方公开 API 获取元数据，不下载研究参与者数据；有分页循环、最大页数、目录深度保护；不能访问时 fail-closed 并给出错误类别，而不是返回伪造空列表。

联网终端运行：

```bash
python3 tools/osf_inventory.py --out osf-inventory.json
python3 -m unittest discover -s tools -p 'test_*.py' -v
```

仅当 API 返回真实信息时 `metadata_verified` 才意味着文件元数据已核验。**它不意味着内容已审阅、许可已核实或具备外部验证资格。**

## 文件取得后的准入审计

1. 先独立核实授权、再确认是否是逐人、逐次、带实际时区的给药与血样记录。不得只依据 CSV 存在就宣布可验证。
2. 逐人 ID 用隐私保护的去标识编号；原始个人信息不上传至公开仓库，数据共享须符合原研究许可和适用伦理要求。
3. 必须包含剂量、路线、制剂、实际服药时间、含服时间/吞咽情况（如果可用）、采血实际时间、测量方法、单位、LOD/LOQ、预处理及基线；缺失就标记 unknown。
4. 明确药代时间序列，至少支持预定义的相对形状评估；只有月度随访/随机抽血的资料不是高密度 PK 数据。
5. 数据的研究中心、受试者交叉试验、时间区间、与既有训练和设计资料有无人员重叠，必须确认。研究已被 P2 预先接触时不能将它直接称为完全未见的外测。
6. 按 P2-AM / P2-AN 规则锁定预测、分组及缺失样本处理，先输出准入报告，再决定是否运行任何外测；不边测边调参数。

## 本次可验证的界限

发现的不是新的 M2 有效性证据，而是 OSF 检查流程与明确的访问失败证据。**没有观察到任何个体浓度、没有新的独立样本数、也没有模型性能改变。**