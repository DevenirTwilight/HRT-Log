# UI-13　全应用配色：青绿主色 + 点缀色，中性底色（任务书）

**负责人**：DevenirTwilight　**日期**：2026-10-30　**编号**：UI-13（UI-10、UI-11、UI-12 已占用；UI-13 之前先读 REQUIREMENTS §156 确认仍空缺，被占用就顺延并在记录里写明）。UI-14 是同一批样图里的复诊页，见 `ui14-prompt.md`，依赖本任务的点缀色 token。

先读：`CLAUDE.md`、`docs/REQUIREMENTS.md`、`docs/HANDOFF.md`、`docs/design/UI_STYLE_AUTHORITY.md`。**样图**：`docs/design/ui13-accent-colors-2026-10-30/`（README 记录负责人决定，`assets/` 12 张 PNG，`source/` 画板源文件）。样图 PR #161 若还没合并，先等它合并再开始；样图与本任务书文字冲突时以本任务书为准。

## 0　负责人的决定（逐条照做，不要自己加解释或开关）

1. **主色仍是青绿。** 按钮、首页大卡、选中项、右下角按钮、日历“已服”、曲线全部保持现状。
2. **其他颜色只做点缀，而且面积要小。** 新增三种点缀色：蓝、紫、玫红。**一个页面只用自己那一种点缀色，同一功能在所有页面用同一个颜色；点缀色只表示“属于哪一类”，不表示好坏或状态**（彩色图标块方案）。只用于图标块、小标签、日期方块、时间线小圆点这类小面积元素。**不得**用于按钮、大卡、选中项、曲线和图表主线，也不得铺大面积底色。
3. **点缀色不得挤掉主色。** 负责人已经指出过一次：化验页顶部蓝色大卡让页面像“蓝色为主色”，已在样图里改回青绿。实现时每个页面都按这条自查。
4. **底色**：现在背景、卡片、容器的灰都偏绿（例如 `#F4FBF9`、`#EEF5F3`），是“整个应用绿扑扑”的原因之一。改为中性暖白，数值见下。
5. 已服 / 库存不足 / 已过时间 / 漏服 / 错误这些**语义状态色一个都不改**（UI-06）。红色仍只给真正的错误和删除。
6. 不新增功能、不加开关、不加解释文字。

## 1　修改 UI_STYLE_AUTHORITY.md

“必须继承的语言”一段写的是“低饱和背景、深青绿与柔和健康主题强调色”。按本任务更新：加一句“负责人 2026-10-30 决定：青绿是主色，蓝、紫、玫红为小面积点缀色，底色为中性暖白”，并链接 README。保留历史样图与审查，不改写过去的验收事实。这是文档修改，属于本任务范围。

## 2　数值（样图已核对对比度；任何调整都要重新核对并写进测试）

**底色**（浅色 / 深色）：背景 `#FBF9F7` / `#141211`；surfaceContainerLow `#F5F2EF` / `#1C1A18`；surfaceContainer `#EFEBE8` / `#211F1D`；surfaceContainerHigh `#E9E4E0` / `#2B2826`；onSurface `#1D1B1A` / `#E8E2DE`；onSurfaceVariant `#4C4642` / `#CFC6C1`；outlineVariant `#D8D1CC` / `#4C4642`；outline `#7B7470` / `#968D88`。其余 surface 系列（surfaceDim、surfaceBright、surfaceVariant、surfaceContainerLowest/Highest）按同一暖中性色相补齐，保持和现有 Material 色阶一致的明暗次序，不要留下偏绿的灰。**primary、secondary、tertiary、error 及各自的 container 不动**（secondaryContainer 的青绿灰继续用于选中项）。

**点缀色**（每种四个值：主色 / 容器 / 容器上文字 / 淡底；深色另一套）：

| 点缀 | 浅色 | 深色 |
|---|---|---|
| 蓝（复诊、医疗档案） | `#2C4A78` `#D6E3FF` `#0F2547` `#EAF1FC` | `#AFC6F2` `#25406B` `#D6E3FF` `#17233A` |
| 紫（化验、浓度估算） | `#5A3E78` `#E9DDF5` `#2A1740` `#F4EEF9` | `#D3BDEB` `#483262` `#E9DDF5` `#261E33` |
| 玫红（身心状态） | `#8A3B5E` `#F8DCE8` `#3B1226` `#FCEFF4` | `#F0B2CC` `#6A2B49` `#F8DCE8` `#331C28` |

