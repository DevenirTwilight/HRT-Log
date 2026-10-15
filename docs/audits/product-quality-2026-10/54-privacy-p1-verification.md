# 54 — PRIVACY-P1 验证记录（MED-02B5 PR-A）

2026-10-15。基线：默认分支 `claude/new-session-1959qb` @ `b43d109`（#32、#33 已合并；#34 仍 OPEN，等待负责人独立处理，本 PR 不包含其内容）。本报告只记录 PR-A；PR-B（B3-BACKUP-GATE）另有报告与 CI。最终 HEAD 的 CI 结果在其结束后追加；在此之前不称通过。

## A1 “删除全部数据”遗漏 `stock_alerts`

**核实（源码）**：`core/reminder/.../StockAlerts.kt` 在普通（凭据加密）存储的 SharedPreferences `stock_alerts` 里按 `"<药物 id>:<LOW|EXPIRY>"` 记录最后一次发送库存/开封到期提醒的 ISO 日期，用于“每天最多一次”。写入方是 `ReminderCoordinator` 的重排（需要已打开的数据库，即已解锁的真实空间；伪装/私人便签不写此存储）。`NotesViewModel.wipeAll` 清除了数据库与密钥、`prefs`、`shell_notes`、`notify`、AppLock、提醒缓存、伪装与私人便签，**唯独没有 `stock_alerts`**。后果：
1. 隐私残留：删除后仍可读出曾有哪些内部 id 在哪些日期触发过低库存/到期提醒（无药名）。
2. 功能错误：删除后新库从 id 1 重新编号，新药若撞上旧 id，会被旧标记误判为“今天已提醒”而漏掉当天第一次提醒。

扫描 `app/src/main`、`app/src/full`、`core/reminder/src/main`、`core/data/src/main` 中全部 `getSharedPreferences`：`prefs`、`shell_notes`、`notify`、`stock_alerts` 四个存储；未发现其他遗漏。文件类存储（数据库/wrap/Keystore、`lock.bin`、私人便签文件、`reminders.cache`）已在原流程中删除。

**修复（最小生产改动）**：
- `StockAlerts.PREFS`、`StockAlerts.clear(context)`（`commit()`，返回是否写入成功）、`StockAlerts.isClear(context)`。
- `wipeAll`：在销毁数据库**之前**先清一次（进程在中途被杀也不会把旧标记留在新库之后）；最后一次提醒重排之后再清一次并校验为空；任一失败抛出 → 既有 `catch` 显示 `DataJob.Failed(operation_error)`，**不会**显示成功或重启界面。取消对话框不调用 wipe。
- 不改数据库/密钥处理语义、无迁移；DECOY 删除逻辑不变；不触及用户已导出的外部文件。

**测试与红→绿**：

| 测试 | 层级 | 内容 | 红（修复前） | 绿 |
| --- | --- | --- | --- | --- |
| `LocalDataWipeCoverageTest`（2） | JVM 源码守卫 | 所有 SharedPreferences 存储都被 wipeAll 清除；`stock_alerts` 在销毁数据库前清除、在报告成功前校验 | 在 b43d109 的 `NotesViewModel.kt` 上 2 项失败：`Stores not cleared by wipeAll expected:<[]> but was:<[stock_alerts]>`；`Clear stock alerts before destroying the database` | 2/2 |
| `StockAlertsClearTest`（2） | Robolectric API35 | 当前与旧版键全部清除、重复清除幂等、新 id 不继承“今天已提醒”；只清本存储不误删其他存储 | 新增 API，修复前不可编译（无红态） | 2/2 |
| `WipeStockAlertsAndroidTest`（2） | **原生 emulator（CI device-tests，API35）** | 生产 `DataSection` 真实“删除全部数据”对话框：取消→标记仍在；输入确认词并确认→当前与旧版键清空、通知文案存储同时清空。仅 `ro.kernel.qemu=1` 运行 | — | 待 CI |

未覆盖、如实标注：删除流程中途进程被杀的真实设备重放（NOT_RUN；顺序设计保证 `stock_alerts` 先清）；`commit()` 写失败只能由源码守卫证明其会报告失败，未做文件系统故障注入（FAULT_INJECTION NOT_RUN）。

## A2 Bouncy Castle 许可声明

