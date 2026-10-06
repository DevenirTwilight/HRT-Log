# 表层工具、真实应用与私人便签

2026-10-06。仅 full 变体提供此功能。没有网络、遥测、云同步或自动生成内容。

| 表层工具 | 普通输入 | 真实秘密 | 替代解锁码 |
|---|---|---|---|
| Calculator | 计算器 | PRIMARY HRT | 私人便签 |
| Notes | 普通便签与搜索 | PRIMARY HRT | 同一个私人便签 |

## 数据与认证边界

`Disguise.check` 返回 `UnlockTarget`，不返回数据库 `Space`。秘密仍用现有 Argon2id（19 MiB、2 次迭代）与 Android Keystore 加密哈希。为保留旧版已设置的替代秘密，继续使用原 credential 文件/alias；名称不出现在 UI。所有有效格式的错误码共用原有 10 次/60 秒静默限速，修改替代码不清掉限速。配置变更修订号可拒绝旧的异步认证结果；结果返回时 shell 已离开前台则拒绝进入任一受保护目标，不偷开界面或重新开放会话。

PRIMARY 只进入 MainActivity，NotesViewModel 固定 PRIMARY；提醒一直固定 PRIMARY。PRIVATE 只进入非 exported 的 PrivateNotesActivity，没有 NotesApp、HRT 导航、onboarding、repository/entity/domain/PK/导入导出依赖。私人界面只允许编辑、删除和回 shell，没有进入 PRIMARY 的入口。

私人笔记的 id/title/body/createdAt/updatedAt 保存于 credential-protected `noBackupFilesDir/private.bin`。AES-256-GCM、每次写入随机 nonce、版本/AAD 和独立非导出 Keystore `notes.private`；单个 AtomicFile 文档，无明文缓存和数据库。读取损坏/缺 key 时显示普通错误，绝不将其当空文件覆盖。跨 store 实例串行读改写；清空使旧实例失效，防止排队/迟到的后台保存重新创建已删除内容；10 MiB 文件上限。输入、列表和草稿只在内存中，配置重建用独立 ViewModel 保留，锁定清空内存，不写 Activity Bundle。备份被全应用禁用，HRT backup/export 不涉及此文件。

旧 Space.DECOY 保留已有 `notes_b.db` / wrapped key / alias，目的仅为升级时不静默删除原内容以及显式清理。新认证和任何新 UI 均不选择它，不把旧 HRT 内容转换成私人便签。用户确认清空、移除替代码或禁用伪装时，等待清除新私人笔记及旧空间完成后才显示成功；清理步骤不可因 UI 销毁而中止，不启动随主 ViewModel 销毁而取消的清理任务。PRIMARY 不受影响。改替代码保留私人笔记；清空保留替代码；移除替代码同时删文件、AtomicFile sidecars 及独立 key。

## 会话与返回栈

会话只有内存中的不可变 target/generation 快照（StateFlow）。PRIVATE 持续观察认证；撤销或换目标即隐藏内容并退出，不等下一次生命周期事件。新进程无认证；Intent/Bundle 无法设置 target。PRIMARY 和 PRIVATE 互斥，停止旧 Activity 的回调不能锁掉新认证会话。开启伪装是在已解锁的设置页中保护当前 PRIMARY，保留当前 Activity 的代次；因此后台仍能正确锁定。配置异步写入本身不开放会话；完成回调核对原 Activity 仍 RESUMED、代次仍相同，不能在用户离开后或新 PRIVATE 认证后重开 PRIMARY。

真实伪装认证后的 PRIMARY 不再立刻要求普通 PIN；非伪装模式仍遵守普通 PIN、后台超时和生物识别。MainActivity 的 create/start/resume/newIntent 均验证会话，PRIVATE 不能通过它。PRIMARY 只有 system document picker 在前台时免于离开即锁，例外按代次持有、返回只消费一次、launch 失败撤销；配置重建不锁定，进程重启不保留例外。PRIVATE 无 picker 例外。

