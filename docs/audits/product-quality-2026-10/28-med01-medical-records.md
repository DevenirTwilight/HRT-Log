# 28 — MED-01 医疗档案现状审计与A验收

基线fe8798634ccd0d92d2913f384abfcfff94d32274；Android38470156117 attempt2三作业success、Science38470156282 gate success/deep按纯文档规则skip。R15 PR25 OPEN，HEAD239a73c，独立分支保留，不复制未合并功能。MED-A独立分支，尚未实施/验收。REQ87。

| 已有能力 | 真正缺口 | 可复用组件 |
| --- | --- | --- |
| Visits/Appointment：医生practitioner、机构location、类别type、带zone的UTC时间、note，completed_utc只表示用户确认，不因过去自动完成 | 集中档案入口；过去的预约≠真实就诊；一次原子录入过去就诊与确认，成功前不关窗、失败后不重复写入 | AppointmentEntity、VisitsScreen、VisitDetail、DateTimeRow、既有事务 |
| visit_question保存问题/回答及显式ASKED状态；VisitPack保存不可变生成元数据，PDF实际在用户SAF目标 | 单独问题删除没有独立回收（既有R26），不是完整医疗文件归档；不得把回答解读为诊断 | 就诊删除既有Trash.APPOINTMENT原子快照包含三表，恢复同ID及关联；本期整次就诊恢复复用 |
| Labs有数值/单位/方法/参考范围/采样UTC+zone/机构/备注；LabContextEntity保存版本化采样上下文 | 原件缺失；无“属于本次就诊”的显式FK，日期邻近不能推断归属 | LabsScreen、LabDialog、LabContextSection，保留同一lab ID，不复制化验 |
| Longitudinal有规则版本/真实服药/化验/预约/里程碑事件及历史快照 | 汇总时间事件不是结构化诊断/文件库 | 链接现有HRT时间线，只浏览上下文，不建立隐含就诊归属 |
| PDF/CSV/VisitPack离线通过SAF输出，已有明确隐私告知；PDF是生成摘要 | 输出的文件不在app安全附件库，不能把SAF URI/路径当永久证据 | ExportPrivacyNotice、Session.launchPicker；B需新的可恢复稳定对象ID |
| SQLCipher数据库，Keystore AES-GCM包裹随机32字节DB密钥；PRIMARY/DECOY独立库/key/alias，应用FLAG_SECURE/锁定 | 当前DECOY是保留兼容的旧空间，不能假设伪装登录打开该库；full伪装为独立加密私密笔记，不显示医疗页 | DatabaseAccess.pinnedTo、现有Session和PrivateNotes，保持现有认证路由 |
| PNBAK1 Argon2id(32MiB/3轮/p1)+AES-GCM，JSON表数据整包；最大文件/JSON32MiB、100000行、单字符串1MiB，恢复验证后事务替换 | 不适合大量BLOB/base64文件（膨胀/内存/上限），不能偷偷增加附件目录后仍叫完整备份 | A新增记录在既有三表中正常备份；B独立版本化容器设计保留旧PNBAK1读写 |
| SAF导入、B2私有临时SQLite复制锁/白名单清理/进程死后清理、密码清除、恢复防迟到回调 | SQLite临时明文策略不是附件默认安全方案；PDF随机访问与缩略图cache必须另验 | 可复用操作生命周期/清理边界，不复制明文附件到cache |

## A实施边界

用户确认“已经发生”才写completed_utc；就诊实际时间仍at_utc，确认时间与发生时间区分。过去未确认预约可从档案打开既有详情明确确认，不伪造事实。历史编辑保留原zone；日期范围按该zone的日边界，稳定UTC/id排序。关联问题、回答、生成包始终保留原ID；化验只作为用户选择范围内上下文，详情复用现有化验组件。整次就诊删除/撤回二次确认，删除到原回收站，可原子恢复；自由文本只存用户所写，不解析医学结论。

现有“单独问题删除无独立回收”是审计发现的旧缺口，MED-A医疗入口不新增该不可恢复删除路径；整次就诊删除及恢复包含问题。以后独立R26/附件元数据规则扩展再确定。

B设计见29，不实现文件导入/预览、新表、新备份格式或加密机制。后续红测、失败修复、全量结果及最终SHA/PR/CI必须用实际结果补充，不能用本审计代替代码。

## 红测与设计阶段记录

scope89a9277 Android38489468115三作业、Science38489468353均实际success。SDK28仓库13项红测实际13失败、0skip，13.093s，构建1m5s；临时UnsupportedOperationException seams只用于红测，生产提交前替换。两次测试返回类型写错的编译失败不当作红测；已按saveLab实际Unit返回，改为检查持久化lab ID，原业务断言未减少。四语UI/重复提交红测仍在运行，尚不计作通过。

MED-B设计29完成：推荐元数据+逐对象/版本DEK分块AEAD、独立空间KEK及可移植密码备份；未知SAF长度用认证结束清单而不是提前猜总长。PDF公开ProxyFD无明文预览必须实证，失败不采用明文缓存。新Schema/备份/加密及配额/旧备份关联恢复均等待产品批准，不阻塞A。没有实施附件机制。
