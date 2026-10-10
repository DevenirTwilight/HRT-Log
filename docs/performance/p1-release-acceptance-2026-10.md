# P1：ARM64 Release 运行时与兼容性验收（2026-10-10）

机器可读结果：[p1-release-acceptance-2026-10.json](p1-release-acceptance-2026-10.json)。验收工具：[`scripts/release-acceptance/`](../../scripts/release-acceptance/)、[`app/src/releaseAcceptanceTest/`](../../app/src/releaseAcceptanceTest/)、工作流 [`.github/workflows/release-acceptance.yml`](../../.github/workflows/release-acceptance.yml)。状态含义：PASS＝实际执行并通过；FAIL＝实际执行失败；BLOCKED＝环境无法提供；NOT_RUN＝未执行。

## 1. 结论

> **更新（2026-10-10，用户实机）**：2026-10-10 用户实机反馈：正式签名 ARM64 包（SHA-256 80268747…2dff）已在用户本人手机上覆盖安装 Universal 正式版，用户报告“能正常解锁，原有记录、库存、化验都在；提醒能正常响；伪装入口正常；能导出备份”，功能正常。这是用户手动检查、单台设备、非自动化测试；设备型号/Android 版本未记录。它补上了“C 在真实 ARM64 硬件上运行”与“正式签名 universal→arm64 覆盖升级且数据保留”两项，状态记为 PASS（用户手动）；其余未覆盖项（D、反向 arm64→universal、其他设备、自动化断言）仍按下文。

- **ARM64（C/D）在真实 ARM64 环境的运行时：BLOCKED。** 本容器无 KVM；GitHub 托管的 ubuntu-24.04-arm 没有 `/dev/kvm`，Google 也不发布 Linux aarch64 模拟器；macos-15（Apple M1 Virtual）虽然 `emulator -accel-check` 返回成功，但 C/D/A 三个 arm64-v8a 模拟器全部以 `HVF error: HV_UNSUPPORTED` 启动失败。软件 CPU（QEMU TCG，`-accel off`）的 arm64 运行在报告时仍未出结果，且即使通过也只是“真实 arm64 用户态、非硬件、非手机”。**因此不能据此认定 ARM64 包可以正式发布。**
- **A/B（x86_64 API 35 模拟器，Release 等效、R8 开启）：除 1 项外全部通过。** 唯一失败是导航抽屉在 320×640 小屏上无法滚动到“设置”（**既有 UI 问题，与 R8/ABI/资源裁剪无关**，A/B 完全相同）。
- **资源裁剪（B）未发现运行时回归**：四语字符串值全部一致，被移除的源码引用字符串只有两个，均属生产中不可达的对话框；入口别名图标/标签、三个按名读取的 JSON、PDF 四语、伪装/私人便签全部通过。D 只在 x86_64 上做到“安装被拒”，其资源裁剪运行时同样 BLOCKED。
- **ABI 过滤本身（A→C）在 x86_64 上能验证的只有一件事**：arm64-only 包在无 arm64-v8a 的设备上被 Package Manager 拒绝（`INSTALL_FAILED_NO_MATCHING_ABIS`），不会覆盖或破坏已有安装。
- 生产代码、Gradle、版本号、Schema 9、PK 参数、备份密码学参数/格式均未改动；未生成正式安装包，未接触签名私钥、用户设备或真实数据。

## 2. 构建身份与工具链

| 项 | 值 |
|---|---|
| 生产源码 | 与 P0 固定源码 `622e84ee927a2ebd7afc6d102c8a0af74b47799e` 及 `d0284a4` 在 app/core/pk-engine/importer/Gradle 上 **git diff 为空** |
| 验收工具提交 | `c387b10b45b1f01ff9a3fcc6166826cbd85c139a`（权威运行 [38043594012](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38043594012)） |
| 工具链 | Temurin JDK 21、Gradle 9.3.1、AGP 9.1.1、Kotlin 2.2.20、compileSdk 37.0、Build Tools 37.0.0 |
| R8 | 四组均 `minify=true`，`proguard-android-optimize` + `app/proguard-rules.pro`（Gradle 实际解析值写入构建日志 `HRT_ACCEPTANCE_CONDITIONS`） |
| 资源裁剪 / ABI | A：否/全部；B：是/全部；C：否/arm64-v8a；D：是/arm64-v8a（与 P0 相同） |
| 签名 | 仓库公开的 debug 测试密钥（CN=Android Debug，SHA-256 `fc58f907…def13b`），**不是正式证书，不能覆盖真实安装** |
| 仓库常规 CI | “Android and domain checks”（含 PeriodStability）在本轮提交上 success（如 38037216050、38038170448） |

两种模式（只经 `-I acceptance.init.gradle` 生效，平时构建不受影响）：

