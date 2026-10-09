# Full APK 所有者私有交付与 P1 验收（2026-10）

## 状态与输入

2026-10-09，本地起始 f73a7e7f4603a456d8735454608dfd5af1ab44ca，预检远端 7ba1f6e3b0a9d7373df365512e4fa406da842701；工作区干净，现有 claude/new-session-1959qb 快进，无重建分支。选定构建/回归源码 **5133a6155635314e0b5621ebe520d7c5601ccd3a**；本轮只添加图表回归和文档，没有生产功能变更。后续文档提交不是另一个 APK 源码版本。

当前交付状态：**正式签名 APK 已上传用户私有 Drive，下载回查字节与本地哈希一致；用户手机未安装验收。** 原环境没有 keystore；用户确认沿用既有隔壁私有仓库后，核实私有属性并恢复原签名材料至受限临时目录。用户另授权 Google Drive 私有交付。凭据/备份/秘密路径不进入源码、公共 CI 或交付物。

## 为什么采用此路线

所有者已有官方签名应用，debug 证书不同不能安全覆盖；unsigned release 不是安装包。使用既有 sign_local_apk.sh，不生成新身份、不放松证书检查。历史正式 full APK 实际 apksigner、证书公告、恢复密钥和脚本四者 SHA256 一致：`989ba04532e4c3ec11c2de989d5b1905cf67bdc6c449361293ef62b8a59379b1`。历史包仅用于证书核验，旧 Release/tag/assets 原封不动。

本轮保持 Build25/0.2.0/Schema9，无确证手机版本需要升号；不能由历史公开包版本推断手机版本。全量官方签名只在本地受控环境进行，APK 不进 Git/公开 Actions artifact。Drive 仅私有，不在公开文档存储受控下载链接或权限凭据。

## 构建及签名工具

JDK21、wrapper9.3.1、AGP9.1.1、Kotlin2.2.20、Android platform/build-tools37.0/37.0.0；min26/target37，fullRelease，applicationId net.plainnotes.app。依赖版本保持 gradle/libs.versions.toml 原值，不升级。release 开启 R8；debug 只用于合成自动验证，绝非安装交付物。

执行命令（本机 JAVA_HOME/Gradle cache/代理仅运行时配置，不改变项目）：

```sh
./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test --rerun-tasks --no-parallel --max-workers=2
python3 -m unittest discover -s tools/pk-research -p 'test_*.py'
python3 -m unittest discover -s tools/pk-audit -p 'test_*.py'
./gradlew :core:data:testDebugUnitTest :core:reminder:testDebugUnitTest :app:testFullDebugUnitTest lintFullDebug :app:assembleFullDebug :app:assembleFullRelease --rerun-tasks --no-parallel --max-workers=2
python3 scripts/check_release_manifest.py
# 受控本地路径，秘密不进入命令文本或公开日志
bash scripts/sign_local_apk.sh BUILD_TOOLS_DIR UNSIGNED_APK KEYSTORE PASSWORD_FILE OUTPUT_APK
apksigner verify --verbose --print-certs SIGNED_APK
zipalign -c -P 16 4 SIGNED_APK
sha256sum SIGNED_APK
```

确切 AGP 未签名路径 app/build/outputs/apk/full/release/app-full-release-unsigned.apk；最终字节身份以同交付清单为准。manifest 检查正常/伪装入口；allowBackup=false，无 INTERNET。签名之后另用 aapt、ZIP/ELF 验证包名/版本/SDK/ABI和原生库，不凭文件名判断。

## P1 150k 的含义与保持历史事实

受控纯舌下 2mg/46min、无化验、200次固定种子：中心278.8647796pg/mL；旧 P5/P95=0/152714.8058638，新=98.8224699/692.8536039。r0.9/1/1.1当前SL全部固定人口速率，输出同一278.8647796。P1-A仅修复数值病态，不证明人体外部准确性，692.85不是医学上限/正常值。混合途径和实际输入须另核验。

