# 31 — MED-02B 最小实施方案与审批清单

2026-10-14，DESIGN_ONLY。报告30已验证三API公开PDF及30项/端合成POC，仍须独立review后再评审；协议未经独立安全认证，下面不是已上线功能，不实施Room/备份/真实附件迁移。

## 十项技术决定

1. **推荐协议**：SQLCipher加密元数据+独立不可变对象；AES-256-GCM块使用新对象版本DEK，随机nonce前缀+递增序号，末尾独立保留nonce认证总长/块数/SHA256。本POC头68字节、固定块记录8字节+16字节tag、认证footer68字节，magic SYNPOC01明确不能成为生产格式。头包含store/object/version UUID和块大小，全字段AAD；无临床名称、路径或公开hash。发布前完成EOF清单，打开时完整认证哈希，再随机重认证目标块。原语由JCA实现，不自制密码算法；新DEK使每次重试在独立nonce域，不能原位覆盖同DEK。标准向量、错误key、头/tag/序/缺块/拼接/截断及跨空间测试是证据，不等于审查。生产协议字段/扩展/反回滚/备份域须独立评审冻结，禁止直接复用POC magic。
2. **PDF**：只接受实测公开ProxyFD+PdfRenderer兼容结果，按offset逐块认证到RAM、不生成完整磁盘明文。真实API26/29/35结果由30与设备证据记录，未跑不能声称可行。只保持一张受限页面位图，锁/换空间撤销读权限、关闭FD/native renderer/recycle；Java/内核RAM无法承诺彻底零痕迹。完整对象认证成本O(N)使打开前读取全部密文但不一次驻留全部明文。更大的/恶意PDF可能在native parser占内存，需隔离进程/超时/页数限额/实际低内存ARM测试后生产开放。
3. **外部PDF依赖**：公共API若三API实际通过，则本阶段无需引入外部库；密码PDF/脚本/嵌套/恶意页/不同设备驱动必须单列，未验证的功能不启用。若Proxy不可行，推荐保管密文+明确用户导出（是否允许需产品批准），或单独研究审查过的内存随机IO解析库；绝不换明文临时文件方案。
4. **图片**：原字节不修改（含EXIF）；魔数/MIME/bounds/像素/采样/内存限额后解码，无磁盘thumb。技术可行只由合成JPEG/PNG/WebP与恶意尺寸结果支持；生产需接AppLock/session generation、FLAG_SECURE、旧设备OOM/corruption解析和独立review；POC通过不能当产品验收。
5. **配额**：保守提议16MiB/原件、256MiB/空间、1000对象；首轮建议64KiB块（4MiB stream实测且buffer较小）；1MiB仍需随机IO/ARM对照后再选。不因更大附件放宽PNBAK1限制。图片源像素暂32MP且各边≤32768，单缩略图≤4MiB、边≤1024；PDF单页≤16MiB且各边≤2048、仅一张保留。这些是待批准限额，不是手机实测峰值保证；剩余磁盘至少64MiB+新对象上限、替换需新旧两份。
6. **Room9→10设计**：只新增attachment_document（storeUUID、标题、用户分类、revision、trash）、attachment_version（随机UUID、协议、大小/加密DB内hash、DEK envelope、状态、旧版）、attachment_link（typed目标、原记录ID、recordEpoch/明确关联revision）及operation/generation journal。主/伪装独立库/KEK/root，不复制Appointment/Lab/真实服药数据，不破坏不可变流水。单独schema审核/9→10迁移+旧SQLCipher文件/旧PNBAK1回归、typed链接父记录回收/恢复同ID和失败回滚测试；本轮没有新表/迁移。
7. **备份共存**：保留PNBAK1 magic/Argon2id参数/32MiB/恢复行为。未来完整医疗包用新magic/version、固定受信KDF预算、加密元数据和可移植KEK/DEK、逐对象流与认证最终索引/EOF；不是把BLOB/base64偷偷塞RawData或扩大旧限制。提供记录备份时清楚标不含附件。POC PBKDF2 envelope仅测试密码跨key恢复，不替代正式Argon2id选型，不代表全包协议已实现。
8. **旧备份ID冲突**：为每次记录恢复提供recordEpoch；旧PNBAK1恢复后当前附件cipher保持隔离、自动解除链接，不凭相同整数ID绑定新记录；用户明确核对后重新分配。新全包恢复采用新cipher generation+数据库事务最后切pointer，旧库/旧对象直到提交后保留，恢复中断不会先删旧对象。POC模型测试不等于跨文件/SQLCipher原子实现；后续必须真DB/掉电/journal逐阶段注入。
9. **独立审查**：AAD域分离、GCM消息/总字节/每DEK限额、nonce生成/保留和重试、结束清单/全部hash/EOF、反回滚（AEAD不能阻止旧有效cipher重放）、Keystore/便携envelope/KDF、授权竞态、PDF native攻击/缓存/内核RAM、SAF云提供者/分享Uri、fsync目录与文件/rename/DBcommit/drop/recovery。PoC只证明测试范围，不宣称密码学安全认证或OOM攻击全面覆盖。
10. **MED-02B最小范围**：先批准协议review及迁移/关联政策→实施加密元数据+图片原件导入/查看/逻辑作废/回收恢复→新完整备份与原子恢复可靠后才作为正式可存储功能启用；PDF只在公开API实测及parser资源限额过关后加受控预览。不要先上线无法可靠完整备份的原件库。每批独立PR、旧功能回归和最终HEADCI；不改PK/版本/签名/build26，不自合并/交付APK。

