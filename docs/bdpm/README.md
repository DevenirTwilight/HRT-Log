# BDPM 离线药品目录

当前随APK分发**HRT相关成分子集**，不是法国全部药品。负责人2026-10-22确认宽范围：雌激素、孕激素、睾酮、抗雄激素及GnRH辅助药。420个CIS、550条包装、702条组成，SQLite 360448B（352KiB）；规则、覆盖边界、更新办法见 [hrt-subset.md](hrt-subset.md)。收录不是适应证认证或治疗推荐，也不声称全球HRT全集。此前全量构建11116544B/15883产品为历史阶段，现已由子集取代。

## 来源与许可

下载2026-10-22。生产域当次503；实际使用ANSM替代站 <https://bdpmt.ansm.sante.fr/telechargement>，页面有**TEST横幅，不能声称最新生产导出**，界面明确显示此限制。原始身份/组成公布日期2026-09-29，包装2026-10-13；日期为下载网页显示值，不是已知导出时间。完整官方原字节：CIS15883行、CIP20908行、组成32439行，留在tools源归档供复建，不随APK分发。

CIS/COMPO完整SHA与仓库此前生产下载记录相同，96条既存生产包装的CIS/CIP7/CIP13/原文全部匹配；当前CIP全文件SHA不同，不追认最新生产快照。原文件编码、URL、SHA、记录数在 `tools/bdpm/sources/manifest.json`；网页/格式v4/v5/实际许可及交叉核对在 `source-evidence/`。

实际下载合同为Licence Ouverte1.0，data.gouv.fr标记fr-lo；页脚2.0不用于替代所附合同。保留来源/日期且不暗示背书。没有RCP/Notice正文、监管公告或临床建议，未开放#58正文。

## 数据和关联边界

按SA有效成分代码选择产品，保留命中产品全部原组成、剂型、途径、规格、包装及CIS/CIP字符串；原SA/FT/ST与关联编号分别保留，不合并、不计算剂量。完整源4条孤立CIP在全量复建的orphan_package保留，子集不编造缺失父身份。

公共SQLite只读、随APK分发，与SQLCipher用户库隔离。复制、哈希核对、查询和快照读取在IO；坏公共副本可以重试修复，不删用户数据。无INTERNET权限，不上传查询，不需要联网初始化。

官方BDPM_FR/format2/SOURCE_RECORD与旧USER_ENTERED/format1明确区分。复用ReferenceStore的UUID幂等、预期revision、事务及不可变追加历史。保存源版本、库SHA、日期、选中包装原文/CIP7/CIP13；不推断实际包装，不改剂量、计划、库存或PK。目录更新不重建旧快照。

Room仍13，无迁移或SQLCipher表变化。备份业务格式：含官方历史快照时13，纯用户资料仍12；新读者1–13，旧最高12读者拒绝13。PNBAK1/KDF/密钥不变，公共目录不进用户备份，不声称可降级读取新来源。

## 实现与验证

来源切换、初始/搜索中/结果/空结果/不可用/重试、原名和包装展示、最终确认、取消、重新关联、重开及解除已接真实App入口。未保存药物可浏览，关联禁用并保留草稿。四语界面声明HRT相关子集，支持小屏/大字号的既有布局及选中页签/状态播报语义。

子集候选054c30ad：Python7项PASS；真实资产核心查询/快照/备份恢复4项、四语言Compose实际App/草稿/故障重试12项JVM PASS；隐私事实3项PASS，data/App原生源码编译PASS。原生和必需CI独立记录，不能将编译/JVM当设备通过。`validation/hrt-subset/receipt.json`绑定测试文件SHA；原始失败/RED及修复证据保留，不相加冒称同次全套通过。

054c30ad原生：API26/29/35目录4项各自PASS，API35真实App3项PASS，API29整体SUCCESS；API26两个单次Back断言FAIL，API35独立断网后续因AGP卸载instrumentation而NOT_RUN。两个原始失败及有证据的执行修正均保留，PR98当前Draft，最终候选必需CI待核实。真机/TalkBack NOT_RUN；不签名交付APK或发布Release，不运行P4性能任务。最新状态以HANDOFF及PR运行SHA为准。

## 协作

独立分支基线aaab9669，不覆盖库存工作树。拥有BdpmCataloguePanel、MedicationReferenceDialog、DrugReferenceCenterScreen及专用资源/构建脚本；未改AppShell/NotesViewModel/P4 writer/验证架构。共享数据文件仅增加官方payload/备份拒绝规则。

未合并PR58也涉及MedicationReferenceDialog.kt，未来必须逐段人工集成；本分支没有复制或覆盖其未审实现。使用现有SQLCipher测试依赖，不新增release依赖。
