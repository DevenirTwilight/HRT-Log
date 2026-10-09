# fullRelease APK 体积 P0 审计（2026-10）

四组同源码 unsigned Release 已完成构建及静态审计。最大收益来自 ABI 分发；资源压缩收益较小。正式 Gradle、版本、Schema、PK、历史与加密参数没有修改。**release 功能和 ARM64 设备运行未验证，不据此启用生产默认。**

## 实测 2×2 结果

| 指标 | A 默认/universal | B shrink/universal | C 默认/arm64 | D shrink/arm64 |
|---|---:|---:|---:|---:|
| APK bytes | 23,506,758 | 23,189,359 | 9,201,627 | 8,884,228 |
| MB（10^6） | 23.506758 | 23.189359 | 9.201627 | 8.884228 |
| MiB（2^20） | 22.417791 | 22.115096 | 8.775355 | 8.472660 |
| native libs 压缩 MB | 19.451876 | 19.451876 | 5.197640 | 5.197640 |
| DEX 压缩 MB | 2.263882 | 2.264136 | 2.263882 | 2.264136 |
| Android resources 压缩 MB | 1.556379 | 1.246786 | 1.556379 | 1.246786 |
| ZIP结构/对齐等开销 MB | 0.114790 | 0.106746 | 0.063895 | 0.055851 |

差值采用“新−旧”，负数为减少；所有百分比以 A 的实际 bytes 为分母。

| 差值 | bytes | % of A |
|---|---:|---:|
| B-A | -317,399 | -1.350246% |
| C-A | -14,305,131 | -60.855397% |
| D-A | -14,622,530 | -62.205643% |
| D-C-B+A | +0 | +0.000000% |

## 构建条件与身份

- 固定源码 `622e84ee927a2ebd7afc6d102c8a0af74b47799e`，初始 fetch 后工作区干净。后续远端 P2 研究和审计文档提交原样保留，不进入四个 APK。
- Temurin JDK21.0.12.1+1；Gradle9.3.1，AGP9.1.1，Kotlin2.2.20，SDK37.0 revision2，Build Tools37.0.0；全部仅 `:app:assembleFullRelease`，独立临时worktree与输出。
- JDK包SHA256 `ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94`；Gradle分发SHA256 `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06` 与wrapper配置一致。
- 四组始终R8=true、full-only、signingConfig=null；A/C实际shrinkResources=false，B/D=true；A/B实际ABI过滤空，C/D=[arm64-v8a]。Gradle模型实际输出与每组命令/退出码/修改文件SHA均在JSON和日志。
- 包名net.plainnotes.app，Build25/0.2.0，minSdk26/targetSdk37，Schema9。继承HTTP代理，使用系统Java CA信任库，TLS校验开启。
- 8GiB构建环境：串行Release；Kotlin编译完成、R8开始后终止本任务独立JDK的空闲编译进程。所有场景采用相同调度，不改变源码/优化级别。

| APK | SHA256 | 构建退出 | ABI/原生库 |
|---|---|---:|---|
| A | `0d0d6853504624fbf668d0f9f6837d80397515b6ddb586e3ccbc8064828c65c4` | 0 | arm64-v8a, armeabi-v7a, x86, x86_64 / 8 |
| B | `f018bb132c2f5eee4ea89e350e5227f84851ddbf6a5aec5da3fdacbd00b4e844` | 0 | arm64-v8a, armeabi-v7a, x86, x86_64 / 8 |
| C | `1a0f84e598e3ceebef892927539fe8c8b49bed58941a3dcc7728ff34cea8a217` | 0 | arm64-v8a / 2 |
| D | `44919e2fb6c666658685cfd755b3ca9e1a8fb065e6eb5c91ed6d5c4197e74965` | 0 | arm64-v8a / 2 |

## 条目解释与证据边界

A本机库19,451,876bytes，占APK 82.7501%；SQLCipher19,414,484bytes，其他37,392bytes。SQLCipher归因来自实际 `lib/<abi>/libsqlcipher.so`，不是构建期jar。A的DEX压缩2,263,882bytes，不对Bouncy Castle/Compose Icons虚构单库贡献；跨库R8优化与压缩不能按jar大小拆分。

