# 手工实盘与原消费冲销

2026-10-16。适配器尚无 Repository/App 调用者，备份接受仍 OFF。

负向真实数据库测试复现：半片消耗后用户确认剩余27片，再撤销原半片，会把已确认剩余量变成27.5片。原量冲销在算术上成立，但不能无提示重解释后来的独立实盘事实。证据 observation-red-evidence.json；未修改生产代码制造失败。

当前候选保护：每次 ADJUST 捕获当时该容器的账本头 ID。观察锚包含原消费时，新的逆操作返回 OPERATION_CONFLICT，整个事务不变；锚早于原消费时可按原量冲销。零差额观察也捕获锚。已经成功的同 UUID 逆操作重放仍返回原结果，不重新捕获锚或重复写入。不依据墙上时钟猜测先后顺序。最初“存在任何观察即阻断”的保守方案已由此规则取代，红/绿历史证据保留；正式更正协调入口仍不可启用。

已经实现的精化：ADJUST 用户请求和执行捕获分离，request_sha256 只覆盖稳定用户意图；在同事务捕获实盘时明确已存在的该容器账本头 ID。重复同请求不重新捕获头或改写旧观察。用该因果锚区分消费前/后的观察，避免把 created_utc 当严格操作次序。缺失锚或未知来源继续拒绝自动逆操作。

存在消费后观察时，需要明确、可审阅的协调交互及原子计划；不得静默夹取为零、猜测补库存或重算旧数量。真实用药事实的修订与物理库存事实的确认必须分别表达，取消或失败均无部分写入。该工作与业务备份新格式交叉验证、精确缓存恢复、统一未知预测一起构成正式入口阻断门。


## 候选因果锚合同（适配器已实现，正式入口未启用）

- ADJUST input_json 保存封闭 envelope：format、request、execution；request_sha256 只覆盖 request。request 包含原始输入、规范库存单位量、明确确认、冻结包装与原投影，execution 捕获该容器当时 MAX(supply_transaction.id)，空账本为0。
- 头 ID 是整数身份，不是库存量；按规范十进制字符串保存，范围为SQLite整数ID，不能套用2^53库存刻度上限或转Double。
- 同 UUID 重放先严格验证旧 envelope 与 request，一致则返回原观察，不读取当前头重写执行捕获。返回的是历史观察量，调用层仍须重新读取当前余额。
- 原消费 ID 大于某观察锚时，该观察先于消费；此逆操作不改变那条较早观察的事实。观察锚包含原消费时，必须先完成独立协调。零差额观察没有新流水行，但同样捕获头。
- 旧试验格式无锚时不推断顺序；本分支所有此类格式此前从未启用正式入口或备份接受。任何未来外部输入缺少锚均明确拒绝，不静默补齐。
- 要覆盖：观察前消费、观察后消费、零差额观察、设备时钟相同/倒退、旧请求在后续消费后重放、非法头ID/字段、跨容器及跨数据集锚。


源c7daddf已通过32项共享合同及六项必需CI；API26/29/35均实际运行这32项且零失败。消费前观察、消费后观察、零差额、倒退时钟、历史请求重放及非法/不存在/异容器锚均覆盖。证据projection-ci-evidence.json。单容器只读验证及因果锚不能代替完整全局备份校验或正式库存接线验收。


## 正式入口仍需解决的边界

账本头只表示**记录提交的因果顺序**，不是实际服药/观察发生时间的认证。当前库存实盘必须明确为用户本次确认的当前剩余量；不得把过去某日的观察当当前余额套用。补录较早实际用药时，也不能从“用户确认了包装”自动推断这次需要额外扣减：它可能已经包含在后来的实盘中。正式 UI/事务合同需分别确认真实物理用量与库存效果，保留 UNKNOWN/已计入实盘等明确状态；没有可靠依据时不产生新扣减，也不修改真实历史。

ADJUST 的正式确认票据还需绑定完整库存观察版本（包含无差额观察），防止余额恢复为同一数值后掩盖中间操作的 ABA 情况。当前内部适配器只比较容器投影与冻结包装，尚无正式确认票据/更正协调 UI，因此这部分不能标为生产接线验收完成。

2026-10-17 integration finding: a later independent count can reduce projected used quanta below the original consumption delta. An ordinary inverse would then leave the valid [0, capacity] range even inside an otherwise atomic correction. Existing `reverseOriginal` rejects this before writing, so neither record revision nor stock is changed. This is a protected refusal, not a completed reconciliation feature. A future explicit reconciliation journal must distinguish any bookkeeping bridge from a real user stock observation, retain the original consumption/count, prove its linked inverse/final observation, and validate every prefix. Merely bypassing the count guard, allowing negative prefixes, or renaming an invented intermediate balance as a user count is unacceptable. Tests for the completed workflow remain NOT_IMPLEMENTED.
