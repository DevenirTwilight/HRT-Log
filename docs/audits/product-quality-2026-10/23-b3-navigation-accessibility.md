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

## 三个用户场景

| 场景 | 修改前 | 修改后 | 证据 |
|---|---|---|---|
| 1. 从库存按返回 | 应用直接退出 | 回到日历；日历上再按才退出 | AppShellBackNavigationTest（自动化，Robolectric 真实 AppShell） |
| 2. 用 TalkBack 浏览日历 | 只读出日期数字，状态只靠颜色 | 每个日期读出完整日期、今天、选中、服药/预约/库存状态，可逐日点击 | CalendarSemanticsTest（自动化语义，四语）；真人 TalkBack：NOT_RUN |
| 3. 选历史日期后旋转 | 跳回起始日期 | 保留所选日期，当天记录一致、不重复 | CalendarSelectionRestoreTest、CalendarCoreFlowTest（自动化状态恢复）；物理设备旋转：NOT_RUN |

## 改动文件

- `ui/AppShell.kt`：一个 `BackHandler`（抽屉 → 日历），伪装模式和日历页不启用；日历“添加”按钮加测试标签。
- `MainActivity.kt`：伪装模式下传 `backToCalendar=false`。
- `ui/CalendarScreen.kt`：日期单元 `selectable` + 无障碍名称，装饰数字/圆点不重复朗读；年视图月份名称；`calendarStart` 仅在真正改变时覆盖选中日期。
- `ui/Format.kt`：`formatFullDate`。
- 四语 `strings.xml`：7 个日期朗读用字符串。
- 测试：`AppShellBackNavigationTest`（7）、`CalendarSemanticsTest`（6）、`CalendarCoreFlowTest`（10）、`CalendarSelectionRestoreTest`（3）、设备测试 `CalendarIntakeAndroidTest`（2），仅调试版的合成数据宿主 `debug/CalendarTestActivity`（未导出，不进 Release）。

未改：日期计算、服药状态规则、记录写入、PK、Schema、备份格式、B2 隐私行为、权限。

## 测试

| 测试 | 修改前 | 修改后（本地） |
|---|---|---|
| AppShellBackNavigationTest 7 项 | 3 失败（顶级页面返回、抽屉返回、复诊列表返回日历） | 7/7 |
| CalendarSemanticsTest 6 项（月视图四语、周、年） | 6 失败（无逐日节点、年视图无名称） | 6/6 |
| CalendarCoreFlowTest：状态恢复保留选中日 | 失败 | 通过 |
| CalendarCoreFlowTest：选日查看服药、32 种布局组合（四语 × 320/411dp × 1×/2× 字号 × 浅/深色） | — | 9/9 |
| CalendarSelectionRestoreTest 3 项：跨年历史日期旋转后保留选中日与当天唯一记录；外部起始日期真正改变仍同步、之后用户选择不被覆盖；空数据保留选中日并显示无记录 | 3 失败 | 3/3 |
| CalendarIntakeAndroidTest（API35 模拟器）：取消、真实系统返回键不写记录；保存一次（核对状态与剂量）后真实返回键导航不重复、从历史返回落在日历 | — | CI 实际执行 2/2 通过、0 跳过（见下） |

本地全量：domain 58、pk 124、importer 12、data 72、reminder 14、app 726（61 个既有条件跳过）零失败；含 PeriodStabilityTest、M2OnlyNumericalContractTest、SublingualEstimatePolicyTest（D-2）、B2 隐私回归、TranslationsTest。lint 0 错误、179 警告（与基线相同）。androidTest 编译通过。Debug/unsigned Release、设备测试以 CI 为准。

Robolectric 中含文本框的服药确认对话框无法空闲（B2 已记录同类问题），因此“取消/返回不写记录”和“不重复记录”放在 API35 设备测试中执行，不以单元测试冒称已验证。

## 设备测试排障记录（仅测试代码与 debug 宿主）

| 提交 | 设备 CI 结果 | 原因 | 修复 |
|---|---|---|---|
| `2d91184`/`6164842` | 2 项初始化失败 | `runOnIdle` 在主线程内读取 semantics（Compose 禁止） | `7eb4902`：在测试线程等待并断言 |
| `7eb4902` | 失败（第 46 行未选中；第 57 行返回键后对话框未关；第 71 行未回日历） | 冷启动数据加载时空状态卡片消失、网格移动导致点击落空；测试在界面组合后才注册常开空回调，抢先吞掉返回；按键早于对话框窗口获得焦点 | `24a5da8`/`edc039e`：宿主在 `onCreate` 注册回调（与 MainActivity 同序）并计数；等对话框窗口真正获得焦点再发送真实 KEYCODE_BACK（软键盘打开时第二次返回才关闭）；断言返回未泄漏到 activity |
| `edc039e` | 2 项在加载等待处超时 | 以药名作为“已加载”信号不可靠（模拟器上当天服药卡已不显示，库存卡在屏外） | `b6b2918`：改为等日期格出现且“暂无计划”空状态消失 |
| `b6b2918` | **通过** | — | — |

未修改任何生产代码；断言未放宽（新增：保存记录状态/剂量、历史页目的地、返回未泄漏、活动返回计数）。

## 最终 CI（`b6b2918`）

- Android push [38393405106](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38393405106)、PR [38393412694](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38393412694)：jvm、android（单元测试、lint、Debug/unsigned Release、Manifest、来源与账本、Schema 不变）、device-tests 全部 success；android 约 13.5 分钟，与此前一致。
- 设备原始报告（PR 运行工件）：app 90 项、0 失败、6 跳过（伪装、导入清理、恢复进程死亡 3 组重启测试按设计在主套件跳过，由独立脚本在同一作业通过：fresh process 清理导入残留、强杀后新进程无待恢复状态等 PASS）；`CalendarIntakeAndroidTest` 2 项、0 失败、0 跳过；core/data 14 项、0 失败。
- Science validation：science-change-gate success；research-deep 按路径规则 skipped（仅应用代码）。

## 未执行与风险

- NOT_RUN：真人 TalkBack 实机操作、物理 ARM 手机。自动化语义测试不等于真实无障碍验收。
- 320dp 下月视图日期格实测可点击区域约 34dp×58–60dp（411dp：47dp×60–62dp），相邻无重叠，2× 字号与深色一致；选日、切月、查看记录、完成服药均可操作。宽度低于 48dp 建议，按产品负责人 2026-10-13 决定不阻断合并，留作后续可用性优化。
- 月视图对视力正常用户仍主要靠背景色区分状态（有图例）；读屏已用文字表达。在窄格内增加非颜色标记需另行设计。
- 伪装模式运行中刚开启伪装时，返回行为在下次进入应用后才切换为“立即回外壳”；不会泄露页面。
