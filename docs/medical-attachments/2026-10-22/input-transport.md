# Bounded attachment input transport

Baseline e4ebab2e (#102 merged). This is a source-stream ownership and byte
accounting adapter, not an importer, parser, encryption format or save receipt.
No production caller or UI is activated by this slice.

- `AttachmentInputStream.open(parent, source, maximum)` transfers sole source
  ownership even on rejection. It forks one reader capability; closing that
  reader does not cancel the parent or its other readers.
- The caller must supply a positive policy limit smaller than Long.MAX_VALUE.
  No provider size/URI/name/MIME is used as authority and no product quota is
  frozen here. At most maximum+1 bytes cross the supplied stream boundary.
- A request can read at most the remaining budget plus one byte. The probe is
  never returned as successful content. Even a large first read fails closed
  if it exceeds the limit; bytesRead only counts accepted bytes.
- Limits apply at this InputStream boundary, not to a provider's private buffers.
  Consumers must stream; this adapter cannot make readBytes() or a decoder's
  allocations bounded. It does not authenticate a signature, MIME or file.
- Every read checks authority before source IO and at the accounting/delivery
  linearization point. IO is outside the authorization monitor. Late/rejected
  data and provider-throw partial reads are cleared in the requested range.
  Previously accepted copies remain the consumer's responsibility.
- Nine consecutive zero-result reads reject a stalled source, including the
  single-byte API. Invalid provider counts reject rather than forge EOF/data.
  Zero-length caller reads do not call the provider. Backward reset is unsupported.
- The original provider exception remains primary; cleanup errors are suppressed.
  Failed reads close the reader and source, without cancelling sibling readers.
  Close does not wait for the read monitor; cooperative sources may unblock.
  Arbitrary blocking providers still require separately approved worker/deadline
  isolation. No universal close-latency or native-memory erasure claim is made.
- EOF and bytesRead are transport facts only. A future writer still needs the
  complete encryption/authentication, original identity checks, durable file and
  directory ownership, atomic metadata commit and valid session publication.

Tests include malformed/stalled/partial-error sources, cancellation during read,
explicit range and Long limits, independent byte-count oracle over size/chunk
combinations, real ParcelFileDescriptor pipes and real Android reliable-pipe
closeWithError. Original POC/oracle and default-disabled assertions remain.
Native, JVM and physical-device results are recorded separately.
