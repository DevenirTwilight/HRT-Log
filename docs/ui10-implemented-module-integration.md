# UI-10 — 已实现功能的界面接线（2026-10-27）

## 任务编号勘误（2026-10-27）

UI-10 的真实交付只包括已有能力的界面导航、占位状态、文案与可达性，故继续保留 UI-10 / PR #135 的原始追溯编号。**完整医疗附件管理闭环不属于 UI-11**；其后续候选代号为 **MED-03**，见 REQUIREMENTS §156。UI-10 未开放统一文件库、文件对象回收/恢复或自动重新关联；本补充不修改当次测试/验收史实。

基线：`0daeb63cc47f7baac6c603735dd6307c085bbe08`。最终验收精确源码与 CI 链接以本 PR 的 check suite / 合并收据为准；这里不将待运行结果写成 PASS。

| 分类 | 能力与真实路径 | 边界 |
|---|---|---|
| IMPLEMENTED_AND_EXPOSED | 医疗中心 → 就诊附件 → 就诊 → 附件 | 复用 VisitsScreen / AppointmentEntity.id / RuntimeLibrarySession |
| IMPLEMENTED_AND_EXPOSED | 医疗中心 → 单项化验附件 → 化验结果 → 附件 | 复用 LabsScreen / LabValue.id；不是 LabPanel.id |
| IMPLEMENTED_AND_EXPOSED | 医疗中心 → 完整医疗备份；设置 → 数据与备份 → 完整医疗备份 | 共享 MedicalLibraryTransferDialog/Page；PNMEDBK2，普通 PNBAK1 不含原件 |
| IMPLEMENTED_AND_EXPOSED | 就诊准备、就诊资料包 PDF | 现有就诊详情，不创建新导出引擎 |
| IMPLEMENTED_AND_EXPOSED | 药品中心 → BDPM 离线身份/包装及用户保存资料 | 原有 catalogue / reference 入口，用户资料不是官方正文 |
| IMPLEMENTED_AND_EXPOSED | 批量化验、LabPanel、跨应用统一导入 | 保留现有导航和写入语义 |
| IMPLEMENTED_BUT_NOT_EXPOSED | 后端已有附件软删除等生命周期能力 | 本轮不开放新的管理操作；已公开的指定关系解除保留原行为，密文/容量不释放 |
| PARTIAL | 库存、医疗资料导出 | 现有库存操作、Visit Pack 和完整备份可用；不意味着 P5/P6 全流程、任意明文文件导出可用 |
| NOT_IMPLEMENTED | 全库统一文件浏览、独立医生/机构档案、文件分类/摘要/单文件任意导出 | 保留三张准确未开放卡；医生机构字段仍在就诊可用 |
| NOT_IMPLEMENTED | RCP、Notice、安全信息、短缺、召回/监管 | 药品中心五项继续 OFF；身份检索不代表这些正文服务 |

## 改动与安全边界

医疗中心只发出已有导航回调，不获取 capability，不推断任何记录身份。就诊/LAB 目标仍由原详情页构建。完整备份页面与设置共用一个生产宿主；导航开关只在内存，页面关闭销毁原 controller，现有撤权、UNKNOWN、事务回执和资料库边界不变。没有 Room、数据层、加密、权限或版本变更。所有 Destination 当前 ready=true，ComingSoonScreen 仅保留兜底，无新增可达占位。无生产引用的 ref2_official_off 是旧资源，未用它描述当前 BDPM 状态。

## 验证范围

- 现有 MedicalHome / AppShell 返回 / 中心矩阵测试同步真实三项未开放边界；新增完整备份入口回调、真实 NotesApp 返回无写入断言。
- 四语、320/411dp、1/2倍字号、深浅及对比度复用现有 MedicalCenterMatrixTest；自动语义检查不是人工 TalkBack。
- 现有真实 MainActivity MedicalAttachmentProductAndroidTest 改从医疗中心进入就诊和 LAB，保留真实 DocumentsUI、COMMIT、重开、预览、精确 ID、完整生产引擎备份恢复断言；另验证医疗中心备份页可达和返回。不声称测试已逐个操作归档 SAF/密码控件。
- PR #133/#134 的安全/恢复证据继续有效，不重跑无关低内存研究。最终 CI 自然触发的检查如实报告。
- 真机、人工 TalkBack、实际断电 NOT_RUN；独立外审 NOT_REVIEWED_EXTERNALLY。没有签名交付或公开发布。

## 本地执行记录

命令：`JAVA_HOME=/workspace/jdk21 ANDROID_HOME=/workspace/android-sdk ./gradlew -ProbolectricDir=/workspace/robolectric-jars :app:compileFullDebugAndroidTestKotlin :app:testFullDebugUnitTest --tests '*MedicalHomeTest' --tests '*AppShellBackNavigationTest' --tests '*CenterModuleScreenshotsTest' --tests '*MedicalCenterMatrixTest' --tests '*DrugCenterMatrixTest' --tests '*DrugCenterNavigationTest' --no-parallel --max-workers=2`。

首次未配置离线镜像的原始连接失败、离线初轮130项/1个测试输入FAIL及其XML保存在 `ui10-evidence/`。医疗/药品矩阵各48组、中心渲染8组、药品导航1、MedicalHome4、Ui05MedicalHome3通过。唯一返回FAIL来自测试直接调用底层Activity BackDispatcher；修正为当前Dialog窗口Back后，AppShell全套再次执行通过（原PASS日志及XML摘要入库）。原生测试用真实Android BACK而非此Robolectric辅助函数。没有为了求绿改生产权限、删除断言或跳过测试。
