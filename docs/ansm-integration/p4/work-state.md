# P4 工作状态

2026-10-16：IN_PROGRESS，生产库存未接线。当前复用P3的de26444，普通合入P1源945512a，不直接复制未合并源码。#54已受保护合并为c95cea1；#55待主线检查及更新。

源9ed9aaa纯domain140项PASS，包含新14项StockQuantum测试；集成后Python157项PASS。精确分数、不同物理量、成分/来源版本、标签容量未知、用户声明容量区分、2^53边界、连续反向及极端汇总抵消均覆盖。未修改旧Scalar账本、Room、备份、预测或UI。纯核心不是生产库存集成验收。

下一动作：冻结商品/容器包装载荷与来源hash；明确新模式整数投影、旧LEGACY_UNKNOWN隔离和恢复精确归并；实现与真实记录同事务的显式包装确认、CONSUME/REVERSE/ADJUST幂等及故障回滚；在任何新模式入口启用前完成备份版本拒绝、旧路径分配/resize隔离及日历/库存/提醒未知保护。具体边界见quantum-bridge-contract.md。

不将名义64泵当实际可用；不将用户资料声明当官方核实；不从PK或当前引用推断实际包装。附件/联网/PK/签名/发布均不动。真机及TalkBack NOT_RUN。此分支尚不可自动合并，不能把14项纯测试当成正式库存已完成。

新增候选包装载荷codec12项首轮10通过/2失败：一项用BigDecimal.equals错误要求相同scale（80与8E+1数学相同，实际存储plain文本80），改为明确plain文本80断言；另一项负向测试在调用codec之前就由canonical(Double)抛IllegalState，未进入预期验证异常。修复read将外部解析/类型错误统一为IllegalArgumentException，并把非法Double原始JSON送入codec，保留其必须拒绝断言。来源、单位、精度等断言未削弱；后续完整回归待运行。当前Room11/业务10及拒绝新模式的守卫未改。
