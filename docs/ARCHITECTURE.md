# Architecture

## Layers

- **Compose UI**: capture, palace browsing, recall sessions, and progress views.
- **Domain**: memory items, recall attempts, and review scheduling. Scheduling is a transparent baseline and must be evaluated against measured retention.
- **Local data**: persistent device-local storage. The current bootstrap uses a compact local store; move to a database-backed repository before treating it as suitable for very large collections.
- **Future optional intelligence**: content structuring and association suggestions. Core capture and recall must remain usable offline.

## Core entities

- **Memory item**: prompt, answer, optional palace and locus, creation time, next review, last score, review count.
- **Recall attempt**: item, recall mode, correctness, retrieval latency, timestamp.
- **Palace hierarchy (planned)**: palace → route/area → locus → linked memory items. Support nesting and stable IDs instead of relying only on display names.

## Measurement requirements

A session should distinguish:
- correctness and partial correctness;
- retrieval latency;
- recall mode (cued, free, reverse/flexible, mixed/timed);
- order accuracy where order matters;
- interference or confusion with similar items;
- delayed retention at configurable intervals, with initial checkpoints at 24 hours, 7 days, and 30 days.

Do not equate an immediate correct answer with durable mastery. Progress metrics should show sample size and testing interval.

## Data integrity

- Keep data local by default.
- Use stable IDs for records and references.
- Add schema migrations before changing persistent data formats.
- Do not introduce account, analytics, or network requirements into the core learning loop.
- Provide export/backup before adding destructive bulk operations.
