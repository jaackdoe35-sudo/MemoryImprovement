package com.memoryimprovement.app

import org.json.JSONArray
import org.json.JSONObject

/** Result of attempting to decode legacy preference data; failures retain the original raw value. */
data class LegacyMigrationResult(
    val items: List<MemoryItem>,
    val errorMessage: String? = null,
    val preservedRawData: String? = null,
)

object LegacyMigrationPlan {
    fun decode(raw: String, decoder: (String) -> List<MemoryItem>): LegacyMigrationResult =
        runCatching { decoder(raw) }
            .fold(
                onSuccess = { LegacyMigrationResult(items = it) },
                onFailure = {
                    LegacyMigrationResult(
                        items = emptyList(),
                        errorMessage = "Legacy memory data could not be imported: ${it.message ?: "invalid data"}. The original data has been preserved.",
                        preservedRawData = raw,
                    )
                },
            )
}

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
    private const val ESCAPED_TSV_MARKER = "#MemoryImprovement-TSV-ESCAPED-1"

    /** Parses legacy TSV as before, or the versioned escaped format emitted by MemoryExportPlan. */
    fun parseTsv(text: String): List<MemoryImportRow> {
        val escapedFormat = text.lineSequence().firstOrNull() == ESCAPED_TSV_MARKER
        val content = if (escapedFormat) text.substringAfter('\n', missingDelimiterValue = "") else text
        val rows = content.lineSequence().mapIndexedNotNull { index, raw ->
            if (raw.isBlank()) return@mapIndexedNotNull null
            val columns = raw.split('\t')
            val displayLine = index + if (escapedFormat) 2 else 1
            require(columns.size in 2..7) { "Line " + displayLine + ": expected 2–7 tab-separated columns." }
            fun decode(value: String): String {
                if (!escapedFormat) return value.trim()
                val out = StringBuilder()
                var i = 0
                while (i < value.length) {
                    if (value[i] == '\\' && i + 1 < value.length) {
                        when (value[i + 1]) {
                            '\\' -> { out.append('\\'); i += 2 }
                            't' -> { out.append('\t'); i += 2 }
                            'n' -> { out.append('\n'); i += 2 }
                            'r' -> { out.append('\r'); i += 2 }
                            else -> { out.append('\\'); i++ }
                        }
                    } else {
                        out.append(value[i])
                        i++
                    }
                }
                return out.toString()
            }
            val prompt = decode(columns[0])
            val answer = decode(columns[1])
            require(prompt.isNotBlank() && answer.isNotBlank()) {
                "Line " + displayLine + ": prompt and answer are required."
            }
            val orderText = decode(columns.getOrNull(6).orEmpty())
            val order = if (orderText.isEmpty()) null else orderText.toIntOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("Line " + displayLine + ": sequence order must be a positive integer.")
            MemoryImportRow(
                prompt = prompt,
                answer = answer,
                palace = decode(columns.getOrNull(2).orEmpty()),
                locus = decode(columns.getOrNull(3).orEmpty()),
                chunkLabel = decode(columns.getOrNull(4).orEmpty()),
                association = decode(columns.getOrNull(5).orEmpty()),
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
    private const val ESCAPED_TSV_MARKER = "#MemoryImprovement-TSV-ESCAPED-1"

    /** Exports lossless, versioned TSV; the importer continues to accept legacy TSV unchanged. */
    fun toTsv(memories: List<MemoryItem>): String {
        if (memories.isEmpty()) return ""
        fun cell(value: String) = buildString(value.length) {
            value.forEach { ch ->
                append(
                    when (ch) {
                        '\\' -> "\\\\"
                        '\t' -> "\\t"
                        '\n' -> "\\n"
                        '\r' -> "\\r"
                        else -> ch.toString()
                    }
                )
            }
        }
        val rows = memories.joinToString("\n") { item ->
            listOf(
                item.prompt, item.answer, item.palace, item.locus, item.chunkLabel, item.association,
                item.sequenceOrder?.toString().orEmpty(),
            ).joinToString("\t", transform = ::cell)
        }
        return "$ESCAPED_TSV_MARKER\n$rows"
    }
}

data class EncodingQualityCheck(
    val score: Int,
    val maxScore: Int = 4,
    val suggestions: List<String>,
)

/**
 * A transparent local checklist for encoding completeness, not a validated predictor of memory performance.
 * It rewards a clear prompt/answer, a named chunk, and a concrete association.
 */
object EncodingQualityPlan {
    fun evaluate(prompt: String, answer: String, chunkLabel: String, association: String): EncodingQualityCheck {
        val suggestions = mutableListOf<String>()
        var score = 0
        if (prompt.trim().length >= 4) score++ else suggestions += "Make the prompt a specific question or cue."
        if (answer.trim().length >= 2) score++ else suggestions += "Add a clear answer in your own words."
        if (chunkLabel.trim().isNotEmpty()) score++ else suggestions += "Group this fact under a short topic or chunk."
        if (association.trim().length >= 20) score++ else suggestions += "Add a distinctive image or action association (about 20 characters or more)."
        return EncodingQualityCheck(score = score, suggestions = suggestions)
    }
}

data class Document(
    val id: String,
    val title: String,
    val fileUri: String,
    val fileType: String,
    val content: String,
    val addedAt: Long = System.currentTimeMillis(),
    val processed: Boolean = false,
)

data class MemoryChunk(
    val id: String,
    val documentId: String?,
    val title: String,
    val content: String,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

data class RecallPrompt(
    val id: String,
    val chunkId: String?,
    val prompt: String,
    val answer: String,
    val type: String = "CUED",
    val createdAt: Long = System.currentTimeMillis(),
)

data class Association(
    val id: String,
    val promptId: String?,
    val chunkId: String?,
    val visualCue: String,
    val actionCue: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class MemoryCueConflict(
    val existing: MemoryItem,
    val similarity: Double,
    val exactMatch: Boolean,
)

/**
 * Flags identical normalized prompts and high lexical overlap. This is a cue-review heuristic,
 * not semantic similarity detection; it deliberately avoids guessing at synonyms or meaning.
 */
object MemoryCueConfusionPlan {
    private val ignored = setOf("what", "when", "where", "which", "who", "whom", "whose", "how", "does", "did", "are", "was", "were", "the", "and", "for", "from", "with", "that", "this", "into", "your")

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    private fun terms(value: String): Set<String> =
        normalize(value).split(" ").filter { it.length >= 3 && it !in ignored }.toSet()

    fun find(candidate: MemoryItem, existing: List<MemoryItem>, limit: Int = 5): List<MemoryCueConflict> {
        val candidateCue = normalize(candidate.prompt)
        if (candidateCue.isBlank()) return emptyList()
        val candidateTerms = terms(candidate.prompt)
        return existing.asSequence()
            .filter { it.id != candidate.id }
            .mapNotNull { item ->
                val existingCue = normalize(item.prompt)
                if (existingCue.isBlank()) return@mapNotNull null
                val exact = candidateCue == existingCue
                val otherTerms = terms(item.prompt)
                val similarity = if (candidateTerms.isEmpty() || otherTerms.isEmpty()) {
                    if (exact) 1.0 else 0.0
                } else {
                    candidateTerms.intersect(otherTerms).size.toDouble() /
                        candidateTerms.union(otherTerms).size.toDouble()
                }
                if (exact || (candidateTerms.size >= 2 && otherTerms.size >= 2 && similarity >= 0.7)) {
                    MemoryCueConflict(item, similarity, exact)
                } else null
            }
            .sortedWith(compareByDescending<MemoryCueConflict> { it.exactMatch }
                .thenByDescending { it.similarity }
                .thenBy { it.existing.prompt.lowercase() })
            .take(limit.coerceAtLeast(0))
            .toList()
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

/** Returns a node and all descendants, protecting memory references before a branch is deleted. */
object PalaceHierarchyPlan {
    fun descendantIds(nodes: List<PalaceNode>, rootId: String): Set<String> {
        if (nodes.none { it.id == rootId }) return emptySet()
        val childrenByParent = nodes.groupBy { it.parentId }
        val result = linkedSetOf<String>()
        val pending = ArrayDeque<String>()
        pending.add(rootId)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (result.add(current)) {
                childrenByParent[current].orEmpty().forEach { pending.add(it.id) }
            }
        }
        return result
    }
}

object PalaceMovePlan {
    fun isValidParent(nodes: List<PalaceNode>, nodeId: String, newParentId: String?): Boolean {
        val node = nodes.firstOrNull { it.id == nodeId } ?: return false
        if (newParentId == nodeId) return false
        val descendants = PalaceHierarchyPlan.descendantIds(nodes, nodeId)
        if (newParentId != null && newParentId in descendants) return false
        val parent = newParentId?.let { id -> nodes.firstOrNull { it.id == id } } ?: return newParentId == null && node.kind == PalaceNodeKind.Palace
        return when (node.kind) {
            PalaceNodeKind.Palace -> false
            PalaceNodeKind.Route, PalaceNodeKind.Area ->
                parent.kind in listOf(PalaceNodeKind.Palace, PalaceNodeKind.Route, PalaceNodeKind.Area)
            PalaceNodeKind.Locus ->
                parent.kind in listOf(PalaceNodeKind.Palace, PalaceNodeKind.Route, PalaceNodeKind.Area)
        }
    }
}

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
    val fullyCorrectCount: Int,
    val partiallyCorrectCount: Int,
    val incorrectCount: Int,
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
            fullyCorrectCount = checked.count { it == 2 },
            partiallyCorrectCount = checked.count { it == 1 },
            incorrectCount = checked.count { it == 0 },
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

/** Computes the failure streak after applying the current recall result. Attempts must be newest first. */
object RecallFailurePlan {
    fun consecutiveFailuresAfter(recentAttempts: List<RecallAttempt>, currentCorrect: Boolean): Int {
        if (currentCorrect) return 0
        return recentAttempts.takeWhile { !it.correct }.size + 1
    }
}

object RecallScheduler {
    fun schedule(
        previousReviews: Int,
        score: Int,
        now: Long = System.currentTimeMillis(),
        consecutiveFailures: Int = 0,
    ): RecallOutcome {
        val normalized = score.coerceIn(0, 5)
        val interval = when {
            consecutiveFailures >= 2 -> 1
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


/** Complete logical backup payload; timestamps and stable IDs are retained across restore. */
data class MemoryBackupSnapshot(
    val createdAt: Long,
    val memories: List<MemoryItem>,
    val palaceNodes: List<PalaceNode>,
    val attempts: List<RecallAttempt>,
    val checkpoints: List<RetentionCheckpoint>,
)

/**
 * Versioned JSON codec for a full logical backup. This preserves study records and history,
 * unlike TSV, which intentionally exports only memory content.
 */
object MemoryBackupPlan {
    private const val FORMAT = "MemoryImprovement-backup"
    private const val VERSION = 1

    fun encode(snapshot: MemoryBackupSnapshot): String {
        validate(snapshot)
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", snapshot.createdAt)
            .put("memories", JSONArray().apply {
                snapshot.memories.forEach { item ->
                    put(JSONObject()
                        .put("id", item.id)
                        .put("prompt", item.prompt)
                        .put("answer", item.answer)
                        .put("palace", item.palace)
                        .put("locus", item.locus)
                        .put("createdAt", item.createdAt)
                        .put("nextReviewAt", item.nextReviewAt)
                        .put("lastScore", item.lastScore ?: JSONObject.NULL)
                        .put("reviewCount", item.reviewCount)
                        .put("palaceNodeId", item.palaceNodeId ?: JSONObject.NULL)
                        .put("chunkLabel", item.chunkLabel)
                        .put("association", item.association)
                        .put("sequenceOrder", item.sequenceOrder ?: JSONObject.NULL))
                }
            })
            .put("palaceNodes", JSONArray().apply {
                snapshot.palaceNodes.forEach { node ->
                    put(JSONObject()
                        .put("id", node.id)
                        .put("name", node.name)
                        .put("kind", node.kind.name)
                        .put("parentId", node.parentId ?: JSONObject.NULL)
                        .put("position", node.position)
                        .put("createdAt", node.createdAt))
                }
            })
            .put("attempts", JSONArray().apply {
                snapshot.attempts.forEach { attempt ->
                    put(JSONObject()
                        .put("itemId", attempt.itemId)
                        .put("mode", attempt.mode.name)
                        .put("correct", attempt.correct)
                        .put("score", attempt.score)
                        .put("latencyMs", attempt.latencyMs)
                        .put("recalledResponse", attempt.recalledResponse ?: JSONObject.NULL)
                        .put("reviewedAt", attempt.reviewedAt))
                }
            })
            .put("checkpoints", JSONArray().apply {
                snapshot.checkpoints.forEach { checkpoint ->
                    put(JSONObject()
                        .put("id", checkpoint.id)
                        .put("itemId", checkpoint.itemId)
                        .put("delayDays", checkpoint.delayDays)
                        .put("dueAt", checkpoint.dueAt)
                        .put("completedAt", checkpoint.completedAt ?: JSONObject.NULL)
                        .put("score", checkpoint.score ?: JSONObject.NULL)
                        .put("latencyMs", checkpoint.latencyMs ?: JSONObject.NULL)
                        .put("recalledResponse", checkpoint.recalledResponse ?: JSONObject.NULL)
                        .put("accuracy", checkpoint.accuracy ?: JSONObject.NULL))
                }
            })
        return root.toString()
    }

    fun decode(raw: String): MemoryBackupSnapshot {
        val root = JSONObject(raw)
        require(root.getString("format") == FORMAT) { "This is not a MemoryImprovement backup." }
        require(root.getInt("version") == VERSION) { "Unsupported backup version: ${root.optInt("version", -1)}." }
        val memories = root.getJSONArray("memories").let { array ->
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                MemoryItem(
                    id = item.getString("id"),
                    prompt = item.getString("prompt"),
                    answer = item.getString("answer"),
                    palace = item.getString("palace"),
                    locus = item.getString("locus"),
                    createdAt = item.getLong("createdAt"),
                    nextReviewAt = item.getLong("nextReviewAt"),
                    lastScore = item.nullableInt("lastScore"),
                    reviewCount = item.getInt("reviewCount"),
                    palaceNodeId = item.nullableString("palaceNodeId"),
                    chunkLabel = item.getString("chunkLabel"),
                    association = item.getString("association"),
                    sequenceOrder = item.nullableInt("sequenceOrder"),
                )
            }
        }
        val nodes = root.getJSONArray("palaceNodes").let { array ->
            (0 until array.length()).map { index ->
                val node = array.getJSONObject(index)
                PalaceNode(
                    id = node.getString("id"),
                    name = node.getString("name"),
                    kind = PalaceNodeKind.valueOf(node.getString("kind")),
                    parentId = node.nullableString("parentId"),
                    position = node.getInt("position"),
                    createdAt = node.getLong("createdAt"),
                )
            }
        }
        val attempts = root.getJSONArray("attempts").let { array ->
            (0 until array.length()).map { index ->
                val attempt = array.getJSONObject(index)
                RecallAttempt(
                    itemId = attempt.getString("itemId"),
                    mode = RecallMode.valueOf(attempt.getString("mode")),
                    correct = attempt.getBoolean("correct"),
                    score = attempt.getInt("score"),
                    latencyMs = attempt.getLong("latencyMs"),
                    recalledResponse = attempt.nullableString("recalledResponse"),
                    reviewedAt = attempt.getLong("reviewedAt"),
                )
            }
        }
        val checkpoints = root.getJSONArray("checkpoints").let { array ->
            (0 until array.length()).map { index ->
                val checkpoint = array.getJSONObject(index)
                RetentionCheckpoint(
                    id = checkpoint.getString("id"),
                    itemId = checkpoint.getString("itemId"),
                    delayDays = checkpoint.getInt("delayDays"),
                    dueAt = checkpoint.getLong("dueAt"),
                    completedAt = checkpoint.nullableLong("completedAt"),
                    score = checkpoint.nullableInt("score"),
                    latencyMs = checkpoint.nullableLong("latencyMs"),
                    recalledResponse = checkpoint.nullableString("recalledResponse"),
                    accuracy = checkpoint.nullableInt("accuracy"),
                )
            }
        }
        return MemoryBackupSnapshot(
            createdAt = root.getLong("createdAt"),
            memories = memories,
            palaceNodes = nodes,
            attempts = attempts,
            checkpoints = checkpoints,
        ).also(::validate)
    }

    private fun validate(snapshot: MemoryBackupSnapshot) {
        require(snapshot.memories.map { it.id }.toSet().size == snapshot.memories.size) { "Backup contains duplicate memory IDs." }
        require(snapshot.palaceNodes.map { it.id }.toSet().size == snapshot.palaceNodes.size) { "Backup contains duplicate palace node IDs." }
        val memoryIds = snapshot.memories.mapTo(mutableSetOf()) { it.id }
        val nodeIds = snapshot.palaceNodes.mapTo(mutableSetOf()) { it.id }
        require(snapshot.memories.all { it.palaceNodeId == null || it.palaceNodeId in nodeIds }) {
            "A memory references a missing palace node."
        }
        require(snapshot.palaceNodes.all { it.parentId == null || it.parentId in nodeIds }) {
            "A palace node references a missing parent."
        }
        require(snapshot.attempts.all { it.itemId in memoryIds }) { "A recall attempt references a missing memory." }
        require(snapshot.checkpoints.all { it.itemId in memoryIds }) { "A retention checkpoint references a missing memory." }
        require(snapshot.checkpoints.map { it.id }.toSet().size == snapshot.checkpoints.size) { "Backup contains duplicate checkpoint IDs." }
        val byId = snapshot.palaceNodes.associateBy { it.id }
        snapshot.palaceNodes.forEach { node ->
            val visited = mutableSetOf<String>()
            var current: PalaceNode? = node
            while (current != null) {
                require(visited.add(current.id)) { "Backup contains a palace hierarchy cycle." }
                current = current.parentId?.let(byId::get)
            }
        }
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.nullableInt(key: String): Int? =
        if (isNull(key)) null else getInt(key)

    private fun JSONObject.nullableLong(key: String): Long? =
        if (isNull(key)) null else getLong(key)
}
