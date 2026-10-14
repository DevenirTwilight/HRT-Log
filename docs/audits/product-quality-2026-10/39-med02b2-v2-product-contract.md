# 39 — MED-02B2 / B2-GATE：PHR V2 产品契约

2026-10-14，DESIGN / TEST_ONLY。不启用附件，无正式writer/reader、新备份或迁移批准。
当前基线：默认claude/new-session-1959qb；代码合并be953672f503940a10880e23dabaaa853d3017bb，交接cfe600eeb6efcca60668763a31969af07a2892b9。27/28/29均MERGED；旧stacked假设撤销。最终主线CI、独立分支/PR见42。

## 来源补齐与决策变更

[原始资料目录](sources/med02b2/README.md)中的四份Markdown已完整阅读并原样保留；SHA清单证明没有把后续修订写回原稿。此前SOURCE_MISSING仅表示接收补充ZIP之前；现SOURCE_SUPPLIED_READ。竞品官方产品描述按用户研究归因，不冒称本轮安装/安全审计；实施授权仍由任务书限定。

| 依据 | 接受的契约/修正 | 计划影响 |
| --- | --- | --- |
| V2 §1、§3.2.1、§4 | 存得住→找得到→用得上；复用MED01档案页，Inbox/最近/全部/类别/临床时间线/回收站 | B4子页面，不能在B2添加空入口 |
| V2 §3.2.2、§7、§9.1 | V1完整门槛包括图片+多页PDF；图片可独立Beta，但不能称完整V1 | B5不可无限延期；B3完整备份仍在前 |
| V2 §3.3、§5–6 | 机构/来源/日期精度、未分类和未关联彼此独立；自报机构不是签章核验 | B4需独立additive ADR，不偷塞B1八表 |
| V2 §3.3、§4.3、§8 | 一报告多Lab、多文件一就诊，双向跳转；同日不同机构不合并 | B4显式typed链接；C1报告/标本分开 |
| V2 §3.3、§4.4、§6 | 分享默认不选敏感资料；目录预览、摘要/原件区分、所选版本固定 | B5基础原件+清单，C2扩展既有VisitPack |
| V2 §3.3、§6 | 注释/页码/更正版替代关系独立元数据；不修改原件字节 | 简单文字B4；页码注释后置，复杂redaction延期 |
| V2 §3.3、§6 | 完整备份覆盖健康度、原件缺失/坏认证/隔离有可见状态 | B3提供真实receipt与图证据，B4展示；不保证云同步 |
| V2 §3.2.3–7、§9 | 配额仍候选；哈希不决定医疗归属；FHIR语义参考而非复制表 | 本批16MiB+1拒绝；大容量需独立预算/ARM审批 |
| V2 §3.4 | OCR/全文检索/DICOM/签章/自动互通/复杂遮盖延期，医院抓取/账号/云AI排除 | C3独立评估；不增加INTERNET |
| 欧盟原文§1–3 + 修订§1–4 | 隐私P0、预期用途与法规变更门、无障碍及分发审计独立 | 合规矩阵单列；不采评分/绝对豁免/历史结案 |

## 用户行为与验收契约

| ID / 工作流 | 行为及失败边界 | 阶段 |
| --- | --- | --- |
| UX01 存报告 | SAF临时URI仅本次读取；实际计数而非声明size，用户可取消；失败保留旧active，不显示半文件 | B2正式实现须再批准 |
| UX02 稍后整理 | 原件完整认证才保存；无需先建不存在的就诊/化验。未分类≠未关联；无附件开启前隐藏入口 | B4 |
| UX03 找原件 | 标题/类别/用户标签/机构/临床日期/签发日期/导入日期/格式/显式就诊Lab筛选；首期仅元数据搜索 | B4 |
| UX04 双向证据 | 从Lab到用户明确链接报告，从报告到多条Lab/Visit；同日/同hash不建立临床强关联 | B4/C1 |
| UX05 纠错 | 文字、日期、分类、链接CAS可撤回；内容替换新版本/DEK，旧版保留；Trash恢复同stableUUID | B4 |
| UX06 损坏 | 缺失、坏tag、密码PDF、旧身份隔离分别说明；不显示空白装正常，不删原件掩盖错误 | B4/B5 |
| UX07 为医生精选 | 选择实际用药/化验/就诊问题/原件版本，显示清单/大小/未包含项目，默认无全库和研究估算 | B5原件清单；C2VisitPack |
| UX08 导出 | SAF可能云端，外部明文副本/EXIF不能收回；真正遮盖需移除底层内容的新副本，叠黑框不算 | B5，复杂遮盖延期 |
| UX09 恢复 | 全包认证及图/所有保留版本/可携DEK材料通过才切CURRENT；失败旧数据保留；旧PNBAK隔离 | B3 |

