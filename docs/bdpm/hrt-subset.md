# HRT 相关离线目录（2026-10-22）

负责人确认采用宽范围：雌激素、孕激素、睾酮及相关雄激素、抗雄激素和GnRH抑制辅助药，补充tibolone替代治疗。`tools/bdpm/hrt-selection.json` 是可审查的精确BDPM成分代码表；标签用于提取，没有疗效、适应证或剂量含义。未把SERM、芳香化酶抑制剂、所有生殖治疗药品无限扩展为HRT。

默认 `python3 scripts/build_bdpm_catalogue.py` 从完整官方归档离线生成随APK分发的子集。`--profile full`可复建原全量库供核查，不要将该输出误提交为HRT资产。更新时复核新成分代码/盐型及unmatched_codes，再运行 `python3 scripts/test_bdpm_catalogue.py` 和相关Android查询/关联测试。

规则只匹配SA行的substance_code，选中产品后保留其**全部**原组成（包括未用于筛选的成分与原SA/FT/ST）、原规格、基准、所有剂型、途径和全部CIP包装，不做剂量相加。包括同成分复方、避孕用途、局部制剂；检索收录不代表适用于用户HRT，不自动关联或修改用药/库存。

当前420个CIS、550条包装、702条组成；数据库360448B（352KiB），较原11116544B减少96.76%。这是未压缩SQLite大小，不是实测APK体积增量。真实PROVAMES 2mg/CIS65421374/CIP13 3400935479341仍在；成分、CIS/CIP搜索和原来源快照机制不变。现有历史快照不重写，目录版本和数据库SHA随子集变化；旧目录关联不要求继续出现在新目录。

“全部”的证明范围是：**规则表中每个成分代码，在此法国BDPM快照内命中的所有产品及其全部组成/包装**。不是全球HRT全集；法国源未收录的海外商品和此快照不存在的成分不伪造补齐。顺势疗法TESTOSTERONE/PROGESTERONUM与放射性FLUOROESTRADIOL不因名字相似入选。现有源TEST横幅、更新时间和许可限制详见README及原始manifest，没有被新筛选消除。

完整官方原字节仅保留tools源归档供维护者复建，不放Android assets，不进入用户SQLCipher库。Room/PNBAK1/关联UUID及不可变快照合同均不改变。尚未签名交付或发布；PR98需新候选必需CI和设备证据，不以Python替代设备验收。

本地fullDebug构建实测：APK ZIP内SQLite压缩98386B、来源manifest9021B，合计107407B（约105KiB），不含ZIP头/签名/公共代码，不能等同整个功能APK增量。见validation/scope-closeout/apk-asset-size.json；未交付该构建。
