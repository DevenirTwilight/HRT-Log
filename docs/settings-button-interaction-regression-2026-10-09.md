# 设置按钮交互回归：范围、依据与验收

2026-10-09，REQUIREMENTS §44。起始329adeb92b2a2f2e58f61b6d367decc6c6004eef，Build25/schema9。

## 修改前与修改后

| 选项组 | 修改前 | 本轮修正 |
|---|---|---|
| 主题 | 足够宽时分段按钮，不足时AdaptiveChoice改成RadioButton列表 | 足够宽仍原Material分段按钮，不足时等尺寸纵排OutlinedButton |
| 对比度 | 同上 | 同上；与主题共享最长译文测量，选中色、勾选、边框、点击反馈和Selected语义清晰 |
| 离开后锁定时间 | 锁启用后固定横排3个SegmentedButton，之前常规审计未启用真实锁 | 根据内容选横排分段或纵排按钮；保留立即/30秒/5分钟、唯一选中与原保存值 |

新组件 AdaptiveButtonChoice 只用于以上三组。按钮最小触控48dp，高度由实际字体/文本测量确定，同组等宽等高；不省略文字、不删合并选项、不改纯文字或单选列表。横排Material单选分段按钮本身使用Role.RadioButton无障碍语义，这不等于RadioButton列表；纵排要求真实Material按钮的Role.Button，并保留Selected语义。测试不能靠全局断言“没有RadioButton语义”来误伤原分段控件。

UiPrefs、AppLock、原保存键和值不变；设置回调仍保留其他外观字段，锁启用/关停/修改PIN、通知文案、提醒、语言、动态颜色等功能不删除。不改数据库、PK、历史数据或复诊。

## 其他 AdaptiveChoice 调用点：只核查，不修改

原AdaptiveChoice仍是“放得下分段按钮，否则Radio列表”。下列8处都存在这一降级路径，因此有类似交互形式变化风险；实际是否触发取决于语言、字号和可用宽度。本轮用户只授权修设置，不把它们顺手改用新组件。

| 代码位置 | 选项 | 与此前交互的关系 |
|---|---|---|
| MedicationEditor.kt | 3种计划频率 | 以前SegmentedButton，§41改为AdaptiveChoice，可能改成列表 |
| CalendarScreen.kt | 日/周/月/年 | 以前SegmentedButton，可能改成列表 |
| ConcentrationScreen.kt（主曲线） | 7/14/60天 | 以前SegmentedButton，可能改成列表 |
| ConcentrationScreen.kt（单位） | pg/mL、pmol/L | 以前SegmentedButton，可能改成列表 |
| ConcentrationScreen.kt（校准方式） | 回顾/因果 | 以前SegmentedButton，可能改成列表；本轮不碰计算或选项逻辑 |
| ConcentrationScreen.kt（其他药物曲线） | 7/14/60天 | 以前SegmentedButton，可能改成列表 |
| DataSection.kt | PDF日期范围 | 以前SegmentedButton，可能改成列表 |
| Components.kt | 窄屏12小时手动输入的AM/PM | §41新增窄屏输入中的AdaptiveChoice；也有Radio降级，不冒充原独立分段按钮直接替换 |

以上7处原分段迁移可由d7f8696和069e61d历史diff核实；AM/PM是新窄屏表单内的选择器。源码与本轮起始完全相同。设置里的语言/地区原本就是下拉菜单，本轮不改；伪装外壳仍是原SegmentedButton，未发现AdaptiveChoice式Radio降级，不重设计伪装模块。

## 回归测试与验证边界

- SettingsButtonInteractionTest：四语×320/411dp×字号1/1.3/2×浅/深，共48项；真实SettingsScreen与UiPrefs，检查类型、三个选项、唯一选中、每项点击、动态颜色/其他字段保留及持久化，等宽等高、完整文字与48dp触控。
- 用修正后的同一测试跑原源码：48项中40项因纵排Role.RadioButton不再是按钮失败，8项（411dp/字号1）通过。首版44失败含中文“跟随系统”与语言下拉框重名导致的选择器歧义；该数字不能当成真实回归数。选择器改为实际Selected控件后重新验证，最终基线40失败。
- 修复后48项和原ButtonLayoutRegressionTest64项本机全部通过。新增原生测试夹具曾因Configuration.locale遮蔽构造参数编译失败，已明确接收者修正并编译通过。
- SettingsButtonsAndroidTest：四语×320/411dp×字号1/1.3/2，共24项；真实AndroidKeyStore setPin并验证PIN，锁启用时测试主题、对比度和锁定时间。检查全部选项、按钮类型、尺寸/文字/触控、选中与实际持久化，并确认PIN仍有效且锁文件密文不变。没有伪造lock.bin或用布尔fixture冒充锁启用。
- 原生矩阵在模拟器物理窗口上以LocalDensity设置并断言320/411dp视口，使用真实设备的字体渲染/Keystore/资源；属于模拟器受控视口验收，不是手机屏幕实机验收。

本机聚焦日志：`/workspace/tooling/settings-buttons-before-corrected.log`、`settings-buttons-focused.log`。完整检查、审计及最终CI结果见最新HANDOFF；未完成的命令或编译出的测试APK不等于原生测试通过。本轮不生成新签名包，先前ceb1912c测试APK不包含本轮设置修复。真机字体、短屏/IME和TalkBack仍需设备验收。

## 本机完整检查与专项审计

完整app343登记/330通过/13跳过/0失败，原历史与稳定性回归通过；Lint0错误/131警告，Full Debug/Release与测试APK、manifest无INTERNET通过（4m48s，settings-buttons-full.log）。设置/伪装设置对话框72配置×2case=144渲染，裁切、重叠、越窗、零尺寸、动作失败及测量失败均0，见 [机器汇总](ui-audit/2026-10-09-settings-button-summary.json) 与 [合成设置截图](ui-audit/2026-10-09-settings-buttons.png)。审计锁关闭；真实锁启用矩阵必须看原生CI，不能混算。

首轮原生CI37898203318新增24项在渲染前失败：用于语言矩阵的Context包装失去ActivityResultRegistryOwner。真实setPin已成功，按钮尚未被验证；夹具显式提供真实测试Activity作为RegistryOwner，重新编译成功。最终原生矩阵结果以HANDOFF为准；不将首轮失败隐藏或当作已通过。