- **exact**：只额外加 [`test-support-keep.pro`](../../scripts/release-acceptance/test-support-keep.pro)（androidx.tracing、concurrent futures、Kotlin 标准库）。原因：AGP 会把应用已含的库从测试 APK 中剔除，而 R8 已删掉其中类，导致测试运行器直接崩溃（运行 38037216069）。应用代码、资源、原生库仍是该场景的生产 R8 输出。exact A 包 24,079,312 bytes，与 P0 未签名 A 的 23,506,758 不同（签名 + 上述保留），**不用于体积结论**。
- **functional**：再加 [`functional-keep.pro`](../../scripts/release-acceptance/functional-keep.pro)（保留项目类和测试直接调用的库），并像 debug 一样加入 Compose 测试宿主 Activity。资源裁剪与 ABI 过滤仍与场景一致；**单独报告，不称字节等同**。

## 3. 测试环境

| 环境 | 结果 |
|---|---|
| x86_64：GitHub ubuntu-latest + KVM，`system-images;android-35;default;x86_64`（abilist=x86_64，native bridge=0，ro.kernel.qemu=1） | A/B 全部阶段执行；C/D 仅安装（预期被拒） |
| ARM64 硬件加速：ubuntu-24.04-arm | BLOCKED：`kvm=absent`；无 linux-aarch64 模拟器 |
| ARM64 硬件加速：macos-15 | BLOCKED：`HV_UNSUPPORTED`（运行 38041739528） |
| ARM64 软件 CPU：macos-15 `-accel off` | NOT_RUN（运行 38043594012 中仍在启动） |
| 用户手机 / 真实 ARM64 设备 | NOT_RUN（本轮禁止接触） |

编排脚本 [`run_device_acceptance.py`](../../scripts/release-acceptance/run_device_acceptance.py) 在非模拟器上拒绝执行；每阶段记录命令、退出码、逐测试结果、按本应用 PID 归属的 logcat 崩溃行，以及 nativeloader 实际加载的 SQLCipher 路径。

## 4. 分项结果（权威运行 38043594012）

| 功能 | A x86_64 | B x86_64 | C | D |
|---|---|---|---|---|
| 安装（exact/functional 应用 + 测试包） | PASS | PASS | x86_64 拒绝安装 PASS（预期） | 同 C |
| 普通入口冷启动 ×3（exact、functional 各一组，`am start -W .Launcher`，无崩溃） | PASS | PASS | BLOCKED | BLOCKED |
| SQLCipher 原生库实际加载：`/proc/self/maps` 偏移对应 APK 条目 `lib/x86_64/libsqlcipher.so`，与进程 ABI 一致；nativeloader 日志 `base.apk!/lib/x86_64/libsqlcipher.so … ok` | PASS | PASS | BLOCKED | BLOCKED |
| SQLCipher 新建/写入 500 行/5 次重开/错误密钥拒绝（`SQLiteNotADatabaseException`，文件哈希不变）/`integrity_check`/`cipher_integrity_check`；cipher_version 4.10.0 community | PASS | PASS | BLOCKED | BLOCKED |
| 应用自身 PRIMARY 库：非明文文件头、3 次新 DatabaseAccess 重开均为 Schema 9、`foreign_key_check` 为空（不删除/重建） | PASS | PASS | BLOCKED | BLOCKED |
| 加密 Schema 1 → 9 迁移（导出 Schema 建库 + SQLCipher 加密 + 正式迁移），迁移后错误密钥拒绝且文件不变 | PASS | PASS | BLOCKED | BLOCKED |
| 备份：0.2.0 写出的 v1 合成备份（SHA `1ca7ac64…`）恢复到 Schema 9；错误密码、篡改、截断、错魔数拒绝；再导出→再恢复→再导出 28 张表完全一致；格式 `PNBAK1|salt16|nonce12|AES-GCM` 未变 | PASS | PASS | BLOCKED | BLOCKED |
| PK/历史：28 个 JVM 测试文件移植到设备上的 Release 进程运行，148 项全部 PASS，其中 PeriodStabilityTest 5/5、LabEstimate 1、ConcentrationCalculator 6 + 同文件 StockSummary 4、ObservedTreatmentHistory 28、ExportTest 2（仅去掉 Robolectric runner 注解，断言与黄金数据逐字未改；原文件仍在 JVM CI 照常运行） | PASS | PASS | BLOCKED | BLOCKED |
| 伪装：DisguiseFlow 16/16、PrivateStorage 3/3、跨进程重启后授权失效、私人便签仍为密文（prepare/verify） | PASS | PASS | BLOCKED | BLOCKED |
| 入口别名：Launcher 默认启用、Calculator/Notes 默认禁用；图标 `ic_launcher`/`ic_shell_calc`/`ic_shell_notes` 与标签可加载；可见启动入口仅 `.Launcher` | PASS | PASS | BLOCKED | BLOCKED |
| 四语字符串（en/zh-CN/zh-TW/fr）运行时值与源码 XML 一致 | 1075/1075 | 1004 存在且全部一致；71 个被裁剪，其中源码引用的仅 2 个不可达字符串 | BLOCKED | BLOCKED |
| 按文件名读取的 `pk-params.json`/`symptom-sources.json`/`wellbeing-translations.json`：APK 内与类加载器读取的 SHA 均等于源码 | PASS | PASS | BLOCKED | BLOCKED |
| PDF：四语生成、PdfRenderer 打开并渲染出内容 | PASS | PASS | BLOCKED | BLOCKED |
| 设置界面：SettingsButtons 24 项（含中英、字号/宽度组合，已启用锁） | PASS | PASS | BLOCKED | BLOCKED |
| 提醒：真实合成计划创建（3 个闹钟）→关闭通知后 0 个→重新开启 3 个且代次变化→通知实际发出 | PASS | PASS | BLOCKED | BLOCKED |
| 通知权限拒绝：不发通知、不崩溃（独立进程运行） | PASS | PASS | BLOCKED | BLOCKED |
| 设备重启后提醒恢复：重启后不打开应用，SystemReceiver 由加密库重建闹钟并发出通知 | PASS | PASS | BLOCKED | BLOCKED |
| UI 冷启动 + 四语导航遍历（UiAutomator） | **FAIL**（见 F1） | **FAIL**（同） | BLOCKED | BLOCKED |

