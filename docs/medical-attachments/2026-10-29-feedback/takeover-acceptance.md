# PR #156 takeover: bounded failure fixes (2026-10-29)

Source before fixes: `54abbdaa07e027cacfb24dace246e8edc8a2c70a`; default `d95952eed3f6732dd99297019a5fb02ca4304191`. Existing rename CAS, authenticated full-screen viewer, Room23 and all public attachment capabilities are retained. No format, quota, key or scientific-model change. No APK built/signed/delivered in this takeover.

## Original failures retained

- Android run [40918579394](https://github.com/DevenirTwilight/HRT-Log/actions/runs/40918579394): host job123605715021 failed `Ui09SourceGuardTest.onlyTheListedExceptionsStayCentred` (3793 total, 1 failure, 601 existing skips). New pager/zoom Rows added two centered layouts outside the inherited exception list. Production rows now use start-aligned 8dp spacing; the guard and all its assertions stay unchanged.
- Same run, API29 job123605715674 / API35 job123605715429: DocumentsUI could not find `SYNTHETIC sample.threepage.pdf`. Provider enumerates all five files, but DocumentsUI virtualizes the directory. Test previously waited for a name outside visible rows without scrolling. It now traverses the real accessibility `dir_list` via ACTION_SCROLL_FORWARD with observable contents, still selects the exact name through real SAF, and refuses an exhausted directory. No direct URI injection or coordinate shortcut.
- API26 job123605715711: whole-draw test timed out at original line460, after BACK waiting for `lab-panel-detail` to disappear. The failure tree still contains the full panel. The production panel now registers an explicit BackHandler in its Dialog; later viewer handlers retain precedence. Native confirmation is pending, not presumed PASS.
- Original low-memory run [40918579392](https://github.com/DevenirTwilight/HRT-Log/actions/runs/40918579392), library job123605391955, artifact12917774187: first PREPARED watchdog included encrypted source collection creation plus target authentication/restoration. PID5158 continues logging work until the runner force-stops it at17:28:09.790; the later instrumentation `Process crashed` follows cleanup. This does not establish LMK, nor prove the absence of an earlier resource problem. Kernel snapshot captured before this restart test cannot settle its cause. Featherline job123605391115 passed and is not changed.

## Bounded changes and measurement boundary

The collection fixture is now generated once in its own `source` instrumentation process, using identical production writer/reader/restore components and the existing60-second source bound. Each live restore still independently authenticates/reconstructs the same collection with its original60-second seam bound and90-second fresh-process verification. Source, prepare and verify reports remain separate. This changes attribution of test setup, not restore internals; it is NOT a claim that the original combined60-second operation or LMK is fixed. RAM512MiB/heap192MiB, fixture members and all restore assertions remain unchanged.

Shared native test geometry reads now resolve `SemanticsNode.boundsInRoot` inside runOnIdle on the Android main thread. Off-thread layout resolution could enter Owner observation from the instrumentation thread; this is the targeted correction for SnapshotStateObserver failures observed on #153 and #155 too. Direct off-thread page Text configuration reads are replaced by a normal semantics matcher. No production thread system is rewritten; final native validation is required to confirm this hypothesis.

## Validation status at this checkpoint

- Python runner syntax: PASS; git diff whitespace: PASS.
- Targeted Ui09 source guard: PASS (4 tests,0 failures,0 skips); app/core AndroidTest Kotlin compilation: PASS. Gradle119 tasks, BUILD SUCCESSFUL in8m24s. Local log retained as `/workspace/scratch/156-takeover-targeted.log`; no local emulator/KVM, so this is not native acceptance.
- New-source native API26/29/35, low-memory and full required CI: PENDING.
- Physical device, manual TalkBack: NOT_RUN.
- #156 remains Draft. Original failures are not relabeled PASS. #153/#155 retain independent PRs; old uncommitted MED03 worktree is preserved and excluded.

## Serial final targeted result

Implementation/test source6e6822d7:246 tests PASS,0 failures/errors/skips; XML in local/serial-final. App AndroidTest Kotlin compilation PASS. Earlier overlapping local Gradle invocations collided in the shared binary-result directory (NoSuchFileException), preserved at /workspace/scratch/156-page-transition.log; that run is invalid as product acceptance. All affected controller/viewer/navigation tests were then executed together serially, BUILD SUCCESSFUL in2m29s. Native final-head checks remain PENDING; physical device/TalkBack NOT_RUN. Low-memory run40925404991 passed both jobs for061fe4de, with the setup timing boundary described above unchanged.
