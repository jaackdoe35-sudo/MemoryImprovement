# Architecture

## Layers

- **Compose UI**: local memory capture and editing, library browsing, recall sessions, palace navigation, TSV import/export, and progress/checkpoint views.
- **Domain/plans**: recall scheduling, recall analytics, migration decoding, import/export parsing, encoding-quality checklist, and palace hierarchy/move validation. These are transparent application rules, not evidence that a learning method is effective.
- **Local data**: Room-backed persistence through `MemoryRepository`, with schema migrations registered through version 6. The repository also imports legacy records from SharedPreferences.
- **Future optional intelligence**: content structuring and association suggestions. Core capture, storage, and recall must remain usable offline.

## Persistent records

- **Memory item**: stable ID, prompt, answer, optional palace/locus and palace-node reference, creation/review fields, chunk label, association, and optional sequence order.
- **Recall attempt**: memory ID, recall mode, correctness, score, latency, recalled response, and timestamp.
- **Palace node**: stable ID, name, type (palace, route, area, or locus), parent reference, position, and creation time.
- **Retention checkpoint**: memory ID, delay interval, due/completion timestamps, score, latency, recalled response, and accuracy value.

## Data integrity and recovery

- Keep core data on-device and usable offline.
- Use stable IDs for records and references.
- Register explicit Room migrations for schema changes; test upgrades from supported prior schemas.
- Legacy SharedPreferences migration preserves malformed raw data and records a pending retry flag. Regression tests cover malformed-data retry and importing missing legacy records without overwriting existing memories.
- Memory deletion removes its retention checkpoints and recall attempts inside a database transaction.
- Palace-branch deletion clears references from memories before deleting the branch, inside a transaction.
- TSV export is a portable data export, not a full backup of database history, review attempts, or checkpoint state. A separately verified backup/restore workflow remains outstanding.

## Import/export

- The TSV importer accepts the legacy tab-separated format and a versioned escaped format.
- Export encodes tabs, newlines, carriage returns, and backslashes for round-trip parsing.
- Import parsing checks required prompt/answer values and sequence order; duplicate filtering compares normalized prompt/answer pairs.
- Current automated unit tests cover parsing, escaped round trips, legacy TSV compatibility, and duplicate filtering. These tests do not alone prove end-to-end UI persistence or backup/restore.

## Measurement requirements and limitations

- Recall attempts record mode, correctness, score, latency, response, and timestamp; analytics summarize accuracy and mean latency by mode.
- Retention checkpoints are initialized for 24-hour, 7-day, and 30-day intervals and store completion data.
- The encoding-quality checklist rewards a specific prompt, a non-empty answer, a chunk label, and a concrete association. It is a heuristic checklist, not a validated predictor of retention.
- Immediate recall, a subjective score, or a checklist score is not proof of durable mastery. Evaluate delayed recall with comparable material, sample sizes, elapsed intervals, and time invested.
- Sequence accuracy and interference tracking require explicit implementation and test verification before being described as supported metrics.

## Verification status

- Android CI #173 passed unit tests and debug APK assembly for commit `129a7c16e78bc647b576fdc9b1bd80d00a4a5d49`.
- Android instrumentation run #8 for that commit was cancelled; a rerun was initiated. Do not report integration tests as passing until that run completes successfully.
- Repository/device acceptance, broad data-integrity regression coverage, and real-device testing remain outstanding.