## 分阶段实施及失败回滚

- B0：独立密码学/供应链审查和固定向量，API26/29/35+低内存ARM解析/内存基准；审批具体Schema/新包/配额/旧备份政策。未批准仍停在POC。
- B1：新表/迁移及独立store root/KEK、alias管理；只合成数据迁移回归，生产默认功能开关关闭。Keystore缺失明确要求可信备份，不生成新key冒充解密。
- B2：SAF输入→ bounded RAM→CREATE_NEW密文part→file fsync→完整认证→同文件系统rename+directory fsync→SQLCipher READY/link事务。授权generation和publish在同一mutex复核；取消/锁/换space在提交前拒绝。崩溃journal只清本操作owned文件，不扫描删除他人文件；任何失败旧active保持，孤立密文不可进入UI。
- B3：图片viewer与明确关联/可修改分类/文字，revision拒绝陈旧；原文件替换新DEK版本，旧版可撤回；逻辑作废/恢复重新核查引用，不物理删除历史。多链接按引用计数，不跨space去重。列表SQL元数据lazy分页20，禁止预解密所有原件。
- B4：新流式完整医疗包/密码错误/硬盘预算/逐阶段恢复、recordEpoch冲突处理及新设备重包裹；旧完整generation最后GC。恢复中断保留旧pointer，提交后新pointer为唯一权威，不能靠多文件copy自称原子。
- B5：经实测PDF受限预览、可选隔离解析worker、FLAG_SECURE/锁/force-stop/取消/后续读取拒绝；导出需二次告知外部明文/原始EXIF不可收回。分享/密码PDF首批默认不开放，需专门验证与批准。
- B6：四语320dp/2x/TalkBack、API26/29/35/真实ARM、1000件/256MiB、全部B2/B3/B4/R15/MED01和Science；独立PR验收后由负责人决定上线/版本/签名。

## 必须由负责人批准

1. 新生产协议独立review及Room10表/关联revision/recordEpoch机制。
2. 独立完整医疗包格式/KDF/容量和旧PNBAK1附件隔离、人工重关联策略。
3. 16MiB/256MiB/1000配额、回收/旧版本保留与永久删除（不能消除外部备份）。
4. 首批图片范围、PDF可行后启用范围、密码PDF/分享推迟、真实ARM测试设备与验收基准。
5. 是否需要伪装医疗档案；默认仍只是真实PRIMARY医疗页，伪装笔记不开放真附件/共享key/去重/计数。

这些审批阻塞正式附件实施，不阻塞MED-02A合成研究；本轮不实施上述任何新生产机制。

## 恢复身份与旧记录备份的兼容细节（需批准与实现验证）

必须区分逻辑dataset/store UUID与本设备物理目录/Keystore alias。完整跨设备恢复经可信最终manifest后保留原逻辑UUID（密文AAD不可随便改），在用户明确选定的PRIMARY目标中新建cipher generation和本设备alias/key envelope；不得借此把PRIMARY医疗库恢复进伪装笔记或绕过space授权。POC RecoveryModel的incomingStore==store仅覆盖同一逻辑store拒绝异空间包，不是“任意新设备安装已实现完整恢复”。

新Room10的storage schema与旧record-only备份序列化schema需独立定义/审核：不能直接把Room版本号10写进PNBAK1并假定老解析器能读，也不能把新attachment tables/keys/links偷偷导入旧格式。保留已定义的记录序列化契约并在新app兼容读旧schema1..9；恢复records与epoch切换/quarantine在同事务完成。旧app能否读新app导出的record-only包，必须给固定9列/旧snapshot实测矩阵和明确兼容标签；本轮不改任何PNBAK1字段或实现此适配器。
