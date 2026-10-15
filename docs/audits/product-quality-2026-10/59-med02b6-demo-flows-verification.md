# 59 · MED-02B6 PR-B / PHR-DEMO-FLOWS：合成交互流程验证（2026-10-15）

本 PR 在 PR-A #37（已合并为 76bba2a）的只读演示资料库上，加上完整的合成交互流程。隔离方式与 PR-A 相同（报告 58）：
- 代码、资源和 activity 只在 `app/src/debug`；
- 数据源是内存中的 `DemoRepository`，放在 `ViewModel` 里，旋转屏幕后仍保留，演示任务结束即消失；
- 不读写 SQLCipher、PNBAK1、Keystore、附件表、应用锁或伪装配置；
- release 隔离脚本照常检查 DEX、清单和 `resources.arsc`；
- 附件 feature flag 仍为 0，B2 NO-GO，B3 NOT_IMPLEMENTED。

## 1. 行为

| 流程 | 实现 | 规则 |
|---|---|---|
| 关联/取消关联 | 详情页“关联记录”对话框可关联 1 次就诊和 2 项化验；已关联行旁有“取消关联” | 只能由用户逐条选择；退役数据集（同编号、不同 epoch）列出但标明“不能关联”；回收站中的文件不能关联；关联会离开待整理，但不会改类别 |
| 编辑 | 标题、机构、类别、日期精度（具体日期/仅月份/仅年份/未知）和备注，可取消 | 标题 1–120 字；年份 1900 至今年；月份 1–12；日期必须真实存在；不能是未来；备注不超过 500 字。出错时不写入，草稿保留并显示原因 |
| 更正版本 | “添加更正版本”，必须填写说明 | 新增 CURRENT 版本（合成），原版本全部转为 RETAINED 并保持原样；损坏或缺失的原件可以借此恢复为可用的测试文件 |
| 回收站/恢复 | “移到回收站”和“恢复” | 恢复后关联与版本不变 |
| 永久删除 | 只能在回收站中进行，需两次确认，两步都可以取消 | 删除后从演示中消失，无法恢复 |
| 重置演示 | 列表顶部“重置演示”，需确认 | 回到确定性 fixture |
| 缺失/损坏/隔离 | 状态块用文字说明，永远不打开，也不显示占位预览 | 与 PR-A 相同 |
| 完成整理 | 待整理文件有“完成整理” | 只是离开待整理，不推断类别或关联 |

## 2. 测试

| 层级 | 测试 | 结果 |
|---|---|---|
| JVM | `DemoRepositoryFlowTest` 5 项：关联/取消/退役拒绝/回收站拒绝；编辑的 9 种错误都不写入、成功后字段正确、版本与关联不被编辑；更正版本；回收站、恢复、仅回收站可永久删除；重置 | 本地 5/5 |
| Robolectric，4 语 × 320dp × 2× | `DemoFlowsUiTest` 5 项 × 4 语：关联 3 条再取消 1 条；编辑先取消（不写入）、再遇到错误（草稿保留）、最后保存；更正版本 → 回收站 → 恢复 → 两步确认各取消一次 → 删除；重置；三种失败状态 | 本地 20/20 |
| 原生 emulator API35 | `DemoArchiveAndroidTest.fullScriptedFlow`：完整脚本流程，每步一张截图（10–19），并单独录屏 `demo-flows.mp4` | 见第 3 节 |
| 原生 emulator API26/29 | `DemoCompatAndroidTest`：打开、横幅、打开文件、关联一条、返回、重置；由 attachment-poc job 的 `scripts/run_demo_compat.py` 运行，输出 `demo-compat-api<N>.txt` | 见第 3 节 |
| 既有 | `DemoFixtureTest` 3、`DemoArchiveLayoutTest` 8 | 本地 3/3；Layout 见下 |

开发中发现的问题：PR-B 初版把“重置演示”按钮与标题放在同一行，`DemoArchiveLayoutTest` 的法语两项（320dp、2× 字号、原生图形）因此一直等不到界面空闲，Robolectric 的 Trace 记录持续累积，最终 OutOfMemoryError。
- 对照实验：
  - PR-A 原代码上同一测试 8/8 通过；
  - 只去掉详情页操作行仍失败。
- 处理：把“重置演示”移到列表末尾、证据卡之后，测试 8/8 通过。
- 确切的布局机制没有进一步定位，作为观察结果记录。

## 3. 本地结果与 CI

- 本地：`net.plainnotes.app.demo.*` 共 36 项全部通过：
  - DemoRepositoryFlowTest 5；
  - DemoFlowsUiTest 20；
  - DemoFixtureTest 3；
  - DemoArchiveLayoutTest 8。
- CI：最终 HEAD 的 Android（含 device-tests 的 API35 完整流程截图与录屏、attachment-poc 26/29 的兼容测试）与 Science 结果见 PR 正文。

## 4. 演示步骤（emulator）

1. 构建 debug 包并安装：`./gradlew :app:installFullDebug`。这只适用于 emulator，因为 debug 与正式应用同包名。
2. 启动：`adb shell am start -n net.plainnotes.app/net.plainnotes.app.debug.demo.MedicalArchiveDemoActivity`
3. 在“待整理”里打开“诊所宣传单照片”：
   - 点“关联记录”，关联就诊和两项化验；退役数据集显示为不可关联。
   - 点“编辑信息”，填一个不存在的日期会看到错误；改正后保存，再点“完成整理”。
   - 点“添加更正版本”，填写说明后会出现 v2。
   - 点“移到回收站”，然后“恢复”。再次移到回收站后“永久删除”，需要两次确认。
4. 打开损坏、缺失或隔离的文件：只显示状态说明，不会打开。
5. 点“重置演示”回到初始状态。

## 5. 未覆盖

- 真机 TalkBack、真实 ARM 设备：NOT_RUN。
- 演示中的“版本文件”都是合成元数据，没有任何真实文件读写。
- 正式附件存储、加密协议与备份上线仍需独立批准。PR #34 不在本 PR 中。

## 6. 合并与最终证据（2026-10-15 追加）

PR #39 合并为 43caf6f。最终 HEAD a007bfe：
- Android PR 运行 38758147413：全部 success。API29 job 首次因模拟器 16MiB 堆上限，core:data 既有 Argon2id 测试 OOM（环境故障，与本 PR 无关），按惯例重跑一次后成功（job 116509808934）。
- Android push 运行 38758153669：全部 success。
- Science 38758154499 / 38758146540：均 success。

开发过程中修正的 3 处设备测试问题（都只改测试）：
1. 软键盘遮住编辑对话框顶部的错误文字：改为按文字断言，对话框按钮用语义点击。
2. 关联后文件已离开“待整理”，脚本不再去点“完成整理”。
3. “重置演示”从标题行移到列表末尾（法语 + 原生图形下持续重新布局，见第 2 节）。

证据副本（合成数据）在 `docs/ui-audit/2026-10-15-med02b6-flows/`：
- API35 截图 20 张：01–08 浏览，10–19 完整交互流程，90/91 夜间模式；
- 录屏 `demo-walkthrough.mp4`、`demo-flows.mp4`；
- `demo-evidence.json`；
- API26/29 兼容测试输出 `demo-compat-api26.txt` / `demo-compat-api29.txt`（各 OK (1 test)）。
