# ANSM-01：本轮来源核实报告

2026-10-15；原始参考Markdown保持原样。下列结果来自本轮实际取得的官方文件，
不是直接把旧摘录改成生产数据。字段级出处、原行与校验和见
[manifest](../reference/fr/manifest.json)、[source rows](../reference/fr/source-rows.json)、
[pages](../reference/fr/page-evidence.json)、[机器报告](../reference/fr/validation-report.json)。

## 目标核对

|目标|已读官方记录|仍未知/边界|
|---|---|---|
|Provames|CIS65281285(1mg)/65421374(2mg)，分别30/28片；成分原文ESTRADIOL；授权active及销售声明|不把半水合物质量从旧摘录补入，当前成分文件只给该字段；其他AMM包装不猜补|
|Progynova|CIS62512491(1mg)/60940721(2mg)，VALÉRATE D'ESTRADIOL；两者当前均archivée/non commercialisée；各20片|授权归档日期未核实。1mg包装停止销售声明是2022-02-23；不能与旧文档2025-02-23授权归档说法混同|
|Oestrodose|CIS63071975/CIP3400933829247，0.06g/100g，80g；RCP6.5明确64单位、每单位1.25g凝胶/0.75mgE2|不把吸收量、泵启动损失或实际剩余量作为该包装容量；不改旧凝胶PK产品映射|
|Dermestril / Septem|CIS64833859/65662980/66501802：25/50/100µg/24h；Septem64061941/62402447/61774149：25/50/75；包装分别8或4贴|释放速率保留商品原名，成分含量按composition原文独立保存，不以µg/24h替代贴内总mg；不据品牌类推更换频率|
|Thaissept|CIS61614618/65982553：25/50µg/24h，4贴；SA水合物和FT治疗部分分开|FT与格式说明ST代码差异报告冲突，不把两行质量加起来|
|CPA / Androcur|原研CIS61255738，Arrow50/100、EG50/100、Teva100、Biogaran50、Mylan50/100均保留实际原始状态|授权/销售不一，不把所有仿制药标在售；分片等剂量性、监管证明书/风险文本未在本批核实|
|Aldactone / spironolactone|Aldactone25/50/75及所选单方仿制药，CIS/CIP/强度/包装分别建模；含30/90/100片|Pfizer仿制规格有active但non commercialisée；不能以有效授权推断销售/生产或供应|
|Utrogestan / progestérone|100/200及400mg均来自当前文件；100/200保留orale+vaginale，400仅vaginale；所选Biogaran/Viatris单方各有身份|不把相同成分或名称推断成途径相同；现有App非E2 route缺口另批设计|

每个CIS均有对应的已访问产品页及两个独立RCP/Notice面板观察。
这证明入口和身份，不表示逐字审阅了所有说明书的医学内容。
本轮不收录储存、过敏原、相互作用、不良反应、漏服指令、处方资格、PK数值。

58商品、64成分行、96包装；50授权有效、3归档、5撤销；本次目标范围无供应公告行。
每个商品均明确保留production_status、live_supply、authorization_end_date等未知字段。
没有供应条目不能当作供应正常；声明销售不代表实时药房库存。
多语言别名尚未核实，不建立中国商品与法国商品的自动等效关系。

## 官方原名与来源版本

原始下载包含CIS/CIP/COMPO/供应四文件；版本由全文件SHA和有限摘录SHA共同标识。
网站显示2026-09-29，获取日2026-10-15，格式说明2026-10-13，各自独立；
不宣称四文件内容同时于9月29日更新。所有页观察含UTC获取时间/响应字节数/完整HTML哈希。
源文件编码严格解码，CIS/COMPO/供应cp1252，CIP为UTF-8。离线生成不访问网络。

**许可版本冲突是发布阻断项。** 下载页页脚是Etalab2.0链接，实际许可PDF为1.0，
PDF原句与URL、文本哈希见[数据说明](../reference/fr/README.md)及manifest。
本批保留署名、来源更新日、再利用范围和不背书说明；不把官方资料改标项目MIT。
未将资料打包进App，不复制说明书全文，不将许可冲突写成已解决。

## 尚未完成的核实

- 授权撤销/归档的精确生效日期与实际生产状态；不能用查询缺失补猜。
- 来源FT/ST冲突的官方解释；原始值均保留。
- RCP/Notice临床章节、储存、辅料、相互作用及再利用文本范围（ANSM-04）。
- ANSM专题安全通报、召回、MRI/监测资料（ANSM-05/06设计阶段），无自动个人风险判断。
- RCP5.2与现有参数证据的独立科学对照（ANSM-PK）；正式模型完全不动。
- 人工独立药品数据审阅、负责人验收和App端加载/身份持久化。

没有本轮HAS/PubMed研究结论；不会把旧文献或未读资料称为ANSM官方结论。