B−A：resources.arsc −287,484bytes，res/ −22,109bytes，DEX +254bytes，profile −16bytes，容器开销 −8,044bytes。DEX小幅增加是实测现象，未进一步归因到具体类。res/ 362→292条，逻辑资源名2493→2030；路径已被AAPT缩短，JSON保存按ZIP条目及逻辑资源名的完整差分。未验证每个被删除资源的运行时安全性。

C−A：删除其他三个ABI的6项本机库，native −14,254,236bytes，容器开销 −50,895bytes；其他组件大小不变。原生库比对包含完整SHA集合，arm64 SQLCipher与graphics.path均未改变。D的同类差分与交互项以表格和JSON实测为准。

四组CRC、合并Manifest检查、打包Manifest/权限/资源表、关键图标与locale XML、三个命名Java JSON源码SHA一致性、ZIP16KiB对齐均通过。A的8项ELF LOAD段p_align均16384；其他包保留库的字节SHA一致，因此对应ELF内容相同。静态检查不替代SQLCipher打开/迁移、设备或功能验证。ZIP开销包含元数据、对齐和其他字节；这些包未签名，不能把该差值全称签名开销。

四组打包权限一致，未含INTERNET；实际权限见证据目录。历史signed Build25的23,555,291bytes/SHA仅来自附件与既有交付记录，本轮未取该包，仅作规模背景，不计其与新A差值为优化。没有获取/反编译/复制Featherline APK、源码或资产，8,985,589bytes arm64参考不作为同等A/B或架构评价。

## 资源与兼容性风险

- 生产Kotlin搜索未发现getIdentifier/Class.forName；不覆盖依赖内部实现。Disguise.kt动态切换两个默认disabled的launcher alias，图标/标签使用直接R引用且在Manifest声明。四包中普通/计算器/便签图标及其前景/单色资源均静态保留。
- values、values-zh、values-b+zh+Hant、values-fr：已核对四APK中settings/shell_calc/private_notes三个代表性字符串的四语值与源码一致，locales_config及关键图标静态保留；不覆盖所有字符串或运行时路径，最终四语交互/字体和启动器运行未验证。
- `/pk-params.json`、`/symptom-sources.json`、`/wellbeing-translations.json`按文件名加载，四APK内容与固定源码完整SHA一致。没有添加宽泛keep。
- SQLCipher及加密备份实现不动，Argon2id/AES-GCM参数、旧冻结/备份、PK/P2不改。体积与软件回归不是医学准确度证据。

## 实际测试与未验证事项

| 模块 | 登记 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| core/domain | 58 | 0 | 0 | 0 |
| pk-engine | 58 | 0 | 0 | 0 |
| importer | 12 | 0 | 0 | 0 |
| core/data | 72 | 0 | 0 | 0 |
| core/reminder | 14 | 0 | 0 | 0 |
| app | 399 | 0 | 0 | 13 |

Python旧PK审计13、固定源码隔离研究126、APK脚本3项（两个stored/deflated合成ZIP、CRC损坏、非ZIP反例）均通过。应用单元命令退出0，399登记/386通过/13既有跳过，PeriodStability5全部通过；六模块合计613登记/600通过/13跳过。独立lint最终退出0，0错误/131警告，完整XML与命令日志已归档；没有把未完成或缺失XML计作通过。

本地未运行release instrumentation、ARM64设备、x86_64模拟器或用户手机；推送触发的既有CI不作为本轮B/D设备验收。正常/计算器/便签入口、私人便签隔离、四语、PDF、SQLCipher迁移、安全锁及通知的**压缩release运行时验收均未验证**。原有debug回归不替代B/D验收。APK字节统计不测应用RAM、安装展开或冷启动。

## 前置失败与恢复

1. wrapper下载未使用HTTP代理，Connection refused（退出1）；使用同一官方Gradle分发并验SHA恢复。
2. 预置Java只有JRE、缺javac（退出1）；换到已验SHA的完整JDK21。
3. 新JDK没有代理CA，首轮A PKIX失败（退出1），B/C/D未跑；系统CA恢复，TLS未关闭。
4. 并行Release/Android任务时R8 daemon退出；随后联合Android test/lint又退出，cgroup两次OOM kill。失败日志保留；改串行并回收本任务空闲编译进程，最终四组成功。
5. 首次reminder6项/4失败是Robolectric依赖下载无代理；测试JVM传播代理后data72/reminder14通过。app399（386通过13跳过）及独立lint最终完成，不隐藏原失败。

