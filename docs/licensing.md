# 许可状态（浓度模型与校准）

最后更新：2026-10-06

## 当前状态（0.3.0 起）

- 浓度模型和化验校准已按文献独立重写（2026-10-06 决定，见 REQUIREMENTS.md 第 8 节），不再包含移植代码。
- 已删除：`pk-engine` 中移植的 `Pk.kt`、`Gel.kt`、`Calibration.kt`（EKF）；`tools/pk-reference/`（含上游原文件副本 `upstream/`）；对照测试 `UpstreamParityTest` 及其数据 `pk-engine/src/test/resources/reference/`；`pk-engine/UPSTREAM_LICENSE`。关于页的 MIT 全文和上游链接也已移除。`Types.kt` 重写（途径代码字符串保留，因为它们是数据库中已存的值），单位换算和插值在新的 `Units.kt` 中重写，分子量按分子式和标准原子量计算。
- EKF 的出处始终无法核实（上游未说明），按决定一并重写为 `LabFit.kt`（最大后验 + 拉普拉斯近似）。
- 注意：**0.2.0 的 GitHub Release、标签和仓库历史中仍包含移植代码**。是否撤回或删除 0.2.0 需先问用户，不要自行处理。

## 原来源链（历史记录，代码已删除）

| 部分 | 当时位置 | 直接来源 | 上游来源 | 许可状态 |
|---|---|---|---|---|
| 浓度模型 | `Pk.kt`、`Gel.kt`、`Types.kt` | Transmtf HRT Tracker `pk.ts`（MIT，提交 8c9abdde） | LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test | 上游没有 LICENSE，未获授权 |
| 化验校准（EKF） | `Calibration.kt` | Transmtf HRT Tracker `personalModel.ts`（MIT） | 未见说明 | 未核实 |
| 对照测试数据、上游副本 | `pk-engine/src/test/resources/reference/`、`tools/pk-reference/upstream/` | 同上 | 同上 | 同上 |

本项目其余部分（提醒、数据、界面、Trans Memo / HRT tracker 导入器等）的代码为本项目自写。功能设计参照过 Chrysalide 协会出品的 Trans Memo（开发时参考了它的界面截图）和 Transmtf HRT Tracker；没有使用 Trans Memo 的代码，与 Chrysalide、Transmtf 都没有隶属或合作关系。Trans Memo 导入器只读取用户自己导出的数据库，HRT tracker 导入器只读取其 JSON 导出格式。

## 授权请求（历史记录）

- 计划：向 LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test 提 issue，请求添加开源许可证或书面同意复用（标题与正文见下）。
- **状态：尚未发出。** 2026-10-06 在开发环境中运行 `gh auth status`，结果是未登录（环境里的 GH_TOKEN 无效）。按要求没有尝试其他登录方式，需要你登录后发出，或由你手动发出。
- Issue 链接：（发出后填写）
- 发出日期：（发出后填写）
- 当前状态：未发出

### Issue 标题

请求为本仓库添加开源许可证 / Request for a license

### Issue 正文

```
你好！我在开发一个开源的 Android HRT 记录应用（用药提醒、库存、血药浓度估算），目前的浓度模型是经 Transmtf HRT Tracker 间接移植自本仓库的 PKcore / PKparameter 逻辑。

我注意到本仓库没有 LICENSE 文件。按默认规则，没有许可证意味着他人不能合法复用代码。请问你是否愿意添加一个开源许可证（例如 MIT 或 Apache-2.0），或者书面同意我在自己的项目中使用并修改这部分代码？我会在应用的"关于"页和 NOTICE 文件中显眼地注明出处并链接回本仓库。

如果你不希望被复用，也完全理解，我会改为根据公开文献独立实现。感谢你的工作，它帮了很多人。

---

Hi! I'm building an open-source Android HRT tracking app. Its concentration model is indirectly ported (via Transmtf HRT Tracker) from the PK logic in this repo. Since the repo has no LICENSE file, could you add an open-source license (e.g. MIT or Apache-2.0), or give written permission for reuse with attribution? If you'd prefer not, I'll reimplement independently from published literature. Thank you for this work!
```

## 授权明确之前（历史记录；0.3.0 起不再适用）

- 不发布任何包含现有浓度模型的公开版本（GitHub Release、F-Droid）。
- 仓库应保持私有。**注意：截至 2026-10-06，本仓库是公开的**，包括 `tools/pk-reference/upstream/` 中的原文件副本，以及 CI 产物中的 APK（任何登录 GitHub 的人都能下载）。
- 同时进行 M4a 文献调研（`docs/pk-model.md`、`pk-params.json`），以便在得不到授权时按文献独立实现。

## 0.2.0 发布指令与最新核实

- 产品负责人在本轮明确要求将 0.2.0 做成公开 GitHub Release；本次按这一最新发布指令执行，发布说明保留上游许可待确认的事实。这不表示已经取得上游授权，也不表示已有文献独立实现。
- 2026-10-06 再次检查上游：仓库仍没有 LICENSE；已有 [许可询问 #12](https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test/issues/12)，尚无回复。本轮仅核实，未代产品负责人发出留言或新 issue。
- 上述来源链、MIT 通知和未完成的 M4a 工作继续保留；本次指令不包含 F-Droid 发布。

## 2026-10-06 决定

- 上游 issue #12 无回复，不再等待，按文献独立重写（已完成，见上）。
- 0.3.0 已准备好但**不发布**：不建 Release、不传 APK、不动 0.2.0 的 Release 和标签，直到用户另行通知。

## 0.2.0 紧急修复替换（build 4，2026-10-06）

用户最新要求将公开版本名保留为 0.2.0，并替换原 Release。内部 versionCode 4，代码保留此前准备的独立文献引擎及所有已验证修复，不回退至旧移植引擎。此次附件与原 0.2.0/build 2 不同，第三方通知同步更新；旧上游许可状态仍作为历史记录，不能说已获得授权。用户已授权替换 Release 与标签，此决定覆盖上面的待通知限制；Git 历史仍保留旧代码。