functional 全集：A/B 各 225 项 = 221 PASS + 1 FAIL + 3 项按阶段跳过（这 3 项在各自阶段单独运行并通过）。exact：A/B 各 6 项 = 5 PASS + 1 FAIL（同一项）。

## 5. 失败、阻塞与未验证

- **F1（FAIL，既有 UI 问题；2026-10-10 用户授权后已修复：抽屉加 verticalScroll，回归测试 DrawerScrollUiTest，见 HANDOFF）**：导航抽屉 `Column`（`app/src/main/java/net/plainnotes/app/ui/AppShell.kt:136`）不可滚动；在 320×640 屏幕上“设置/关于”位于屏幕外，无法从抽屉进入。窗口层级证据见运行工件 `ui-failure-en.xml`（可见项止于“Lab results”，无可滚动节点）。与 Release/R8/ABI/裁剪无关，debug 同一布局；现有 SettingsButtons 测试直接打开设置页，因此未覆盖。最小修复：给该 Column 加 `verticalScroll(rememberScrollState())`。属生产 UI 改动，**本轮未改，待授权**。由于该项在 en 处中止，**UI 层面的四语导航遍历未完成**；四语资源已由字符串、SettingsButtons（中英）和 PDF（四语）覆盖。
- **F2（观察）**：`ImportedPlanDialog` 只在 `NotesViewModel.prepareImportedLink` 后打开，而该函数无生产调用方（仅 DataRefreshTest）；R8 删除后 B/D 资源裁剪随之移除 `import_link_help`/`import_link_empty`。不是裁剪回归；测试中对这两项作了带理由的白名单。
- **BLOCKED**：C/D 全部运行时功能（见第 3 节）；D 的资源裁剪运行时。
- **NOT_RUN**：ARM64 软件 CPU 运行（待出结果）；伪装入口经设置界面实际切换启动器别名（功能测试以代码路径覆盖，exact 只核对别名元数据）；生物识别（模拟器无法真实验证）；系统文件选择器返回、经 SAF 的 CSV/PDF/备份导入导出（编码与 PDF 渲染已测，选择器 UI 未测）；32 位 ARM / x86 真机安装行为；正式签名覆盖升级；用户手机。

## 6. 安全与兼容性风险

- 未发现新的安全风险：加密库为密文、错误密钥/密码被拒且不改动文件、备份格式不变、私人便签跨进程不保留授权、无新增权限（P0 已核验无 INTERNET）。
- ARM64-only 包在无 arm64-v8a 的设备（x86/x86_64 模拟器、32 位 ARM、仅 32 位用户态的 arm64 设备）上无法安装；已验证安装被拒发生在替换旧包之前。
- 从 universal 切换到 arm64 包（或反向）需同一正式证书；本轮只用测试密钥，**覆盖升级与数据保留未验证**。
- 资源裁剪收益仅约 317 KB（P0），且依赖“不可达代码”判断；未来若通过反射/按名访问字符串，存在被误删的风险。

## 7. Universal + ARM64 双发行建议