## 3　实现方式

1. **token**：在 `core/ui` 新增点缀色 token（参照 `SemanticColors.kt` 的做法：数据类 + `staticCompositionLocalOf` + `NotesTheme.accent`），不要在各页面里写 `Color(0x…)`。每种点缀色提供 `main / container / onContainer / soft`。
2. **对比度档位**：沿用 `Contrast.MEDIUM / HIGH` 的处理方式（参考 `SemanticColors` 的 `raised()`）：高对比度下点缀文字和图标用 onSurface，不靠色相区分。
3. **动态取色**：开启动态取色时，点缀色不强加固定色，改用动态方案的 secondary / tertiary 家族；三种点缀在动态取色下不要求互相可区分，因为靠颜色传达的信息都有图标或文字。
4. **NotesTheme.kt**：只改浅色/深色方案里的 background、surface、surfaceVariant、surfaceContainer* 等中性项和对应 on 色、outline 系列（见上）。`withContrast` 的 HIGH 档逻辑保持。
5. **每个页面的点缀位置**（样图为准）：

| 页面 | 点缀色 | 用在哪里（只限小元素） | 仍保持青绿 |
|---|---|---|---|
| 抽屉 `AppShell.kt` 的 `DrawerContent` | 紫：浓度估算、化验。蓝：医疗档案、复诊。玫红：身心状态。青绿：日历、服药记录、药物方案、库存。时间线、设置、关于为中性灰 | 每个入口前 38dp 圆角 13dp 的图标块（淡底 + 点缀色图标） | 选中项样式、应用图标块、库存提醒徽标（仍用 tertiaryContainer） |
| 时间线 `LongitudinalScreen.kt`（事件类型到颜色的映射在文件约 396 行：现在化验用 tertiary 珊瑚、就诊用 secondary） | 化验=紫，就诊=蓝，阶段回顾=玫红，里程碑=青绿；图例同步 | 事件圆形标识、图例 | 当前时期标签、过滤选中项 |
| 化验 `LabsScreen.kt` | 紫 | 每行前的小图标块 | 顶部走势卡（淡青绿）、曲线、数字、添加按钮 |
| 浓度估算 `ConcentrationScreen.kt` / `ConcChart.kt` | 紫 | **仅**图上化验结果的小圆点及其图例；服药刻度改淡青绿 | 曲线、E2 合计大卡、范围切换、按钮 |
| 身心状态 `WellbeingScreen.kt` / `WellbeingHub.kt` | 玫红 | 每个项目前的小图标块 | 今天大卡、选中的 1–5 数字、趋势线 |
| 医疗档案 `MedicalRecordsScreen.kt` 及文件库 | 蓝：下次就诊、想问的问题、历史就诊圆圈。紫（同一功能同一色）：化验类文件、化验入口、就诊资料包 | 小图标、小圆圈、文件类型方块 | 添加文件按钮、确认已就诊按钮、链接文字 |
| 首页、日历、库存、药物、设置、关于、历史 | 无 | 只换底色，不加点缀 | 全部 |

6. **原来用珊瑚做类型标识的地方**（时间线里化验现在用 tertiary 珊瑚；动手前在全仓搜索 `tertiaryContainer` / `tertiary` 的其他类型标识用法）改为上表的点缀色。真正的提醒用法（库存不足、已过时间、首页库存提示条、抽屉库存徽标）保持珊瑚。珊瑚是“库存不足 / 已过时间”的提醒色，不应同时当类型色。

## 4　保护文件与授权