Shell/Main/Private 使用同一 task affinity。切换和退出通过 NEW_TASK | CLEAR_TASK 替换整个任务，不把 MainActivity 留在私人空间下面，也不在启动替代任务后调用 finishAndRemoveTask 删除新任务。PRIVATE 的 Back/Close 回当前 shell，编辑页 Back 取消草稿回列表；摇动也退出。后台清会话，恢复时回 shell；配置重建保留当前有效目标。禁用伪装后私人 Activity 直接结束，不跳到 Main。真实/私人屏幕都有 FLAG_SECURE。

## 普通 UI 身份审计

| 位置 | 处理与限制 |
|---|---|
| Launcher | 正常 alias 独立 HRT Log/原图标；伪装 aliases Calculator/Notes，非当前 aliases 关闭 |
| Android Settings / 通知 header | application label 永久中性 Notes（便签/便箋）；full application icon 为普通 Notes 图标 |
| Recents / TaskDescription | shell 用对应标签/图标；PRIMARY 在伪装时也用 shell 身份，启用时立即替换当前 task description；PRIVATE 用 Private Notes/Notes 图标；受保护窗口禁截图 |
| Notifications / AlarmClock | 伪装时强制使用中性标题/正文，不显示详情或自定义内容；“Taken”改为“Done”，提醒源仍 PRIMARY。启用取消旧通知，启用/禁用后重建提醒 |
| Shortcut | 不发布动态或固定 shortcut；旧 launcher shortcut/OEM 缓存可能保留旧身份，需用户移除/重加 |
| Share sheet / deep link / intents | 无 share/deep-link receiver；只有 launcher aliases exported；Main 和 Private 不 exported，内部显式启动仍验证会话 |
| 文件选择器 / import-export | 仅真实应用提供；返回例外保留，PRIVATE 不提供这些操作。不从 picker intent 获取认证 |
| Backup filename | 加密 `notes-日期.pnbak`；CSV/PDF 文件名为 notes；私人内容不参与。用户主动导出的 HRT CSV/PDF 内容本身可辨认用途 |
| Crash / error | 私人 UI 只有普通便签错误，没有异常文本、数据库名、内部类名或恢复 HRT 的按钮；不记日志。系统崩溃/ANR/历史记录由平台控制 |
| 重启 / 重建 / 后台 / 摇动 | 目标不持久化；重启锁定，重建保留有效内存会话，后台及退出撤销；两种 shell 都回自身，不经过 HRT |

中性名称不是抹除 Android 身份：applicationId、安装来源、签名、APK 内的真实功能与正常 launcher alias 都可被系统/包分析查看；旧 Recents/shortcut/OEM 缓存、文件选择器最近文档和用户导出的报告不能由本应用可靠擦除。输入法和可访问性服务属于系统/用户启用的服务，也不能保证隐藏其观察结果。此功能保护普通界面流程，不承诺抵抗 root、恶意系统或 APK 静态分析。

## 验证

- 单元测试：Session 的目标/代次/普通 PIN/第二层 PIN/文件选择器/启用后锁定；私人 store CRUD、重开、加密、并发、损坏/key 缺失、删除隔离；源码依赖边界；通知中性文案；已有计算器、截图、多语言、HRT 功能测试。
- Android：真实 Keystore 与密文、两种 shell 输入/路由/Back、CRUD/旋转、wrong code、未配置/同码/限速、改码/移除/禁用、Main 直接启动、正常 App Lock、PRIMARY 无第二 PIN、后台及 picker、Manifest 系统名称/alias/非 exported 属性。
- 真正进程重启：`scripts/run_disguise_restart.py` 在两个独立 instrumentation 进程之间 force-stop，验证新进程无 target、直达私人 Activity 被退回 shell、便签密文保留。
- 合并的 full/play release Manifest 由 `scripts/check_release_manifest.py` 审核；源码 Manifest 不是最终依据。最终运行结果见 HANDOFF §2e。
