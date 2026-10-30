# UI-14 复诊页升级：前后对比

- **before**：主线 `a022952a`（UI-13 ① 已合并，UI-14 之前）。**after**：UI-14 分支（见 PR）。两边用同一个生成器 `Ui14VisitsShotTest`、同一份合成数据，只调用 `VisitsScreen` 的长期参数：`UI14_SHOTS=before|after ./gradlew :app:testFullDebugUnitTest --tests '*Ui14VisitsShotTest*'`。
- **合成数据**：占位医生和机构名（Doctor Placeholder、Clinic Placeholder），不含任何真实就诊、医生或机构信息。
- **四个场景**：`1_past-two` 只有过去两次（均已确认）；`2_future-and-past` 一次未来加两次过去（一次已确认、有问题和资料包，一次未确认）；`3_unconfirmed-only` 只有一次未确认的过去就诊；`4_long-location` 很长的地点（两行省略，TalkBack 读完整内容）。`2_future-and-past_scrolled` 是同一场景滚动到列表后部。
- **覆盖**：简体中文、法文；320dp/字号 2 和 411dp/字号 1；浅色、深色。四语 × 字号 1/1.3/2 的布局断言在 `Ui14VisitsTest`（384 项）里，这里只放图。
- **NOT_RUN**：真机、人工 TalkBack。这些图不是验收，负责人看真机或对比图确认后才算验收。