- 本任务书是负责人对以下改动的授权记录，请原文归档到样图目录的 `TASK_BOOK_ORIGINAL.md` 并写入 REQUIREMENTS 新节（取下一个空闲 § 号，不要照抄别处）：
  - `LabsScreen.kt`、`ConcentrationScreen.kt`、`ConcChart.kt` 为 PK_DISPLAY_REVIEW（B0 矩阵）。授权范围**仅限颜色与图标块外观**。**不得**改任何数值、单位换算、点的位置、标记/范围逻辑、校准、数据流。改动必须带 PK 显示回归，并把回归证据写入报告。
  - `NotesTheme.kt`、`AppShell.kt`、`LongitudinalScreen.kt`、`WellbeingScreen.kt`、`WellbeingHub.kt`、`CalendarScreen.kt`、`Components.kt` 等为 HISTORY_ONLY，可改。
  - 医疗档案相关文件先查 `docs/audits/product-quality-2026-10/B0-protected-file-matrix.json` 的分类再动手。
  - LIVE_LOCK_RESEARCH_INPUT / LIVE_LOCK_EVIDENCE **绝不触碰**。
- 不升级 Compose BOM 或任何依赖，不用实验性 API。不改 PDF / 复诊资料包 / 导出里的颜色（导出版式另有任务）。

## 5　测试与证据

1. **对比度测试**（参照 `Ui05ContrastTest`）：每种点缀色在 浅/深 × 标准/中/高对比度 下，容器上文字对容器 ≥ 4.5:1，主色图标对淡底 ≥ 3:1，点缀主色文字对背景 ≥ 4.5:1。
2. **token 测试**（参照 `StatusStylesTest`）：状态色 token 全部逐值不变（断言旧值）；点缀 token 与本任务书表一致；动态取色下回退到 secondary/tertiary。
3. **迁移旧断言**：`Ui05TimelineTest`（事件类型颜色）、`Ui03DrawerTest`、`Ui05DrawerBadgeTest` 等凡是写死旧颜色的，按新颜色更新，并在报告里逐条说明“旧含义 → 新含义”。不得删除、跳过、放宽断言；`PeriodStabilityTest` 保持绿。
4. **面积自查测试或检查**：新增一个测试，证明点缀色只出现在允许的小元素上（例如在合成数据下渲染各页面，统计点缀色像素占页面面积的比例并设上限，如 ≤ 8%；上限写进报告，超过即失败）。这条是为了防止点缀挤掉主色。
5. **布局回归**：`ButtonLayoutRegressionTest` 和现有布局测试（四语 × 320/411dp × 浅/深色 × 字号 2）全部通过；抽屉图标块不得导致 320dp、字号 2 下文字被裁切。
6. **前后对比图**：按 UI_STYLE_AUTHORITY 的要求，用**同一合成数据**生成真实 before/after：首页、抽屉、化验、浓度估算、身心状态、医疗档案、时间线、库存；浅色 + 深色，411dp 与 320dp，字号 1 与 2，简体中文与法文至少两语（四语中其余两语按现有截图测试能覆盖的范围）。注明源码 SHA 和生成方式。人工 TalkBack / 真机没做就标 NOT_RUN，不得写成通过。
7. 不把 AI 自评或“能编译”当作验收。负责人看真机或对比图确认后才算验收。

## 6　禁止

- 不得把点缀色用于按钮、大卡、选中项、曲线和图表主线，不得铺大面积底色。
- 不得修改主色、语义状态色、error 色；不得用红色表示非错误。
- 不得让任何状态只靠颜色表达；点缀色元素旁边必须有图标或文字。
- 不得因此改动 PK 数值、导出、数据库、权限、通知。
- 不得加主题开关、色彩方案选择、解释文字或新功能。
- 不得在仓库放真实健康数据；测试与截图用合成数据。

## 7　交付

- 允许分两个 PR：① token + 底色 + 抽屉 + 时间线（含测试迁移）；② 化验 / 浓度 / 身心 / 医疗档案点缀（带 PK 显示回归）。AI 创建的 PR 按 §106 自动合并，CI 精确源码通过、无冲突、无未解决评审才合并。
- 报告编号接最新一份（最近为 77 之后的下一个），写清：改了哪些文件、哪些没改及原因、测试结果、对比图路径、NOT_RUN 项、面积检查的数值。
- 每完成一步更新 `docs/HANDOFF.md` 并推送；新决定写进 REQUIREMENTS 并注明日期（任务书原文归档）。
- 不签名、不发布 Release、不移动 tag，除非负责人另行授权。
- 做完在最后的消息里用中文说清：哪些页面有了点缀、有没有你不同意的地方、需要负责人真机确认什么。
