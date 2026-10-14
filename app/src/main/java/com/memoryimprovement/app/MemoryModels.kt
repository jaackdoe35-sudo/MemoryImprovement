package com.memoryimprovement.app

/** Search and due filtering for the library; ordering prioritizes due reviews. */
object MemoryLibraryPlan {
    fun filterAndSort(
        memories: List<MemoryItem>,
        query: String,
        dueOnly: Boolean,
        now: Long,
    ): List<MemoryItem> {
        val normalizedQuery = query.trim()
        return memories.asSequence()
            .filter { !dueOnly || it.nextReviewAt <= now }
            .filter { item ->
                normalizedQuery.isEmpty() || listOf(
                    item.prompt, item.answer, item.palace, item.locus, item.chunkLabel, item.association,
                ).any { it.contains(normalizedQuery, ignoreCase = true) }
            }
            .sortedWith(compareBy<MemoryItem> { it.nextReviewAt }.thenByDescending { it.createdAt })
            .toList()
    }
}

data class MemoryImportRow(
    val prompt: String,
    val answer: String,
    val palace: String = "",
    val locus: String = "",
    val chunkLabel: String = "",
    val association: String = "",
    val sequenceOrder: Int? = null,
)

object MemoryImportPlan {
    /** Parses tab-separated rows: prompt, answer, palace, locus, chunk, association, sequence order. */
    fun parseTsv(text: String): List<MemoryImportRow> {
        val rows = text.lineSequence().mapIndexedNotNull { index, raw ->
            if (raw.isBlank()) return@mapIndexedNotNull null
            val columns = raw.split('\t')
            require(columns.size in 2..7) { "Line ${index + 1}: expected 2–7 tab-separated columns." }
            val prompt = columns[0].trim()
            val answer = columns[1].trim()
            require(prompt.isNotEmpty() && answer.isNotEmpty()) {
                "Line ${index + 1}: prompt and answer are required."
            }
            val orderText = columns.getOrNull(6)?.trim().orEmpty()
            val order = if (orderText.isEmpty()) null else orderText.toIntOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("Line ${index + 1}: sequence order must be a positive integer.")
            MemoryImportRow(
                prompt = prompt,
                answer = answer,
                palace = columns.getOrNull(2)?.trim().orEmpty(),
                locus = columns.getOrNull(3)?.trim().orEmpty(),
                chunkLabel = columns.getOrNull(4)?.trim().orEmpty(),
                association = columns.getOrNull(5)?.trim().orEmpty(),
                sequenceOrder = order,
            )
        }.toList()
        require(rows.isNotEmpty()) { "Paste at least one valid row." }
        return rows
    }

    /** Skips repeated prompt+answer pairs, including duplicates within the import batch. */
    fun excludeDuplicates(imported: List<MemoryImportRow>, existing: List<MemoryItem>): MemoryImportBatch {
        fun key(prompt: String, answer: String) = prompt.trim().lowercase() to answer.trim().lowercase()
        val seen = existing.mapTo(mutableSetOf()) { key(it.prompt, it.answer) }
        val unique = imported.filter { seen.add(key(it.prompt, it.answer)) }
        return MemoryImportBatch(unique, imported.size - unique.size)
    }
}

data class MemoryImportBatch(val rows: List<MemoryImportRow>, val duplicatesSkipped: Int)

object MemoryExportPlan {
    /** Exports memory content as seven-column TSV, compatible with MemoryImportPlan. */
    fun toTsv(memories: List<MemoryItem>): String {
        fun cell(value: String) = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
        return memories.joinToString("\n") { item ->
            listOf(
                item.prompt, item.answer, item.palace, item.locus, item.chunkLabel, item.association,
                item.sequenceOrder?.toString().orEmpty(),
            ).joinToString("\t", transform = ::cell)
        }
    }
}

data class MemoryItem(
    val id: String,
    val prompt: String,
    val answer: String,
    val palace: String = "",
    val locus: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val nextReviewAt: Long = createdAt,
    val lastScore: Int? = null,
    val reviewCount: Int = 0,
    val palaceNodeId: String? = null,
    val chunkLabel: String = "",
    val association: String = "",
    val sequenceOrder: Int? = null,
)