6. 首轮独立lint被执行环境重连中断，没有最终报告/退出码；中断日志保留，单独重跑最终退出0。

## 建议与发行兼容性

| 产物配置 | 64位ARM Android | 32位ARM Android | x86/x86_64 Android | 本轮运行时状态 |
|---|---|---|---|---|
| A/B universal full | ABI支持 | ABI支持 | ABI支持 | 未验证release功能 |
| C/D arm64 full | ABI支持 | 不支持 | 不支持 | 未执行ARM64运行 |

保留正式full默认配置。本轮只交测量、工具和报告；如果选择ARM64分发，保留universal兼容渠道并确认目标设备实际支持arm64 ABI。资源压缩收益约1.35%，应先通过release-equivalent的伪装/私有便签/四语/PDF/数据库/迁移/备份/锁/通知测试；在临时测试区保留R8与资源标志、使用CI内部测试签名和纯合成数据，A/B在干净x86_64环境，C/D另用ARM64环境，不涉及现有用户安装或官方密钥。任何正式资源压缩/ABI发行改变另需用户授权，不能将D直接提交生产。

## 待执行的 Release 功能验收设计

使用同一固定源码、相同R8/资源/ABI标志，临时内部测试签名及全合成数据；A/B用干净x86_64测试环境，C/D单独ARM64环境。每项四包执行同样断言，按包独立记录，不根据debug结果填通过。

| 路径 | 触发及断言 |
|---|---|
| 普通/计算器/便签入口 | 逐一切换alias、退出并重启；核对图标/标签与实际入口，私人便签只从授权入口可见 |
| 四语及动态资源 | 切换英文/简中/繁中/法文，遍历设置、伪装和便签；断言无Resources.NotFoundException，文本/字体及图标正确 |
| PDF及命名JSON | 合成日志生成PDF，核对文本/图表/语言；运行使用三JSON的症状、PK和翻译路径 |
| 数据库/迁移/备份 | 合成旧Schema测试fixture迁到9；SQLCipher新建/重开/读写；既有Argon2id/AES-GCM备份往返及错误密码拒绝，参数和格式不改 |
| 锁与通知 | 合成数据下启用安全锁、后台返回及重启，断言授权前隐藏；核验提醒调度/权限拒绝/通知展示 |
| PeriodStability/PK | 保留原数值和回归快照，复制现有关键断言到release测试宿主，确认资源裁剪不影响按名读取 |

此表是待执行计划，无设备结果；缺相应环境则继续标未验证，不操作用户现有安装或真实数据。

## 复现与交付

执行前保证工作区干净、准备相同JDK/SDK；设置JAVA_HOME及系统信任库，继承代理。下面参数适用于本次专用实验目录，换目录时保留JDK位于输出父目录内的安全约束。

```bash
export JAVA_HOME=/tmp/hrt-apk-experiment/jdk/jdk-21.0.12.1+1
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
python3 scripts/run_apk_size_experiment.py \
  --commit 622e84ee927a2ebd7afc6d102c8a0af74b47799e \
  --out /tmp/hrt-apk-experiment/new-round \
  --sdk /tmp/hrt-apk-experiment/android-sdk \
  --gradle /tmp/hrt-apk-experiment/gradle-9.3.1/bin/gradle \
  --http-proxy --stop-idle-kotlin-daemon
python3 scripts/apk_size_audit.py /tmp/hrt-apk-experiment/new-round/{A,B,C,D}.apk \
  --out /tmp/hrt-apk-experiment/new-round/size-report
```

每次使用新目录，旧失败不覆盖。现有测试命令、工具身份、每组日志/退出码/SHA、全部条目/本机库、逻辑资源名差分均在[机器报告](apk-size-baseline-2026-10.json)及[证据目录](apk-size-evidence-2026-10/)。APK只在仓库外 `/tmp/hrt-apk-experiment/pinned-serial/`，不签名、安装、交付或上传；临时环境删除后需要按固定条件重建。正式生产目录git diff为空。

## 各 APK 完整 ZIP 统计

以下包括按ABI汇总、全部本机库原始/压缩大小及每包前15条；profiles单独归类。

# APK size audit

All packages are compared using their actual byte size; all comparisons require matching source revision, release mode and build tools.

## Summary

