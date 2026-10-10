# P2 离线科研程序及结构化结果归档

本目录源于历史独立研究 ZIP，包含 **184 个逐文件筛选的 UTF-8 Python、JSON 和 CSV 文件**，涉及 P2-S 至 P2-AS（仅有真实科研包的 23 个阶段）。目录按阶段小写名称划分，每个阶段保留原 ZIP 内部层级，**避免同名实验文件覆盖**。

- [文件级 SHA-256、原始 ZIP 文件名及跳过清单](SELECTION-MANIFEST.json)
- [标准库完整性校验工具](verify_archived_sources.py)
- [完整研究报告索引](../README.md)
- [临床证据和发布状态](../EVIDENCE-AND-RELEASE-STATUS.md)

**限制：** 所有文件作为历史科研产物冻结归档，而不是正式生产代码。这里没有复制原 ZIP 的全部依赖、PNG、版权论文扫描或体积巨大的中间 JSON。旧脚本中提到的相对路径可能需要恢复原始 ZIP 目录结构后才能运行，部分 `verify_manifest.py` 仅在完整原包中有意义。不能声称该归档的每个旧脚本都已经被 GitHub CI 复算。

本目录从不包含 TCRUW/VNC54 原始逐人 Excel；P2-AQ 只含匿名化汇总检查及代码，不包含逐人原始测量。它不会修改 `pk-engine`、`ConcentrationCalculator`、`AndroidManifest` 或受保护科研协议。

目前 **P2-AT 报告**已经单独存档，但其原始源码 ZIP 无法在当前运行环境中取得可验证字节，因此不虚构对应代码文件。P2-AA/P2-AR 也没有可核实的独立完整研究包。

运行：`python3 docs/pk-research/p2/archive/repro/verify_archived_sources.py` —— 仅检验本目录全部 184 个已纳入文本文件的 SHA-256，**不宣称验证人体药代模型准确度**。