enum class PalaceNodeKind(val label: String) {
    Palace("Palace"), Route("Route"), Area("Area"), Locus("Locus")
}

data class PalaceNode(
    val id: String,
    val name: String,
    val kind: PalaceNodeKind,
    val parentId: String?,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class RecallMode { Cued, Free, Reverse, Sequence, Discrimination, Retention }

data class RecallSequence(val label: String, val items: List<MemoryItem>)

data class InterferencePair(
    val first: MemoryItem,
    val second: MemoryItem,
    val sharedTerms: List<String>,
    val overlap: Double,
)

/** Lexical heuristic only; this does not detect synonym or conceptual similarity. */
object MemoryInterferencePlan {
    private val ignored = setOf("about", "after", "also", "been", "being", "between", "could", "does", "each", "from", "have", "into", "more", "most", "only", "other", "over", "same", "some", "such", "than", "that", "their", "them", "then", "there", "these", "they", "this", "those", "through", "under", "very", "what", "when", "where", "which", "while", "with", "would", "your", "will", "must", "should", "using", "used")

    private fun terms(item: MemoryItem): Set<String> =
        (item.prompt + " " + item.answer).lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 && it !in ignored }
            .toSet()

    fun find(memories: List<MemoryItem>, limit: Int = 8): List<InterferencePair> {
        val candidates = memories.map { it to terms(it) }.filter { it.second.size >= 2 }
        val pairs = mutableListOf<InterferencePair>()
        for (i in candidates.indices) {
            for (j in i + 1 until candidates.size) {
                val (first, a) = candidates[i]
                val (second, b) = candidates[j]
                val shared = a.intersect(b).sorted()
                val overlap = shared.size.toDouble() / minOf(a.size, b.size).coerceAtLeast(1)
                if (shared.size >= 2 && overlap >= 0.5) {
                    pairs += InterferencePair(first, second, shared, overlap)
                }
            }
        }
        return pairs.sortedWith(compareByDescending<InterferencePair> { it.overlap }
            .thenByDescending { it.sharedTerms.size }
            .thenBy { it.first.prompt.lowercase() })
            .take(limit)
    }
}

object RecallSequencePlan {
    /** Groups only explicitly numbered items; each group must contain at least two distinct facts. */
    fun groups(memories: List<MemoryItem>): List<RecallSequence> = memories
        .filter { it.chunkLabel.isNotBlank() && (it.sequenceOrder ?: 0) > 0 }
        .groupBy { it.chunkLabel.trim().lowercase() }
        .mapNotNull { (key, items) ->
            val ordered = items.sortedWith(compareBy<MemoryItem> { it.sequenceOrder }.thenBy { it.createdAt }.thenBy { it.id })
            if (ordered.size < 2) null else RecallSequence(
                label = ordered.first().chunkLabel.trim().ifBlank { key },
                items = ordered,
            )
        }
        .sortedBy { it.label.lowercase() }
}
/** Pure summary model for longitudinal recall history; correctness reflects saved attempt labels. */
data class RecallModeSummary(
    val mode: RecallMode,
    val attempts: Int,
    val correct: Int,
    val accuracyPercent: Int,
    val meanLatencyMs: Long,
)

object RecallAnalytics {
    fun byMode(attempts: List<RecallAttempt>): List<RecallModeSummary> =
        attempts.groupBy { it.mode }.map { (mode, values) ->
            val correct = values.count { it.correct }
            RecallModeSummary(
                mode = mode,
                attempts = values.size,
                correct = correct,
                accuracyPercent = correct * 100 / values.size,
                meanLatencyMs = values.map { it.latencyMs }.average().toLong(),
            )
        }.sortedBy { it.mode.ordinal }
}

data class RepeatedErrorSummary(
    val item: MemoryItem,
    val incorrectAttempts: Int,
    val mostRecentFailureAt: Long,
    val lastIncorrectResponse: String?,
)

