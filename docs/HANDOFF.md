## PR #177 与已合并交付记录 #176 无损同步 — 2026-10-31

等待df89完整Android时，默认分支合入a7a882b5（#176，交付账本、Release历史和源码版本floor42）。只解REQUIREMENTS/HANDOFF两处冲突，双方有效内容完整保留，继承已批准版本42；不签名/交付APK、不触碰公开Release/tag。全部跳过功能生产源码与df89逐字节相同，LAB04原哈希及精确逆转证明保持。df89 API26/29/35真实MainActivity用例均已实际PASS，原XML及artifact哈希在PR最终原生验收评论6542070164及后续收据；df89完整Android尚在执行、不能冒称PASS。同步后的准确HEAD继续现有必需CI。当前代码可Ready for Review，按§106启用GitHub原生auto-merge，保护必须等最终检查成功，不绕过冲突/评审/失败。

## PR #177 综合CI字节锁收口 — 2026-10-31

74c13371完整PR Android4333项仅LAB04 projectionSourcesAreTheBaseBytes失败（609原条件skip），旧化验输出金标通过。保留全部BASE_DIGEST/BASE_SOURCES原值；对用户已授权的四处SKIP投影差异建立完整SHA+精确逆转证明，逆转后继续比对原字节；新增任意其它化验/源码改动拒绝测试。3项原锁/金标/负向+投影12+时期5共20项PASS；直接App64+core33合计97项PASS。生产代码与74完全相同，仅守卫/审批证据及文档变化；新准确HEAD必需CI仍需完成。

74原生API29、35真实MainActivity跳过/阶段归属/保存覆盖/新Activity历史/撤销均PASS，原XMLgzip见docs/fixes/explicit-skip-2026-10-31；API26尚PENDING，不能先称全通过。PR API27安全首次设备userdata磁盘不足、instrumentation未开始，原完整log压缩入库；仅该失败job一次重验PASS。原始失败/前后证据与原SHA均保留；不签名、交付或发行。最终远端精确SHA的结果与合并状态以PR #177 checks和最后验收评论为准，不把这些历史检查点当永久结论。

## PR #177 实证修复：跳过不能清空已保存覆盖 — 2026-10-31

新四语真实OverrideDialog测试复现4/4生产缺陷：已保存rescheduled/zone/dose=3.0，切skip后onSave却全部null。Dialogs.kt最小修正：skip时保留initial已有覆盖，不提交隐藏的未保存编辑；普通更改/取消/重置与原Repository撤销不变。修复前原XML和修复后12项原XML在docs/fixes/explicit-skip-2026-10-31。现在61项App直接目标+33项core共94项PASS；最终原生编译PASS。MainActivity用例先准备已保存改时/3mg，再经正式TodayCard菜单完成跳过与历史撤销，保持原有效时间/剂量、无实际服药/库存影响。该最终修正必须在准确新HEAD执行三API和必需CI，旧b639原生结果不能替代；Draft保持直到验收，不能签名/交付/发版。

## PR #177 原生路径对照修正 — 2026-10-31

正式日程当天剂量位于TodayCard，day-dose列表只为其它日期渲染。原生候选改为真实当天卡片的对应slot菜单→更改/跳过→有名称的开关→保存，不触碰生产逻辑、不注入数据库跳过。兼容同时间其它药、较晚剂量展开；解除后要求原时刻/剂量保持且槽位回到合法OPEN状态（PENDING/SOON/OVERDUE由既有当前时刻规则决定，不把未来两小时跨午夜假设当业务要求）。编译实际6秒PASS。90项局部结果不变；最终准确HEADCI原生仍PENDING。

## PR #177 最终目标测试冻结 — 2026-10-31

追加真实跳过对话框四语点击/保存校验，发现三个Switch缺少自身无障碍名；现有SwitchRow只补对应label的contentDescription，不改布局/业务。原生按真实label选择跳过，无控件坐标/序号猜测。57项App直接目标及33项core撤销共90项PASS，最终8项四语原XML入docs/fixes/explicit-skip-2026-10-31，AndroidTest编译PASS。原未具名Switch匹配失败原XML保留。接下来只收口最终准确HEAD的CI/API26/29/35真实MainActivity合同；均满足才能Ready正常保护合并。无真机/TalkBack人工/签名/发行。

## PR #177 接手：已确认跳过归回治疗阶段 — 2026-10-31

沿用原分支与PR，远端446c618d无新提交。新增生产修正：提前跳过是已确认决策，保留原计划时刻但不进入Upcoming；空slot不当作显式计划证据。正常模式显示冻结药名与原时区计划时刻，详情显示计划剂量，简易/伪装隐私规则不变。用独立四语资源明确已跳过，不改变旧冻结strings/PDF摘要。原Repository/applyOverride/undoSkipped、库存、浓度、提醒、Room及备份协议均未改。

