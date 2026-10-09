# P2-F：TransPrEP 的 LC-MS/MS 证据与更高阶吸收核的必要条件

2026-10-09。性质：研究方法与数学结构审查，**不授权替换正式人口模型，不修改已签名 APK、个人化验、原 P2 冻结输入或版本**。原始数值、来源等级和共享队列关系见 p2f-cohort-and-transit-source.json；独立数学代码及测试在 tools/pk-research/。

## 一、新原始研究与数据独立性：Yager 2022 + Abdelmawla 2023

**原始人体论文**：Yager JL et al., *Gender-Affirming Hormone Pharmacokinetics Among Adolescent and Young Adult Transgender Persons Receiving Daily Emtricitabine/Tenofovir Disoproxil Fumarate*, 2022, DOI 10.1089/AID.2022.0044, PMID 35815468, https://pmc.ncbi.nlm.nih.gov/articles/PMC9910105/。研究登记 NCT03652623。

- TransPrEP 研究入组26名跨性别女性，有1名缺密集血药采样，实际用于激素PK分析25人。**13名口服/舌下合并组**，12名肌注组。这不是13名纯舌下受试者。
- 受试者至少稳定使用相应激素1个月或连续三剂，不是洗脱后的单剂新开始。每日给药口服/舌下组血清采样计划0（给药前）、1、2、4、6、8、24h；在开始PrEP前和2–3周后重复。E2使用LC-MS/MS。
- 口服/舌下合并组Table2基线（尚未使用PrEP）E2 AUC_last几何均值为**1531 pg·h/mL**，Cmax几何均值**191 pg/mL**；使用PrEP后分别**1434**与**161**。论文Cmax置信区间列排版有端点顺序异常，本次只转录几何均值，不自作SD修复。
- 文献没有把该组拆分成纯舌下与纯口服逐人时序，不提供可以把这个AUC唯一对应给1mg单剂纯舌下的实际记录。AUC_last为持续服药时的观测浓度暴露；不能直接与Price1997**单剂扣个人基线**后的AUC0–24 2109进行大小比较。
- 未获取可用于估计纯舌下形状参数或独立预测覆盖率的逐人可公开数据。即使论文有原始LC-MS/MS，也不等于我们已取得原始逐人浓度。

**同队列人口PK建模论文（不是新的独立实验）**：Farah Abdelmawla, 2023, University of Colorado Anschutz MS Thesis, *Population Pharmacokinetics modeling of gender-affirming hormone in adolescents and young transgender persons receiving PrEP therapy*, DOI 10.25677/3ygcx-1ac37，机构存档 https://digitalcollections.cuanschutz.edu/works/publication-dissertation/3ygcx-1ac37。

机构摘要明确指出使用TransPrEP的26名跨性别女性等原始研究资料做人口PK再分析，报告**口服/舌下合并模型表观半衰期约28.4小时**。这不是纯舌下单剂的实测消除半衰期；模型的吸收与终末辨识问题、实际分剂史和合并途径都会影响参数解释。**不能把28.4h塞入正式E2_SL的消除速率**，也不能将同队列的论文+论文改写为两项独立人体外部验证。

本轮直接阅读了机构网页摘要；机构PDF的下载被服务端/网络访问限制阻止，未核实全文的微分方程、SE和诊断图。不得写为已经完整审稿、已获得可再分发的参与者级数据。机构标注版权状态未评估，全文不入Git。

## 二、Rosano1997 四个早期时点对候选核的明确数学限制

既有P2-E原始数值取自Rosano1997独立药代子队列**25名绝经后女性（不是同论文主要干预试验9+7名）**，1mg SL后10/20/40/60分钟E2群体均值分别**234/468/1980/2124 pmol/L**。此n25队列的治疗前平均基线b未知，不能挪用另一心血管n9组的基线。

若假设单次给药、固定非负基线、同一种零延迟输入、正速率和一个n级gamma核：
    
    h_n(t)=A*t^(n-1)*exp(-k*t), A>0, k>0

这里n是**经验数学形状阶数**，不等于人体真实器官或分室数量。其两倍时间之比满足严格上界：

    h_n(2t)/h_n(t)=2^(n-1)*exp(-k*t) < 2^(n-1)

Rosano 40/20的原始群体均值之比 = 1980/468 ≈ **4.23077**。任一固定基线0≤b<468的扣基线增量比只会更大；因此 **n=2（上界2）、n=3（上界4）的单个零延迟gamma核都无法精确再现此组中心均值**。若仅据这一对点选一个**整数**gamma阶数，n至少4，但这**绝不证明人体具有四个吸收分室**。

更重要的是**10/20/40三个时点**。单个gamma核（同正k）应满足：

    h(20)^2 >= h(10)*h(40)

将实际浓度扣同一个未知基线b后，需要：

    (468-b)^2 >= (234-b)*(1980-b)
    b >= (234*1980 - 468^2)/(234+1980 - 2*468)
    b >= 191.155 pmol/L ≈ 52.07 pg/mL

**191.155是简化模型同时匹配这三个均值的必要条件，不是该人群真实基线估计，也不是充分条件**。若让n作为连续有效形状参数，且强行匹配20/40与40/60两个均值：
    
    R1=(1980-b)/(468-b)
    R2=(2124-b)/(1980-b)
    alpha=ln(R1/R2)/ln(4/3)  (=n-1)
    k=3*[alpha*ln(2)-ln(R1)] (/h)

当b=0：n≈5.770、k≈5.591/h，而这种核对10/20的预测增量比约10.74，实际是2；若假设b=225，n≈7.599、k≈7.790/h，对10/20则约26.46，实际约27。两种**任意基线情景**都会给出极不相同的有效速率和阶数。这些只是数学重建，不是经过辨识的人体参数或标准药代拟合。

因此早期增长的反常形状，可能来自更复杂的输入、溶出延迟、多路径与人群/检测差异；不能简单“拟合出一条更接近四个点的曲线”后称其为验证。更高阶gamma核也不能自动解决Price1997 **24h AUC** 或 Doll2022 **LC-MS/MS 8h时间窗** 的研究差异。

## 三、继续研究时必须遵循的模型选择原则

1. 不再把Price基线扣除单剂AUC、Yager慢性用药总浓度AUC_last和Doll 8h AUC比当成三个相同统计对象直接拟合。
2. 不能使用TransPrEP人口论文的28.4h直接固定舌下单剂真实终末清除；真正消除率与输入/翻转动力学须分清。
3. P2-A/B已证明双途径生理吞咽份额、生物利用度和容积不能从一条稀疏曲线各自识别。增加多个lag/transit/k只会加大自由度，必须用实测高密度早期与晚期数据约束。
4. 本轮Yager /论文的摘要、Rosano等均为已见探索性材料。不能冒充新LOCKED_EXTERNAL；目前无已证明独立外部人体时序准确性或90%真实覆盖区间。
5. 下一步应尝试依法/经作者许可获取TransPrEP按纯SL/口服拆分的匿名逐时浓度、剂量和采样时刻，以及Doll LC-MS/MS实际逐时数据；同时向Price/Rosano原作者询问基线、完整分时表、检测法和统计类型。无真实数据则只做结构敏感性报告。

独立机器验收：

    python3 -m unittest discover -s tools/pk-research -p 'test_p2f_transit_audit.py'
    python3 tools/pk-research/p2f_transit_audit.py --out /tmp/p2f-AFTER-UNIQUE.json

脚本拒绝覆盖既有输出，仅执行公开文献的小量汇总值与解析公式，临床有效性标记明确false。**本轮未改现有人口模型或历史，不批准P2-C替换**。
