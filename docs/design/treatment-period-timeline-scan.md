# Treatment Period / Timeline：Phase 1 源码扫描

2026-10-07。扫描基线 `9e55c10`，功能基线 `ecedb19`，分支 `claude/new-session-1959qb`。用户新附件 `HRT-Log-Codex-treatment-period-timeline-prompt.md` 已授权实施，不再受上一轮仅设计的范围限制；但附件明确要求第8项不能安全解决时先停下报告。本次不修改功能代码。

## 八项结论

| 项目 | 源码结论 |
|---|---|
| 1. 实际版本 | `app/build.gradle.kts:6`：versionCode 11、versionName 0.2.0；`core/data/src/main/java/net/plainnotes/app/data/NotesDatabase.kt:3`：schema 6。只维护full。README仍写build10/schema5与Visit Pack待实现，本次纠正；不改历史Release或标签。 |
| 2. Timeline | `app/src/main/java/net/plainnotes/app/ui/LongitudinalScreen.kt:29`：Timeline/Stages两种视图，默认90天；`timeline/LongitudinalProjection.kt`仍投影实际记录、漏服、症状、wellbeing及可选计划。不是附件要求的按时期组织的重要事件列表。 |
| 3. TreatmentEpochs | `core/domain/src/main/kotlin/net/plainnotes/app/domain/TreatmentEpochs.kt:16`：按精确UTC版本起止组合，只有相邻版本ID集合相同才合并。旧key为`epoch:<UTC毫秒>:<排序版本IDs>`。不读取实际服药状态，漏服本身不会切旧阶段；提醒变更若产生版本则会碎化。没有新版therapySignatureV2。 |
| 4. Milestone | `LongitudinalScreen.kt:30,90,178`：旧日期被90天过滤，阶段列表无里程碑；调用onSave后立即关闭，无saving/防双击/成功定位；草稿不是rememberSaveable。`NotesViewModel.kt:126`异步保存，错误提示不能恢复已关闭的输入。STARTED空title合法，重复STARTED合法。用户已确认在Timeline保存且日期超过90天；本轮只核对源码，未访问用户数据库或重新运行旧探针。 |
| 5. 冻结引用 | `core/data/.../LabContext.kt:15,31,56`使用V1 builder，保存version1 JSON、原版本ID与原epoch key，并校验key格式。`app/.../visit/VisitPack.kt:13,33,60,90`使用template1，started/ended计数是原始版本边界；digest覆盖选中事实及上下文。Visit Pack没有独立epoch key列，但不能让新投影改变template1的计数或digest输入。 |
| 6. 冲突 | 提醒时间/zone/phase进入旧signature；UI逐条展示执行记录、双视图及默认90天；缺保存确认/定位与可靠草稿恢复。旧数据也不能仅凭区间结束推断暂停原因。没有足够证据时只显示no active recorded regimen，不能写explicit stopped。 |
| 7. 保持schema6 | 分层投影、Timeline/History分工、里程碑保存与可见性修复原则上不需迁移。旧signature/定义/Context1/VisitPack1必须原样保留。不同剂量时段的跨版本身份是未解决范围，尚不能保证全部验收；也没有证据证明必须schema7。 |
| 8. Slot identity | **不足。** rule_time有行ID，但新规则重建行，冻结RegimenDefinition丢弃ID并按钟点排序。没有可追溯的跨版本时段身份。仅凭现存快照无法同时保证提醒移动不切时期与剂量分配交换必切时期，因此按附件停止功能实现。 |

## 第8项的可验证证据与歧义

- `core/data/src/main/java/net/plainnotes/app/data/Entities.kt:58`：TimeEntity只有id、rule_id、local_time、dose_override。行ID不等于跨规则版本的业务身份。
- `RegimenHistory.kt:13–20,39–40`：冻结定义只有clock/override；从TimeEntity生成时丢掉行ID，JSON按clock排序。
- `NotesRepository.kt:93,143`：编辑提交List<LocalTime>；新规则插入新TimeEntity，override全部设null。
- `app/src/main/java/net/plainnotes/app/ui/MedicationEditor.kt:32,59`：draft只有时刻列表；初始化按时刻排序，没有每时段剂量或原slot引用。编辑已有非均匀计划还可能丢失override，不能把后续剂量变化简单归为纯提醒修改。
- `NotesDao.kt:27`查询没有ORDER BY，不能用SQLite返回顺序弥补身份。

