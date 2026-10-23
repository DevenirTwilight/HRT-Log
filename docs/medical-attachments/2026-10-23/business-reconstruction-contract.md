# Isolated business reconstruction (OFF)

Baseline `0a95293d4743656d90464827893a7b39aaedcf2f` (#112). Authorization §147.B.

`AttachmentBusinessRestore.reconstruct` is internal production code, without an application entry, public destination database handle/path, archive parser or activation method. It consumes owned bounded structured bytes and an already admitted `AttachmentRestorePreflight.Plan`. Hash/length must match the admitted bytes. That binds this reconstruction to the plan; it does **not** authenticate a portable archive. A future approved independent outer reader must do that separately.

It creates a fresh, private **in-memory SQLCipher** Room14 database with a random transient key, never the current user's key. TEMP storage is forced to MEMORY. New namespace is exactly the preflight's fresh dataset/epoch/generation; no current namespace, epoch or generation is replaced. No persistent plaintext file, candidate SQL file, Room migration, device alias or key wrap is created.

## Exact graph, original oracle, explicit identity

The complete source business graph keeps its integer primary/foreign keys in this new dataset. UUID/type mappings must individually and explicitly name those new-dataset rows; integer equality across datasets never establishes identity. A requested integer renumbering is refused before any provider opens. Arbitrary renumbering of business hash chains, frozen snapshots and trash payloads would require a separately proven graph transformation; this component does not invent one. It is not a merge/import into populated tables.

`RawData.reconstructIsolated` requires a transaction, memory-only attached databases, MEMORY temporary storage, exactly the known business tables, and empty destination business tables. It reuses existing validation, trusted insertion dependencies and append-only restore-guard handling. It then executes the **original full SQL/history audit**, with both audit reuse budgets zero. No derived-value repairs, trash backfill, clinical normalization, new trust cache or weakened audit is introduced. The old PNBAK1 restore methods are unchanged.

`AttachmentCandidateMetadata` independently compares every business column and full row multiplicity with the source after SQL insertion. Numeric scalar equality is exact decimal equality; text/null remain distinct. Trusted full-snapshot columns are mandatory. It rejects same-count substitutions; counts alone never establish equality. Its bounded local comparison notation is not the proposed portable canonical codec.

All attachment documents, immutable versions (including original content contexts and both digests/lengths), revisions, timestamps, trash states, typed identities, link states and revision evidence are inserted into **native TEMP relations with deferred native foreign keys**. Read-back manifest must equal the entire source manifest except the explicitly new dataset/epoch. Main and TEMP FK checks must pass. Active targets and retained soft-deleted doses must exist at the mapped typed row. LAB/APPOINTMENT trash remains the exact payload already checked by preflight and preserved by full-row equality. PURGED has no target integer and cannot resurrect a source row. Historical QUARANTINED/RETRACTED links stay in that state.

TEMP metadata deliberately does not populate production attachment tables: current VERIFIED requires local durable journal/key/ownership proof, which a source portable manifest cannot supply. It would be unsafe to disable these guards or fabricate proof. Candidate attachment_store stays empty; TEMP feature_enabled is constrained to zero.

## Publication, cancellation and process death

Only after successful business reconstruction does the unchanged `AttachmentRestoreStaging` authenticate and quarantine **every** required retained inner ciphertext. Missing/changed membership, short/extra/corrupt data, wrong keys or cancellation refuses the whole candidate. No partially constructed candidate escapes. Existing owned streams/DEKs are closed/wiped. Failed quarantine leaves remain retained/charged UNKNOWN, without guessed cleanup.

The resulting closable candidate exposes only guarded read-back metadata and borrowed bounded business bytes; it owns its RAM database under the existing revocable session. Byte callbacks run outside the publication monitor and their output buffers are wiped on return/revocation. Current scope is checked before/after operations; lock/cancel/switch/close withholds further output. No SQL handle escapes. Process death loses the RAM candidate and all capabilities; existing quarantine's no-adoption restart policy applies. This is not a durable restore resume/activation journal.

`activatable=false`; object receipt `wholeArchiveAuthenticated=false`. Success means an isolated graph and its inner objects were reconstructed/checked, **never** that a user library was restored, that cross-phone recovery works, or that full-library activation is approved.

## Evidence and limits

Shared JVM/native contracts use genuine Repository correction/deletion and strict full snapshots. They test exact clinical/history/trash equality, same-count fact mutation, cancellation rollback, identity/target refusal and populated-target refusal. Native end-to-end tests create real synthetic PDF/PNG/binary originals, immutable PDF history, document trash, active/trashed/purged clinical identities and typed links, then use production capture/preflight/reconstruction/held-FD/Tink staging and independently compare exported business bytes and all metadata. The transport obtaining synthetic source DEKs is androidTest only, **not** a production portable exporter.

Full-row equality preserves evidence and original timestamps; the old full oracle proves only its existing supported invariants, not truth of clinical statements or authenticity absent the future outer reader. Unsupported source business versions, partial historical rows and arbitrary ID rewrites fail closed. At most existing bounded JSON/row/object limits apply; no claim of whole-archive low-memory acceptance. Source, candidate, KDF, parser/JSON copies and comparison memory still require a measured joint budget before formal format/activation approval. Persistent restart of a complete candidate and different-device portable key wrapping are not implemented.