未更新手机仍可能运行旧实时算法，不能以新源码否定反馈。Calculator1历史 LabEstimate/PDF原数值保留，旧150k不会安装后自动被替换；新capture(calibrate=false)使用Calculator2。既有 LabEstimateTest 使用合成旧异常快照、加密备份两次往返，逐字节一致，版本2新建及未知3拒绝；未修改真实资料。

P1-B/C1/C2历史完整性、采样时间因果边界、离群集合一致性已实现并保留回归，不将后来化验泄漏到过去；严格“实际获知结果时间”仍独立未实施。参数不确定性不等于测量误差/研究异质性/结构误差的全覆盖。

## 完整区间开关与合成验收

ConcentrationScreen.fullBand只传入ChartData.includeBandsInScale；ChartViewport.top开启时纳入P95，ConcChart始终画内外带。开关是纵轴缩放，不是统计修复/真正外带显隐。新增ChartViewportTest对7/14/60天、局部缩放、拟合断点及读数回调验证数据不变、读数一致、轴有限与中心段保留；是几何层回归，不冒充真机触摸/渲染。

既有PK P1-A覆盖1/2/4mg、四档、临界速率、长期给药、漏服/修正剂量、单/多化验、单位、混合途径；应用回归覆盖冻结上下文、新旧快照、备份、资格门控、因果与全/部分离群。保留原P0/P1历史报告，不重拟合人口参数。参数SHA256保持 b2768a6947d29d65f272b1d20e31fd59b9661acf8166b437f2a3b179f235b16b。

## 用户手机安全验收（尚未执行）

1. 用户自行在原应用导出加密.pnbak到自己控制的安全位置，确认文件存在并妥善保管密码；不要发送备份/密码给开发者。系统自动备份不是充分后备。
2. 确认原安装证书及versionCode不高于25、Android≥26与ABI兼容，系统安装器显示“更新”后才继续。无授权没有连接ADB或安装；用户手机证书/版本/空间未知，因此原位升级仍待确认。签名冲突/降级错误立即停止，禁止卸载、清数据、强制降级。
3. 升级后检查应用锁、原记录/库存/化验、伪装与文件选择器回返、提醒授权/重启、备份导出；合成测试环境检查新实时区间和纵轴开关、Calculator2新估算、Calculator1旧冻结原值。不要在真实主数据库做覆盖恢复或造假医疗事件。

同签名普通更新应保留数据，但未实机验收不作保证。安装失败优先检查证书、版本和空间；崩溃仅收脱敏堆栈/OS/构建指纹，不要求健康历史。禁止以删数据排障。旧二进制回退受versionCode及快照版本约束，Schema9未变也不能保证安全降级。

## CI 与验证分类

源码905f49efb90f80e29fc676b9b83db5725363f5e5的[CI37967659364](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37967659364)三个job实际success；与起始远端7ba1f6e只有3项文档差异。7ba1f6e的run37967453526实际cancelled，不冒称通过。

已通过连接器下载/按GitHub digest核验该CI报告：JVM21文件，PK58/domain58/importer12均零失败跳过；Android224文件，app398（385通过/13既有跳过）、data72/reminder14均零失败；device25文件，API35/x86_64 data14（7迁移/7加密）全部通过，app64（62通过/2重启用例常规跳过），独立重启prepare/verify各OK(1 test)。报告均无APK。初次gh下载受网络限制，连接器下载后哈希核验成功。**此CI不包含本轮新增图表测试**；新测试运行另列。

用户本人手机：not_verified_on_user_device。模拟器debug测试不替代正式签名包原位更新验收。

下载链接仅私有交付消息，不入公开仓库。


## 本轮实际成品与验收

构建输入5133a6155635314e0b5621ebe520d7c5601ccd3a，完整重跑378项任务、8m25s成功。未签名23506758 bytes / SHA256 `916656a4740543ccb87c9cecfa7b091e0e21c43bee2fe6575927ee9a3b85d841`；初次增量包hash不同，**只以最终完整构建及下面签名成品为交付对象**。