1. **默认继续提供 Universal full APK**（向后兼容、已有用户路径）。
2. **ARM64 full APK 仅在真实 ARM64 设备验收后作为可选附加资产**：同一正式证书、同一 versionCode/versionName，在 Release 说明中写明“仅限 64 位 ARM 设备，安装失败请用 Universal”。实现建议用 AGP `splits.abi`（`include("arm64-v8a")` + `universalApk = true`）一次构建两份，避免两套源码配置；如上 Play 需另行规划 versionCode 偏移。
3. **暂不启用资源裁剪**：收益小，D 尚无 ARM64 运行证据；可在 C 通过真机验收后单独评估。
4. `scripts/upload_release_assets.py` 与 `.github/release-assets.json` 若改为双资产，需要同步哈希与校验逻辑（本轮未改）。

**当前不建议正式采用 ARM64 发行**：唯一阻塞是缺少真实 ARM64 运行证据，不是已发现的缺陷。

## 8. 尚未完成的真实设备验收（建议由用户或具备 ARM64 设备的环境执行）

用一台**非日常使用、无真实健康数据**的 ARM64 Android 设备（或自托管 ARM64 Linux + KVM / 裸机 Apple Silicon 上的 arm64-v8a 模拟器），对 C（必要时 D）运行同一编排脚本：

```bash
./gradlew -I scripts/release-acceptance/acceptance.init.gradle -PhrtAcceptanceScenario=C -PhrtAcceptanceMode=exact :app:assembleFullRelease :app:assembleFullReleaseAndroidTest
# 复制 exact 包后再用 -PhrtAcceptanceMode=functional 构建，然后：
python3 scripts/release-acceptance/run_device_acceptance.py --scenario C --exact-app … --exact-test … --functional-app … --functional-test … --out <dir>
```

注意：脚本只允许在模拟器上运行（qemu 属性校验）；在实体机上运行需人工确认后另行放开，且会卸载/安装 `net.plainnotes.app`，**绝不能在装有真实数据的手机上执行**。正式签名覆盖升级（universal↔arm64、数据保留）需在测试机上用正式包另行验证。

## 9. 后续最小工作清单

1. 读取运行 38043594012 中 arm64 软件 CPU 作业结果，按实际状态补入本报告（不改变“硬件 ARM64 BLOCKED”结论）。
2. 获取真实 ARM64 环境，对 C 跑 exact + functional 全集；通过后再测 D。
3. 决定是否修复 F1（抽屉可滚动）；修复后 UI 四语遍历应可完成。
4. 在测试机上验证正式签名下 universal↔arm64 覆盖升级与数据保留。
5. 获得授权后才改发行配置（`splits.abi`）与发布资产脚本；资源裁剪单独决定。

## 10. 追加实验 E：Universal + 压缩原生库（2026-10-10）

用户要求实测“保留 Universal、压缩原生库”。E = A + `packaging.jniLibs.useLegacyPackaging = true`（manifest `extractNativeLibs=true`），R8 开启、无资源裁剪、四 ABI 全保留；只经验收 init 脚本生效，**正式配置未改**。

| 项 | A（现行） | E | 差值 |
|---|---:|---:|---:|
| 未签名 APK（源码 b6262c3，仅加该开关） | 23,506,758 | **13,025,314** | −10,481,444（−44.59%） |
| 原生库在 APK 中 | 19.45 MB 未压缩 | 约 8.9 MB deflate | |
| 安装后占用估算（APK + 本机 ABI 解压库） | 约 23.5 MB | 约 13.0 + 5.2（arm64）≈ 18.2 MB | 约 −5 MB |

8 个原生库解压后 SHA-256 与 P0 完全一致；ZIP 完整；`resources.arsc` 仍按系统要求未压缩。

运行 [38052673701](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38052673701)（源码 7d1bea6，x86_64 API 35）：E **全部 PASS**——exact 6/6、functional 222/222（另 3 项按阶段单独运行均 PASS），冷启动×3、进程重启、通知拒绝、重启后提醒恢复均 PASS；四语字符串 1075/1075；PeriodStability 5/5、DisguiseFlow 16/16。新断言证实全部 `lib/` 条目为 DEFLATED，且 SQLCipher 从解压目录加载（nativeloader：`/lib/x86_64/libsqlcipher.so … ok`）。同一运行中 A（含 F1 修复）也全部通过。

代价与未验证：首次安装需解压原生库（稍慢）；E 在 ARM64 硬件上未测（Android 解压机制与架构无关，但未实证）；正式签名下现有安装→E 的覆盖升级未测。建议：若采纳，改正式 Gradle 一行（`packaging { jniLibs { useLegacyPackaging = true } }`），出正式包后在测试机或用户手机按“备份→只接受更新”流程确认。
