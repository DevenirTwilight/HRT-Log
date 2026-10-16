# B0 验证账本

2026-10-16，全部为合成数据。测试状态与功能状态分列，不以测试PASS宣称生产已实施。

## 已运行及早期失败

- 原03A 35项：PASS，四个原文件SHA256与冻结PR50一致（repository-audit.json），没有改写断言。
- 影子新增23项，domain总121项：PASS（本地完整domain运行，0失败/跳过）。
- 数据层193项：PASS（本地完整Robolectric，包含新增13项）。
- 提醒20项：PASS（包含新增巴黎春/秋DST第6/7日边界与共享预测结果测试）。
- 7项ANSM Python隔离/原边界检查：PASS。
- 9598093首轮CI：JVM 320项全PASS（domain121/PK124/importer12/POC63）；数据193/提醒20/应用1198项0失败，应用205项原有条件SKIPPED。
- API26/29原生：各data83（含新增13合同）/app70，0失败/跳过。
- 首轮Android整体FAIL：漏声明生成目录到AndroidTest lint模型的依赖，后续manifest/release隔离等步骤因此SKIPPED，不是成功；本批补齐lint任务依赖且保留检查。
- API35原生：data150/app264，0失败，app有8项原有条件SKIPPED；新增原生合同13项0失败/跳过。
- 首轮5份实际artifact已下载校验SHA256/CRC且无APK条目，见first-ci-evidence.json；未下载的demo-evidence不计为已核验。
- 修正提交的最终六项检查及release APK隔离以PR #51最终HEAD检查和PR正文证据为准；本文件的首轮记录不代替最终结果。

早期失败保留：

1. AGP9不接受Sync TaskProvider直接作为java.srcDir：配置FAIL，测试NOT_RUN；改成确定生成目录并显式依赖测试编译/KSP任务，未放宽AGP设置。
2. 首次10项真实DB合同：2 FAIL。DDL注入下标包含不存在的第9条语句；改用明确声明的8条数量并校验列表长度。
3. 同一轮完整账本恢复自身触发既有自引用外键拒绝。没有修复生产或削弱外键；新增明确失败/全事务回滚断言，另测空目标成功恢复。见compatibility-findings。
4. 9598093的Android CI因generateDebugAndroidTestLintModel缺少Sync输出依赖FAIL；修正仅补lint任务显式依赖，没有跳过lint/改AGP设置。
5. 额外尝试compileReleaseUnitTestKotlin任务不存在，配置FAIL/测试NOT_RUN；tasks --all确认当前库只有Debug测试变体，未启用新变体。实际bundleLibRuntimeToJarRelease构建PASS，302类隔离检查PASS。
本地修正后`:core:data:lintDebug` PASS（1m25s），保留UnitTest/AndroidTest全部lint模型和分析任务。

6. 隔离检查曾因正在改动的影子源码与上次生成测试输入不同而FAIL；重新生成后通过。此检查保留，拒绝编译过期的副本。

## 重现

使用仓库JDK21及Android SDK37；单元测试需Robolectric依赖可访问。

```sh
./gradlew :core:domain:test :core:data:testDebugUnitTest :core:reminder:testDebugUnitTest :core:data:assembleDebugAndroidTest
python3 -m unittest discover -s scripts -p 'test_ansm03*.py'
python3 scripts/ansm03b0_report.py --output /tmp/ansm03b0-local.json
# 现有完整CI构建后，验证未混入正式main JAR/Release；APK只作既有CI检查，不交付
python3 scripts/check_ansm03b0_isolation.py
# 合成SQLCipher合同；CI在API26/29/35执行
./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.plainnotes.app.data.Ansm03b0InventoryAndroidTest
```

Gradle只将原实验核心与只读影子两个源文件生成到测试目录，shared代码仅test/androidTest使用；无新运行依赖。
local-evidence.json记录最后一次targeted数据层13项，完整193项另见首轮CI报告；没有把subset冒充完整重跑。

JUnitXML、实际逐事件JSON、测试源码哈希应配合提交SHA及命令结束状态阅读；目录中存在旧报告不能证明新HEAD成功。
首轮CI：Android PR run38882689936、push38882682411，Science PR38882689925、push38882682409（两次science含deep均PASS）。
首轮类级报告：PeriodStabilityTest 5/0失败/0跳过；API26/29的新增原生合同各13/0/0。最终检查链接见PR正文，不能用首轮替代最终HEAD。

完整CI保留原任务/断言，API26/29只增加合同类，API35原完整运行自动发现；新增release隔离守卫独立于既有attachment守卫。

## 状态边界

- TEST_ONLY：所有新增计量、SQL候选、Repository实验与故障注入。
- DESIGN_VERIFIED：候选域隔离、源payload读回、事务异常回滚的已测试范围；不是完整迁移/备份实现批准。
- INTEGRATION_VERIFIED：仅实际运行通过的现有Repository/Room场景；原生结果须单独列实际CI。
- PRODUCTION_BLOCKED：全部B1准入及恢复风险尚待负责人决定。
- PRODUCTION_IMPLEMENTED：否。
- RELEASED：否。

NOT_IMPLEMENTED：正式计量API、02B关联、RoomN、完整新业务备份writer/reader、生产库存接线、预测日期计算和未知UI。
NOT_RUN：物理真机/ARM设备、新候选协议强停断电与降级安装、真实用户数据（禁止使用）。既有CI重启测试通过也不替代新协议强停证据。
SKIPPED：最终CI如有条件跳过逐项登记，不改条件制造成功。
