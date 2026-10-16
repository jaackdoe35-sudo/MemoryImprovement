# UI audit and redesign plan

## Audit scope and feature inventory

Reviewed `MainActivity.kt`, Room-backed models/repository/database, architecture and roadmap documentation, Gradle dependencies, and unit/instrumentation tests.

- **Today:** first-run guidance, primary recall/add action, learning-method explanations, practice metrics, recall analytics, delayed-retention summaries, per-memory progress, due checkpoints, sequence practice, discrimination/cue-conflict drill, backup/recovery.
- **Memory Palace:** nested palace/route/area/locus browsing; create, rename, move, delete nodes; link memories to locations.
- **Recall:** cued, free, reverse, sequence, discrimination, delayed-retention practice; reveal/check answers and record attempts, self-ratings, response latency.
- **Memory Library:** search/sort/filter due reviews; create/edit/delete memories; TSV import/export.
- **Create/edit memory:** three-step prompt/answer, topic/association, optional sequence/palace location; live completeness checklist and cue-conflict guidance.
- **Import/restore:** TSV parsing and duplicate preview; JSON backup selection, validation, replacement confirmation, restore status/errors; legacy-data recovery/export.
- **Persistence:** Room migrations and repository-backed memories, palace nodes, attempts, checkpoints; local preferences preserve malformed legacy input for recovery.

## Weaknesses observed in source

- Theme was hard-coded as light-only; Android system dark preference was ignored.
- Most typography used default Material 3 styles without a strong type scale.
- Navigation used text glyphs instead of a coherent icon system and had limited selected-state customization.
- Spacing, shape, elevation and panel emphasis varied across screens.
- Today combined onboarding, backup controls, educational content, analytics and progress in one long feed.
- Library header placed multiple actions in one row, risking crowding on narrow screens; no-results feedback was plain text.
- Some file/data operations have no shared visible loading/success/error treatment. File-picker cancellation is silent.
- No app-wide component layer standardizes action hierarchy.
- Actual emulator/device screenshot review is not available through the repository connector; clipping, touch target sizing and runtime contrast remain unverified.

## Design direction

**Editorial memory workspace:** warm paper-like surfaces, deep botanical green, restrained clay accents, crisp typographic hierarchy, calm density and generous spacing. Focused and deliberate rather than a generic dashboard.

## Design system

Centralized in `MemoryStudioTheme.kt`:
- Semantic light/dark palettes; dark mode follows Android `isSystemInDarkTheme()`.
- Type scale from 11sp labels through 32sp display headings, with normal, medium, semibold and bold weights.
- Shape radii at 8/12/18/24/30dp.
- Semantic primary, secondary, tertiary, background, surface, outline and error tokens.

This is a native Compose app, so it uses Android's sans-serif font family and platform fallbacks rather than a web font, avoiding unnecessary network/font dependencies and preserving offline use.

## Implementation order

1. Centralize theme tokens and dark-mode support without changing learning logic or persistence.
2. Improve app chrome/navigation, then Today and Library hierarchy.
3. Apply the system consistently to Palace, Recall, dialogs, analytics and empty/error/loading states.
4. Run unit tests, assemble APK, run emulator instrumentation, capture/inspect mobile and wide layouts in both themes.
5. Document remaining limitations; claim only checks that actually completed.

## Functional boundaries

Current work is presentation-focused. Room schema, migrations, recall scheduling, analytics calculations, backup formats, and data mutation rules should remain untouched unless a necessary logic change is explicitly documented.