| APK | Size (MB decimal) | vs first APK | SHA256 prefix |
|---|---:|---:|---|
| `A.apk` | 23.507 | +0.000 MB (+0.00%) | `0d0d68535046` |
| `B.apk` | 23.189 | -0.317 MB (-1.35%) | `f018bb132c2f` |
| `C.apk` | 9.202 | -14.305 MB (-60.86%) | `1a0f84e598e3` |
| `D.apk` | 8.884 | -14.623 MB (-62.21%) | `44919e2fb6c6` |

## A.apk

- Total: 23,506,758 B (23.507 MB)
- SHA256: `0d0d6853504624fbf668d0f9f6837d80397515b6ddb586e3ccbc8064828c65c4`
- ZIP file entries: 476

| Package component | Compressed (MB) | Uncompressed (MB) | Entries |
|---|---:|---:|---:|
| `lib/x86_64` | 5.757 | 5.757 | 2 |
| `lib/arm64-v8a` | 5.198 | 5.198 | 2 |
| `lib/x86` | 4.933 | 4.933 | 2 |
| `lib/armeabi-v7a` | 3.565 | 3.565 | 2 |
| `dex` | 2.264 | 4.513 | 1 |
| `resources.arsc` | 1.405 | 1.405 | 1 |
| `res/` | 0.151 | 0.251 | 362 |
| `other` | 0.074 | 0.448 | 8 |
| `META-INF/` | 0.023 | 0.064 | 86 |
| `kotlin/` | 0.012 | 0.051 | 8 |
| `profiles` | 0.011 | 0.011 | 2 |
| ZIP metadata, alignment and other overhead | 0.115 | — | — |

**Largest entries by ZIP compressed size**

| Member | Compressed MB | Raw MB |
|---|---:|---:|
| `lib/x86_64/libsqlcipher.so` | 5.746 | 5.746 |
| `lib/arm64-v8a/libsqlcipher.so` | 5.188 | 5.188 |
| `lib/x86/libsqlcipher.so` | 4.923 | 4.923 |
| `lib/armeabi-v7a/libsqlcipher.so` | 3.557 | 3.557 |
| `classes.dex` | 2.264 | 4.513 |
| `resources.arsc` | 1.405 | 1.405 |
| `pk-params.json` | 0.040 | 0.259 |
| `lib/x86_64/libandroidx.graphics.path.so` | 0.011 | 0.011 |
| `lib/arm64-v8a/libandroidx.graphics.path.so` | 0.010 | 0.010 |
| `assets/dexopt/baseline.prof` | 0.010 | 0.010 |
| `lib/x86/libandroidx.graphics.path.so` | 0.009 | 0.009 |
| `wellbeing-translations.json` | 0.008 | 0.035 |
| `lib/armeabi-v7a/libandroidx.graphics.path.so` | 0.007 | 0.007 |
| `symptom-sources.json` | 0.007 | 0.039 |
| `org/bouncycastle/x509/CertPathReviewerMessages.properties` | 0.007 | 0.047 |

**Native libraries (all entries)**

| Member | Compressed bytes | Raw bytes | SQLCipher |
|---|---:|---:|---|
| `lib/arm64-v8a/libandroidx.graphics.path.so` | 10096 | 10096 | False |
| `lib/arm64-v8a/libsqlcipher.so` | 5187544 | 5187544 | True |
| `lib/armeabi-v7a/libandroidx.graphics.path.so` | 7252 | 7252 | False |
| `lib/armeabi-v7a/libsqlcipher.so` | 3557424 | 3557424 | True |
| `lib/x86/libandroidx.graphics.path.so` | 9284 | 9284 | False |
| `lib/x86/libsqlcipher.so` | 4923492 | 4923492 | True |
| `lib/x86_64/libandroidx.graphics.path.so` | 10760 | 10760 | False |
| `lib/x86_64/libsqlcipher.so` | 5746024 | 5746024 | True |

Caution: ZIP component sizes do not measure Android runtime RAM usage, installed storage expansion, cold-start time or clinical/functional correctness.

## B.apk

- Total: 23,189,359 B (23.189 MB)
- SHA256: `f018bb132c2f5eee4ea89e350e5227f84851ddbf6a5aec5da3fdacbd00b4e844`
- ZIP file entries: 406

