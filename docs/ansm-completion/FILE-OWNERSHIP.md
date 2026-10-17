# 文件修改隔离

本会话只有Codex在 /workspace/HRT-Log-p4、feature/ansm03b-p4-exact-stock-20261016 / PR#57 实施；无子代理。

- P4当前独占core/data的库存、引用、备份、Repository和共享historyTest合同，以及相应Room候选schema、P4守卫与阶段交接。共享Gradle/CI不顺带修改。
- 共享HANDOFF不并行改写，独立进度以ansm-integration/WORK-STATE.md和p4/work-state.md为准；REQUIREMENTS仅追加负责人新决定。
- P5需P4稳定/合并后另起分支，才整合ViewModel/库存UI/四语；先重查其他开发者PR。
- 04/05/06从实际默认分支独立worktree/PR开始，不复制未合并P4生产代码；只读DTO可独立设计。其新增临床/版权资料不由P4自动合并授权覆盖。
- 没有并行代理时顺序执行；若外部开发者开始编辑上述共享文件，先划分明确文件责任再接线。
