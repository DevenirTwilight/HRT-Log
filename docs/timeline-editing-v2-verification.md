# build 25 验证（2026-10-08）

功能源码：`52b092a98b8d2cbb6a48b0759112458c6488eb74`；要求见 REQUIREMENTS §40，设计见 [timeline-editing-v2.md](design/timeline-editing-v2.md)。只有 full，versionCode 25，schema 9。

所有测试数据均为合成数据，没有读取用户健康记录。

| 检查 | 结果 |
| --- | --- |
| app full 单元及 Robolectric/Compose | 230 项，217 通过，13 既有跳过 |
| core/data | 72 通过 |
| core/reminder | 14 通过 |
| core/domain | 58 通过 |
| pk-engine | 18 通过 |
| importer | 12 通过 |
| 合计 | 404 登记，391 通过，13 既有跳过，0 失败 |
| lintFullDebug、full debug/release、data/app Android test APK 构建 | 全部通过 |
| 合并 release manifest、schema/PLAN 无意外变更 | 通过；包名和入口身份正确，无 INTERNET |

最终功能 CI：[37838271727](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37838271727)，jvm/android/device-tests 全部 success。之后提交仅补充验证/交接文档，安装包来自上述功能源码。

正式签名 full APK：23,510,235 bytes，SHA256 `1397ca198fc8841788238a29976139e4bd917520513475d362a0bdf11ee3ed64`。原证书 SHA256 `989ba04532e4c3ec11c2de989d5b1905cf67bdc6c449361293ef62b8a59379b1`；v2/v3、ZIP 和 8 个原生库 ELF LOAD 16KB 对齐核验通过。独立下载回查 SHA 一致，临时私钥/密码/备份 clone 已清理。

## 业务回归

- `TimelineV2Test` 6：系统识别直接生效、相同标准保留用户边界、至今在随后实际治疗变化处结束、build 24 显示边界/标准/同日卡片数/标签保留、转换幂等，以及删除已因方案变化结束的时期不能再向后留下用户残段。最后一项先以失败测试复现，再修复。
- `TimelineV2FlowTest` 5：重叠拆分固定邻段、缩短本段重新显露系统、完整覆盖单独入回收站与恢复冲突事务回滚、删除恢复保持原显示身份、旧备份和转换幂等、旧删除的已确认背景经转换后仍能恢复。原始记录和方案版本逐值比较不变。
- `PeriodStabilityTest` 5：原有黄金数据、操作顺序、夏令时与跨午夜、删除不留空方案检查继续保留；原 4 组历史增加第 5 组“编辑过的时期”，在库存、包装、备注、提醒/语言/时区、备份恢复、重启和回收站操作后投影及标签不变。未放宽原断言或添加跳过。
- 界面检查：统一表单、精简模式隐藏药名/剂量、重叠预览接受与取消；无确认/撤销确认和拆开/合并菜单。旧格式操作仅在测试夹具中保留。
- 新化验上下文记录 SYSTEM/USER_EDIT；资料包新模板去掉待确认计数，旧冻结资料保持兼容且不修改。

## 限制

API 35 模拟器覆盖数据库版本迁移和现有原生界面/PDF/重启测试；build 24→25 的时期行转换由上述合成 Room/Robolectric 流程测试覆盖。没有验证用户真机的覆盖安装、输入法/日期选择器，以及其具体历史；不声称已读取或核验真实数据。PK 公式和参数未改动。
