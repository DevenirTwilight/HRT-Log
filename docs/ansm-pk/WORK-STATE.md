# ANSM-PK 独立证据审计

基线实际默认claude/new-session-1959qb / f1e9b4de1c97e73966b4af8900b78391eada5c43。独立分支feature/ansm-pk-evidence-firewall-20261017，不复制#57库存生产源码，不参与P4合并。负责人V4§8允许证据索引与科学防火墙，不授权正式模型变化；V4原文见#57归档owner-v4-2026-10-17.md，SHA256 c6728ed10f66b61bf19df6253bc591bc325ac6a8a1ab9604e5b41226967fcd48。

当前仅维护者资料：复用现有P2研究而不重新拟合，索引pk-params中全部BDPM引用与P2获取台账。本次实际取得Oestrodose、Progynova2、Provames2三个产品页面；记录HTTP响应哈希/时间与RCP5.2限定事实，不打包原文。其余官方来源必须明确NOT_RETRIEVED_THIS_AUDIT，不能借三页成功宣称全部已核实。科学独立复核PENDING、官方APK资料OFF，模型变化MODEL_CHANGE_BLOCKED。

关键边界：Oestrodose约10%/75µg是标签吸收描述，不是库存/个人吸收入血保证；80pg/mL是报告的平均浓度，不擅自改成Cmax。Progynova约一天是代谢物排泄描述，不是母体E2消除半衰期。Provames口服2mg峰时3–6h；当前E2_ORAL基于另一FDA资料、参数检查峰时约7.026h，构成产品/研究条件差异，需要独立科学审查，不能从单个范围反拟合整条曲线。没有据此修改正式模型或声称模型错误/临床准确。

尚未完成：其余官方引用及58商品RCP5.2逐项核实、完整研究方法/人群对照、独立科学复核、最终CI。新索引不把此前已暴露研究改称held-out；旧P2的0独立锁定SL队列结论以既有台账及哈希保留，不自动认证模型。真机/模型改动验收NOT_RUN，不提供建议剂量，不发布。

本地维护者检查：离线索引字节复现PASS，原有Python156项PASS；没有新增拟合/治疗算法，不能把这些脚本测试叫作医学验证。18个PK/浓度源文件逐一与默认基线blob摘要相等，pk-engine/app/core/importer相对基线无任何差异。当前仍无原生或真机新功能测试（NOT_RUN），CI待提交后运行。

独立草稿PR59：https://github.com/DevenirTwilight/HRT-Log/pull/59。验收源3ec00f5的push/PR六项必需CI全SUCCESS（科研深测按原条件有PASS/SKIPPED）；本PR未下载远端报告，不借其他PR的artifact当本次证据。本次增加现有参数顶层旧status与实际models加载的口径核查、来源点击链接及CI状态归档，未改生产参数或源文件；索引字节复现PASS。新证据提交需自己的最终六CI。尚未全面核实其余标签、58产品及研究方法，独立科学审查PENDING，模型变化/官方分发仍BLOCKED，未经授权不合并。
