# 用户商品资料界面：P2

官方目录分发仍为OFF。以下证据是实际生产组件使用**用户录入的合成资料**生成，不是BDPM商品核实或真实健康记录。

依赖P1 #54的稳定引用/历史/备份API；本阶段不新增Schema，不改变库存或PK。PR #56当前stacked草稿，合并前必须retarget实际默认分支并重新通过六检查。

本地data239、app62全PASS；后者包含Controller6、四语小屏2x操作16、布局矩阵32、PeriodStability5及Privacy3。每个布局遍历STANDARD/MEDIUM/HIGH三档对比度，产生96配置/192张合成截图。local-evidence.json记录准确测试源、报告摘要和限制；完整图片摘要见screenshot-manifest.json，四语代表图在screenshots/。

复现：`ANSM_P2_SHOTS=local ./gradlew :core:data:testDebugUnitTest :app:testFullDebugUnitTest --tests '*MedicationReferenceControllerTest' --tests '*MedicationReferenceUiTest' --tests '*MedicationReferenceMatrixTest' --tests '*PeriodStabilityTest' --tests '*PrivacyNoticeFactsTest'`。图片位于app/build/ansm-p2-ui-shots/local。CI使用ci标签并上传到现有build-results。

Robolectric截图与SQL/交互测试不能代替真实TalkBack、软键盘、锁屏/伪装和设备验收；没有运行的项目明确NOT_RUN。无签名、发布或APK交付。