- 坐标：`org.bouncycastle:bcprov-jdk18on:1.86`（`gradle/libs.versions.toml`），`app` 与 `core/data` 的 `implementation`（fullReleaseRuntimeClasspath 已确认），`tools/attachment-poc` 仅测试。用途：AppLock Argon2id 与 `.pnbak` 备份 KDF（参数不变）。
- 许可来源：构件内 `META-INF/LICENSE.md` 为 MIT 全文（“Copyright (c) 2000-2026 The Legion of the Bouncy Castle Inc.”）；POM `<license>` 为 “Bouncy Castle Licence”，https://www.bouncycastle.org/licence.html。jar SHA-256 `2af190b300cbb0b35e248ccf5f4a06b6072030aeb3da7a98ec73abe5b4cb371f`（本地 Gradle 缓存）。
- 修改：`THIRD_PARTY_NOTICES.md` 新增 Bouncy Castle 节（坐标、用途、MIT 全文）；应用内“开源许可”四语正文加入 Bouncy Castle，并显示不翻译的许可全文（`res/values/legal_notices.xml`）。项目自身 MIT 与第三方许可的边界不变。
- 发布 APK 核查（本地 `:app:assembleFullRelease`，未签名）：APK 内保留 Bouncy Castle 构件自带的 `META-INF/LICENSE.md`（MIT 全文）与 Jakarta Dependency Injection 的 `META-INF/NOTICE.md`（Apache-2.0）；后者经 Dagger/Hilt 传递引入（`jakarta.inject-api:2.0.1`、`javax.inject:1`），原通知未点名，已补一行。权限清单无 INTERNET；`check_attachment_release_isolation.py` PASS（1 个 DEX）。
- 守卫 `ThirdPartyNoticesTest`（2，JVM）：所有交付模块的运行时依赖别名必须映射到通知名称并同时出现在 `THIRD_PARTY_NOTICES.md` 与应用内文本；Bouncy Castle 坐标/版本与 MIT 关键句逐字存在。红：b43d109 的通知上失败 `Bouncy Castle missing from THIRD_PARTY_NOTICES.md`、`Notice names the exact artifact and version`。

## A3 隐私页与真实 Android 验证

- 文案：只更新确实变化的事实——“删除全部数据”的范围（`PRIVACY.md`、四语应用内说明、证据表）。导出/SAF/云副本不可撤回等原表述不变。
- 新增原生 emulator 测试（CI device-tests，API35）：
  - `PrivacyNoticeDeviceAndroidTest`（1）：生产 About 屏（debug 专用宿主 `AboutTestActivity`，不进入 release）打开说明 → 标题语义 1+10、每节可滚动可见、关闭按钮 ≥48dp → activity 重建（旋转等价）后仍打开 → 系统返回键关闭说明而不关闭页面 → 关闭按钮再次关闭。
  - `PrivacyNoticeLockAndDisguiseAndroidTest`（2）：PIN 锁屏与计算器伪装外壳均无说明、入口或说明文案；伪装外壳无真实应用名。
- Robolectric 层保持：四语 × 320/411dp × 1/2×、WCAG 4.5:1、≥48dp、API26/29（Robolectric，不是 emulator）。
- 真机 TalkBack/屏幕阅读器人工走查：NOT_RUN（无设备）。

## 不变量

不改 Room schema/guards、PNBAK1（Argon2id 32MiB/3/1、AES-256-GCM、序列化 9）、AppLock 参数、PK/M2、versionCode/applicationId/签名、依赖目录；无新权限、无 INTERNET；debug 清单新增的测试宿主只在 debug 构建。

## 本地结果（提交前，06eaa8c 起）

- `:app:testFullDebugUnitTest` 893（基线 889 + `LocalDataWipeCoverageTest` 2 + `ThirdPartyNoticesTest` 2），0 失败/错误，61 skip 与基线相同（既有条件跳过：ExportTest、M2ScenarioVisualAuditTest、ButtonAuditTest），含 `PeriodStabilityTest`。
- `:core:reminder:testDebugUnitTest` 19（含 `StockAlertsClearTest` 2），`:core:data:testDebugUnitTest` 180，均 0 失败/skip。
- `lintFullDebug` 0 error，178 warning 与基线数量相同（`NotesViewModel` 两条 ApplySharedPref 为既有 `commit()` 行位移）。
- `:app:assembleFullDebug`、`:app:assembleFullDebugAndroidTest`（含 3 个新设备测试类）编译成功；本机无 KVM，设备测试由 CI 运行。
- 红态证据 XML：`docs/audits/product-quality-2026-10/evidence/privacy-p1/`。

## CI 结果

（待最终 HEAD 的 CI 结束后追加。）
