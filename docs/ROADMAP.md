# Roadmap

## Milestone 0 — repository foundation

- [x] Kotlin Android application module with Jetpack Compose.
- [x] Local memory capture and basic recall flow.
- [x] Initial palace/locus assignment.
- [x] Transparent baseline review scheduler with unit tests.
- [x] GitHub Actions build/test workflow.
- [x] Confirm CI build and unit tests pass on GitHub Actions (latest verified run: #21).

## Milestone 1 — dependable learning records

- [x] Move from a single serialized preference value to a database-backed repository suitable for larger collections.
- [x] Persist recall attempts and timestamps, including retrieval latency; free-recall response capture is now stored as well.
- [x] Implement initial cued, free, and reverse recall modes; manual ratings remain subjective.
- [ ] Add editing, deletion, import/export, and safe backup.
- [ ] Add automated repository and scheduler tests (checkpoint schedule unit tests added; repository/migration tests still needed).

## Milestone 2 — navigable memory palaces (in progress)

- [x] Model nested palaces, routes/areas, loci, and fact links using stable IDs.
- [x] Provide a basic indented hierarchy view and create-child flow.
- [x] Allow memory items to link to a stable locus ID.
- [ ] Add focused route/locus drill-down, editing, and safe node deletion.
- [ ] Detect duplicate or potentially confusable cues and let the user resolve them.
- [ ] Support ordered sequences and test order accuracy.

## Milestone 3 — retention measurement

- [x] Define and persist 24-hour, 7-day, and 30-day retention checkpoints; initialize missing checkpoints for existing memories.
- [ ] Track accuracy, latency, order accuracy, and interference; free-recall correctness is not yet independently scored.
- [ ] Surface and run 24-hour, 7-day, and 30-day checkpoints in the UI, then display retention trends.
- [ ] Compare methods on comparable material and time invested.
- [ ] Avoid declaring a method superior without enough comparable observations.

## Milestone 4 — optimize encoding

- [ ] Add chunking and association-building tools.
- [ ] Let the user place facts in a palace while encoding.
- [ ] Test method combinations against recall outcomes.
- [ ] Add optional AI-assisted organization only after the offline flow is dependable.

## Acceptance principle

A feature is not complete because it looks finished. It must preserve user data, behave correctly, and expose evidence needed to assess whether it improves reliable recall.
