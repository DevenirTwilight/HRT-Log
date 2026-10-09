# P1-A：舌下 E2 校准数值稳定性方案与验收

2026-10-09；起始2761ed95c49e1ae74f0accd46aaf66bb87254040，Build25/schema9。状态：生产修复已实施；本地数值/单测/lint/构建已通过，CI API35设备与完整检查已通过并回查。P0原始证据、数据及结果只读保留；不重新拟合人口参数，不匹配竞品。

## 编码前方案选择

全量PK26实际重新执行，4s，复现P0异常与先验带。原coef391432.57、ka1.0005、lambda0.9995：改变lambda且保持coef会破坏抵消，r超过1.0010005就变负，最后被截0。

| 候选 | 数学/数值 | 先验与辨识 | 兼容/选择 |
| --- | --- | --- | --- |
| A稳定重参数化 | B=A(ka−lambda0)，核为B·exp(−min(ka,lambda0r)t)·[−expm1(−abs(ka−lambda0r)t)]/abs(ka−lambda0r)；相等取B t exp(−ka t)。r=1等价、交点连续非负 | 仍需可信SL速率先验；多项摘要不足以辨识真实吸收/清除 | 只用于独立比较，暂不投产；稳定不等于生理正确 |
| B仅幅度 | 完整SL事件固定r=1，h_cal(t)=exp(u)h_population(t)，不估SL速率/峰时 | 单参数不从一次化验拟合两个量；保留原幅度CV假设但不称人体已验证分布 | 采用。无需新人口参数；易隔离回滚，其他途径仍可调整速率 |

独立算A的2mg快速项46min：r0.9=289.7894、r1=278.8268、临界r=278.7199、r1.1=268.4101（不含吞咽项）。此算术支持A稳定性，不提供先验生理证据，因此按用户产品偏好选B。

完整SL事件必须锁定快速及吞咽项，不能只按model.key==E2_SL锁快速、留下口服慢项间接改变。在Engine分组中区分SL来源与真实口服，即使共用E2_ORAL；默认人口曲线在浮点误差内不变。混合途径：u为共同经验幅度，v只作用非SL的既有有效速率，不称已验证人体清除；纯SL v固定0、协方差第二维0，MC仅抽u，测量误差只通过拟合似然，不人为加浓度上限。

## 兼容与版本边界

个人拟合状态只在内存，每次从当前记录/化验重新拟合；没有持久化u/v，不复用旧值。prefs仅开关/模式不改。使用运行时算法版本2辨别新策略；新冻结结果沿已有calculator_version字段写2，estimate JSON的version仍1、字段形状不变；校验同时接受旧1和新2，未知版本仍拒绝。无新表/字段/Schema/备份格式，无迁移或批量重捕获。

旧LabContext/历史PDF保存的数值/输入/参数/版本原样展示、备份恢复；不会调用新算法重算旧快照。这里只标记新计算，不承诺完整历史可执行bundle。若以后需要改变JSON形状/持久化模型，必须另案设计，不混入本次。较旧应用的validator仅接受calculator_version1，因此不承诺新版本备份可向旧版应用降级恢复；代码回滚校准行为时须保留读取1/2的兼容校验，不能回滚它而使新冻结资料不可读。新应用读取旧备份已测试。

## 实施与验收计划

转换P0错误特征化断言为“SL率变化不改变核、先验区间有限正值”；保留未修历史窗口/缺上下文/因果/离群特征化。增加1/2/4mg×13时刻、4含服档、临界速率/极值、共同幅度、q6/12/24h30天漏服/修订、单/多点化验/单位/混合/因果/后验PSD/种子/分位数、旧snapshot/新snapshot/备份/PDF回归。

区间只表示固定核下的参数分布，不包含观测噪声、研究间异质性或结构不确定性的全部范围；不叫临床可信区间。保留/增加四语准确说明及尚存历史完整性、因果边界和离群问题警告，不做无关UI重设计。

新的P1-A JSON/CSV/可读报告及命令/数值误差/性能已保存，不覆盖docs/pk-research/results/sublingual-p0-*。

## 已运行的实际验收

使用Java21、Android SDK37.0、GRADLE_USER_HOME=/workspace/gradle-home；网络构建使用环境代理（与算法无关）。命令前缀为：

```sh
JAVA_HOME=/workspace/jdk21 JAVA_TOOL_OPTIONS='-Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080' GRADLE_USER_HOME=/workspace/gradle-home
```

实际命令（各次Gradle顺序运行，追加`--no-parallel --max-workers=2`）：

