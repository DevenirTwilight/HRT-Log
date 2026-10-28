# ANSM-04 / ANSM-06 — 正式只读资料

基线：PR #140 / `32e3a4223bd0b9921e8f860751a21f841a8e1dd3`。首个独立提交 `3e6947df` 先确立全项目 [UI_STYLE_AUTHORITY](../../design/UI_STYLE_AUTHORITY.md)，随后才开发新产品界面。继承 UI-11，不创建第二个药品中心、用户数据库或授权系统。

## 当前生产实现与入口

药品资料中心 → 查找商品资料 → 搜索（如 PROVAMES）→ 展开官方产品 → 官方资料与文档。已保存的官方关联详情也可打开按 CIS 查询的当前公共资料，历史关联快照不会被重写。标题/摘要行默认折叠；正文为官方法语原文，界面翻译不冒充官方临床译文。

| 功能 | 实际能力与边界 |
|---|---|
| 处方发药条件 | 全部 CPD 条件逐条保留；无条目保持未知，不推断无需处方。原 9 个单字段“异常行”经原字节核实为空白分隔行，raw 行审计保留9，不造临床条件。 |
| 仿制药分组 | 原分组 ID/标签、类型0/1/2/4、所有成员 CIS 与可核实的名称。当前 GENER 引用37个不在本批身份文件中的 CIS：保留未知身份和原分组事实，不猜名称或替换药物。可查看已知同组药品当前详情；不等于个人可互换结论。 |
| HAS | 所有 SMR/ASMR 原等级、原因、日期、正文及通过 dossier 连接的官方评估网址。PROVAMES CT-21206 的 SMR 等级“Modéré”与正文“reste important”原样分别保留，不擅自统一。不是个人疗效或安全评分。 |
| MITM / 加强监测 | MITM 原 ATC、名称、持有人、经营者、发布日期；独立显示 CIS 的原 Oui/Non 监测字段。无值 UNKNOWN，不把任一类别变成个人危险结论。 |
| RCP / Notice | 逐产品正式页面 GET、精确名称+CIS 路径匹配、正文哈希和时间取证后，提供浏览器入口。420个产品页面核对通过，其中382有独立RCP/Notice标签，11有 EMA 合并产品 PDF；其余显示未提供/未核对，不猜另一个产品网址。浏览器打开后需选择相应官方标签；不承诺片段自动切换网站标签。 |
| RCP / Notice 离线全文 | **未打包**。本轮没有独立确认完整网页临床正文的再分发范围/正文版本合同，不将下载文件开放许可自动套用于所有网站正文。网页取证仅保存哈希、身份、日期和链接，不保存或生成说明书全文；官网访问不等于离线全文。 |
| 重要安全信息 | CIS 精确关联、原法语文本、开始/结束日期和安全校验后的官方链接。源文件动态生成时间未提供，显示快照下载日期与“生成日期未提供”，不冒充发布时间、实时风险或完整召回。 |
| 供应 | 原 CIS/CIP、代码及原标签、开始/公告更新/恢复供应日期、官方链接；区分所有包装与指定 CIP，历史日期限制随 v5 说明保留。无行不等于正常，恢复供应不等于药房有货。 |
| 全量召回 | **未证实覆盖，不开启完整监测**。保留不可用说明，并提供本轮实际 GET 200 核对的 ANSM 官方安全公告网页查询入口；重要信息文件不是全量召回源。 |

中心原 RCP、Notice、安全、供应四项现在可点击进入真实产品选择；召回保留准确的未实现状态。现有 BDPM 身份/成分/包装搜索、用户来源、关联/解除、剂量/计划/库存均继承；没有自动提醒、换药、库存事件或 PK 行为。

## 数据与来源

完整来源及文件自身日期、编码、字节、行数、SHA-256：
- [`tools/bdpm/official-sources/manifest.json`](../../../tools/bdpm/official-sources/manifest.json)（原字节 gzip 同目录，全部正式生产主机）。
- [`document-pages.json`](../../../tools/bdpm/official-sources/document-pages.json)：逐产品核对证据；未核对不暴露构造文档链接。
- [`official-navigation.json`](../../../tools/bdpm/official-sources/official-navigation.json)：ANSM 查询入口的真实标题与 HTTP 响应体哈希。
- 运行时 [`official-manifest.json`](../../../core/data/src/main/assets/bdpm/official-manifest.json) 与独立只读 `official.sqlite`；Android 在 IO 上验证哈希、复制并只读查询，不进入 SQLCipher 用户库。

本批各来源日期：身份/成分/CPD/GENER/SMR/ASMR/HAS链接 **2026-09-29**；包装 **2026-10-28**；供应 **2026-10-27**；MITM **2026-10-13**；重要信息无可靠生成日期，实际下载 **2026-10-28**。不是所有数据都“今天更新”。

仍为明确成分代码筛选的 HRT 相关目录，不是全法国或全球药品目录。保留原 420 产品/702 成分；包装550→544，逐行差异见 `package-update-audit.json`。身份/成分原始文件 SHA 与旧批相同；不能据包装缺行认定停产或无供应。旧 TEST 来源的 manifest 和包装原字节保存在 `tools/bdpm/archive/`；旧历史记录依原来源显示 TEST 警示，不静默升级为生产证据。

