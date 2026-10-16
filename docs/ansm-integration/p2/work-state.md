# P2 生产用户商品资料界面

2026-10-16。依赖P1 PR #54，当前分支feature/ansm02b-p2-user-reference-ui-20261016，起点d9fee991bf61b98a3d560a9a224ba29fb9b4fa9b。父PR尚未合并，创建PR时明确stacked base，合并前改为实际默认分支并验证完整依赖。与P3 #55独立。

状态：CODED / LOCAL_TESTS_IN_PROGRESS，不代表CI成功、实机验收或发布。

真实生产入口位于药物编辑和药物卡片菜单；新增药物可以浏览资料，需先保存药物再明确关联，现有快速手动创建保持。取消资料窗口保留原药物编辑草稿。真实关联/解除操作走独立控制器和P1专用API，不走saveMedication、reminders.mutate、refresh、loadConcentration，不改剂量/计划/途径/库存/PK。

官方目录OFF：不包含BDPM资源或SYNTH示例目录。可搜索的只是用户曾录入的不可变商品快照；所有资料USER_ENTERED / UNVERIFIED。大小写/法语重音/œ/æ折叠仅用于搜索，不用于存储身份和等效判断。商品/成分查询与剂型/途径包含筛选离线执行，结果最多200条匹配；继续扫描原库，细化查询可找到之前未显示的匹配。不同CIS、规格、版本和快照不合并。

规格及原单位仅原样展示，不推断含量、包装容量、推荐剂量或吸收量。授权/销售/供应状态均UNKNOWN；RCP与Notice分别标未核实，不生成医学摘要。仅允许打开用户提供且对应CIS的受限BDPM商品URL：先由用户确认交给外部浏览器，不附健康记录参数，失败显示错误。无INTERNET权限。

用户录入姓名/成分/剂型/规格/途径/来源版本/录入日期/CIS/CIP/来源URL，录入日期不称核实日期。成分与途径按换行分项，不拆分化学名称中的逗号。过长CIS/CIP/日期不得截成合法的另一身份；本地校验失败保留可修改草稿。预览和最终确认分离，重复点击busy期间不重复提交；重试复用operation UUID；过期scope/revision必须明确重新加载。

新增测试：ReferenceCatalogTest 6项；MedicationReferenceControllerTest 6项，真实Room/备份/药代Profile/库存/历史不变性；四语320dp/2x生产UI操作16项；32种locale/width/font/theme组合各遍历三档对比度，截图可用ANSM_P2_SHOTS=<label>导出合成图，未执行不标PASS。TalkBack/物理设备测试NOT_RUN。

首次本地app编译因跨模块nullable scope无法smart cast而FAIL，已改为捕获局部不可变scope，未削弱断言。测试和截图最终结果随后写入local-evidence.json。

首轮core/data239项PASS；应用Controller5+四语UI16+Period5+Privacy3共29项PASS。随后补充busy期间真实等待/重复确认和关闭拒绝测试，以及加载竞态与过期后明确reload，正在运行最终含32组合/三档对比度矩阵。截图与最终PASS尚未宣称。