以下仅为合成示例，同一组旧/新快照可对应两种不同用户操作：

```text
旧：08:00 / 1 mg，20:00 / 2 mg
新：08:00 / 2 mg，20:00 / 1 mg

操作A：分别移动原两个提醒，1 mg时段移到20:00，2 mg时段移到08:00。
操作B：保持两个提醒不动，交换各时段剂量。
```

附件要求A不切、B切，但快照内容相同。按排序剂量向量判断会误判A；按剂量多重集合判断会漏掉B。08:00移到21:00导致排序翻转的例子同样不能用向量差异证明治疗标准改变。增加新列也无法恢复旧历史已丢失的意图。

## 保守范围（用户随后回复“继续”，按此范围实施）

优先schema6，不用时段排序虚构身份：

1. 均匀剂量、明确剂量集合/次数/频率/成分/途径变化按V2可判定事实处理；实际执行状态不参与。
2. 旧非均匀剂量的对应关系有歧义时，显示“时段对应关系未记录”，保留可查看的全部raw changes；不能宣称已经证明治疗标准相同或改变。可按钟点无关的剂量集合做保守显示归组，但须明确这不满足“识别所有历史剂量分配交换”的完整保证，不暗中包装成全部验收通过。
3. 未来编辑必须保留各时段剂量，并明确记录“移动提醒”与“调整剂量分配”的操作身份。评估在现有JSON中新记录独立版本化元数据的方案，或者后续获准的schema迁移。两者均不能回填猜测旧身份；现阶段还未实现或验证JSON扩展的旧备份/校验兼容性。

用户随后回复“继续”，本轮采用上述旧歧义保留unknown的有限保证。未来slot身份存储另批设计；当前实现不扩展冻结JSON，编辑先保留旧dose_override，不猜历史映射。

## 卡点解决后的最小实施计划

1. 里程碑：repository返回保存ID；ViewModel区分事务提交失败与提交后refresh失败；保存状态、防双击；UI草稿可恢复，保存后提示/定位，不隐藏旧日期。四语资源和失败路径回归。
2. Domain：新增V2类型与builder（RawInterval→StandardSpan→ClinicalSegment→DisplayPeriod），保留TreatmentEpochs原实现及key。精确UTC不倒推午夜，同日显示合并不删除A→B→A中间事实；gap后恢复另开时期。
3. Data adapter：只读解析冻结定义，按明确策略计算V2；不重写clinical_signature/历史行。旧冻结Context只读映射到当前显示时期，映射不写回JSON。
4. Projection/UI：独立重要事件projection，只列LAB/REVIEW/MILESTONE/APPOINTMENT；合并双视图，当前置顶、历史逆序、unknown和Upcoming独立。History ledger完整保留。详情按source ID定位，不在时期卡展示完整数值/全文。
5. 兼容：旧signature、validateLinks、schema1–6恢复、Context1和PK结果、VisitPack1 facts/digest全部原样；不碰库存ledger、PK、INTERNET或play。
6. 验证：合成domain/data/reminder/app回归与full lint/debug/release、manifest/schema检查、原生迁移/SQLCipher测试；覆盖附件stop/missed、时刻/DST、多药同日、草稿恢复、失败/双击、旧快照不变等要求。实际执行后再报告结果。
7. 文档/交付：及时提交推送HANDOFF；若构建交付只用原正式签名，不发布Release或改标签。

## 本次完成与未完成

完成源码扫描、过时README/路线图纠正、需求与交接更新。未实施新版Timeline、V2或里程碑功能修复；未升级schema/versionCode，未构建或交付新APK，未运行构建/测试。既有build11测试记录仍仅代表原功能，不能作为本次改版已验证的证据。