## 许可核实

2026-10-28 正式下载页面明确允许 reproduire / diffuser / redistribuer / exploiter，要求不歪曲数据、标明 BDPM 来源与更新日期，不暗示 ANSM/HAS/UNCAM 认可。该页仍链接 Licence Ouverte **1.0** PDF（实际文本自述版本1.0，SHA `44ff23ae2b5cce0d729d1b53b20d17aae625189b131c2dd9cb98d1fe0d0a2a90`）；页脚另链2.0。两者不偷换，实际采用下载合同及页面明示条件，保留原文件和两种链接的证据。此公开数据不改为项目 MIT；应用明确来源、每文件日期、法语原文/界面翻译区别、离线非实时和无背书。旧 OWNER_BLOCKED 是历史审批状态，不能覆盖本轮明确授权和本次实际许可事实。完整 RCP/Notice 正文的独立许可/完整性检查没有冒充完成。

## 更新与兼容

维护者显式运行：

```sh
python3 scripts/bdpm_official_details.py --download --pages --build
python3 -m unittest discover -s scripts -p 'test_bdpm*.py'
```

离线重建：不带 `--download --pages`，只加 `--build`。原始压缩文件逐个验哈希、列数/编码/CIS/CIP；失败直接拒绝，不能替换已安装数据或污染用户库。公开资产随正式应用版本更新；没有后台网络或未审查的用户数据集导入器。查询保留旧 public cache 的哈希文件名，不原地更改私人数据。新旧原始快照和生成清单支持维护者回退，不宣称老应用能理解任何未来新数据。

现有 `ReferencePayload` 格式不变；唯一必要持久化兼容改动是允许两个**精确**的下载源地址（历史 TEST、正式生产），未扩大为任意域名。旧关联与旧备份仍可读；新生产关联的地址在旧版“仅 TEST”校验器中会被拒绝。**不得承诺向旧应用降级可读或删库解决**；恢复前仍须完整校验，沿用所有原事务/历史保护。没有 Room22、没有修改 PNBAK1/PNMEDBK1/PNMEDBK2 加密封装。

## 验证与视觉

- Python 原目录7项、新资料8项（完整来源/多行/分组成员/四种供应/文档/CIS/HTML与链接/破坏数据集失败保留原输出/确定重建）实际执行，最终结果以日志/精确 PR SHA 为准。
- 核心原 BDPM 合同保持原断言；追加生产资料/来源等级矛盾/日期/未知身份/官方白名单/公开缓存损坏修复。TEST 地址回读与恶意来源 URL 拒绝仍验证。首轮生产地址被旧规则拒绝的真实 XML 和日志保留在 `evidence/`。
- 原 Controller、ReferenceFlow、关联/解除与备份回归不弱化；UI-11 断言只将已实际实现四项迁移为可点击，完整召回继续无动作/OFF。
- 新 UI 组件32组：四语 × 320/411dp × 1/2倍字体 × 明暗，公开 PROVAMES 原始字段和确认/取消/准确 ACTION_VIEW URI，实际本地通过。截图是合成宿主中的生产组件，不冒充 MainActivity/真机。
- 新增实际解锁 MainActivity → 药品中心 → RCP入口 → PROVAMES → CPD/官方网页确认取消，复用三 API 工作流；尚未执行前标 PENDING，最终以 PR 原生 XML 为准。所有未来外部网页动态访问不作永久可用保证。
- [UI-11 基线 before/after](../../ui-audit/2026-10-28-ui11/)与本目录截图对照：保留 hero、分组/药丸/返回，不把全部字段摊在首页，不恢复药物/库存捷径。新增资料为白卡内默认折叠入口及摘要行；外部浏览器确认是唯一新增必要警示弹窗。人工 TalkBack、真机、独立外审 NOT_RUN/NOT_REVIEWED_EXTERNALLY；不冒充更高层认证。

本轮不签名、交付 APK 或创建 Release。APK 实测大小及最终 PR/CI/SHA 收据在 PR 正文，不能拿局部编译冒充原生验收。

## #142 初次最终原生及实际体积收据

`f27d0364` 的新增 MainActivity 正式入口合同在 API26/29/35 均实际 PASS。同批原有中心导航合同仍要求四个已实现模块没有点击动作，因此三个作业 FAIL；保留 XML/API35完整日志。最小修正只把四项改为验证实际产品选择窗口、返回和解锁会话，召回仍 OFF、文件 Intent 仍必须0；新候选的整体结果须等待其自己的最终 CI，不能以此前子项 PASS 代替。

[实际 unsigned APK 对比](evidence/unsigned-apk-size-receipt.json)：同环境 clean #140 `32e3a422` 14,062,172 bytes → 生产实现 `f27d0364` 14,286,553 bytes，增加224,381 bytes（约219KiB）。两者均通过 DEX 源码身份核对；当前包的正式身份、无 INTERNET、附件与 ANSM 试验代码隔离实际 PASS。没有签名/交付。后继仅测试/证据修订不能冒充这份 APK 的来源；最终全部 CI/合并收据见 [PR #142](https://github.com/DevenirTwilight/HRT-Log/pull/142)。
