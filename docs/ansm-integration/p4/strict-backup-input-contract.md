# 严格备份输入检查点

状态：输入边界已实现；完整精确库存备份/恢复仍未启用。

PNBAK1 的 magic、KDF、AES-GCM 和外层格式不变。解密明文在 finally 清零；UTF-8 无效字节、尾随 JSON、重复键、非标准数字和注释拒绝。解析应用原有大小/深度预算，并限制对象字段和累计数组元素；新库存整数投影不经过 Double 解析后再判断。

INTEGER_QUANTA_V1 的 capacity、initial_used_amount、used_amount 在 Schema11 中必须以整数词法序列化并处于 0…2^53−1。小数、指数和 -0 不接受，即使浮点解析会变成整数。LEGACY_SCALAR 的原 Double 事实保留；不声称由十进制文本恢复原始计量精度。没有标记的 Schema1…10 只能保留旧标量模式。

本检查点没有移除公开路径的 legacy-only / PRODUCT-only 门。全局校验后再接通：可信内存暂存 → 旧标量独立缓存处理 → 完整精确历史与缓存一致性检查 → 单事务目标替换 → 本地 epoch 更新。精确库存不得走现有 SUM(Double) 重建。还须补齐历史 VOID/RESTORE 正文与前缀证据，然后运行实际 Repository 原子恢复合同。

证据：strict-json-red-evidence.json 保留旧公开恢复接受尾随内容的失败；strict-json-initial-evidence.json 保留新夹具基线初始化顺序错误；strict-json-final-local-evidence.json 为修正后的334项回归。原生源码编译不等于原生运行。3343724 的 API35 CI 失败为旧测试固定业务版本10，与候选11不一致；改为明确11并增加旧容器LEGACY断言，原VOIDED/账本/重启断言继续保留。