| Package component | Compressed (MB) | Uncompressed (MB) | Entries |
|---|---:|---:|---:|
| `lib/x86_64` | 5.757 | 5.757 | 2 |
| `lib/arm64-v8a` | 5.198 | 5.198 | 2 |
| `lib/x86` | 4.933 | 4.933 | 2 |
| `lib/armeabi-v7a` | 3.565 | 3.565 | 2 |
| `dex` | 2.264 | 4.514 | 1 |
| `resources.arsc` | 1.118 | 1.118 | 1 |
| `res/` | 0.129 | 0.208 | 292 |
| `other` | 0.074 | 0.448 | 8 |
| `META-INF/` | 0.023 | 0.064 | 86 |
| `kotlin/` | 0.012 | 0.051 | 8 |
| `profiles` | 0.011 | 0.011 | 2 |
| ZIP metadata, alignment and other overhead | 0.107 | — | — |

**Largest entries by ZIP compressed size**

| Member | Compressed MB | Raw MB |
|---|---:|---:|
| `lib/x86_64/libsqlcipher.so` | 5.746 | 5.746 |
| `lib/arm64-v8a/libsqlcipher.so` | 5.188 | 5.188 |
| `lib/x86/libsqlcipher.so` | 4.923 | 4.923 |
| `lib/armeabi-v7a/libsqlcipher.so` | 3.557 | 3.557 |
| `classes.dex` | 2.264 | 4.514 |
| `resources.arsc` | 1.118 | 1.118 |
| `pk-params.json` | 0.040 | 0.259 |
| `lib/x86_64/libandroidx.graphics.path.so` | 0.011 | 0.011 |
| `lib/arm64-v8a/libandroidx.graphics.path.so` | 0.010 | 0.010 |
| `assets/dexopt/baseline.prof` | 0.010 | 0.010 |
| `lib/x86/libandroidx.graphics.path.so` | 0.009 | 0.009 |
| `wellbeing-translations.json` | 0.008 | 0.035 |
| `lib/armeabi-v7a/libandroidx.graphics.path.so` | 0.007 | 0.007 |
| `symptom-sources.json` | 0.007 | 0.039 |
| `org/bouncycastle/x509/CertPathReviewerMessages.properties` | 0.007 | 0.047 |

**Native libraries (all entries)**

| Member | Compressed bytes | Raw bytes | SQLCipher |
|---|---:|---:|---|
| `lib/arm64-v8a/libandroidx.graphics.path.so` | 10096 | 10096 | False |
| `lib/arm64-v8a/libsqlcipher.so` | 5187544 | 5187544 | True |
| `lib/armeabi-v7a/libandroidx.graphics.path.so` | 7252 | 7252 | False |
| `lib/armeabi-v7a/libsqlcipher.so` | 3557424 | 3557424 | True |
| `lib/x86/libandroidx.graphics.path.so` | 9284 | 9284 | False |
| `lib/x86/libsqlcipher.so` | 4923492 | 4923492 | True |
| `lib/x86_64/libandroidx.graphics.path.so` | 10760 | 10760 | False |
| `lib/x86_64/libsqlcipher.so` | 5746024 | 5746024 | True |

Caution: ZIP component sizes do not measure Android runtime RAM usage, installed storage expansion, cold-start time or clinical/functional correctness.

## C.apk

- Total: 9,201,627 B (9.202 MB)
- SHA256: `1a0f84e598e3ceebef892927539fe8c8b49bed58941a3dcc7728ff34cea8a217`
- ZIP file entries: 470

| Package component | Compressed (MB) | Uncompressed (MB) | Entries |
|---|---:|---:|---:|
| `lib/arm64-v8a` | 5.198 | 5.198 | 2 |
| `dex` | 2.264 | 4.513 | 1 |
| `resources.arsc` | 1.405 | 1.405 | 1 |
| `res/` | 0.151 | 0.251 | 362 |
| `other` | 0.074 | 0.448 | 8 |
| `META-INF/` | 0.023 | 0.064 | 86 |
| `kotlin/` | 0.012 | 0.051 | 8 |
| `profiles` | 0.011 | 0.011 | 2 |
| ZIP metadata, alignment and other overhead | 0.064 | — | — |

**Largest entries by ZIP compressed size**

