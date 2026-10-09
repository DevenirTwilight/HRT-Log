# P2-A/B 独立工程与科学验收（2026-10-09）

研究任务起始 `e89579a02da3c92a9e4976baea729af9d532437e`，现有`claude/new-session-1959qb`，初始工作区干净、fetch后远端一致。Build25/0.2.0/Schema9、LabFit2/Calculator2、旧冻结1/2不变。

## 时间顺序与冻结证据

1. Gate A **90518758449f7d52bc213ef2f2cfe48aa9754a57**：七研究/来源请求/整研究分割/全部数值假设/生产与旧证据基线，**候选拟合前提交并推送**。
2. Gate B **138b9813818568fa348c181dc899fd986a005424**：隔离gamma/双途径、独立ODE/积分oracle，23项数学/证据测试；之后首次跑339情景。
3. Gate B **af3767e2c6b5b78b235dbf08ba2594f45d10ba89**：报告契约/全部P0人口黄金场景，32项；完整CI验收。
4. Gate B **ff23d33151dab007af8edfa0cce3eeba96bbac49**：真实删除训练来源不再拟合、跨文件系统manifest排序；32项再通过。只实现预定删除检查/确定性输出，无科学协议变更。
5. Gate B **388ef0fc36dccf3299ff2aac62be94a8914a1f59**：纠正初值仅作标签的问题；预定四初值真实决定首次搜索括号，各自求解并保留initial_bracket，32项再通过。解析拟合幅度、曲线和科学比较完全不变，协议没有变更。
6. Gate C：最终成果和本文另提交，研究输出code_revision=388ef0f。Git历史可核对冻结早于全部候选实现；不能把最后文档SHA误称实际测试源码SHA。

protocol SHA256 `457530bfbe002dce0682f01f0f80d8eba020b54d4bda84efd56e684abaf9007f`。
dataset SHA256 `02750c38f1f99002c7d8294d17ac65c502460ab419e21a1a337b222eb9e42e4e`。
正式params SHA256 `b2768a6947d29d65f272b1d20e31fd59b9661acf8166b437f2a3b179f235b16b`。
所有冻结文件SHA见protocol-lock.json；四代码SHA见results/analysis.json.code_sha256；候选参数、CSV、JSON/MD输出SHA见results/output-manifest.json。科学协议偏离**无**；未知输入没有被补造。

## 完整命令与本地实际结果

本地JDK21 `/workspace/jdk21`、Gradle home `/workspace/gradle-home`、Android SDK `/workspace/android-sdk`，Python3.12.14；网络代理只供Gradle取依赖，研究工具无需网络。每条Gradle命令环境前缀：

```sh
JAVA_HOME=/workspace/jdk21 JAVA_TOOL_OPTIONS='-Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080' GRADLE_USER_HOME=/workspace/gradle-home
```

以下命令都在仓库根目录实际执行，Gradle串行、不同时启动：

```sh
./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test --rerun-tasks --no-parallel --max-workers=2
./gradlew :core:data:testDebugUnitTest :core:reminder:testDebugUnitTest :app:testFullDebugUnitTest lintFullDebug :app:assembleFullDebug :app:assembleFullRelease :core:data:assembleDebugAndroidTest :app:assembleFullDebugAndroidTest --rerun-tasks --no-parallel --max-workers=2
python3 -m unittest discover -s tools/pk-audit -p 'test_*.py'
python3 -m unittest discover -s tools/pk-research -p 'test_*.py'
python3 tools/pk-research/check_protocol.py
python3 tools/pk-research/research.py --output docs/pk-research/p2/results
python3 tools/pk-research/research.py --output /workspace/tooling/pk-p2/replay-388ef0f
python3 scripts/check_release_manifest.py
git diff --check
git diff --exit-code e89579a02da3c92a9e4976baea729af9d532437e -- app core pk-engine importer gradle tools/pk-audit tools/pk-fit build.gradle.kts settings.gradle.kts
```

最终首次结果目录先移至工作区研究备份，然后生成空目录；没有覆盖P0/P1证据。CLI拒绝非空输出目录。两次新输出五个文件逐字节一致（不含非确定的计时/工程日志）。后补工程/CI记录独立于科学output-manifest；校验清单分开保存。

| 套件 | 实际通过 | 失败/错误 | 跳过 |
|---|---:|---:|---:|
| PK JVM | 58 | 0 | 0 |
| domain JVM | 58 | 0 | 0 |
| importer JVM | 12 | 0 | 0 |
| data Android单元 | 72 | 0 | 0 |
| reminder Android单元 | 14 | 0 | 0 |
| app Full Android单元 | 385/398 | 0 | 13既有 |
| 既有Python审计 | 13 | 0 | 0 |
| 新研究Python | 32 | 0 | 0 |

JVM 8s成功；完整Android 7m33s/444 tasks成功；Lint **0错误/131已有警告**；Full Debug/Release通过；instrumentation编译通过，编译不是设备执行。实际XML/本地lint计数见results/engineering-local.json；完整任务日志及Python输出见results/local-logs/。不因绿灯宣称临床准确。仅前期测试夹具出现两次失败：连续性断言误用比冻结1e-6更严容差，以及回放混合旧事件时误按SL处理口服；均修正，正式生产未改。

旧证据只读重放（新输出目录，不执行fit.py）：

