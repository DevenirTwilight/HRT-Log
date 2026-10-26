# Targeted workspace correction

2026-10-26, source patch based on da5f4ee4. Final local MedicalArchive9 + FeatherlineContainer7 + ArchiveKdfWorkspace4 =20 PASS, failures/errors/skips0; actual XML retained. Existing fixed Argon/HKDF and independent Python cipher vectors remain unchanged. New coverage: bounded reused blocks, wrong/correct password and different salts, concurrent archives, cancellation after KDF clears key and subsequent call works. Native core test Kotlin compile PASS. These are host results, not low-memory/API/physical acceptance.

First local prototype refused all KDF work because its allocation bound included65536 memory blocks but omitted BC1.86 FillBlock's four auxiliary blocks. Original RED XML/console retained. Corrected bound65536+4 includes the algorithm's existing4KiB overhead; protocol64MiB memory parameter is unchanged. No original assertion was removed or relaxed. Final source must pass the same native and512MiB/192MiB gates.
