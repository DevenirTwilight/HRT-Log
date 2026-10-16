# 医疗中心未开放区域 UI 核查

2026-10-16，只读源码审计。默认分支claude/new-session-1959qb；核对主线基线0dca778396619a005144dab044eaf4eafeafad40。医疗相关源码未因P2药品引用界面改变。

- 医疗档案首页、就诊、化验、历史索引和问诊资料：生产UI已存在。见app/src/main/java/net/plainnotes/app/ui/MedicalRecordsScreen.kt、VisitsScreen.kt、LabsScreen.kt，以及medical/MedicalRecordIndex.kt、visit/VisitPack.kt；AppShell的MEDICAL/VISITS/LABS导航ready=true。
- 未开放的医疗附件/文件库：未发现正式文件列表、导入、附件详情或预览页。MedicalRecordsScreen的生产注释明确“no file or attachment entry”。不能说完整UI已经写好，只需开关就能用。
- 已有附件数据库/身份/密钥/操作日志基础：core/data/src/main/.../Attachment*.kt。feature_enabled默认0，AttachmentSchema写入守卫要求feature_enabled=0；基础设施不代表UI或正式功能已完成。
- PDF/图片读取与安全预览：app/src/androidTest/.../attachmentpoc、core/data/src/androidTest/.../gate2、tools/attachment-poc为隔离测试/研究代码，不能算正式医疗附件UI。

状态：SOURCE_AUDITED。此次未启用附件、未更改开关/Schema/UI导航、未导入真实健康资料；实机显示及TalkBack NOT_RUN。后续医疗附件功能仍需独立实施与安全准入，ANSM授权不包含启用它。
