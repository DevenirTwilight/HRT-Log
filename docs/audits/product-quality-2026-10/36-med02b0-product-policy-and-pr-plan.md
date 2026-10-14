# 36 — 容量、纠错政策与B1–B6独立PR计划

2026-10-14。所有下列生产选择均PROPOSED / AWAITING_OWNER_APPROVAL。没有启用附件功能，16MiB/256MiB/1000不是已验收标准。

## 首批范围与容量

只在PRIMARY医疗档案，首批静态JPEG/PNG/WebP原件。保留完整原始字节及EXIF，不生成诊断/OCR结论、不自动根据日期/hash关联化验。用户选现有就诊/化验并确认关联，不复制既有医疗数据。伪装笔记没有共享索引/alias/目录/入口，跨空间即撤销capabilities。PDF暂不开放；密码PDF、外部分享/导出先不支持。后续受限PDF需恶意资源语料、解析隔离/deadline和真实ARM，不以良性模拟器结果批准。

候选每原件16MiB，每空间256MiB，最多1000**对象/版本**，不等于1000document。计入所有保留版本、垃圾箱、已验证但未commit对象与pending reservations；cipher额外头/tag及operation元数据也单列预算。重复hash不静默去重或替代关联，给用户提示并可明确选择继续，操作重试靠operation幂等而不是hash去重。

候选导入最低可用余量：预留cipher大小+元数据+16MiB操作余量，并保留全机至少64MiB或总容量5%（较大者）；均待低端ARM实测，不是OS写成功保证。未知长度SAF必须按实际读字节限流，实时预算预约增长，超限取消并只清owned ciphertext；不信任MIME/宣称长度。完整恢复需同时保留旧数据与完整新staging、至少完整输入估计cipher+SQL暂存+margin，不自动删旧原件腾空间。预览最大并发1、图片候选原像素≤20M、边≤10000、输出≤4M像素、deadline与PSS目标需实测后冻结；这些不是native parser安全保证。

报告30实际只有单合成4MiB PDF的三模拟器进程采样；不是16MiB原件、256MiB库、1000对象/低端ARM基准。B0没有新增性能/内存测量，不填虚构数值。新的API目录fsync/no-replace实验是操作可用性，非性能或掉电验证。

容量验收矩阵：API26/29/35各真实低内存ARM设备；1/16MiB及+1B文件，PNG/JPEG/WebP高压缩/EXIF/ICC/动画拒绝语料，1/20/100/1000对象及超额，含trash/旧版本/pending，满配并发import/backup/delete/restore；测walltime、CPU、进程PSS/native/java采样、峰值采样间隔、磁盘cipher/staging/WAL增长、用户取消时延。列表SQL分页+boundedLazy，不能一次创建1000节点；320dp/2×font四语/TalkBack。未有设备项标NOT_RUN，不能用emulator代替ARM。

## 建议纠错/保留政策

|操作|建议行为|安全要求|
|---|---|---|
|文字/日期/分类修改|CAS revision更新与operation证据|不解析成未经确认诊断；日期不自动建立关联|
|链接更正|旧link RETRACTED，新user-selected link|typed recordUUID/epoch校验，历史可追溯|
|替换原件|新version/object/DEK，旧版本保留|验证新完整对象再CAS切active；不覆盖原件|
|误添加撤销/删除|document TRASHED，正常UI默认隐藏|保留版本/链接/key，二次确认，立即刷新|
|恢复误删除|验证auth/key/目标当前epoch与revision|缺失旧target只quarantine，不按相同ID/日期猜测|
|永久删除|明确确认，说明备份副本不会随之消失|PURGE_PENDING→owned无引用/lease对象删除+dirsync→PURGED tombstone，失败可重试|
|旧版本回收|初期不自动过期，用户明确选择|不删除active、被其他引用、读/backup lease或pendingrestore对象|

