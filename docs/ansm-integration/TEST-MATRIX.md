# 测试矩阵

当前所有新测试NOT_RUN。每批记录命令、源码SHA、API/ABI、计数、失败和artifact摘要；不继承旧HEAD绿灯。

P0：非空同库/异库/重复恢复、CONSUME/REVERSE/CONSUME、FK孤儿/循环/深链/超预算、导入失败整库回滚、guard完整、重开、SQLCipher API26/29/35；旧PNBAK与全既有回归。
P1：旧1..10→新Room完整链，旧schema1..9→新业务、current catalogue缺失roundtrip、CAS/op冲突、域复用整数ID、快照与捕获不可变、trigger清单、逆量/ADJUST/VOIDED。
P2/P5：四语、320/411dp、1x/2x、明暗/对比度、取消/键盘/重启/备份恢复，AppLock/伪装边界；人工TalkBack/真机NOT_RUN。
P3/P4：原35保留、生产/原型同向量差分、来源有向转换、部分片/凝胶/贴/胶囊/注射合成、同规格FIFO、混合UNKNOWN、Legacy/Verified互斥、事务故障与强停。
六检查：jvm/android/device-tests/compat26/compat29/science-change-gate，保留全部既有任务、断言、科学/权限守卫。
环境JDK21=/tmp/ansm02-tools/jdk，SDK37=/tmp/ansm02-tools/sdk；本地无KVM，原生由CI。Gradle限额1536MiB、1worker/in-process；既有CI不弱化。
