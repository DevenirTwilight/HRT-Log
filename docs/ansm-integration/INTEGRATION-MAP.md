# 来源与集成图

默认分支2f4b958（最近#49仅交接/账本，#47 UI04）。当前无新的开放库存/迁移PR；远端无法证明其他人的未推送工作。

|来源|状态/HEAD|本轮处理|
|---|---|---|
|45|OPEN 348c68def84ccc627a46be57ace463ade3635a15|读manifest/来源；官方资源不打包|
|48|OPEN df0a615f78589e9c7df5352fda75f2364ebcddaa|后续显式merge复用UI/测试，正式入口另接|
|50|OPEN 606696edbccd6583a50d1fc28d6ed8069b2fcb5c|后续先引入原核心/35测试，生产化并差分|
|51|OPEN 74f2e6a327e44bb396dce46ce42d466fdcd3e65c，base50|在50后引入影子/真实合同，恢复预期从已知缺陷改为修复证据，保留故障回滚断言|

P0→P1→P2/P3→P4→P5，均stacked独立PR；受保护默认分支合并需另授权。
共享交点：core/data实体/Room/SchemaGuards/NotesRepository/DataTransfer/BackupValidation、ViewModel/AppShell、四语、CI。当前由本工作树串行集成，避免两个开发者同文件并写。
每次引入记录原始merge父SHA及最终目标提交；不强推或重写旧PR，不带入#34待独立处理的交接提交。
