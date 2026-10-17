# P5 现有 UI 与最小接线审计

基线：默认 `35600a0fe239d73e9701a61f0911405cb29aae17`；P4 合并基线提交 `bfcec65`。这是源代码审计，**不是 P5 已实现或交互验收**。P4 原生性能仍 FAIL，P5 正式接线尚未开始。

## 所有权与真实成果

PR60 仅 `docs/design/claude-ui-2026-10-17/` 八图及审查；其 README 明确没有取得 Artifact 源码/真实交互。不能将 06/07/08 当作待开启的生产 UI。现有 UI-01–04 的 Compose 代码确实存在，P5 优先复用。

2026-10-17 检查本地原始、P0、P1、P2、P3、04A、PK 工作树均无未提交改动；原始/P0/P1/P2/04A/PK 相对其 upstream 无未推送提交。P3 本地旧分支落后其 upstream 21 提交，未 reset、切换或覆盖。当前开放 #63 为 Claude 的 Gradle/CI 修复，与 P4 数据层修改分离。此结论只覆盖可访问工作树与 GitHub 分支，不代表能看到其他机器的未推送代码。

| 真实文件 / 回调 | 已有行为 | P5 最小改动候选 |
|---|---|---|
| `app/.../ui/StockScreen.kt`：`onAdd/onReplace/onCheck/onManage` | 原库存卡、单主按钮与菜单；P4 UNKNOWN 卡不暴露精确写入动作 | 保留布局、token、折叠与菜单；只消费确认数量/未知原因 DTO，按已批准包装模式打开确认流程 |
| `app/.../ui/AppShell.kt`：`stockDialogs` / `onAddStock` | 日历、首页、库存共用已有入口 | 保留导航及返回栈，只把确认后的动作传给统一 gateway |
| `app/.../NotesViewModel.kt`：`replaceContainer/correctStock` | 仍调用旧 Double 库存路径 | 旧数据保持旧分支；新精确包装调用公开 gateway，不以 Double 承载精确数量 |
| `NotesViewModel.refresh` | 先读 `repo.stockForecasts(now)`，随后另读 records/containers 等 | 一个 PRIMARY SQLCipher 只读事务捕获完整不可变 bundle，再一次发布；不能把不同时间的余额与预测组合 |
| `core/data/.../NotesRepository.kt`：measured tickets/create/use/count/revise/delete/restore/package correction | P4 已编码 internal API，app 模块不能直接调用 | 公开 DTO/不透明 ticket 的小型适配层，内部继续验证 owner/dataset/epoch/record revision/ledger head/UUID，不暴露可伪造内部票据 |
| Repository measured backup export/restore | P4 internal 候选接口 | 复用既有用户明确导出/恢复入口；原子 staging 与错误/取消门不变；不碰外层 PNBAK1 密码学 |
| MedicalRecords/Visits/Labs/Visit Pack | 当前医疗信息与结构化导出 UI 已有 | 本阶段不扩展成原件文件库，不重画这些页面 |

## 统一 DTO 必须保持的合同

- 以一个不可变读取快照携带药物/记录/容器及预测，scope/epoch 与投影指纹来自同一事务；日历、首页、库存与提醒共享投影，不在 Compose 中重算。
- 每包装显示原单位、冻结来源与确认余额，UNKNOWN 另列原因。未知不是零、不是库存充足，也不能生成确定剩余天数。旧标量与精确量不相加。
- ticket 由后端发行并捕获作用域；重复 operation UUID 同内容返回原结果，异内容拒绝。取消不写入，不在一次失败后产生第二 UUID 自动重试扣减。
- 剂量与实际包装计量分别由用户确认；不得自动填推荐剂量、频率、盐/酯等效或按当前目录解释历史。
- P5 shared 文件集只包含必要 `NotesViewModel.kt`、`AppShell.kt`、`StockScreen.kt`、既有对话框/回调适配，以及四语新增的必要错误/未知状态；具体 diff 在 P4 完整准入后审阅。当前未改这些文件，不宣称 before/after 截图已取得。

## 接线验收

P4 最终 SHA 六项 CI、原生低内存预算及安全合同通过并合并后才能正式开始。P5 使用合成数据覆盖创建/打开/实际消耗/取消/重复确认/实盘/修订/冲突协调/VOIDED/恢复、应用重启和统一提醒。保留四语 320/411dp、1×/2×、明暗及相关对比度的真实截图对照。TalkBack、国产 ROM、物理 ARM 均待实测。概念 PNG、Mock、JVM 截图不能代替原生操作或真机证据。

状态：**NEEDS_UI_WIRING / P4_BLOCKED**。没有新增生产 gateway、UI 写入或正式入口。
