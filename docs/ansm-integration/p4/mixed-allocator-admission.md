# Prevent scalar fallback for mixed stock

New negative integration checks exercise the real unscheduled Repository entry with both a typed package and legacy scalar package, and attempt to pass an explicitly UNKNOWN measured record into the old allocator. The intended contract is refusal with no record/context/ledger/cache mutation; selecting an unrelated legacy package is not a substitute for actual package confirmation.

The scoped physical-use path remains separate and explicitly retains UNKNOWN without deduction. Legacy-only medications remain unchanged. Mixed packaging requires that path even if the currently available scalar package could numerically cover the clinical dose. No route, strength or physical source is inferred from numerical compatibility.

Status: LOCAL_INTEGRATION_VERIFIED. Full data365 (shared90) PASS, no failures/errors/skips; Android test-source compilation PASS. New native90 runtime NOT_RUN. The initial low-level placement failed an existing compatibility assertion; the final guard is at all four formal Repository allocation entry points. Original low-level legacy assertions remain unchanged. See red, initial and final evidence JSON.
