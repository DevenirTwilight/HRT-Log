# BDPM 离线身份目录（实现与验收）

**最新范围变更**：负责人确认仅分发宽范围HRT相关成分子集，420产品/550包装/702组成、352KiB；完整官方原始归档仍保留。以下全量计数与11MB为此前阶段记录，现行构建及边界见[hrt-subset.md](hrt-subset.md)。

基线 aaab9669；独立 feature/bdpm-offline-catalogue-20261022，不使用/覆盖库存工作树。真实全量三份数据已下载转换；本次明确授权结构化身份和关联，不延续此前部分摘录的分发OFF，也不开放#58正文。

来源：BDPM (ANSM/HAS/UNCAM)。生产下载域本次503；实际源 https://bdpmt.ansm.sante.fr/telechargement 为ANSM替代站点，**带TEST横幅，不能声称最新生产导出**。下载2026-10-22，身份/组成公布日期2026-09-29、包装2026-10-13，均是网页显示日期而非已知导出时间。data.gouv标记fr-lo，下载合同明确Licence Ouverte1.0；页脚2.0链接不用于替代所附合同。1.0允许提取/转换/再分发，要求注明来源/日期、不暗示背书；本应用同时满足这些归因限制。不包含RCP/Notice正文、监管公告或临床建议；这些仍独立未开放。

`tools/bdpm/sources/manifest.json`保存URL/日期/编码/原始字节SHA/数量；gzip为原始字节无损归档。`docs/bdpm/source-evidence/`保存实际下载网页、v4/v5格式和许可及SHA。v4/v5写ST，但本次数据实际26915 SA/5524 FT，保留原始类型、关联编号、元素、物质代码、原剂量和基准，不合并、不做数值运算。全量CIS15883、CIP20908、组成32439；4条孤立CIP因缺CIS父记录保存在orphan_package，不编造商品，也不悄悄丢弃。

离线构建：`python3 scripts/build_bdpm_catalogue.py`，从归档原字节校验后生成core/data资产。更新：维护者显式运行同脚本`--download`（需要开发机网络），重新审查日期/计数/差异/孤立记录及许可，提交源和生成资产，运行相应测试；App不联网、不下载更新。SQLite独立只读，随APK资源压缩，不进入SQLCipher Room，不存用户查询、不进备份。首次复制/校验/检索均IO，坏副本重试修复只影响noBackupFilesDir/bdpm，绝不删用户数据库。

最小快照方案：复用reference_snapshot已有namespace与JSON，无Room迁移。旧USER_ENTERED format1及其规范字节不变；新增BDPM_FR format2/SOURCE_RECORD及有界bdpm来源元数据（数据库SHA、各源日期、下载URL、选中包装原文/CIP7）。ingredients逐行保留全部组成关系；CIS和CIP13保持字符串。沿用ReferenceStore原UUID幂等/预期revision/不可变追加历史，不修改药物、计划、PK或库存。旧快照永不从新目录重建。

备份业务兼容：仅含官方快照时导出schema13，旧资料备份继续schema12。新读者兼容1–13；旧最高12读者在打开数据库前拒绝13，低版本伪报官方来源拒绝恢复。PNBAK1/密钥/加密参数不变。Room仍13，没有迁移或改变现有SQLCipher表。旧App不支持查看新来源，不能声称可降级读取官方快照；不清除/改写旧资料来迁就旧读者。

当前编码已接搜索→包装选择→现有明确确认关联；尚未完成编译、测试及PR CI，不能视作整项功能完成。首次无资料、estradiol成分、真实Compose入口、保存/取消/重开/错误/恢复、用户资料及备份回归待验证；真机/TalkBack/断网模拟器均NOT_RUN。不签名、交付APK或发布Release，不跑P4性能。

来源交叉核对：本次CIS和COMPO的完整SHA与仓库10月17日从生产域记录的SHA逐字相同；此前从生产域保存的96条选中包装，其CIS/CIP7/CIP13/包装原文亦全部一致。当前CIP全文件SHA不同，仍按实际替代源及13/10公布日期记录，不追认最新生产快照。见source-evidence/prior-production-crosscheck.json。

文件所有权：本分支拥有BdpmCataloguePanel、MedicationReferenceDialog、DrugReferenceCenterScreen及专用资源/构建脚本；不改AppShell、NotesViewModel、P4 writer/验证架构或未合并#58。共享数据文件仅增加官方payload与备份版本选择/拒绝规则；差分随独立PR审查，不拷贝其他工作树。测试专用SQLCipher编译依赖复用已在core:data运行时存在的库，不新增release依赖。

当前本地：真实App四语言320dp/2x关联/取消/重开和未保存草稿返回8项PASS；真实文件系统失败→明确不可用→重试恢复→无匹配4项PASS；官方核心4项与译文3项PASS，native源码编译PASS。新增官方解除关联最后验证及最终PR必需CI尚待完成；不把JVM或编译称作设备执行，真机/TalkBack NOT_RUN。历史失败及实际输入清空的RED保留。

最终本地源码170345c7：新来源4+旧Payload10、四语言官方完整关联/取消/重开/解除4、两个native合同源码编译PASS（final-local收据）。最后来源标签校正随最终PR CI验收；此前草稿/故障8项各自的通过和原始失败均保留，不冒充同次或真机结果。

协作补充：未合并PR58与本分支均涉及MedicationReferenceDialog.kt，后续必须逐段人工集成；不覆盖其未审工作。
