# ANSM-02A：合成目录与只读详情交接（2026-10-16）

**实现状态：SYNTHETIC_UI_IMPLEMENTATION_CI_VERIFIED / PRODUCTION_INTEGRATION_BLOCKED。**
这是可运行、可测试的debug原型，不是正式药品目录、用户绑定、医疗资料核实或发布批准。
本轮[要求摘要](requirements.md)、[兼容设计/审批包](compatibility-design.md)、
[验收边界与复现命令](manual-checklist.md)、[本地机器证据](local-verification.json)、[实现SHA的CI证据](ci-code-evidence.json)。

## 基线、前置条件与协作

开工默认分支 `claude/new-session-1959qb`，HEAD `2ab7a0c84aab47d38fb84d90520e67d86b8a5de1`。
独立分支 `feature/ansm02a-synthetic-catalog-20261016` 从该主线创建，未叠在PR45上。
设计提交d2a32df的Android38843986141/Science38843986147已全部成功，仅证明设计阶段同基线工程检查。

过程中PR46由其他执行者合并，最新集成基线
`a3146dfd63057a563b57f7967e2de857de486d90`（仅交付文档变动）；已正常merge纳入本分支，
未改写其历史或签名/交付内容。该主线Android38844599030、Science38844599073全部success。
原2ab主线Android38809226303 attempt2 failure仍是历史事实（API26红对照首次失败，重跑下载失败）；
没有修改断言、重跑其检查或将它改称成功。主线后续新SHA成功不抹去此前故障。

PR45仍OPEN / unmerged，HEAD `348c68def84ccc627a46be57ace463ade3635a15`，无评论/评审/批准。
其最终Android push38811588310 / PR38811596671(attempt2)、Science38811588320/38811596603 success。
未修改其冻结数据，也未复制 `docs/reference/fr` 到本分支或Android。

PR47（UI-04）正在改正式日历、字符串、测试及共享REQUIREMENTS/HANDOFF；历史科研PR和PR34仍独立。
故本轮只改独立debug资源/包/测试、发布隔离守卫和此阶段目录；不编辑共享进度或正式UI文件。
本轮要求先记于requirements.md，后续由集成负责人加入共享索引，避免抢占§101或覆盖他人交接。

## 资料问题结论

[重新获取的官方文件证据](source-review.json)：下载页仍链接Etalab2.0；许可PDF仍写1.0，
格式PDF仍写ST，而成分文件仍有SA/FT。两个PDF哈希与PR45一致；未找到正式处理结论或
负责人对适用条款、日期及分发范围的确认。FT未改标ST，官方资料不改标MIT。
此核查不是法律意见、临床审阅或监管背书；不复制说明书正文或生成医学摘要。

正式Android目录打包、真实链接与用户绑定继续阻断。可做的纯合成工程工作不依赖正式数据分发。

## 已实现与明确未实现

全部新界面和数据在 `app/src/debug/`：

- 固定synthetic-v1本地JSON，8个虚构商品；`SYNTH-CIS-*`/`SYNTH-CIP-*`不能冒充有效CIS/CIP。
  包含同名不同身份、多成分/关联治疗部分、多规格/包装、多途径、未知规格、归档/撤销/失效状态。
- 离线加载、失败拒绝；商品与成分重音/大小写兼容搜索，剂型/原始规格/途径交集筛选。
- 只读详情显示原名/成分角色与分母/剂型/途径/合成身份与包装、独立授权/销售声明、未知供应、
  来源/日期/版本。声明销售不表示当前可买到；未知不变成正常供应。
- RCP/Notice分别显示入口观察与正文未核实；按钮仅显示合成提示，无真实URL、Intent或后台网络。
- 独立Mock编辑、确认、取消/返回、重复选择/解除、无结果/失败/空目录手动回退。
  仅独立UI引用预览，不预填或更改用户输入的名称/剂量，不创建真实药物或写业务备份。
  UI采用rememberSaveable，系统可能恢复合成草稿；跨进程/配置恢复专项NOT_RUN，勿输入真实健康资料。
- 四语、既有Material3主题/token、可滚动长文本、独立输入标签、heading、明确48dp按钮。

宿主非导出、无launcher、无正式导航入口；不接NotesRepository/MedicationDraft/PK/库存。
正式手动创建路径完全未改。DTO/JSON仅为合成交互合同；不能移除横幅后直接接生产。
真实资产适配、实际RCP/Notice打开、正式用户关联及持久化测试均 **NOT_IMPLEMENTED**。

正式02B方案提出不可变官方快照、追加关联/解除/更正事件、新记录显式捕获引用；
旧记录不回填，旧容器身份保持未知。候选Room/备份迁移、CAS、旧PNBAK恢复/删除更正语义
与风险矩阵见兼容设计，全部等待另行审批；本批未新增任何生产字段或表。