## 字段语义与B1八表复用/缺口

- 临床日期（采样/就诊）、签发日期、imported UTC不同；临床可未知，支持仅年月/仅日期/精确时刻与明确zone。时间排序：已知临床日期→精度确定的区间→导入时间→稳定document UUID；未知放“日期未知”，不假填当天。范围不能自动构造LabPanel或同一就诊。
- sourceType USER_UPLOADED / USER_TRANSCRIBED / PROVIDED_BY_CLINIC_CLAIMED是用户陈述；来源SAF provider只保存明确最小描述（待审字段），不保存可重用URI权限；title不是文件路径。
- 敏感标签是本地分享默认值，不是医生ACL；所有版本/回收站占quota。

| B1表/现有字段 | 已有用途 | V2缺口及处理 |
| --- | --- | --- |
| attachment_store | CURRENT dataset、cryptoVault、epoch、默认关闭 | 不新增伪装域；无需字段改动 |
| attachment_record_identity | typedUUID/legacyID/墓碑/trash证据 | 时期/报告/标本非当前typed target；C1独立ADR |
| attachment_document | title/category/revision/state/activeVersion/deletedUtc | 临床/签发/import日期、精度zone、机构、来源、标签、敏感标记、Inbox需B4独立additive扩展设计 |
| attachment_version | 不可变UUID/MIME/hash/长度/ordinal/status/DEKhandle | 页数/解析状态、替代来源/原文件名策略后续ADR；不可改原hash/字节 |
| attachment_link | 显式document↔typedrecord UUID、revision/state | 多链接已有底座，UI/双向查询尚未实现；不创建第二套Lab/Appointment |
| attachment_operation | ownership/attempt资源、reservation、phase、幂等UUID | B2 durable kind-specific状态图、错误/cleanup证据、日志持久化API未实现；不改Schema |
| attachment_generation | 物理代际/manifest/state | 真实FS协调和恢复receipt后续B2/B3，不扫描自动激活 |
| attachment_key_envelope | device角色/alias/bytes/wrapEpoch | portable格式/KDF/专家审核B3；device私钥不导出 |

## 三个任务的流程与小屏/无障碍合同

存报告：选择→检查/取消→完整认证→稍后整理或分类→可选明确关联。
查化验：已存在Lab详情→用户链接原件→原件关联清单；未关联显示“尚未关联”，不给假证据。
为医生：选就诊/临时包→勾选少量事实和原件版本→清单预览→确认目标→输出，失败不记成功。

四语候选（仅设计，不新增生产strings或按钮）：

| 用途 | 简中 | 繁中 | English | Français |
| --- | --- | --- | --- | --- |
| Inbox | 待整理 | 待整理 | Inbox | À classer |
| 记录备份 | 仅结构化记录，不含医疗原件 | 僅結構化記錄，不含醫療原件 | Records only; no medical originals | Données structurées uniquement, sans documents originaux |
| 来源 | 机构由用户填写，未经核验 | 機構由使用者填寫，未經核驗 | Institution entered by you; not verified | Établissement saisi par vous, non vérifié |
| 导出 | 外部副本无法收回 | 外部副本無法收回 | External copies cannot be recalled | Les copies externes ne peuvent pas être rappelées |

320dp/2×字体：单列、长标题可换行、每批20、稳定ID key、有界lazy列表、所有确认/取消按钮可滚动到达；TalkBack焦点依次标题→日期及来源→选取状态→预览→确认/取消；坏文件不能只有颜色提示。未来UI端到端四语NOT_RUN，本批没有伪造页面验收。来源完整性≠机构真实性。

## 分阶段出口

B2-GATE本PR：需求/协议比较、真实候选实验和隔离失败合同，生产NO-GO。
B2-IMPLEMENTATION：另批批准具体协议、key与FS/SQL持久化接线；默认关闭。
B3：完整便携医疗备份/跨设备恢复；B4：图片/Inbox/元数据搜索/链接/纠错；B5：多页PDF/精选原件导出；B6：整套V1图片+PDF+备份+ARM/无障碍验收。
C1：LabPanel/标本和多Lab证据；C2：扩展已有VisitPack；C3：本地OCR、全文与FHIR/CDA互通独立评估。不得以规划等于生产批准。
