# 58 — MED-02B6 / PHR-DEMO 验证记录

2026-10-15。基线：默认分支 `claude/new-session-1959qb` @ `f16c9d6`（#36 合并；合并后 Android 38726656377、Science 38726656317 全部成功）。PR #34 仍 OPEN，等待负责人独立处理，本批不含其内容。生产 B2 **NO-GO**、正式 B3 **NOT_IMPLEMENTED**、**NOT_REVIEWED_EXTERNALLY**；附件 `feature_enabled` 仍为 0。

## PR-A：PHR-DEMO-UI（可看见、可浏览）

### 隔离方式（为什么不新建模块或包名）

演示全部位于 `app/src/debug`（代码、四语字符串）与 `app/src/androidTestFull`、`app/src/test`；release 构建不含任何演示类、资源或入口。没有新增 Gradle 模块或 applicationId：debug 构建与正式应用同包名 `net.plainnotes.app`（现有设备脚本依赖这一点），因此**只在 emulator 上运行**，不得在装有正式签名版的手机上覆盖安装。演示 activity **没有启动器图标**（避免在伪装模式下暴露真实应用名），用 adb 启动。数据是内存中的确定性合成 fixture（`DemoRepository`），不读写 SQLCipher、PNBAK1、Keystore、附件表、AppLock 或伪装配置；activity 不设 FLAG_SECURE，因为不显示任何私人信息、截图是验收证据。

### 内容

- 模型（与未来受审后端同名，`app/src/debug/.../demo/DemoArchive.kt`）：`AttachmentSummary`、`VersionSummary`（CURRENT/RETAINED，不可变）、`LinkTarget`（kind + epoch + 整数 id）、`DocumentStatus`（METADATA_ONLY / TEST_FILE / MISSING / CORRUPT / QUARANTINED）、`StorageFailure`、`DatePrecision`（DAY/MONTH/YEAR/UNKNOWN）、`SourceClaim`（用户陈述，不是签章）。
- Fixture（24 条，固定 UUID/日期/排序）：多版本报告（CURRENT+RETAINED，带更正说明）且关联 1 次就诊 + 2 项化验；未分类且未关联的待整理文件；临床日期未知的文件；仅年份、仅月份精度；回收站文件；缺失 / 损坏 / 隔离各一；仅元数据记录；同整数 id 12 的化验分属当前与已退役 epoch，后者不被任何文件关联。
- 界面（`DemoArchiveScreen.kt`）：常驻四语红色横幅“合成示例——不可用于存储真实医疗原件”；面包屑“医疗档案 › 演示资料库”；标签页 待整理 / 全部文件 / 类别 / 回收站；搜索（标题、类别、填写的来源机构、临床日期、添加日期）；详情页（状态与占位预览、类别、临床日期与精度、添加日期、来源声明与“不是签章”提示、备注、版本列表、显式关联及已退役标记、无关联时说明“不会按日期/哈希/编号自动关联”）；系统返回键回到列表；底部“测试证据回放”卡片明确说明是 PR #36 的 TEST_ONLY CI 结果，不是备份能力。

### 测试

| 测试 | 层级 | 内容 |
| --- | --- | --- |
| `DemoFixtureTest`（3） | JVM | fixture 确定性与全部状态；跨 epoch 同整数 id 不关联；标签页与搜索字段、排序确定 |
| `DemoArchiveLayoutTest`（4 语 × 2） | Robolectric API35，320dp、2× 字体、暗色 | 横幅始终可见；待整理 → 全部 → 搜索 → 详情（2 个版本、3 个关联、无退役关联）→ 返回；日期未知与隔离状态明确、无关联说明 |
| `DemoArchiveAndroidTest`（2） | **原生 emulator（CI device-tests，API35）** | 待整理 → 类别 → 化验报告类 → 全部 + 搜索 → 详情 → 版本与关联 → 系统返回 → 回收站 → 隔离文件；每步真实截图 |
| `scripts/run_demo_capture.py` | 原生 emulator | 录屏 + 拉取截图（亮色 8 张、夜间模式 2 张）到 artifact `demo-evidence` |
| `check_attachment_release_isolation.py` | release APK | DEX/清单禁止 `net/plainnotes/app/debug/demo/`、`MedicalArchiveDemoActivity`、`DemoRepository`；`resources.arsc` 禁止 `demo_banner`、`demo_launcher_label`、`SYNTHETIC DEMO`（新增自测） |

### 演示步骤（可复现）

1. 启动 x86_64 emulator（API26 以上）。不要在装有正式 HRT Log 的手机上操作。
2. `./gradlew :app:installFullDebug`
3. `adb shell am start -n net.plainnotes.app/net.plainnotes.app.debug.demo.MedicalArchiveDemoActivity`
4. 依次点：待整理 → 类别 → 化验报告 → 全部文件 → 搜索 “Blood test” → 打开报告 → 下滑看版本与关联 → 返回 → 回收站。
5. 一键复现截图与录屏：`python3 scripts/run_demo_capture.py`（输出 `app/build/reports/demo/`）。

## 本地结果与 CI

（本地结果与 CI 运行编号在提交后追加。）

## 已知缺口

PR-A 只读浏览：关联/取消关联、编辑、更正版本、回收站恢复、永久删除、Reset Demo 属 PR-B。原生设备验证仅 API35（API26/29 在 PR-B）。真机 TalkBack、真实 ARM 设备 NOT_RUN。预览为明确标注的占位，没有读取或认证任何文件。