```sh
./gradlew :pk-engine:test --rerun-tasks
./gradlew -PjvmOnly :pk-engine:test :core:domain:test :importer:test
./gradlew :app:testFullDebugUnitTest --tests '*SublingualCalibrationAuditTest' --tests '*ConcentrationCalculatorTest' --tests '*LabEstimateTest'
./gradlew :core:data:testDebugUnitTest :core:reminder:testDebugUnitTest :app:testFullDebugUnitTest lintFullDebug :app:assembleFullDebug :app:assembleFullRelease :core:data:assembleDebugAndroidTest :app:assembleFullDebugAndroidTest
python3 -m unittest discover -s tools/pk-audit -p 'test_*.py'
python3 tools/pk-audit/sublingual_compare.py --engine docs/pk-research/results/sublingual-p0-engine.json --calculator docs/pk-research/results/sublingual-p0-calculator.json --output /workspace/tooling/pk-p1a/p0-replay
python3 tools/pk-audit/sublingual_p1a_compare.py
python3 scripts/check_release_manifest.py
```

本地单测XML计数：PK36/域58/importer12/data72/reminder14均0失败0跳过；app349项、336通过/13跳过（可选布局审计和既有原生环境项目）。四语资源完整/占位符测试通过；PeriodStability、时期/Flow、备份冻结、现有PDF等随完整app/data运行。7项Python通过。首次新备份夹具使用不足8字符密码导致1失败，修正为合成合规密码后完整重跑已通过；这是测试夹具问题，不是生产回归。基线PK26/26重新执行，旧版非SL/性能生成器1/1实际执行（3s）。日志保存在仓库外/workspace/tooling/pk-p1a，不提交健康数据或完整APK。

### 核心数值（pg/mL）

| 指标 | P0旧 | P1-A新 |
| --- | --- | --- |
| 人口2mg/46min直接核 | 278.8647796057063 | 278.8647796057063 |
| 输入rate=.9 | 29254.275557153393 | 278.8647796057063 |
| 输入rate=1 | 278.8647796057063 | 278.8647796057063 |
| 输入rate=1.1 | 0 | 278.8647796057063 |
| 无化验p5–p95（seed20261006、200次） | 0–152714.8058637828 | 98.82246992729898–692.8536038998615 |
| 应用当前值（15min网格插值） | 277.76798707874434 | 277.76798707874434 |
| 合成当时化验400、应用校准开启 | 398.42655303749905 | 368.3183698246684 |

单化验真幅度1.5：新MAP幅度1.373203727888、速率1、方差.0669764696633；3次同相位1.449218280927与3次不同相位1.449218280925，速率1、方差.0261182109951。幅度被先验收缩，不能把更接近观测当准确性证明。测量误差参数仍借用口服EV CV29.9%，幅度CV.6仍为工程假设；本轮没有新的人体依据。纯SL删除借用的速率先验维度，混合保留其他途径原先验及相关二维协方差；后者不是经证明的生理消除率。

9组人口/13时点及Cmax/Tmax/AUC/停药指标最大差异7.16795511835e-11（混合来源分组仅浮点舍入差异）；1/2/4mg、含服四档、near/at/cross临界率、极值rate1e-12/1e12与幅度1e-6/1e6有限非负且符合固定核定义。30天q6/q12/q24、漏服/实际剂量修正及冻结途径，单/多点、基线、明显不符、两单位/两模式与混合给药回归均通过。五种非SL路线与实际旧提交黄金夹具参数/协方差/中心/区间在1e-9内一致。临界rate被完整事件固定而非钳制浓度；吞咽项不间接接收rate。

MC按照协方差的Cholesky分解抽样；纯SL第二维严格0，不产生负值裁剪零样本，固定种子/排序及小样本1/2/40/200分位数由独立抽样对照。纯SL固定核复用，不以模拟200次重新改变速率；新中心/抽样共用同一参数语义。协方差为MAP处Gauss–Newton信息的近似，并非已验证的生理分布。

局部性能（同主机、不同JVM）旧5次中位.047623099s，新.009952708s；120事件/2881点/200抽样/2热身。只展示局部固定核复用效果，不保证整机速度比例；完整每次计时保留JSON。P0模型/人体摘要误差原封保留，新人口未改，所以外部预测准确性没有新增结论。

### 数据与验收边界

新/旧snapshot calculator_version2/1、未知3拒绝；旧版数值（含病态区间）导入备份后原JSON字节一致，再备份恢复仍一致；不把新稳定区间替换旧冻结值。无Schema/字段形状/备份格式迁移、无记录/PDF写入或自动重捕获。新个人校准曲线按算法2重算，与旧个人校准不同，这是本次授权修复，未宣称旧算法等价。算法版本标志及已有计算器计数明确区分。

P1-B待实施：针对每条化验的历史覆盖/冻结配置门控，不把窗口首剂当治疗开始，不完整资料不能当基线或拟合事实；保留原始记录、说明拒绝原因和无迁移回归。之后再修因果插值/摘要及全离群标识。当前仍复现500/220假基线、未来化验污染应用插值/摘要、离群集合与采用不一致；四语警告保留。P1-A不等于整个个人校准可靠。P2新人口核仍规划/未实施，外部人体验证未确立。

完整Android命令实际BUILD SUCCESSFUL（5m32s）：lint0错误/131警告，Full Debug/Release及两个instrumentation APK编译通过；合并Release Manifest身份/入口/no INTERNET检查通过。没有更换签名身份、交付APK或发布版本。本地没有可用Android模拟器，不能把AndroidTest编译作为设备通过；实际API35由CI执行，结果待回查。

