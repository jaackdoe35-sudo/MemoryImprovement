# Roadmap

## Milestone 0 — repository foundation

- [x] Kotlin Android application module with Jetpack Compose.
- [x] Local memory capture, library search/filtering, and recall flow.
- [x] Baseline review scheduling with unit tests.
- [x] GitHub Actions build/unit-test workflow and debug APK artifact.
- [x] Latest Android CI run #173 passed for commit `129a7c16e78bc647b576fdc9b1bd80d00a4a5d49`.
- [x] Android emulator instrumentation passes on commit `4377a8802e1201cd0b18f3d30ee239fc0b696de2` ([run](https://github.com/jaackdoe35-sudo/MemoryImprovement/actions/runs/38676002077)).

## Milestone 1 — dependable learning records

- [x] Room-backed local persistence and explicit schema migrations through version 6.
- [x] Legacy preference-data migration with malformed-data preservation and retry handling.
- [x] Persist recall attempts, modes, timestamps, correctness, latency, and free-recall responses.
- [x] Delete a memory and its associated recall attempts/checkpoints transactionally.
- [x] Parse and export TSV, including an escaped versioned format for tabs/newlines/backslashes; accept legacy TSV and preview duplicate filtering.
- [x] Persist retention checkpoints for 24-hour, 7-day, and 30-day intervals.
- [x] Add automated database/repository tests for schema upgrades, migration edge cases, import/export persistence, destructive operations, backup/restore integrity, and checkpoint lifecycle; latest instrumentation run passes ([run](https://github.com/jaackdoe35-sudo/MemoryImprovement/actions/runs/38676002077)).
- [x] Define a versioned full-data JSON backup format and transactional repository restore; codec and restore integration tests pass.
- [x] Add user-facing full backup export and restore selection with payload validation and explicit replacement confirmation.
- [ ] Verify export/restore on a physical Android device: cancellation, malformed files, successful round-trip, and preservation after rejected restore. CI covers automated logic but cannot replace device acceptance.

## Milestone 2 — navigable memory palaces

- [x] Model nested palace, route, area, and locus nodes with stable IDs.
- [x] Create, rename, move, and delete palace branches; clear memory references when deleting a branch.
- [x] Link memories to palace nodes and store optional sequence order.
- [x] Add integration coverage for hierarchy mutations, reference integrity, rejected-operation non-mutation, and sequence-order grouping; instrumentation passes on commit `65832f4` and later (`4377a88`).
- [x] Detect duplicate or potentially confusable cues and let the user resolve them with a documented lexical-overlap heuristic (not semantic similarity).

## Milestone 3 — retention measurement

- [x] Store 24-hour, 7-day, and 30-day retention checkpoints.
- [x] Record checkpoint completion, score, latency, recalled response, and an accuracy value.
- [x] Summarize recall-attempt accuracy and latency by recall mode.
- [x] Verify checkpoint creation, completion idempotency, invalid checkpoint rejection, attempt recording, latency normalization, and scheduler score boundaries with automated tests; see [passing instrumentation](https://github.com/jaackdoe35-sudo/MemoryImprovement/actions/runs/38676002077) and [passing Android CI](https://github.com/jaackdoe35-sudo/MemoryImprovement/actions/runs/38676002019).
- [x] Verify cue-confusion unit tests, debug APK assembly, and Android instrumentation on commit `0792725a4daf3c1fab09e04159078377bdcb8670` ([Android CI #203](https://github.com/jaackdoe35-sudo/MemoryImprovement/actions/runs/38672469081), [Instrumentation #38](https://github.com/jaackdoe35-sudo/MemoryImprovement/actions/runs/38672469080)).
- [ ] Validate the meaning and user-facing interpretation of sequence accuracy and interference/confusion metrics. Sequence grouping and lexical cue-conflict behavior have tests; actual learning significance remains unvalidated.
- [ ] Compare methods on comparable material and time invested.
- [ ] Demonstrate delayed-retention outcomes with measured observations before making efficacy claims.

## Milestone 4 — optimize encoding

- [x] Store chunk labels and associations and provide a local encoding-quality checklist.
- [ ] Evaluate encoding workflow speed and usability against observed user tasks.
- [ ] Test method combinations against delayed recall outcomes.
- [ ] Add optional AI-assisted organization only after core offline reliability is established.

## Release candidate (2026-10-15)\n\n- Debug APK built by successful Android CI run [#38676002019](https://github.com/jaackdoe35-sudo/MemoryImprovement/actions/runs/38676002019) from source commit `4377a8802e1201cd0b18f3d30ee239fc0b696de2`.\n- Instrumentation passed on the same source commit in [run #38676002077](https://github.com/jaackdoe35-sudo/MemoryImprovement/actions/runs/38676002077).\n- APK artifact: `MemoryImprovement-debug-apk`; SHA-256 of extracted `app-debug.apk`: `9cc6b918b87d8263f6059146e0afdbed7b66f1cb27e5af9d866d85d50cb11632`.\n- Release status: CI-built debug candidate available; physical-device acceptance and learning-effectiveness evaluation remain open.\n\n## Acceptance principle

A feature is not complete because it looks finished. Verify data integrity, runtime behavior, and the relevant tests. Distinguish implemented metrics from validated measurements and demonstrated learning effectiveness. Record CI run links and device-test results; do not mark acceptance criteria complete without evidence.
