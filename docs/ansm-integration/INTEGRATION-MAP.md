# 来源与集成图

2026-10-16。最初基线2f4b958；当前已验证默认分支claude/new-session-1959qb，cfbe62c4a466f55234d53fbeff4c7f2c03224d50，六项必需CI通过。远端状态不能证明其他人未推送的本地工作。

|来源|真实状态|本轮处理|
|---|---|---|
|#45|OPEN，原冻结资料348c68def84ccc627a46be57ace463ade3635a15，后续只更新基线|受保护auto-merge已开启；资料仍不打包Android|
|#48|OPEN，原冻结实现df0a615f78589e9c7df5352fda75f2364ebcddaa|受保护auto-merge已开启；合成目录不冒充正式资料|
|#50|MERGED，主线merge b1b2d93be2b6d002faaf842f2519fc3958aef85a|原计量原型/35测试已进入测试源码，尚非生产API|
|#51|MERGED至原feature base，edebcfdd197fef38c4347e0fbf33ee484be46b07|未误称直接进入默认分支；通过#52桥接|
|#52|MERGED，默认merge92f27e99e5567a9eb42bbb3f19a577238ca97c5c|影子测试/真实数据合同已集成|
|#53 P0|MERGED，默认mergecfbe62c4a466f55234d53fbeff4c7f2c03224d50|修复真实非空恢复/清库，原生三API验证；主线CI通过|
|P1|独立分支，PR尚未创建|已合入主线cfbe62c；Room11/业务Schema10实现与完整本地回归|

实施顺序P0→P1→P2/P3→P4→P5，各独立PR。用户后续已明确授权受保护自动合并（REQUIREMENTS §103），不授权绕过检查、修改保护规则或合并无关PR。
共享交点：core/data实体/Room/SchemaGuards/NotesRepository/DataTransfer/BackupValidation、ViewModel/AppShell、四语、CI。串行集成，避免同文件并写；不强推、不重写冻结资料；#34及PK研究PR不属于此队列。
