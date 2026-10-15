# UI audit and redesign plan

## Audit scope

Reviewed the Android app entry point and Compose screen implementation in `MainActivity.kt`, the Room-backed data/repository/model files, architecture and roadmap documentation, Gradle dependencies, and existing unit/instrumentation test sources on `redesign/beginner-first-ux`.

### Existing screens and flows

- **Today**: first-run guidance, primary recall/add action, learning-method explanations, practice metrics, recall analytics, delayed-retention summaries, per-memory progress, due retention checkpoints, sequence practice, discrimination/cue-conflict drill, and data backup/recovery controls.
- **Memory Palace**: browse nested palace/route/area/locus structure; create, rename, move, and delete nodes; link memories to locations.
- **Recall**: choose and run cued, free, reverse, sequence, discrimination, and delayed-retention practice; reveal/check answers and record attempts, self-ratings, and response latency.
- **Memory Library**: search/sort/filter due reviews; create/edit/delete memories; import/export TSV.
- **Memory creation/editing dialog**: three-step prompt/answer, topic/association, and optional sequence/palace-location flow; live completeness checklist and cue-conflict guidance.
- **Import/restore dialogs**: TSV parsing and duplicate preview; JSON backup selection, validation, replacement confirmation, restore status/errors; legacy-data recovery/export.
- **Persistence**: Room database and migrations, local preferences for legacy migration recovery, repository-backed memory/palace/attempt/checkpoint records. Core workflow is intended to remain offline-first.

## UI weaknesses observed in source

- Theme was hard-coded as a light-only color scheme in the activity, so the system dark preference was not respected.
- Typography relied largely on default Material 3 styles with few distinct hierarchy levels and inconsistent emphasis.
- Navigation used text glyphs as icons, with little deliberate treatment of selected/unselected states.
- Screen spacing and card treatment varied; many panels relied on defaults rather than a consistent surface/elevation system.
- The Today screen combined onboarding, backup tools, teaching content, analytics, and progress into one long feed without a strong distinction between primary and secondary tasks.
- Library actions shared a compact header row; this risks crowding on narrow devices. Empty-search feedback was plain text rather than a designed empty state.
- Some data operations use callbacks without a common, visible loading/success/error presentation; file-picker cancellation is silent by design.
- Existing code has no explicit, app-wide reusable component layer for consistent action hierarchy. Some dialogs and forms have helpful instructional copy and validation; preserve those behaviors.
- No emulator/device screenshot review was available through the repository connector, so visual clipping, runtime theme behavior, touch-target size, and actual device contrast cannot honestly be marked as verified.

## Design direction

**Editorial memory workspace:** warm paper-like surfaces and deep botanical green, with restrained clay accents, crisp typographic hierarchy, calm density, and generous spacing. The UI should feel like a focused learning instrument rather than a generic dashboard.

## Design system

Centralized in `MemoryStudioTheme.kt`:
- Semantic light/dark color schemes; light/dark follows Android `isSystemInDarkTheme()`.
- Typography scale spanning 11sp labels through 32sp display headings, with normal, medium, semibold, and bold weights.
- Consistent 8/12/18/24/30dp shape radii.
- Semantic primary, secondary, tertiary, background, surface, outline, and error tokens.

The app is native Compose, so Android's sans-serif font family is used with platform fallbacks instead of loading a web font. This avoids an unnecessary network/font dependency and preserves offline operation.

## Implementation order

1. Centralize theme tokens and dark-mode support without touching persistence or learning algorithms.
2. Improve app chrome and navigation, then primary Today and Library hierarchy.
3. Apply the shared system consistently to Palace, Recall, dialogs, analytics, empty/error/loading states.
4. Run unit tests, assemble the debug APK, run emulator instrumentation, then capture and inspect screenshots at mobile/desktop-equivalent widths and both themes.
5. Record limitations and only claim checks actually completed.

## Functional boundaries

The current commits change presentation only: the repository, Room schema, migration logic, recall scheduling, analytics calculations, backup formats, and data mutation rules are intentionally untouched. Any later logic change must be documented separately.
