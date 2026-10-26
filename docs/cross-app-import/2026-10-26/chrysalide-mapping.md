# Chrysalide v5 与 TransMTF 导入映射（PR-D）

格式核对基线：Chrysalide 上游 `kushiemoon-dev/chrysalide`，commit `73ea9436c037ea966285fd0b46934394f06ef3a0`（MIT）；TransMTF 上游 `TransmtfTeam/Transmtf-HRT-Tracker`，commit `8c9abdde36494719dd8b55be03a0aadc79269ccb`（MIT）。这里只依据公开字段/语义独立实现，没有复制上游代码。

## TransMTF

TransMTF HRT Tracker 当前导出为 `meta.version=2`、`events`、`labResults`、`gelProducts`、`weight`，与 HRT Log 既有 HRT Tracker JSON 结构同构。`HrtTracker.kt` 已把 `timeH` 作为 Unix epoch hours 转为时间、把 `doseMG` 原样作为实际记录化合物质量，保留 route/ester；patch removal、未知路线和不完整自定义制剂按原规则跳过/报告。本阶段没有新建 TransMTF 来源 detector 或改变已保存来源身份；格式无法从同构 HRT Tracker 文件区分时仍显示原有 HRT Tracker 识别标签。

## Chrysalide

仅支持经核对的 `exportAllData()` v5 形状。检测要求版本、导出时间、全部 v5 集合以及 medication/log/lab 行特征；输入仍由统一 SAF 读取一次、受 32 MiB/深度/行数上限保护。v6、缺集合、坏引用、重复/不安全整数 ID、损坏字段均拒绝。非 Chrysalide JSON 不会因通用 `medications`/`bloodTests` 字段通过。

| 上游数据 | HRT Log 行为 | 语义限制 |
| --- | --- | --- |
| `medicationLogs` (`taken=true`, ISO `timestamp`) | 用户显式选择已有 HRT Log 药物后保存为 `IMPORT_CHRYSALIDE` 服药历史 | `actual_dose=NULL`，明确显示未知；不使用当前 medication dosage、`scheduledTime`、剂次序号或计划补造剂量/漏服；来源不提供 timezone，绝对 instant 原样保存并标为 UTC；来源 log 与 medication 原文快照保留 |
| `taken=false` logs | 仅报告数量，不写入 | 不等同漏服 |
| `bloodTests` / `results` | 读取及校验原始分析物、数值、单位并显示未导入数量 | 源界面为 HTML date-only；Dexie Date 的 JSON ISO 表示不能恢复用户时区或采样时刻，故不写入 target 的有时刻化验表；不换算单位、不伪造 panel |
| `medications` | 仅用于来源事件说明和用户显式映射 | 不创建或启用当前药物/方案/提醒，不将 regimen dosage 当历史事实；无已导入 taken log 的定义计为未迁移 |
| 计划、库存、提醒、用户资料、日记、目标、里程碑、治疗变更、医务人员、医疗行动及附件照片 | 按集合逐项报告未导入数量 | 不创建提醒、库存流水、PK/临床事实或 MED-02 附件 |

来源 `id` 是安装内自增值，没有可验证 installation UUID。来源凭据以完整导出内容与具体源记录计算 digest：同一字节内容可幂等重导；同一数字 ID 的不同导出/内容被报告为歧义冲突，不覆盖、重建或自动关联。记录被本地编辑、软删除或永久清除后分别报告冲突/删除/tombstone，普通与完整备份保留凭据。导出日期变化导致同一数字 ID 被视为冲突是有意的 fail-closed 行为。

### 数据结构兼容

Chrysalide 需要可表示“来源确认已服用，但没有实际剂量”的历史行，不能安全塞进 Mona/Featherline 凭据。独立表 `chrysalide_import_item` 与 `IMPORT_CHRYSALIDE` 来源、独立 scope/事务在 Room 20（非破坏性 19→20）中增加；仅存在该事实或凭据时输出 business backup schema 16。无 Chrysalide 内容仍保持现有 12/13/14/15 序列化语义；PNBAK1 外层不变，1–15 reader 继续工作并拒绝 v16。完整资料库集合/重建表加入 schema 16。历史、软删除、回收站、永久清除墓碑随来源凭据验证。未经用户确认的药物映射不会写入。

## 本地验证状态（当前工作分支）

- `:importer:test`：PASS（Chrysalide v5 parser/detector、时间和剂量未知规则、歧义拒绝、HRT Tracker/TransMTF 兼容回归）。
- `:core:data:compileDebugKotlin`、`:core:data:compileDebugUnitTestKotlin`、`:app:compileFullDebugKotlin`、`:app:compileFullDebugAndroidTestKotlin`：PASS；Room KSP 生成 schema20。
- `:core:data:testDebugUnitTest --tests net.plainnotes.app.data.ChrysalideImportTest`：NOT_RUN（Robolectric 测试启动失败，尚未运行测试体）。原始 Gradle 日志与 XML 保存在 `evidence/chrysalide-robolectric-test.log` 和 `evidence/chrysalide-robolectric-test.xml`。可证根因是无法连接以下载 `org.robolectric:android-all-instrumented:9-robolectric-4913185-2-i7`，故不是断言失败，也不是产品路径通过。
- 实际系统 DocumentsUI → 预览取消/确认 → Room commit → 重开与重放的三 API 自动化已扩展，但本机尚未运行。CI 与 native 运行绑定最终 PR SHA 后报告；物理设备与人工 TalkBack 不冒称已执行。
