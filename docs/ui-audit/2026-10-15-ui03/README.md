# UI-03A 截图（合成数据）

由 `Ui03ScreenshotTest` 生成，运行方式：`UI03_SHOTS=<label> ./gradlew :app:testFullDebugUnitTest --tests net.plainnotes.app.Ui03ScreenshotTest`。

- 画面：真实 NotesApp 外壳，数据全部是合成的。
- 组合：4 种语言（en、zh-rCN、zh-rTW、fr-rFR）× 320/411dp × 字号 1/2 × 明/暗，共 32 种，每种 6 张：

| 文件 | 内容 |
|---|---|
| `01_drawer_*` | 打开抽屉的初始画面 |
| `02_drawer_end_*` | 抽屉滚动到“关于” |
| `10_history_*`、`10_timeline_*`、`10_medications_*`、`10_concentration_*` | 4 个改名页面（含顶栏） |

- `before/`：主线 8e72861 的 192 张。`after/`：本分支的 192 张。均缩到 360px 宽并量化，原图未入库。
- 像素比较（原分辨率）：184 张有变化。8 张完全一致，都是英文浓度页：它在英文里本来就叫“Estimated levels”，没有改名。
- `compare/`：左旧右新的拼图，共 12 张：
  - 抽屉和抽屉底部，各 4 种组合；
  - zh-CN 的 4 个改名页面。
- 人工验收清单：[manual-checklist.md](manual-checklist.md)，全部 NOT_RUN。
