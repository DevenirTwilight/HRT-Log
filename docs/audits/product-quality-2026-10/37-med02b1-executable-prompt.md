# 37 — MED-02B1 可执行开发提示词（待批准后使用）

本提示词不是当前执行指令。负责人先明确批准报告34/36中的生产schema/identity/旧包/retention边界；未批准只能继续独立合成验证，不修改正式Room。

> HRT Log MED-02B1 — 默认关闭的医疗附件元数据与密钥隔离基础
>
> 仓库DevenirTwilight/HRT-Log，默认claude/new-session-1959qb。先读AGENTS、REQUIREMENTS、HANDOFF、licensing及报告32–37，确认PR27/B0是否已合并以及最终主线Android/Science全绿；从最新已验证默认创建独立分支。不覆盖其他feature工作，未合并依赖采用明确stacked PR，不自动合并任何PR。
>
> 记录负责人批准的Room10表、身份桥、scope、旧PNBAK/quarantine政策原文；没有相应批准则暂停生产schema子项，保留测试与报告。仅基础，不能实际导入医疗原件或默认启用附件UI，不实施B2 crypto object writer/B3新正式backup。
>
> 先在实际Room/SQLCipher合成库写失败测试：真实schema9全部29表、最早支持migration链、appointments/questions/visitpack、labs/context、Trash snapshot/reference、R15 override与dose、B4 VOIDED/ADJUST/immutable ledger、主/伪装空间。不得使用真实健康数据、清空用户设备数据、删保护trigger、改skip/弱化断言取绿。
>
> 正式实现获批准的additive Migration9_10和Room Entity/DAO/index/FK，附件metadata包括document/version/link/operation/store/typed recordUUID tombstone/generation/独立append envelope，CHECK/trigger统一install。old tables/columns/rows/ledger保护不变，不destructive fallback。feature flag default0，无用户入口。测试Room新export/validate身份hash及旧表hash、重开onOpen、DDL/事务失败回滚、异常trigger完整恢复；SQLite工具22测试仅参考不是生产验收。
>
> lazy identity仅在明确typed可信目标选择时建立；appointment/lab原有snapshot删除→registry TRASHED同事务，原Trash父子同ID恢复→相同recordUUID，purge→不可复用tombstone；旧整数ID新row必须新UUID。不可定位旧payload保守拒绝建立link，不猜Slot/就诊、不复制数据。并发CAS与取消不留半link。
>
> 独立PRIMARY noBackup root/Keystore alias服务，只用合成DEK测试append包裹/轮换/故障，不影响现有DB或伪装笔记key。stable DEKhandle与wrapEpoch分开，缺失alias不生成key掩盖数据损坏；session capability撤权接口+固定store/epoch设计，任何key能力不跨space。设备wrap和便携格式未冻结则仅接口/fake合成实现，不声称生产密钥基础完整。
>
> 先实现经批准的结构化PNBAK adapter：serializationSchema固定9、表字段allowlist冻结、不导出附件/key；magic/KDF/旧限额不变。所有dataset replacement入口（restoreBackup/overwrite import/RawData.clear/全清）与新recordEpoch/links QUARANTINED同事务；错误包失败不改变旧rows/epoch。统一guard安装清单或审查白名单，SupplyLedger append-only始终保留。是否在B1无附件开启状态需完整restore基础hook可以按最小scope实现，但开启门槛不得绕过B3。
>
> 验证current/currentEpoch target、跨store、同ID复用、旧backup换epoch、active版本VERIFIED、不可变版本/envelope、pending idempotency和CAS；实际Room/SQLCipher migration、Keystore合成API26/29/35、锁定/decoy隔离、PNBAK旧包以及PR25/26、B2/B3/B4回归。JVM、Android、lint、Debug/unsigned Release、API35完整device-tests、适用Science全部最终HEAD通过；可用API26/29目标测试，缺设备ARM项NOT_RUN不冒称通过。保留已有测试与条件skip，不新增skip。
>
> 每阶段更新HANDOFF/STATUS/REQ，提交推送并等该批CI完成，不能为新push取消正在运行CI。生成独立验收报告，列migration数据hash/trigger差异、锁定key隔离、oldbackup结果/剩余生产风险，创建PR等负责人review；不自动开始B2。不得签名/交付APK/versionCode/PK模型/PNBAK格式更改。最终给SHA/PR/所有CI链接及未决批准项。