正式成品 `hrt-log-build25-full-official-test-5133a61.apk`，**23555291 bytes**；SHA256 **`6cda87754a06a73362cd983aa374f31176f6b710816177d5d043f15c4b56df71`**。v2/v3=true，zipalign -c -P16 4通过，ZIP完整性通过，arm64-v8a/armeabi-v7a/x86/x86_64共8个原生库全部ELF LOAD对齐16384。SHA256既用于本地签名成品，也独立用于Drive接收文件；字节数/哈希完全相同，下载后的签名验证再次通过。Drive元数据 shared=false、仅owner用户权限；没有创建public/link/domain权限。凭据及签名备份临时副本已删除，APK/私有链接不提交Git。用户授权的Google Drive技能完成上传及元数据/原始文件回查。

完整本地实际结果：domain58、PK58、importer12、data72、reminder14全部通过；app399/386通过/13原有跳过/0失败。PeriodStability5、ChartViewport3（含新增1）、LabEstimate1全部通过。Python研究41/PK审计13全部通过；P1-A当前Kotlin结果重放 numerical_acceptance=true、clinical_accuracy_established=false，9个人口场景117点最大绝对误差7.168e-11pg/mL。Lint0错误/131既有警告；Full Debug/Release构建、最终manifest通过。生产源码、SQLCipher/依赖、Schema9、人口参数、原Release清单与旧P0/P1研究证据未改变。

本轮源码[CI37972293625](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37972293625)：**jvm/android/device-tests三个job全部success**。已下载JVM artifact11638115511（21文件，SHA a2f8d986a926289a50e7a7134d74730f602ba3dfd8374fbface7fb06bd8b129d）与device11636718050（25文件，SHA218d4c073bc5e0955c959a537315ec21be9c0b3dd48b72bbaa67af4e1568a7a3），按GitHub digest验真。JVM58/58/12，API35 data14全通过，app64/62通过/2常规跳过，独立prepare/verify日志各OK(1 test)。当前device与本轮测试源码一致，但仍是公开debug证书模拟器，非官方签名实机升级。Android主步骤11m05s，实际下载build artifact11638227309（222文件，SHA25680651565c0144fe56b9e0ca12f2ef3724738725a590a961525d5f62cc32f7dcd）回查app399/386通过/13跳过/data72/reminder14、ChartViewport3，零失败；lint0错误131警告，PK聚焦40及旧P0/P1四阶段数值/资格/因果/集合验收通过。Full Debug/Release、两测试APK编译、manifest及Schema差异检查success。没有上传APK到CI。机器摘要见[JSON](full-apk-owner-test-2026-10.json)。最终仓库提交仅此验收文档，不改变5133a61对应APK；文档提交触发的CI须另外查询，不把源码run冒称文档run。

| 验收分类 | 状态 |
| --- | --- |
| built | fullRelease通过 |
| officially_signed | 原官方证书、v2/v3通过 |
| artifact_delivered | 私有Drive上传及接收哈希核验通过 |
| installable_same_signature | 与历史正式APK一致；用户设备实际证书/版本/SDK/ABI待确认 |
| installed_on_user_device | not_verified_on_user_device |
| user_data_preserved | not_verified_on_user_device；没有操作用户数据 |
| smoke_test_passed | API35 debug模拟器通过；正式签名实机未验收 |
| p1a_numeric_regression_passed | 合成数值通过，不等于临床准确性 |
| new_snapshot_verified | 合成Calculator2新capture回归通过 |
| legacy_snapshot_unchanged | 合成Calculator1异常快照加密备份往返字节一致 |
| physical_device_acceptance | not_verified_on_user_device |

尚未完成：本人手机覆盖安装/锁/伪装/提醒/文件回返/数据保留与图表实际触控；严格化验结果获知时间回放、人体外部准确性和完整结构误差区间仍独立未实施。完整区间显隐/旧快照版本提示仅Backlog候选，不阻断本轮出包或改写历史。
