# Roadmap

## Milestone 0 — repository foundation

- [x] Kotlin Android application module with Jetpack Compose.
- [x] Local memory capture, library search/filtering, and recall flow.
- [x] Baseline review scheduling with unit tests.
- [x] GitHub Actions build/unit-test workflow and debug APK artifact.
- [x] Latest Android CI run #173 passed for commit `129a7c16e78bc647b576fdc9b1bd80d00a4a5d49`.
- [ ] Obtain a successful Android emulator instrumentation run on the current commit; latest run was cancelled and a rerun is pending.

## Milestone 1 — dependable learning records

- [x] Room-backed local persistence and explicit schema migrations through version 6.
- [x] Legacy preference-data migration with malformed-data preservation and retry handling.
- [x] Persist recall attempts, modes, timestamps, correctness, latency, and free-recall responses.
- [x] Delete a memory and its associated recall attempts/checkpoints transactionally.
- [x] Parse and export TSV, including an escaped versioned format for tabs/newlines/backslashes; accept legacy TSV and preview duplicate filtering.
- [x] Persist retention checkpoints for 24-hour, 7-day, and 30-day intervals.
- [ ] Add broader automated database/repository tests for schema upgrades, migration edge cases, import/export persistence, and destructive operations.
- [x] Define a versioned full-data JSON backup format and transactional repository restore; codec and restore integration tests pass.
- [x] Add user-facing full backup export and restore selection with payload validation and explicit replacement confirmation.
- [ ] Verify the new export/restore flow on a physical Android device, including cancellation, malformed files, successful round-trip, and preservation after rejected restore.

## Milestone 2 — navigable memory palaces

- [x] Model nested palace, route, area, and locus nodes with stable IDs.
- [x] Create, rename, move, and delete palace branches; clear memory references when deleting a branch.
- [x] Link memories to palace nodes and store optional sequence order.
- [ ] Add focused integration coverage for hierarchy mutations, reference integrity, and sequence-order behavior.
- [ ] Detect duplicate or potentially confusable cues and let the user resolve them.

## Milestone 3 — retention measurement

- [x] Store 24-hour, 7-day, and 30-day retention checkpoints.
- [x] Record checkpoint completion, score, latency, recalled response, and an accuracy value.
- [x] Summarize recall-attempt accuracy and latency by recall mode.
- [ ] Verify scoring semantics and end-to-end checkpoint behavior with automated tests.
- [ ] Track and validate sequence accuracy and interference/confusion metrics; do not treat their existence as verified until code and tests establish them.
- [ ] Compare methods on comparable material and time invested.
- [ ] Demonstrate delayed-retention outcomes with measured observations before making efficacy claims.

## Milestone 4 — optimize encoding

- [x] Store chunk labels and associations and provide a local encoding-quality checklist.
- [ ] Evaluate encoding workflow speed and usability against observed user tasks.
- [ ] Test method combinations against delayed recall outcomes.
- [ ] Add optional AI-assisted organization only after core offline reliability is established.

## Acceptance principle

A feature is not complete because it looks finished. Verify data integrity, runtime behavior, and the relevant tests. Distinguish implemented metrics from validated measurements and demonstrated learning effectiveness. Record CI run links and device-test results; do not mark acceptance criteria complete without evidence.
