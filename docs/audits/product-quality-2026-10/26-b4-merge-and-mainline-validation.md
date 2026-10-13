# 26 — B4 两项 PR 顺序合并与主线验证

2026-10-13；状态：两项 PR 均 MERGED_CI_VERIFIED。产品负责人在独立验收 PR24 后明确“合并”，授权见 REQUIREMENTS §85。

| 步骤 | 验收 HEAD / 实际合并 | 新 CI | 结果 |
|---|---|---|---|
| PR23 | 8749207 → d06bccee040cc9c488be319e21fffb3c5d66ff3a | [Android38463996746](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38463996746)、[Science38463996997](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38463996997) | JVM/android/device全部success；gate/deep实际success |
| PR24 改目标后复验 | 92f8c42，新基线d06bcce，验证merge32345cb | [Android38466418672](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38466418672)、[Science38466418719](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38466418719) | 三项Android及gate/deep全部实际success |
| PR24 合并后主线 | 9af38f3008b7d8b7260c995f397b20e59174d443 | [Android38468046764](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38468046764)、[Science38468046661](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38468046661) | 三项Android及gate/deep全部实际success |

## 顺序与源码一致性

先以merge commit合并PR23，等其主线完整检查通过，再把stacked PR24目标从B4改为默认分支claude/new-session-1959qb。改目标前后仍为原19文件差异，没有重复引入B4或修改已验收HEAD。GitHub合并预览引用暂仍保留旧8749207父提交，不能直接将旧预览当新基线证据；独立验证merge32345cb明确以d06bcce与92f8c42为双亲执行完整CI。复验成功后，重新核对默认分支、PR HEAD和mergeability，使用expected-head92f8c42合并PR24。

PR23合并双亲bab53d3/8749207，树25123f002b5cf6f33c5cc157652ea6cccf73fed5与其验收HEAD相同。PR24实际合并双亲d06bcce/92f8c42；验收HEAD、验证合并及实际主线树均为2fae7ed87075e70e8b8d11480456c4a5d8b17134。保留开发与证据分支，没有squash、rebase或历史重写。

首轮主线Android38463928846因同SHA后续push触发原并发规则而cancelled；未主动取消、未计为通过。替代Android38463996746实际成功。其他Science重复事件的成功与最终验收运行分开记录。机器证据见[26-b4-merge-evidence.json](26-b4-merge-evidence.json)。

## 最终记录提交与边界

本记录及HANDOFF/STATUS/REQUIREMENTS归入默认分支时，相对于已全绿的9af38f3只改文档，生产源码保持一致。最后文档提交自身完整CI也须完成成功才交还；精确最终SHA和CI链接固定在PR正文，不循环追加自身SHA提交。检查不因本记录而取消。

原报告24/25、B2/B3及库存/纠错/导航测试保留；不新增产品阶段。500条包装最多3个挂载语义节点的既有证据不等于真实ARM性能基准。CONSUME/REVERSE直接作废的原安全限制不变，本轮未扩大情况C。Room、库存流水/预测、PK、备份/加密、版本与签名配置未改。

已有正式build26 APK SHA256仍为0c6bd9132ab15ca7625f1da19b59c1c9450cf4cb0518b15100e30396c5ed3fa0；未恢复密钥、重新签名、生成新正式交付包或上传APK，无Release/标签操作。CI常规Debug/unsigned Release构建不作为新交付。无需新增产品决定。
