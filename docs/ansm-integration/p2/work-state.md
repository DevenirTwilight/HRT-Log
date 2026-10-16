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

截图矩阵首次FAIL：Compose captureToImage在Robolectric Dialog上超时，尚未产图；不是已完成截图。改用ShadowDialog取得实际Dialog窗口并原生Canvas绘制，保留全部语义/滚动/操作断言；后续重跑并目视检查。另强化结果上限测试，显式选择确实不在前200条中的快照，避免随机UUID排序使测试只验证已显示条目。

最终源8e910ee：data239、app62全PASS，0失败/错误/跳过；32组合×三档对比度=96配置、192张真实Dialog原生绘制合成图均非空，8张四语代表图入库，其余由CI收集。人工抽查en320/2x/dark/HIGH、fr320/2x/dark/HIGH、zh-CN411/1x/light/STANDARD及zh-TW411/2x/light/STANDARD，长名换行/原单位/身份/状态明确。不是实机或TalkBack证据。
已普通合入父PR #54的1f8d150证据更新（仅文档）；#56仍是以feature为base的草稿，不得auto-merge到feature。CI集成计划：#48未修改.github，#54的现有原生类清单保持，只在同一android job设置截图环境并追加合成截图artifact路径，检查名和全部断言不变；真实最终CI待当前HEAD。

对照负责人P2原始要求补齐原始规格筛选：仅原文包含搜索，不将2mg与2000µg自动视为同一商品。原先239/62证据仍对应8e910ee；新筛选变更单独重跑。

原始规格补齐源ca399ec的data239/app62与192图再次PASS。新增EmptyPreview恢复负向测试随后真实FAIL，明确观察到source events=1，证据empty-preview-red-evidence.json。P1 #54已转回草稿修复本地previewEpoch协议，#56保持草稿，旧通过证据不冒充此边界已通过。

已合入P1空预览epoch修复cb7d90f，真实控制器首次提交携带打开时previewEpoch；每次成功后独立读取实际最新关联（幂等回放的原事件不伪装当前状态），仍不调用全局refresh/PK。此前空引用恢复红测试预期转绿，最新完整回归待运行。

真实历史来源UI已接入HistoryRecordDialogs的只读入口，捕获选择记录时的ExtraState.referenceEpoch，DAO同一事务读取记录与epoch；不在异步按钮执行时重取新数据集票据。历史来源窗口保留原始版本、明确参考背景不代表实际包装；旧记录未记录来源不补填当前引用。新增四语320dp/2x的冻结来源/无来源/恢复后拒绝/原编辑窗口入口测试12项；最新回归待运行，P1历史接口237数据/原生24项编译已PASS。

历史四语新增测试首轮12项中4项FAIL：fixture只设active=false仍保留已生效rule，NotesViewModel正常reconcile生成AUTO_MISSED，导致“single record”断言失败。改为创建无计划的合成历史药物（saveMedication active=false），保留记录数量/原内容相等断言；12项定向重跑PASS。未改变生产reconcile。另复核旋转期间在途写入：控制器open改为busy时返回null，保留原事务/目标且AppShell不重新打开错误目标；原真实阻塞测试追加拒绝open及状态完全相等断言。

最终源6697747：data243/app75全PASS，零失败/错误/跳过，含真实历史UI12项、控制器7项、四语UI16项、矩阵32项、Period5与Privacy3；96配置192图，8张代表图和全量摘要已更新。最新人工抽查zh-CN320dp/2x/dark/HIGH，未冒充真机或TalkBack。合成fixture首轮4失败保留在工作记录，不删除断言。普通合入父PR最新945512a（含已合并#45/#48冻结资源及debug原型）；最终整合CI尚待执行。#56仍是feature base草稿，不能合进feature，需父PR #54合入默认后改base并按保护检查自动合并。

父#54六检查在源945512a全部SUCCESS并保护合并c95cea1。#56已改实际默认base并普通合入c95cea1；后续保护自动合并仅针对默认分支，不再是feature base草稿。P1最新三API及完整CI证据更新于p1-ci-evidence.json，旧d9证据独立归档，未混同主线复验。
