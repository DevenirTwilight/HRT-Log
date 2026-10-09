# fullRelease APK 体积 P0 审计（2026-10）

状态：A/B已实测，C/D仍构建中；不可计算未完成组的收益。生产 Gradle/PK/Schema/版本/加密参数未更改。只审计 full，未签名、安装或发布。

## 固定源码与构建条件

- 仓库 `DevenirTwilight/HRT-Log`，实验固定 `622e84ee927a2ebd7afc6d102c8a0af74b47799e`；初始新克隆干净并重新 fetch。审计文档提交与后续 P2 研究提交不进入实验源码。
- Gradle 9.3.1，下载 ZIP SHA256 `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`（与 wrapper 属性一致）；AGP 9.1.1，Kotlin 2.2.20。
- 完整 Temurin JDK 21.0.12.1+1，下载包 SHA256 `ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94`；原预置 Java 只有 JRE，不能作为成功构建环境。
- Android platform 37.0 / build-tools 37.0.0，与 README/CI 一致。代理通过继承的 HTTPS_PROXY 显式传给 Java；新 JDK 使用 `/etc/ssl/certs/java/cacerts` 系统信任库，保持 TLS 验证。
- 包名 `net.plainnotes.app`、Build25/0.2.0、Schema9，task `:app:assembleFullRelease`。原始 `app/build.gradle.kts` SHA256 `a2964e07757c4e257eae877a4ceb35650cc9337cd8f1164508d291fcf6c17500`。
- A=源码默认资源压缩/无ABI过滤；B=true/无ABI过滤；C=源码默认/arm64-v8a；D=true/arm64-v8a。R8全部保持true，所有实验 unsigned。独立 detached worktree 和构建输出，正式 Gradle 文件不改。

## 已观察的静态风险

| 项目 | 证据 | 判断与边界 |
|---|---|---|
| 资源名动态查找 | 搜索 app/main、app/full、core、pk-engine、importer 生产 Kotlin，未发现 getIdentifier/Class.forName | 只说明这次搜索没有匹配；不覆盖依赖内部实现或运行时行为 |
| 伪装 launcher | app/src/full/.../Disguise.kt 动态 setComponentEnabledSetting；两个 alias 默认 disabled | 图标/标签直接 R 引用且 Manifest 显式声明；必须确认 B/D 实际保留并做 release 入口验证 |
| 普通/计算器/便签图标 | app main/full Manifest 及对应 mipmap-anydpi-v26、drawable | 静态可追踪，不代表不同启动器/OEM验收已通过 |
| 四语 | values、values-zh、values-b+zh+Hant、values-fr；locales_config=en-US/fr-FR/zh-CN/zh-TW | 没有新增语言过滤；仍需压缩后资源表及 release 四语验证 |
| Java resources 按文件名加载 | FittedModels.kt 的 /pk-params.json；SymptomCatalog/SourceTranslations 的两个 JSON；LabEstimate.kt | 区别于 Android res；需逐APK确认文件保留且内容不变，不能仅凭没有 getIdentifier 排除风险 |
| SQLCipher | DatabaseAccess.kt System.loadLibrary("sqlcipher")；ProGuard keep SQLCipher类 | arm64必须保留libsqlcipher.so与其他基线arm64本机库；构建不替代数据库打开/迁移验收 |
| 加密备份 | DataTransfer.kt Argon2id 32MiB/3轮/并行1与AES-GCM128位tag | 参数不动，不以移除Bouncy Castle或降低KDF换体积 |

未发现需要新增 keep 规则的生产动态 Android 资源名证据；不添加宽泛 keep 来掩盖失败。Compose Icons、Bouncy Castle 的依赖 jar 大小不能计作 APK 内占用；DEX 归因需要实际 R8/打包分析证据，本次不凭构建期 jar 推断。

## 构建前置失败与恢复

1. 预置 wrapper Java下载没有使用HTTP代理：Connection refused，退出1。
2. 通过代理配置运行Gradle后，预置Java缺JAVA_COMPILER：退出1。
3. 下载完整JDK后首轮固定A构建因代理CA未被新JDK信任：PKIX错误，退出1；该轮B/C/D未执行，没有伪造APK或数字。
4. 系统信任库下重新开独立实验目录，A运行到R8时Gradle daemon退出（退出1）；cgroup上限8GiB，memory.events记录oom=1/oom_kill=1、峰值超过8GiB。此前同时运行release和Android测试，触发环境内存限制。该轮B/C/D未执行。只调整任务调度为串行，保持R8/Gradle源码与所有场景参数一致；等待既有测试结束后再启动全新四组目录，不删除失败日志。
5. Android测试首次4个Robolectric测试类因测试JVM未继承代理而依赖获取失败（reminder当轮6项/4失败，非业务断言失败）；系统信任库+测试JVM代理下重跑，失败原XML保留。

## 功能验收边界与下一步

A=23,506,758bytes（23.506758MB、SHA256 0d0d6853504624fbf668d0f9f6837d80397515b6ddb586e3ccbc8064828c65c4），CRC/Manifest/关键资源和Java JSON保留检查通过，ZIP16KiB对齐检查退出0；本机库19,451,876bytes，其中SQLCipher19,414,484bytes、其他本机库37,392bytes，DEX2,263,882bytes，Android resources1,556,379bytes，ZIP结构/对齐等开销114,790bytes。B=23,189,359bytes，B−A=−317,399bytes（−1.3502457%），res/从362条变292条、逻辑资源名2493→2030，资源表−287,484bytes、res/−22,109bytes、DEX+254bytes、profile−16bytes、ZIP开销−8,044bytes。B本机库全SHA/三个JSON/关键图标不变，zipalign16KiB退出0。C/D尚未完成，ABI收益及交互项未测得；release资源/设备功能未验证。原有debug及设备测试即便通过，也不能替代B/D的资源压缩release验收。arm64需ARM64环境；本环境没有 `/dev/kvm`，未操作真实手机。

先取得四个同源码unsigned APK并按条目解释差分，再讨论正式资源压缩与ARM64发行方案。方案另需用户授权；本轮不修改生产默认。历史signed Build25的23,555,291bytes与SHA仅沿用附件/既有交付记录作规模参考，本轮未取得该APK，不算同等A/B。未获取/反编译/复制Featherline二进制、资源或源码。
