# 恢复原UI，仅增加模型选择（2026-10-11）

最新用户指令：“我要恢复原ui，只增加选模型功能”。425a7ab0c75c625c070a17b116cc4f99aa79ff0d直接复用原ConcentrationScreen和原ConcChart，不再使用新研究页面布局。此前b272/059卡片重排原型不作为最终修复交付；059 CI成功也不代替本修复验收。

实际导航为抽屉→唯一血药浓度页→原页面中的M2/M1/Legacy选择。原提示、浓度卡片、7/14/60天原图表、单位、完整范围开关、化验点、校准、模型参数、其他药物和历史修复均留在原组件中。删除主界面的新参数表单、指标分区、研究模式切换和叠加控制；保留科研源码、候选参数、比较引擎和归档证据，无实验页面导航。

M2显示固定候选p2x-14（p2x-m2-research-v0.1的15候选族首项，不冒称逐点中位模型或临床最优），M1使用p2az-m1-grid-v1的32候选族首项p2az-m1-00（n=1、kfast=4/h、kelim=0.4/h、慢输入权重=0），候选ID在页面显示；该选择是确定、可追溯的研究展示约定，不是冻结唯一临床M1，不混入P2-V。Legacy使用未经改动的原ConcentrationCalculator、Engine及0.1.0-literature-draft参数。

两研究模型沿用不可变历史适配器，仅读取合格实际舌下E2剂量，按1mg/1h共同响应归一化并叠加。C=B+A×H，B=25、A=100 pg/mL均人为情景假设，明确标注，pmol/L按3.671换算；化验点只作观测，不参与情景标定。没有合格舌下E2历史且存在其他历史时退回适用Legacy，研究选项禁用。未来尾部不加入计划剂量，不显示候选上下界为人体90%区间。原校准/参数卡片仍读原Legacy result，情景不进入该生产结果对象。

两处受保护UI文件的精确原/新SHA、授权和实际diff分别见original-concentration-ui-authorization.json、original-concentration-ui.patch及P2追加偏离；冻结基线/协议锁、Legacy数学、ConcChart/ChartViewport、参数及数据源码未改。check_protocol.py保留精确链及变异拒绝，不建立通用浓度文件豁免。

## 本轮验收状态

本轮425a7ab源码的Python协议守卫及9项负向/授权测试通过。本地29项聚焦JVM/原生截图测试0失败0跳过，其中16组四语×320/411dp×1/2字体生成原页/研究选择页共32张新截图。原卡片、时间选择、单位、点击/缩放/平移、模型切换、页面重建、精确核函数、多剂量叠加和不可变历史读取断言通过。CI38165696135的domain/pk/importer共184项0失败0跳过，API35设备data14项0失败0跳过、app67项0失败2项原有进程重启用例跳过，其中本页3项均通过。该2项不是本轮验证成功用例，不沿用旧001补跑当作新测试。设备截图主模型切换、重建和横竖屏实际通过。

CI38165696135三任务（JVM/Android/device）均成功。除上述184项外，data72、reminder14均0失败0跳过；app420项0失败13跳过（12项可选原生截图在本地16组32截图已实际补跑，1项原有原生PDF仍为CI跳过，不称本轮完成）；Debug/Release/仪器APK及lint构建均通过。CI lint 0错误、172警告，未关闭检查。旧研究页资源字符串仍在源码，资源收缩会剔除未引用项，本轮不冒称已清理所有lint警告。

本地同一425a7ab源码最终命令16m24s退出0：29项、Debug、fullRelease、Debug仪器APK及lint均通过；本地lint 0错误/172警告。APK对应被测源码，仅后续文档/验收记录提交，不替换被测应用文件。科研比较核的单次Debug模拟器60剂/30天计算4081.639ms、PSS227374→230954KiB，非本页FPS、Release性能、物理设备或个人临床验证。

已人工视查简中411dp/正常字及320dp/200%字截图，原卡片/控件及模型行恢复；原ConcChart在320dp/200%字右端日期有继承自原实现的轻微裁剪。该原图组件未修改，画布日期裁剪未由语义测试覆盖，记录为既有显示限制，不冒称所有视觉问题已消除。

## 正式签名及交付

原正式证书SHA-256为989ba04532e4c3ec11c2de989d5b1905cf67bdc6c449361293ef62b8a59379b1；签名备份按HANDOFF §7从已授权私有仓库恢复，仅临时目录，沿用scripts/sign_local_apk.sh校验签名和16KB对齐，私有Drive交付。此前用户反馈前的APK（12,815,220B、b53ad1d70a114a5538b7af455a05f2d94996cdf484c94be99cdefb09a4f52ccb）不是本修复版，不再次交付。本修复版正式签名APK为12,782,452B（12.782 MB），SHA256 `2bdfc370499c409cf8c916ba5db35c5a53b1dcc35d21081005e4b332f41896b8`。v2/v3签名、原证书、ZIP CRC和16KB对齐通过；私有Drive文件只有owner、shared=false，独立重新下载的字节数/哈希/签名/对齐相同。临时签名凭据已清除。私钥、密码、APK和私有链接不入公开仓库。

未签名fullRelease为12,736,319B，SHA256 `c145fb953f5c6ae0ab1963fff0bce758f65f8699cc3135f53e7ad9fe7ea39b3a`。相比错误新UI原交付源码001的12,768,827B减少32,508B；相比059中间重排原型12,774,843B减少38,524B；相比默认12,707,919B增加28,400B；相比旧PR3实验页12,812,823B减少76,504B。正式签名包相比之前正式交付包减少32,768B（签名/对齐开销与unsigned变化分开计算）。同一工具链/配置，四ABI、全部原生库CRC/压缩方式/字节数一致；具体DEX和resources.arsc差异见apk-size-audit.json/md。原ConcChart源码11298B/178行保持原样；研究页外壳19548B/221行降为2916B/48行，新只读preview2365B/37行；原ConcentrationScreen从32107B/388行到32874B/390行，只增接线及必要情景标签。

个人M2 pg/mL正式生产替换仍缺个体绝对标定及独立临床预测验证；这不阻塞此原UI修复、条件情景与签名交付。未合并默认分支、未公开发布。

## 本轮实际修改文件

应用：experimental/UnifiedConcentrationScreen.kt、新增experimental/ConcentrationPreview.kt、ui/AppShell.kt、ui/ConcentrationScreen.kt；四语strings_unified_concentration.xml。验证：debug/UnifiedConcentrationTestActivity.kt、androidTestFullDebug/UnifiedConcentrationAndroidTest.kt、UnifiedConcentrationUiTest.kt、新增ConcentrationPreviewTest.kt、testFull/audit/ButtonAuditTest.kt。协议：docs/pk-research/p2/protocol-deviations.json、tools/pk-research/check_protocol.py及test_unified_protocol.py、original-concentration-ui-authorization.json及.patch。交接：REQUIREMENTS §60、HANDOFF顶部及本报告；concentration-layout-restoration-evidence提供真实测试/哈希/体积/签名及接收证据。完整相对059源码修改清单为425a7ab提交的18个文件，后续提交仅文档和验收记录。

删除的是统一页中的重复UI分区和状态，不删除科研计算/比较/归档，不删除Legacy生产内核或用户数据/数据库字段。不改变原ConcChart、ChartViewport、Engine、ConcentrationCalculator、pk-params.json及数据库迁移。PR #19仍开放待合并审查，本轮没有合并默认分支或公开发布。
