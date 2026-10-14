# Roadmap

## Milestone 0 — repository foundation

- [x] Kotlin Android application module with Jetpack Compose.
- [x] Local memory capture and basic recall flow.
- [x] Initial palace/locus assignment.
- [x] Transparent baseline review scheduler with unit tests.
- [x] GitHub Actions build/test workflow.
- [ ] Confirm CI build and unit tests pass on GitHub Actions.

## Milestone 1 — dependable learning records

- [ ] Move from a single serialized preference value to a database-backed repository suitable for larger collections.
- [ ] Persist recall attempts and timestamps, including retrieval latency.
- [ ] Implement free recall and reverse/flexible recall modes.
- [ ] Add editing, deletion, import/export, and safe backup.
- [ ] Add automated repository and scheduler tests.

## Milestone 2 — navigable memory palaces

- [ ] Model nested palaces, routes/areas, loci, and fact links using stable IDs.
- [ ] Provide palace overview, route view, locus view, and zoom/drill-down navigation.
- [ ] Detect duplicate or potentially confusable cues and let the user resolve them.
- [ ] Support ordered sequences and test order accuracy.

## Milestone 3 — retention measurement

- [ ] Store immediate and delayed test outcomes.
- [ ] Track accuracy, latency, order accuracy, and interference.
- [ ] Surface 24-hour, 7-day, and 30-day checkpoints.
- [ ] Compare methods on comparable material and time invested.
- [ ] Avoid declaring a method superior without enough comparable observations.

## Milestone 4 — optimize encoding

- [ ] Add chunking and association-building tools.
- [ ] Let the user place facts in a palace while encoding.
- [ ] Test method combinations against recall outcomes.
- [ ] Add optional AI-assisted organization only after the offline flow is dependable.

## Acceptance principle

A feature is not complete because it looks finished. It must preserve user data, behave correctly, and expose evidence needed to assess whether it improves reliable recall.