## 自动证据与不变性

本地最终命令：新增114测试（数据9、交互8、资产宿主1、布局96）、PeriodStability5、
现有翻译2，共121，0失败/错误/跳过；全部scripts108通过。Lint成功，0错误、185项警告，
新目录文件0项。详见机器证据。
384张Native Robolectric截图：四语×320/411dp×1/2××明暗×三对比度×四视图。
完整图随既有CI build-results归档；本目录保留8张代表图，完整文件哈希在机器报告。

首次本地执行的97个新增测试失败、测量诊断及修正均记于机器证据：成分搜索预期修正；
Native Paragraph溢出标记误报，改用与原隐私验收相同的实际字形/字符/省略号检查；
发现不足48dp的可视语义按钮后修复新UI。没有改动、跳过或削弱任何既有断言。

相对a314基线，正式main/full、core、pk-engine、importer、Gradle与CI路径的Git树完全一致，
机器报告保留tree/blob标识。Room10/旧v2历史快照/PNBAK1业务schema9/账本与VOIDED/
计划和预测/锁与伪装/PK与PDF/版本包名不改。正式APK隔离守卫新增目录代码、资源、资产
拒绝项；保留全部原有检查，负向注入测试通过。实际Release/No INTERNET和完整兼容回归
还必须以本PR最终HEAD的六项必需CI为准，不借用先前SHA或Mock结果。

实现SHA `0129df3020caa995bbbfe42f92db49cbcb87a22e` 的push Android38846525419 / Science38846525473、
PR Android38846586349 / Science38846586350全部success，均attempt1，未取消/重跑。
下载报告校验和/CRC已核对且无APK：JVM262（domain63/PK124/importer12/POC63），
app1272/0fail/173原skip、data180/reminder19；新目录114与PeriodStability5均0fail/skip。
API35 data137、app264（8原skip）；API26/29各data70+app70正常套件0fail/skip；
独立红对照按预期失败且修复路径成功，不混同正常测试。No INTERNET、实际unsigned Release
目录隔离、PNBAK1/VOIDED/撤销及新PID恢复日志均已核验。CI生成384张图与本地哈希全部相同。
这些数字只归属于实现SHA；此后的交接文档HEAD仍须完整CI，不能借用旧绿灯。

最终SHA、完整CI结论和报告下载证据固定在本PR正文/checks，避免为记录自身SHA循环提交。
不自动合并，不签名，不交付APK，不创建Release。工程成功不代表资料或临床/发布验收。

## 代表截图

|语言|411dp/1×/浅色/标准对比度 · 详情|320dp/2×/深色/高对比度 · 确认|
|---|---|---|
|English|[detail](screenshots/en_411_1.0_false_STANDARD_detail.png)|[confirm](screenshots/en_320_2.0_true_HIGH_confirm.png)|
|Français|[détail](screenshots/fr-rFR_411_1.0_false_STANDARD_detail.png)|[confirmation](screenshots/fr-rFR_320_2.0_true_HIGH_confirm.png)|
|简体中文|[详情](screenshots/zh-rCN_411_1.0_false_STANDARD_detail.png)|[确认](screenshots/zh-rCN_320_2.0_true_HIGH_confirm.png)|
|繁體中文|[詳情](screenshots/zh-rTW_411_1.0_false_STANDARD_detail.png)|[確認](screenshots/zh-rTW_320_2.0_true_HIGH_confirm.png)|

## 文件范围与后续门

- `app/src/debug/java/.../catalog/{SyntheticCatalog,CatalogPrototypeScreen}.kt`：DTO/加载/检索及原型。
- `app/src/debug/assets/ansm02-synthetic.json`：原创合成数据；debug Manifest：非导出宿主。
- `app/src/debug/res/{values,values-fr,values-zh,values-b+zh+Hant}/ansm_catalog.xml`：独立四语。
- `app/src/test/java/.../catalog/{SyntheticCatalogTest,CatalogPrototypeUiTest}.kt`：新合同及渲染。
- `scripts/test_ansm02_boundary.py`、`check_attachment_release_isolation.py`：合成/四语/生产隔离。
- `docs/ansm02/**`：需求、来源证据、兼容设计、机器报告、人工缺口与代表图。

真机、人工TalkBack、OEM输入法/Launcher与实际网页恢复 **NOT_RUN**；不以自动语义代替。
02B依赖01合并/主线全绿、许可与数据范围负责人确认、DTO/快照/迁移/备份方案独立批准、
并行文件冲突解除及真正历史/库存/备份不变性测试。ANSM-03还需独立量纲与包装转换规格、
容器单位/历史包装快照方案及库存授权，不能由本次Mock或CIP标签推导消耗或可用容量。
