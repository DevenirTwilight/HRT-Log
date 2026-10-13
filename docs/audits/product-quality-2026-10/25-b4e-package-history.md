# B4-E 包装历史分页与长期可用性（2026-10-13）

基线：PR23 / feature/b4-stock-consistency-20261013 / `8749207ad883161275ead8a3ec458b0a352cc868`，四组最终CI已通过，PR OPEN未合并。新分支feature/b4e-package-history-20261013；独立后续stacked PR，不改变已验收PR23。

范围仅PackagesDialog展示与测试：有界LazyColumn，四正常组与高级VOIDED每组20条按需追加；普通历史与VOIDED默认折叠/隐藏；稳定ID及开封日相同时ID排序；列表状态在详情覆盖期间保留，更新删除失效行。记录已在内存，仅UI渐进展示，不新增数据库分页或后台任务。

计划验证1/20/21/100/500包装、100VOIDED、混合分组、多批/末尾、详情返回、作废恢复/重分组、320dp/2倍字号/四语，记录实际可观察的已组合节点数量。原库存计算/纠错/B2/B3/Science完整保留。当前测试结果PENDING，不提前宣称通过。

Room9、SupplyLedger、流水、VOIDED业务、PK、PNBAK1/加密、build26和签名配置不得改动；不合并、不签名、不交付APK。
