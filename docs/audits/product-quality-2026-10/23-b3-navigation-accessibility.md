# 23 B3 导航与无障碍验收

2026-10-13；分支 `feature/b3-navigation-accessibility-20261013`，起点默认分支 `d5dc9e5`（PR #21 合并后默认分支 CI 全绿：Android 38385723304、Science 38385723291）。路线图 R-06 / R-13 / R-24。未合并、未签名、未交付。

## 用户行为对比

| 操作 | 修改前 | 修改后 |
|---|---|---|
| 在库存/化验/历史/浓度/设置等页面按系统返回键 | 直接退出应用 | 回到日历；在日历再按才按系统行为退出 |
| 侧边菜单打开时按返回键 | 直接退出应用 | 先关闭菜单，页面不变 |
| 对话框、复诊详情中按返回键 | 关闭对话框 / 回到复诊列表 | 不变 |
| 伪装模式按返回键 | 立即回到伪装外壳 | 不变（保留最快离开真实页面的行为） |
| 应用锁界面按返回键 | 交给 MainActivity（退出） | 不变；被隐藏的页面不会被切换 |
| TalkBack 读日历日期 | 只读数字（如“10”），服药状态仅靠颜色 | 每个日期是一个按钮：完整日期、今天、选中状态、全部已服/部分漏服/漏服/未确认、计划次数、预约、库存用完；周视图逐次读出时间和状态 |
| TalkBack 读年视图月份 | 只读缩写或无可点名称 | “2026年3月”之类的按钮，点击说明“查看本月” |
| 旋转屏幕/界面重建 | 选中的日期被重置为起始日期 | 保留用户选中的日期和当前页面 |

## 改动文件

- `ui/AppShell.kt`：一个 `BackHandler`（抽屉 → 日历），伪装模式和日历页不启用；日历“添加”按钮加测试标签。
- `MainActivity.kt`：伪装模式下传 `backToCalendar=false`。
- `ui/CalendarScreen.kt`：日期单元 `selectable` + 无障碍名称，装饰数字/圆点不重复朗读；年视图月份名称；`calendarStart` 仅在真正改变时覆盖选中日期。
- `ui/Format.kt`：`formatFullDate`。
- 四语 `strings.xml`：7 个日期朗读用字符串。
- 测试：`AppShellBackNavigationTest`（7）、`CalendarSemanticsTest`（6）、`CalendarCoreFlowTest`（10）、设备测试 `CalendarIntakeAndroidTest`（2），仅调试版的合成数据宿主 `debug/CalendarTestActivity`（未导出，不进 Release）。

未改：日期计算、服药状态规则、记录写入、PK、Schema、备份格式、B2 隐私行为、权限。

## 测试

| 测试 | 修改前 | 修改后（本地） |
|---|---|---|
| AppShellBackNavigationTest 7 项 | 3 失败（顶级页面返回、抽屉返回、复诊列表返回日历） | 7/7 |
| CalendarSemanticsTest 6 项（月视图四语、周、年） | 6 失败（无逐日节点、年视图无名称） | 6/6 |
| CalendarCoreFlowTest：状态恢复保留选中日 | 失败 | 通过 |
| CalendarCoreFlowTest：选日查看服药、32 种布局组合（四语 × 320/411dp × 1×/2× 字号 × 浅/深色） | — | 9/9 |
| CalendarIntakeAndroidTest：取消、系统返回不写记录；保存一次后返回导航不重复 | — | 仅设备 CI，结果见 PR 检查 |

本地全量：domain 58、pk 124、importer 12、data 72、reminder 14、app 726（61 个既有条件跳过）零失败；含 PeriodStabilityTest、M2OnlyNumericalContractTest、SublingualEstimatePolicyTest（D-2）、B2 隐私回归、TranslationsTest。lint 0 错误、179 警告（与基线相同）。androidTest 编译通过。Debug/unsigned Release、设备测试以 CI 为准。

Robolectric 中含文本框的服药确认对话框无法空闲（B2 已记录同类问题），因此“取消/返回不写记录”和“不重复记录”放在 API35 设备测试中执行，不以单元测试冒称已验证。

## 未执行与风险

- NOT_RUN：真人 TalkBack 实机操作、物理 ARM 手机。自动化语义测试不等于真实无障碍验收。
- 320dp 下月视图日期格约 34dp 宽，低于 48dp 触控建议；本轮未改布局（需另行设计）。
- 月视图对视力正常用户仍主要靠背景色区分状态（有图例）；读屏已用文字表达。在窄格内增加非颜色标记需另行设计。
- 伪装模式运行中刚开启伪装时，返回行为在下次进入应用后才切换为“立即回外壳”；不会泄露页面。