| Member | Compressed MB | Raw MB |
|---|---:|---:|
| `lib/arm64-v8a/libsqlcipher.so` | 5.188 | 5.188 |
| `classes.dex` | 2.264 | 4.513 |
| `resources.arsc` | 1.405 | 1.405 |
| `pk-params.json` | 0.040 | 0.259 |
| `lib/arm64-v8a/libandroidx.graphics.path.so` | 0.010 | 0.010 |
| `assets/dexopt/baseline.prof` | 0.010 | 0.010 |
| `wellbeing-translations.json` | 0.008 | 0.035 |
| `symptom-sources.json` | 0.007 | 0.039 |
| `org/bouncycastle/x509/CertPathReviewerMessages.properties` | 0.007 | 0.047 |
| `org/bouncycastle/x509/CertPathReviewerMessages_de.properties` | 0.007 | 0.050 |
| `kotlin/kotlin.kotlin_builtins` | 0.005 | 0.029 |
| `META-INF/androidx/annotation/annotation/LICENSE.txt` | 0.004 | 0.010 |
| `META-INF/androidx/collection/collection-ktx/LICENSE.txt` | 0.004 | 0.010 |
| `META-INF/androidx/collection/collection/LICENSE.txt` | 0.004 | 0.010 |
| `META-INF/androidx/lifecycle/lifecycle-common-java8/LICENSE.txt` | 0.004 | 0.010 |

**Native libraries (all entries)**

| Member | Compressed bytes | Raw bytes | SQLCipher |
|---|---:|---:|---|
| `lib/arm64-v8a/libandroidx.graphics.path.so` | 10096 | 10096 | False |
| `lib/arm64-v8a/libsqlcipher.so` | 5187544 | 5187544 | True |

Caution: ZIP component sizes do not measure Android runtime RAM usage, installed storage expansion, cold-start time or clinical/functional correctness.

## D.apk

- Total: 8,884,228 B (8.884 MB)
- SHA256: `44919e2fb6c666658685cfd755b3ca9e1a8fb065e6eb5c91ed6d5c4197e74965`
- ZIP file entries: 400

| Package component | Compressed (MB) | Uncompressed (MB) | Entries |
|---|---:|---:|---:|
| `lib/arm64-v8a` | 5.198 | 5.198 | 2 |
| `dex` | 2.264 | 4.514 | 1 |
| `resources.arsc` | 1.118 | 1.118 | 1 |
| `res/` | 0.129 | 0.208 | 292 |
| `other` | 0.074 | 0.448 | 8 |
| `META-INF/` | 0.023 | 0.064 | 86 |
| `kotlin/` | 0.012 | 0.051 | 8 |
| `profiles` | 0.011 | 0.011 | 2 |
| ZIP metadata, alignment and other overhead | 0.056 | — | — |

**Largest entries by ZIP compressed size**

| Member | Compressed MB | Raw MB |
|---|---:|---:|
| `lib/arm64-v8a/libsqlcipher.so` | 5.188 | 5.188 |
| `classes.dex` | 2.264 | 4.514 |
| `resources.arsc` | 1.118 | 1.118 |
| `pk-params.json` | 0.040 | 0.259 |
| `lib/arm64-v8a/libandroidx.graphics.path.so` | 0.010 | 0.010 |
| `assets/dexopt/baseline.prof` | 0.010 | 0.010 |
| `wellbeing-translations.json` | 0.008 | 0.035 |
| `symptom-sources.json` | 0.007 | 0.039 |
| `org/bouncycastle/x509/CertPathReviewerMessages.properties` | 0.007 | 0.047 |
| `org/bouncycastle/x509/CertPathReviewerMessages_de.properties` | 0.007 | 0.050 |
| `kotlin/kotlin.kotlin_builtins` | 0.005 | 0.029 |
| `META-INF/androidx/annotation/annotation/LICENSE.txt` | 0.004 | 0.010 |
| `META-INF/androidx/collection/collection-ktx/LICENSE.txt` | 0.004 | 0.010 |
| `META-INF/androidx/collection/collection/LICENSE.txt` | 0.004 | 0.010 |
| `META-INF/androidx/lifecycle/lifecycle-common-java8/LICENSE.txt` | 0.004 | 0.010 |

**Native libraries (all entries)**

| Member | Compressed bytes | Raw bytes | SQLCipher |
|---|---:|---:|---|
| `lib/arm64-v8a/libandroidx.graphics.path.so` | 10096 | 10096 | False |
| `lib/arm64-v8a/libsqlcipher.so` | 5187544 | 5187544 | True |

Caution: ZIP component sizes do not measure Android runtime RAM usage, installed storage expansion, cold-start time or clinical/functional correctness.
