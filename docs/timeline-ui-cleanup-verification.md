# 时间线精简验收（2026-10-08，REQUIREMENTS §42）

起始 b40b7706561f84d7491b066d82bc3e85cec26298；应用变更 6bb8fac2e46cc931f955e19a5f2a003a20b5c21e，后续只有审计工具/证据/文档改动。Build 25/schema 9，不生成新正式签名交付、不发布 APK。

## 显示变更与数据边界

- 正式菜单移除复制合并诊断及四语专用文案；MergeDiagnostics 移到 test 源码，原诊断/合并/稳定性回归保留，生产合并判断未动。
- 原始记录入口改为更多菜单“查看用药记录”，不展示数量。原筛选和传给 History 的记录 ID 完全相同；历史页仍可查看全部记录。
- 常驻方案审计按钮移入更多菜单“方案详情”，继续展示原方案内容与变化；去掉内部版本 ID/原 UTC 字符串，使用本地日期和时间。化验事件详情也去掉内部方案主键映射文本，冻结上下文仍显示。
- 去掉卡片重复“我修改过的”标记；模型、修订和化验上下文中的来源信息保留。真正由记录识别的方案使用简洁“由用药记录推定”，不将推断伪装成用户声明。旧重建/频率不确定提示保留。
- 起止日期、药物/方案、必要变化、创建/编辑/删除及删除确认保留；精简模式仍能通过菜单查看用药记录及编辑删除。
- AppShell、VisitsScreen、日历、复诊数据/问题清单/PDF、时期引擎、历史归属/标签、统计导出、Schema 与 PK 未修改；没有迁移。冻结事实不变性由 TimelineV2FlowTest 继续验证。

## 本机可核实结果

| 检查 | 结果 |
|---|---|
| 修改前诊断/稳定性基线 | UserDiagnosticRegressionTest、SameCardSplitTest、PeriodStabilityTest 通过 |
| 全量 app 单测 | 295 登记、282 通过、13 跳过、0 失败；跳过含可选审计默认配置与既有 JVM PDF 条件 |
| 数据/提醒单测 | data 72、reminder 14，全部通过 |
| 常规布局回归 | 64 项全部通过；其中新时间线菜单16项，四语×320/411dp×浅/深×字号2 |
| 时期与冻结历史 | TimelineV2Test 6、TimelineV2FlowTest 6、PeriodStabilityTest 5、TimelineEditFlowTest 4 通过 |
| 入口回归 | ImportedTimelineUiTest 11、TimelineEditUiTest 2 通过；原始记录 ID、原短暂3mg方案、编辑删除及诊断不存在仍断言 |
| Lint | 0 错误、131 警告，未做无关警告清理 |
| 构建 | Full Debug/Release、app/data instrumentation APK 构建成功；Release manifest 无 INTERNET |
| 四语资源/文档 | XML 合法且无重复名称；需求、交接、Roadmap 和设计均明确未实施/保留复诊；Schema/PLAN/复诊源码无漂移 |

实际日志在仓库外 `/workspace/tooling/timeline-cleanup-final.log`、`timeline-menu-focused.log`、`timeline-cleanup-jvm.log` 和 `timeline-audit-final-<语言>.log`。首轮旧长推断文案触发两条旧 UI 断言，改为独立简洁文案后最终全量通过。新布局测试曾因法文字形的小数像素高度信号失败，改用与既有回归一致的实际行边界、省略/遗漏及触控尺寸断言，16项最终通过，未放宽到允许真实截断。

## 时间线专项审计

四语 × 字号1/1.3/2 × 宽320/411dp × LIGHT/DARK/HIGH = **72 配置**，每配置 timeline、timeline_menu、timeline_details 三项，共 **216 渲染**；严重裁切、重叠、越窗、零尺寸/触控丢失、动作失败和测量失败均0。机器汇总见 [JSON](ui-audit/2026-10-08-timeline-cleanup-summary.json)。

首次专项审计发现工具只采集主窗口/对话框，漏掉 Compose Popup；菜单没有被测量，详情动作报错，不能算通过。补上 WindowInspector 弹出窗口根与截图采集后，四批 Gradle 重跑全部成功；汇总校验每配置三项覆盖以及详情对话框截图存在，不以 Gradle 成功代替发现核查。审计总 case 数由48增为50，上一轮1152渲染的历史证据仍保留。

合成截图：[卡片之前](ui-audit/2026-10-08-timeline-before.png)、[卡片之后](ui-audit/2026-10-08-timeline-after.png)、[窄屏大字体菜单](ui-audit/2026-10-08-timeline-menu.png)、[窄屏大字体详情](ui-audit/2026-10-08-timeline-details.png)。之前截图取自上一轮当前基线的合成审计，未重新制作全部72配置基线；这里只比较常驻控件与菜单变化，不伪造完整前后矩阵。动态日期/时刻来自合成夹具运行时间，不表示真实治疗事实被改写。

## 仍需区分的验证

本机没有 Android 模拟器；原生 Schema 迁移、SQLCipher、原生 PDF 和时间线入口测试通过 GitHub API35 模拟器 CI 执行，当前结果见 HANDOFF。模拟器不等于真实设备；覆盖安装、实际设备字体/短屏/IME/TalkBack及正式签名交付仍未做。本轮仅构建验证，不能把 debug 或未签名 Release 包当覆盖安装交付。

[医疗档案设计](design/medical-records-roadmap.md) 已独立提交推送；方向为规划中、未实施，现有复诊入口和功能继续保留，附件开发另行设计/授权。