object RepeatedErrorPlan {
    /** Surfaces only items with at least two attempts marked incorrect; does not infer semantic error types. */
    fun find(
        memories: List<MemoryItem>,
        attempts: List<RecallAttempt>,
        limit: Int = 5,
    ): List<RepeatedErrorSummary> {
        val byId = memories.associateBy { it.id }
        return attempts.filterNot { it.correct }
            .groupBy { it.itemId }
            .mapNotNull { (itemId, failures) ->
                val item = byId[itemId] ?: return@mapNotNull null
                if (failures.size < 2) return@mapNotNull null
                val latest = failures.maxByOrNull { it.reviewedAt } ?: return@mapNotNull null
                RepeatedErrorSummary(item, failures.size, latest.reviewedAt, latest.recalledResponse)
            }
            .sortedWith(compareByDescending<RepeatedErrorSummary> { it.incorrectAttempts }
                .thenByDescending { it.mostRecentFailureAt })
            .take(limit)
    }
}


data class MemoryProgressSummary(
    val item: MemoryItem,
    val attemptCount: Int,
    val correctCount: Int,
    val firstCorrect: Boolean,
    val latestCorrect: Boolean,
    val meanLatencyMs: Long,
)

object MemoryProgressPlan {
    /** Summarizes recorded attempt labels only; two or more attempts are required to compare first vs latest. */
    fun summarize(memories: List<MemoryItem>, attempts: List<RecallAttempt>, limit: Int = 10): List<MemoryProgressSummary> {
        val byId = memories.associateBy { it.id }
        return attempts.groupBy { it.itemId }
            .mapNotNull { (itemId, history) ->
                val item = byId[itemId] ?: return@mapNotNull null
                if (history.size < 2) return@mapNotNull null
                val ordered = history.sortedBy { it.reviewedAt }
                MemoryProgressSummary(
                    item = item,
                    attemptCount = ordered.size,
                    correctCount = ordered.count { it.correct },
                    firstCorrect = ordered.first().correct,
                    latestCorrect = ordered.last().correct,
                    meanLatencyMs = ordered.map { it.latencyMs }.average().toLong(),
                )
            }
            .sortedWith(compareByDescending<MemoryProgressSummary> { it.latestCorrect && !it.firstCorrect }
                .thenByDescending { it.correctCount.toDouble() / it.attemptCount }
                .thenByDescending { it.attemptCount })
            .take(limit)
    }
}





data class RecallAttempt(
    val itemId: String,
    val mode: RecallMode,
    val correct: Boolean,
    val score: Int,
    val latencyMs: Long,
    val recalledResponse: String? = null,
    val reviewedAt: Long = System.currentTimeMillis(),
)



data class RecallOutcome(val nextReviewAt: Long, val intervalDays: Int, val score: Int)

data class RetentionCheckpoint(
    val id: String,
    val itemId: String,
    val delayDays: Int,
    val dueAt: Long,
    val completedAt: Long? = null,
    val score: Int? = null,
    val latencyMs: Long? = null,
    val recalledResponse: String? = null,
    /** User-checked answer accuracy: 0 incorrect, 1 partial, 2 correct; null for legacy results. */
    val accuracy: Int? = null,
)

object RetentionCheckpointPlan {
    val delaysDays = listOf(1, 7, 30)

    fun dueTimes(itemId: String, encodedAt: Long): List<RetentionCheckpoint> =
        delaysDays.map { days ->
            RetentionCheckpoint(
                id = "$itemId-$days",
                itemId = itemId,
                delayDays = days,
                dueAt = encodedAt + days * 24L * 60L * 60L * 1000L,
            )
        }
}

data class RetentionDelaySummary(
    val delayDays: Int,
    val total: Int,
    val completed: Int,
    val meanSelfRating: Double?,
    val checkedAccuracyPercent: Int?,
    val checkedAccuracyCount: Int,
    val meanLatencyMs: Long?,
)