本地53项投影/四语UI/真实Room加密备份恢复/旧识别/撤销UI/PDF守卫，以及core撤销33项全PASS；新MainActivity真实日程→跳过→时间线→新Activity历史→撤销编译PASS。新原生合同接现有API26/29兼容列表，API35整套自然执行；原生与最终CI尚PENDING，不能合并或称产品验收完成。失败夹具与selector原XML/日志、执行范围在docs/fixes/explicit-skip-2026-10-31/，不改原安全断言/时限。无真机/人工TalkBack，无签名/交付/Release授权。下一步只查本提交准确HEAD的CI/原生，真实失败精确修复，保护条件满足才Ready正常合并。

## 2026-10-31 用户主动跳过服药：时间线错误显示待识别方案（候选待 CI）

问题：`NotesRepository.applyOverride` 已用冻结方案快照保存 `APP/SKIPPED`、slot_key、rule_version_id 和 scheduled_utc；但 `ObservedTreatmentHistory` 不将尚未到时的未来跳过加入 resolvedRecords，`ImportedHistoryProjection` 随后把它显示为「待识别方案的历史记录」。这并不表示用户实际服药或方案缺失。

候选修复（分支 `fix/explicit-skipped-dose-timeline-20261031`）：在 `PeriodTimelineProjection` 中仅将有真实 slot_key、规则版本、预定时刻且未删除的 `APP/SKIPPED` 记录映射为独立 `EventKind.SKIPPED`，保留预定时刻和冻结身份；排除它们的 imported-history 重复显示。跳过的日期还未到时保留在计划时刻排序的 upcoming 区域，但明确标「已跳过服药」。历史导入/无真实 slot 的记录仍沿旧识别流程。详情显示冻结药品名称与**计划**剂量，不把它当实际服药、医疗建议、治疗停药或浓度输入。代码仅改只读时间线投影和 UI 文案，不写入/修改任何记录、库存、药代、Room 或备份。

新增 `ImportedHistoryTimelineTest` 三组：未来主动跳过；已过时跳过与未识别导入记录共存；撤销后被标记删除。原 `PeriodTimelineTest` 未关联的普通记录仍不生成单独服药卡。**截至提交时测试与真实设备均未执行，必须依据精确 HEAD 的 CI 后更新 PASS/FAIL/NOT_RUN；未构建、签名、发布 APK。**

---

## Build 42 已签名并私下交付 — 2026-10-31

- **交付物**：`HRT-Log-build42-full-official.apk`，versionCode 42 / 0.2.0 / `net.plainnotes.app`，14,539,934 字节，SHA-256 `fe93a36d771e03e0cce7459b4e76f0f64a07c4daa5f6bd5d58632268ceca7c28`；原正式证书 `989ba045…79b1`，v2/v3 通过、单签名者；**取代 Build 41**。负责人 2026-10-31 “好了给apk”；只在会话内私下发给负责人，不入 Git/CI/公开 Release/tag。
- **来源**：干净 `f112e109`（#167、#175 合并后的主线）+ 源码外 `version42.init.gradle`（仅 versionCode 41→42）；未签名包 SHA-256 `ea5ccc84…8d80`。内容：Build 41 + #167 线程修复（REQUIREMENTS §176，正式应用行为不变）。
- **核验**（收据 `docs/delivery/build42-private-verification.json`）：签名前后 324 个业务条目逐字节一致；64 位原生库 ELF 与 ZIP 均 16KiB 对齐；无 INTERNET、非 debug；密钥临时副本已 shred。主线 `f112e109` 与 `fdabc4ce` 的 CI 在签名前已全部成功、无重跑。账本追加 42，源码默认 versionCode 同步 42。
- 未做（NOT_RUN）：真机安装、人工 TalkBack、覆盖升级真实 APK 测试；不是公开 Beta。覆盖安装前先导出完整备份，不卸载、不清数据。

## 公开 Release v0.2.0 已替换为最新主线 — 2026-10-31

- 负责人“替换现有release，不用说build多少”（REQUIREMENTS §177）。把刚私下交付的同一个正式签名 APK（SHA-256 `fe93a36d…7c28`，源码 `f112e109b3b32e156e33518f71aad562cd168a05`）换进 Release `v0.2.0`：4 个附件、说明、tag 都已更新；工作流 run 41184909240 重新下载后逐个核对哈希，tag 与说明首行也核对过，临时分支已被工作流删除。
- 说明里**不写构建号**（也不写 versionCode）；README 公开版本行同样去掉。机器清单与账本里保留 `version_code`，只用于核对。
- 被替换前的哈希与旧 tag `76627bfe` 见 `docs/releases/0.2.0-rebuild-2026-10-17-superseded.md`。合并后 `release-assets.yml` 会对已发布附件、tag 做只读复核。
- NOT_RUN：真机 ARM 覆盖安装、人工读屏。

