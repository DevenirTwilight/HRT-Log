# MED-02 resource ownership slice

Baseline: 4e31afcd (Room13; backup business version13; PNBAK1 unchanged).

This slice supplies resource cleanup to the existing PRIMARY-only, memory-only
AttachmentSession. It does not implement file encryption, activate an importer,
change AppLock, or provide a second file/record database.

## Ownership contract

- A capability may take sole ownership of a newly created AutoCloseable. Callers
  must not register the same handle twice. A lease provides cleanup, not permission
  to read; consumers must continue to check their capability for delivery.
- Rejected registration closes the incoming resource. The original authorization
  failure remains primary; a cleanup failure is suppressed, never silently hidden.
- Session revocation invalidates authority under the existing publication monitor.
  Resource cleanup runs outside that monitor so blocked close operations cannot
  let later publications through or prevent other checks from rejecting.
- Closing a parent releases its descendants' resources without revoking independent
  readers; individual lease close leaves the capability and siblings unchanged.
- Cleanup is attempted exactly once per lease. Concurrent callers wait for that
  attempt. A failed close remains recorded and is reported again on later owner
  cleanup, rather than claiming successful cleanup or retrying an ambiguous close.
- Every selected resource is attempted even if one fails. No cleanup error can
  re-enable a revoked session or skip clearing a later registered buffer.
- ownBytes clears only the transferred array. Copies, immutable Strings, native
  decoder/page caches, displayed pixels and a borrowed consumer's private memory
  are not erased by that operation. No claim of universal zeroization is made.
- Cleanup callbacks must be bounded, release-only, and must not publish, acquire
  other leases, or wait for protected work. Future Android FD/worker/preview
  adapters must separately demonstrate their cancellation and cleanup semantics.

## Remaining production gates

The existing external-review requirement remains NOT_REVIEWED_EXTERNALLY. No
wire format, KEK/DEK role, portable backup protocol, quota policy, migration or
production feature activation is approved here. Full B2/B3 services, real AppLock
and space-switch wiring, PDF/image consumers, hostile inputs, ARM/resource tests,
SAF ownership and whole-library backup/restore remain separate work. The current
release's visible unavailable entries must continue to perform no real import.

## Evidence

RED/GREEN and full regression results are recorded with original logs and JUnit
results in this directory as they complete. Compile success, JVM contracts,
Android emulator contracts and physical-device checks are distinct. No real
health files are used.
