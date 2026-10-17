# 04A 数据与导航合同

本批仅只读展示，不新增药物/容器/计划字段，不修改Room11、业务备份10或PNBAK1。已保存历史仍使用现有冻结UserReferenceProduct；查询新来源版本不能改写旧引用或实际记录。官方目录与临床正文不进入Android资源，生产分发常量固定OFF，无偏好开关、联网开关或灰色旁路。

DocumentEvidence分别保存RCP/Notice种类、CIS、CIP所属、命名空间与版本、原始公共URL、提取/页面身份/正文核实日期、文档版本、已核实Last-Modified、证据摘要、面板标识、入口状态与文本再利用状态。未知日期不补今天。该DTO不存原文，不声明医学或翻译审阅完成；入口存在不提升正文和再利用权限。官方批准前生产注册表为空；测试注入的看似VERIFIED证据在分发OFF时仍被拒绝。正式版本/字段声明级审阅与内容离线读取属于条件化04B，不伪称已完成。

公共产品页面URL必须原本存在于保存的用户引用或未来批准的证据；验证器只验证，不生成产品或文档URL。仅精确https主机、对应八位CIS的/medicament/CIS/extrait；拒绝端口、userinfo、查询、片段、控制字符、非ASCII、百分号/双重编码、不同产品、额外路径。RCP/Notice若只能核实同一商品页，必须清楚标为入口，不猜测直接PDF或锚点。

外部访问需要页面按钮及二次确认，点击时重新验证URL、当前MainActivity初始化/RESUMED/内容可见/未锁定、非PRIVATE、相同会话代数及伪装PRIMARY权限。MainActivity只提供既有状态的只读准入，不修改Session、锁定、PIN或文件选择器豁免。返回浏览器仍由原锁定/伪装机制处理。Context不属于MainActivity时默认拒绝，测试只能显式注入合成浏览器调用。

Intent仅ACTION_VIEW+公共URI+CATEGORY_BROWSABLE，无健康记录/个人参数/extra/ClipData，不嵌入WebView，不使用INTERNET。浏览器或网站随后如何跳转不受App控制，四语确认文字明确说明。无法打开浏览器或权限变化给出可恢复错误；取消、浏览、失败均无Repository写入。离线端无法确认网站当前正文/可用性，不伪造网页错误检测。

真实产品source-check JSON只记录维护者访问日期、响应摘要与DOM面板存在。该次没有逐项审阅页面身份/正文/译文，也没有解决许可证。原始临床HTML仅在仓库外临时目录，不进git或APK。

UI使用原Material3主题、无截断字段、独立48dp外链操作、可滚动父布局、文档标题语义。四语新资源单独成文件并有完整性守卫；正式strings.xml无改动，避免与P4/P5冲突。手动创建与原快捷流程不变。自动合并不在本PR授权内；真机/手工TalkBack/实际浏览器离线与网页下架测试NOT_RUN。