object RetentionAnalytics {
    fun byDelay(
        checkpoints: List<RetentionCheckpoint>,
        delays: List<Int> = RetentionCheckpointPlan.delaysDays,
    ): List<RetentionDelaySummary> = delays.map { delay ->
        val group = checkpoints.filter { it.delayDays == delay }
        val completed = group.filter { it.completedAt != null }
        val scores = completed.mapNotNull { it.score }
        val checked = completed.mapNotNull { it.accuracy }
        val latencies = completed.mapNotNull { it.latencyMs }
        RetentionDelaySummary(
            delayDays = delay,
            total = group.size,
            completed = completed.size,
            meanSelfRating = scores.takeIf { it.isNotEmpty() }?.average(),
            checkedAccuracyPercent = checked.takeIf { it.isNotEmpty() }
                ?.let { it.count { accuracy -> accuracy == 2 } * 100 / it.size },
            checkedAccuracyCount = checked.size,
            meanLatencyMs = latencies.takeIf { it.isNotEmpty() }?.average()?.toLong(),
        )
    }
}


/** Transparent baseline; evaluate against measured delayed recall before claiming it is optimal. */
data class WeakMemoryPriority(val item: MemoryItem, val priority: Int, val reason: String)

object WeakMemoryPlan {
    /** Transparent baseline, not an ML prediction. Repeated incorrect labels add a capped priority bonus. */
    fun rank(memories: List<MemoryItem>, now: Long = System.currentTimeMillis(), limit: Int = 5): List<WeakMemoryPriority> =
        rank(memories, emptyList(), now, limit)

    fun rank(
        memories: List<MemoryItem>,
        attempts: List<RecallAttempt>,
        now: Long = System.currentTimeMillis(),
        limit: Int = 5,
    ): List<WeakMemoryPriority> {
        val failuresByItem = attempts.filterNot { it.correct }.groupingBy { it.itemId }.eachCount()
        return memories.map { item ->
            val lowScore = item.lastScore?.let { (5 - it.coerceIn(0, 5)) * 3 } ?: 0
            val overdueDays = ((now - item.nextReviewAt).coerceAtLeast(0L) / (24L * 60L * 60L * 1000L)).coerceAtMost(30L).toInt()
            val untested = if (item.reviewCount == 0) 2 else 0
            val failures = failuresByItem[item.id] ?: 0
            val repeatedFailureBonus = (failures * 2).coerceAtMost(10)
            val priority = lowScore + overdueDays + untested + repeatedFailureBonus
            val reason = when {
                failures >= 2 -> "Repeated incorrect attempts ($failures)"
                item.lastScore != null && item.lastScore <= 2 -> "Low last self-rating"
                overdueDays > 0 -> "Overdue by $overdueDays day(s)"
                item.reviewCount == 0 -> "Not yet practised"
                else -> "Routine review"
            }
            WeakMemoryPriority(item, priority, reason)
        }
            .sortedWith(compareByDescending<WeakMemoryPriority> { it.priority }.thenBy { it.item.nextReviewAt }.thenBy { it.item.prompt.lowercase() })
            .take(limit)
    }
}

object RecallScheduler {
    fun schedule(previousReviews: Int, score: Int, now: Long = System.currentTimeMillis()): RecallOutcome {
        val normalized = score.coerceIn(0, 5)
        val interval = when {
            normalized <= 1 -> 1
            normalized == 2 -> 2
            normalized == 3 -> listOf(1, 3, 7, 14)[previousReviews.coerceIn(0, 3)]
            else -> listOf(2, 5, 10, 21, 30)[previousReviews.coerceIn(0, 4)]
        }
        return RecallOutcome(now + interval * 24L * 60L * 60L * 1000L, interval, normalized)
    }
}

/** Applies a delayed test's effort rating to the next review date; accuracy remains separately recorded. */
object RetentionReviewPlan {
    fun apply(item: MemoryItem, effortScore: Int, completedAt: Long): MemoryItem {
        val outcome = RecallScheduler.schedule(item.reviewCount, effortScore, completedAt)
        return item.copy(
            nextReviewAt = outcome.nextReviewAt,
            lastScore = outcome.score,
            reviewCount = item.reviewCount + 1,
        )
    }
}
