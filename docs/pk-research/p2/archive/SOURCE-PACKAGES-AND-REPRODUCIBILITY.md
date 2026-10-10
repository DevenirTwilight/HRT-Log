# 完整报告与原始研究包的保全范围

此 GitHub 提交保存了 P2-S..Z、AB/AC、AD..AQ、AS/AT 共 **26 份完整 Markdown 报告**及额外筛选的 **184 个研究源码/结构化结果文件**；原始 P2-R 已在既有仓库。

## 已在仓库的可运行资料

- P2-R 的正式方案、来源台账、门禁脚本和测试：见 `../p2r-*`、`tools/pk-research/p2r_evidence_gate.py`。
- P2-AB/AC 的完整原始科研核说明：本目录 `p2-ab.md`/`p2-ac.md`；对应 Kotlin 内核、JVM/Android 测试、P2-X 冻结候选 CSV 和 Python 一致性脚本见 [PR #1](https://github.com/DevenirTwilight/HRT-Log/pull/1)。
- M2 的 Android 实验页面与历史记录准入、四语言文案见 [PR #2](https://github.com/DevenirTwilight/HRT-Log/pull/2)（该 PR 的最新状态需单独核查）。

## 研究原始附件的处理

先前各阶段交付过独立 ZIP。本归档对其中**184 个已筛选的 UTF-8 Python、JSON 和 CSV 科研文件**做了只读提取，保留在 [repro/](repro/) 目录，覆盖 23 个有可验证原始 ZIP 的阶段。每个归档文件的原始 ZIP 成员名和 SHA-256、每项跳过原因见 [SELECTION-MANIFEST.json](repro/SELECTION-MANIFEST.json)。\n\n**完整原 ZIP、所有原始附件、绘图 PNG/PDF 和每个过大中间 JSON 仍未上传 GitHub**；也没有把隔离科研脚本接入 Android 或现有 CI。归档代码是否能在独立目录直接运行，取决于它原先所需的完整源 ZIP 与依赖，应以原包为准。

下列源 ZIP 的 SHA-256 用于离线核对原交付物（不是 GitHub 版本哈希）：

| 阶段 | 原 ZIP SHA-256 |
|---|---|
| S | `05e550d9e274883f8a3bd549af523ad77b8a4b66da7387d250949783efee855a` |
| T | `05c08e31001b2477c8e1aa9bc0f0157d913377986b83a0cca2e4a05749193791` |
| U | `ef41a7e96fd84f2fe99192eca8446878aa034d9a68dcf46af7b42596bc4291b0` |
| V | `2233f380bf1ede37618d026cb08e6ef0653f16aee65454d941e3f99d943e687b` |
| W | `1b864a70c3c680e5611e4bc5852bd8a852d6f3e48891f61be43f9d57f7ea02a9` |
| X | `f5ea61764512f14dea4b5f5ad8a4bdf42de1e2329b0a536879a6976e15b130a1` |
| Y | `82d2b89bf727e68cc6a6c4656dba24ae21746d3882b1cbce530425db13021dd2` |
| Z | `fc9b1ed1ab6d0f97355f558f6adc82b9c6fe40bcf901b7f0a11a9af944cbdabb` |
| AD | `98cb76b681c574bd82907c066afc00e92fbef39513ff2ef51dcc2d592801e5e3` |
| AE | `02e424853112bdc50c2cd6bd94105987d6f702f5cf6945711125b01bca4430e5` |
| AF | `e9259fbc23b77e89288ecd306517021747ac29ff051d3032c2ae926f0b4b29a3` |
| AG | `cab4776a4a97e2ffea9d1b926a9fa8bf53b8548eabba879ab560933fa09603de` |
| AH | `808ba28cae296aaaab42f1021853419a051cf182eed9ea8bc145eccc5e0fa220` |
| AI | `9f4243eacb725748f3ff0c8d70ec9bb164906b91194d15ad4e3e09f654b19561` |
| AJ | `7f8aa8a5549efc76cc59dfb4f0ca01b34909c434f785922ce7f3732248a104da` |
| AK | `d5b18787dfc5db377550fdc20275128eba7e6c7bc48b0097df258198fc0e5285` |
| AL | `32fcba10f92b54028ff48edddbf0c02a4df211cc2d238954da77dcc99960965d` |
| AM | `ac49fc262d742930fdf4b134ada018f38de80d10a384b3eaaa2f57f2779882a3` |
| AN | `965a3893b4a88b3a3441862dc76ee825bed4b8013ef9c1cf2e5b85c8f5c7f20b` |
| AO | `7ddf39cf4556c902fd23c4fde733c17afa0cf393a094ea2a4802ccb4e8119c99` |
| AP | `cc24c3aceb5270d7453d01ad150ae6dd5e17dc985e87a85df91b8ca838904300` |
| AQ | `657d6eb1df8cb694c6e03a321862ae610ea150b13e810a9cf3d9986fbfb6e63a` |
| AS | `f88b6acdda3e4de27dc1fb0fb8d0b62b2e51ac0b2d85347f3219eb4656662b40` |

P2-AT 原 ZIP 在此前文件库中存在，但这份归档没有假定它在当前容器有相同字节可供哈希核验，因此不捏造校验值。P2-AB/AC 主要源见 PR #1。

## 未随公开仓库再分发的材料

- 用户提供的 **TCRUW/VNC54 原始逐人 Excel/ZIP**：即使去掉直接姓名，仍含健康/问卷记录、重复访视和可能的准标识符；只有匿名聚合统计写入 P2-AQ 报告。
- **Price 1997 原期刊 PDF、图页扫描和其他非自行创作的受版权约束全文/图像**：报告保留必要方法、出处、数量和数据解释，不上载原件。
- 邮件联系方式、个人 Gmail、研究参与者身份、个人医疗数据和本地密钥/签名文件均不包含。

## 复现与后续改进

1. 仓库内有完整报告供阅读；若要重运行某项离线 Python 数值研究，需要取回该阶段用户已收到的同哈希 ZIP。
2. 对 ZIP 的新增代码先做输入/许可证/依赖检查；禁止把真实人体原始表或期刊版权文件加入公开仓库。
3. 将来可在**另一独立、经用户授权的工程 PR**中把科学测试代码和经过审查的衍生公开数据逐步移入仓库，并保持冻结基线和审计可追溯。
4. 不因归档报告而降低原有 `check_protocol.py` 的冻结强度、删除旧文档或重写生产参数。

**归档范围的真实表述是：全部可核实阶段的完整 Markdown 报告、184 个经过筛选的 Python/JSON/CSV 文件、现有仓库/PR 中的研究计算核及带哈希的原包溯源；不包含所有旧 ZIP 的大体积中间输出、期刊原图或原始个体健康记录。**