```sh
python3 tools/pk-audit/sublingual_compare.py --engine docs/pk-research/results/sublingual-p0-engine.json --calculator docs/pk-research/results/sublingual-p0-calculator.json --output /workspace/tooling/pk-p2/old-p0-replay
python3 tools/pk-audit/sublingual_p1a_compare.py --engine docs/pk-research/results/sublingual-p1a-engine.json --calculator docs/pk-research/results/sublingual-p1a-calculator.json --stability docs/pk-research/results/sublingual-p1a-stability.json --output /workspace/tooling/pk-p2/old-p1a-replay
python3 tools/pk-audit/sublingual_p1b_compare.py --engine docs/pk-research/results/sublingual-p1b-engine.json --calculator docs/pk-research/results/sublingual-p1b-calculator.json --eligibility docs/pk-research/results/sublingual-p1b-eligibility.json --output /workspace/tooling/pk-p2/old-p1b-replay
python3 tools/pk-audit/sublingual_p1c1_compare.py --after docs/pk-research/results/sublingual-p1c1-after.json --engine docs/pk-research/results/sublingual-p1c1-engine.json --calculator docs/pk-research/results/sublingual-p1c1-calculator.json --eligibility docs/pk-research/results/sublingual-p1c1-eligibility.json --output /workspace/tooling/pk-p2/old-p1c1-replay
python3 tools/pk-audit/sublingual_p1c2_compare.py --output /workspace/tooling/pk-p2/old-p1c2-replay
```

全部工程状态true/科学状态false；旧P0人口9场景117点最大算术差4.76746e-10pg/mL。新研究仍精确重算2mg46min当前278.8647796/Featherline固定80kg914.262352，不作为新拟合目标。单点拟合残差不用于宣称临床效度。

## 实际CI、API35与产物核对

[CI37953301790](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37953301790)对应af3767e，jvm/android/device-tests **全部success**。四份产物已实际下载、解压、检查：JVM21文件、build225文件、device25文件、research5文件，全部无APK。归档ID/ZIP SHA及HTML/XML计数见results/ci-af3767e.json；实际成功/跳过/独立重启关键日志随结果保存。

- JVM PK58/domain58/importer12；Python新32/旧13；完整app398（385通过/13跳过）、data72/reminder14及聚焦PK40均零失败。
- API35 x86_64模拟器：data14/14（migration7、SQLCipher/encryption7）；app64（62通过、2重启测试常规跳过）；独立prepare/verifyFreshProcess各1通过。
- 日志“Finished66”不是app主套件66全通过：以HTML64及独立重启分开计数。初期adb启动探测exit1随后重试成功，不是测试失败。
- 研究四个核心文件与af3767e本地逐字节一致，原manifest字典仅顺序不同、值相同；ff23d33已排序。没有下载论文正文至CI，没有APK/密钥/健康数据上传。
- Full构建/Lint/manifest实际success；环境无本地模拟器、无真实设备验收。API35不得称实机。

ff23d33的[CI37954537326](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37954537326)三个任务亦实际success。最终研究源码388ef0f的[CI37956265064](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37956265064)三个任务**全部success**，日志及四份报告已实际下载/校验ZIP digest：JVM21、build224、device25、research5文件，全部无APK。最终新Python32、旧13、JVM58/58/12、app398（385通过/13跳过）、data72/reminder14、聚焦PK40全通过；Lint0错误131警告，Full Debug/Release/manifest成功。API35 data14/14（迁移7、SQLCipher7）、app64（62通过/2常规跳过）、独立重启两次各1成功。五个科学结果文件与本地**逐字节完全一致**，含排序后的hash清单和真实初值搜索括号。机器计数/归档ID/哈希见results/ci-388ef0f.json，关键日志一起保存。最终文档提交只补研究报告/说明，不改已验收源码；其新触发CI状态应另查，不冒充已完成。

## 性能与数学/科学分离

results/performance-af3767e.json是同一主机研究核4800点×5次计时（不是Android运行性能）。中位数HRT10.21ms、Featherline6.45ms、A0.724ms、B6.12ms；计时噪声较大，没有临床或生产性能结论。复现方法（读取已保存canonical参数，无拟合）：

```sh
python3 - <<'PY'
import sys, json, time, statistics
sys.path.insert(0, 'tools/pk-research')
import models as m
params=json.load(open('pk-engine/src/main/resources/pk-params.json'))
meta=json.load(open('docs/pk-research/p2/evidence-catalog.json'))['comparison_model']
models={'HRT':lambda t:m.current(t,params),'Feather80':lambda t:m.feather(t,meta),
        'A':m.Gamma(391.4325832981025,1),'B':m.Dual(215.13280238264798,4,.32,.41,.1)}
for name,kernel in models.items():
    samples=[]
    for _ in range(5):
        start=time.perf_counter();checksum=sum(kernel(i/200) for i in range(4800))
        samples.append(time.perf_counter()-start)
    print(name,samples,statistics.median(samples),checksum)
PY
```

数学验收：稳定等率/穿越、解析与独立RK4/Simpson、非负有限、单位/剂量、重复/漏服/更正/停药、三组微参数同轨迹、Jacobian秩、真实删除研究、来源/单位/统计/协议篡改拒绝、报告不伪称临床通过、外部值扰动不改变拟合、确定性输出。参数/测量/研究/结构不确定均不具足够人体验证资料，不给虚构95%人体覆盖率。

科学结论：七研究仅1训练、3已见外部条件、3定性、0盲法外测；没有至少两项输入足够的人体独立验证。**external_validation_insufficient、clinical_accuracy_established=false**。P2-C仅书面门槛/待独立授权；正式模型和所有P1工程修复保持。未改Schema、用户事实、冻结、备份、旧PDF、签名、权限或无关模块；未发布APK/Release/PR。
