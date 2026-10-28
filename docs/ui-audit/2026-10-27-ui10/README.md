# UI-10 视觉证据（2026-10-27）

全部来自真实 Compose 渲染（Robolectric NATIVE 图形，合成数据），没有生成图。样图（`docs/design/claude-ui-2026-10-17/assets/`）是设计概念。

- `before/`：开工前 main `019c805d` 的生产组件（en、zh-rCN；320dp/2×、411dp/1×；化验详情弹窗只能在 320dp 渲染，Robolectric 的 411dp 对话框不会空闲）。
- `compare/`：并排对照（样图 | Before | After）。`hub_records.png`、`hub_files.png`、`lab_detail.png`、`attachments_and_file_page.png`、`library_and_detail_components.png`（后者是等 MED-03 的组件，合成数据）。
- `after/`：精选 After（每个画面 en/411/浅、zh-rCN/411/深、zh-rTW/320/2×/浅、fr/320/2×/深）。全部 4 语言 × 2 尺寸 × 浅/深在测试里渲染，用 `UI10_SHOTS=<目录名>` 环境变量重新生成：`./gradlew :app:testFullDebugUnitTest --tests 'net.plainnotes.app.ui10.*'`，输出在 `app/build/ui10-shots/<目录名>/`。
- Before 的生成器是一次性测试（已删除，渲染的旧组件已不存在）。
