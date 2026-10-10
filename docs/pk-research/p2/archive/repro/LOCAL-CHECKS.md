# P2 归档源文件的本地与 GitHub 对照检查（2026-10-10）

本验收只判断**归档内容和 Python 语法**，不验证临床预测准确性、不构成所有科研脚本在 GitHub CI 完整复现成功。

## 结果

- 23 份已有独立研究 ZIP 经白名单筛出 **184 个 UTF-8 源码/JSON/CSV**，均位于 `repro/<phase>/...`。
- 基于原始 ZIP 逐字节创建 SHA-256 文件清单，所筛的 **184/184 个文件重构后哈希一致**。
- **97 个 Python 文件**逐个执行 Python `compile(..., 'exec')` 语法检查：**97 通过，0 SyntaxError**。注意该操作**不执行导入、数值运算或研究单元测试**。
- GitHub 树 `db75c7a7dddfd76105200adfb2ee744575535a18` 验证：归档原有 **26 份完整阶段报告**，源数据与支持文件 187 个，全部位于新 `docs/pk-research/p2/archive/` 目录。树中无 `.xlsx`、`.pdf`、`.zip` 原件。
- GitHub 原生 Blob 抽样精确相等：P2-S Python 源、P2-X 条件候选 JSON、P2-AQ 审计 Python、P2-AS Legacy 审计 Python，四项逐字比对通过。
- P2-R 的现有原文及检查代码保留；未修改生产 `pk-params.json`、`Engine.kt`、科研冻结文件或 Claude 的 PR。

## 现阶段不能声称的内容

- 没有重跑每份历史 ZIP 内全部数值测试；已有阶段报告记录的旧测试次数不得误算成此次归档新 CI 结果。
- 部分后期研究的大 JSON（例如 P2-AE 辨识性数值扫描）超过本次 100KB 单文件归档阈值：具体逐文件排除原因见 `SELECTION-MANIFEST.json`。
- 原始人体逐人 Excel、版权论文扫描、PNG 图像和部分生成性大中间文件未包含；所有临床外测门槛仍未放行。
- P2-AT 的报告在仓库，但其代码 ZIP 暂无可验证的原始文件字节，不捏造源码。

建议在完整仓库 Checkout 中执行：

```sh
python3 docs/pk-research/p2/archive/repro/verify_archived_sources.py
```

程序会复核 184 个研究文件的 SHA-256；其结果仅为**版本完整性检查**。
