# 许可状态（浓度模型与校准）

最后更新：2026-10-06

## 来源链

| 部分 | 本仓库位置 | 直接来源 | 上游来源 | 许可状态 |
|---|---|---|---|---|
| 浓度模型（单剂量动力学、舌下分层、双相肌注库房、凝胶三室、贴片、CPA、比卡鲁胺） | `pk-engine/src/main/kotlin/.../pk/Pk.kt`、`Gel.kt`、`Types.kt` | Transmtf HRT Tracker `pk.ts`（MIT，提交 8c9abdde） | LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test 的 `PKcore.swift` / `PKparameter.swift` | **待确认**：上游仓库没有 LICENSE 文件。Transmtf 的 README 写明其药代算法、模型和参数"直接来源于"该仓库，因此 Transmtf 的 MIT 许可未必覆盖这部分。 |
| 化验校准（EKF：theta_s / theta_k、回顾式/因果式、基线、离群值） | `pk-engine/.../pk/Calibration.kt` | Transmtf HRT Tracker `personalModel.ts`（MIT） | 未见上游说明 | 按 MIT 使用；**未核实**是否也源自上游仓库。 |
| 对照测试数据 | `pk-engine/src/test/resources/reference/` | 由 `tools/pk-reference/generate.ts` 运行上游代码生成的合成场景 | 同上 | 随浓度模型的许可状态。 |
| 上游原文件副本 | `tools/pk-reference/upstream/`（`pk.ts`、`personalModel.ts` 等，未修改） | Transmtf HRT Tracker | 同上 | 随浓度模型的许可状态；**这是未经授权内容的原样副本**。 |

本项目的其余部分（提醒、数据、界面、Trans Memo / HRT tracker 导入器等）为原创代码。

## 授权请求

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

## 授权明确之前

- 不发布任何包含现有浓度模型的公开版本（GitHub Release、F-Droid）。
- 仓库应保持私有。**注意：截至 2026-10-06，本仓库是公开的**，包括 `tools/pk-reference/upstream/` 中的原文件副本，以及 CI 产物中的 APK（任何登录 GitHub 的人都能下载）。
- 同时进行 M4a 文献调研（`docs/pk-model.md`、`pk-params.json`），以便在得不到授权时按文献独立实现。
