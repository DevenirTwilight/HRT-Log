> 2026-10-21 最新：运行owner/冲突门及2项重放+1项空间绕门修正已实现；最终生产8b24ccf5，data527/Period5/reminder21、Android测试源码/App编译全PASS。详见 [runtime-owner/implementation.md](runtime-owner/implementation.md) 与final-local收据、HANDOFF顶部。当前工作树/workspace/HRT-Log-checkout，分支同下；无在途测试。App仍未绑定、入口OFF/Draft/autoMerge=null，≤5秒政策未批/LMK未证明/最终CI/native未跑，停止性能攻关继续有效。以下ae7688da/514/“未实现owner”均为此前历史检查点，勿重复实现。

# P4 换账号接手入口（2026-10-21）

这是交接检查点，不是合并或发布批准。接手者负责前后端，不等待另一个前端代理。

## 从正确分支开始

- 仓库：`DevenirTwilight/HRT-Log`；PR：[#57](https://github.com/DevenirTwilight/HRT-Log/pull/57)。
- 分支：`feature/ansm03b-p4-exact-stock-20261016`。
- 本次交接前远端证据 HEAD：`307a109366d06f131dd42836ca44baeb8ead4e5a`。
- 最新已测生产源码：`ae7688da`。交接后的文档提交不改该源码。
- 实际默认分支：`claude/new-session-1959qb`，最近核实基线 `320171cdc882ec0b62830b60e2329a4174ffa31c`；接手时重新核实。
- 当前工作树：`/workspace/HRT-Log-p4-attribution`，detached HEAD，修改已正常推送到上述远端分支。
- 新账号先确认具有仓库读取/推送权限；本交接不保存或转交任何凭据。新环境直接检出远端分支，不依赖聊天记录或 `/workspace/scratch`。

按顺序阅读仓库 `AGENTS.md` / `CLAUDE.md` 指定的 REQUIREMENTS、HANDOFF、licensing；然后读本目录 `proposal.md`、WORK-STATE 顶部和 PR 最新正文。HANDOFF 下面的旧检查点按历史记录理解。

## 当前结论

**P4 不能合并：Draft / 正式入口 OFF / autoMerge=null。** 不转 Ready、不合并、不签名、不发布 APK。

停止性能攻关已经生效：不恢复全审计微优化、A/B/C、增量验证或大库跑分。原 ≤5 秒失败仍是 FAIL；将该门槛改成独立延迟指标只是待批准政策，未改原断言。完整审计、事务回滚、票据与 UUID/epoch/revision/ABA 约束都保留。

恢复 LMK 稳定性仍 **NOT_PROVEN**。资源清理修正不是低内存恢复峰值已通过的证明。

## 已实现及证据

|内容|准确状态及位置|
|---|---|
|链读取失败、恢复正常/异常/取消出口的明文引用清理|生产修正 `31303e64`，已包含在 `ae7688da`；`resource-cleanup-applied/`；原两项 RED 断言未改，已 GREEN|
|冻结独立实际用药请求与只读历史提交观察|`MeasuredUseObservation.kt`、`MeasuredUseIntegration.kt`、`NotesRepository.kt` 的 internal prepare/observe；没有 App 运行调用|
|最后完整数据回归|`ae7688da`：514 PASS / 0 FAIL / 0 SKIP；`reference-context-green/` 的 XML、日志、receipt、SHA|
|来源上下文反例|`reference-context-red/` 保留两项真实失败；相同测试在最终源码 GREEN。只比较剂量/配置/时间/UUID 不足以区分改变后的药物关联，必须核对原 CONTEXT 事件|
|最终编译|`ae7688da`：Android 测试源码、App full debug 源码编译 PASS；未组装或交付 APK|
|界面及时期回归|`4111c0b8`：37 PASS（32 UI/导航/翻译 + 5 PeriodStability）；`frozen-outcome-android-ui/` 含截图。之后 App 源码未改，最后内部 context 字段修正后只编译 App，没有再次运行这 37 项|
|后台 owner 原型|`NonBlockingWritePrototype.kt` 位于 historyTest；TEST_ONLY，不在主 jar；不是运行服务|
|界面|共享显示协议、四语状态卡片、真实 NotesApp 内联状态区已实现；参数默认 null，正式保存未绑定|

新源码的远端必需 CI、API26/29/35 SQLCipher 原生、低内存恢复、真实手机和 TalkBack 均 **NOT_RUN**。提交带 `[skip ci]` 是为避免恢复已停止的昂贵原生运行，不是 CI 通过或允许绕过合并保护。

## 接着做什么（未完成）

1. 实现应用级进程 owner 和最薄保存门面，复用现有 Repository IO 事务；不用页面/ViewModel 生命周期承载保存，不增加持久队列、WorkManager 或新数据库。
2. 同 UUID + 完整冻结请求复用同一任务，异载荷拒绝。请求包括原配置、来源上下文、实际包装和数量，不能自动刷新票据或换 UUID 重试。
3. SUCCESS 必须是根事务真实提交后的结果。当前 internal 观察应在 writer 结束后的独立读取事务调用；不能把嵌在未提交外层写事务里的观察当耐久证明。
4. 观察只支持独立实际用药创建；计划完成、修订、删除/恢复、实盘/调整等操作尚无等价的完整核对接口。历史回执成功不等于当前记录还未被更正、删除或冲销。
5. `NotObserved` 只能保持结果待核对，不能据此宣告未提交、允许重试或将未知当零。进程死亡没有获批 token 时刷新真实数据、人工核对，不自动补写。
6. 覆盖同数据集/记录/药物/库存以及旧写入口、VOID/RESTORE、规格编辑、restore/reset/destroy 等冲突。只禁用 UI 按钮不够；票据依赖全局操作链，不能只按药物 ID 锁定。固定 PRIMARY/dataset/epoch，锁屏或伪装不改投数据集。
7. 从真实 owner 状态接 NotesApp，保留可浏览无冲突页面、收起/展开、返回和隐私边界。未核对结果不显示失败或可重试，不在内存入队时报告成功。
8. 在完整具体实施与政策方案可审阅后取得验收变更批准；恢复安全与必需 CI 是独立门槛。没有新授权不要自行重开大库性能或 LMK 试跑。

不改 Room13/业务备份12、PNBAK1 密码学、PK、权限、签名、版本、官方资料分发或 P5/P6。

## 旧工作树与复现环境

旧 `/workspace/HRT-Log-p4` 还有已停止的研究 WIP。小型源码/文档差异已原样归档 `takeover-old-wip/`，标记 **ARCHIVE_ONLY_NOT_APPLIED_SUPERSEDED**；不要应用其中旧 REQUIREMENTS 或恢复旧性能计划。

`/workspace/HRT-Log-p4-resource-check` 的资源修正源码与 `saved-resource-wip/` 的远端归档 SHA 一致，修正现已接入主工作树并回归通过，不要重复应用。原工作树均未 reset 或丢弃。

没有在途测试或未推送的本轮有效成果。机器上的 JDK/SDK、离线 Robolectric jar 和 `gradle-env.sh` 是环境缓存，不随账号/克隆自动迁移；按仓库构建要求重新准备，不能将缺工具写成测试 PASS。本轮不为交接重跑测试。
