# ANSM-02A 验收边界

本批为debug-only合成原型，非导出测试宿主，没有正式导航入口。
正式药物手动创建未改；Mock手动按钮只显示预览，不创建真实药物。

自动执行命令（只维护full）：

```sh
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew :app:testFullDebugUnitTest --tests 'net.plainnotes.app.catalog.*' --tests '*PeriodStabilityTest' --tests '*TranslationsTest'
```

完整CI仍运行原有任务和六项必需检查。此原型不需要新库/网络权限/Gradle或CI配置。
`CatalogPrototypeHostTest`实际从debug assets读取本地合成JSON；其余场景以注入方式覆盖
Loading/Failed/Ready/empty，测试不会访问官方站点或用户数据库。

截图位于 `app/build/reports/tests/ansm02/screenshots/`，随既有build-results报告归档，
96组合（四语×两宽度×两字号×明暗×三档对比度）×editor/results/detail/confirm=384。
代表图在[screenshots](screenshots/)，完整SHA清单和测试结果在[local-verification.json](local-verification.json)。
这些是Native Robolectric实际渲染，不是设备截图或人工TalkBack验收。

|项目|自动覆盖|独立人工/真机|
|---|---|---|
|四语、320/411dp、1×/2×、明暗、三档对比度|96参数化布局，字形边界/完整字符/无省略号/文字对比度/heading/48dp|NOT_RUN|
|搜索、重音/大小写、三筛选、多CIS/包装/途径、未知与归档|9数据合同，真实解析合成资产|真实数据适配NOT_IMPLEMENTED|
|确认/取消/返回/重选/解除、非法国/无结果及手动回退|四语8交互用例，真实资产宿主1用例|输入法/OEM键盘/物理Back NOT_RUN|
|加载/失败/重试/空目录与保留草稿|四语注入状态交互用例|真实损坏Android正式资产NOT_IMPLEMENTED|
|TalkBack阅读顺序、链接区分、焦点和手势|heading及48dp语义断言；四语文案检查，RCP/Notice使用不同标签|人工TalkBack NOT_RUN|
|PRIMARY锁定、PRIVATE/伪装边界|正式入口不存在；源码边界/现有回归CI|真机锁/Launcher NOT_RUN|
|正式关联、目录更新后的真实历史、Room/备份新字段|无正式实现；只能证明生产路径未改|NOT_IMPLEMENTED|
|实际RCP/Notice网页及无浏览器恢复|无真实URL/Intent；模拟入口提示不打开任何网页|NOT_IMPLEMENTED|

02B需另行验证真正Repository的取消/重复/并发CAS、历史快照、旧PNBAK恢复与新备份回滚、
完整迁移、删除/恢复和库存/PK不变性，不能用本表Mock通过替代。

人工复验建议：在独立测试环境运行宿主测试并检查报告；该Activity不导出，不提供adb公开入口。
正式数据门开放前不要给用户安装带真实目录的构建。没有发布、APK或生产Schema授权。
