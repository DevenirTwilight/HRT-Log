# Exact source77394f6f API26 failure

PR run40207089641 job121210243171, artifact12605025552 SHA256 `86f93897df7c6cbeb79fa93169e4d227a2341625338f09f90bd29ecc87df1983` verified, all ZIP CRC verified. Original XML, full logcat and primary failure retained. Not a passing run.

Primary error: choose(tracker) waiting for SYNTHETIC tracker.db at line141, actual window net.plainnotes.app. Logs show picker displayed15:34:39.768 and MainActivity restarted15:34:40.849 before any file touch or host-resume wait. Thus added lifecycle wait cannot fix this earlier failure. The explicit Back used to close roots was racing the system's automatic root-selection close and cancelling the picker; this test is not allowed to inject extra Back during source navigation.

Minimal correction: select root using only a node scoped to actual drawer_roots, await the system's own close, then await/verify real dir_list file. No Back during selection. Final cancel first navigates a root if necessary (same actual system action) then sends one intentional cancel Back. All original dual-source/preview/COMMIT/reopen/cancel assertions preserved. Lifecycle and primary-failure diagnostics retained. No production changes. Fresh exact-source native acceptance required; no unchanged-source retry requested.