建议垃圾箱和旧版本**不按天自动永久删除**，容量满时提示手动管理/先完整备份。永久删除前检查成功完整备份或用户明确接受无备份不可恢复；这项豁免与确认文案需负责人批准。软删可恢复；永久purge不承诺可恢复或闪存法证擦除，删除key不清除既有便携备份/旧alias副本。原件缺失/损坏保留metadata/link并清楚标记，不伪造空文件，不吞错。

## 分批PR与默认关闭门槛

每批独立最小PR、失败测试先行、最终HEAD完整checks通过、负责人review，不自动合并。B1–B5所有入口默认关闭；即使单批通过也不向真实用户开放。最早图片开启需要协议独立审查、B1–B4、B3完整备份/真实新device恢复、ARM与隐私验收、负责人显式批准；B6完成整体正式验收。PDF独立后置门槛不拖低图片安全标准。

|PR|独立实施|验收证据/依赖|
|---|---|---|
|B1|批准后正式Room9→10 additive metadata/identity/envelope/generation；独立key/PRIMARY隔离；feature默认0|真实Room/SQLCipher合成升级/回滚/onOpen/29旧表hash/VOIDED/ledger保护；trash hooks及旧backup serialization adapter；不导入原件。依赖批准schema/身份/旧包政策|
|B2|批准并专家review的新协议加密导入、未知长度、operation journal、无覆盖发布、session registry、崩溃reconcile|公开+全协议正负向量多provider；每durability seam kill、ENOSPC、删除失败、同generation CAS、无明文；并发撤权/锁定/space switch。依赖B1与协议冻结|
|B3|批准的独立完整medical package、stream snapshot leases、portable key recovery、generation激活；旧PNBAK quarantine|全集合认证/缺块/重排/trailing/密码/KDF预算、真实新device、坏包旧数据不变、重复restore修订/CAS、双空间；旧包读写严格兼容。未通过不得启用B4|
|B4|受限安全静态图片预览、显式file links、分类/替换/垃圾箱/恢复/永久删除|恶意图片/deadline/无cache/撤权能力、原字节/EXIF不变、20批分页、共享引用与versionleases、四语基础；B1–B3合格仍默认关闭直到开启批准|
|B5|受限PDF、明确导出隐私提示及临时URI生命周期|真实ARM恶意PDF资源限制/worker隔离/随机FD读取/无plaintext disk/锁定/进程death；password PDF不支持，无失败明文回退；外部导出需要新隐私授权范围|
|B6|产品四语/无障碍/ARM容量与完整B2/B3/B4/R15/MED01/Science回归|320dp/2×font/TalkBack，quota实测、现有PNBAK/锁定/decoy/noINTERNET/release未签名；完成统一报告与正式启用批准，不自动签名/发布|

## 明确需要负责人决定

1. 是否批准B1生产additive Room10及typed identity/tombstone/envelope/generation表，禁止destructive migration？草案SQL不等于批准。
2. 独立安全专家人选/范围：对象组合/AEAD预算/KDF、完整backup/恢复/key包裹、Android native hostile parsers与durability；AI审查不能替代。
3. 是否先评估成熟Streaming AEAD后再冻结自有组合？新的正式magic/协议/backup格式须另批准，不使用研究标记。
4. 图片首批PRIMARY-only、PDF/分享延期是否批准？quota与像素/deadline只按实测批准，16/256/1000候选可调。
5. 旧PNBAK读取不变、record-only serialization固定9、新旧links隔离和不自动ID重关联政策；完整restore采取整个集合替换、不merge。
6. 旧版本/垃圾箱默认不自动过期；永久delete备份前置或显式无备份豁免；tombstone/audit保留最小信息/期限。
7. portable KDF实机成本profile、密码UX、重复restore用户确认规则、真实设备测试资源。
8. B1–B5默认关闭，图片与PDF各自何时显式启用。任何批准不得推导为授权签名/version/APK发布。

当前没有需要暂停本轮设计/合成测试的问题；上述决定只阻断对应生产实施，不阻断B0交付。
