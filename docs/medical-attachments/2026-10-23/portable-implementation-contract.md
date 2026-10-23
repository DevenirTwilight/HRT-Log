# PNMEDBK1 OFF实现合同（2026-10-23）

授权：REQUIREMENTS §147.D，唯一参数见 portable-backup-format-proposal.md。**IMPLEMENTED_OFF 不等于永久冻结、正式恢复成功或功能启用**。本文件记录调用边界，不另提格式方案。

## 实际调用链

1. 现有 `AttachmentBackupPreparation.capture` 在一致只读事务读取完整业务事实、稳定身份、历史、回收站及关系；复用原全历史审计、归属inventory与所有内层认证，拒绝未知/遗漏/退休集合。
2. `Snapshot.member` 只按捕获的版本及物理归属重开，重新认证，再借出密文流和临时DEK；不暴露alias、路径、明文原件。回调在授权发布锁外；撤权/换dataset后的结果拒绝，临时key清零。捕获的设备envelope随快照关闭清理。`records` 前后核对固定内容承诺，不能导出调用方改写的事实。
3. `MedicalArchiveWriter.write` 独占密码和目标stream。固定完整成员后创建新salt/archive/root；每version在wrap前消费一次attempt预算，失败不能恢复原加密attempt。canonical manifest、原始业务JSON、未改动的全部内层密文、全集合footer经同一outer Streaming AEAD认证；逐对象重新核密文承诺。仅返回完整stream receipt，**不保证provider已durable保存**，没有SQL COMMIT/恢复/激活含义。目标只收到密文；失败可能留下无效部分密文，独立reader必须拒绝。
4. `MedicalArchiveReader.read` 独占密码和held channel。固定80B头及实际总长先于KDF/可变分配检查；独立逐token解码canonical字段，再顺序验证frame顺序/身份/长度/密文hash/footer计数/集合承诺和完整outer EOF。Tink seekable API首次read前不可查询size；精确明文size在完整读取及EOF后核对。之后所有保留成员逐个经原 `AttachmentObjectStreams.open` 完整内层AEAD、明文/密文hash、长度和EOF。任何失败关闭全部租约、清理RAM，不返回部分Archive。
5. Archive中的 `wholeArchiveAuthenticated=true` **仅表示归档及全部原件的密码学认证**，不等于临床事实有效/已恢复。`preflight` 必须复用已有严格业务版本、typed关联、墓碑/DOSE reference trash与LAB/APPOINTMENT snapshot、资源及完整闭合验证。只读业务回调借出独立副本，回调返回/撤权清零，不能修改已认证内部事实。
6. `Archive.reconstruct` 只接受对应manifest的既有Plan，调用已合并的 `AttachmentBusinessRestore`：新dataset/epoch/generation的私有RAM SQLCipher业务库、完整独立历史/逐字段比较、全内层认证和密文quarantine。`activatable=false`，不替换现库，不创建本地新alias或伪造持久VERIFIED。旧库/原件不变。

## 拒绝与所有权

- canonical是独立typed streaming parser（无通用DOM），每字段/数组/元数据行/bytes预算在分配前检查；语法不代替现有临床验证。编码对照原TEST_ONLY独立Python固定向量，未将原型弱格式改名上线。
- 原预检的连续历史/latest-pointer、typed源唯一性和attempt/DEK唯一性提取为 `AttachmentBackupManifest.validateComplete`，预检继续调用同一规则，并复用于capture/codec。原全历史oracle和所有旧断言保留。
- root/derived keys/DEK/工作缓冲依既有会话所有权清理；第三方primitive内部副本仍受其生命周期约束，不声称可强制擦除所有JVM/native内部副本。密码不规范化/修剪；正式64MiB/3/1无弱参数回退。
- 错误密码、非规范字段、未知参数、外层/内层损坏、坏portable envelope、成员缺失/多余/重复/替换、尾部字节、取消均整次拒绝。相同密码再次导出必须创建新attempt，不复用header/root/IV。没有导出重试journal或自动resume。
- 源文件缺失或归属变动时不得导出；不删除不明对象。reader源为持有的加密channel，后续随机读继续检查会话/物理总长并由Tink认证段，隔离stage还独立重核整个内层成员。没有持久明文暂存。

## 验收与未实现边界

本轮JVM合同使用正式KDF与独立测试frame producer，覆盖canonical/UTF8/数字/边界、固定Argon+HKDF独立KAT、包装域与一次性尝试、外层有效但成员/包装/计数错误、错误密码/截断/尾部/撤权。原生合同再运行实际生产writer→删除源测试Keystore alias及原库→另一数据库密钥与dataset的reader→预检→RAM SQLCipher重建；核全部业务字节、历史/trash/typed关系和旧库逐行不变。测试均为合成PDF/PNG/binary，不是真机或正式整库激活。

精确源码CI和原件状态见HANDOFF/证据；首次本地Tink size调用失败原件保留 `portable-archive-evidence/local-first-red`，不能将修复前的负例绿色当作完整验证。测试入口中删除合成alias是既有fixture清理，不是生产永久删除功能。

**仍未实现/未批准**：全库durable authority/journal与generation激活、旧请求scope/maintenance屏障、生产归档公共入口、本地新数据集完整持久恢复及新设备alias重新包装发布、永久格式冻结/启用。候选1000 versions/512MiB是表示上限，现有256MiB/512aliases等工程限额不提升；整个上限档案+KDF+旧库+持久SQLCipher/WAL/rollback的联合资源验收仍NOT_RUN，不以小合成fixture替代。独立物理设备/断电/新portable过程force-stop不是本轮已有quarantine强停证据。外审NOT_REVIEWED_EXTERNALLY、feature0、真实原件和永久删除OFF，PNBAK1/Room14/SQLCipher密钥/既有对象协议/UI/PK/BDPM/P5/P6不变。
