# ANSM-03A 验证账本

状态区分PASS / FAIL / SKIPPED / NOT_RUN / NOT_IMPLEMENTED。最终HEAD与六项必需CI以本PR正文和检查页固定，避免文档自引用SHA循环。

## 先行证据与修正历史

1. 在计量实现前提交3e0e0e13fc6e130b24bd550bc56fd4868a1e6deb：量纲合同、数据流、LegacyBoundaryTest，4/4 PASS。
   首个本地JVM因未为Java配置已有代理，在测试前停止：NOT_COMPLETED，不算通过；配置代理后同断言通过。
2. 新核心测试在实现文件未加入前运行，compileTestKotlin因未解析的新API失败；这是**编译红**，不是运行时负向断言或生产回归失败。
3. 首轮核心实现23项（含4旧边界）有1个FAIL：不同计数维度先报身份不同，测试要求单位不兼容。
   修复函数检查顺序，保持原断言。随后29项及全部JVM通过。
4. 追加审查收紧部分泵/贴/胶囊、禁止化合物等效/释放推导，增加实际使用正数与独立事实冲突检查、快照所属/版本、显式逆关系性质测试。
   最终计数以实际XML和local-verification.json为准，不用旧运行覆盖新源码。

5. 后续独立审查发现remaining可接受空白产品/摘要身份；新增1项负向测试实际运行1 fail，
   补上完整ref校验后全JVM297（新增35）通过，原断言保留。精确红阶段源码哈希、失败消息与日志哈希见negative-evidence.json。

## 测试范围与限制

|范围|证据/边界|
|---|---|
|数学量纲与包装|MeasurementCoreTest；质量SI、质量/体积/次数、语义身份、分数/分片证据、单向关系、多成分、不同规格、声明冲突、精度/范围与未知拒绝|
|确定性性质|固定种子1000样本质量逆换算，每样本重复5轮；1000片数线性/确定性；1000显式分数片有向逆换算；无额外性质库|
|冻结历史协议|SyntheticHistoryContractTest；显式单容器oracle，仅不可变输入、操作重放/冲销、余额、作废标记、历史版本与时区无关的数学；不是生产allocator/数据库/备份验收|
|旧信息不足|LegacyBoundaryTest合成反例4；不修改旧库存算法以制造失败|
|现有真实业务回归|原SupplyLedger/StockForecast/StockCorrection/PlanEdit/DataTransfer/AttachmentMigration/PeriodStability/BackupHistory/Translations；最终CI再跑全部既有检查|
|主代码隔离|scripts/ansm03_report.py核对protected路径git对象与差异、JVM main jar无measurement类、源码哈希及实际JUnit XML；脚本不联网|
|正式新表/新备份/新扣减|NOT_IMPLEMENTED；不能用合成oracle成功冒充03B完成|
|实际手机、真实ARM、TalkBack|NOT_RUN；本轮无UI，没有新增截图或安装包|
|research-deep|若原Science路径门控未命中则SKIPPED；不改门控，不称执行成功|

## 本地最终实际结果

- PASS：JVM297（domain98含新35、PK124、importer12、attachment-poc63），零失败/错误/跳过。
- PASS：数据层定向32、应用定向23（PeriodStability5、BackupHistory16、两项四语），零失败/错误/跳过。
- PASS：Python全部107项，包含新增3项隔离守卫。生产保护对象相同、main JAR无计量类。
- 新集成/新迁移/新备份：NOT_IMPLEMENTED；物理设备/真实ARM/TalkBack：NOT_RUN。
- 最终完整远端CI与SHA：见PR50正文和检查页，不以本地通过替代。

## 可复现命令

使用JDK21、仓库Gradle；Android测试需SDK37与原有Java代理/证书配置。只设运行时变量，不改Gradle：

```sh
./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test :attachment-poc:test --no-daemon --max-workers=1
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew :core:data:testDebugUnitTest --tests '*SupplyLedgerTest' --tests '*StockForecastTest' --tests '*StockCorrectionTest' --tests '*PlanEditTest' --tests '*DataTransferTest' --tests '*AttachmentMigrationTest' :app:testFullDebugUnitTest --tests '*PeriodStabilityTest' --tests '*BackupHistoryTest' --tests '*TranslationsTest' --no-daemon --max-workers=1
python3 scripts/ansm03_report.py --baseline 2f4b958425e1cd67dad1d97deb8ab724eb8ecac1 --output /tmp/ansm03-report.json
```

本地使用-Xmx1536m与Kotlin in-process限制同时运行内存；所有项目文件配置原样保留。
报告读取磁盘实际XML；必须和该次命令完成/源码哈希一同使用，不能把旧XML当未执行的新测试。
没有删减/跳过/放宽任何既有断言。完整构建、No INTERNET、PK科学守卫、锁/伪装、API26/29/35真实模拟器、Room1→10/PNBAK/强停等由最终CI确认，未完成前标NOT_RUN。

## 审计提交远端基线证据（不是最终实现CI）

3e0e0e1仅含4项旧边界测试，push Android38862315036五job与Science38862314983 gate全部成功。
已实际下载/校验5份artifact SHA/CRC，无APK；机器证据在repository-audit.json的audit_commit_ci。
JVM266（domain67/PK124/importer12/POC63），应用1198/0fail/205既有skip，data180/reminder19；lint0error/185warning。
API35 data137/app264（8既有skip），API26/29各data70/app70；无fail。
这些数据建立旧生产基线；最终实现应domain98/JVM297且新增35零skip，须由最终HEAD重新确认。

最终实现推送前正常合入PR49的文档主线2f4b958；所有生产代码/配置及计量源码哈希不变，
无需把同源码数学验证重复冒充新证据。相对最新主线的保护对象与源哈希验证追加repository-audit.json。
