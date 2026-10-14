# MemoryImprovement

A native Android app for encoding large amounts of information and retrieving it reliably over time.

## Product principles

- **Encode efficiently:** structure information into chunks, connect new facts to existing knowledge, and use vivid, distinctive associations.
- **Organize for capacity:** build nested memory palaces with routes, loci, and indexed facts.
- **Retrieve on demand:** practise cued recall, free recall, reverse/flexible recall, mixed and timed recall.
- **Prove retention:** track retrieval latency, accuracy, order, interference, and delayed recall—not only immediate quiz scores.
- **Offline-first:** core study sessions and local progress must work without network access. AI-assisted features are a later, optional layer.

## First milestone

The initial Android foundation will establish a Compose UI, domain models for material and recall attempts, and a local-first study flow. It is a foundation, not a claim that long-term memory outcomes have already been validated.

## Planned learning loop

1. Import or enter material.
2. Structure it into meaningful chunks and relationships.
3. Optionally place facts in a palace, route, and locus.
4. Practise immediate retrieval in more than one direction.
5. Schedule reviews based on performance.
6. Measure delayed recall (initial targets: 24 hours, 7 days, and 30 days) and flag likely interference.
7. Compare methods using correct, durable retrieval per time invested.

## Build

Open the project in a recent Android Studio version. The GitHub Actions workflow checks the Android Gradle build on pushes and pull requests. A successful workflow run is the source of truth for build status.

## Current status

The repository is being bootstrapped. Research-backed learning principles are design hypotheses to evaluate in-app, not promises of unlimited or perfect memory.