源码/测试/工具提交0cae6162e5ba5845903164c96e01aec92f31ee79；审计CSV LF与实际浮点误差修正提交4fc513f48e75e6341dfeafddf56bbab531959395。本轮全部35个变更文件均在开发分支，名单可由`git diff --name-only 2761ed95c49e1ae74f0accd46aaf66bb87254040 4fc513f48e75e6341dfeafddf56bbab531959395`获取；未改生产参数、数据库Schema/迁移或旧P0结果文件。初始文档检查点16e6cc1的CI及被新提交替代的0cae616 CI已取消，不计作通过；最终源码CI以4fc513f的运行与实际产物为准。

## 本轮文件清单

- `.github/workflows/android.yml`
- `app/src/main/java/net/plainnotes/app/conc/ConcentrationCalculator.kt`
- `app/src/main/java/net/plainnotes/app/conc/LabEstimate.kt`
- `app/src/main/java/net/plainnotes/app/ui/ConcentrationScreen.kt`
- `app/src/main/res/values-b+zh+Hant/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
- `app/src/main/res/values-zh/strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/net/plainnotes/app/LabEstimateTest.kt`
- `app/src/test/java/net/plainnotes/app/SublingualCalibrationAuditTest.kt`
- `core/data/src/main/java/net/plainnotes/app/data/LabContext.kt`
- `docs/BACKLOG.md`
- `docs/HANDOFF.md`
- `docs/REQUIREMENTS.md`
- `docs/hrt-roadmap-2026-10-07.md`
- `docs/pk-model.md`
- `docs/pk-research/results/sublingual-p1a-calculator.json`
- `docs/pk-research/results/sublingual-p1a-curves.csv`
- `docs/pk-research/results/sublingual-p1a-engine.json`
- `docs/pk-research/results/sublingual-p1a-report.json`
- `docs/pk-research/results/sublingual-p1a-report.md`
- `docs/pk-research/results/sublingual-p1a-stability.json`
- `docs/pk-research/sublingual-calibration-audit.md`
- `docs/pk-research/sublingual-p1-design.md`
- `docs/pk-research/sublingual-p1a-verification.md`
- `pk-engine/src/main/kotlin/net/plainnotes/app/pk/Engine.kt`
- `pk-engine/src/main/kotlin/net/plainnotes/app/pk/FittedModels.kt`
- `pk-engine/src/main/kotlin/net/plainnotes/app/pk/LabFit.kt`
- `pk-engine/src/test/kotlin/net/plainnotes/app/pk/SublingualP0AuditTest.kt`
- `pk-engine/src/test/kotlin/net/plainnotes/app/pk/SublingualP1ATest.kt`
- `pk-engine/src/test/resources/non-sl-calibration-v1.json`
- `tools/pk-audit/README.md`
- `tools/pk-audit/legacy_non_sl_baseline.kt`
- `tools/pk-audit/sublingual_p1a_compare.py`
- `tools/pk-audit/test_sublingual_p1a.py`

## CI全绿及产物已回查

源码4fc513f的[CI37912745994](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37912745994)：jvm和device-tests成功。实际下载device-test-results（artifact11608900281，21文件、0 APK）回查HTML：data14/14通过（MigrationBaseline7、EncryptionIntegration7），app53项/51通过/2常规跳过/0失败；独立重启日志prepare/verify各`OK (1 test)`。Settings锁启用24/24、Timeline6/6、Wellbeing2/2及其他原生隐私测试无失败。模拟器API35/x86_64，不冒充真机。Android完整任务也已success（主体BUILD SUCCESSFUL 9m29s），全运行三个job均success。实际下载build-results（artifact11607902449，199文件、0 APK）：app349/336通过/13跳过、data72、reminder14均0失败；聚焦PK18（P0复用8+P1A10）全部通过，JVM job另跑完整PK36/域58/importer12。Lint0错误/131警告、Full Debug/Release、两测试APK编译、release manifest、Schema/PLAN差异检查均success。7项Python及P0只读重放/P1-A数学验收成功。

CI实际engine/calculator JSON与入库本地JSON完全相同，参数hash不变、population_max_absolute_error=7.167955118347891e-11，numerical_acceptance=true、clinical_accuracy_established=false。CI计时新中位.045045126s来自不同主机，不能与旧本地.047623099s推断性能改善；审计脚本原“同主机”注释已修正为环境可能不同，仅独立本地验收具有同机条件。旧P0原始结果未覆盖。

末尾补充仅文档、报告说明及研究脚本性能注释，不修改生产代码或Kotlin测试；生产/测试与全绿4fc513f相同。修正脚本后7项Python再次通过，并以已下载的CI输入重新生成独立报告验收，数值判断不变。所有必需测试已实际运行；未做真实设备/真实健康数据验收，不发布APK/Release/PR。
