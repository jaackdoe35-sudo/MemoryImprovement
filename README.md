# MemoryImprovement

MemoryImprovement is an offline-first Android app for organizing facts, practising active recall, and tracking memory performance over time.

The app is under development. A successful build confirms that a commit compiles; it does not prove every feature is complete or that the learning methods improve retention for every user.

## Current features

- Local memory library: create, edit, search, sort, import, and export study items.
- Encoding support: prompts, answers, topic chunks, distinctive associations, optional palace locations, and optional sequence positions.
- Live encoding checklist: a local 0–4 completeness check flags missing prompt, answer, chunk, or association details. It is a writing aid, not a validated measure of memory strength.
- Memory palaces: nested palaces, routes, areas, and loci; create, rename, move, and delete branches. Branch deletion clears location links while preserving associated memories.
- Recall practice: cued, free, reverse, sequence, discrimination, and delayed-retention modes.
- Review scheduling: review dates are updated from practice outcomes.
- Progress tracking: recall attempts, self-ratings, response latency, performance by recall mode, repeated errors, sequence practice, and lexical overlap alerts.
- Delayed-retention checkpoints: 1-day, 7-day, and 30-day follow-ups. Results distinguish fully correct, partially correct, incorrect, and not-yet-tested checkpoints.
- Legacy-data recovery: malformed legacy data is reported instead of silently discarded, with original content preserved for recovery/export.

## Offline and privacy scope

The Android manifest currently declares no permissions. The app's core study data is stored locally using Room and local preferences. No account, cloud sync, or AI service is required by the current core workflow.

This source-level description is not a substitute for a network-traffic audit or a full privacy/security review.

## Build and test

The project uses Kotlin, Jetpack Compose, Room, Gradle, and JDK 17.

GitHub Actions uses separate workflows:

- **Android CI** runs unit tests, assembles the debug APK, and uploads it as the `MemoryImprovement-debug-apk` artifact.
- **Android instrumentation tests** runs Room migration integration tests on an Android emulator.

See the [Android CI runs](https://github.com/jaackdoe35-sudo/MemoryImprovement/actions/workflows/android-ci.yml) and [instrumentation runs](https://github.com/jaackdoe35-sudo/MemoryImprovement/actions/workflows/android-instrumentation.yml).

In an Android development environment, run:

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

To run Room instrumentation tests with a configured Android emulator:

```bash
gradle connectedDebugAndroidTest
```

A successful CI build is the source of truth for whether a particular commit compiled and produced the APK. Instrumentation tests must also pass before the database recovery path should be considered validated. Download the debug APK artifact from a successful CI run to install it on a physical device.

## Limitations and remaining validation

- Emulator integration tests must pass consistently; APK assembly and instrumentation testing are reported separately.
- The app still needs a complete acceptance test on a physical Android device.
- Interference detection is a lexical-overlap heuristic. It does not identify conceptual similarity, synonyms, or causal relationships.
- The encoding checklist is a completeness check, not a scientifically validated predictor of retention.
- Memory outcomes depend on the material and study conditions. The app does not promise perfect or unlimited memory.

## Product loop

1. Enter or import material.
2. Chunk facts and add distinctive associations.
3. Optionally place facts in a palace or numbered sequence.
4. Practise retrieval in different modes.
5. Schedule reviews from practice performance.
6. Measure delayed recall and inspect recurring errors.
7. Use the evidence to adjust study effort and encoding strategy.

The project remains offline-first. Cloud sync, accounts, and AI-assisted features are intentionally deferred.
